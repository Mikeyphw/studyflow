#!/usr/bin/env python3
"""SFDT21: fail-closed real Android emulator instrumentation and byte provenance.

Only the exact GitHub-hosted API 36 emulator may produce this receipt. Never
upload raw instrumentation logs, private device dumps, or non-test account data.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import struct
import subprocess
import sys

PACKAGE = 'com.mikeyphw.studyflow'
CLASS = PACKAGE + '.Sfdt21HostedRealUiTest'
RUNNER = PACKAGE + '.test/androidx.test.runner.AndroidJUnitRunner'
SERIAL = 'emulator-5554'
TESTS = (
    'realActivityNavigatesFourWorkspacesAndCapturesUi',
    'realActivityRestoresSelectedWorkspaceAfterRecreation',
    'realActivityMaintainsExclusiveSelectionAcrossRepeatedNavigation',
    'realActivityOpensGlobalSearchAndReturnsToToday',
)
SCREENS = ('today', 'plan', 'practice', 'progress')
SHA40 = re.compile(r'[0-9a-f]{40}\Z')
SHA256 = re.compile(r'[0-9a-f]{64}\Z')
OUTCOME = 'PASS_HOSTED_REAL_MAINACTIVITY_UI'
# These constants do not depend on user data, APK paths, raw ADB or stack traces.
PHASES = (
    'host_identity', 'emulator_connected', 'emulator_identity', 'fresh_install',
    'apk_artifact_hashes', 'apk_signer_app', 'apk_signer_test',
    'install_app', 'install_test', 'locate_installed_app',
    'hash_installed_app', 'instrumentation', 'screenshots', 'complete',
)

class Refused(ValueError):
    pass

def need(ok: bool, why: str) -> None:
    if not ok:
        raise Refused(why)

def checked_sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()

def file_sha256(path: Path, *, low: int = 1, high: int = 80 * 1024 * 1024) -> dict:
    need(path.is_file() and not path.is_symlink(), 'Missing or symlinked artifact')
    sz = path.stat().st_size
    need(low <= sz <= high, 'Artifact size outside reviewed bounds')
    with path.open('rb') as file:
        digest = hashlib.file_digest(file, 'sha256').hexdigest()
    return {'size': sz, 'sha256': digest}

def png_dimensions(data: bytes) -> tuple[int, int]:
    need(1000 <= len(data) <= 5 * 1024 * 1024, 'PNG size refused')
    need(data[:8] == b'\x89PNG\r\n\x1a\n' and data[12:16] == b'IHDR', 'Invalid PNG magic')
    need(struct.unpack('>I', data[8:12])[0] == 13, 'Invalid PNG IHDR')
    w, h = struct.unpack('>II', data[16:24])
    need(320 <= w <= 4096 and 480 <= h <= 4096, 'Invalid real UI dimensions')
    need(data.endswith(b'IEND\xaeB`\x82'), 'Truncated PNG')
    return w, h

def summarize(raw: str) -> dict:
    """Allowlist test names and status codes only. Never preserve raw logs."""
    started, passed, failed = set(), set(), set()
    active = None
    for line in raw.splitlines():
        if line.startswith('INSTRUMENTATION_STATUS: test='):
            value = line.partition('=')[2].strip()
            active = value if value in TESTS else None
        elif line.startswith('INSTRUMENTATION_STATUS_CODE: '):
            code = line.partition(': ')[2].strip()
            if active is not None:
                if code == '1': started.add(active)
                elif code == '0': passed.add(active)
                elif code in ('-1', '-2'): failed.add(active)
            active = None
    passed -= failed
    errors = ('INSTRUMENTATION_FAILED', 'INSTRUMENTATION_ABORTED',
              'Process crashed', 'INSTRUMENTATION_RESULT: shortMsg=',
              'AssertionFailedError', 'AssertionError', 'ComposeTimeoutException')
    return {
        'expected_tests': list(TESTS),
        'started_tests': [t for t in TESTS if t in started],
        'passed_tests': [t for t in TESTS if t in passed],
        'failed_tests': [t for t in TESTS if t in failed],
        'terminal_success': bool(re.search(r'(?m)^INSTRUMENTATION_CODE: -1\s*$', raw)),
        'reported_ok': bool(re.search(rf'(?m)^OK \({len(TESTS)} tests\)\s*$', raw)),
        'error_categories': [t for t in errors if t in raw],
        'raw_logs_retained': False,
    }

def valid_summary(result: dict) -> bool:
    return (result['started_tests'] == list(TESTS) and
            result['passed_tests'] == list(TESTS) and not result['failed_tests'] and
            result['terminal_success'] and result['reported_ok'] and
            not result['error_categories'] and not result['raw_logs_retained'])

def command(*args: str, timeout: int = 90) -> bytes:
    p = subprocess.run(args, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                       timeout=timeout, check=False)
    need(p.returncode == 0, 'Hosted emulator subprocess failed')
    return p.stdout

def adb(*args: str, timeout: int = 90) -> bytes:
    return command('adb', '-s', SERIAL, *args, timeout=timeout)

def assert_fresh_emulator() -> None:
    """A missing Android package is success, not a `pm path` subprocess error.

    `pm path <absent>` can return exit 1 on API 36. `pm list packages`
    returns exit 0 and an empty response when no names match. Any installed
    StudyFlow-prefix package, unexpected output, or ADB error fails closed.
    """
    listing = adb('shell', 'pm', 'list', 'packages', PACKAGE, timeout=30)
    # A brand-new emulator must not have the app OR its test package. Do not
    # use `pm path`: its absent-package exit status is device-dependent.
    need(listing.strip() == b'', 'Previously installed StudyFlow package refused')

def assert_host(source: str, run_id: str, attempt: str) -> None:
    need(SHA40.fullmatch(source) is not None and run_id.isdecimal() and
         attempt.isdecimal() and int(run_id) > 0 and int(attempt) > 0,
         'Unreviewed source or run identity')
    env = os.environ
    need(all((env.get('GITHUB_ACTIONS') == 'true', env.get('RUNNER_OS') == 'Linux',
              env.get('RUNNER_ARCH') == 'X64', env.get('GITHUB_REPOSITORY') == 'Mikeyphw/studyflow',
              env.get('GITHUB_SHA') == source, env.get('GITHUB_RUN_ID') == run_id,
              env.get('GITHUB_RUN_ATTEMPT') == attempt,
              env.get('STUDYFLOW_SFDT21_HOSTED_EMULATOR') == 'true')),
         'Only exact GitHub-hosted emulator is permitted')
    need(command('git', 'rev-parse', 'HEAD', timeout=10).decode().strip() == source,
         'Git HEAD differs from workflow source')

def certificate_sha256(apk: Path) -> str:
    """Independently read the actual debug APK signer from the pinned SDK tool."""
    sdk_root = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
    need(bool(sdk_root), 'Android SDK location missing')
    tool = Path(sdk_root) / 'build-tools/36.0.0/apksigner'
    need(tool.is_file() and not tool.is_symlink(), 'Pinned apksigner missing')
    output = command(str(tool), 'verify', '--print-certs', str(apk), timeout=90).decode('utf-8', 'replace')
    match = re.search(r'(?m)^Signer #1 certificate SHA-256 digest: ([0-9a-fA-F]{64})\r?$', output)
    need(match is not None, 'Debug APK certificate SHA-256 unavailable')
    return match.group(1).lower()

def artifact_paths(repo: Path) -> tuple[Path, Path]:
    return (repo / 'app/build/outputs/apk/debug/app-debug.apk',
            repo / 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')

def preflight_apks(source: str, run_id: str, attempt: str, repo: Path) -> dict:
    """Hosted, read-only pre-AVD check; no APK installation or instrumentation."""
    assert_host(source, run_id, attempt)
    app, test = artifact_paths(repo)
    app_info = file_sha256(app, low=1024)
    test_info = file_sha256(test, low=1024)
    app_cert = certificate_sha256(app)
    test_cert = certificate_sha256(test)
    need(SHA256.fullmatch(app_cert) is not None and SHA256.fullmatch(test_cert) is not None,
         'Non-SHA256 APK signer')
    return {'source_sha': source, 'run_id': run_id, 'attempt': attempt,
            'app_sha256': app_info['sha256'], 'test_sha256': test_info['sha256'],
            'app_signer_sha256': app_cert, 'test_signer_sha256': test_cert,
            'status': 'SFDT21_PRE_AVD_APK_PREFLIGHT_PASS',
            'emulator_ui_qualified': False, 'release_qualified': False}

def run(output_dir: Path, source: str, run_id: str, attempt: str, repo: Path) -> bool:
    output_dir.mkdir(parents=True, exist_ok=True)
    receipt_path = output_dir / 'SFDT21_EVIDENCE.json'
    evidence = {
        'schema': 1, 'producer': 'studyflow-sfdt21-hosted-api36-real-mainactivity',
        'overall': 'FAILED_OR_NOT_RUN', 'source_sha': source,
        'run_id': run_id, 'attempt': attempt,
        'hosted_emulator_only': True, 'physical_device_qualified': False,
        'release_qualified': False, 'publication_allowed': False,
        'real_main_activity': True, 'real_ui_interactions': True,
        'fresh_no_account_fixture': True, 'network_disabled': True,
        'raw_logs_retained': False,
    }
    for s in SCREENS:
        (output_dir / f'sfdt21-ui-{s}.png').unlink(missing_ok=True)
    phase = 'host_identity'
    try:
        assert_host(source, run_id, attempt)
        phase = 'emulator_connected'
        need(adb('get-state').strip() == b'device', 'Emulator device unavailable')
        phase = 'emulator_identity'
        need(adb('shell', 'getprop', 'ro.kernel.qemu').strip() == b'1', 'Physical device refused')
        need(adb('shell', 'getprop', 'ro.build.version.sdk').strip() == b'36', 'Not API 36')
        phase = 'fresh_install'
        assert_fresh_emulator()
        phase = 'apk_artifact_hashes'
        app, test = artifact_paths(repo)
        app_info = file_sha256(app, low=1024)
        test_info = file_sha256(test, low=1024)
        phase = 'apk_signer_app'
        app_cert = certificate_sha256(app)
        phase = 'apk_signer_test'
        test_cert = certificate_sha256(test)
        phase = 'install_app'
        adb('install', '-r', '-t', str(app), timeout=120)
        phase = 'install_test'
        adb('install', '-r', '-t', str(test), timeout=120)
        phase = 'locate_installed_app'
        path_bytes = adb('shell', 'pm', 'path', PACKAGE).decode().replace('\r', '').strip()
        need(path_bytes.startswith('package:/data/app/') and path_bytes.endswith('/base.apk'),
             'Installed package path refused')
        installed_path = path_bytes.removeprefix('package:')
        phase = 'hash_installed_app'
        installed = adb('shell', 'sha256sum', installed_path).decode().strip().split()[0]
        need(installed == app_info['sha256'], 'Installed APK bytes differ from built APK')
        phase = 'instrumentation'
        p = subprocess.run(['adb', '-s', SERIAL, 'shell', 'am', 'instrument', '-w', '-r',
                            '-e', 'class', CLASS, RUNNER], capture_output=True,
                           timeout=720, check=False)
        summary = summarize(p.stdout.decode('utf-8', 'replace'))
        evidence['instrumentation_summary'] = summary
        need(p.returncode == 0 and valid_summary(summary), 'Real MainActivity UI tests failed')
        phase = 'screenshots'
        images = []
        seen = set()
        for name in SCREENS:
            raw = adb('exec-out', 'run-as', PACKAGE, 'cat', 'files/sfdt21-ui-' + name + '.png')
            width, height = png_dimensions(raw)
            digest = checked_sha256(raw)
            need(digest not in seen, 'Duplicate real UI screenshots')
            seen.add(digest)
            file = output_dir / ('sfdt21-ui-' + name + '.png')
            file.write_bytes(raw)
            images.append({'name': file.name, 'width': width, 'height': height,
                           'size': len(raw), 'sha256': digest})
        evidence.update(overall=OUTCOME, api_level=36, emulator_serial=SERIAL,
                        app_apk=app_info, test_apk=test_info,
                        debug_signing_certificates={'app': app_cert, 'test': test_cert},
                        installed_app_sha256=installed, screenshots=images,
                        test_count=len(TESTS), screenshot_count=len(SCREENS))
    except (OSError, KeyError, Refused, subprocess.TimeoutExpired, ValueError) as exc:
        evidence['failure_class'] = type(exc).__name__
        evidence['failure_phase'] = phase  # Constant, bounded enum; no private output.
        for name in SCREENS:
            (output_dir / ('sfdt21-ui-' + name + '.png')).unlink(missing_ok=True)
    receipt_path.write_text(json.dumps(evidence, indent=2, sort_keys=True) + '\n', encoding='utf-8')
    print('SFDT21_EMULATOR_' + ('PASS' if evidence['overall'] == OUTCOME else 'REFUSED') +
          ': real_main_activity=' + str(evidence['real_main_activity']).lower() +
          ' tests=' + str(evidence.get('test_count', 0)) +
          ' phase=' + (evidence.get('failure_phase') or phase), flush=True)
    return evidence['overall'] == OUTCOME

def verify(output_dir: Path, source: str, run_id: str, attempt: str, repo: Path, *, cert_reader=None) -> dict:
    need(SHA40.fullmatch(source) is not None and run_id.isdecimal() and attempt.isdecimal(),
         'Invalid expected identity')
    receipt_path = output_dir / 'SFDT21_EVIDENCE.json'
    need(receipt_path.is_file() and not receipt_path.is_symlink() and
         0 < receipt_path.stat().st_size <= 16_384, 'Missing/oversize receipt')
    obj = json.loads(receipt_path.read_bytes())
    expected = {'schema': 1, 'producer': 'studyflow-sfdt21-hosted-api36-real-mainactivity',
                'overall': OUTCOME, 'source_sha': source, 'run_id': run_id,
                'attempt': attempt, 'hosted_emulator_only': True,
                'physical_device_qualified': False, 'release_qualified': False,
                'publication_allowed': False, 'real_main_activity': True,
                'real_ui_interactions': True, 'fresh_no_account_fixture': True,
                'network_disabled': True, 'raw_logs_retained': False,
                'api_level': 36, 'emulator_serial': SERIAL,
                'test_count': len(TESTS), 'screenshot_count': len(SCREENS)}
    for key, value in expected.items():
        need(obj.get(key) == value, 'Invalid required emulator receipt field: ' + key)
    need(valid_summary(obj.get('instrumentation_summary', {})), 'Instrumentation outcome invalid')
    app, test = artifact_paths(repo)
    for key, file in (('app_apk', app), ('test_apk', test)):
        actual = file_sha256(file, low=1024)
        need(obj.get(key) == actual, 'Built APK provenance mismatch: ' + key)
    need(obj.get('installed_app_sha256') == obj['app_apk']['sha256'], 'Installed bytes mismatch')
    cert_reader = certificate_sha256 if cert_reader is None else cert_reader
    expected_certs = {'app': cert_reader(app), 'test': cert_reader(test)}
    need(all(isinstance(value, str) and SHA256.fullmatch(value) for value in expected_certs.values()),
         'Invalid certificate SHA-256')
    need(obj.get('debug_signing_certificates') == expected_certs,
         'Signer identity differs from actual hosted APK bytes')
    imgs = obj.get('screenshots')
    need(isinstance(imgs, list) and len(imgs) == len(SCREENS), 'Screenshot inventory mismatch')
    need({p.name for p in output_dir.glob('*.png')} ==
         {'sfdt21-ui-' + s + '.png' for s in SCREENS}, 'Unexpected PNG files')
    seen = set()
    for index, name in enumerate(SCREENS):
        record = imgs[index]
        filename = 'sfdt21-ui-' + name + '.png'
        need(record.get('name') == filename, 'Screenshot name/order mismatch')
        path = output_dir / filename
        need(path.is_file() and not path.is_symlink(), 'Missing/linked screenshot')
        raw = path.read_bytes()
        width, height = png_dimensions(raw)
        need(record.get('width') == width and record.get('height') == height and
             record.get('size') == len(raw) and
             record.get('sha256') == checked_sha256(raw), 'Screenshot hash/dimensions invalid')
        need(record['sha256'] not in seen, 'Duplicate screenshot hash')
        seen.add(record['sha256'])
    need(not (output_dir / 'instrumentation.log').exists(), 'Raw instrumentation log present')
    return {'status': 'SFDT21_HOSTED_ACCEPTANCE_VERIFIED', 'source_sha': source,
            'run_id': run_id, 'test_count': len(TESTS), 'screenshots': len(SCREENS),
            'physical_device_qualified': False, 'release_qualified': False}

def main(argv=None) -> int:
    p = argparse.ArgumentParser(description=__doc__)
    group = p.add_mutually_exclusive_group(required=True)
    group.add_argument('--run', action='store_true')
    group.add_argument('--preflight-apks', action='store_true')
    group.add_argument('--verify', action='store_true')
    p.add_argument('--output-dir', required=True, type=Path)
    p.add_argument('--source-sha', required=True)
    p.add_argument('--run-id', required=True)
    p.add_argument('--attempt', required=True)
    args = p.parse_args(argv)
    repo = Path(__file__).resolve().parents[1]
    try:
        if args.preflight_apks:
            # Do not persist signer or raw command output; print only a fixed pass marker.
            preflight_apks(args.source_sha, args.run_id, args.attempt, repo)
            print('SFDT21_PRE_AVD_APK_PREFLIGHT_PASS')
            return 0
        if args.run:
            return 0 if run(args.output_dir, args.source_sha, args.run_id, args.attempt, repo) else 2
        data = verify(args.output_dir, args.source_sha, args.run_id, args.attempt, repo)
        print(json.dumps(data, sort_keys=True))
        return 0
    except (OSError, ValueError, KeyError, TypeError, json.JSONDecodeError) as exc:
        print('SFDT21_EVIDENCE_REFUSED: ' + type(exc).__name__, file=sys.stderr)
        return 2

if __name__ == '__main__':
    raise SystemExit(main())
