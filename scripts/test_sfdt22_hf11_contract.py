#!/usr/bin/env python3
"""HF11 deep-audit regression: persisted UI checkpoints and genuine topic editor."""
from __future__ import annotations
import hashlib
import importlib.util
import sys
import types
import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
if 'sfdt21_emulator_verify' not in sys.modules:
    stub=types.ModuleType('sfdt21_emulator_verify')
    stub.adb=lambda *a,**kw:b''
    sys.modules['sfdt21_emulator_verify']=stub
spec=importlib.util.spec_from_file_location('sfdt22_hf11', ROOT/'scripts/sfdt22_emulator_verify.py')
assert spec and spec.loader
v=importlib.util.module_from_spec(spec)
spec.loader.exec_module(v)
K='app/src/androidTest/java/com/mikeyphw/studyflow/Sfdt22HostedStudyJourneyTest.kt'
S='feature/subjects/src/main/java/com/mikeyphw/studyflow/feature/subjects/StudyContentScreen.kt'
V='scripts/sfdt22_emulator_verify.py'
H9='scripts/test_sfdt22_hf09_contract.py'
H11='scripts/test_sfdt22_hf11_contract.py'
WF='.github/workflows/studyflow-sfdt22-hosted-journeys.yml'
class HF11(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.kt=(ROOT/K).read_text()
        cls.ui=(ROOT/S).read_text()
        cls.ver=(ROOT/V).read_text()
        cls.wf=(ROOT/WF).read_text()
        cls.h9=(ROOT/H9).read_text()
    def test_01_real_subject_card_id_visible_after_save(self):
        self.assertIn('studyflow.subject_card.name.${subject.name}',self.ui)
        self.assertIn('private fun awaitSubjectSaved()',self.kt)
        self.assertIn('stage("subject_confirm") { awaitSubjectSaved() }',self.kt)
        self.assertNotIn('stage("subject_confirm") { visible(subjectName) }',self.kt)
        self.assertIn('SFDT22_SUBJECT_CARD_MISSING',self.kt)
    def test_02_no_advance_from_unsaved_form_text(self):
        helper=self.kt.split('private fun awaitSubjectSaved() {')[1].split('private fun openTopicEditorFromSubject()')[0]
        self.assertIn('studyflow.subject_editor.name',helper)
        self.assertIn('SFDT22_SUBJECT_EDITOR_STILL_OPEN',helper)
        self.assertIn('hasTestTag("studyflow.subject_card.name.$subjectName")',helper)
        self.assertNotIn('hasText(subjectName)',helper)
    def test_03_topic_action_scoped_to_created_subject(self):
        helper=self.kt.split('private fun openTopicEditorFromSubject() {')[1].split('private fun replaceTopicNameInDialog')[0]
        self.assertIn('hasTestTag("studyflow.subject_card.add_topic")',helper)
        self.assertIn('hasAnyAncestor(subjectCardTag)',helper)
        self.assertIn('hasClickAction()',helper)
        self.assertIn('addActions.onFirst()',helper)
        self.assertIn('action.performClick()',helper)
        self.assertNotIn('addActions.onFirst().assertIsDisplayed().performTouchInput { click() }',helper)
        self.assertIn('SFDT22_TOPIC_ADD_BUTTON_MISSING',helper)
    def test_04_prove_topic_editor_independently_from_test_tag(self):
        helper=self.kt.split('private fun openTopicEditorFromSubject() {')[1].split('private fun replaceTopicNameInDialog')[0]
        self.assertIn('beforeFields',helper)
        self.assertIn('afterFields',helper)
        self.assertIn('taggedMerged',helper)
        self.assertIn('taggedUnmerged',helper)
        self.assertIn('SFDT22_TOPIC_EDITOR_TAG_MISSING',helper)
        self.assertIn('SFDT22_TOPIC_DIALOG_MISSING',helper)
        self.assertNotIn('isDialog()',helper)
    def test_05_true_editable_prefers_merged_then_unmerged(self):
        helper=self.kt.split('private fun replaceTopicNameInDialog(value: String) {')[1].split('    private fun click(resId: Int)')[0]
        self.assertIn('val mergedFields = rule.onAllNodes(hasSetTextAction() and nameTag)',helper)
        self.assertIn('val unmergedFields = rule.onAllNodes(editableMatcher, useUnmergedTree = true)',helper)
        self.assertIn('val editor = fields.onFirst()',helper)
        self.assertIn('editor.assertIsDisplayed().performTextReplacement(value)',helper)
        self.assertIn('editor.assertTextContains(value)',helper)
        self.assertIn('hasAnyAncestor(nameTag)',helper)
    def test_06_topic_saved_means_rendered_persisted_row(self):
        self.assertIn('studyflow.topic_row.name.${topic.name}',self.ui)
        self.assertIn('private fun awaitTopicSaved()',self.kt)
        self.assertIn('stage("topic_confirm") { awaitTopicSaved() }',self.kt)
        self.assertNotIn('stage("topic_confirm") { visible(topicName) }',self.kt)
        self.assertIn('SFDT22_TOPIC_ROW_MISSING',self.kt)
    def test_07_restart_checks_actual_saved_content(self):
        cold=self.kt.split('@Test fun realUserSeesPersistedProgressAfterColdRestart()')[1]
        self.assertIn('openPlanContentTab()',cold)
        self.assertIn('awaitSubjectSaved()',cold)
        self.assertIn('awaitTopicSaved()',cold)
    def test_08_no_faked_data_or_bypassed_ui(self):
        for forbidden in ('setContent {','mockk(','runBlocking {','assumeTrue(','SamplePlanProvisioner','container.manageStudyPlan'):
            self.assertNotIn(forbidden,self.kt)
        self.assertIn('createAndroidComposeRule<MainActivity>()',self.kt)
        self.assertIn('onClick = { onAddTopic(null) }',self.ui)
        self.assertIn('onClick = { addingSubject = true }',self.ui)
    def test_09_sanitized_failure_reasons_are_exhaustive(self):
        pairs=(('SFDT22_SUBJECT_CARD_MISSING','subject_card_missing','subject_confirm'),
               ('SFDT22_SUBJECT_EDITOR_STILL_OPEN','subject_editor_still_open','subject_confirm'),
               ('SFDT22_TOPIC_ADD_BUTTON_AMBIGUOUS','topic_add_button_ambiguous','topic_open_dialog'),
               ('SFDT22_TOPIC_EDITOR_TAG_MISSING','topic_editor_tag_missing','topic_open_dialog'),
               ('SFDT22_TOPIC_ROW_MISSING','topic_row_missing','topic_confirm'))
        for marker,code,stage in pairs:
            self.assertIn(marker,self.kt)
            self.assertIn(code,v.REASON_CODES)
            f=v.summarize_failure('SFDT22_STEP_'+stage+' '+marker+' PRIVATE_TOKEN',v.TESTS[1],0)
            self.assertEqual((f.reason,f.stage),(code,stage))
            self.assertNotIn('PRIVATE_TOKEN',str(f))
    def test_10_existing_later_journeys_still_mandatory(self):
        self.assertEqual(self.kt.count('@Test fun '),5)
        for name in v.TESTS:self.assertIn('fun '+name+'(',self.kt)
        for name in v.SCREENS:self.assertIn('screenshot("'+name+'")',self.kt)
        self.assertIn('force_stopped_app_and_verified_new_pid=True',self.ver)
        self.assertIn('realUserStartsPausesResumesAndCompletesASession',self.kt)
        self.assertIn('realUserAnswersQuestionAndOpensReviewQueue',self.kt)
    def test_11_existing_contracts_repaired_without_deleting_coverage(self):
        self.assertIn('private fun openTopicEditorFromSubject()',self.h9)
        self.assertIn('action.performClick()',self.h9)
        self.assertIn('stage("topic_confirm") { awaitTopicSaved() }',self.h9)
    def test_12_exact_sha_pins_and_required_ci_source(self):
        for path in (K,S,V,H9,H11):
            self.assertIn(hashlib.sha256((ROOT/path).read_bytes()).hexdigest()+'  '+path,self.wf)
        self.assertIn('python3 -B '+H11,self.wf)
        for n in range(1,11):self.assertIn('python3 -B scripts/test_sfdt22_hf%02d_contract.py'%n,self.wf)
    def test_13_hosted_gate_is_not_downgraded(self):
        for check in ('needs: [source, emulator]','test "$EMULATOR_RESULT" = success',':app:assembleDebugAndroidTest','api-level: 36'):
            self.assertIn(check,self.wf)
        for bad in ('continue-on-error:','allow-failure','if: false'):
            self.assertNotIn(bad,self.wf)
        blank=v.blank('a'*40,'1','1')
        for key in ('raw_logs_retained','physical_device_qualified','release_qualified','publication_allowed'):
            self.assertFalse(blank[key])
    def test_14_previous_hf08_hf09_hf10_route_preserved(self):
        self.assertIn('private fun openPlanContentTab()',self.kt)
        self.assertIn('stage("subject_open_content") { openPlanContentTab() }',self.kt)
        self.assertIn('stage("topic_open_dialog") { openTopicEditorFromSubject() }',self.kt)
        self.assertIn('stage("topic_fill_name") { replaceTopicNameInDialog(topicName) }',self.kt)
        self.assertIn('stage("topic_save") { clickDialog(SubjectsR.string.save) }',self.kt)
        self.assertIn('stage("subject_save") { clickDialog(SubjectsR.string.save) }',self.kt)
if __name__=='__main__':unittest.main(verbosity=2)
