#!/usr/bin/env python3
"""HF07 staged Android 36 diagnostics; never qualifies tests or releases.

`room-run` builds AND runs the real Room instrumented class with independent
fail-closed stage receipts. `report` emits a privacy-minimized summary after
any full/focused emulator result, including if the emulator never booted.
This module does not change real UI runner, evidence verifier, or test counts.
"""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import time
from xml.etree import ElementTree as ET

REPO_NAME = 'Mikeyphw/studyflow'
SERIAL = 'emulator-5554'
SCHEMA = 1
ROOM_CLASS = 'com.mikeyphw.studyflow.core.data.StudySessionAtomicStateTest'
ROOM_METHODS = frozenset({
    'linkedSessionCannotClaimDifferentActivityOwnershipMetadata',
    'cancellingSessionRestoresLinkedActivityToItsOriginalMissedState',
    'quickSessionCompletesAndPersistsWithoutPlannedActivity',
    'quickSessionCancelsAndPersistsWithoutPlannedActivity',
    'linkedSessionCompletionStillUpdatesPlannedActivity',
})
UI_PHASES = frozenset({'host','device','fresh','apks','install','installed_apk',
    'room_regression','plan','subjects','session','practice','force_stop',
    'post_restart','screenshots','diagnostic_complete','complete'})
# Static safety defaults for stand-alone tests; on the real runner these are
# further narrowed to the exact STAGES/REASON_CODES/TESTS declared by the real
# verified SFDT22 instrumentation harness.
SAFE_UI_STAGES = frozenset({'session_start', 'session_running',
    'session_finish_confirm','session_completion_persisted', 'session_completion_ui',
    'practice_open_root', 'plan_save', 'topic_save'})
SAFE_UI_REASONS = frozenset({'session_finish_operation_failed',
    'session_ui_not_settled', 'session_status_not_completed', 'runner_nonzero_exit',
    'test_failed'})
ROOM_STAGES = frozenset({'android_room_preflight','build_test_apk',
    'execute_room_tests','room_test_discovery','room_finished'})
ROOM_REASONS = frozenset({'none','room_build_timeout','room_build_failed',
    'room_runtime_timeout','room_instrumentation_failed',
    'room_test_discovery_incomplete'})
ROOM_PATH = 'HF07_ROOM.json'
SUMMARY_PATH = 'HF07_DIAGNOSTICS.json'
EVIDENCE_PATH = 'SFDT22_EVIDENCE.json'
CODE_RE = re.compile(r'^[a-z][a-z0-9_]{0,79}$')
SHA_RE = re.compile(r'^[a-f0-9]{40}$')
POSITIVE_RE = re.compile(r'^[1-9][0-9]{0,15}$')


class Refused(Exception):
    pass


def need(ok: bool, reason: str) -> None:
    if not ok:
        raise Refused(reason)


def identity(args: argparse.Namespace, *, require_emulator: bool) -> Path:
    """Reject manual/physical/mismatched-source invocation before any actions."""
    repo = Path(__file__).resolve().parents[1]
    sha = subprocess.run(['git', '-C', str(repo), 'rev-parse', 'HEAD'],
        capture_output=True, text=True, timeout=20)
    need(sha.returncode == 0 and sha.stdout.strip() == args.source_sha,
         'source_sha_mismatch')
    need(bool(SHA_RE.fullmatch(args.source_sha)), 'source_sha_invalid')
    need(bool(POSITIVE_RE.fullmatch(args.run_id)), 'run_id_invalid')
    need(bool(POSITIVE_RE.fullmatch(args.attempt)), 'run_attempt_invalid')
    need(os.environ.get('GITHUB_ACTIONS') == 'true' and
         os.environ.get('RUNNER_OS') == 'Linux' and
         os.environ.get('RUNNER_ARCH') == 'X64' and
         os.environ.get('GITHUB_REPOSITORY') == REPO_NAME and
         os.environ.get('GITHUB_SHA') == args.source_sha and
         os.environ.get('GITHUB_RUN_ID') == args.run_id and
         os.environ.get('GITHUB_RUN_ATTEMPT') == args.attempt,
         'github_identity_mismatch')
    need(os.environ.get('STUDYFLOW_SFDT21_HOSTED_EMULATOR') == 'true' and
         os.environ.get('STUDYFLOW_SFDT22_HOSTED_JOURNEYS') == 'true',
         'debug_emulator_gate_missing')
    need(not any(os.environ.get(name) for name in (
         'STUDYFLOW_RELEASE_STORE_PASSWORD', 'STUDYFLOW_RELEASE_KEY_PASSWORD',
         'STUDYFLOW_RELEASE_STORE_FILE')), 'release_secrets_unexpected')
    output = args.output_dir
    expected = Path(os.environ.get('RUNNER_TEMP', '/does-not-exist')) / 'sfdt22-emulator'
    need(output.is_absolute() and not output.is_symlink() and
         output == expected and output.parent.is_dir() and
         not output.parent.is_symlink(), 'output_dir_untrusted')
    output.mkdir(parents=True, exist_ok=True)
    if require_emulator:
        need(os.uname().machine == 'x86_64', 'host_arch_invalid')
        for command, value, reason in (
            (('get-state',), 'device', 'adb_device_unavailable'),
            (('shell','getprop','ro.kernel.qemu'), '1', 'physical_device_refused'),
            (('shell','getprop','ro.build.version.sdk'), '36', 'api36_required'),
        ):
            proc = subprocess.run(['adb','-s',SERIAL,*command],
                capture_output=True, text=True, timeout=15)
            need(proc.returncode == 0 and proc.stdout.strip().replace('\r','') == value,
                 reason)
    return output


def read_receipt(path: Path, max_bytes: int = 131072) -> dict | None:
    if not path.exists():
        return None
    need(path.is_file() and not path.is_symlink() and path.stat().st_size <= max_bytes,
         'receipt_untrusted')
    try:
        result = json.loads(path.read_text(encoding='utf-8'))
    except (ValueError, UnicodeError, OSError) as exc:
        raise Refused('receipt_invalid') from exc
    need(isinstance(result, dict), 'receipt_not_object')
    return result


def atomic_receipt(path: Path, fields: dict) -> None:
    need(not path.is_symlink(), 'receipt_symlink_refused')
    need(path.parent.is_dir() and not path.parent.is_symlink(), 'receipt_dir_refused')
    text = json.dumps(fields, sort_keys=True, separators=(',', ':')) + '\n'
    need(len(text) <= 16000, 'receipt_too_large')
    fd, temp = tempfile.mkstemp(prefix='.hf07-receipt-', dir=path.parent)
    try:
        with os.fdopen(fd, 'w', encoding='utf-8') as out:
            out.write(text)
            out.flush()
            os.fsync(out.fileno())
        os.replace(temp, path)
    finally:
        if os.path.exists(temp):
            os.unlink(temp)


def base_identity(args: argparse.Namespace, mode: str) -> dict:
    return dict(schema=SCHEMA, producer='studyflow-hf07-staged-real-api36',
        source_sha=args.source_sha, run_id=args.run_id, attempt=args.attempt,
        mode=mode, diagnostic_only=(mode == 'focused'),
        release_qualified=False, publication_allowed=False,
        physical_device_qualified=False, raw_logs_retained=False)


def room_failures(repo: Path) -> dict:
    """Read bounded, whitelisted test names only; not assertion/data/log text."""
    base = repo / 'core/data/build/outputs/androidTest-results/connected'
    if not base.is_dir() or base.is_symlink():
        return {'report_state': 'not_emitted', 'failing_tests': [], 'reported_tests': []}
    found = set()
    failing = set()
    for path in sorted(base.rglob('*.xml')):
        if len(found) >= 12:
            break
        if path.is_symlink() or not path.is_file() or path.stat().st_size > 512_000:
            continue
        try:
            root = ET.parse(path).getroot()
        except (ET.ParseError, OSError, ValueError):
            continue
        for tc in root.iter('testcase'):
            if tc.attrib.get('classname') != ROOM_CLASS:
                continue
            name = tc.attrib.get('name')
            if name not in ROOM_METHODS:
                continue
            found.add(name)
            if tc.find('failure') is not None or tc.find('error') is not None:
                failing.add(name)
    return {'report_state': 'found' if found else 'no_room_cases',
            'failing_tests': sorted(failing), 'reported_tests': sorted(found)}


def run_gradle(repo: Path, output: Path, args: argparse.Namespace) -> int:
    base = base_identity(args, 'full')
    record = {**base, 'status':'RUNNING', 'step':'android_room_preflight',
              'reason':'none', 'failed_task':'none', 'stages': [],
              'room': {'report_state':'not_emitted','failing_tests':[], 'reported_tests':[]}}
    path = output / ROOM_PATH
    atomic_receipt(path, record)
    tasks = (
        ('build_test_apk', ':core:data:assembleDebugAndroidTest'),
        ('execute_room_tests', ':core:data:connectedDebugAndroidTest'),
    )
    for stage, task in tasks:
        print('HF07_STAGE_BEGIN=' + stage, flush=True)
        entry = {'stage':stage,'status':'RUNNING','task':task}
        record['stages'].append(entry)
        record['step'] = stage
        atomic_receipt(path, record)
        # Gradle's own compiler/instrumentation output remains visible in GitHub
        # logs for debugging; no raw output is copied to uploaded artifacts.
        cmd = ['./gradlew','--no-daemon','--max-workers=2',
               '--console=plain','--stacktrace',
               '-Pandroid.testInstrumentationRunnerArguments.class=' + ROOM_CLASS,
               task]
        start = time.monotonic()
        try:
            result = subprocess.run(cmd, cwd=repo, timeout=1800, check=False)
            rc = result.returncode
        except subprocess.TimeoutExpired:
            rc = 124
        elapsed = int(time.monotonic() - start)
        entry['duration_seconds'] = elapsed
        entry['status'] = 'PASS' if rc == 0 else 'FAIL'
        entry['exit_code'] = rc
        if rc:
            record['status'] = 'FAILED'
            record['reason'] = ('room_build_timeout' if rc == 124 else 'room_build_failed') if stage == 'build_test_apk' else (
                'room_runtime_timeout' if rc == 124 else 'room_instrumentation_failed')
            record['failed_task'] = task
            record['room'] = room_failures(repo)
            atomic_receipt(path, record)
            print('HF07_STAGE_FAIL=' + stage + ' reason=' + record['reason'] +
                  ' test_methods=' + ','.join(record['room']['failing_tests']), flush=True)
            return 2
        atomic_receipt(path, record)
        print('HF07_STAGE_PASS=' + stage, flush=True)
    record['room'] = room_failures(repo)
    if (set(record['room']['reported_tests']) != ROOM_METHODS or
            record['room']['failing_tests']):
        record['status'] = 'FAILED'
        record['step'] = 'room_test_discovery'
        record['reason'] = 'room_test_discovery_incomplete'
        atomic_receipt(path, record)
        print('HF07_STAGE_FAIL=room_test_discovery reason=room_test_discovery_incomplete',
              flush=True)
        return 2
    record['status'] = 'PASSED'
    record['step'] = 'room_finished'
    record['reason'] = 'none'
    atomic_receipt(path, record)
    print('HF07_REAL_ROOM_5_METHODS_PASS', flush=True)
    return 0


def allow_code(value: object, allowed: frozenset | None = None) -> str:
    if not isinstance(value, str) or CODE_RE.fullmatch(value) is None:
        return 'unavailable'
    if allowed is not None and value not in allowed:
        return 'unavailable'
    return value


def build_summary(evidence: dict | None, room: dict | None, mode: str,
                  args: argparse.Namespace) -> dict:
    report = base_identity(args, mode)
    report['ui_status'] = 'NOT_RUN'
    report['ui_phase'] = 'unavailable'
    report['ui_test'] = 'unavailable'
    report['ui_stage'] = 'unavailable'
    report['ui_reason'] = 'unavailable'
    report['verified_before_failure'] = 0
    if evidence is not None:
        overall = evidence.get('overall')
        if overall == 'NOT_RUN':
            report['ui_status'] = 'NOT_RUN'
        elif overall == ('DIAGNOSTIC_PASS_NOT_RELEASE_QUALIFIED' if mode=='focused'
                          else 'PASS_HOSTED_REAL_STUDY_JOURNEYS'):
            report['ui_status'] = 'PASSED_DIAGNOSTIC' if mode == 'focused' else 'PASSED_FULL'
        else:
            report['ui_status'] = 'FAILED_OR_INCOMPLETE'
        # No free-form code or user-supplied string reaches public diagnostics.
        # Exact underlying harness allowlists preserve the complete UI taxonomy.
        try:
            import sfdt22_emulator_verify as verifier
            tests = frozenset(verifier.TESTS)
            stages = frozenset(verifier.STAGES)
            reasons = frozenset(verifier.REASON_CODES)
            phases = frozenset(verifier.PHASES)
        except (ImportError, AttributeError):
            tests = frozenset()
            stages = SAFE_UI_STAGES
            reasons = SAFE_UI_REASONS
            phases = UI_PHASES
        report['ui_phase'] = allow_code(evidence.get('failure_phase'), phases)
        report['ui_stage'] = allow_code(evidence.get('failure_step'), stages)
        report['ui_reason'] = allow_code(evidence.get('failure_reason_code'), reasons)
        # Emit only the reviewed test identifiers, never arbitrary test names.
        test = evidence.get('failure_test')
        if isinstance(test, str) and test in tests:
            report['ui_test'] = test
        count = evidence.get('completed_before_failure', 0)
        if isinstance(count, int) and not isinstance(count,bool) and 0 <= count <= 5:
            report['verified_before_failure'] = count
    if mode == 'full':
        report['room_status'] = 'NOT_RUN'
        report['room_stage'] = 'unavailable'
        report['room_reason'] = 'unavailable'
        report['room_failing_methods'] = []
        if room is not None:
            report['room_status'] = room.get('status') if room.get('status') in (
                'RUNNING','FAILED','PASSED') else 'UNKNOWN'
            report['room_stage'] = allow_code(room.get('step'), ROOM_STAGES)
            report['room_reason'] = allow_code(room.get('reason'), ROOM_REASONS)
            tests = room.get('room', {}).get('failing_tests', []) if isinstance(room.get('room'),dict) else []
            if isinstance(tests, list):
                report['room_failing_methods'] = sorted(set(t for t in tests if isinstance(t, str) and t in ROOM_METHODS))
    report['status'] = ('ALL_UI_TESTS_PASSED_NOT_RELEASE_QUALIFIED' if mode=='focused'
         else 'RUNTIME_PASSED_AWAIT_INDEPENDENT_VERIFIER') if (
        report['ui_status'] in ('PASSED_DIAGNOSTIC','PASSED_FULL') and
        (mode=='focused' or report.get('room_status')=='PASSED')) else 'FAILED_OR_INCOMPLETE'
    return report


def write_report(output: Path, args: argparse.Namespace) -> dict:
    evidence = read_receipt(output / EVIDENCE_PATH)
    room = read_receipt(output / ROOM_PATH) if args.mode == 'full' else None
    record = build_summary(evidence, room, args.mode, args)
    atomic_receipt(output / SUMMARY_PATH, record)
    line = ('HF07_DIAG=' + record['status'] + ' room=' + record.get('room_status','not_applicable') +
        ' room_stage=' + record.get('room_stage','none') +
        ' room_failure=' + ','.join(record.get('room_failing_methods',[])) +
        ' ui=' + record['ui_status'] + ' phase=' + record['ui_phase'] +
        ' test=' + record['ui_test'] + ' stage=' + record['ui_stage'] +
        ' reason=' + record['ui_reason'])
    print(line, flush=True)
    path = os.environ.get('GITHUB_STEP_SUMMARY')
    if path:
        try:
            with open(path,'a',encoding='utf-8') as report:
                report.write('### StudyFlow HF07 — sanitized Android diagnostic report\n\n')
                report.write('`' + line + '`\n\n')
                report.write('Full acceptance requires the independent SFDT22 verifier and green gate.\n')
        except OSError:
            print('HF07_SUMMARY_UNAVAILABLE', file=sys.stderr)
    return record


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=('room-run','report'))
    parser.add_argument('--mode', choices=('full','focused'), required=True)
    parser.add_argument('--output-dir', type=Path, required=True)
    parser.add_argument('--source-sha', required=True)
    parser.add_argument('--run-id', required=True)
    parser.add_argument('--attempt', required=True)
    a=parser.parse_args(argv)
    try:
        need(a.action != 'room-run' or a.mode == 'full', 'room_only_full')
        output = identity(a, require_emulator=a.action == 'room-run')
        if a.action == 'room-run':
            return run_gradle(Path(__file__).resolve().parents[1], output, a)
        write_report(output, a)
        return 0
    except (Refused, OSError, subprocess.TimeoutExpired) as error:
        # The fixed category identifies failures without leaking raw exceptions.
        code = error.args[0] if isinstance(error,Refused) else 'host_internal_error'
        print('HF07_REFUSED=' + allow_code(code), file=sys.stderr, flush=True)
        return 2


if __name__ == '__main__':
    raise SystemExit(main())
