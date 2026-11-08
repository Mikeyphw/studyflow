#!/usr/bin/env python3
"""SFDT22: fail-closed, GitHub-hosted API36 real study-journey acceptance.

This verifier deliberately does not retain instrumentation logs, device dumps,
account data, or full host metadata. A screenshot is evidence only when the
matching real instrumentation method passed on this same hosted emulator.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time

# Reuse the already qualified SFDT21 APK/signer/PNG primitives. The new workflow
# verifies its own five tests, forced app process restart and seven screenshots.
import sfdt21_emulator_verify as base

PACKAGE = 'com.mikeyphw.studyflow'
RUNNER = PACKAGE + '.test/androidx.test.runner.AndroidJUnitRunner'
CLASS = PACKAGE + '.Sfdt22HostedStudyJourneyTest'
TESTS = (
    'realUserCreatesAStudyPlan',
    'realUserAddsSubjectAndTopic',
    'realUserStartsPausesResumesAndCompletesASession',
    'realUserAnswersQuestionAndOpensReviewQueue',
    'realUserSeesPersistedProgressAfterColdRestart',
)
SCREENS = ('plan', 'subject', 'topic', 'session', 'question', 'review', 'restart')
EXPECTED = 'PASS_HOSTED_REAL_STUDY_JOURNEYS'
SERIAL = 'emulator-5554'
PHASES = ('host', 'device', 'fresh', 'apks', 'install', 'installed_apk',
          'plan', 'subjects', 'session', 'practice', 'force_stop',
          'post_restart', 'screenshots', 'complete')
SHA256 = re.compile(r'^[0-9a-f]{64}$')
STAGES = frozenset({
    'plan_open', 'plan_name', 'plan_target', 'plan_preferences',
    'plan_save', 'plan_confirm', 'plan_capture',
})
REASON_CODES = frozenset({
    'no_matching_compose_node', 'ambiguous_compose_node',
    'compose_wait_timeout', 'compose_assertion', 'app_process_crashed',
    'test_failed', 'unexpected_runner_status', 'runner_nonzero_exit',
})


class JourneyFailure(ValueError):
    """Carries only allowlisted evidence fields, never instrumentation output."""
    def __init__(self, name: str, *, reason: str, stage: str = 'unavailable'):
        require(name in TESTS and reason in REASON_CODES and
                (stage == 'unavailable' or stage in STAGES),
                'Unreviewed journey diagnostic')
        super().__init__('SFDT22 instrumentation failed')
        self.test = name
        self.reason = reason
        self.stage = stage


def summarize_failure(raw: str, test: str, returncode: int) -> JourneyFailure:
    """Map volatile Android stdout to stable enums; persist none of raw."""
    marker = re.search(r'SFDT22_STEP_([a-z][a-z0-9_]{1,39})', raw)
    stage = marker.group(1) if marker and marker.group(1) in STAGES else 'unavailable'
    if 'No node found that satisfies' in raw or 'No matching node found' in raw:
        reason = 'no_matching_compose_node'
    elif 'Expected exactly' in raw or 'Found multiple nodes' in raw:
        reason = 'ambiguous_compose_node'
    elif 'ComposeTimeoutException' in raw or 'Condition still not satisfied' in raw:
        reason = 'compose_wait_timeout'
    elif 'AssertionError' in raw or 'assertIsDisplayed' in raw:
        reason = 'compose_assertion'
    elif 'Process crashed' in raw or 'INSTRUMENTATION_FAILED' in raw:
        reason = 'app_process_crashed'
    elif 'FAILURES!!!' in raw or 'INSTRUMENTATION_STATUS_CODE: -2' in raw:
        reason = 'test_failed'
    elif returncode != 0:
        reason = 'runner_nonzero_exit'
    else:
        reason = 'unexpected_runner_status'
    return JourneyFailure(test, reason=reason, stage=stage)


def require(ok: bool, message: str) -> None:
    if not ok:
        raise ValueError(message)


def host(source: str, run: str, attempt: str) -> None:
    base.assert_host(source, run, attempt)  # includes SFDT21 debug-opt-in guard
    require(os.environ.get('STUDYFLOW_SFDT22_HOSTED_JOURNEYS') == 'true',
            'Missing dedicated SFDT22 host opt-in')
    require(os.environ.get('CI') == 'true', 'Hosted CI environment required')
    require(not any(os.environ.get(s) for s in (
        'STUDYFLOW_RELEASE_STORE_PASSWORD', 'STUDYFLOW_RELEASE_KEY_PASSWORD',
        'STUDYFLOW_RELEASE_STORE_FILE',
    )), 'Release credentials must not be used in test gate')


def check_device() -> None:
    require(base.adb('get-state').strip() == b'device', 'No emulator')
    require(base.adb('shell', 'getprop', 'ro.kernel.qemu').strip() == b'1',
            'Real hardware not allowed')
    require(base.adb('shell', 'getprop', 'ro.build.version.sdk').strip() == b'36',
            'Wrong Android API')
    require(base.adb('shell', 'getprop', 'ro.product.cpu.abi').strip() == b'x86_64',
            'Wrong architecture')


def run_test(name: str) -> dict:
    require(name in TESTS, 'Unknown instrumentation case')
    p = subprocess.run(
        ['adb', '-s', SERIAL, 'shell', 'am', 'instrument', '-w', '-r',
         '-e', 'class', CLASS + '#' + name, RUNNER],
        capture_output=True, timeout=360, check=False,
    )
    raw = p.stdout.decode('utf-8', 'replace')
    # Explicit status tied to the exact allowlisted test, not a permissive
    # Gradle/runner success marker or a source-only contract.
    started = re.findall(r'(?m)^INSTRUMENTATION_STATUS: test=([^\r\n]+)\r?$', raw)
    codes = re.findall(r'(?m)^INSTRUMENTATION_STATUS_CODE: ([^\r\n]+)\r?$', raw)
    results = re.findall(r'(?m)^INSTRUMENTATION_CODE: ([^\r\n]+)\r?$', raw)
    if not (p.returncode == 0 and set(started) == {name} and
            codes.count('1') == 1 and codes.count('0') == 1 and
            len(codes) == 2 and results == ['-1'] and
            'FAILURES!!!' not in raw and 'INSTRUMENTATION_FAILED' not in raw):
        raise summarize_failure(raw, name, p.returncode)
    return {'test': name, 'status': 'passed', 'started': 1, 'finished': 1}


def current_pid() -> int:
    # Android pidof exits 1 when no process exists. That *expected* absence
    # is distinct from a transport error, which is never treated as success.
    p = subprocess.run(['adb', '-s', SERIAL, 'shell', 'pidof', PACKAGE],
                       capture_output=True, timeout=25, check=False)
    raw = p.stdout.strip()
    if p.returncode == 1 and not raw and not p.stderr.strip():
        return 0
    require(p.returncode == 0, 'Failed to inspect Android process')
    require(bool(raw), 'Unreported Android process')
    values = raw.split()
    require(len(values) == 1 and values[0].isdigit(), 'Ambiguous app PID')
    return int(values[0])


def wait_for_pid(*, present: bool) -> int:
    for _ in range(20):
        pid = current_pid()
        if bool(pid) == present:
            return pid
        time.sleep(1)
    raise ValueError('Android process-state transition not observed')


def real_process_restart() -> None:
    component = PACKAGE + '/.MainActivity'
    base.adb('shell', 'am', 'start', '-n', component, timeout=30)
    before = wait_for_pid(present=True)
    base.adb('shell', 'am', 'force-stop', PACKAGE, timeout=30)
    wait_for_pid(present=False)
    base.adb('shell', 'am', 'start', '-n', component, timeout=30)
    after = wait_for_pid(present=True)
    require(before != after, 'App process did not actually restart')


def apk_facts(repo: Path) -> tuple[dict, dict, dict]:
    app, test = base.artifact_paths(repo)
    app_hash = base.file_sha256(app, low=1024)
    test_hash = base.file_sha256(test, low=1024)
    certs = {'app': base.certificate_sha256(app), 'test': base.certificate_sha256(test)}
    require(all(SHA256.fullmatch(x) for x in certs.values()), 'Invalid APK signer')
    return app_hash, test_hash, certs


def blank(source: str, run_id: str, attempt: str) -> dict:
    return dict(schema=1, producer='studyflow-sfdt22-hosted-api36-real-journeys',
                overall='FAILED_OR_NOT_RUN', source_sha=source, run_id=run_id,
                attempt=attempt, hosted_emulator_only=True,
                physical_device_qualified=False, release_qualified=False,
                publication_allowed=False, fresh_no_account_fixture=True,
                real_main_activity=True, real_ui_interactions=True,
                force_stopped_app_and_verified_new_pid=False,
                network_disabled=True, raw_logs_retained=False)


def run(output: Path, source: str, run_id: str, attempt: str, repo: Path) -> bool:
    output.mkdir(parents=True, exist_ok=True)
    receipt = output / 'SFDT22_EVIDENCE.json'
    evidence = blank(source, run_id, attempt)
    for name in SCREENS:
        (output / ('sfdt22-ui-' + name + '.png')).unlink(missing_ok=True)
    phase = 'host'
    try:
        host(source, run_id, attempt)
        phase = 'device'
        check_device()
        base.adb('shell', 'settings', 'put', 'system', 'font_scale', '1.0')
        base.adb('shell', 'svc', 'wifi', 'disable')
        base.adb('shell', 'svc', 'data', 'disable')
        phase = 'fresh'
        base.assert_fresh_emulator()
        phase = 'apks'
        app, test = base.artifact_paths(repo)
        app_hash, test_hash, certs = apk_facts(repo)
        phase = 'install'
        base.adb('install', '-r', '-t', str(app), timeout=120)
        base.adb('install', '-r', '-t', str(test), timeout=120)
        phase = 'installed_apk'
        path = base.adb('shell', 'pm', 'path', PACKAGE).decode().replace('\r', '').strip()
        require(path.startswith('package:/data/app/') and path.endswith('/base.apk'),
                'Unexpected installed APK path')
        installed_hash = base.adb('shell', 'sha256sum', path.removeprefix('package:')).decode().split()[0]
        require(installed_hash == app_hash['sha256'], 'Installed APK provenance mismatch')
        results = []
        for index, name in enumerate(TESTS[:4]):
            phase = ('plan', 'subjects', 'session', 'practice')[index]
            results.append(run_test(name))
        phase = 'force_stop'
        real_process_restart()
        phase = 'post_restart'
        results.append(run_test(TESTS[4]))
        phase = 'screenshots'
        images = []
        seen = set()
        for name in SCREENS:
            raw = base.adb('exec-out', 'run-as', PACKAGE, 'cat', 'files/sfdt22-ui-' + name + '.png')
            width, height = base.png_dimensions(raw)
            digest = hashlib.sha256(raw).hexdigest()
            require(digest not in seen, 'Identical screenshots cannot prove distinct screens')
            seen.add(digest)
            file = output / ('sfdt22-ui-' + name + '.png')
            file.write_bytes(raw)
            images.append(dict(name=file.name, width=width, height=height,
                               size=len(raw), sha256=digest))
        evidence.update(overall=EXPECTED, api_level=36, emulator_serial=SERIAL,
                        tests=results, test_count=len(TESTS), screenshots=images,
                        screenshot_count=len(SCREENS), app_apk=app_hash,
                        test_apk=test_hash, debug_signing_certificates=certs,
                        installed_app_sha256=installed_hash,
                        force_stopped_app_and_verified_new_pid=True)
        phase = 'complete'
    except (OSError, ValueError, KeyError, subprocess.TimeoutExpired) as exc:
        evidence['failure_phase'] = phase if phase in PHASES else 'host'
        evidence['failure_class'] = type(exc).__name__
        if isinstance(exc, JourneyFailure):
            evidence['failure_test'] = exc.test
            evidence['failure_step'] = exc.stage
            evidence['failure_reason_code'] = exc.reason
        for name in SCREENS:
            (output / ('sfdt22-ui-' + name + '.png')).unlink(missing_ok=True)
    receipt.write_text(json.dumps(evidence, sort_keys=True, indent=2) + '\n')
    print('SFDT22_REAL_JOURNEY_' + ('PASS' if evidence['overall'] == EXPECTED else 'REFUSED') +
          ' phase=' + phase + ' verified_tests=' + str(evidence.get('test_count', 0)), flush=True)
    return evidence['overall'] == EXPECTED


def verify(output: Path, source: str, run_id: str, attempt: str, repo: Path) -> None:
    host(source, run_id, attempt)
    path = output / 'SFDT22_EVIDENCE.json'
    require(path.is_file() and not path.is_symlink() and path.stat().st_size <= 20_000,
            'Missing or oversized evidence')
    data = json.loads(path.read_text())
    expected = blank(source, run_id, attempt)
    expected.update(overall=EXPECTED, api_level=36, emulator_serial=SERIAL,
                    test_count=len(TESTS), screenshot_count=len(SCREENS),
                    force_stopped_app_and_verified_new_pid=True)
    for key, value in expected.items():
        require(data.get(key) == value, 'Evidence identity mismatch: ' + key)
    require(data.get('tests') == [dict(test=x, status='passed', started=1, finished=1) for x in TESTS],
            'Test count/order/outcome mismatch')
    app_hash, test_hash, certs = apk_facts(repo)
    require(data.get('app_apk') == app_hash and data.get('test_apk') == test_hash and
            data.get('debug_signing_certificates') == certs and
            data.get('installed_app_sha256') == app_hash['sha256'],
            'APK content or signer provenance mismatch')
    files = {'sfdt22-ui-' + x + '.png' for x in SCREENS}
    require({p.name for p in output.glob('*.png')} == files, 'Image inventory mismatch')
    images = data.get('screenshots')
    require(isinstance(images, list) and len(images) == len(SCREENS), 'Missing screenshot evidence')
    seen = set()
    for name, record in zip(SCREENS, images):
        filename = 'sfdt22-ui-' + name + '.png'
        pic = output / filename
        require(pic.is_file() and not pic.is_symlink(), 'Missing image')
        raw = pic.read_bytes()
        width, height = base.png_dimensions(raw)
        digest = hashlib.sha256(raw).hexdigest()
        require(record == dict(name=filename, width=width, height=height,
                               size=len(raw), sha256=digest) and digest not in seen,
                'Image bytes or dimensions differ')
        seen.add(digest)
    require(not list(output.glob('*log*')), 'Raw instrumentation logs are forbidden')


def source_check(repo: Path) -> None:
    test = repo / 'app/src/androidTest/java/com/mikeyphw/studyflow/Sfdt22HostedStudyJourneyTest.kt'
    bash = repo / 'scripts/sfdt22_emulator_ci.sh'
    workflow = repo / '.github/workflows/studyflow-sfdt22-hosted-journeys.yml'
    require(all(x.is_file() for x in (test, bash, workflow)), 'Missing SFDT22 source')
    src = test.read_text()
    require('createAndroidComposeRule<MainActivity>()' in src and
            all(('fun ' + name + '(') in src for name in TESTS), 'Not real MainActivity tests')
    require(src.count('@Test fun ') == len(TESTS), 'Unexpected test count')
    require(all(('screenshot("' + name + '")') in src for name in SCREENS),
            'Incomplete UI screenshot coverage')
    require('performClick()' in src and 'performTextReplacement' in src,
            'No real user interactions found')
    require('setContent {' not in src and 'mockk(' not in src and
            'assumeTrue(' not in src, 'Source substitutes are forbidden')
    sh = bash.read_text()
    wf = workflow.read_text()
    require('sfdt22_emulator_verify.py --run' in sh and
            'STUDYFLOW_SFDT22_HOSTED_JOURNEYS' in wf and
            'api-level: 36' in wf and 'SFDT22 hosted real-study UI gate' in wf,
            'Hosted gate absent or diluted')


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    modes = parser.add_mutually_exclusive_group(required=True)
    for arg in ('source-check', 'preflight-apks', 'run', 'verify'):
        modes.add_argument('--' + arg, action='store_true')
    parser.add_argument('--output-dir', type=Path)
    parser.add_argument('--source-sha')
    parser.add_argument('--run-id')
    parser.add_argument('--attempt')
    args = parser.parse_args()
    repo = Path(__file__).resolve().parents[1]
    try:
        if args.source_check:
            source_check(repo)
            print('SFDT22_SOURCE_CONTRACT_PASS')
            return 0
        require(args.output_dir is not None and bool(args.source_sha) and
                bool(args.run_id) and bool(args.attempt), 'Missing run identity')
        if args.preflight_apks:
            host(args.source_sha, args.run_id, args.attempt)
            apk_facts(repo)
            print('SFDT22_DEBUG_APK_PREFLIGHT_PASS')
            return 0
        if args.run:
            return 0 if run(args.output_dir, args.source_sha, args.run_id, args.attempt, repo) else 2
        verify(args.output_dir, args.source_sha, args.run_id, args.attempt, repo)
        print('SFDT22_EVIDENCE_VERIFIED: five real UI tests, forced restart, seven screenshots')
        return 0
    except (OSError, KeyError, ValueError, TypeError, json.JSONDecodeError) as exc:
        print('SFDT22_REFUSED: ' + type(exc).__name__, file=sys.stderr)
        return 2


if __name__ == '__main__':
    raise SystemExit(main())
