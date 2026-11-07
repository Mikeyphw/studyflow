#!/usr/bin/env python3
"""StudyFlow hosted Devtool CI: existing native DAGs, four ordinary CI lanes.

No local Gradle, signing, release, publishing, device, or CI-policy mutation.
Phone/TV use first-class Devtool Android jobs; Web/Desktop use existing
source-reviewed Python-shell bridges *owned by* native Devtool DAG jobs.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
REPO = 'Mikeyphw/studyflow'
DEVTOOL_SHA = 'bee6e0b7e49aaaa0c56f755fd5be434a27c52b6c'
SHA = re.compile(r'[a-f0-9]{40}\Z')
# This inventory preserves existing .devtool.toml DAGs and their real providers.
NODES = {
    'phone': (
        ('studyflow_android', 'ci-sfdt07-phone-debug', 'sfdt07-phone-debug', 'android-gradle',
         (':app:assembleDebug', ':app:lintDebug'), 'app/build/outputs/apk/debug/app-debug.apk', None),
        ('studyflow_android', 'ci-sfdt08-phone-release-jvm', 'sfdt08-phone-release-jvm', 'android-gradle',
         (':app:testReleaseUnitTest',), None, 'app/build/test-results/testReleaseUnitTest'),
    ),
    'tv': (
        ('studyflow_tv', 'ci-sfdt07-tv-debug', 'sfdt07-tv-debug', 'android-gradle',
         (':app-tv:assembleDebug', ':app-tv:testDebugUnitTest', ':app-tv:lintDebug'),
         'app-tv/build/outputs/apk/debug/app-tv-debug.apk', 'app-tv/build/test-results/testDebugUnitTest'),
    ),
    'desktop': (
        ('studyflow_desktop', 'ci-sfdt08-desktop-fast', 'sfdt08-desktop-fast', 'python-shell',
         (), None, 'desktopApp/build/test-results/test'),
    ),
    'web': (
        ('studyflow_android', 'ci-sfdt08-web-full', 'sfdt08-web-full', 'python-shell',
         (), None, 'web/build/test-results/sfdt08-pytest'),
    ),
}


class Refused(ValueError):
    pass


def need(ok: bool, reason: str) -> None:
    if not ok:
        raise Refused(reason)


def git(root: Path, *args: str) -> str:
    p = subprocess.run(['git', '-C', str(root), *args], text=True, capture_output=True,
                       timeout=15, check=False)
    need(p.returncode == 0, 'Cannot verify Git source identity')
    return p.stdout.strip()


def hosted_identity(env: dict, root: Path) -> dict:
    for k, v in (('GITHUB_ACTIONS', 'true'), ('CI', 'true'), ('RUNNER_OS', 'Linux'),
                 ('RUNNER_ARCH', 'X64'), ('GITHUB_REPOSITORY', REPO)):
        need(env.get(k) == v, 'Unreviewed hosted environment: ' + k)
    need(env.get('GITHUB_EVENT_NAME') in ('push', 'pull_request', 'workflow_dispatch'),
         'Unexpected event for regular CI')
    sha = env.get('GITHUB_SHA', '')
    need(isinstance(sha, str) and SHA.fullmatch(sha) is not None,
         'Source SHA must be 40 lowercase hex')
    need(Path(env.get('GITHUB_WORKSPACE', '/not-this')).resolve() == root.resolve(),
         'Runner workspace differs from StudyFlow source root')
    need(git(root, 'rev-parse', 'HEAD') == sha, 'Checkout and workflow SHA mismatch')
    need(not git(root, 'status', '--porcelain', '--untracked-files=normal'),
         'Dirty checkout before native provider execution')
    temp = Path(env.get('RUNNER_TEMP', '/not-this')).resolve()
    need(temp.is_dir() and not temp.is_relative_to(root.resolve()) and temp != root.resolve(),
         'Unsafe runner temp directory')
    installed = temp / 'studyflow-primary-devtool-source'
    need(git(installed, 'rev-parse', 'HEAD') == DEVTOOL_SHA,
         'Actual Devtool runtime is not the reviewed pin')
    need(env.get('DEVTOOL_SOURCE_SHA') == DEVTOOL_SHA,
         'Workflow environment Devtool runtime differs from reviewed pin')
    for key in ('STUDYFLOW_RELEASE_STORE_PASSWORD', 'STUDYFLOW_RELEASE_KEY_PASSWORD',
                'STUDYFLOW_RELEASE_STORE_FILE'):
        need(not env.get(key), 'Release signing material must not enter CI: ' + key)
    run, attempt = env.get('GITHUB_RUN_ID', ''), env.get('GITHUB_RUN_ATTEMPT', '')
    need(run.isdecimal() and attempt.isdecimal() and int(run) > 0 and int(attempt) > 0,
         'GitHub run/attempt missing')
    return {'studyflow_sha': sha, 'devtool_sha': DEVTOOL_SHA,
            'run_id': int(run), 'run_attempt': int(attempt),
            'event': env['GITHUB_EVENT_NAME']}


def inspect_junit(root: Path, directory: str, pattern: str = 'TEST-*.xml') -> dict:
    """Conservative local test-report fallback for the TV Android provider."""
    location = root / directory
    need(location.is_dir() and not location.is_symlink(), 'Missing reviewed JUnit directory: ' + directory)
    need(pattern in ('TEST-*.xml', 'web-pytest-junit.xml'), 'Unreviewed JUnit report pattern')
    files = sorted(location.glob(pattern))
    need(0 < len(files) <= 256, 'Missing/unbounded JUnit XML inventory: ' + directory)
    executed = failed = errors = skipped = 0
    for file in files:
        need(file.is_file() and not file.is_symlink() and 0 < file.stat().st_size <= 6*1024*1024,
             'Unsafe JUnit XML member')
        try:
            root_node = ET.parse(file).getroot()
        except (ET.ParseError, OSError) as exc:
            raise Refused('Invalid JUnit XML: ' + file.name) from exc
        need(root_node.tag in ('testsuite', 'testsuites'), 'Not a JUnit root')
        cases = list(root_node.iter('testcase'))
        need(bool(cases), 'JUnit contains no testcases: ' + file.name)
        for case in cases:
            executed += 1
            failed += int(any(child.tag == 'failure' for child in case))
            errors += int(any(child.tag == 'error' for child in case))
            skipped += int(any(child.tag == 'skipped' for child in case))
    need(executed > skipped and failed == 0 and errors == 0,
         'JUnit failed or contains no passing tests')
    return {'executed': executed, 'failed': failed, 'errors': errors, 'skipped': skipped}


def check_node(graph: dict, spec: tuple, identity: dict) -> dict:
    target, workflow, job, provider, tasks, apk, junit_dir = spec
    need(graph.get('consumer_commit') == identity['studyflow_sha'] and
         graph.get('runtime_sha') == identity['devtool_sha'], 'Devtool graph source/runtime drift')
    jobs = graph.get('jobs')
    need(isinstance(jobs, list) and len(jobs) == 1, 'Expected one reviewed native DAG node')
    row = jobs[0]
    need(row.get('job') == job and row.get('provider') == provider and
         row.get('runner') == 'ubuntu-24.04' and
         not row.get('handoffs') and not row.get('needs'), 'Unexpected native job/provider/host')
    outputs = row.get('outputs')
    need(outputs == ([apk] if apk else []), 'Native output ownership differs from reviewed config')
    if provider == 'android-gradle':
        review = row.get('android_review') or {}
        need(review.get('semantic_request') is True and tuple(review.get('tasks') or ()) == tasks,
             'Reviewed native Android task list changed')
        need(review.get('java_version') == '21' and
             review.get('ndk_version') == '29.0.14206865' and
             review.get('host_profile') == 'studyflow-ubuntu24',
             'Android toolchain profile changed')
    if junit_dir and job != 'sfdt07-tv-debug':
        need(row.get('junit_report_dir') == junit_dir,
             'Native provider JUnit directory differs from reviewed path')
    return row


def check_result(result: dict, graph: dict, row: dict, identity: dict, junit_dir: str | None,
                 root: Path, *, apk: str | None) -> dict:
    typed = result.get('typed_node_evidence') or {}
    need(result.get('status') == 'LOCAL_JOB_SUCCEEDED' and
         typed.get('source_commit') == identity['studyflow_sha'] and
         typed.get('graph_sha256') == graph['graph_sha256'] and
         typed.get('job_id') == row['id'] and
         typed.get('authenticated_remote') is False and
         typed.get('release_qualified') is False,
         'Native Devtool typed receipt mismatches source or job')
    item = {'job': row['job'], 'provider': row['provider'],
            'graph_sha256': graph['graph_sha256'],
            'native_receipt_sha256': typed.get('receipt_sha256')}
    need(isinstance(item['native_receipt_sha256'], str) and
         re.fullmatch(r'[a-f0-9]{64}', item['native_receipt_sha256']) is not None,
         'Native receipt lacks SHA-256')
    if apk:
        file = root / apk
        need(file.is_file() and not file.is_symlink() and
             1024 <= file.stat().st_size <= 500*1024*1024,
             'Missing/invalid debug APK; fallback artifacts do not count')
        item['apk'] = {'path': apk, 'size': file.stat().st_size,
                       'sha256': hashlib.sha256(file.read_bytes()).hexdigest()}
    if junit_dir:
        if row['job'] != 'sfdt07-tv-debug':
            gate = (typed.get('typed_evidence') or {}).get('gates', {}).get('test', {}).get('state')
            need(gate == 'PASS', 'Devtool typed JUnit test gate did not pass')
        evidence = result.get('local_junit_verification')
        if evidence and evidence.get('local_verified') is True:
            tests = evidence.get('tests') or {}
            need(isinstance(tests.get('total'), int) and tests['total'] > 0 and
                 tests.get('failed') == 0, 'Devtool JUnit proof failed')
            item['junit'] = {'executed': tests['total'], 'failed': 0,
                             'proof': 'devtool-local-junit'}
        else:
            need(row['job'] == 'sfdt07-tv-debug', 'Missing native Devtool JUnit evidence')
            item['junit'] = {**inspect_junit(root, junit_dir), 'proof': 'local-XML'}
    return item


def execute(lane: str, out: Path, *, root: Path = ROOT, env: dict | None = None,
            compiler=None, runner=None) -> dict:
    need(lane in NODES, 'Unreviewed CI lane')
    env = dict(os.environ if env is None else env)
    identity = hosted_identity(env, root)
    temp = Path(env['RUNNER_TEMP']).resolve()
    need(not out.is_symlink() and out.resolve().is_relative_to(temp) and not out.exists(),
         'Receipt must be a new file beneath RUNNER_TEMP')
    if compiler is None or runner is None:
        from devtool.actions_graph_export import compile_graph, run_node
        compiler = compile_graph if compiler is None else compiler
        runner = run_node if runner is None else runner
    report = {'schema': 'studyflow.devtool.primary.v1', 'lane': lane,
              'identity': identity, 'status': 'FAILED_INCOMPLETE', 'nodes': [],
              'ci_lane_passed': False, 'cutover_authorized': False,
              'signing_qualified': False, 'release_qualified': False,
              'device_qualified': False, 'started_at': datetime.now(timezone.utc).isoformat()}
    try:
        for index, spec in enumerate(NODES[lane]):
            target, workflow, _, _, _, apk, junit_dir = spec
            graph = compiler(root, target=target, workflow=workflow, devtool_sha=DEVTOOL_SHA)
            row = check_node(graph, spec, identity)
            # Devtool's hosted receipt writer admits only these canonical paths.
            # After each node, preserve its bytes outside the canonical location
            # so the phone's second native job starts with an empty stage.
            stage_inputs = temp / 'rm06-inputs'
            stage_outputs = temp / 'rm06-output'
            stage_evidence = temp / 'rm06-evidence'
            # Every one-node graph uses node-01. Devtool writes its typed
            # evidence to RUNNER_TEMP/rm06-evidence/node-01 and deliberately
            # refuses to overwrite an existing receipt. The phone lane has
            # two sequential graphs, so isolate *all three* native staging
            # directories between them (not just inputs and outputs).
            need(all(not path.exists() and not path.is_symlink()
                     for path in (stage_inputs, stage_outputs, stage_evidence)),
                 'Native Devtool staging contains stale bytes')
            result = runner(root, target=target, workflow=workflow, devtool_sha=DEVTOOL_SHA,
                            graph_sha=graph['graph_sha256'], job_id=row['id'],
                            inputs=stage_inputs, outputs=stage_outputs)
            item = check_result(result, graph, row, identity, junit_dir, root, apk=apk)
            isolated = temp / 'studyflow-primary-execution' / lane / str(index)
            if stage_outputs.exists():
                isolated.mkdir(parents=True, exist_ok=False)
                shutil.move(str(stage_outputs), str(isolated/'outputs'))
            if stage_inputs.exists():
                isolated.mkdir(parents=True, exist_ok=True)
                shutil.move(str(stage_inputs), str(isolated/'inputs'))
            need(not stage_evidence.is_symlink(), 'Symlinked native Devtool evidence directory')
            if stage_evidence.exists():
                need(stage_evidence.is_dir(), 'Invalid native Devtool evidence directory')
                isolated.mkdir(parents=True, exist_ok=True)
                shutil.move(str(stage_evidence), str(isolated/'evidence'))
            report['nodes'].append(item)
        report['status'] = 'NATIVE_CI_LANE_PASSED'
        report['ci_lane_passed'] = True
        return report
    except Exception as exc:
        report['error'] = type(exc).__name__ + ': ' + str(exc)[:300]
        raise
    finally:
        report['completed_at'] = datetime.now(timezone.utc).isoformat()
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(json.dumps(report, indent=2, sort_keys=True)+'\n', encoding='utf-8')


# The Actions upload-artifact step is intentionally best-effort on failed jobs.
# On successful jobs, this independent source-owned check makes missing test
# report bytes fatal before the lane/gate can be marked green.
REPORT_DIRS = {
    'phone': 'app/build/test-results/testReleaseUnitTest',
    'tv': 'app-tv/build/test-results/testDebugUnitTest',
    'desktop': 'desktopApp/build/test-results/test',
    'web': 'web/build/test-results/sfdt08-pytest',
}
LINT_DIRS = {
    'phone': 'app/build/reports',
    'tv': 'app-tv/build/reports',
}


def verify_reports(lane: str, out: Path, *, root: Path = ROOT) -> dict:
    need(lane in NODES, 'Unreviewed hosted report lane')
    temp = Path(os.environ.get('RUNNER_TEMP', '/missing')).resolve()
    need(temp.is_dir() and out.is_file() and not out.is_symlink()
         and out.resolve().is_relative_to(temp),
         'Report receipt missing or outside runner temp')
    record = json.loads(out.read_text(encoding='utf-8'))
    need(record.get('lane') == lane and record.get('status') == 'NATIVE_CI_LANE_PASSED'
         and record.get('ci_lane_passed') is True
         and record.get('cutover_authorized') is False,
         'Cannot admit incomplete, failed or mismatched Devtool lane receipt')
    pattern = 'web-pytest-junit.xml' if lane == 'web' else 'TEST-*.xml'
    junit = inspect_junit(root, REPORT_DIRS[lane], pattern)
    evidence = {'lane': lane, 'junit': junit, 'junit_directory': REPORT_DIRS[lane]}
    if lane in LINT_DIRS:
        reports_dir = root / LINT_DIRS[lane]
        need(reports_dir.is_dir() and not reports_dir.is_symlink(),
             'Android lint report directory missing')
        reports = [p for ext in ('xml','html','sarif')
                   for p in [reports_dir / ('lint-results-debug.' + ext)]
                   if p.is_file() and not p.is_symlink() and p.stat().st_size > 0]
        need(reports, 'Android lint reports are missing')
        evidence['lint_reports'] = [p.name for p in reports]
    return evidence


def fallback(lane: str, out: Path) -> None:
    need(lane in NODES and not out.exists() and not out.is_symlink(), 'Invalid fallback request')
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps({'schema': 'studyflow.devtool.primary.v1', 'lane': lane,
                               'status': 'FAILED_OR_NOT_RUN', 'ci_lane_passed': False,
                               'cutover_authorized': False}, indent=2) + '\n')


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--lane', required=True, choices=tuple(NODES))
    p.add_argument('--out', required=True, type=Path)
    actions = p.add_mutually_exclusive_group(required=True)
    actions.add_argument('--execute', action='store_true')
    actions.add_argument('--guarded', action='store_true')
    actions.add_argument('--fallback', action='store_true')
    actions.add_argument('--verify-reports', action='store_true')
    args = p.parse_args(argv)
    try:
        if args.fallback:
            fallback(args.lane, args.out)
        elif args.verify_reports:
            summary = verify_reports(args.lane, args.out)
            print('STUDYFLOW_DEVTOOL_PRIMARY_REPORTS_PASS:', args.lane,
                  'junit_cases=', summary['junit']['executed'], flush=True)
        elif args.guarded:
            need(args.lane == 'phone', 'Only phone native Gradle is guarded')
            # Reuse SFDT18-HF01's verified monitor, not its manual-dispatch-only CLI.
            from sfdt18_phone_debug_guard import monitor
            temp = Path(os.environ['RUNNER_TEMP']).resolve()
            command = [sys.executable, '-u', '-B', str(Path(__file__).resolve()),
                       '--lane', 'phone', '--execute', '--out', str(args.out)]
            return monitor(command, temp/'studyflow-devtool-primary',
                           quiet_limit=10*60, wall_limit=45*60)
        else:
            receipt = execute(args.lane, args.out)
            print('STUDYFLOW_DEVTOOL_PRIMARY_LANE_PASS:', args.lane,
                  'nodes=',len(receipt['nodes']), flush=True)
        return 0
    except Exception as exc:
        print('STUDYFLOW_DEVTOOL_PRIMARY_REFUSED:', type(exc).__name__, str(exc)[:300],
              file=sys.stderr, flush=True)
        return 2


if __name__ == '__main__':
    raise SystemExit(main())
