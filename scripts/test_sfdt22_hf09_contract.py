#!/usr/bin/env python3
"""HF09: verify topic editor is reached through real tagged UI, not isDialog()."""
from __future__ import annotations
import hashlib, importlib.util, sys, types, unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
if 'sfdt21_emulator_verify' not in sys.modules:
    stub=types.ModuleType('sfdt21_emulator_verify')
    stub.adb=lambda *args,**kwargs:b''
    sys.modules['sfdt21_emulator_verify']=stub
spec=importlib.util.spec_from_file_location('sfdt22_hf09_verifier',ROOT/'scripts/sfdt22_emulator_verify.py')
assert spec and spec.loader
v=importlib.util.module_from_spec(spec)
spec.loader.exec_module(v)
K='app/src/androidTest/java/com/mikeyphw/studyflow/Sfdt22HostedStudyJourneyTest.kt'
S='feature/subjects/src/main/java/com/mikeyphw/studyflow/feature/subjects/StudyContentScreen.kt'
D='feature/subjects/src/main/java/com/mikeyphw/studyflow/feature/subjects/StudyContentDialogs.kt'
V='scripts/sfdt22_emulator_verify.py'
WF='.github/workflows/studyflow-sfdt22-hosted-journeys.yml'
H6='scripts/test_sfdt22_hf06_contract.py'
H8='scripts/test_sfdt22_hf08_contract.py'
H9='scripts/test_sfdt22_hf09_contract.py'
class HF09(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.kt=(ROOT/K).read_text()
        cls.ui=(ROOT/S).read_text()
        cls.dialog=(ROOT/D).read_text()
        cls.verify=(ROOT/V).read_text()
        cls.wf=(ROOT/WF).read_text()
        cls.h6=(ROOT/H6).read_text()
        cls.h8=(ROOT/H8).read_text()
    def test_01_real_subject_card_add_topic_is_tagged(self):
        self.assertIn('modifier = Modifier.testTag("studyflow.subject_card.add_topic")', self.ui)
        self.assertIn('onClick = { onAddTopic(null) }',self.ui)
        self.assertIn('import androidx.compose.ui.platform.testTag',self.ui)
        self.assertEqual(self.ui.count('Modifier.testTag("studyflow.subject_card.add_topic")'),1)
    def test_02_true_topic_editor_owns_unique_named_input(self):
        self.assertEqual(self.dialog.count('testTag = "studyflow.topic_editor.name"'),1)
        self.assertIn('TextFieldLine(name, { name = it }, R.string.name, testTag = "studyflow.topic_editor.name")',self.dialog)
        self.assertIn('OutlinedTextField(',self.dialog)
        self.assertIn('enabled = valid,',self.dialog)
    def test_03_topic_open_is_genuine_touch_and_positive_verification(self):
        self.assertIn('private fun openTopicEditorFromSubject()',self.kt)
        self.assertIn('val addActions = rule.onAllNodes(',self.kt)
        self.assertIn('hasTestTag("studyflow.subject_card.add_topic"), useUnmergedTree = true',self.kt)
        self.assertIn('addActions.onFirst().assertIsDisplayed().performTouchInput { click() }',self.kt)
        self.assertIn('stage("topic_open_dialog") { openTopicEditorFromSubject() }',self.kt)
        self.assertNotIn('stage("topic_open_dialog") { click(SubjectsR.string.add_topic) }',self.kt)
    def test_04_topic_field_does_not_rely_on_isDialog_semantics(self):
        helper=self.kt.split('private fun replaceTopicNameInDialog(value: String) {')[1].split('    private fun click(resId: Int)')[0]
        self.assertNotIn('isDialog()',helper)
        self.assertNotIn('hasAnyAncestor(isDialog())',helper)
        self.assertIn('hasTestTag("studyflow.topic_editor.name")',helper)
        self.assertIn('hasSetTextAction()',helper)
        self.assertIn('hasAnyAncestor(nameTag)',helper)
        self.assertIn('editor.assertIsDisplayed().performTextReplacement(value)',helper)
        self.assertNotIn('rule.onAllNodes(hasSetTextAction()).onFirst()',helper)
    def test_05_value_is_verified_before_real_save(self):
        self.assertIn('hasText(value)',self.kt)
        self.assertIn('SFDT22_TOPIC_VALUE_NOT_WRITTEN',self.kt)
        self.assertLess(self.kt.index('stage("topic_fill_name") { replaceTopicNameInDialog(topicName) }'),self.kt.index('stage("topic_save")'))
        self.assertIn('stage("topic_confirm") { visible(topicName) }',self.kt)
    def test_06_new_failure_codes_are_sanitized_and_reported(self):
        for marker,reason,step in (('SFDT22_TOPIC_ADD_BUTTON_MISSING','topic_add_button_missing','topic_open_dialog'),('SFDT22_TOPIC_DIALOG_MISSING','topic_dialog_missing','topic_open_dialog'),('SFDT22_TOPIC_EDITABLE_MISSING','topic_editable_missing','topic_fill_name'),('SFDT22_TOPIC_VALUE_NOT_WRITTEN','topic_value_not_written','topic_fill_name')):
            self.assertIn(marker,self.kt)
            self.assertIn(reason,v.REASON_CODES)
            e=v.summarize_failure('SFDT22_STEP_'+step+' '+marker+' token=PRIVATE',v.TESTS[1],0)
            self.assertEqual((e.reason,e.stage),(reason,step))
            self.assertNotIn('PRIVATE',str(e))
    def test_07_legacy_tests_preserved_but_buggy_isDialog_requirement_removed(self):
        self.assertNotIn('self.assertIn(\'hasSetTextAction() and hasAnyAncestor(isDialog())\',self.kt)',self.h6)
        self.assertIn('replaceTopicNameInDialog(topicName)',self.h6)
        self.assertNotIn("'rule.onAllNodes(isDialog(), useUnmergedTree = true)'",self.h8)
        self.assertIn('stage("topic_fill_name")',self.h8)
    def test_08_all_five_journeys_and_seven_screens_remain(self):
        self.assertEqual(self.kt.count('@Test fun '),5)
        for t in v.TESTS:self.assertIn('fun '+t+'(',self.kt)
        for s in v.SCREENS:self.assertIn('screenshot("'+s+'")',self.kt)
        self.assertIn('force_stopped_app_and_verified_new_pid=True',self.verify)
        self.assertIn('createAndroidComposeRule<MainActivity>()',self.kt)
    def test_09_source_pins_are_exact_for_every_changed_source(self):
        for p in (K,S,V,H6,H8,H9):
            self.assertIn(hashlib.sha256((ROOT/p).read_bytes()).hexdigest()+'  '+p,self.wf)
    def test_10_earlier_regressions_remain_mandatory(self):
        for n in ('test_sfdt22_emulator_contract.py','test_sfdt22_hf01_contract.py','test_sfdt22_hf02_contract.py','test_sfdt22_hf03_contract.py','test_sfdt22_hf04_contract.py','test_sfdt22_hf05_contract.py','test_sfdt22_hf06_contract.py','test_sfdt22_hf07_contract.py','test_sfdt22_hf08_contract.py','test_sfdt22_hf09_contract.py'):
            self.assertIn('python3 -B scripts/'+n,self.wf)
    def test_11_android_16_real_ui_gate_remains_fail_closed(self):
        for check in ('needs: [source, emulator]', 'test "$EMULATOR_RESULT" = success', ':app:assembleDebugAndroidTest','api-level: 36'):
            self.assertIn(check,self.wf)
        for bad in ('continue-on-error:','allow-failure','if: false'):
            self.assertNotIn(bad,self.wf)
        b=v.blank('a'*40,'1','1')
        for key in ('raw_logs_retained','physical_device_qualified','release_qualified','publication_allowed'):
            self.assertFalse(b[key])
    def test_12_no_replacement_of_real_navigation_or_fake_fixture(self):
        for bad in ('setContent {','mockk(','SamplePlanProvisioner','container.manageStudyPlan','runBlocking {','assumeTrue('):
            self.assertNotIn(bad,self.kt)
        self.assertIn('stage("subject_save") { clickDialog(SubjectsR.string.save) }',self.kt)
        self.assertIn('stage("topic_save") { clickDialog(SubjectsR.string.save) }',self.kt)
if __name__=='__main__':unittest.main(verbosity=2)
