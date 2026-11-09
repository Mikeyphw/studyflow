#!/usr/bin/env python3
"""SFDT22 HF04 regression contracts for real, tagged production CTA."""
from __future__ import annotations
import hashlib
import importlib.util
from pathlib import Path
import sys
import types
import unittest
ROOT=Path(__file__).resolve().parents[1]
if 'sfdt21_emulator_verify' not in sys.modules:
    stub=types.ModuleType('sfdt21_emulator_verify')
    stub.adb=lambda *args, **kwargs: b''
    sys.modules['sfdt21_emulator_verify']=stub
spec=importlib.util.spec_from_file_location('sfdt22_hf04_verify',ROOT/'scripts/sfdt22_emulator_verify.py')
assert spec and spec.loader
m=importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)
K='app/src/androidTest/java/com/mikeyphw/studyflow/Sfdt22HostedStudyJourneyTest.kt'
D='core/designsystem/src/main/java/com/mikeyphw/studyflow/core/designsystem/StudyFlowComponents.kt'
W='feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/WorkspaceCommon.kt'
F='.github/workflows/studyflow-sfdt22-hosted-journeys.yml'
class HF04(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.kt=(ROOT/K).read_text()
        cls.design=(ROOT/D).read_text()
        cls.workspace=(ROOT/W).read_text()
        cls.wf=(ROOT/F).read_text()
    def test_01_tag_is_on_real_production_material_button(self):
        self.assertIn('import androidx.compose.ui.platform.testTag',self.design)
        self.assertIn('Button(\n                onClick = onAction,\n                modifier = if (actionTestTag == null) Modifier else Modifier.testTag(actionTestTag),',self.design)
        self.assertIn('Text(text = actionLabel)',self.design)
    def test_02_only_no_plan_call_site_supplies_tag(self):
        self.assertIn('actionLabel = stringResource(R.string.create_plan)',self.workspace)
        self.assertIn('onAction = onCreatePlan',self.workspace)
        self.assertEqual(self.workspace.count('actionTestTag = "studyflow.no_plan.create_plan"'),1)
        self.assertIn('actionTestTag: String? = null',self.design)
    def test_03_existing_error_pane_unmodified_semantics(self):
        self.assertIn('actionTestTag = null,\n        isError = true',self.design)
        self.assertIn('actionTestTag = actionTestTag,\n        isError = false',self.design)
    def test_04_real_mainactivity_taps_tagged_button(self):
        self.assertIn('createAndroidComposeRule<MainActivity>()',self.kt)
        self.assertIn('rule.onNodeWithTag("studyflow.plan_list.create_plan", useUnmergedTree = true)',self.kt)
        self.assertIn('button.assertIsDisplayed().performTouchInput { click() }',self.kt)
        self.assertNotIn('labels.fetchSemanticsNodes().size == 1',self.kt)
    def test_05_readiness_and_navigation_are_verified(self):
        stages=['stage("plan_shell")','stage("plan_system_hub")','stage("plan_all_plans")','stage("plan_open")','stage("plan_editor_ready")','stage("plan_name")','stage("plan_save")','stage("plan_confirm")']
        self.assertEqual(sorted(self.kt.index(s) for s in stages),[self.kt.index(s) for s in stages])
        self.assertIn('rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size == 2',self.kt)
        self.assertIn('visible(planName)',self.kt)
    def test_06_never_replace_real_ui_with_fake_state(self):
        for forbidden in ('setContent {','mockk(','assumeTrue(','SamplePlanProvisioner','container.manageStudyPlan','runBlocking {'):
            self.assertNotIn(forbidden,self.kt)
        self.assertEqual(self.kt.count('@Test fun '),5)
    def test_07_full_journey_and_screenshot_contract_kept(self):
        for test in m.TESTS:self.assertIn('fun '+test+'(',self.kt)
        for shot in m.SCREENS:self.assertIn('screenshot("'+shot+'")',self.kt)
        self.assertIn('force_stopped_app_and_verified_new_pid=True',(ROOT/'scripts/sfdt22_emulator_verify.py').read_text())
    def test_08_source_pins_all_modified_production_sources(self):
        for r in (K,D,W):
            self.assertIn(hashlib.sha256((ROOT/r).read_bytes()).hexdigest()+'  '+r,self.wf)
    def test_09_regression_suite_wired_as_mandatory(self):
        for n in ('test_sfdt22_emulator_contract.py','test_sfdt22_hf01_contract.py','test_sfdt22_hf02_contract.py','test_sfdt22_hf03_contract.py','test_sfdt22_hf04_contract.py'):
            self.assertIn('python3 -B scripts/'+n,self.wf)
        self.assertIn('needs: [source, emulator]',self.wf)
        self.assertIn('test "$EMULATOR_RESULT" = success',self.wf)
        self.assertNotIn('continue-on-error:',self.wf)
        self.assertNotIn('allow-failure',self.wf)
    def test_10_failures_remain_fail_closed_and_sanitized(self):
        f=m.summarize_failure('SFDT22_STEP_plan_open ComposeTimeoutException secret=private',m.TESTS[0],0)
        self.assertEqual(f.stage,'plan_open')
        self.assertEqual(f.reason,'compose_wait_timeout')
        self.assertNotIn('private',str(f))
        self.assertFalse(m.blank('a'*40,'2','1')['raw_logs_retained'])
    def test_11_false_success_still_unavailable(self):
        self.assertFalse(m.blank('a'*40,'2','1')['publication_allowed'])
        self.assertFalse(m.blank('a'*40,'2','1')['physical_device_qualified'])
        self.assertFalse(m.blank('a'*40,'2','1')['release_qualified'])
    def test_12_ci_still_requires_real_android_instrumentation(self):
        self.assertIn('api-level: 36',self.wf)
        self.assertIn(':app:assembleDebugAndroidTest',self.wf)
        self.assertIn('sfdt22_emulator_verify.py --run', (ROOT/'scripts/sfdt22_emulator_ci.sh').read_text())
if __name__=='__main__':unittest.main(verbosity=2)
