#!/usr/bin/env python3
"""Read-only, same-SHA three-workflow comparison: legacy, source shadow, expanded.

A green GitHub job is not independent test or package attestation. The actual
JUnit/debug receipts must still be separately inspected before cutover.
"""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import tempfile
import re
import subprocess
import sys
from typing import Any, Callable

SHA = re.compile(r'[0-9a-f]{40}\Z')
REPO = re.compile(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+\Z')
PATHS = {
    'legacy': '.github/workflows/studyflow-ci.yml',
    'source': '.github/workflows/studyflow-devtool-shadow.yml',
    'expanded': '.github/workflows/studyflow-devtool-native-expanded.yml',
}
LANES = {'native-phone-debug', 'native-tv-debug', 'native-web-pytest', 'native-quality-source'}


class ComparisonError(ValueError):
    pass


def check(ok: bool, message: str) -> None:
    if not ok:
        raise ComparisonError(message)


def _jobs(blob: dict) -> list[dict]:
    check(isinstance(blob, dict) and isinstance(blob.get('jobs'), list), 'Missing workflow jobs')
    rows = blob['jobs']
    check(len(rows) <= 100 and blob.get('total_count') == len(rows),
          'Missing or paginated job records; cannot claim complete run')
    check(all(isinstance(j, dict) for j in rows), 'Invalid jobs data')
    return rows


def _artifacts(blob: dict) -> list[dict]:
    check(isinstance(blob, dict) and isinstance(blob.get('artifacts'), list), 'Missing artifacts inventory')
    rows = blob['artifacts']
    check(len(rows) <= 100 and blob.get('total_count') == len(rows),
          'Artifacts pagination incomplete or missing')
    check(all(isinstance(a, dict) for a in rows), 'Malformed artifact inventory')
    return rows


def compare(runs: dict[str, dict], jobs: dict[str, dict], artifacts: dict,
            *, repository: str = 'Mikeyphw/studyflow') -> dict:
    check(REPO.fullmatch(repository) is not None, 'Unsafe repository')
    for kind, path in PATHS.items():
        run = runs.get(kind)
        check(isinstance(run, dict) and run.get('path') == path,
              'Wrong ' + kind + ' workflow identity')
        check(run.get('repository', {}).get('full_name') == repository,
              'Wrong ' + kind + ' repository')
        check(type(run.get('id')) is int and run['id'] > 0 and
              type(run.get('run_attempt')) is int and run['run_attempt'] > 0 and
              isinstance(run.get('head_sha'), str) and SHA.fullmatch(run['head_sha']) is not None,
              'Missing ' + kind + ' source/run/attempt identity')
        check(run.get('status') == 'completed', kind + ' run is not terminal')
        check(run.get('event') == ('workflow_dispatch' if kind != 'legacy' else run.get('event')) and
              run.get('event') in ('push', 'workflow_dispatch'),
              'Unreviewed dispatch trigger')
    source = runs['legacy']['head_sha']
    check(all(x['head_sha'] == source for x in runs.values()),
          'Workflow runs are not on the same exact Git commit')
    rows = {name: _jobs(jobs.get(name)) for name in PATHS}
    for name, members in rows.items():
        check(all(j.get('run_id') == runs[name]['id'] and
                  ('head_sha' not in j or j['head_sha'] == source) for j in members),
              'Job source/run identity mismatch: ' + name)
    def one(kind: str, name: str) -> dict:
        matched = [j for j in rows[kind] if j.get('name') == name]
        check(len(matched) == 1, 'Missing/duplicate job ' + kind + ': ' + name)
        return matched[0]
    check(len(rows['source']) == 1, 'Source shadow must contain one native job')
    source_job = one('source', 'native-source-shadow')
    check({j.get('name') for j in rows['expanded']} == LANES and len(rows['expanded']) == 4,
          'Expanded shadow matrix missing/replaced native lanes')
    source_ci = one('legacy', 'Actions integration source contract')
    web = one('legacy', 'web')
    desktop = one('legacy', 'desktop')
    android = [j for j in rows['legacy'] if isinstance(j.get('name'), str) and
               j['name'].startswith('android (')]
    check(len(android) == 2 and any('(app,' in j['name'] for j in android) and
          any('(app-tv,' in j['name'] for j in android),
          'Both phone and TV legacy matrix jobs are required')
    relevant = [source_ci, web, desktop, *android]
    def success(j: dict) -> bool:
        return j.get('status') == 'completed' and j.get('conclusion') == 'success'
    existing = runs['legacy'].get('conclusion') == 'success' and all(map(success, relevant))
    shadow = runs['source'].get('conclusion') == 'success' and success(source_job)
    native = runs['expanded'].get('conclusion') == 'success' and all(map(success, rows['expanded']))
    recorded = _artifacts(artifacts)
    evidence = {}
    for lane in sorted(LANES):
        short = lane.removeprefix('native-')
        label = f'studyflow-sfdt07-{short}-{runs["expanded"]["id"]}-{runs["expanded"]["run_attempt"]}'
        found = [a for a in recorded if a.get('name') == label]
        check(len(found) == 1 and not found[0].get('expired') and
              type(found[0].get('size_in_bytes')) is int and found[0]['size_in_bytes'] > 0 and
              re.fullmatch(r'sha256:[0-9a-f]{64}', found[0].get('digest', '')) is not None,
              'Expanded evidence artifact missing/expired/ambiguous: ' + short)
        evidence[short] = {'artifact_id': found[0].get('id'),
                           'transfer_digest': found[0]['digest'],
                           'member_evidence_verified': False}
    return {'schema_version': 1, 'kind': 'studyflow.sfdt07.three-run-comparison',
            'source_sha': source, 'same_sha': True,
            'legacy_run_id': runs['legacy']['id'], 'source_shadow_run_id': runs['source']['id'],
            'expanded_shadow_run_id': runs['expanded']['id'],
            'legacy_fast_passed': existing, 'source_shadow_passed': shadow,
            'expanded_shadow_passed': native,
            'legacy_job_results': {j['name']: j['conclusion'] for j in relevant},
            'native_lane_results': {j['name']: j['conclusion'] for j in rows['expanded']},
            'evidence_artifacts': evidence,
            'status': ('ALL_THREE_HOSTED_RUNS_GREEN_PARTIAL_COVERAGE' if existing and shadow and native
                       else 'INCOMPLETE_OR_FAILED'),
            'web_npm_pipeline_equivalent': False, 'desktop_gradle_equivalent': False,
            'phone_release_jvm_equivalent': False, 'quality_gradle_equivalent': False,
            'parity_gradle_equivalent': False, 'deep_profiles_qualified': False,
            'member_artifacts_authenticated': False, 'device_qualified': False,
            'release_qualified': False, 'cutover_authorized': False,
            'warning': 'Metadata and artifact transfer digests do not authenticate native JUnit/APK members or prove equivalence.'}



def download_member_artifacts(repository: str, run_id: int, attempt: int,
                              source_sha: str, names: dict[str, str]) -> dict[str, dict]:
    """Download four named GitHub artifacts using read-only gh into isolated temp dirs.

    Validate member bytes against frozen native receipts; not an attestation of
    GitHub's producer or signed APK provenance. No files touch the checkout.
    """
    from sfdt07_member_evidence import verify_members, MemberError
    checked: dict[str, dict] = {}
    # Per-lane isolation prevents one artifact supplying another lane's receipts.
    with tempfile.TemporaryDirectory(prefix='sfdt07-member-review-') as base:
        for lane, artifact_name in sorted(names.items()):
            folder=Path(base)/lane
            folder.mkdir()
            try:
                proc=subprocess.run(['gh', 'run', 'download', str(run_id), '-R', repository,
                                     '-n', artifact_name, '-D', str(folder)],
                                    capture_output=True, text=True, timeout=240,
                                    check=False,
                                    env={**os.environ,'GH_PROMPT_DISABLED':'1'})
            except (OSError,subprocess.TimeoutExpired) as exc:
                raise ComparisonError('Member evidence download unavailable for '+lane) from exc
            check(proc.returncode==0, 'Cannot fetch complete member artifact: '+lane)
            # Reject unexpected member trees, symlink inserts and out-of-bounds
            # aggregate sizes before touching any file content.
            total=0
            for path in folder.rglob('*'):
                check(not path.is_symlink(), 'Symlinked GitHub artifact entry: '+lane)
                if path.is_file():
                    total+=path.stat().st_size
                    check(total<=180*1024*1024, 'Expanded native evidence exceeds per-lane byte cap')
            try:
                checked[lane]=verify_members(folder,lane,source_sha=source_sha,
                                              run_id=run_id,attempt=attempt)
            except (MemberError,OSError,ValueError,TypeError,KeyError) as exc:
                raise ComparisonError('Native member verification rejected '+lane+': '+str(exc)[:200]) from exc
    return checked

def fetch(endpoint: str) -> dict[str, Any]:
    try:
        result = subprocess.run(['gh', 'api', '--method', 'GET', endpoint],
                                capture_output=True, text=True, check=False, timeout=35)
    except (OSError, subprocess.TimeoutExpired) as exc:
        raise ComparisonError('GitHub CLI unavailable or timed out') from exc
    check(result.returncode == 0, 'GitHub read-only API request failed')
    try:
        obj = json.loads(result.stdout)
    except (ValueError, UnicodeError) as exc:
        raise ComparisonError('Malformed GitHub JSON') from exc
    check(isinstance(obj, dict), 'GitHub API returned nonobject')
    return obj


def main(argv: list[str] | None = None, *, get: Callable[[str], dict] = fetch) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--repo', default='Mikeyphw/studyflow')
    ap.add_argument('--legacy-run', type=int, required=True)
    ap.add_argument('--source-shadow-run', type=int, required=True)
    ap.add_argument('--expanded-run', type=int, required=True)
    ap.add_argument('--json', action='store_true')
    ap.add_argument('--metadata-only', action='store_true',
                    help='Skip downloaded member inspection; explicitly unqualified')
    opts = ap.parse_args(argv)
    try:
        check(REPO.fullmatch(opts.repo) is not None, 'Invalid repository')
        ids = {'legacy': opts.legacy_run, 'source': opts.source_shadow_run,
               'expanded': opts.expanded_run}
        check(all(x > 0 for x in ids.values()) and len(set(ids.values())) == 3,
              'Three distinct positive run IDs required')
        base = '/repos/' + opts.repo + '/actions/runs/'
        runs = {kind: get(base + str(runid)) for kind, runid in ids.items()}
        jobs = {kind: get(base + str(runid) + '/jobs?per_page=100')
                for kind, runid in ids.items()}
        for kind in ids:
            check(runs[kind].get('id') == ids[kind], 'Wrong GitHub run response')
        arts = get(base + str(opts.expanded_run) + '/artifacts?per_page=100')
        record = compare(runs, jobs, arts, repository=opts.repo)
        record['member_evidence_verified'] = False
        record['evidence_verification'] = 'METADATA_ONLY_UNQUALIFIED'
        complete = all((record['legacy_fast_passed'], record['source_shadow_passed'],
                        record['expanded_shadow_passed']))
        if complete and not opts.metadata_only:
            names = {lane: f'studyflow-sfdt07-{lane}-{opts.expanded_run}-'
                            f'{runs["expanded"]["run_attempt"]}'
                     for lane in ('phone-debug','tv-debug','web-pytest','quality-source')}
            verified=download_member_artifacts(opts.repo,opts.expanded_run,
                    runs['expanded']['run_attempt'],record['source_sha'],names)
            record['member_evidence_verified'] = True
            record['evidence_verification'] = 'NATIVE_MEMBER_BYTES_VERIFIED_UNQUALIFIED'
            record['member_details']=verified
            for lane in verified:
                record['evidence_artifacts'][lane]['member_evidence_verified'] = True
        elif complete:
            record['status']='ALL_THREE_HOSTED_RUNS_GREEN_METADATA_ONLY_UNQUALIFIED'
    except ComparisonError as exc:
        print('SFDT07_COMPARE_REFUSED: ' + str(exc), file=sys.stderr)
        return 2
    if opts.json:
        print(json.dumps(record, sort_keys=True, indent=2))
    else:
        print('SFDT07_COMPARE: ' + record['status'] + ' ' + record['source_sha'][:12])
        print('  evidence=' + record['evidence_verification'] + '; cutover=BLOCKED; release/device/deep=NOT_QUALIFIED')
    return 0 if all((record['legacy_fast_passed'], record['source_shadow_passed'],
                     record['expanded_shadow_passed'])) else 1


if __name__ == '__main__':
    raise SystemExit(main())
