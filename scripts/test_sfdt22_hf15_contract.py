#!/usr/bin/env python3
"""SFDT22 HF15: verify real Compose click, unique state and preserved CI gate."""
from __future__ import annotations
import hashlib, importlib.util, sys, types, unittest
from pathlib import Path
R=Path(__file__).resolve().parents[1]
K='app/src/androidTest/java/com/mikeyphw/studyflow/Sfdt22HostedStudyJourneyTest.kt'
V='scripts/sfdt22_emulator_verify.py'
WF='.github/workflows/studyflow-sfdt22-hosted-journeys.yml'
D='docs/actions/SFDT22_HF15_CI_FAILURE_REMEDIATION.md'
T='scripts/test_sfdt22_hf15_contract.py'
if 'sfdt21_emulator_verify' not in sys.modules:
    sys.modules['sfdt21_emulator_verify']=types.ModuleType('sfdt21_emulator_verify')
spec=importlib.util.spec_from_file_location('sfdt22_hf15_reader',R/V)
v=importlib.util.module_from_spec(spec);spec.loader.exec_module(v)
class HF15(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.kt=(R/K).read_text(); cls.wf=(R/WF).read_text(); cls.helper=cls.kt.split('private fun ensureSavedSubjectExpanded() {',1)[1].split('private fun awaitTopicSaved()',1)[0]
    def test_01_click_is_real_and_scoped(self):
        for s in ('awaitSubjectSaved()', 'hasAnyAncestor(subjectCardTag)', 'hasClickAction()',
                  'val semanticsClick = runCatching {', 'action.performClick()',
                  'if (expanded.fetchSemanticsNodes().size == 1) return',
                  'val expandedAfterSemantics',
                  'action.assertIsDisplayed().performTouchInput { click() }'):
            self.assertIn(s, self.helper)
        self.assertLess(self.helper.index('action.performClick()'),self.helper.index('performTouchInput { click() }'))
        self.assertEqual(self.helper.count('performTouchInput { click() }'),1)
    def test_02_obstructions_and_double_toggle_guarded(self):
        for s in ('subjectSnackbar.fetchSemanticsNodes().isEmpty()',
                  'topicSnackbar.fetchSemanticsNodes().isEmpty()',
                  'failureSnackbar.fetchSemanticsNodes().isEmpty()',
                  'if (collapsed.fetchSemanticsNodes().size != 1)',
                  'SFDT22_SUBJECT_EXPAND_STATE_AMBIGUOUS',
                  'SFDT22_SUBJECT_EXPAND_SNACKBAR_OBSTRUCTION'):
            self.assertIn(s,self.helper)
        self.assertLess(self.helper.index('topicSnackbar.fetchSemanticsNodes().isEmpty()'),
                        self.helper.index('performTouchInput { click() }'))
    def test_03_accurate_sanitized_failure_codes(self):
        checks=(('SUBJECT_EXPAND_SEMANTIC_CLICK_FAILED','subject_expand_semantic_click_failed'),
                ('SUBJECT_EXPAND_TOUCH_FAILED','subject_expand_touch_failed'),
                ('SUBJECT_EXPAND_STATE_AMBIGUOUS','subject_expand_state_ambiguous'),
                ('SUBJECT_EXPAND_SNACKBAR_OBSTRUCTION','subject_expand_snackbar_obstruction'),
                ('SUBJECT_NOT_EXPANDED','subject_not_expanded'))
        for marker,reason in checks:
            self.assertIn('SFDT22_'+marker,self.helper)
            self.assertIn(reason,v.REASON_CODES)
            fail=v.summarize_failure('SFDT22_STEP_topic_expand SFDT22_'+marker+' PRIVATE=secret',v.TESTS[1],1)
            self.assertEqual((fail.reason,fail.stage),(reason,'topic_expand'))
            self.assertNotIn('PRIVATE',str(fail))
    def test_04_source_verification_not_diluted(self):
        for s in ('stage("topic_confirm") { awaitTopicSaved() }',
                  'stage("topic_expand") { ensureSavedSubjectExpanded() }',
                  'rows.onFirst().assertIsDisplayed()',
                  'force_stopped_app_and_verified_new_pid=True'):
            self.assertIn(s,self.kt+ (R/V).read_text())
        self.assertEqual(self.kt.count('@Test fun '),5)
        self.assertEqual(len(v.SCREENS),7)
        for bad in ('setContent {', 'mockk(', 'assumeTrue(', 'content.saveTopic('):
            self.assertNotIn(bad,self.helper)
    def test_05_exact_pins_and_five_real_journeys(self):
        for p in (K,V,T,D):
            self.assertIn(hashlib.sha256((R/p).read_bytes()).hexdigest()+'  '+p,self.wf)
        for s in ('python3 -B scripts/test_sfdt22_hf15_contract.py',
                  'needs: [source, emulator]', 'api-level: 36',
                  'test "$EMULATOR_RESULT" = success', ':app:assembleDebugAndroidTest'):
            self.assertIn(s,self.wf)
        self.assertNotIn('continue-on-error:',self.wf)
    def test_06_historical_manifest_restored(self):
        import json
        m=json.loads((R/'devtool-overlay.json').read_text())
        self.assertEqual(m['name'],'studyflow_failure_audit_ov02_sflp_programming_contracts_v1')
        self.assertEqual(m['target'],'studyflow_android')
if __name__=='__main__': unittest.main(verbosity=2)
