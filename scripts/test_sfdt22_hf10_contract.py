#!/usr/bin/env python3
"""HF10: navigation-readiness regression for the actual Plan Content tab."""
from __future__ import annotations
import hashlib, importlib.util, sys, types, unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
if 'sfdt21_emulator_verify' not in sys.modules:
    m=types.ModuleType('sfdt21_emulator_verify')
    m.adb=lambda *args,**kwargs:b''
    sys.modules['sfdt21_emulator_verify']=m
spec=importlib.util.spec_from_file_location('sfdt22_hf10_verifier', ROOT/'scripts/sfdt22_emulator_verify.py')
assert spec and spec.loader
v=importlib.util.module_from_spec(spec)
spec.loader.exec_module(v)
K='app/src/androidTest/java/com/mikeyphw/studyflow/Sfdt22HostedStudyJourneyTest.kt'
V='scripts/sfdt22_emulator_verify.py'
WF='.github/workflows/studyflow-sfdt22-hosted-journeys.yml'
H10='scripts/test_sfdt22_hf10_contract.py'
class HF10(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.kt=(ROOT/K).read_text()
        cls.ver=(ROOT/V).read_text()
        cls.wf=(ROOT/WF).read_text()
        cls.helper=cls.kt.split('    private fun openPlanContentTab() {')[1].split('    private fun shell()')[0]
    def test_01_subject_uses_readiness_helper_not_immediate_click(self):
        self.assertIn('stage("subject_open_content") { openPlanContentTab() }',self.kt)
        self.assertNotIn('stage("subject_open_content") { innerTab(WorkspaceR.string.plan_content).performClick() }',self.kt)
        self.assertIn('private fun innerTab(resId: Int) = rule.onAllNodes(',self.kt)
    def test_02_helper_scopes_to_real_plan_tab_role_and_label(self):
        self.assertIn('hasText(text(WorkspaceR.string.plan_content))',self.helper)
        self.assertIn('SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)',self.helper)
        self.assertIn('rule.onAllNodes(tabMatcher)',self.helper)
        self.assertNotIn('setContent {',self.helper)
        self.assertNotIn('navigate(',self.helper)
    def test_03_waits_until_actual_content_tab_is_composed(self):
        self.assertIn('rule.waitUntil(45_000L) { tabs.fetchSemanticsNodes().isNotEmpty() }',self.helper)
        self.assertIn('SFDT22_PLAN_CONTENT_TAB_MISSING',self.helper)
    def test_04_scrolls_in_real_scrollable_tabstrip_before_touch(self):
        self.assertIn('tab.performScrollTo()',self.helper)
        self.assertIn('tab.assertIsDisplayed()',self.helper)
        self.assertIn('tab.performTouchInput { click() }',self.helper)
        self.assertIn('SFDT22_PLAN_CONTENT_TAB_TAP_FAILED',self.helper)
    def test_05_real_selection_not_merely_successful_touch(self):
        self.assertIn('tabs.onFirst().assertIsSelected()',self.helper)
        self.assertIn('SFDT22_PLAN_CONTENT_TAB_NOT_SELECTED',self.helper)
    def test_06_real_content_pane_ready_before_manager_click(self):
        self.assertIn('hasText(text(WorkspaceR.string.manage_content)) and hasClickAction()',self.helper)
        self.assertIn('SFDT22_PLAN_CONTENT_PANE_NOT_READY',self.helper)
        self.assertLess(self.kt.index('stage("subject_open_content")'),self.kt.index('stage("subject_open_manager")'))
    def test_07_diagnostic_mapping_is_allowlisted_and_no_raw_log_leak(self):
        codes=(('SFDT22_PLAN_CONTENT_TAB_MISSING','plan_content_tab_missing'),
               ('SFDT22_PLAN_CONTENT_TAB_TAP_FAILED','plan_content_tab_tap_failed'),
               ('SFDT22_PLAN_CONTENT_TAB_NOT_SELECTED','plan_content_tab_not_selected'),
               ('SFDT22_PLAN_CONTENT_PANE_NOT_READY','plan_content_pane_not_ready'))
        for marker,code in codes:
            self.assertIn(marker,self.kt)
            self.assertIn(code,v.REASON_CODES)
            e=v.summarize_failure('SFDT22_STEP_subject_open_content '+marker+' secret=NOPE',v.TESTS[1],0)
            self.assertEqual((e.stage,e.reason),('subject_open_content',code))
            self.assertNotIn('NOPE',str(e))
    def test_08_preserves_all_five_real_journeys_and_screenshots(self):
        self.assertEqual(self.kt.count('@Test fun '),5)
        for t in v.TESTS: self.assertIn('fun '+t+'(',self.kt)
        for s in v.SCREENS: self.assertIn('screenshot("'+s+'")',self.kt)
        self.assertIn('force_stopped_app_and_verified_new_pid=True',self.ver)
    def test_09_previous_topic_editor_fix_still_required(self):
        self.assertIn('openTopicEditorFromSubject()',self.kt)
        self.assertIn('replaceTopicNameInDialog(topicName)',self.kt)
        self.assertIn('hasTestTag("studyflow.topic_editor.name")',self.kt)
        self.assertIn('stage("topic_save")',self.kt)
    def test_10_exact_source_pins_and_ci_execution(self):
        for p in (K,V,H10):
            self.assertIn(hashlib.sha256((ROOT/p).read_bytes()).hexdigest()+'  '+p,self.wf)
        self.assertIn('python3 -B '+H10,self.wf)
        for n in range(1,10): self.assertIn('python3 -B scripts/test_sfdt22_hf%02d_contract.py'%n,self.wf)
    def test_11_api36_hosted_fail_closed_no_bypass(self):
        for s in ('needs: [source, emulator]','test "$EMULATOR_RESULT" = success',':app:assembleDebugAndroidTest','api-level: 36'):
            self.assertIn(s,self.wf)
        for s in ('continue-on-error:','allow-failure','if: false'):
            self.assertNotIn(s,self.wf)
        b=v.blank('a'*40,'1','1')
        for k in ('raw_logs_retained','physical_device_qualified','release_qualified','publication_allowed'):
            self.assertFalse(b[k])
    def test_12_no_mock_navigation_or_data_injection(self):
        for s in ('setContent {','mockk(','SamplePlanProvisioner','container.manageStudyPlan','runBlocking {','assumeTrue('):
            self.assertNotIn(s,self.kt)
        self.assertIn('createAndroidComposeRule<MainActivity>()',self.kt)
if __name__=='__main__':unittest.main(verbosity=2)
