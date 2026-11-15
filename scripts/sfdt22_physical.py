#!/usr/bin/env python3
"""SFDT22 physical ARM64 runner: real instrumentation, sanitized local evidence.

Build APKs through GitHub Actions (never local Gradle). No app/data writes during
--preflight. --run-existing is NONQUALIFYING and changes StudyFlow test data.
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
import tempfile
import time
from contextlib import nullcontext

import sfdt21_emulator_verify as base
import sfdt22_emulator_verify as journey

GITHUB_REPO = 'Mikeyphw/studyflow'
BUILD_WORKFLOW = 'studyflow-sfdt22-physical-apks.yml'
SOURCE_BRANCH = 'sfdt22-hotfix/hf30-hf04-pr-20261110'
PACKAGE = 'com.mikeyphw.studyflow'
TEST_PACKAGE = PACKAGE + '.test'
INSTRUMENTATION = TEST_PACKAGE + '/androidx.test.runner.AndroidJUnitRunner'
SCHEMA = 1
SHA40 = re.compile(r'[0-9a-f]{40}\Z')
SHA256 = re.compile(r'[0-9a-f]{64}\Z')
EXPECTED_METHODS = tuple(journey.TESTS)
ROOM_METHODS = tuple(journey.SFDT27_ROOM_TESTS)
SFLP_METHODS = tuple(journey.SFDT27_SFLP_TESTS)
SCREEN_NAMES = tuple(journey.SCREENS)

class Refused(ValueError):
    pass

def report(kind: str, detail: str) -> None:
    line = 'SFDT22_PHYSICAL_' + kind + ' ' + detail
    try:
        from rich.console import Console
        color = {'PASS':'green','INFO':'cyan','REFUSED':'red'}.get(kind,'white')
        Console().print(f'[{color}]{line}[/]', highlight=False)
    except ImportError:
        print(line, flush=True)

def need(ok: bool, category: str) -> None:
    if not ok:
        raise Refused(category)

def adb(serial: str, *args: str, timeout: int = 45, allow_failure: bool = False) -> bytes:
    try:
        process = subprocess.run(['adb', '-s', serial, *args], capture_output=True,
                                 timeout=timeout, check=False)
    except (OSError, subprocess.TimeoutExpired) as exc:
        raise Refused('ADB_UNAVAILABLE_OR_TIMED_OUT') from None
    if not allow_failure:
        need(process.returncode == 0, 'ADB_COMMAND_FAILED')
    return process.stdout

def exact_packages(raw: bytes) -> set[str]:
    try:
        lines = raw.decode('utf-8', 'strict').strip().splitlines()
    except UnicodeError:
        raise Refused('PACKAGE_INVENTORY_INVALID') from None
    if len(lines) > 50:
        raise Refused('PACKAGE_INVENTORY_INVALID')
    if any(not line.startswith('package:') for line in lines):
        raise Refused('PACKAGE_INVENTORY_INVALID')
    return {line.removeprefix('package:').strip() for line in lines}

def matching_instrumentation(raw: bytes) -> bool:
    try:
        lines = raw.decode('utf-8', 'strict').splitlines()
    except UnicodeError:
        return False
    wanted = 'instrumentation:' + INSTRUMENTATION + ' (target=' + PACKAGE + ')'
    return sum(line.strip() == wanted for line in lines) == 1

def check_physical(serial: str) -> dict:
    need(bool(re.fullmatch(r'[A-Za-z0-9._:-]{1,64}', serial)), 'INVALID_SERIAL')
    need(adb(serial, 'get-state').strip() == b'device', 'DEVICE_NOT_ONLINE')
    api = adb(serial, 'shell', 'getprop', 'ro.build.version.sdk').strip()
    abi = adb(serial, 'shell', 'getprop', 'ro.product.cpu.abi').strip()
    qemu = adb(serial, 'shell', 'getprop', 'ro.kernel.qemu').strip()
    need(api == b'36', 'EXPECTED_API36')
    need(abi == b'arm64-v8a', 'EXPECTED_PHYSICAL_ARM64')
    need(qemu != b'1', 'EMULATOR_REFUSED')
    listing = exact_packages(adb(serial, 'shell', 'pm', 'list', 'packages', PACKAGE))
    return {'api_level': 36, 'abi': 'arm64-v8a',
            'app_installed': PACKAGE in listing,
            'test_installed': TEST_PACKAGE in listing,
            'instrumentation_ready': matching_instrumentation(
                adb(serial, 'shell', 'pm', 'list', 'instrumentation'))}

def local_sha(path: Path) -> str:
    need(path.is_file() and not path.is_symlink() and 1024 <= path.stat().st_size <= 90*1024*1024,
         'APK_FILE_INVALID')
    with path.open('rb') as handle:
        return hashlib.file_digest(handle, 'sha256').hexdigest()

def load_apks(apk_dir: Path, source: str) -> dict:
    location = apk_dir / 'SFDT22_PHYSICAL_APKS.json'
    need(location.is_file() and not location.is_symlink() and location.stat().st_size < 4096,
         'ARTIFACT_MANIFEST_MISSING')
    try:
        data = json.loads(location.read_text(encoding='utf-8'))
    except (OSError, UnicodeError, ValueError):
        raise Refused('ARTIFACT_MANIFEST_INVALID') from None
    need(data.get('schema') == 1 and data.get('source_sha') == source,
         'ARTIFACT_SOURCE_MISMATCH')
    need(isinstance(data.get('run_id'), str) and data['run_id'].isdecimal() and int(data['run_id']) > 0,
         'ARTIFACT_RUN_INVALID')
    app = apk_dir / 'app-debug.apk'
    test = apk_dir / 'app-debug-androidTest.apk'
    for path, field in ((app, 'app_sha256'), (test, 'test_sha256')):
        digest = data.get(field)
        need(isinstance(digest, str) and SHA256.fullmatch(digest) is not None,
             'ARTIFACT_DIGEST_INVALID')
        need(local_sha(path) == digest, 'ARTIFACT_DIGEST_MISMATCH')
    for field in ('app_certificate_sha256', 'test_certificate_sha256'):
        value = data.get(field)
        need(isinstance(value, str) and SHA256.fullmatch(value) is not None,
             'ARTIFACT_CERTIFICATE_INVALID')
    need(data['app_certificate_sha256'] == data['test_certificate_sha256'],
         'ARTIFACT_SIGNERS_DIFFER')
    return {'app': app, 'test': test, 'manifest': data}

def installed_apk_path(serial: str, package: str) -> str:
    raw = adb(serial, 'shell', 'pm', 'path', package)
    lines = raw.decode('utf-8', 'replace').strip().splitlines()
    need(len(lines) == 1 and lines[0].startswith('package:/data/app/') and
         lines[0].endswith('/base.apk'), 'INSTALLED_APK_PATH_REFUSED')
    return lines[0][len('package:'):]

def installed_apk_sha(serial: str, package: str) -> str:
    path = installed_apk_path(serial, package)
    raw = adb(serial, 'shell', 'sha256sum', path)
    pieces = raw.decode('ascii', 'replace').strip().split()
    need(len(pieces) == 2 and SHA256.fullmatch(pieces[0]) is not None and pieces[1] == path,
         'INSTALLED_APK_PROVENANCE_UNAVAILABLE')
    return pieces[0]

def verify_installed(serial: str, apks: dict) -> None:
    need(installed_apk_sha(serial, PACKAGE) == apks['manifest']['app_sha256'],
         'INSTALLED_APP_HASH_MISMATCH')
    need(installed_apk_sha(serial, TEST_PACKAGE) == apks['manifest']['test_sha256'],
         'INSTALLED_TEST_HASH_MISMATCH')
    need(matching_instrumentation(adb(serial, 'shell', 'pm', 'list', 'instrumentation')),
         'INSTRUMENTATION_RUNNER_MISSING')

def maybe_install(serial: str, info: dict, apks: dict, *, allow_install: bool) -> bool:
    """Automatic, additive installation; never overwrite or wipe existing packages.

    Returns True only when both app and test were previously absent. Existing
    app data means a successful run remains diagnostic-only, even when the app
    bytes are exactly the source-pinned GitHub build.
    """
    app_present, test_present = info['app_installed'], info['test_installed']
    if app_present and test_present:
        verify_installed(serial, apks)
        return False
    need(allow_install, 'AUTOMATIC_APK_INSTALL_DISABLED')
    if app_present and not test_present:
        # Install the missing instrumentation APK, but only if the already
        # installed app is byte-for-byte the exact GitHub build. Never replace
        # a signed personal app, unknown version, or another test package.
        need(installed_apk_sha(serial, PACKAGE) == apks['manifest']['app_sha256'],
             'INSTALLED_APP_HASH_MISMATCH_NO_REPLACEMENT')
        report('INFO', 'INSTALLING_MISSING_MATCHING_INSTRUMENTATION_APK')
        adb(serial, 'install', '-t', str(apks['test']), timeout=160)
        verify_installed(serial, apks)
        return False
    need(not test_present, 'ORPHAN_INSTRUMENTATION_REFUSED_NO_REPLACEMENT')
    report('INFO', 'INSTALLING_FRESH_MATCHING_APP_AND_INSTRUMENTATION_APKS')
    adb(serial, 'install', '-t', str(apks['app']), timeout=160)
    adb(serial, 'install', '-t', str(apks['test']), timeout=160)
    verify_installed(serial, apks)
    return True

def repo_source(repo: Path) -> str:
    try:
        p = subprocess.run(['git', '-C', str(repo), 'rev-parse', 'HEAD'],
                           capture_output=True, text=True, timeout=12, check=False)
        origin = subprocess.run(['git', '-C', str(repo), 'remote', 'get-url', 'origin'],
                                capture_output=True, text=True, timeout=12, check=False)
    except (OSError, subprocess.TimeoutExpired):
        raise Refused('REPOSITORY_UNAVAILABLE') from None
    need(p.returncode == origin.returncode == 0 and
         SHA40.fullmatch(p.stdout.strip()) is not None, 'REPOSITORY_UNAVAILABLE')
    need(origin.stdout.strip() in {
        'git@github.com:Mikeyphw/studyflow.git','git@github.com:Mikeyphw/studyflow',
        'https://github.com/Mikeyphw/studyflow.git', 'https://github.com/Mikeyphw/studyflow',
        'ssh://git@github.com/Mikeyphw/studyflow.git',
        'ssh://git@github.com/Mikeyphw/studyflow'}, 'WRONG_REPOSITORY')
    return p.stdout.strip()

def gh_json(*args: str) -> dict | list:
    """Bounded GitHub CLI JSON with no token, raw logs or command output echoed."""
    try:
        proc = subprocess.run(['gh', *args], capture_output=True, text=True,
                              timeout=35, check=False)
    except (OSError, subprocess.TimeoutExpired):
        raise Refused('GH_CLI_UNAVAILABLE_OR_TIMED_OUT') from None
    need(proc.returncode == 0 and 0 < len(proc.stdout) < 200_000,
         'GH_AUTH_OR_QUERY_FAILED')
    try:
        return json.loads(proc.stdout)
    except (ValueError, TypeError):
        raise Refused('GH_JSON_INVALID') from None


def discover_exact_build_run(source: str) -> int:
    need(SHA40.fullmatch(source) is not None, 'INVALID_SOURCE_SHA')
    # GitHub Actions registration is eventually consistent: a missing run is
    # not an actual failed build. This is bounded (5 polls, <= ~50s).
    for attempt in range(5):
        runs = gh_json('run','list','-R',GITHUB_REPO,'--workflow',BUILD_WORKFLOW,
                       '--branch',SOURCE_BRANCH,'--limit','35',
                       '--json','databaseId,headSha,status,conclusion,createdAt')
        need(isinstance(runs, list) and len(runs) <= 35, 'BUILD_RUN_LIST_INVALID')
        matches=[x for x in runs if isinstance(x,dict) and x.get('headSha') == source]
        successful=[x for x in matches if x.get('status') == 'completed'
                    and x.get('conclusion') == 'success'
                    and isinstance(x.get('databaseId'),int) and x['databaseId'] > 0]
        if successful:
            return max(successful, key=lambda x:x.get('createdAt',''))['databaseId']
        if matches and all(x.get('status') == 'completed' for x in matches):
            raise Refused('EXACT_SOURCE_APK_BUILD_FAILED')
        if attempt != 4:
            time.sleep((2,4,8,12)[attempt])
    raise Refused('EXACT_SOURCE_APK_BUILD_NOT_YET_AVAILABLE')


def download_exact_apks(repo: Path, source: str, destination: Path) -> dict:
    """Fetch exactly the successful HEAD-matched build and verify its manifest."""
    run_id = discover_exact_build_run(source)
    response = gh_json('api',f'repos/{GITHUB_REPO}/actions/runs/{run_id}/artifacts',
                       '--method','GET')
    need(isinstance(response,dict) and isinstance(response.get('artifacts'),list),
         'BUILD_ARTIFACT_LIST_INVALID')
    pattern = re.compile(r'sfdt22-physical-apks-' + str(run_id) + r'-(\d+)\Z')
    artifacts = [x for x in response['artifacts'] if isinstance(x,dict)
                 and isinstance(x.get('name'),str)
                 and pattern.fullmatch(x['name']) and not x.get('expired',False)]
    need(len(artifacts) == 1, 'EXACT_BUILD_ARTIFACT_MISSING_OR_AMBIGUOUS')
    artifact = artifacts[0]
    attempt = int(pattern.fullmatch(artifact['name']).group(1))
    need(attempt > 0, 'BUILD_ARTIFACT_ATTEMPT_INVALID')
    try:
        proc = subprocess.run(['gh','run','download',str(run_id),'-R',GITHUB_REPO,
                               '--name',artifact['name'],'--dir',str(destination)],
                              capture_output=True, text=True, timeout=180, check=False)
    except (OSError,subprocess.TimeoutExpired):
        raise Refused('GH_ARTIFACT_DOWNLOAD_FAILED') from None
    need(proc.returncode == 0, 'GH_ARTIFACT_DOWNLOAD_FAILED')
    wanted={'app-debug.apk','app-debug-androidTest.apk','SFDT22_PHYSICAL_APKS.json'}
    actual={p.name for p in destination.iterdir()}
    need(actual == wanted, 'ARTIFACT_FILE_INVENTORY_MISMATCH')
    bundle = load_apks(destination,source)
    manifest=bundle['manifest']
    need(manifest['run_id'] == str(run_id) and manifest.get('run_attempt') == str(attempt),
         'ARTIFACT_RUN_ATTEMPT_MISMATCH')
    report('PASS', 'EXACT_APK_ARTIFACT run='+str(run_id)+' sha='+source)
    return bundle


def atomic_json(path: Path, value: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    data = (json.dumps(value, indent=2, sort_keys=True, ensure_ascii=True) + '\n').encode('ascii')
    need(len(data) < 20000, 'EVIDENCE_TOO_LARGE')
    with tempfile.NamedTemporaryFile(mode='wb', prefix='.sfdt22-', suffix='.json',
                                     dir=path.parent, delete=False) as out:
        tmp = Path(out.name)
        out.write(data); out.flush(); os.fsync(out.fileno())
    os.replace(tmp, path)

def perform_real_tests(serial: str, output: Path, source: str, provenance: dict,
                       *, qualifying: bool) -> bool:
    base.SERIAL = serial
    journey.SERIAL = serial
    # Never leave screenshots from a previous run resembling current evidence.
    for name in SCREEN_NAMES:
        candidate=output/('sfdt22-ui-'+name+'.png')
        need(not candidate.is_symlink(), 'SCREENSHOT_SYMLINK_REFUSED')
        candidate.unlink(missing_ok=True)
    evidence = dict(schema=SCHEMA, source_sha=source,
                    producer='sfdt22-physical-arm64-real-instrumentation',
                    qualification='NOT_QUALIFIED', physical_device_qualified=False,
                    release_qualified=False, publication_allowed=False,
                    raw_logs_retained=False, abi='arm64-v8a', api_level=36,
                    app_apk_sha256=provenance.get('app_sha256'),
                    test_apk_sha256=provenance.get('test_sha256'),
                    room_tests=[], sflp_tests=[], journeys=[], screenshots=[],
                    failure_phase='preflight')
    try:
        for name in ROOM_METHODS:
            evidence['failure_phase'] = 'room_regression'
            # Actual distinct Android instrumentation method, not source-only tests.
            p = subprocess.run(['adb','-s',serial,'shell','am','instrument','-w','-r',
                                '-e','class', PACKAGE+'.Sfdt27RoomSettingsRegressionTest#'+name,
                                INSTRUMENTATION], capture_output=True, timeout=240, check=False)
            reason = journey.sfdt27_room_result(p.stdout, name, p.returncode)
            need(reason == 'passed', 'ROOM_' + reason.upper())
            evidence['room_tests'].append(name)
            report('PASS', 'ROOM '+name)
        for name in SFLP_METHODS:
            evidence['failure_phase'] = 'sflp_regression'
            need(journey.sfdt27_sflp_clear_stage(), 'SFLP_STAGE_RESET_FAILED')
            p = subprocess.run(['adb','-s',serial,'shell','am','instrument','-w','-r',
                                '-e','class', PACKAGE+'.Sfdt27SflpReplayRegressionTest#'+name,
                                INSTRUMENTATION], capture_output=True, timeout=240, check=False)
            reason = journey.sfdt27_room_result(p.stdout, name, p.returncode)
            status, stage = journey.sfdt27_sflp_read_stage(name)
            need(reason == 'passed' and status == 'complete',
                 'SFLP_' + (reason.upper() if reason != 'passed' else 'STAGE_'+status.upper()))
            evidence['sflp_tests'].append(name)
            report('PASS', 'SFLP '+name)
        for name in EXPECTED_METHODS[:4]:
            evidence['failure_phase'] = 'journey_' + str(len(evidence['journeys'])+1)
            item = journey.run_test(name)
            need(item == dict(test=name, status='passed', started=1, finished=1),
                 'JOURNEY_RESULT_INVALID')
            evidence['journeys'].append(item)
            report('PASS', 'JOURNEY '+name)
        evidence['failure_phase'] = 'process_restart'
        journey.real_process_restart()
        evidence['process_restart_new_pid_verified'] = True
        report('PASS', 'ACTUAL_NEW_APP_PID')
        evidence['failure_phase'] = 'journey_5'
        name = EXPECTED_METHODS[-1]
        item = journey.run_test(name)
        need(item == dict(test=name, status='passed', started=1, finished=1),
             'JOURNEY_RESULT_INVALID')
        evidence['journeys'].append(item)
        evidence['failure_phase'] = 'screenshots'
        hashes=set()
        for name in SCREEN_NAMES:
            raw = adb(serial, 'exec-out', 'run-as', PACKAGE,
                      'cat', 'files/sfdt22-ui-' + name + '.png', timeout=30)
            width,height=base.png_dimensions(raw)
            digest=hashlib.sha256(raw).hexdigest()
            need(digest not in hashes, 'DUPLICATE_SCREENSHOT')
            hashes.add(digest)
            filename='sfdt22-ui-'+name+'.png'
            (output/filename).write_bytes(raw)
            evidence['screenshots'].append(dict(name=filename,width=width,height=height,
                                                size=len(raw),sha256=digest))
        # Explicitly preserve mandatory source-of-truth / production probes.
        evidence['failure_phase'] = 'boundary_probes'
        evidence['boundaries'] = journey.read_boundaries()
        need(evidence['boundaries'] == journey.expected_boundaries(5),
             'BOUNDARY_PROOF_INCOMPLETE')
        evidence['sfdt27_probes'] = journey.sfdt27_read_probes()
        need(evidence['sfdt27_probes'] == journey.sfdt27_expected_probes(),
             'SFLP_PROOF_INCOMPLETE')
        evidence['qualification']='PHYSICAL_PASS' if qualifying else 'DIAGNOSTIC_ONLY_PASS'
        evidence['physical_device_qualified']=bool(qualifying)
        evidence.pop('failure_phase',None)
        report('PASS', evidence['qualification'])
    except journey.JourneyFailure as e:
        evidence['failure_test']=e.test; evidence['failure_step']=e.stage
        evidence['failure_reason_code']=e.reason
        report('REFUSED', 'step='+e.stage+' reason='+e.reason)
    except journey.Sfdt27RoomFailure as e:
        evidence['failure_test']=e.test; evidence['failure_reason_code']=e.reason
        report('REFUSED', 'reason='+e.reason)
    except (Refused, ValueError, OSError, subprocess.TimeoutExpired) as e:
        evidence['failure_reason_code']=(str(e) if isinstance(e,Refused) else 'INSTRUMENTATION_FAILED')
        report('REFUSED', 'reason='+evidence['failure_reason_code'])
    finally:
        atomic_json(output/'SFDT22_PHYSICAL_EVIDENCE.json',evidence)
    return evidence['qualification'] in ('PHYSICAL_PASS','DIAGNOSTIC_ONLY_PASS')

def run(args: argparse.Namespace) -> int:
    repo = Path(__file__).resolve().parents[1]
    source = repo_source(repo)
    info = check_physical(args.serial)
    report('PASS', 'PREFLIGHT api=36 abi=arm64-v8a '
          'app_installed='+str(info['app_installed']).lower()+' instrumentation_ready='+
          str(info['instrumentation_ready']).lower())
    if args.preflight:
        report('INFO', 'read_only=true installer=disabled')
        return 0
    need(args.accept_device_tests, 'DEVICE_TEST_WRITE_AUTHORIZATION_REQUIRED')
    if args.run_existing:
        need(not args.install_apks and args.apk_dir is None,
             'EXISTING_MODE_MUST_NOT_INSTALL')
        need(args.accept_existing_data, 'EXISTING_DATA_AUTHORIZATION_REQUIRED')
        need(info['app_installed'] and info['test_installed'] and info['instrumentation_ready'],
             'MATCHING_INSTALLED_INSTRUMENTATION_MISSING')
        provenance = {}
        qualifies = False
        report('INFO', 'NONQUALIFYING_PREEXISTING_INSTRUMENTATION')
    else:
        # No local Gradle: fetch the exact successful GitHub APK pair unless a
        # caller explicitly supplies a previously verified artifact directory.
        if args.apk_dir:
            apks = load_apks(Path(args.apk_dir).expanduser(), source)
        else:
            with tempfile.TemporaryDirectory(prefix='sfdt22-apks-') as temporary:
                apks = download_exact_apks(repo,source,Path(temporary))
                return run_with_apks(args, info, apks, source)
        return run_with_apks(args,info,apks,source)
    return run_suite(args,source,provenance,qualifies)


def run_suite(args: argparse.Namespace, source: str, provenance: dict, qualifies: bool) -> int:
    requested_output = Path(args.output_dir).expanduser()
    need(not requested_output.is_symlink(), 'OUTPUT_SYMLINK_REFUSED')
    output = requested_output.resolve()
    output.mkdir(parents=True,exist_ok=True)
    return 0 if perform_real_tests(args.serial,output,source,provenance,qualifying=qualifies) else 2


def run_with_apks(args: argparse.Namespace, info: dict, apks: dict, source: str) -> int:
    # Prevent even installing the instrumentation APK unless the user has
    # acknowledged that this test may modify pre-existing StudyFlow data.
    if info['app_installed'] or info['test_installed']:
        need(args.accept_existing_data, 'EXISTING_DATA_AUTHORIZATION_REQUIRED')
    # Backwards compatible --install-apks; --run now installs the matched pair
    # automatically. No replacement/wipe of an existing package is permitted.
    freshly_installed = maybe_install(args.serial,info,apks,allow_install=True)
    if not freshly_installed:
        need(args.accept_existing_data, 'EXISTING_DATA_AUTHORIZATION_REQUIRED')
    provenance={'app_sha256':apks['manifest']['app_sha256'],
                'test_sha256':apks['manifest']['test_sha256']}
    qualifies = freshly_installed
    return run_suite(args,source,provenance,qualifies)

def main() -> int:
    parser=argparse.ArgumentParser(description=__doc__)
    group=parser.add_mutually_exclusive_group(required=True)
    group.add_argument('--preflight',action='store_true',help='Read-only; discover existing instrumentation')
    group.add_argument('--run',action='store_true',help='Use source-matched APKs, optionally install')
    group.add_argument('--run-existing',action='store_true',help='Nonqualifying, use existing instrumentation app')
    parser.add_argument('--serial',default='localhost:5555')
    parser.add_argument('--apk-dir',help='Optional artifact directory; omitted means auto-download exact matching GitHub build')
    parser.add_argument('--install-apks',action='store_true',help='Legacy compatibility; --run automatically installs missing verified APKs')
    parser.add_argument('--accept-device-tests',action='store_true',help='Explicitly allow real app-data mutations')
    parser.add_argument('--accept-existing-data',action='store_true',help='Explicitly accept running against existing app data (nonqualifying)')
    parser.add_argument('--output-dir',default='~/.local/share/studyflow/physical-sfdt22')
    args=parser.parse_args()
    if args.install_apks and not args.run:
        parser.error('--install-apks requires --run')
    try:
        return run(args)
    except Refused as e:
        report('REFUSED', 'reason='+str(e))
        return 2
    except (ValueError,OSError,subprocess.TimeoutExpired):
        report('REFUSED', 'reason=UNEXPECTED_HOST_FAILURE')
        return 2

if __name__=='__main__':
    raise SystemExit(main())
