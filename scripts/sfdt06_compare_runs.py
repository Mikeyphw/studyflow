#!/usr/bin/env python3
"""Read-only SFDT-06 same-commit hosted shadow vs. legacy CI status audit.

Only GitHub run/job metadata is compared. This does NOT attest tests, build
artifacts, APK authenticity, signing, deep coverage, or native equivalence.
All GitHub requests use `gh api` with read-only GET, without modifying files.
"""
from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from typing import Any, Callable

SHA = re.compile(r'[0-9a-f]{40}\Z')
REPO = re.compile(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+\Z')
SHADOW = '.github/workflows/studyflow-devtool-shadow.yml'
LEGACY = '.github/workflows/studyflow-ci.yml'


class ComparisonError(ValueError):
    pass


def require(ok: bool, message: str) -> None:
    if not ok:
        raise ComparisonError(message)


def _list_jobs(payload: dict[str, Any]) -> list[dict[str, Any]]:
    require(isinstance(payload, dict) and isinstance(payload.get('jobs'), list), 'Missing GitHub jobs inventory')
    jobs = payload['jobs']
    require(len(jobs) < 100 and all(isinstance(j, dict) for j in jobs), 'Unbounded or malformed job inventory')
    require(payload.get('total_count') == len(jobs), 'GitHub jobs pagination is incomplete; refusing optimistic PASS')
    return jobs


def compare(shadow: dict[str, Any], legacy: dict[str, Any], shadow_jobs: dict[str, Any],
            legacy_jobs: dict[str, Any], *, expected_repo: str = 'Mikeyphw/studyflow') -> dict[str, Any]:
    require(REPO.fullmatch(expected_repo) is not None, 'Unsafe repository identity')
    for name, run, path in (('shadow', shadow, SHADOW), ('legacy', legacy, LEGACY)):
        require(isinstance(run, dict), f'{name} run record missing')
        require(run.get('path') == path, f'{name} workflow identity mismatch')
        require(run.get('repository', {}).get('full_name') == expected_repo, f'{name} repository mismatch')
        require(isinstance(run.get('head_sha'), str) and SHA.fullmatch(run['head_sha']) is not None,
                f'{name} source commit missing')
        require(type(run.get('id')) is int and run['id'] > 0, f'{name} run ID missing')
        require(type(run.get('run_attempt')) is int and run['run_attempt'] > 0,
                f'{name} attempt missing')
        require(run.get('status') == 'completed', f'{name} run is not terminal')
        require(run.get('event') in ('push', 'workflow_dispatch'), f'{name} workflow has unreviewed trigger')
    require(shadow['event'] == 'workflow_dispatch', 'Native shadow must be explicit dispatch')
    require(shadow['head_sha'] == legacy['head_sha'], 'Different commits; no same-SHA comparison possible')
    sjobs = _list_jobs(shadow_jobs)
    ljobs = _list_jobs(legacy_jobs)
    for name, run, jobs in (('shadow', shadow, sjobs), ('legacy', legacy, ljobs)):
        for job in jobs:
            require(job.get('run_id') == run['id'], f'{name} job belongs to another run')
            if 'head_sha' in job:
                require(job['head_sha'] == run['head_sha'], f'{name} job has another source SHA')
    native = [j for j in sjobs if j.get('name') == 'native-source-shadow']
    require(len(sjobs) == 1 and len(native) == 1, 'Native shadow job identity/count drifted')
    source = [j for j in ljobs if j.get('name') == 'Actions integration source contract']
    web = [j for j in ljobs if j.get('name') == 'web']
    desktop = [j for j in ljobs if j.get('name') == 'desktop']
    android = [j for j in ljobs if isinstance(j.get('name'), str) and
               (j['name'] == 'android' or j['name'].startswith('android ('))]
    require(len(source) == 1 and len(web) == 1 and len(desktop) == 1 and len(android) == 2,
            'Legacy CI source/web/desktop/Android job inventory missing')
    def state(j: dict[str, Any]) -> str:
        return j.get('conclusion') if j.get('status') == 'completed' else 'NOT_TERMINAL'
    scopes = {'source': [source[0]], 'web': web, 'desktop': desktop, 'android': android}
    reports = {name: {'conclusions': [state(j) for j in rows],
                      'passed': all(state(j) == 'success' for j in rows)}
               for name, rows in scopes.items()}
    native_green = shadow.get('conclusion') == 'success' and state(native[0]) == 'success'
    fast_green = legacy.get('conclusion') == 'success' and all(x['passed'] for x in reports.values())
    return {'schema_version': 1, 'kind': 'studyflow.sfdt06.same-sha-comparison',
            'source_sha': shadow['head_sha'], 'same_source_sha': True,
            'native_shadow_run': shadow['id'], 'native_shadow_attempt': shadow['run_attempt'],
            'legacy_ci_run': legacy['id'], 'legacy_ci_attempt': legacy['run_attempt'],
            'native_source_shadow_passed': native_green,
            'legacy_fast_passed': fast_green, 'legacy_scopes': reports,
            'status': ('SOURCE_SHADOW_AND_LEGACY_FAST_GREEN' if native_green and fast_green
                       else 'INCOMPLETE_OR_FAILED'),
            'native_source_tasks_equivalent': False,
            'web_native_equivalent': False, 'gradle_native_equivalent': False,
            'deep_profiles_qualified': False, 'device_qualified': False,
            'release_qualified': False, 'cutover_authorized': False,
            'warning': 'GitHub status metadata alone cannot prove native task/test/artifact equivalence.'}


def _fetch(endpoint: str) -> dict[str, Any]:
    try:
        p = subprocess.run(['gh', 'api', '--method', 'GET', endpoint], capture_output=True,
                           text=True, timeout=30, check=False)
    except (OSError, subprocess.TimeoutExpired) as exc:
        raise ComparisonError('GitHub CLI read-only request unavailable or timed out') from exc
    if p.returncode:
        raise ComparisonError('GitHub read-only query failed; check gh auth and run IDs')
    try:
        data = json.loads(p.stdout)
    except (ValueError, UnicodeError) as exc:
        raise ComparisonError('Malformed GitHub JSON response') from exc
    require(isinstance(data, dict), 'Expected GitHub object response')
    return data


def main(argv: list[str] | None = None, *, fetch: Callable[[str], dict[str, Any]] = _fetch) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--repo', default='Mikeyphw/studyflow')
    ap.add_argument('--shadow-run', type=int, required=True)
    ap.add_argument('--legacy-run', type=int, required=True)
    ap.add_argument('--json', action='store_true')
    args = ap.parse_args(argv)
    try:
        require(REPO.fullmatch(args.repo) is not None, 'Unsafe repository identity')
        require(args.shadow_run > 0 and args.legacy_run > 0 and args.shadow_run != args.legacy_run,
                'Need two distinct positive run IDs')
        base = '/repos/' + args.repo + '/actions/runs/'
        s = fetch(base + str(args.shadow_run))
        l = fetch(base + str(args.legacy_run))
        sr = fetch(base + str(args.shadow_run) + '/jobs?per_page=100')
        lr = fetch(base + str(args.legacy_run) + '/jobs?per_page=100')
        require(s.get('id') == args.shadow_run and l.get('id') == args.legacy_run,
                'Fetched run IDs do not match request')
        record = compare(s, l, sr, lr, expected_repo=args.repo)
    except ComparisonError as exc:
        print('SFDT06_COMPARE_REFUSED: ' + str(exc), file=sys.stderr)
        return 2
    if args.json:
        print(json.dumps(record, sort_keys=True, indent=2))
    else:
        print('SFDT06_SAME_SHA_' + ('BOTH_GREEN' if record['native_source_shadow_passed'] and record['legacy_fast_passed']
                                   else 'NOT_QUALIFIED') + ': ' + record['source_sha'][:12])
        for name, row in record['legacy_scopes'].items():
            print(f'  {name}: {", ".join(row["conclusions"])}')
        print('  deep=NOT_QUALIFIED, native_equivalence=NOT_QUALIFIED, cutover=BLOCKED')
    return 0 if record['native_source_shadow_passed'] and record['legacy_fast_passed'] else 1


if __name__ == '__main__':
    raise SystemExit(main())
