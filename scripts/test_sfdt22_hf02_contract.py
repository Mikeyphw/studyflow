#!/usr/bin/env python3
"""HF02: fail-closed, privacy-preserving Android journey regression contracts."""
from __future__ import annotations
import hashlib
import importlib.util
from pathlib import Path
import sys
import subprocess
import unittest
import types
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
# Isolate the SFDT22 algorithm from Android SDK just like source-only SFDT22 tests.
if 'sfdt21_emulator_verify' not in sys.modules:
    stub = types.ModuleType('sfdt21_emulator_verify')
    stub.adb = lambda *args, **kwargs: b''
    sys.modules['sfdt21_emulator_verify'] = stub
spec = importlib.util.spec_from_file_location('sfdt22_hf02_module',ROOT/'scripts/sfdt22_emulator_verify.py')
assert spec and spec.loader
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)

class HotfixTests(unittest.TestCase):
    def test_01_plan_field_targets_real_edit_action(self):
        src=(ROOT/'app/src/androidTest/java/com/mikeyphw/studyflow/Sfdt22HostedStudyJourneyTest.kt').read_text()
        self.assertIn('if (resId == PlansR.string.plan_name)',src)
        self.assertIn('fields.fetchSemanticsNodes().size == 2',src)
        self.assertIn('fields.onFirst().performTextReplacement(value)',src)
        self.assertIn('hasSetTextAction()',src)
        self.assertNotIn('setContent {',src)
        self.assertEqual(src.count('@Test fun '),5)
    def test_02_stages_must_rethrow_not_swallow(self):
        src=(ROOT/'app/src/androidTest/java/com/mikeyphw/studyflow/Sfdt22HostedStudyJourneyTest.kt').read_text()
        self.assertIn('throw AssertionError("SFDT22_STEP_" + name, failure)',src)
        for stage in m.STAGES:
            self.assertIn('stage("'+stage+'")',src)
        self.assertIn('createAndroidComposeRule<MainActivity>()',src)
    def test_03_failure_reason_is_stable_not_user_text(self):
        raw=('INSTRUMENTATION_STATUS: stack=java.lang.AssertionError: '
             'SFDT22_STEP_plan_name\nCaused by: No node found that satisfies: '
             'secret personal study content !\n'
             'INSTRUMENTATION_STATUS_CODE: -2\n')
        failure=m.summarize_failure(raw,m.TESTS[0],0)
        self.assertEqual(failure.reason,'no_matching_compose_node')
        self.assertEqual(failure.stage,'plan_name')
        self.assertEqual(failure.test,m.TESTS[0])
        self.assertNotIn('secret',str(failure))
        self.assertEqual(set(vars(failure)), {'test','reason','stage'})
    def test_04_unreviewed_stage_is_not_exposed(self):
        f=m.summarize_failure('SFDT22_STEP_secret_value AssertionError',m.TESTS[0],0)
        self.assertEqual(f.stage,'unavailable')
        self.assertNotIn('secret_value',str(f))
    def test_05_malformed_test_or_reason_refused(self):
        with self.assertRaises(ValueError):m.JourneyFailure('otherTest',reason='test_failed')
        with self.assertRaises(ValueError):m.JourneyFailure(m.TESTS[0],reason='user secret')
        with self.assertRaises(ValueError):m.JourneyFailure(m.TESTS[0],reason='test_failed',stage='user secret')
    def test_06_pass_exactly_one_test(self):
        n=m.TESTS[0]
        valid=(f'INSTRUMENTATION_STATUS: test={n}\nINSTRUMENTATION_STATUS_CODE: 1\n'
               f'INSTRUMENTATION_STATUS: test={n}\nINSTRUMENTATION_STATUS_CODE: 0\n'
               'INSTRUMENTATION_CODE: -1\n').encode()
        p=subprocess.CompletedProcess(args=['adb'],returncode=0,stdout=valid,stderr=b'')
        with patch.object(m.subprocess,'run',return_value=p):
            self.assertEqual(m.run_test(n)['status'],'passed')
    def test_07_failure_stays_red_with_allowlisted_status(self):
        n=m.TESTS[0]
        failed=(f'INSTRUMENTATION_STATUS: test={n}\nINSTRUMENTATION_STATUS_CODE: 1\n'
                'INSTRUMENTATION_STATUS: stack=SFDT22_STEP_plan_open\n'
                'INSTRUMENTATION_STATUS_CODE: -2\nINSTRUMENTATION_CODE: -1\n').encode()
        p=subprocess.CompletedProcess(args=['adb'],returncode=0,stdout=failed,stderr=b'')
        with patch.object(m.subprocess,'run',return_value=p):
            with self.assertRaises(m.JourneyFailure) as raised:m.run_test(n)
        self.assertEqual(raised.exception.stage,'plan_open')
        self.assertEqual(raised.exception.reason,'test_failed')
    def test_08_mismatch_and_nonzero_exit_stay_red(self):
        p=subprocess.CompletedProcess(args=['adb'],returncode=1,stdout=b'',stderr=b'secret')
        with patch.object(m.subprocess,'run',return_value=p):
            with self.assertRaises(m.JourneyFailure) as raised:m.run_test(m.TESTS[0])
        self.assertEqual(raised.exception.reason,'runner_nonzero_exit')
        self.assertNotIn('secret',str(raised.exception))
    def test_09_workflow_source_pins_are_exact(self):
        wf=(ROOT/'.github/workflows/studyflow-sfdt22-hosted-journeys.yml').read_text()
        for p in ('app/src/androidTest/java/com/mikeyphw/studyflow/Sfdt22HostedStudyJourneyTest.kt',
                  'scripts/sfdt22_emulator_verify.py'):
            self.assertIn(hashlib.sha256((ROOT/p).read_bytes()).hexdigest()+'  '+p,wf)
        self.assertIn('python3 -B scripts/test_sfdt22_hf02_contract.py',wf)
        self.assertIn('needs: [source, emulator]',wf)
        self.assertNotIn('continue-on-error:',wf)
    def test_10_no_raw_device_logs_retained(self):
        src=(ROOT/'scripts/sfdt22_emulator_verify.py').read_text()
        self.assertIn("evidence['failure_reason_code'] = exc.reason",src)
        self.assertIn("evidence['failure_step'] = exc.stage",src)
        self.assertIn('raw_logs_retained=False',src)
        self.assertNotIn("evidence['stack']",src)
        self.assertNotIn("evidence['raw']",src)
        self.assertIn("require(not list(output.glob('*log*'))",src)
    def test_11_no_prod_build_or_devtool_lane_weakening(self):
        wf=(ROOT/'.github/workflows/studyflow-sfdt22-hosted-journeys.yml').read_text()
        self.assertIn(':app:assembleDebugAndroidTest',wf)
        self.assertIn('SFDT22 hosted real-study UI gate',wf)
        self.assertNotIn('continue-on-error:',wf)
        self.assertNotIn('allow-failure',wf)

if __name__=='__main__':unittest.main(verbosity=2)
