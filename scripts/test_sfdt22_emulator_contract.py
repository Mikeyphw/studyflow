#!/usr/bin/env python3
"""Offline adversarial contracts for SFDT22, run before installing a hosted AVD."""
from __future__ import annotations

import importlib.util
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import types
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
# SFDT21 remains an independently source-checked, immutable production sibling.
# Stub its OS-dependent primitives solely for offline adversarial verifier tests.
prior = sys.modules.get('sfdt21_emulator_verify')
if prior is None:
    base = types.ModuleType('sfdt21_emulator_verify')
    base.adb = lambda *args, **kwargs: b''
    sys.modules['sfdt21_emulator_verify'] = base
spec = importlib.util.spec_from_file_location(
    'sfdt22_emulator_verify', ROOT / 'scripts/sfdt22_emulator_verify.py')
assert spec and spec.loader
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)


def result(code=0, out=b'', err=b''):
    return subprocess.CompletedProcess(args=['adb'], returncode=code,
                                       stdout=out, stderr=err)


class StudyJourneyContracts(unittest.TestCase):
    def test_01_no_missing_hosted_files(self):
        m.source_check(ROOT)

    def test_02_five_immutable_declared_journeys(self):
        self.assertEqual(len(m.TESTS), 5)
        self.assertEqual(len(set(m.TESTS)), 5)
        self.assertEqual(m.TESTS[-1], 'realUserSeesPersistedProgressAfterColdRestart')

    def test_03_seven_distinct_immutable_capture_names(self):
        self.assertEqual(len(m.SCREENS), 7)
        self.assertEqual(len(set(m.SCREENS)), 7)
        self.assertEqual(m.SCREENS[-1], 'restart')

    def test_04_disallows_unreviewed_instrumentation_name(self):
        with self.assertRaises(ValueError):
            m.run_test('unitTestPretendingToBeEmulator')

    def test_05_real_instrumentation_requires_status_plus_exit(self):
        valid = (b'INSTRUMENTATION_STATUS: test=realUserCreatesAStudyPlan\n'
                 b'INSTRUMENTATION_STATUS_CODE: 1\n'
                 b'INSTRUMENTATION_STATUS: test=realUserCreatesAStudyPlan\n'
                 b'INSTRUMENTATION_STATUS_CODE: 0\n'
                 b'INSTRUMENTATION_CODE: -1\n')
        with patch.object(m.subprocess, 'run', return_value=result(out=valid)) as call:
            self.assertEqual(m.run_test(m.TESTS[0])['status'], 'passed')
            args = call.call_args.args[0]
            self.assertIn('am', args)
            self.assertIn('instrument', args)
            self.assertIn(m.CLASS + '#' + m.TESTS[0], args)

    def test_06_missing_instrumentation_status_is_refused(self):
        with patch.object(m.subprocess, 'run', return_value=result(out=b'OK (1 test)\n')):
            with self.assertRaises(ValueError):
                m.run_test(m.TESTS[0])

    def test_07_failed_instrumentation_is_refused(self):
        raw = (b'INSTRUMENTATION_STATUS: test=realUserCreatesAStudyPlan\n'
               b'INSTRUMENTATION_STATUS_CODE: 1\n'
               b'INSTRUMENTATION_STATUS_CODE: -2\nINSTRUMENTATION_CODE: -1\n')
        with patch.object(m.subprocess, 'run', return_value=result(out=raw)):
            with self.assertRaises(ValueError):
                m.run_test(m.TESTS[0])

    def test_08_wrong_test_identity_is_refused(self):
        raw = (b'INSTRUMENTATION_STATUS: test=otherTest\nINSTRUMENTATION_STATUS_CODE: 1\n'
               b'INSTRUMENTATION_STATUS_CODE: 0\nINSTRUMENTATION_CODE: -1\n')
        with patch.object(m.subprocess, 'run', return_value=result(out=raw)):
            with self.assertRaises(ValueError):
                m.run_test(m.TESTS[0])

    def test_09_pidof_absence_is_legitimate_when_exit_one(self):
        with patch.object(m.subprocess, 'run', return_value=result(code=1)):
            self.assertEqual(m.current_pid(), 0)

    def test_10_pidof_transport_failure_is_refused(self):
        with patch.object(m.subprocess, 'run', return_value=result(code=127, err=b'not found')):
            with self.assertRaises(ValueError):
                m.current_pid()

    def test_11_pidof_multiple_processes_refused(self):
        with patch.object(m.subprocess, 'run', return_value=result(out=b'111 222\n')):
            with self.assertRaises(ValueError):
                m.current_pid()

    def test_12_cold_restart_rejects_recycled_pid(self):
        with patch.object(m.base, 'adb', return_value=b'Starting: Intent...'), \
                patch.object(m, 'wait_for_pid', side_effect=[1234, 0, 1234]):
            with self.assertRaises(ValueError):
                m.real_process_restart()

    def test_13_cold_restart_requires_force_stop_and_new_pid(self):
        with patch.object(m.base, 'adb', return_value=b'Starting: Intent...') as adb, \
                patch.object(m, 'wait_for_pid', side_effect=[1234, 0, 4321]):
            m.real_process_restart()
            self.assertIn(('shell', 'am', 'force-stop', m.PACKAGE),
                          [call.args for call in adb.call_args_list])

    def test_14_host_lacks_release_or_publication_qualification(self):
        obj = m.blank('a' * 40, '123', '1')
        self.assertFalse(obj['physical_device_qualified'])
        self.assertFalse(obj['release_qualified'])
        self.assertFalse(obj['publication_allowed'])
        self.assertFalse(obj['force_stopped_app_and_verified_new_pid'])

    def test_15_github_workflow_fails_closed(self):
        workflow = (ROOT / '.github/workflows/studyflow-sfdt22-hosted-journeys.yml').read_text()
        self.assertIn('needs: [source, emulator]', workflow)
        self.assertIn('test "$SOURCE_RESULT" = success', workflow)
        self.assertIn('test "$EMULATOR_RESULT" = success', workflow)
        self.assertIn('--verify', workflow)
        self.assertIn('if: always()', workflow)
        self.assertIn(':app:assembleDebugAndroidTest', workflow)
        self.assertNotIn('continue-on-error:', workflow)
        self.assertNotIn('allow-failure', workflow)

    def test_16_real_ui_screen_does_not_use_injected_domain(self):
        src = (ROOT / 'app/src/androidTest/java/com/mikeyphw/studyflow/Sfdt22HostedStudyJourneyTest.kt').read_text()
        for forbidden in ('setContent(', 'container.manageStudy', 'SamplePlanProvisioner',
                          'runBlocking {', 'Fake', 'mockk(', 'assumeTrue('):
            self.assertNotIn(forbidden, src)
        self.assertEqual(src.count('@Test fun '), 5)
        self.assertIn('performTextReplacement', src)
        self.assertIn('performClick', src)


if __name__ == '__main__':
    unittest.main(verbosity=2)
