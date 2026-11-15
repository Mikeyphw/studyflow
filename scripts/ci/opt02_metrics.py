#!/usr/bin/env python3
"""OPT02 P03: read-only, exact-run, exact-SHA shared-APK CI measurements.

Measures only an observed duplicate compilation avoided, not a causal cost saving.
No Gradle, app installation, network outside GitHub CLI, Git mutations or GitHub writes.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import re
import subprocess
import sys

REPO = 'Mikeyphw/studyflow'
BRANCH = 'sfdt22-hotfix/hf30-hf04-pr-20261110'
TARGET_WORKFLOW = '.github/workflows/studyflow-targeted.yml'
PRODUCER_WORKFLOW = '.github/workflows/studyflow-sfdt22-physical-apks.yml'
JOB = 'Targeted Android APK and instrumentation compilation'
GATE = 'Targeted required-result gate'
BUILDER = 'Build APK pair — NO emulator, NO device, NO release'
REUSE = 'Reuse independently verified exact-source signed APK pair when available'
COMPILE = 'Compile Android debug and AndroidTest APKs, no emulator/release'
VERIFY = 'Verify actual debug-only outputs from shared artifact or hosted Gradle'
BUILDER_STEP = 'Build matching debuggable app plus real androidTest APK, no emulator'
HIT = 'OPT02_SHARED_APKS_HIT EXACT_SHA_SIGNED_APK_PAIR_VERIFIED'
MISS = 'OPT02_SHARED_APKS_MISS'
REFUSED = 'OPT02_SHARED_APKS_REFUSED'
SHA40 = re.compile(r'[0-9a-f]{40}\Z')

class Refused(Exception):
    pass

def need(ok: bool, reason: str) -> None:
    if not ok:
        raise Refused(reason)

def run_id(value: int) -> int:
    need(type(value) is int and 0 < value <= 10**15, 'RUN_ID_INVALID')
    return value

def gh_json(path: str, *, cap: int = 4_000_000) -> dict:
    need(path.startswith('repos/' + REPO + '/actions/') and '\n' not in path,
         'GH_API_PATH_REFUSED')
    try:
        p = subprocess.run(['gh', 'api', '--method', 'GET', path],
                           stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                           timeout=90, check=False)
    except (OSError, subprocess.TimeoutExpired):
        raise Refused('GH_API_UNAVAILABLE_OR_TIMEOUT') from None
    need(p.returncode == 0, 'GH_API_NONZERO')
    need(len(p.stdout) <= cap, 'GH_API_OVERSIZE')
    try:
        obj = json.loads(p.stdout)
    except (ValueError, UnicodeError):
        raise Refused('GH_API_JSON_INVALID') from None
    need(type(obj) is dict, 'GH_API_TYPE_INVALID')
    return obj

def gh_job_log(job_id: int) -> str:
    """Fetch bounded raw log internally; print only predetermined evidence markers."""
    job_id = run_id(job_id)
    try:
        p = subprocess.run(['gh', 'api', '--method', 'GET',
                            f'repos/{REPO}/actions/jobs/{job_id}/logs'],
                           stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                           timeout=100, check=False)
    except (OSError, subprocess.TimeoutExpired):
        raise Refused('GH_JOB_LOG_UNAVAILABLE_OR_TIMEOUT') from None
    need(p.returncode == 0 and 0 < len(p.stdout) <= 8_000_000,
         'GH_JOB_LOG_UNAVAILABLE_OR_OVERSIZE')
    return p.stdout.decode('utf-8', errors='replace')

def validate_run(obj: dict, expected_id: int, workflow: str, event: str) -> str:
    need(type(obj) is dict and obj.get('id') == run_id(expected_id),
         'RUN_METADATA_ID_MISMATCH')
    need(obj.get('path') == workflow and obj.get('event') == event,
         'RUN_WORKFLOW_OR_EVENT_MISMATCH')
    need(obj.get('head_branch') == BRANCH and
         isinstance(obj.get('repository'), dict) and
         obj['repository'].get('full_name') == REPO and
         isinstance(obj.get('head_repository'), dict) and
         obj['head_repository'].get('full_name') == REPO,
         'RUN_REPOSITORY_OR_BRANCH_MISMATCH')
    sha = obj.get('head_sha')
    need(isinstance(sha, str) and SHA40.fullmatch(sha) is not None,
         'RUN_HEAD_SHA_INVALID')
    need(obj.get('status') == 'completed' and obj.get('conclusion') == 'success',
         'RUN_NOT_SUCCESSFUL')
    need(type(obj.get('run_attempt')) is int and obj['run_attempt'] > 0,
         'RUN_ATTEMPT_INVALID')
    return sha

def find_job(jobs: dict, title: str) -> dict:
    need(isinstance(jobs, dict) and type(jobs.get('jobs')) is list and
         len(jobs['jobs']) <= 100 and len(jobs['jobs']) > 0,
         'JOBS_RESPONSE_INVALID')
    found = [j for j in jobs['jobs'] if isinstance(j, dict) and j.get('name') == title]
    need(len(found) == 1 and type(found[0].get('id')) is int and
         found[0].get('conclusion') == 'success',
         'JOB_MISSING_DUPLICATE_OR_FAILED_' + re.sub('[^A-Z_]', '_', title.upper())[:50])
    return found[0]

def step(job: dict, name: str, conclusion: str) -> dict:
    steps = job.get('steps')
    need(type(steps) is list and len(steps) <= 200, 'JOB_STEPS_INVALID')
    hits = [s for s in steps if isinstance(s, dict) and s.get('name') == name]
    need(len(hits) == 1 and hits[0].get('conclusion') == conclusion,
         'STEP_CONCLUSION_MISMATCH_' + re.sub('[^A-Z_]', '_', name.upper())[:50])
    return hits[0]

def dt(value: str) -> datetime:
    need(isinstance(value, str) and bool(re.fullmatch(r'\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z', value)),
         'TIMESTAMP_INVALID')
    try:
        return datetime.strptime(value, '%Y-%m-%dT%H:%M:%SZ').replace(tzinfo=timezone.utc)
    except ValueError:
        raise Refused('TIMESTAMP_INVALID') from None

def duration_seconds(obj: dict) -> int:
    start, end = dt(obj.get('started_at')), dt(obj.get('completed_at'))
    value = int((end - start).total_seconds())
    need(0 <= value <= 12*3600, 'DURATION_INVALID')
    return value

def validate_artifacts(doc: dict, producer_run_id: int, attempt: int) -> dict:
    items = doc.get('artifacts')
    need(type(items) is list and 0 < len(items) <= 100,
         'ARTIFACTS_MISSING_OR_INVALID')
    name = f'sfdt22-physical-apks-{producer_run_id}-{attempt}'
    found = [a for a in items if isinstance(a, dict) and a.get('name') == name]
    need(len(found) == 1 and type(found[0].get('id')) is int and
         found[0]['id'] > 0 and found[0].get('expired') is False and
         type(found[0].get('size_in_bytes')) is int and found[0]['size_in_bytes'] > 4096,
         'EXACT_ARTIFACT_INVALID_OR_AMBIGUOUS')
    return found[0]

def analyze(*, baseline: dict, targeted: dict, producer: dict,
            baseline_jobs: dict, targeted_jobs: dict, producer_jobs: dict,
            producer_artifacts: dict, consumer_log: str,
            baseline_run_id: int, target_run_id: int, producer_run_id: int) -> dict:
    """Pure, deterministic analysis: no network, shell, files or device access."""
    base_sha = validate_run(baseline, baseline_run_id, TARGET_WORKFLOW, 'pull_request')
    exact_sha = validate_run(targeted, target_run_id, TARGET_WORKFLOW, 'pull_request')
    producer_sha = validate_run(producer, producer_run_id, PRODUCER_WORKFLOW, 'push')
    need(exact_sha == producer_sha, 'PRODUCER_CONSUMER_SHA_MISMATCH')
    need(base_sha != exact_sha, 'BASELINE_MUST_BE_DISTINCT_COMMIT')
    base_job = find_job(baseline_jobs, JOB)
    consumer = find_job(targeted_jobs, JOB)
    find_job(targeted_jobs, GATE)
    prod_job = find_job(producer_jobs, BUILDER)
    step(base_job, COMPILE, 'success')
    step(consumer, REUSE, 'success')
    step(consumer, COMPILE, 'skipped')
    step(consumer, VERIFY, 'success')
    step(prod_job, BUILDER_STEP, 'success')
    artifact = validate_artifacts(producer_artifacts, producer_run_id, producer['run_attempt'])
    need(isinstance(consumer_log, str) and 0 < len(consumer_log) <= 8_000_000,
         'CONSUMER_LOG_INVALID')
    need(consumer_log.count(HIT) == 1 and MISS not in consumer_log and
         REFUSED not in consumer_log, 'EXACT_HIT_LOG_MARKER_MISSING_OR_AMBIGUOUS')
    download_match = re.findall(r'OPT02_SHARED_APKS_INFO DOWNLOAD_PASS attempts=([1-6])\b', consumer_log)
    need(len(download_match) == 1, 'DOWNLOAD_PASS_MARKER_INVALID')
    baseline_job_s = duration_seconds(base_job)
    current_job_s = duration_seconds(consumer)
    baseline_compile_s = duration_seconds(step(base_job, COMPILE, 'success'))
    wait_s = duration_seconds(step(consumer, REUSE, 'success'))
    saved_s = baseline_job_s - current_job_s
    return {
        'schema': 1, 'kind': 'OPT02_P03_READ_ONLY_OBSERVED_METRICS', 'verification': 'PASS',
        'repository': REPO, 'branch': BRANCH,
        'baseline': {'run_id': baseline_run_id, 'source_sha': base_sha,
                     'android_job_seconds': baseline_job_s,
                     'android_gradle_compile_seconds': baseline_compile_s},
        'current': {'run_id': target_run_id, 'source_sha': exact_sha,
                    'android_job_seconds': current_job_s,
                    'reuse_step_seconds': wait_s,
                    'gradle_compilation': 'skipped',
                    'download_attempts': int(download_match[0]),
                    'verified_reuse_log': HIT},
        'producer': {'run_id': producer_run_id, 'source_sha': producer_sha,
                     'artifact_id': artifact['id'],
                     'artifact_name': artifact['name'],
                     'artifact_size_bytes': artifact['size_in_bytes']},
        'observation': {'duplicate_android_gradle_compilations_avoided': 1,
                        'job_wall_time_delta_seconds': saved_s,
                        'job_wall_time_delta_percent': round(100*saved_s/baseline_job_s, 1)
                            if baseline_job_s > 0 else 0},
        'qualifications': {'physical_device_qualified_by_report': False,
                           'release_qualified_by_report': False,
                           'publication_allowed_by_report': False},
        'limitations': [
            'Cross-commit wall-clock comparison is observational, not a causal estimate.',
            'Waiting for the existing producer still consumes Targeted CI runner time.',
            'This report checks GitHub success states and HIT logs; APK byte and signer verification occurs in the consumer, not in this auditor.',
            'SFDT21 uses PR merge SHA and is not eligible for producer APK reuse without a same-commit proof.',
        ],
    }

def collect(baseline_run_id: int, target_run_id: int, producer_run_id: int) -> dict:
    ids = list(map(run_id, (baseline_run_id, target_run_id, producer_run_id)))
    need(len(set(ids)) == 3, 'RUN_IDS_MUST_BE_DISTINCT')
    runs = [gh_json(f'repos/{REPO}/actions/runs/{id}') for id in ids]
    jobs = [gh_json(f'repos/{REPO}/actions/runs/{id}/jobs?per_page=100') for id in ids]
    targeted_job = find_job(jobs[1], JOB)
    artifacts = gh_json(f'repos/{REPO}/actions/runs/{ids[2]}/artifacts?per_page=100')
    log = gh_job_log(targeted_job['id'])
    return analyze(baseline=runs[0], targeted=runs[1], producer=runs[2],
                   baseline_jobs=jobs[0], targeted_jobs=jobs[1], producer_jobs=jobs[2],
                   producer_artifacts=artifacts, consumer_log=log,
                   baseline_run_id=ids[0], target_run_id=ids[1], producer_run_id=ids[2])

def main() -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--baseline-run', type=int, required=True)
    p.add_argument('--targeted-run', type=int, required=True)
    p.add_argument('--producer-run', type=int, required=True)
    p.add_argument('--output', type=Path, help='Optional local JSON report, never GitHub/device write')
    args = p.parse_args()
    try:
        report = collect(args.baseline_run, args.targeted_run, args.producer_run)
        raw = json.dumps(report, indent=2, sort_keys=True, ensure_ascii=False) + '\n'
        if args.output is not None:
            need(not args.output.is_symlink() and
                 (not args.output.exists() or args.output.is_file()), 'OUTPUT_PATH_INVALID')
            args.output.write_text(raw, encoding='utf-8')
        print('OPT02_P03_METRICS_PASS verified_hit=true gradle_skipped=true ' +
              'baseline_compile_seconds=' + str(report['baseline']['android_gradle_compile_seconds']) +
              ' observed_job_delta_seconds=' + str(report['observation']['job_wall_time_delta_seconds']))
        if args.output is None:
            print(raw, end='')
        return 0
    except Refused as e:
        print('OPT02_P03_METRICS_REFUSED ' + str(e), file=sys.stderr)
        return 2
    except (OSError, UnicodeError, ValueError):
        print('OPT02_P03_METRICS_REFUSED LOCAL_IO_OR_PARSE', file=sys.stderr)
        return 2

if __name__ == '__main__':
    raise SystemExit(main())
