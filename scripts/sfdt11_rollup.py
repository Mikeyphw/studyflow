#!/usr/bin/env python3
"""SFDT11 consolidated, read-only, fail-closed hosted StudyFlow evidence rollup.

Aggregates actual bytes already verified by SFDT07/SFDT08 comparators, original
SFDT06 and R2 artifacts, and legacy deep diagnostics. A successful rollup is
NOT authorization to migrate required CI, sign, release, or qualify devices.
"""
from __future__ import annotations

import argparse
from hashlib import sha256
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
from typing import Any

from sfdt11_contract import DEVTOOL, ROOT, source_check

REPO = 'Mikeyphw/studyflow'
SHA = re.compile(r'[0-9a-f]{40}\Z')
DIGEST = re.compile(r'sha256:[0-9a-f]{64}\Z')
PATHS = {
    'legacy': '.github/workflows/studyflow-ci.yml',
    'legacy_deep': '.github/workflows/studyflow-ci.yml',
    'source': '.github/workflows/studyflow-devtool-shadow.yml',
    'expanded': '.github/workflows/studyflow-devtool-native-expanded.yml',
    'fast': '.github/workflows/studyflow-devtool-native-coverage.yml',
    'deep': '.github/workflows/studyflow-devtool-native-coverage.yml',
    'android': '.github/workflows/studyflow-devtool-sfdt10-debug-shadow.yml',
    'compare': '.github/workflows/studyflow-devtool-sfdt10-r2-compare.yml',
}
DEEP = ('android-contracts','tv-contracts','desktop-contracts','quality-contracts','parity-contracts')
FAST = ('phone-release-jvm','desktop-fast','web-full')
EXPANDED = ('phone-debug','tv-debug','web-pytest','quality-source')
ANDROID = ('phone-debug','tv-debug','phone-release-jvm')
LEGACY_FAST = ('Actions integration source contract', 'web', 'desktop',
               'android (app, :app:testReleaseUnitTest)',
               'android (app-tv, :app-tv:testDebugUnitTest)')

class EvidenceError(ValueError):
    pass


def need(ok: bool, message: str) -> None:
    if not ok:
        raise EvidenceError(message)


def command(args: list[str], *, timeout: int = 200) -> str:
    try:
        out = subprocess.run(args, capture_output=True, text=True, timeout=timeout,
                             check=False, env={**os.environ, 'GH_PROMPT_DISABLED': '1',
                                               'GIT_TERMINAL_PROMPT': '0'})
    except (OSError, subprocess.TimeoutExpired) as exc:
        raise EvidenceError('Command unavailable/timeout: ' + args[0]) from exc
    need(out.returncode == 0, 'Command failed: ' + ' '.join(args[:4]) +
         ' (' + out.stderr[-240:] + ')')
    return out.stdout


def api(path: str) -> dict:
    need(re.fullmatch(r'[A-Za-z0-9_./?=&-]+', path) is not None and
         path.startswith('repos/Mikeyphw/studyflow/actions/runs/'),
         'Unreviewed read-only GitHub API path')
    raw = command(['gh', 'api', '--method', 'GET', path], timeout=45)
    try:
        obj = json.loads(raw)
    except (ValueError,UnicodeError) as exc:
        raise EvidenceError('Invalid GitHub API JSON') from exc
    need(isinstance(obj, dict), 'Invalid GitHub API object')
    return obj


def run_identity(row: dict, *, rid: int, name: str, source: str) -> int:
    need(name in PATHS and type(rid) is int and rid > 0 and SHA.fullmatch(source) is not None,
         'Invalid workflow identity request')
    need(row.get('id') == rid and row.get('head_sha') == source and
         row.get('head_branch') == 'main' and
         (row.get('repository') or {}).get('full_name') == REPO and
         row.get('path') == PATHS[name] and row.get('status') == 'completed' and
         row.get('conclusion') == 'success', 'Same-source run mismatch: ' + name)
    need(row.get('event') == ('push' if name == 'legacy' else 'workflow_dispatch'),
         'Wrong run event: ' + name)
    attempt = row.get('run_attempt')
    need(type(attempt) is int and attempt > 0, 'Missing attempt: ' + name)
    return attempt


def job_names(payload: dict, *, rid: int, expected: set[str], label: str) -> None:
    jobs = payload.get('jobs')
    need(isinstance(jobs, list) and payload.get('total_count') == len(jobs) and
         len(jobs) < 40, 'Incomplete GitHub jobs page: ' + label)
    active = [j for j in jobs if j.get('conclusion') != 'skipped']
    need(len(active) == len(expected) and {j.get('name') for j in active} == expected,
         'Missing, unexpected, or duplicate jobs: ' + label)
    need(all(j.get('run_id') == rid and j.get('status') == 'completed' and
             j.get('conclusion') == 'success' for j in active),
         'Failed/misattributed job: ' + label)
    need(all(j.get('conclusion') == 'skipped' for j in jobs if j not in active),
         'Unaccounted job state: ' + label)


def expected_jobs(name: str) -> set[str]:
    if name == 'legacy': return set(LEGACY_FAST)
    if name == 'legacy_deep': return {'Actions integration source contract',
                                    *(f'Devtool {p}' for p in DEEP)}
    if name == 'source': return {'native-source-shadow'}
    if name == 'expanded': return {'native-' + x for x in EXPANDED}
    if name == 'fast': return {'native-' + x for x in FAST}
    if name == 'deep': return {'native-' + x for x in DEEP}
    if name == 'android': return {'Source and signing isolation contract',
                                 *(f'native-{x}' for x in ANDROID)}
    if name == 'compare': return {'Compare same-SHA Android APK, lint and JVM evidence'}
    raise EvidenceError('Unexpected lane')


def artifact_inventory(obj: dict) -> dict[str, dict]:
    rows = obj.get('artifacts')
    need(isinstance(rows,list) and obj.get('total_count') == len(rows) and
         len(rows) <= 60, 'Incomplete GitHub artifact page')
    result: dict[str,dict] = {}
    for row in rows:
        name = row.get('name')
        need(isinstance(name,str) and name not in result and len(name) < 180 and
             row.get('expired') is False and type(row.get('size_in_bytes')) is int and
             0 < row['size_in_bytes'] < 150_000_000 and
             isinstance(row.get('digest'), str) and DIGEST.fullmatch(row['digest']) is not None,
             'Artifact missing/expired/duplicated/undigested')
        result[name] = row
    return result


def require_artifacts(inventory: dict[str,dict], *, rid: int, attempt: int,
                      name: str) -> dict[str, str]:
    def prefix(label: str) -> str:
        return label + f'-{rid}-{attempt}'
    if name == 'source':
        expected = [prefix('studyflow-sfdt06-native-shadow')]
    elif name == 'expanded':
        expected = [prefix('studyflow-sfdt07-' + x) for x in EXPANDED]
    elif name in ('fast','deep'):
        expected = [prefix('studyflow-sfdt08-' + x) for x in (FAST if name=='fast' else DEEP)]
    elif name == 'android':
        expected = [prefix('studyflow-sfdt10-' + x) for x in ANDROID]
    elif name == 'compare':
        expected = [prefix('studyflow-sfdt10-r2-compare')]
    elif name == 'legacy':
        expected = [f'studyflow-app-ci-debug-{rid}', f'studyflow-app-tv-ci-debug-{rid}',
                    prefix('studyflow-app-diagnostics'), prefix('studyflow-app-tv-diagnostics')]
    elif name == 'legacy_deep':
        expected = [prefix('studyflow-devtool-'+p) for p in DEEP]
    else:
        raise EvidenceError('Unknown artifact lane')
    need(set(inventory) >= set(expected), 'Missing required artifacts: '+name)
    # The legacy deep job may also publish Android lint diagnostics. Those are
    # additional evidence, not a substitute for the five profile receipts.
    if name not in ('legacy_deep','legacy'):
        need(set(inventory) == set(expected), 'Unexpected artifacts: '+name)
    return {k: inventory[k]['digest'] for k in expected}


def extracted(name: str, *, rid: int, to: Path) -> Path:
    need(not to.exists() and not to.is_symlink(), 'Stale output directory')
    to.mkdir(parents=True)
    command(['gh','run','download',str(rid),'-R',REPO,'-n',name,'-D',str(to)],timeout=240)
    paths = list(to.rglob('*'))
    need(0 < len(paths) <= 400, 'Missing/oversized downloaded artifact')
    total = 0
    for p in paths:
        need(not p.is_symlink() and p.resolve().is_relative_to(to.resolve()),
             'Artifact member symlink/traversal')
        if p.is_file():
            total += p.stat().st_size
            need(total < 80_000_000, 'Receipt archive unbounded')
    return to


def source_receipt(row: dict, *, source: str, rid: int) -> None:
    need(row.get('kind') == 'studyflow.sfdt06.native-source-shadow' and
         row.get('status') == 'NATIVE_SOURCE_SHADOW_PASSED' and
         len(row.get('nodes',[])) == 4 and
         all(x.get('status') == 'NATIVE_NODE_PASSED' for x in row['nodes']) and
         row.get('legacy_equivalent') is False and
         row.get('release_qualified') is False,
         'Source-shadow source-only native receipt not qualified')
    stamp = row.get('source',{})
    need(stamp.get('source_commit') == source and stamp.get('run_id') == rid,
         'Source-shadow run receipt source mismatch')



def source_members(base: Path, row: dict, *, source: str, rid: int, attempt: int) -> int:
    """Verify all four actual SFDT06 typed node receipts and their digests."""
    need(re.fullmatch('[a-f0-9]{64}', str(row.get('graph_sha256'))) is not None,
         'Source native graph digest absent')
    seen=set()
    for node in row.get('nodes', []):
        nid=node.get('id')
        need(isinstance(nid,str) and re.fullmatch(r'node-[0-9]{2}(?:-[a-z0-9-]+)?',nid) is not None
             and nid not in seen,'Unreviewed/duplicate source native node ID')
        seen.add(nid)
        f=base/'rm06-evidence'/nid/'node-evidence.json'
        need(f.is_file() and not f.is_symlink() and
             f.resolve().is_relative_to(base.resolve()) and 0 < f.stat().st_size < 160_000,
             'Missing or unsafe source node receipt')
        typed=json.loads(f.read_text(encoding='utf-8'))
        need(typed.get('source_commit')==source and
             typed.get('run_id')==rid and typed.get('attempt')==attempt and
             typed.get('graph_sha256')==row['graph_sha256'] and
             typed.get('release_qualified') is False and
             typed.get('authenticated_remote') is False and
             typed.get('receipt_sha256')==node.get('evidence_sha256'),
             'Source typed node receipt/run/graph mismatch')
        canonical={k:v for k,v in typed.items() if k!='receipt_sha256'}
        checksum=sha256(json.dumps(canonical,sort_keys=True,separators=(',',':'),
                                  ensure_ascii=True).encode('ascii')).hexdigest()
        need(checksum==typed['receipt_sha256'],
             'Source node typed canonical evidence digest mismatch')
    need(len(seen)==4,'Four unique source native nodes required')
    return len(seen)


def reviewed_deep_plan(profile: str) -> dict:
    from studyflow_ci_gradle_profile import profile_plan
    return profile_plan(profile)


def deep_receipt(row: dict, *, profile: str, source: str, rid: int, attempt: int,
                 reviewed_plan: dict, config_sha256: str) -> None:
    """Bind each legacy deep Gradle execution receipt to reviewed source/tasks."""
    need(profile in DEEP and row.get('schema')=='studyflow.devtool.github-gradle-plan.v1'
         and row.get('profile')==profile and row.get('git_sha')==source and
         row.get('github_run_id')==str(rid) and row.get('github_run_attempt')==str(attempt),
         'Deep receipt source/profile/run identity mismatch')
    need(row.get('status')=='passed' and row.get('exit_code')==0 and
         row.get('build_executed') is True and
         row.get('native_exo_gradle_execution') is False and
         row.get('qualified_release') is False and
         row.get('execution_authority')=='GitHub Actions only',
         'Deep receipt missing actual hosted Gradle success or claimed native/release authority')
    need(row.get('config_sha256')==config_sha256 and
         row.get('target')==reviewed_plan['target'] and
         row.get('operation')==reviewed_plan['operation'] and
         row.get('tasks')==reviewed_plan['tasks'] and
         row.get('gradle_argv')==['./gradlew','--no-daemon','--max-workers=2',
                                 *reviewed_plan['tasks']],
         'Deep receipt disagrees with exact reviewed Gradle tasks')


def android_receipt(row: dict, *, source: str, legacy: int, android: int) -> dict:
    need(row.get('kind') == 'studyflow.sfdt10.r2.android-same-source-evidence' and
         row.get('status') == 'ANDROID_MEMBER_CONTRACT_MATCHED_UNQUALIFIED' and
         row.get('source_sha') == source and
         row.get('legacy_run') == legacy and row.get('shadow_run') == android,
         'Android comparator receipt source/runs mismatch')
    need({x.get('lane') for x in row.get('lanes',[])} == set(ANDROID) and
         len(row['lanes']) == len(ANDROID), 'Android comparator lane coverage mismatch')
    for key in ('cutover_authorized','legacy_equivalent','release_qualified','device_qualified',
                'github_archive_bytes_sha256_verified'):
        need(row.get(key) is False, 'Android comparator invented qualification: '+key)
    for lane in row['lanes']:
        if lane['lane'] != 'phone-release-jvm':
            need((lane.get('apk') or {}).get('signatures_valid') is True and
                 (lane.get('lint') or {}).get('matching_issues') is True,
                 'Android native APK/lint comparison missing')
        else:
            need(lane.get('junit_cases',0) > 0, 'Missing phone release JVM cases')
    return {'status': row['status'], 'lanes': len(row['lanes']),
            'junit_cases': {x['lane']: x.get('junit_cases',0) for x in row['lanes']}}


def external_compare_report(row: dict, *, source: str, scope: str) -> dict:
    need(row.get('source_sha') == source and row.get('scope') == scope and
         row.get('status') == 'HOSTED_NATIVE_COVERAGE_VERIFIED_UNQUALIFIED' and
         row.get('cutover_authorized') is False and row.get('release_qualified') is False and
         row.get('device_qualified') is False and row.get('legacy_equivalent') is False and
         row.get('deep_equivalent') is False, 'SFDT08 comparison invented success/authority')
    expected = set(FAST if scope=='fast' else DEEP)
    lanes = row.get('lanes',[])
    need(isinstance(lanes,list) and len(lanes) == len(expected) and
         {x.get('lane') for x in lanes} == expected,
         'SFDT08 comparison did not verify full expected lane scope')
    need(all(x.get('failed') == 0 and x.get('cutover_authorized') is False for x in lanes),
         'SFDT08 reported native failed tests / unauthorized cutover')
    return {'status': row['status'], 'lanes': len(lanes),
            'junit_cases': {x['lane']: x.get('tests',0) for x in lanes}}


def expanded_report(row: dict, *, source: str) -> dict:
    need(row.get('source_sha') == source and row.get('same_sha') is True and
         row.get('status') == 'ALL_THREE_HOSTED_RUNS_GREEN_PARTIAL_COVERAGE' and
         all(row.get(k) is True for k in ('legacy_fast_passed','source_shadow_passed',
                                          'expanded_shadow_passed','member_evidence_verified')) and
         row.get('evidence_verification') == 'NATIVE_MEMBER_BYTES_VERIFIED_UNQUALIFIED' and
         row.get('cutover_authorized') is False, 'SFDT07 expanded member comparison incomplete')
    need(set(row.get('member_details',{})) == set(EXPANDED),
         'Missing SFDT07 member details')
    return {'status': row['status'], 'lanes': len(row['member_details'])}


def run_external(script: str, args: list[str]) -> dict:
    need(script in ('sfdt07_compare_runs.py', 'sfdt08_compare_coverage.py'),
         'Unreviewed comparison script')
    raw = command([sys.executable,'-B',str(ROOT/'scripts'/script),*args,'--json'], timeout=900)
    try: obj = json.loads(raw)
    except (ValueError,UnicodeError) as exc:
        raise EvidenceError('Comparison did not produce JSON: '+script) from exc
    need(isinstance(obj,dict), 'Invalid comparison report')
    return obj


def collect(*, source: str, run_ids: dict[str,int], temp: Path) -> dict:
    need(SHA.fullmatch(source) is not None and set(run_ids) == set(PATHS) and
         all(type(x) is int and x > 0 for x in run_ids.values()) and
         len(set(run_ids.values())) == 8, 'Eight distinct source-bound runs required')
    need(temp.is_dir() and not temp.is_symlink(), 'Invalid isolated runner temporary root')
    attempts: dict[str,int] = {}
    artifacts: dict[str,dict[str,str]] = {}
    for name, rid in run_ids.items():
        run = api(f'repos/Mikeyphw/studyflow/actions/runs/{rid}')
        attempts[name] = run_identity(run, rid=rid, name=name, source=source)
        job_names(api(f'repos/Mikeyphw/studyflow/actions/runs/{rid}/jobs?per_page=100'),
                  rid=rid, expected=expected_jobs(name), label=name)
        inventory = artifact_inventory(api(f'repos/Mikeyphw/studyflow/actions/runs/{rid}/artifacts?per_page=100'))
        artifacts[name] = require_artifacts(inventory, rid=rid, attempt=attempts[name], name=name)
    # Original GitHub artifacts, not user-supplied metadata or cached reports.
    src_name = f'studyflow-sfdt06-native-shadow-{run_ids["source"]}-{attempts["source"]}'
    src_dir = extracted(src_name, rid=run_ids['source'], to=temp/'source')
    p = src_dir/'sfdt06/native-source-shadow.json'
    need(p.is_file() and not p.is_symlink() and p.stat().st_size <= 200_000,
         'Source shadow receipt missing')
    source_row = json.loads(p.read_text())
    source_receipt(source_row, source=source, rid=run_ids['source'])
    source_members(src_dir, source_row, source=source, rid=run_ids['source'],
                   attempt=attempts['source'])
    # Independently re-open all five hosted canonical deep Gradle receipts;
    # their green job statuses do not suffice to qualify task execution.
    cfg_digest=sha256((ROOT/'.devtool.toml').read_bytes()).hexdigest()
    for profile in DEEP:
        name=f'studyflow-devtool-{profile}-{run_ids["legacy_deep"]}-{attempts["legacy_deep"]}'
        deep_dir=extracted(name,rid=run_ids['legacy_deep'],to=temp/('legacy-deep-'+profile))
        matching=[p for p in deep_dir.rglob('studyflow-devtool-profile.json') if p.is_file() and not p.is_symlink()]
        need(len(matching)==1 and matching[0].stat().st_size<=200_000,
             'Missing/ambiguous legacy deep Gradle receipt: '+profile)
        deep_receipt(json.loads(matching[0].read_text()),profile=profile,source=source,
                     rid=run_ids['legacy_deep'],attempt=attempts['legacy_deep'],
                     reviewed_plan=reviewed_deep_plan(profile),config_sha256=cfg_digest)
    cmp_name = f'studyflow-sfdt10-r2-compare-{run_ids["compare"]}-{attempts["compare"]}'
    cmp_dir = extracted(cmp_name, rid=run_ids['compare'], to=temp/'android-compare')
    p = cmp_dir/'sfdt10-r2-report.json'
    need(p.is_file() and not p.is_symlink() and p.stat().st_size <= 200_000,
         'R2 comparator member report missing')
    android = android_receipt(json.loads(p.read_text()), source=source,
                              legacy=run_ids['legacy'], android=run_ids['android'])
    args = ['--repo',REPO,'--legacy-run',str(run_ids['legacy']),
            '--source-shadow-run',str(run_ids['source']), '--expanded-run',str(run_ids['expanded'])]
    expanded = expanded_report(run_external('sfdt07_compare_runs.py',args), source=source)
    others = {}
    for scope in ('fast','deep'):
        opts = ['--repo',REPO,'--legacy-run',str(run_ids['legacy']),
                '--source-run',str(run_ids['source']),
                '--expanded-run',str(run_ids['expanded']),
                '--coverage-run',str(run_ids[scope]),'--scope',scope]
        others[scope] = external_compare_report(
            run_external('sfdt08_compare_coverage.py',opts), source=source, scope=scope)
    return {'schema_version':1,'kind':'studyflow.sfdt11.consolidated-hosted-evidence',
            'status':'EIGHT_RUN_BOUND_NATIVE_AND_LEGACY_EVIDENCE_VERIFIED_UNQUALIFIED',
            'source_sha':source,'devtool_sha':DEVTOOL,
            'run_ids':run_ids,'attempts':attempts,'artifact_archive_metadata_sha256':artifacts,
            'source_nodes':4,'expanded':expanded,'fast':others['fast'],
            'deep':others['deep'],'android_parity':android,
            'legacy_deep_profiles':list(DEEP), 'legacy_deep_pass':True,
            'web_node_first_class':False,'desktop_jvm_first_class':False,
            'pr_push_required_checks_qualified':False,
            'branch_protection_readback_qualified':False,
            'signing_environment_readback_qualified':False,
            'physical_device_qualified':False,
            'zip_transfer_byte_sha256_verified':False,
            'legacy_equivalent':False,'deep_equivalent':False,
            'release_qualified':False,'device_qualified':False,
            'cutover_authorized':False,
            'decision':'NO_GO_CI_CUTOVER'}


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--source-check',action='store_true')
    ap.add_argument('--source',default='')
    for name in PATHS:
        ap.add_argument('--'+name.replace('_','-')+'-run',type=int,default=0)
    ap.add_argument('--output',type=Path)
    a=ap.parse_args(argv)
    try:
        if a.source_check:
            source_check()
            print('SFDT11_CONSOLIDATED_SOURCE_PASS')
            return 0
        need(a.output is not None and not a.output.exists() and
             os.environ.get('GITHUB_ACTIONS')=='true' and
             os.environ.get('GITHUB_EVENT_NAME')=='workflow_dispatch' and
             os.environ.get('RUNNER_OS')=='Linux' and os.environ.get('RUNNER_ARCH')=='X64' and
             bool(os.environ.get('GH_TOKEN')), 'Hosted read-only manual rollup required')
        need(os.environ.get('GITHUB_SHA')==a.source and
             os.environ.get('EXPECTED_SHA')==a.source,
             'Requested SHA is not checkout SHA')
        need(command(['git','-C',str(ROOT),'rev-parse','HEAD'],timeout=12).strip()==a.source,
             'Checked out wrong source')
        source_check()
        ids={name:getattr(a,name+'_run') for name in PATHS}
        with tempfile.TemporaryDirectory(prefix='sfdt11-receipts-',dir=os.environ['RUNNER_TEMP']) as d:
            record=collect(source=a.source,run_ids=ids,temp=Path(d))
        a.output.parent.mkdir(parents=True,exist_ok=True)
        a.output.write_text(json.dumps(record,indent=2,sort_keys=True)+'\n',encoding='utf-8')
        print('SFDT11_CONSOLIDATED_EIGHT_RUN_MEMBER_EVIDENCE_PASS_UNQUALIFIED:',
              a.source[:12],'; cutover=BLOCKED; Web/Desktop native provider=NOT_QUALIFIED')
    except (EvidenceError,OSError,ValueError,TypeError,KeyError,
            json.JSONDecodeError,subprocess.TimeoutExpired) as exc:
        print('SFDT11_CONSOLIDATED_REFUSED:',str(exc)[:360],file=sys.stderr)
        return 2
    return 0

if __name__=='__main__':
    raise SystemExit(main())
