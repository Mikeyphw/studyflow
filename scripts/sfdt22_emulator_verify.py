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
          'plan', 'subjects', 'session', 'practice', 'room_regression', 'force_stop',
          'post_restart', 'screenshots', 'complete')
SHA256 = re.compile(r'^[0-9a-f]{64}$')
STAGES = frozenset({
    'settings_after_create', 'settings_after_subject', 'settings_after_topic',
    'sfdt27_after_subject', 'sfdt27_before_topic', 'sfdt27_after_topic_action', 'sfdt27_after_topic_saved',
    'session_route_identity', 'settings_before_session_start',
    'plan_shell', 'plan_system_hub', 'plan_all_plans', 'plan_open',
    'plan_editor_ready', 'plan_name', 'plan_target', 'plan_preferences',
    'plan_save', 'plan_confirm', 'plan_capture',
    'subject_open_root', 'subject_open_content', 'subject_open_manager',
    'subject_open_dialog', 'subject_fill_name', 'subject_save',
    'subject_confirm', 'subject_plan_identity', 'subject_owner_confirm',
    'subject_capture', 'topic_open_dialog',
    'topic_fill_name', 'topic_save', 'topic_save_feedback', 'topic_expand',
    'topic_confirm', 'topic_capture',
    'session_open_root', 'session_open_overview', 'session_open_sessions', 'session_quick_dialog', 'session_title', 'session_start', 'session_running', 'session_pause', 'session_paused', 'session_resume', 'session_resumed', 'session_finish_open', 'session_finish_dialog', 'session_finish_notes', 'session_finish_confirm', 'session_finished', 'session_capture',
    'practice_open_questions', 'practice_open_reviews',
    'restart_open_content', 'restart_open_overview', 'restart_open_questions', 'restart_open_reviews',
})
TOPIC_CAUSE_CODES = frozenset({'settings_before_topic', 'settings_before_progress', 'settings_changed_progress', 'settings_changed_topic', 'sqlite_constraint', 'sqlite_locked', 'sqlite_other', 'invalid_input', 'unexpected'})
REASON_CODES = frozenset({
    'subject_card_missing',
    'workspace_plan_mismatch', 'subject_owner_mismatch', 'subject_owner_lookup_failed',
    'subject_editor_still_open',
    'topic_add_button_ambiguous',
    'topic_editor_tag_missing',
    'topic_row_missing', 'topic_row_not_composed',
    'topic_save_failed', *('topic_save_' + code for code in TOPIC_CAUSE_CODES),
    'subject_expand_action_missing', 'subject_not_expanded',
    'topic_search_editor_missing', 'topic_search_write_failed',
    'subject_expand_snackbar_obstruction', 'subject_expand_touch_failed',
    'subject_expand_semantic_click_failed', 'subject_expand_state_ambiguous',
    'topic_editor_still_open',
    'session_control_not_ready', 'session_editor_not_ready',
    'session_start_validation_failed', 'session_start_plan_settings_failed',
    'session_start_plan_settings_missing', 'session_start_plan_settings_lookup_failed', 'session_start_already_active', 'session_start_active_lookup_failed', 'session_start_persistence_failed', 'session_start_diagnostic_ambiguous',
    'session_start_failed', 'session_start_success_without_running_ui', 'session_running_control_missing', 'session_running_action_missing', 'session_running_control_ambiguous', 'session_running_control_disabled', 'session_running_control_offscreen',
    'inner_tab_missing', 'inner_tab_touch_failed',
    'inner_tab_not_selected', 'inner_pane_not_ready',
    'plan_content_tab_missing',
    'plan_content_tab_tap_failed',
    'plan_content_tab_not_selected',
    'plan_content_pane_not_ready',
    'topic_add_button_missing',
    'topic_fallback_touch_failed', 'topic_snackbar_obstruction',
    'topic_dialog_missing',
    'topic_dialog_ambiguous',
    'topic_editable_missing',
    'topic_value_not_written',
    'no_matching_compose_node', 'ambiguous_compose_node',
    'compose_wait_timeout', 'compose_assertion', 'app_process_crashed',
    'test_failed', 'unexpected_runner_status', 'runner_nonzero_exit',
    *('boundary_' + point + '_' + state for point in
      ('after_create', 'after_subject', 'after_topic', 'before_session_start') for state in
      ('missing', 'lookup_failed', 'ambiguous', 'mismatched')),
    'boundary_session_route_mismatched',
})


# Only these exact categories may be emitted; names, ids and raw Android output are forbidden.
BOUNDARY_POINTS = ('after_create', 'after_subject', 'after_topic', 'session_route', 'before_session_start')
BOUNDARY_STATES = frozenset({'present', 'missing', 'lookup_failed', 'ambiguous', 'mismatched', 'matched'})
# DIAG-01: device-private ASCII is parsed and RE-EMITTED, never uploaded raw.
SFDT27_TRACE_AREAS = frozenset({'ui','android_test','content_repository','plan_repository','seed','database_sql','database_lifecycle','portability','sync_restore','sample_provision'})
SFDT27_TRACE_POINTS = frozenset({
    'PLAN_CREATED','PLAN_UPDATED','SUBJECT_TRANSACTION_START','SUBJECT_COMMIT',
    'TOPIC_UI_CLICK','TOPIC_COROUTINE_START','TOPIC_TRANSACTION_START','TOPIC_PRECHECK',
    'TOPIC_UPSERT_DONE','PROGRESS_PRECHECK','PROGRESS_UPDATED',
    'TOPIC_TRANSACTION_COMMIT','TOPIC_TRANSACTION_ROLLBACK','TOPIC_POSTCOMMIT_READ_ERROR','TOPIC_POSTCOMMIT_VERIFY','DATABASE_OPEN',
    'TOPIC_REPOSITORY_RETURN','TOPIC_UI_FEEDBACK_REQUESTED',
    'TOPIC_EXTERNAL_PROBE_ACTION','TOPIC_EXTERNAL_PROBE_SAVED','PLAN_DELETE',
     'SEED_PASS','SETTINGS_WRITTEN','SETTINGS_OVERWRITE','TRACE_RESET',
    'SQL_PLAN_MUTATION','SQL_SETTINGS_MUTATION','DATABASE_HANDLE_OPEN',
    'PRODUCTION_AFTER_SUBJECT','PRODUCTION_BEFORE_TOPIC','PRODUCTION_AFTER_TOPIC_ACTION',
    'PRODUCTION_AFTER_TOPIC_SAVED','PRODUCTION_BEFORE_FEEDBACK','PRODUCTION_AFTER_FEEDBACK',
    'PORTABILITY_IMPORT_START','PORTABILITY_SNAPSHOT_RESTORE_START',
    'SYNC_SNAPSHOT_RESTORE_START','SYNC_SNAPSHOT_RESTORE_COMMIT',
    'SYNC_SNAPSHOT_RESTORE_ROLLBACK','SYNC_RESTORE_REQUESTED','SAMPLE_PLAN_INSERTED',
})
SFDT27_TRACE_OPERATIONS = frozenset({'none','create_plan','update_plan','delete_plan',
    'upsert_subject','upsert_topic','update_progress','insert_settings','replace_settings','seed','read_settings','sql_insert','sql_update','sql_replace','sql_delete','restore_graph','restore_snapshot','sync_restore','provision_sample'})
SFDT27_TRACE_TRANSACTIONS = frozenset({'not_started','begun','write_seen','committed','rolled_back','unknown'})
SFDT27_TRACE_OBSERVERS = frozenset({'none','ui','production_room','independent_pair','sql_callback'})
SFDT27_TRACE_STATES = frozenset({'present','missing','changed','unknown','unavailable'})
SFDT27_TRACE_ERRORS = frozenset({'none','constraint','busy','locked','io','corruption',
    'unexpected','probe_error','cancelled','writer_unresolved',
    'settings_before_topic','settings_before_progress','settings_changed_progress',
    'settings_changed_topic','invalid_input'})

def sfdt27_diag_parse(raw: bytes) -> list[dict]:
    require(3 <= len(raw) <= 16384 and raw.startswith(b'V2\n') and raw.endswith(b'\n'),
            'DIAG-01 missing/invalid atomic trace')
    # Strict ASCII catches Unicode and non-printing payloads. No ignored keys.
    require(all(byte == 10 or 32 <= byte <= 126 for byte in raw),
            'DIAG-01 noncanonical ASCII/control bytes')
    rows = raw.decode('ascii', 'strict').splitlines()
    require(rows[0] == 'V2' and 1 <= len(rows)-1 <= 64, 'DIAG-01 event count')
    events = []
    previous = None
    enums = (SFDT27_TRACE_AREAS,SFDT27_TRACE_POINTS,SFDT27_TRACE_OPERATIONS,
             SFDT27_TRACE_TRANSACTIONS,SFDT27_TRACE_OBSERVERS,SFDT27_TRACE_STATES,
             SFDT27_TRACE_STATES,SFDT27_TRACE_ERRORS)
    keys = ('area','checkpoint','operation','transaction','observer','plan','settings','error')
    for row in rows[1:]:
        parts = row.split('|')
        require(len(parts) == 9 and parts[0].isascii() and parts[0].isdigit() and
                (parts[0] == '0' or not parts[0].startswith('0')),
                'DIAG-01 event field count/sequence')
        seq = int(parts[0])
        require(seq > 0 and (previous is None or seq == previous + 1),
                'DIAG-01 duplicate/out-of-order/gap')
        previous = seq
        require(all(value in allowed for value,allowed in zip(parts[1:],enums)),
                'DIAG-01 unreviewed enum/PII')
        events.append({'seq':seq, **dict(zip(keys,parts[1:]))})
    # The partial record may start mid-journey after ring rollover. But two
    # observed ordered transaction checkpoints must never appear reversed.
    positions = {e['checkpoint']: i for i,e in enumerate(events)}
    require('TRACE_RESET' not in positions or positions['TRACE_RESET'] == 0,
            'DIAG-01 noninitial reset')
    for before,after in (
        ('TOPIC_UI_CLICK','TOPIC_COROUTINE_START'),
        ('TOPIC_TRANSACTION_START','TOPIC_PRECHECK'),
        ('TOPIC_PRECHECK','TOPIC_UPSERT_DONE'),
        ('TOPIC_PRECHECK','TOPIC_TRANSACTION_COMMIT'),
        ('TOPIC_UPSERT_DONE','TOPIC_TRANSACTION_COMMIT'),
        ('TOPIC_TRANSACTION_COMMIT','TOPIC_POSTCOMMIT_VERIFY'),
        ('TOPIC_EXTERNAL_PROBE_ACTION','TOPIC_EXTERNAL_PROBE_SAVED'),
    ):
        require(before not in positions or after not in positions or positions[before] < positions[after],
                'DIAG-01 reordered checkpoints')
    return events

def sfdt27_diag_read() -> tuple[str, list[dict]]:
    try:
        p = subprocess.run(['adb','-s',SERIAL,'exec-out','run-as',PACKAGE,
                            'cat','files/sfdt27-diag-trace.txt'],
                           capture_output=True,timeout=20,check=False)
        if p.returncode: return ('capture_unavailable',[])
        try:
            return ('captured',sfdt27_diag_parse(p.stdout))
        except (ValueError,UnicodeError):
            return ('capture_invalid',[])
    except (OSError,subprocess.TimeoutExpired):
        return ('capture_unavailable',[])

def sfdt27_diag_attach(evidence: dict) -> None:
    status, events = sfdt27_diag_read()
    evidence['sfdt27_diag_capture'] = status
    evidence['sfdt27_diag_trace_schema'] = 2
    evidence['sfdt27_diag_events'] = events
    missing = next((e for e in events if e['settings'] == 'missing'),None)
    evidence['sfdt27_diag_first_missing'] = (missing['checkpoint'] if missing else 'not_observed')
    # A missing row is NOT enough to prove a deleting writer. Do not fabricate causality.
    evidence['sfdt27_diag_cause'] = ('writer_unresolved' if missing else 'not_established')
    evidence['diagnostic_only'] = True

SFDT27_POINTS = ('after_subject', 'before_topic', 'after_topic_action', 'after_topic_saved')
SFDT27_STATES = frozenset({
    'matched', 'ui_marker_missing', 'lookup_failed', 'plan_ambiguous',
    'different_plan_id', 'subject_not_owned', 'room_missing_sql_present',
    'room_present_sql_missing', 'row_missing', 'read_only_snapshot_mismatch',
    'row_changed', 'baseline_missing',
})


def sfdt27_parse_probes(raw: bytes) -> list[dict]:
    require(len(raw) <= 384, 'HF27 probe exceeds fixed bound')
    records = []
    for line in raw.decode('ascii', 'strict').splitlines():
        parts = line.split('|')
        require(len(parts) == 2 and parts[0] in SFDT27_POINTS and
                parts[1] in SFDT27_STATES, 'HF27 probe contains an unreviewed value')
        require(parts[0] not in {r['point'] for r in records}, 'HF27 duplicate probe')
        records.append({'point': parts[0], 'state': parts[1]})
    require([x['point'] for x in records] == list(SFDT27_POINTS[:len(records)]),
            'HF27 probe order mismatch')
    return records


def sfdt27_read_probes() -> list[dict]:
    p = subprocess.run(['adb', '-s', SERIAL, 'exec-out', 'run-as', PACKAGE,
                        'cat', 'files/sfdt27-probes.txt'],
                       capture_output=True, timeout=20, check=False)
    return [] if p.returncode else sfdt27_parse_probes(p.stdout)


def sfdt27_expected_probes() -> list[dict]:
    return [{'point': p, 'state': 'matched'} for p in SFDT27_POINTS]


SFDT27_SFLP_STAGE_FILE = 'files/sfdt27-sflp-stage-v1.txt'
SFDT27_SFLP_STAGES = frozenset({
    'SFLP_SETUP', 'SFLP_FIXTURE', 'SFLP_BRIDGE', 'SFLP_EXISTING',
    'SFLP_CAPTURE', 'SFLP_REPLAY', 'SFLP_VERIFY', 'SFLP_REOPEN',
    'SFLP_TAMPER', 'SFLP_NEW', 'SFLP_INSERT', 'SFLP_DELETE',
    'SFLP_SAVE', 'SFLP_CLEANUP', 'SFLP_CLOSE', 'SFLP_DELETE_DB',
    'SFLP_COMPLETE',
})

SFDT27_ROOM_TESTS = (
    'customizedSettingsAndOwnershipSurviveSubjectTopicAndColdRoomReopen',
    'failedTopicTransactionRollsBackWithoutChangingPersonalizedSettings',
)
SFDT27_SFLP_TESTS = (
    'existingPlanReplayPreservesSettingsAndOwnedContentAfterColdReopen',
    'noncanonicalPeerKeysAndValuesFailWithoutMutation',
    'newPlanReplayInsertsAndExplicitDeleteStillWorks',
)
SFDT27_ROOM_STAGES = frozenset({
    'PLAN_SETUP', 'SUBJECT_TOPIC', 'PLAN_UPDATE', 'COLD_REOPEN',
    'ROLLBACK_SETUP', 'ROLLBACK_WRITE', 'ROLLBACK_VERIFY',
    'SFLP_EXISTING', 'SFLP_SETUP', 'SFLP_FIXTURE', 'SFLP_BRIDGE', 'SFLP_CLEANUP', 'SFLP_CLOSE',
    'SFLP_CAPTURE', 'SFLP_REPLAY', 'SFLP_VERIFY', 'SFLP_REOPEN',
    'SFLP_TAMPER', 'SFLP_NEW', 'SFLP_INSERT', 'SFLP_DELETE', 'SFLP_SAVE',
    'SFLP_DELETE_DB', 'SFLP_COMPLETE',
})
SFDT27_ROOM_CODES = frozenset({
    'runner_nonzero', 'runner_timeout', 'runner_no_test_status',
    'runner_bad_status', 'runner_output_oversize', 'test_failed',
    'runner_test_failed_unstaged', 'runner_instrumentation_failed',
    'runner_stage_receipt_unavailable', 'runner_stage_receipt_invalid',
    'runner_stage_receipt_incomplete', 'runner_stage_receipt_reset_failed',
    *('stage_' + stage.lower() for stage in SFDT27_ROOM_STAGES),
})


class Sfdt27RoomFailure(ValueError):
    def __init__(self, test: str, reason: str) -> None:
        require((test in SFDT27_ROOM_TESTS or test in SFDT27_SFLP_TESTS) and reason in SFDT27_ROOM_CODES,
                'Unreviewed HF27 Room/SFLP failure category')
        self.test = test
        self.reason = reason
        super().__init__('HF27_ROOM_FAILED')


def sfdt27_room_result(raw: bytes, test: str, return_code: int) -> str:
    # Allowlist-only result; raw Android output is never persisted or printed.
    require(test in SFDT27_ROOM_TESTS or test in SFDT27_SFLP_TESTS, 'Unknown HF27 Room/SFLP test')
    if len(raw) > 65536:
        return 'runner_output_oversize'
    output = raw.decode('utf-8', 'replace')
    tests = re.findall(r'(?m)^INSTRUMENTATION_STATUS: test=([^\r\n]+)\r?$', output)
    codes = re.findall(r'(?m)^INSTRUMENTATION_STATUS_CODE: ([^\r\n]+)\r?$', output)
    result = re.findall(r'(?m)^INSTRUMENTATION_CODE: ([^\r\n]+)\r?$', output)
    if (return_code == 0 and set(tests) == {test} and codes.count('1') == 1 and codes.count('0') == 1
            and len(codes) == 2 and result == ['-1'] and 'INSTRUMENTATION_FAILED' not in output
            and 'FAILURES!!!' not in output):
        return 'passed'
    markers = set(re.findall(r'SFDT27_ROOM_STAGE_([A-Z_]+)', output))
    if len(markers) == 1 and next(iter(markers)) in SFDT27_ROOM_STAGES:
        return 'stage_' + next(iter(markers)).lower()
    # Classify real Android test failures even if they occur in @Before/@After
    # and never reach the test body's stage guard. These are ALWAYS red.
    if 'INSTRUMENTATION_FAILED' in output:
        return 'runner_instrumentation_failed'
    if tests and set(tests) == {test} and '-2' in codes:
        return 'runner_test_failed_unstaged'
    if return_code != 0:
        return 'runner_nonzero'
    if not tests:
        return 'runner_no_test_status'
    if set(tests) != {test} or codes.count('1') != 1 or codes.count('0') != 1 or len(codes) != 2 or result != ['-1']:
        return 'runner_bad_status'
    return 'test_failed'


def sfdt27_hosted_room_regression() -> None:
    # Separate true Android/JUnit instrumentation invocations: each case must
    # explicitly report 1 start, 1 finish, and success. No raw logs/artifacts.
    for test in SFDT27_ROOM_TESTS:
        try:
            process = subprocess.run(
                ['adb', '-s', SERIAL, 'shell', 'am', 'instrument', '-w', '-r',
                 '-e', 'class', PACKAGE + '.Sfdt27RoomSettingsRegressionTest#' + test,
                 RUNNER], capture_output=True, timeout=180, check=False,
            )
        except subprocess.TimeoutExpired:
            raise Sfdt27RoomFailure(test, 'runner_timeout') from None
        reason = sfdt27_room_result(process.stdout, test, process.returncode)
        if reason != 'passed':
            raise Sfdt27RoomFailure(test, reason)

def sfdt27_sflp_parse_stage(raw: bytes, test: str) -> tuple[str, str]:
    """Fail-closed; only exact allowlisted method, stage and state, never private data."""
    if not (0 < len(raw) <= 2048):
        return ('invalid', 'unknown')
    try:
        decoded = raw.decode('ascii', 'strict')
    except UnicodeError:
        return ('invalid', 'unknown')
    lines = decoded.splitlines()
    if (not raw.endswith(b'\n') or not (2 <= len(lines) <= 36) or
            lines[0] != 'SFLP_V1' or test not in SFDT27_SFLP_TESTS):
        return ('invalid', 'unknown')
    stages = []
    for row in lines[1:]:
        parts = row.split('|')
        if (len(parts) != 3 or parts[0] != test or
                parts[1] not in SFDT27_SFLP_STAGES or
                parts[2] not in ('ENTER', 'FAIL', 'PASS')):
            return ('invalid', 'unknown')
        stages.append((parts[1], parts[2]))
    if stages[0] != ('SFLP_SETUP', 'ENTER'):
        return ('invalid', 'unknown')
    failures = [stage for stage, state in stages if state == 'FAIL']
    if failures:
        return ('failed', failures[0] if len(failures) == 1 else 'unknown')
    complete = sum(stage == 'SFLP_COMPLETE' and state == 'PASS' for stage,state in stages)
    if complete == 1 and stages[-1] == ('SFLP_COMPLETE', 'PASS'):
        return ('complete', 'SFLP_COMPLETE')
    if complete > 1 or any(state == 'PASS' or stage == 'SFLP_COMPLETE' for stage,state in stages):
        return ('invalid', 'unknown')
    return ('incomplete', stages[-1][0])


def sfdt27_sflp_read_stage(test: str) -> tuple[str, str]:
    try:
        result = subprocess.run(
            ['adb', '-s', SERIAL, 'exec-out', 'run-as', PACKAGE,
             'cat', SFDT27_SFLP_STAGE_FILE],
            capture_output=True, timeout=15, check=False,
        )
    except (OSError, subprocess.TimeoutExpired):
        return ('unavailable', 'unknown')
    if result.returncode:
        return ('unavailable', 'unknown')
    return sfdt27_sflp_parse_stage(result.stdout, test)


def sfdt27_sflp_clear_stage() -> bool:
    try:
        result = subprocess.run(
            ['adb', '-s', SERIAL, 'shell', 'run-as', PACKAGE,
             'rm', '-f', SFDT27_SFLP_STAGE_FILE],
            capture_output=True, timeout=15, check=False,
        )
        return result.returncode == 0
    except (OSError, subprocess.TimeoutExpired):
        return False


def sfdt27_hosted_sflp_regression() -> None:
    # True app/instrumentation tests, not SQL string matches or synthetic output.
    # Every test has a private fixture identity and must emit exactly one start,
    # one finish, and an Android instrumentation success status.
    for test in SFDT27_SFLP_TESTS:
        if not sfdt27_sflp_clear_stage():
            raise Sfdt27RoomFailure(test, 'runner_stage_receipt_reset_failed')
        try:
            process = subprocess.run(
                ['adb', '-s', SERIAL, 'shell', 'am', 'instrument', '-w', '-r',
                 '-e', 'class', PACKAGE + '.Sfdt27SflpReplayRegressionTest#' + test,
                 RUNNER], capture_output=True, timeout=180, check=False,
            )
        except subprocess.TimeoutExpired:
            raise Sfdt27RoomFailure(test, 'runner_timeout') from None
        reason = sfdt27_room_result(process.stdout, test, process.returncode)
        status, stage = sfdt27_sflp_read_stage(test)
        # Real Android JUnit status and a NEW, test-bound, device-private
        # completion seal are both mandatory. Neither can substitute for the other.
        if reason == 'passed':
            if status != 'complete':
                code = ('runner_stage_receipt_invalid' if status == 'invalid' else
                        'runner_stage_receipt_unavailable' if status == 'unavailable' else
                        'runner_stage_receipt_incomplete')
                raise Sfdt27RoomFailure(test, code)
        else:
            if reason in ('runner_test_failed_unstaged', 'test_failed', 'runner_bad_status'):
                if status == 'failed' and stage in SFDT27_SFLP_STAGES:
                    reason = 'stage_' + stage.lower()
                elif status == 'incomplete' and stage in SFDT27_SFLP_STAGES:
                    reason = 'stage_' + stage.lower()
            raise Sfdt27RoomFailure(test, reason)

DIAGNOSTIC_THROUGH = {'plan': 1, 'subjects': 2, 'session': 3, 'practice': 4}
DIAGNOSTIC_PASS = 'DIAGNOSTIC_PASS_NOT_RELEASE_QUALIFIED'


def parse_boundaries(raw: bytes) -> list[dict]:
    require(len(raw) <= 1024, 'Boundary evidence too large')
    decoded = raw.decode('ascii', 'strict')
    records = []
    for line in decoded.splitlines():
        fields = line.split('|')
        require(len(fields) == 2 and fields[0] in BOUNDARY_POINTS and
                fields[1] in BOUNDARY_STATES, 'Unreviewed boundary evidence')
        require(fields[0] not in {r['point'] for r in records}, 'Duplicate boundary checkpoint')
        records.append({'point': fields[0], 'state': fields[1]})
    # The test writes checkpoints in the order in which users encounter them.
    require([r['point'] for r in records] ==
            list(BOUNDARY_POINTS[:len(records)]), 'Out-of-order boundary checkpoints')
    return records


def read_boundaries() -> list[dict]:
    p = subprocess.run(['adb', '-s', SERIAL, 'exec-out', 'run-as', PACKAGE,
                        'cat', 'files/sfdt22-boundaries.txt'],
                       capture_output=True, timeout=20, check=False)
    if p.returncode != 0:
        return []  # Earlier host/install failure: no production UI checkpoints yet.
    return parse_boundaries(p.stdout)


def expected_boundaries(count: int) -> list[dict]:
    points = ('after_create', 'after_subject', 'after_topic', 'session_route', 'before_session_start')
    n = (0, 1, 3, 5, 5, 5)[count]
    return [{'point': point, 'state': 'matched' if point == 'session_route' else 'present'}
            for point in points[:n]]


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
    boundary = re.search(r'SFDT22_BOUNDARY_(AFTER_CREATE|AFTER_SUBJECT|AFTER_TOPIC|BEFORE_SESSION_START)_(MISSING|LOOKUP_FAILED|AMBIGUOUS|MISMATCHED)', raw)
    topic_cause = re.search(r'SFDT22_TOPIC_CAUSE_([A-Z_]{2,50})\b', raw)
    if boundary:
        reason = 'boundary_' + boundary.group(1).lower() + '_' + boundary.group(2).lower()
    elif topic_cause and test == TESTS[1] and stage == 'topic_save_feedback' and topic_cause.group(1).lower() in TOPIC_CAUSE_CODES:
        reason = 'topic_save_' + topic_cause.group(1).lower()
    elif 'SFDT22_BOUNDARY_SESSION_ROUTE_MISMATCHED' in raw:
        reason = 'boundary_session_route_mismatched'
    elif 'SFDT22_WORKSPACE_PLAN_MISMATCH' in raw and test == TESTS[1]:
        reason = 'workspace_plan_mismatch'
    elif 'SFDT22_SUBJECT_OWNER_LOOKUP_FAILED' in raw and test == TESTS[1]:
        reason = 'subject_owner_lookup_failed'
    elif 'SFDT22_SUBJECT_OWNER_MISMATCH' in raw and test == TESTS[1]:
        reason = 'subject_owner_mismatch'
    elif 'SFDT22_SUBJECT_CARD_MISSING' in raw:
        reason = 'subject_card_missing'
    elif 'SFDT22_SUBJECT_EDITOR_STILL_OPEN' in raw:
        reason = 'subject_editor_still_open'
    elif 'SFDT22_TOPIC_ADD_BUTTON_AMBIGUOUS' in raw:
        reason = 'topic_add_button_ambiguous'
    elif 'SFDT22_TOPIC_EDITOR_TAG_MISSING' in raw:
        reason = 'topic_editor_tag_missing'
    elif 'SFDT22_TOPIC_SAVE_FAILED' in raw:
        reason = 'topic_save_failed'
    elif 'SFDT22_SUBJECT_EXPAND_ACTION_MISSING' in raw:
        reason = 'subject_expand_action_missing'
    elif 'SFDT22_SUBJECT_EXPAND_SNACKBAR_OBSTRUCTION' in raw:
        reason = 'subject_expand_snackbar_obstruction'
    elif 'SFDT22_SUBJECT_EXPAND_TOUCH_FAILED' in raw:
        reason = 'subject_expand_touch_failed'
    elif 'SFDT22_SUBJECT_EXPAND_SEMANTIC_CLICK_FAILED' in raw:
        reason = 'subject_expand_semantic_click_failed'
    elif 'SFDT22_SUBJECT_EXPAND_STATE_AMBIGUOUS' in raw:
        reason = 'subject_expand_state_ambiguous'
    elif 'SFDT22_SUBJECT_NOT_EXPANDED' in raw:
        reason = 'subject_not_expanded'
    elif 'SFDT22_TOPIC_SEARCH_EDITOR_MISSING' in raw:
        reason = 'topic_search_editor_missing'
    elif 'SFDT22_TOPIC_SEARCH_WRITE_FAILED' in raw:
        reason = 'topic_search_write_failed'
    elif 'SFDT22_TOPIC_ROW_NOT_COMPOSED' in raw:
        reason = 'topic_row_not_composed'
    elif 'SFDT22_TOPIC_ROW_MISSING' in raw:
        reason = 'topic_row_missing'
    elif 'SFDT22_TOPIC_EDITOR_STILL_OPEN' in raw:
        reason = 'topic_editor_still_open'
    elif 'SFDT22_SESSION_START_VALIDATION_FAILED' in raw:
        reason = 'session_start_validation_failed'
    elif 'SFDT22_SESSION_START_PLAN_SETTINGS_MISSING' in raw:
        reason = 'session_start_plan_settings_missing'
    elif 'SFDT22_SESSION_START_PLAN_SETTINGS_LOOKUP_FAILED' in raw:
        reason = 'session_start_plan_settings_lookup_failed'
    elif 'SFDT22_SESSION_START_PLAN_SETTINGS_FAILED' in raw:
        reason = 'session_start_plan_settings_failed'
    elif 'SFDT22_SESSION_START_ALREADY_ACTIVE' in raw:
        reason = 'session_start_already_active'
    elif 'SFDT22_SESSION_START_ACTIVE_LOOKUP_FAILED' in raw:
        reason = 'session_start_active_lookup_failed'
    elif 'SFDT22_SESSION_START_PERSISTENCE_FAILED' in raw:
        reason = 'session_start_persistence_failed'
    elif 'SFDT22_SESSION_START_DIAGNOSTIC_AMBIGUOUS' in raw:
        reason = 'session_start_diagnostic_ambiguous'
    elif 'SFDT22_SESSION_START_FAILED' in raw:
        reason = 'session_start_failed'
    elif 'SFDT22_SESSION_START_SUCCEEDED_NO_RUNNING_UI' in raw:
        reason = 'session_start_success_without_running_ui'
    elif 'SFDT22_SESSION_RUNNING_CONTROL_MISSING' in raw:
        reason = 'session_running_control_missing'
    elif 'SFDT22_SESSION_RUNNING_ACTION_MISSING' in raw:
        reason = 'session_running_action_missing'
    elif 'SFDT22_SESSION_RUNNING_CONTROL_AMBIGUOUS' in raw:
        reason = 'session_running_control_ambiguous'
    elif 'SFDT22_SESSION_RUNNING_CONTROL_DISABLED' in raw:
        reason = 'session_running_control_disabled'
    elif 'SFDT22_SESSION_RUNNING_CONTROL_OFFSCREEN' in raw:
        reason = 'session_running_control_offscreen'
    elif 'SFDT22_SESSION_CONTROL_NOT_READY' in raw:
        reason = 'session_control_not_ready'
    elif 'SFDT22_SESSION_EDITOR_NOT_READY' in raw:
        reason = 'session_editor_not_ready'
    elif 'SFDT22_INNER_TAB_MISSING' in raw:
        reason = 'inner_tab_missing'
    elif 'SFDT22_INNER_TAB_TOUCH_FAILED' in raw:
        reason = 'inner_tab_touch_failed'
    elif 'SFDT22_INNER_TAB_NOT_SELECTED' in raw:
        reason = 'inner_tab_not_selected'
    elif 'SFDT22_INNER_PANE_NOT_READY' in raw:
        reason = 'inner_pane_not_ready'
    elif 'SFDT22_PLAN_CONTENT_TAB_MISSING' in raw:
        reason = 'plan_content_tab_missing'
    elif 'SFDT22_PLAN_CONTENT_TAB_TAP_FAILED' in raw:
        reason = 'plan_content_tab_tap_failed'
    elif 'SFDT22_PLAN_CONTENT_TAB_NOT_SELECTED' in raw:
        reason = 'plan_content_tab_not_selected'
    elif 'SFDT22_PLAN_CONTENT_PANE_NOT_READY' in raw:
        reason = 'plan_content_pane_not_ready'
    elif 'SFDT22_TOPIC_FALLBACK_TOUCH_FAILED' in raw:
        reason = 'topic_fallback_touch_failed'
    elif 'SFDT22_TOPIC_SNACKBAR_OBSTRUCTION' in raw:
        reason = 'topic_snackbar_obstruction'
    elif 'SFDT22_TOPIC_ADD_BUTTON_MISSING' in raw:
        reason = 'topic_add_button_missing'
    elif 'SFDT22_TOPIC_DIALOG_MISSING' in raw:
        reason = 'topic_dialog_missing'
    elif 'SFDT22_TOPIC_DIALOG_AMBIGUOUS' in raw:
        reason = 'topic_dialog_ambiguous'
    elif 'SFDT22_TOPIC_EDITABLE_MISSING' in raw:
        reason = 'topic_editable_missing'
    elif 'SFDT22_TOPIC_VALUE_NOT_WRITTEN' in raw:
        reason = 'topic_value_not_written'
    elif 'No node found that satisfies' in raw or 'No matching node found' in raw:
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


def run(output: Path, source: str, run_id: str, attempt: str, repo: Path, diagnostic_through: str | None = None) -> bool:
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
        phase = 'room_regression'
        room_error = None
        try:
            sfdt27_hosted_room_regression()
        except Sfdt27RoomFailure as failure:
            room_error = failure
            evidence['hf27_room_regression'] = 'not_passed'
            evidence['hf27_room_failure_test'] = failure.test
            evidence['hf27_room_failure_reason_code'] = failure.reason
            print('SFDT27_ROOM_REGRESSION_REFUSED test=' + failure.test +
                  ' reason=' + failure.reason, flush=True)
        else:
            evidence['hf27_room_regression'] = 'passed'
        sflp_error = None
        try:
            sfdt27_hosted_sflp_regression()
        except Sfdt27RoomFailure as failure:
            sflp_error = failure
            evidence['hf27_sflp_regression'] = 'not_passed'
            evidence['hf27_sflp_failure_test'] = failure.test
            evidence['hf27_sflp_failure_reason_code'] = failure.reason
            print('SFDT27_SFLP_REGRESSION_REFUSED test=' + failure.test +
                  ' reason=' + failure.reason, flush=True)
        else:
            evidence['hf27_sflp_regression'] = 'passed'
        results = []
        diagnostic_limit = DIAGNOSTIC_THROUGH.get(diagnostic_through) if diagnostic_through else 4
        for index, name in enumerate(TESTS[:diagnostic_limit]):
            phase = ('plan', 'subjects', 'session', 'practice')[index]
            results.append(run_test(name))
            if diagnostic_through and index == 1:
                sfdt27_diag_attach(evidence)
        if diagnostic_through:
            evidence.update(overall=DIAGNOSTIC_PASS, tests=results,
                            test_count=len(results), diagnostic_through=diagnostic_through,
                            boundaries=read_boundaries(), sfdt27_probes=sfdt27_read_probes(),
                            app_apk=app_hash, test_apk=test_hash,
                            debug_signing_certificates=certs,
                            installed_app_sha256=installed_hash)
            require(evidence['boundaries'] == expected_boundaries(len(results)),
                    'Incomplete diagnostic-only boundary evidence')
            require(evidence['sfdt27_probes'] == (sfdt27_expected_probes() if len(results) >= 2 else []),
                    'HF27 exact-plan diagnostics incomplete or inconsistent')
            phase = 'diagnostic_complete'
        else:
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
                            force_stopped_app_and_verified_new_pid=True,
                            boundaries=read_boundaries(), sfdt27_probes=sfdt27_read_probes())
            require(evidence['sfdt27_probes'] == sfdt27_expected_probes(),
                    'HF27 exact-plan diagnostics incomplete or inconsistent')
            require(evidence['boundaries'] == expected_boundaries(5),
                    'Incomplete full-gate boundary evidence')
        if diagnostic_through:
            # Keep the subject-journey capture: session instrumentation may
            # create a new process and must not erase source timing evidence.
            if 'sfdt27_diag_capture' not in evidence:
                sfdt27_diag_attach(evidence)
            require(evidence['sfdt27_diag_capture'] == 'captured' and
                any(e['checkpoint'] == 'TRACE_RESET' for e in evidence['sfdt27_diag_events']) and
                any(e['checkpoint'] == 'TOPIC_TRANSACTION_COMMIT' for e in evidence['sfdt27_diag_events']),
                'DIAG-01 missing reset or committed topic transaction')
        # Both the independent Room regression and real UI journeys are
        # mandatory. Continue diagnosis after a Room failure, but never
        # qualify or preserve a positive overall status in its presence.
        if room_error is not None:
            evidence['overall'] = 'FAILED_OR_NOT_RUN'
            phase = 'room_regression'
            raise room_error
        if sflp_error is not None:
            evidence['overall'] = 'FAILED_OR_NOT_RUN'
            phase = 'room_regression'
            raise sflp_error
        phase = 'complete'
    except (OSError, ValueError, KeyError, subprocess.TimeoutExpired) as exc:
        evidence['failure_phase'] = phase if phase in PHASES else 'host'
        evidence['failure_class'] = type(exc).__name__
        if isinstance(exc, Sfdt27RoomFailure):
            tag = 'sflp' if exc.test in SFDT27_SFLP_TESTS else 'room'
            evidence['hf27_' + tag + '_failure_test'] = exc.test
            evidence['hf27_' + tag + '_failure_reason_code'] = exc.reason
            print('SFDT27_' + tag.upper() + '_REGRESSION_REFUSED test=' + exc.test +
                  ' reason=' + exc.reason, flush=True)
        if isinstance(exc, JourneyFailure):
            evidence['failure_test'] = exc.test
            evidence['failure_step'] = exc.stage
            evidence['failure_reason_code'] = exc.reason
        evidence['completed_before_failure'] = len(results) if 'results' in locals() else 0
        evidence['hf27_room_regression'] = evidence.get('hf27_room_regression', 'not_passed')
        if diagnostic_through and 'sfdt27_diag_capture' not in evidence:
            sfdt27_diag_attach(evidence)
        try:
            evidence['sfdt27_probes'] = sfdt27_read_probes()
        except (OSError, ValueError, subprocess.TimeoutExpired):
            evidence['sfdt27_probes_capture'] = 'unavailable'
        try:
            evidence['boundaries'] = read_boundaries()
        except (OSError, ValueError, subprocess.TimeoutExpired):
            evidence['boundary_capture'] = 'unavailable'
        for name in SCREENS:
            (output / ('sfdt22-ui-' + name + '.png')).unlink(missing_ok=True)
    # Atomic host-side receipt: a crash cannot publish a torn success JSON.
    from tempfile import NamedTemporaryFile
    data = (json.dumps(evidence, sort_keys=True, indent=2) + '\n').encode('ascii')
    require(len(data) <= 20_000, 'DIAG-01 receipt exceeds bound')
    with NamedTemporaryFile(dir=output, prefix='.diag01-', delete=False) as temp_receipt:
        tmp_path = Path(temp_receipt.name)
        temp_receipt.write(data)
        temp_receipt.flush()
        os.fsync(temp_receipt.fileno())
    os.replace(tmp_path, receipt)
    ok = evidence['overall'] in (EXPECTED, DIAGNOSTIC_PASS)
    status = ('DIAGNOSTIC_PASS_ONLY' if evidence['overall'] == DIAGNOSTIC_PASS else
              'REAL_JOURNEY_PASS' if evidence['overall'] == EXPECTED else 'REAL_JOURNEY_REFUSED')
    print('SFDT22_' + status + ' phase=' + phase + ' verified_tests=' +
          str(evidence.get('test_count', evidence.get('completed_before_failure', 0))), flush=True)
    return ok


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
    require(data.get('hf27_room_regression') == 'passed' and
            data.get('hf27_sflp_regression') == 'passed' and
            data.get('sfdt27_probes') == sfdt27_expected_probes(),
            'HF27 hosted Room/probe evidence incomplete')
    require(data.get('boundaries') == expected_boundaries(5),
            'Missing required source-of-truth lifecycle boundaries')
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


def verify_diagnostic(output: Path, source: str, run_id: str, attempt: str,
                      repo: Path, through: str) -> None:
    host(source, run_id, attempt)
    p = output / 'SFDT22_EVIDENCE.json'
    require(p.is_file() and not p.is_symlink() and p.stat().st_size <= 20_000,
            'No bounded diagnostic receipt')
    data = json.loads(p.read_text())
    expected = blank(source, run_id, attempt)
    for key, value in expected.items():
        if key != 'overall':
            require(data.get(key) == value, 'Diagnostic identity mismatch: ' + key)
    require(data.get('overall') == DIAGNOSTIC_PASS and data.get('diagnostic_through') == through,
            'Diagnostic-only success marker missing')
    count = DIAGNOSTIC_THROUGH[through]
    require(data.get('tests') == [dict(test=x, status='passed', started=1, finished=1)
                                   for x in TESTS[:count]], 'Diagnostic tests incomplete')
    require(data.get('test_count') == count and data.get('boundaries') == expected_boundaries(count),
            'Diagnostic state checkpoints incomplete')
    require(data.get('hf27_room_regression') == 'passed' and
            data.get('hf27_sflp_regression') == 'passed' and
            data.get('sfdt27_probes') == (sfdt27_expected_probes() if count >= 2 else []),
            'HF27 hosted Room/probe evidence incomplete')
    require(data.get('diagnostic_only') is True and data.get('sfdt27_diag_capture') == 'captured' and
            data.get('sfdt27_diag_trace_schema') == 2 and
            any(e.get('checkpoint') == 'TOPIC_TRANSACTION_COMMIT' for e in data.get('sfdt27_diag_events', [])),
            'DIAG-01 causal diagnostics missing')
    app_hash, test_hash, certs = apk_facts(repo)
    require(data.get('app_apk') == app_hash and data.get('test_apk') == test_hash and
            data.get('debug_signing_certificates') == certs and
            data.get('installed_app_sha256') == app_hash['sha256'],
            'Diagnostic APK provenance mismatch')
    require(not list(output.glob('*.png')),
            'Diagnostic lane must not masquerade as seven-screen qualification')


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
    for arg in ('source-check', 'preflight-apks', 'run', 'verify', 'verify-diagnostic'):
        modes.add_argument('--' + arg, action='store_true')
    parser.add_argument('--output-dir', type=Path)
    parser.add_argument('--source-sha')
    parser.add_argument('--run-id')
    parser.add_argument('--attempt')
    parser.add_argument('--diagnostic-through', choices=tuple(DIAGNOSTIC_THROUGH))
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
            return 0 if run(args.output_dir, args.source_sha, args.run_id, args.attempt, repo,
                               args.diagnostic_through) else 2
        if args.verify_diagnostic:
            require(args.diagnostic_through in DIAGNOSTIC_THROUGH, 'Explicit diagnostic phase required')
            verify_diagnostic(args.output_dir, args.source_sha, args.run_id, args.attempt,
                              repo, args.diagnostic_through)
            print('SFDT22_DIAGNOSTIC_ONLY_VERIFIED: not release qualified')
            return 0
        require(args.diagnostic_through is None, 'Diagnostic selector incompatible with full verifier')
        verify(args.output_dir, args.source_sha, args.run_id, args.attempt, repo)
        print('SFDT22_EVIDENCE_VERIFIED: five real UI tests, forced restart, seven screenshots')
        return 0
    except (OSError, KeyError, ValueError, TypeError, json.JSONDecodeError) as exc:
        print('SFDT22_REFUSED: ' + type(exc).__name__, file=sys.stderr)
        return 2


if __name__ == '__main__':
    raise SystemExit(main())
