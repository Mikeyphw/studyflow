#!/usr/bin/env python3
"""SFDT-07: GitHub-only native Devtool DAG node executor, four shadow lanes.

Compile and run with the EXISTING Devtool EXO/DAG implementation. No independent
Gradle subprocess, job scheduler, signing, release, or local execution.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
SHA40 = re.compile(r'[a-f0-9]{40}\Z')
LANES = {
    'phone-debug': ('studyflow_android', 'ci-sfdt07-phone-debug', 'sfdt07-phone-debug',
                    'android-gradle', [':app:assembleDebug', ':app:lintDebug']),
    'tv-debug': ('studyflow_tv', 'ci-sfdt07-tv-debug', 'sfdt07-tv-debug',
                 'android-gradle', [':app-tv:assembleDebug', ':app-tv:testDebugUnitTest', ':app-tv:lintDebug']),
    'web-pytest': ('studyflow_android', 'ci-sfdt07-web-pytest', 'sfdt07-web-pytest',
                   'python-shell', []),
    'quality-source': ('studyflow_quality', 'ci-sfdt07-quality-source', 'sfdt07-quality-source',
                       'python-shell', []),
}


class NativeRefusal(ValueError):
    pass


def require(test: bool, message: str) -> None:
    if not test:
        raise NativeRefusal(message)


def _git(root: Path, *args: str) -> str:
    p = subprocess.run(['git', '-C', str(root), *args], capture_output=True, text=True,
                       timeout=15, check=False)
    require(p.returncode == 0, 'Cannot read reviewed consumer Git source')
    return p.stdout.strip()


def identity(env: dict, root: Path) -> dict:
    for key, value in [('GITHUB_ACTIONS', 'true'), ('GITHUB_EVENT_NAME', 'workflow_dispatch'),
                       ('CI', 'true'), ('RUNNER_OS', 'Linux'), ('RUNNER_ARCH', 'X64')]:
        require(env.get(key) == value, 'Not a reviewed GitHub-hosted dispatch: ' + key)
    require(env.get('GITHUB_WORKSPACE') == str(root.resolve()), 'Runner workspace is not consumer source root')
    sha = _git(root, 'rev-parse', 'HEAD')
    tree = _git(root, 'rev-parse', 'HEAD^{tree}')
    require(bool(SHA40.fullmatch(sha) and SHA40.fullmatch(tree)), 'Git source is not pinned')
    require(env.get('GITHUB_SHA') == sha and env.get('EXPECTED_SHA') == sha,
            'Requested SHA does not match checked-out Git commit')
    runtime = env.get('DEVTOOL_SOURCE_SHA', '')
    require(bool(SHA40.fullmatch(runtime)), 'Devtool runtime SHA missing')
    run, attempt = env.get('GITHUB_RUN_ID', ''), env.get('GITHUB_RUN_ATTEMPT', '')
    require(run.isdecimal() and attempt.isdecimal() and int(run) > 0 and int(attempt) > 0,
            'GitHub run/attempt is missing')
    require(not _git(root, 'status', '--porcelain', '--untracked-files=normal'),
            'Consumer source is dirty before native plan')
    return {'source_sha': sha, 'source_tree': tree, 'runtime_sha': runtime,
            'run_id': int(run), 'attempt': int(attempt), 'repository': env.get('GITHUB_REPOSITORY', '')}


def junit(directory: Path) -> dict:
    """Read a bounded set of actual JUnit XML reports, not stdout text."""
    require(directory.is_dir() and not directory.is_symlink(), 'Missing JUnit directory')
    files = sorted(directory.glob('TEST-*.xml')) if directory.name != 'sfdt07' else [directory / 'web-pytest-junit.xml']
    require(0 < len(files) <= 128, 'Expected bounded nonempty JUnit report inventory')
    tests = failures = errors = skipped = 0
    digests = {}
    for p in files:
        require(p.is_file() and not p.is_symlink() and 0 < p.stat().st_size <= 4*1024*1024,
                'Missing/symlinked/oversized JUnit report')
        try:
            tree = ET.parse(p)
        except (ET.ParseError, OSError) as exc:
            raise NativeRefusal('Malformed JUnit report') from exc
        nodes = [tree.getroot()] if tree.getroot().tag == 'testsuite' else list(tree.getroot())
        require(bool(nodes) and all(n.tag == 'testsuite' for n in nodes), 'Non-JUnit root')
        for suite in nodes:
            for tag in ('tests','failures','errors','skipped'):
                value = suite.get(tag, '0')
                require(value.isdecimal() and int(value) <= 100000, 'Invalid JUnit count')
            # A self-declared suite count alone is not independent JUnit
            # evidence; require corresponding testcase elements.
            cases = suite.findall('testcase')
            require(len(cases) == int(suite.get('tests','0')),
                    'JUnit suite counts lack actual testcase members')
            observed = {'failures': sum(bool(case.findall('failure')) for case in cases),
                        'errors': sum(bool(case.findall('error')) for case in cases),
                        'skipped': sum(bool(case.findall('skipped')) for case in cases)}
            require(all(observed[k] == int(suite.get(k,'0')) for k in observed),
                    'JUnit failure/error/skip summary differs from testcase members')
            tests += len(cases)
            failures += observed['failures']
            errors += observed['errors']
            skipped += observed['skipped']
        digests[p.name] = hashlib.sha256(p.read_bytes()).hexdigest()
    require(tests > 0 and tests > skipped and failures == 0 and errors == 0,
            'JUnit evidence missing actual passing tests or contains failures')
    return {'tests': tests, 'failures': failures, 'errors': errors, 'skipped': skipped,
            'report_sha256': digests, 'verified': True}


def select_lane(graph: dict, lane: str) -> dict:
    target, workflow, job_name, provider, tasks = LANES[lane]
    jobs = graph.get('jobs')
    require(isinstance(jobs, list) and len(jobs) == 1, 'Shadow native graph must own exactly one job')
    row = jobs[0]
    require(row.get('job') == job_name and row.get('provider') == provider
            and row.get('runner') == 'ubuntu-24.04' and row.get('needs') == []
            and row.get('handoffs') == [], 'Native provider/job/runner identity drift')
    if provider == 'android-gradle':
        review = row.get('android_review') or {}
        require(review.get('semantic_request') is True and review.get('tasks') == tasks
                and review.get('java_version') == '21'
                and review.get('ndk_version') == '29.0.14206865'
                and review.get('host_profile') == 'studyflow-ubuntu24',
                'Android task/host profile does not match reviewed debug graph')
        require(len(row.get('outputs', [])) == 1, 'Native Android build must stage exactly one debug APK')
    else:
        require(not row.get('outputs') and not tasks, 'Python shadow may not publish Android bytes')
    return row


def execute(root: Path, lane: str, output: Path, *, env: dict | None = None) -> dict:
    require(lane in LANES, 'Unknown native shadow lane')
    env = dict(os.environ if env is None else env)
    # Do not resolve away a symlink before inspecting it.
    require(not output.is_symlink(), 'Symlinked evidence destination')
    output = output.absolute()
    require(not output.is_relative_to(root.resolve()) and
            not output.parent.is_symlink(),
            'Evidence must remain outside consumer checkout')
    record = {'schema_version': 1, 'kind': 'studyflow.sfdt07.native-hosted-shadow',
              'lane': lane, 'status': 'FAILED_INCOMPLETE', 'source': {}, 'node': {},
              'junit': None, 'native_graph_executed': False, 'legacy_equivalent': False,
              'deep_profiles_qualified': False, 'device_qualified': False,
              'signing_qualified': False, 'release_qualified': False, 'cutover_authorized': False}
    try:
        bound = identity(env, root)
        record['source'] = bound
        temp = Path(env.get('RUNNER_TEMP', '')).resolve()
        require(temp.is_dir() and not temp.is_relative_to(root.resolve()), 'Unsafe GitHub runner temp directory')
        from devtool.actions_graph_export import compile_graph, run_node
        target, workflow, job_name, _, _ = LANES[lane]
        graph = compile_graph(root, target=target, workflow=workflow,
                              devtool_sha=bound['runtime_sha'])
        require(graph.get('consumer_commit') == bound['source_sha'] and
                graph.get('runtime_sha') == bound['runtime_sha'], 'Frozen source/runtime graph drift')
        row = select_lane(graph, lane)
        record['graph_sha256'] = graph['graph_sha256']
        result = run_node(root, target=target, workflow=workflow,
                          devtool_sha=bound['runtime_sha'], graph_sha=graph['graph_sha256'],
                          job_id=row['id'], inputs=temp/'rm06-inputs', outputs=temp/'rm06-output')
        evidence = result.get('typed_node_evidence') or {}
        require(result.get('status') == 'LOCAL_JOB_SUCCEEDED' and
                evidence.get('source_commit') == bound['source_sha'] and
                evidence.get('graph_sha256') == graph['graph_sha256'] and
                evidence.get('job_id') == row['id'] and
                evidence.get('release_qualified') is False and
                evidence.get('authenticated_remote') is False,
                'Missing/mismatched native Devtool typed evidence')
        record['node'] = {'job_id': row['id'], 'job': job_name, 'provider': row['provider'],
                          'native_receipt_sha256': evidence['receipt_sha256'],
                          'output_paths': row['outputs'], 'remote_authenticated': False}
        record['native_graph_executed'] = True
        if lane == 'web-pytest':
            record['junit'] = junit(temp/'sfdt07')
        elif lane == 'tv-debug':
            tv_reports = root/'app-tv/build/test-results/testDebugUnitTest'
            record['junit'] = junit(tv_reports)
            # Preserve the report members alongside the digest-bound receipt,
            # not merely the unauditable local JUnit count summary.
            staged = temp/'sfdt07/tv-junit'
            require(not staged.exists() and not staged.is_symlink(),
                    'Existing TV JUnit evidence staging directory')
            staged.mkdir(parents=True)
            for name, expected in record['junit']['report_sha256'].items():
                src = tv_reports / name
                require(src.is_file() and not src.is_symlink() and
                        hashlib.sha256(src.read_bytes()).hexdigest() == expected,
                        'Native TV JUnit changed since parsing')
                (staged/name).write_bytes(src.read_bytes())
        record['status'] = 'NATIVE_SHADOW_PASSED'
        return record
    except Exception as exc:
        record['error'] = f'{type(exc).__name__}: {str(exc)[:230]}'
        raise
    finally:
        record['completed_at'] = datetime.now(timezone.utc).isoformat()
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(record, indent=2, sort_keys=True) + '\n', encoding='utf-8')


def fallback(lane: str, output: Path) -> None:
    require(lane in LANES, 'Unknown fallback lane')
    require(not output.exists() and not output.is_symlink(), 'Cannot overwrite existing native receipt')
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps({
        'schema_version': 1, 'kind': 'studyflow.sfdt07.native-hosted-shadow',
        'lane': lane, 'status': 'FAILED_OR_NOT_RUN',
        'source': {'source_sha': os.environ.get('GITHUB_SHA', ''),
                   'run_id': os.environ.get('GITHUB_RUN_ID', ''),
                   'attempt': os.environ.get('GITHUB_RUN_ATTEMPT', '')},
        'native_graph_executed': False, 'legacy_equivalent': False,
        'signing_qualified': False, 'release_qualified': False,
        'cutover_authorized': False}, sort_keys=True, indent=2) + '\n')


def main() -> int:
    parser = argparse.ArgumentParser(description='SFDT07 reviewed GitHub-only native lanes')
    parser.add_argument('--lane', required=True, choices=sorted(LANES))
    parser.add_argument('--out', required=True, type=Path)
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument('--execute', action='store_true')
    group.add_argument('--fallback', action='store_true')
    args = parser.parse_args()
    try:
        if args.fallback:
            fallback(args.lane, args.out)
            return 0
        row = execute(ROOT, args.lane, args.out)
    except Exception as exc:
        print('SFDT07_NATIVE_REFUSED: ' + f'{type(exc).__name__}: {str(exc)[:200]}', file=sys.stderr)
        return 2
    print(f'SFDT07_NATIVE_SHADOW_PASS: {args.lane}, source={row["source"]["source_sha"][:12]}, '
          'cutover=BLOCKED, release=UNQUALIFIED')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
