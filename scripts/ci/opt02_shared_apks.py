#!/usr/bin/env python3
"""OPT02 P02: coordinated reuse of exact-source signed APK pair in targeted CI.

Fail-closed immutable artifact verification. A producer that is absent, pending,
failed, or expired is a cache MISS: ordinary hosted Gradle compilation remains
mandatory. A successful producer with ambiguous, tampered or malformed evidence
is an ERROR, never a cache hit or silent fallback. No tests are skipped.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import time

REPO = 'Mikeyphw/studyflow'
PRODUCER = 'studyflow-sfdt22-physical-apks.yml'
BRANCH = 'sfdt22-hotfix/hf30-hf04-pr-20261110'
APP = 'app/build/outputs/apk/debug/app-debug.apk'
TEST = 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'
SHA40 = re.compile(r'[0-9a-f]{40}\Z')
SHA256 = re.compile(r'[0-9a-f]{64}\Z')
CERT_PATTERN = re.compile(r'(?m)^Signer #1 certificate SHA-256 digest: ([0-9a-fA-F]{64})\r?$')
EXPECTED_KEYS = frozenset({'SFDT22_PHYSICAL_APKS.json', 'app-debug.apk', 'app-debug-androidTest.apk'})

class Refused(Exception):
    pass

def need(ok: bool, reason: str) -> None:
    if not ok:
        raise Refused(reason)

def command(argv: list[str], *, limit: int = 180_000, timeout: int = 45) -> bytes:
    try:
        run = subprocess.run(argv, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                             timeout=timeout, check=False)
    except (OSError, subprocess.TimeoutExpired):
        raise Refused('COMMAND_UNAVAILABLE_OR_TIMEOUT') from None
    need(run.returncode == 0 and len(run.stdout) <= limit, 'COMMAND_FAILURE')
    return run.stdout

def github_json(path: str) -> dict:
    raw = command(['gh', 'api', '--method', 'GET', path], limit=250_000)
    try:
        value = json.loads(raw)
    except (UnicodeError, ValueError):
        raise Refused('GH_JSON_INVALID') from None
    need(isinstance(value, dict), 'GH_RESPONSE_INVALID')
    return value

def select_run(document: dict, source: str) -> dict | None:
    """Only a successful, same-repo, reviewed branch push may supply APKs."""
    need(SHA40.fullmatch(source) is not None, 'SOURCE_SHA_INVALID')
    runs = document.get('workflow_runs')
    need(isinstance(runs, list) and len(runs) <= 40, 'RUN_LIST_INVALID')
    matched = []
    for run in runs:
        need(isinstance(run, dict), 'RUN_RECORD_INVALID')
        if run.get('head_sha') != source:
            continue
        # A manual dispatch at this same SHA is not an eligible producer.
        # Ignore it; do not let its presence shadow a valid push build.
        if run.get('event') != 'push' or run.get('head_branch') != BRANCH:
            continue
        repository = run.get('repository')
        need(isinstance(repository, dict) and repository.get('full_name') == REPO,
             'PRODUCER_REPOSITORY_MISMATCH')
        need(isinstance(run.get('id'), int) and run['id'] > 0 and
             isinstance(run.get('run_attempt'), int) and run['run_attempt'] > 0,
             'PRODUCER_RUN_ID_INVALID')
        if run.get('status') == 'completed' and run.get('conclusion') == 'success':
            matched.append(run)
    return max(matched, key=lambda x: (x['id'], x['run_attempt'])) if matched else None

def select_artifact(document: dict, run: dict) -> dict | None:
    artifacts = document.get('artifacts')
    need(isinstance(artifacts, list) and len(artifacts) <= 100, 'ARTIFACT_LIST_INVALID')
    wanted = f"sfdt22-physical-apks-{run['id']}-{run['run_attempt']}"
    relevant = [x for x in artifacts if isinstance(x, dict) and x.get('name') == wanted]
    need(len(relevant) == 1, 'ARTIFACT_AMBIGUOUS_OR_MISSING')
    item = relevant[0]
    need(isinstance(item.get('id'), int) and item['id'] > 0 and
         isinstance(item.get('expired'), bool), 'ARTIFACT_METADATA_INVALID')
    return None if item['expired'] else item

def sha(path: Path) -> str:
    need(path.is_file() and not path.is_symlink() and
         1024 <= path.stat().st_size <= 90_000_000, 'APK_FILE_INVALID')
    with path.open('rb') as f:
        return hashlib.file_digest(f, 'sha256').hexdigest()

def signer(apk: Path, android_home: Path) -> str:
    tool = android_home / 'build-tools/36.0.0/apksigner'
    need(tool.is_file() and not tool.is_symlink(), 'PINNED_APKSIGNER_MISSING')
    data = command([str(tool), 'verify', '--print-certs', str(apk)], limit=15_000, timeout=60)
    match = CERT_PATTERN.search(data.decode('utf-8', 'replace'))
    need(match is not None, 'APK_SIGNER_UNAVAILABLE')
    return match.group(1).lower()

def verify_bundle(directory: Path, source: str, run: dict, android_home: Path) -> tuple[Path, Path]:
    need(directory.is_dir() and not directory.is_symlink(), 'ARTIFACT_DIR_INVALID')
    files = {x.name for x in directory.iterdir()}
    need(files == EXPECTED_KEYS, 'ARTIFACT_INVENTORY_INVALID')
    manifest = directory / 'SFDT22_PHYSICAL_APKS.json'
    need(manifest.is_file() and not manifest.is_symlink() and manifest.stat().st_size < 4096,
         'MANIFEST_FILE_INVALID')
    try:
        data = json.loads(manifest.read_text(encoding='utf-8'))
    except (ValueError, UnicodeError, OSError):
        raise Refused('MANIFEST_JSON_INVALID') from None
    need(isinstance(data, dict) and data.get('schema') == 1 and
         data.get('source_sha') == source and data.get('run_id') == str(run['id']) and
         data.get('run_attempt') == str(run['run_attempt']), 'MANIFEST_PROVENANCE_MISMATCH')
    for key in ('executed_in_hosted_emulator', 'physical_device_qualified',
                'release_qualified', 'publication_allowed'):
        need(data.get(key) is False, 'MANIFEST_QUALIFICATION_FALSE_REQUIRED')
    app = directory / 'app-debug.apk'
    test = directory / 'app-debug-androidTest.apk'
    for apk, field in ((app, 'app_sha256'), (test, 'test_sha256')):
        digest = data.get(field)
        need(isinstance(digest, str) and SHA256.fullmatch(digest) is not None and
             sha(apk) == digest, 'ARTIFACT_DIGEST_MISMATCH')
    certs = []
    for apk, field in ((app, 'app_certificate_sha256'), (test, 'test_certificate_sha256')):
        expected = data.get(field)
        need(isinstance(expected, str) and SHA256.fullmatch(expected) is not None,
             'MANIFEST_SIGNER_INVALID')
        actual = signer(apk, android_home)
        need(actual == expected, 'ARTIFACT_SIGNER_MISMATCH')
        certs.append(actual)
    need(certs[0] == certs[1], 'APK_PAIR_SIGNERS_DIFFER')
    return app, test

def deliver(app: Path, test: Path, root: Path) -> None:
    """Copy only after full pair verification; never replace existing outputs."""
    destinations = (root / APP, root / TEST)
    for dst in destinations:
        need(not dst.exists() and not dst.is_symlink(), 'TARGET_APK_ALREADY_EXISTS')
        need(not dst.parent.is_symlink(), 'TARGET_DIR_SYMLINK_REFUSED')
    created = []
    try:
        for source, dst in zip((app, test), destinations):
            dst.parent.mkdir(parents=True, exist_ok=True)
            with tempfile.NamedTemporaryFile(dir=dst.parent, prefix='.opt02-', delete=False) as tmp:
                staging = Path(tmp.name)
                created.append(staging)
                with source.open('rb') as inp:
                    shutil.copyfileobj(inp, tmp, 1024 * 1024)
                tmp.flush()
                os.fsync(tmp.fileno())
            need(sha(staging) == sha(source), 'COPY_DIGEST_MISMATCH')
            os.replace(staging, dst)
            created.append(dst)
    except BaseException:
        for item in reversed(created):
            item.unlink(missing_ok=True)
        raise

def inspect_source(root: Path, source: str) -> None:
    need(SHA40.fullmatch(source) is not None, 'SOURCE_SHA_INVALID')
    need(os.getenv('GITHUB_ACTIONS') == 'true' and os.getenv('GITHUB_REPOSITORY') == REPO and
         os.getenv('RUNNER_OS') == 'Linux', 'GITHUB_HOST_IDENTITY_INVALID')
    need(command(['git','-C',str(root),'rev-parse','HEAD']).decode().strip() == source,
         'CHECKOUT_SHA_MISMATCH')
    need(command(['git','-C',str(root),'status','--porcelain','--untracked-files=normal']) == b'',
         'DIRTY_CHECKOUT')
    need(os.getenv('GH_TOKEN') is not None, 'GH_TOKEN_MISSING')

def producer_activity(document: dict, source: str) -> str:
    """Same-source push activity only; unrelated runs never justify a wait."""
    runs = document.get('workflow_runs')
    need(isinstance(runs, list) and len(runs) <= 40, 'RUN_LIST_INVALID')
    relevant = []
    for run in runs:
        need(isinstance(run, dict), 'RUN_RECORD_INVALID')
        if run.get('head_sha') != source or run.get('event') != 'push' or run.get('head_branch') != BRANCH:
            continue
        need(isinstance(run.get('repository'), dict) and
             run['repository'].get('full_name') == REPO, 'PRODUCER_REPOSITORY_MISMATCH')
        need(isinstance(run.get('id'), int) and run['id'] > 0 and
             isinstance(run.get('run_attempt'), int) and run['run_attempt'] > 0,
             'PRODUCER_RUN_ID_INVALID')
        relevant.append(run)
    if any(x.get('status') in ('in_progress', 'queued', 'pending', 'requested', 'waiting')
           for x in relevant):
        return 'pending'
    return 'terminal' if relevant else 'not_discovered'


def find_producer(source: str, *, wait_seconds: int = 0) -> dict | None:
    """Coordinate separate GitHub workflows without weakening source identity.

    Old P01 direct callers still do one read (wait_seconds=0). Only the hosted
    CLI enables bounded waiting. A commit no longer at producer branch HEAD
    is never awaited, so old or unrelated PRs do not waste CI minutes.
    """
    from urllib.parse import quote
    need(type(wait_seconds) is int and 0 <= wait_seconds <= 900,
         'PRODUCER_WAIT_BUDGET_INVALID')
    need(SHA40.fullmatch(source) is not None, 'SOURCE_SHA_INVALID')
    listing_path = ('repos/' + REPO + '/actions/workflows/' + PRODUCER +
                    '/runs?branch=' + quote(BRANCH, safe='') + '&event=push&per_page=40')
    listing = github_json(listing_path)
    success = select_run(listing, source)
    if success is not None or wait_seconds == 0:
        return success
    if producer_activity(listing, source) == 'terminal':
        return None  # Completed failed/cancelled build: native Gradle fallback
    branch = github_json('repos/' + REPO + '/branches/' + quote(BRANCH, safe=''))
    identity = branch.get('commit')
    need(isinstance(identity, dict) and
         isinstance(identity.get('sha'), str) and SHA40.fullmatch(identity['sha']) is not None,
         'PRODUCER_BRANCH_HEAD_UNVERIFIABLE')
    if identity['sha'] != source:
        return None  # No build will arrive for a superseded HEAD
    # 2026-11-15: the push producer took 7m35s while the former 300s
    # consumer budget expired after 5m. A bounded 12m budget accommodates
    # normal hosted queues while no-run discovery is capped at 60s.
    deadline = time.monotonic() + wait_seconds
    discovery_deadline = time.monotonic() + min(wait_seconds, 60)
    attempt = 0
    while True:
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            return None
        attempt += 1
        # Bounded polling, not an unbounded CI watcher or hidden background job.
        delay = min(20.0, remaining)
        print('OPT02_SHARED_APKS_INFO PRODUCER_PENDING poll=' + str(attempt) +
              ' wait_s=' + str(round(delay)), flush=True)
        time.sleep(delay)
        listing = github_json(listing_path)
        success = select_run(listing, source)
        if success is not None:
            return success
        state = producer_activity(listing, source)
        if state == 'terminal':
            return None
        if state == 'not_discovered' and time.monotonic() >= discovery_deadline:
            return None  # no producer registered: avoid a 12-minute idle miss


def try_reuse(root: Path, source: str, android_home: Path,
              *, wait_seconds: int = 0) -> tuple[bool, str]:
    # P02 waits for the separate exact-source producer before any Gradle fallback.
    # An absent/failed producer remains a MISS, never a false qualification.
    candidate = find_producer(source, wait_seconds=wait_seconds)
    if candidate is None:
        return False, ('NO_SUCCESSFUL_EXACT_SHA_PRODUCER' if wait_seconds == 0
                       else 'NO_SUCCESSFUL_EXACT_SHA_PRODUCER_AFTER_BOUNDED_WAIT')
    run_id = candidate['id']
    artifact = select_artifact(github_json(f'repos/{REPO}/actions/runs/{run_id}/artifacts'), candidate)
    if artifact is None:
        return False, 'PRODUCER_ARTIFACT_EXPIRED'
    with tempfile.TemporaryDirectory(prefix='opt02-shared-') as temporary:
        tmp = Path(temporary)
        name = artifact['name']
        command(['gh', 'run', 'download', str(run_id), '-R', REPO,
                 '--name', name, '--dir', str(tmp)], limit=10_000, timeout=240)
        app, test = verify_bundle(tmp, source, candidate, android_home)
        deliver(app, test, root)
    return True, 'EXACT_SHA_SIGNED_APK_PAIR_VERIFIED'

def append_output(path: Path, reused: bool) -> None:
    need(path.is_file() and not path.is_symlink(), 'GITHUB_OUTPUT_INVALID')
    with path.open('a', encoding='ascii') as f:
        f.write('reused=' + ('true' if reused else 'false') + '\n')

def main() -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--reuse', action='store_true', required=True)
    p.add_argument('--expected-sha', required=True)
    p.add_argument('--github-output', type=Path, required=True)
    p.add_argument('--wait-seconds', type=int, default=720,
                   help='OPT02 P02 HF01: hosted producer wait 0..900 seconds (default 720)')
    a = p.parse_args()
    try:
        root = Path(__file__).resolve().parents[2]
        inspect_source(root, a.expected_sha)
        home = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
        need(bool(home), 'ANDROID_HOME_UNAVAILABLE')
        need(0 <= a.wait_seconds <= 900, 'PRODUCER_WAIT_BUDGET_INVALID')
        yes, detail = try_reuse(root, a.expected_sha, Path(home),
                                wait_seconds=a.wait_seconds)
        append_output(a.github_output, yes)
        print('OPT02_SHARED_APKS_' + ('HIT' if yes else 'MISS') + ' ' + detail, flush=True)
        return 0
    except Refused as e:
        print('OPT02_SHARED_APKS_REFUSED ' + str(e), file=sys.stderr, flush=True)
        return 2
    except (OSError, ValueError, TypeError, UnicodeError):
        print('OPT02_SHARED_APKS_REFUSED UNEXPECTED_HOST_ERROR', file=sys.stderr, flush=True)
        return 2

if __name__ == '__main__':
    raise SystemExit(main())
