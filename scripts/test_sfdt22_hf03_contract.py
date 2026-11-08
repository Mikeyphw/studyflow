#!/usr/bin/env python3
"""SFDT22 HF03: offline, negative-first tests of the hosted real-touch plan hotfix."""
from __future__ import annotations
import hashlib
import importlib.util
from pathlib import Path
import subprocess
import sys
import types
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
if 'sfdt21_emulator_verify' not in sys.modules:
    stub = types.ModuleType('sfdt21_emulator_verify')
    stub.adb = lambda *args, **kwargs: b''
    sys.modules['sfdt21_emulator_verify'] = stub
spec = importlib.util.spec_from_file_location('sfdt22_hf03_module', ROOT/'scripts/sfdt22_emulator_verify.py')
assert spec and spec.loader
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)

class HF03(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.kt = (ROOT/'app/src/androidTest/java/com/mikeyphw/studyflow/Sfdt22HostedStudyJourneyTest.kt').read_text()
        cls.wf = (ROOT/'.github/workflows/studyflow-sfdt22-hosted-journeys.yml').read_text()

    def test_01_real_unmerged_text_is_located(self):
        self.assertIn('fun tapCreatePlan()',self.kt)
        self.assertIn('rule.onAllNodes(hasText(label), useUnmergedTree = true)',self.kt)
        self.assertIn('labels.fetchSemanticsNodes().size == 1',self.kt)

    def test_02_real_touch_not_direct_route_or_mock(self):
        self.assertIn('labels.onFirst().assertIsDisplayed().performTouchInput { click() }',self.kt)
        self.assertIn('import androidx.compose.ui.test.performTouchInput',self.kt)
        self.assertIn('import androidx.compose.ui.test.click',self.kt)
        self.assertNotIn('setContent {',self.kt)
        self.assertNotIn('mockk(',self.kt)
        self.assertNotIn('assumeTrue(',self.kt)
        self.assertNotIn('composeTestRule.setContent',self.kt)

    def test_03_readiness_touch_editor_steps_are_independent(self):
        a=self.kt.index('stage("plan_shell") { shell() }')
        b=self.kt.index('stage("plan_open") { tapCreatePlan() }')
        c=self.kt.index('stage("plan_editor_ready") {')
        d=self.kt.index('stage("plan_name") { fill(PlansR.string.plan_name, planName) }')
        self.assertTrue(a<b<c<d)

    def test_04_editor_verifies_actual_text_inputs(self):
        self.assertIn('rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size == 2',self.kt)
        self.assertIn('fields.onFirst().performTextReplacement(value)',self.kt)
        self.assertIn('createAndroidComposeRule<MainActivity>()',self.kt)

    def test_05_exact_five_real_journeys_seven_screens(self):
        self.assertEqual(self.kt.count('@Test fun '), 5)
        for name in m.TESTS:self.assertIn('fun '+name+'(',self.kt)
        for name in m.SCREENS:self.assertIn('screenshot("'+name+'")',self.kt)

    def test_06_failure_stages_remain_allowlisted(self):
        self.assertTrue({'plan_shell','plan_open','plan_editor_ready'}.issubset(m.STAGES))
        for stage in m.STAGES: self.assertIn('stage("'+stage+'")',self.kt)
        self.assertIn('throw AssertionError("SFDT22_STEP_" + name, failure)',self.kt)

    def test_07_stage_specific_timeouts_remain_fatal(self):
        for stage in ('plan_shell','plan_open','plan_editor_ready'):
            f=m.summarize_failure('java.lang.AssertionError: SFDT22_STEP_'+stage+
                  '\nCaused by: ComposeTimeoutException: Condition still not satisfied',m.TESTS[0],0)
            self.assertEqual(f.stage,stage)
            self.assertEqual(f.reason,'compose_wait_timeout')

    def test_08_unreviewed_diagnostic_does_not_leak(self):
        f=m.summarize_failure('SFDT22_STEP_accountSecret: token=abcd',m.TESTS[0],0)
        self.assertEqual(f.stage,'unavailable')
        self.assertNotIn('abcd',str(f))
        self.assertFalse(m.blank('a'*40,'2','1')['raw_logs_retained'])

    def test_09_old_hf02_plan_open_evidence_still_supported(self):
        f=m.summarize_failure('SFDT22_STEP_plan_open ComposeTimeoutException',m.TESTS[0],0)
        self.assertEqual(f.stage,'plan_open')

    def test_10_gate_and_source_hashes_still_pinned(self):
        for file in ('scripts/sfdt22_emulator_verify.py',
             'app/src/androidTest/java/com/mikeyphw/studyflow/Sfdt22HostedStudyJourneyTest.kt'):
            self.assertIn(hashlib.sha256((ROOT/file).read_bytes()).hexdigest()+'  '+file,self.wf)
        for test in ('test_sfdt22_emulator_contract.py','test_sfdt22_hf01_contract.py',
                     'test_sfdt22_hf02_contract.py','test_sfdt22_hf03_contract.py'):
            self.assertIn('python3 -B scripts/'+test,self.wf)
        self.assertIn('needs: [source, emulator]',self.wf)
        self.assertIn(':app:assembleDebugAndroidTest',self.wf)
        self.assertNotIn('continue-on-error:',self.wf)
        self.assertNotIn('allow-failure',self.wf)

    def test_11_verified_emulator_only_no_fake_success(self):
        self.assertTrue(m.EXPECTED.startswith('PASS_HOSTED_'))
        self.assertFalse(m.blank('a'*40,'2','1')['publication_allowed'])
        self.assertFalse(m.blank('a'*40,'2','1')['release_qualified'])
        self.assertIn('force_stopped_app_and_verified_new_pid=True',
             (ROOT/'scripts/sfdt22_emulator_verify.py').read_text())

if __name__=='__main__':unittest.main(verbosity=2)
