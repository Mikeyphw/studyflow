#!/usr/bin/env python3
"""SFDT20: verify CI receipt, native artifacts and report hashes, fail closed.

Runs only after StudyFlow's normal Devtool report verification. No building,
network calls, signing, release publication or privilege escalation.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

REPO = 'Mikeyphw/studyflow'
DEVTOOL_SHA = 'bee6e0b7e49aaaa0c56f755fd5be434a27c52b6c'
LANES = {
    'phone': {
        'jobs': [('sfdt07-phone-debug', 'android-gradle'),
                 ('sfdt08-phone-release-jvm', 'android-gradle')],
        'junit': 'app/build/test-results/testReleaseUnitTest',
        'apk': 'app/build/outputs/apk/debug/app-debug.apk',
        'lint': 'app/build/reports',
    },
    'tv': {
        'jobs': [('sfdt07-tv-debug', 'android-gradle')],
        'junit': 'app-tv/build/test-results/testDebugUnitTest',
        'apk': 'app-tv/build/outputs/apk/debug/app-tv-debug.apk',
        'lint': 'app-tv/build/reports',
    },
    'desktop': {
        'jobs': [('sfdt19-native-desktop-test', 'jvm')],
        'junit': 'desktopApp/build/test-results/test',
        'output': 'dist/jvm-tests.xml',
    },
    'web': {
        'jobs': [('sfdt19-native-web-test', 'node')],
        'junit': 'web/build/test-results/native-node',
        'pattern': 'web-pytest-junit.xml',
        'output': 'web/dist/test-results.xml',
    },
}
SHA40 = re.compile(r'[0-9a-f]{40}\Z')
SHA256 = re.compile(r'[0-9a-f]{64}\Z')


class Refused(ValueError):
    pass


def need(ok: bool, why: str) -> None:
    if not ok:
        raise Refused(why)


def evidence_file(repo: Path, rel: str, *, low: int = 1,
                  high: int = 500 * 1024 * 1024) -> dict:
    need(bool(rel) and not rel.startswith('/') and '..' not in Path(rel).parts,
         'Unsafe provenance path')
    p = repo / rel
    need(p.is_file() and not p.is_symlink() and p.resolve().is_relative_to(repo.resolve()),
         'Missing or symlinked evidence file: ' + rel)
    size = p.stat().st_size
    need(low <= size <= high, 'Unbounded or empty evidence file: ' + rel)
    with p.open('rb') as f:
        digest = hashlib.file_digest(f, 'sha256').hexdigest()
    return {'path': rel, 'size': size, 'sha256': digest}


def verify_junit(repo: Path, lane: str) -> tuple[list[dict], dict]:
    cfg = LANES[lane]
    rel = cfg['junit']
    directory = repo / rel
    need(directory.is_dir() and not directory.is_symlink()
         and directory.resolve().is_relative_to(repo.resolve()),
         'Unsafe or missing JUnit directory')
    pattern = cfg.get('pattern', 'TEST-*.xml')
    paths = sorted(directory.glob(pattern))
    need(0 < len(paths) <= 256, 'Missing or unbounded JUnit report count')
    records = []
    counts = {'executed': 0, 'failed': 0, 'errors': 0, 'skipped': 0}
    for path in paths:
        relpath = path.relative_to(repo).as_posix()
        evidence = evidence_file(repo, relpath, high=6 * 1024 * 1024)
        try:
            node = ET.parse(path).getroot()
        except (ET.ParseError, OSError) as exc:
            raise Refused('Malformed JUnit: ' + relpath) from exc
        need(node.tag in ('testsuites', 'testsuite'), 'Unexpected JUnit XML root')
        cases = list(node.iter('testcase'))
        need(bool(cases), 'JUnit with no test cases')
        for case in cases:
            counts['executed'] += 1
            counts['failed'] += bool(case.findall('failure'))
            counts['errors'] += bool(case.findall('error'))
            counts['skipped'] += bool(case.findall('skipped'))
        records.append(evidence)
    need(counts['executed'] > counts['skipped'] and not counts['failed'] and not counts['errors'],
         'Failed or empty JUnit test report')
    return records, counts


def manifest(repo: Path, receipt: dict, lane: str, env: dict) -> dict:
    need(lane in LANES, 'Unreviewed CI lane')
    cfg = LANES[lane]
    need(env.get('GITHUB_ACTIONS') == 'true' and env.get('CI') == 'true'
         and env.get('RUNNER_OS') == 'Linux'
         and env.get('RUNNER_ARCH') == 'X64'
         and env.get('GITHUB_REPOSITORY') == REPO,
         'Provenance must be generated in reviewed GitHub-hosted CI')
    sha = env.get('GITHUB_SHA', '')
    need(SHA40.fullmatch(sha) is not None and
         env.get('DEVTOOL_SOURCE_SHA') == DEVTOOL_SHA,
         'Unreviewed source or Devtool pin')
    need(env.get('GITHUB_EVENT_NAME') in ('push', 'pull_request', 'workflow_dispatch'),
         'Unexpected workflow event')
    need(env.get('GITHUB_RUN_ID', '').isdecimal()
         and env.get('GITHUB_RUN_ATTEMPT', '').isdecimal()
         and int(env['GITHUB_RUN_ID']) > 0 and int(env['GITHUB_RUN_ATTEMPT']) > 0,
         'Invalid run identity')
    need(receipt.get('schema') == 'studyflow.devtool.primary.v1'
         and receipt.get('lane') == lane
         and receipt.get('status') == 'NATIVE_CI_LANE_PASSED'
         and receipt.get('ci_lane_passed') is True
         and receipt.get('cutover_authorized') is False
         and receipt.get('signing_qualified') is False
         and receipt.get('release_qualified') is False
         and receipt.get('device_qualified') is False,
         'Unqualified or mismatched native CI receipt')
    ident = receipt.get('identity') or {}
    need(ident.get('studyflow_sha') == sha and ident.get('devtool_sha') == DEVTOOL_SHA
         and ident.get('run_id') == int(env['GITHUB_RUN_ID'])
         and ident.get('run_attempt') == int(env['GITHUB_RUN_ATTEMPT'])
         and ident.get('event') == env['GITHUB_EVENT_NAME'],
         'Native receipt source/runtime/run mismatch')
    nodes = receipt.get('nodes')
    need(isinstance(nodes, list) and len(nodes) == len(cfg['jobs']),
         'Native node inventory incomplete')
    payload = []
    for observed, (expected_job, expected_provider) in zip(nodes, cfg['jobs']):
        need(observed.get('job') == expected_job and
             observed.get('provider') == expected_provider and
             SHA256.fullmatch(observed.get('graph_sha256', '')) is not None and
             SHA256.fullmatch(observed.get('native_receipt_sha256', '')) is not None,
             'Native job or source-bound receipt mismatch')
        evidence = {'job': expected_job, 'provider': expected_provider,
                    'graph_sha256': observed['graph_sha256'],
                    'native_receipt_sha256': observed['native_receipt_sha256']}
        if 'apk' in observed:
            need(observed['apk'].get('path') == cfg.get('apk'), 'Unexpected native APK path')
            found = evidence_file(repo, observed['apk']['path'], low=1024)
            need(found == observed['apk'], 'APK sha256/size mismatch with native Devtool receipt')
            evidence['apk'] = found
        if 'native_output' in observed:
            need(observed['native_output'].get('path') == cfg.get('output'),
                 'Unexpected native language output')
            found = evidence_file(repo, observed['native_output']['path'], high=8*1024*1024)
            need(found == observed['native_output'],
                 'Language output sha256/size mismatch with native Devtool receipt')
            evidence['native_output'] = found
        if expected_provider in ('jvm','node'):
            need('native_output' in evidence, 'Missing language-owned output')
        if cfg.get('apk') and expected_job in ('sfdt07-phone-debug','sfdt07-tv-debug'):
            need('apk' in evidence, 'Missing Android APK proof')
        if observed.get('junit') is not None:
            proof = observed['junit']
            need(isinstance(proof.get('executed'), int) and proof['executed'] > 0
                 and proof.get('failed') == 0 and proof.get('proof') in ('devtool-local-junit','local-XML'),
                 'Native JUnit receipt is missing or unqualified')
            evidence['junit_receipt'] = proof
        elif expected_job not in ('sfdt07-phone-debug',):
            raise Refused('Missing native JUnit node receipt')
        payload.append(evidence)
    reports, counts = verify_junit(repo, lane)
    claim = [x.get('junit', {}).get('executed') for x in nodes if x.get('junit')]
    need(len(claim) == 1 and claim[0] == counts['executed'],
         'Native and XML JUnit counts differ')
    lint = []
    if 'lint' in cfg:
        for suffix in ('xml', 'html', 'sarif'):
            rel = cfg['lint'] + '/lint-results-debug.' + suffix
            if (repo / rel).exists():
                lint.append(evidence_file(repo, rel, high=30*1024*1024))
        need(bool(lint), 'No real Android lint report files')
    return {'schema': 'studyflow.sfdt20.provenance.v1',
            'identity': {'repo': REPO, 'source_sha': sha, 'devtool_sha': DEVTOOL_SHA,
                         'event': ident['event'], 'run_id': ident['run_id'],
                         'run_attempt': ident['run_attempt']},
            'lane': lane, 'nodes': payload,
            'junit': {'files': reports, 'summary': counts},
            'lint': lint,
            'qualified': True,
            'release_qualified': False, 'device_qualified': False, 'signing_qualified': False}


def main(argv=None) -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--lane', required=True, choices=sorted(LANES))
    p.add_argument('--receipt', required=True, type=Path)
    p.add_argument('--out', required=True, type=Path)
    args = p.parse_args(argv)
    try:
        root = Path(__file__).resolve().parents[1]
        temp = Path(os.environ['RUNNER_TEMP']).resolve()
        need(temp.is_dir() and not temp.is_relative_to(root), 'Unsafe runner temp')
        need(args.receipt.resolve() == (temp/'studyflow-devtool-primary/receipt.json').resolve()
             and args.receipt.is_file() and not args.receipt.is_symlink(),
             'Receipt must come from the native CI runner')
        need(args.out.resolve() == (temp/'studyflow-devtool-primary/sfdt20-provenance.json').resolve()
             and not args.out.exists() and not args.out.is_symlink(),
             'Unreviewed or stale provenance destination')
        receipt = json.loads(args.receipt.read_text(encoding='utf-8'))
        result = manifest(root, receipt, args.lane, dict(os.environ))
        with args.out.open('x', encoding='utf-8') as f:
            json.dump(result, f, sort_keys=True, indent=2)
            f.write('\n')
        print('SFDT20_PROVENANCE_PASS:', args.lane, 'junit=', result['junit']['summary']['executed'],
              'artifacts=', len(result['nodes']), flush=True)
        return 0
    except (Refused, OSError, ValueError, KeyError, TypeError) as exc:
        print('SFDT20_PROVENANCE_REFUSED:', str(exc)[:360], file=sys.stderr)
        return 2


if __name__ == '__main__':
    raise SystemExit(main())
