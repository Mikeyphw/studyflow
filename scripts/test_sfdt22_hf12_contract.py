#!/usr/bin/env python3
"""HF12 follow-on: source/diagnostic contracts for still-hosted real journeys."""
from __future__ import annotations
import hashlib, importlib.util, sys, types, unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
if 'sfdt21_emulator_verify' not in sys.modules:
    stub=types.ModuleType('sfdt21_emulator_verify');stub.adb=lambda *a,**k:b''
    sys.modules['sfdt21_emulator_verify']=stub
spec=importlib.util.spec_from_file_location('sfdt22_hf12_verifier',ROOT/'scripts/sfdt22_emulator_verify.py')
assert spec and spec.loader
v=importlib.util.module_from_spec(spec);spec.loader.exec_module(v)
K='app/src/androidTest/java/com/mikeyphw/studyflow/Sfdt22HostedStudyJourneyTest.kt'
V='scripts/sfdt22_emulator_verify.py'
WF='.github/workflows/studyflow-sfdt22-hosted-journeys.yml'
D='feature/subjects/src/main/java/com/mikeyphw/studyflow/feature/subjects/StudyContentDialogs.kt'
H11='scripts/test_sfdt22_hf11_contract.py'
H12='scripts/test_sfdt22_hf12_contract.py'

class HF12(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.kt=(ROOT/K).read_text();cls.ver=(ROOT/V).read_text();cls.wf=(ROOT/WF).read_text();cls.dialog=(ROOT/D).read_text()
        cls.ready=cls.kt.split('    private fun openInnerTabReady(',1)[1].split('    private fun clickReviewCenterEntry()',1)[0]
        cls.cold=cls.kt.split('@Test fun realUserSeesPersistedProgressAfterColdRestart()',1)[1]

    def test_01_genuine_tab_with_real_pointer_and_selected_semantics(self):
        for term in ('SemanticsProperties.Role, Role.Tab',
                     'rule.waitUntil(45_000L) { tabs.fetchSemanticsNodes().isNotEmpty() }',
                     'selected.assertIsDisplayed().performTouchInput { click() }',
                     'tabs.onFirst().assertIsSelected()'):
            self.assertIn(term,self.ready)
        self.assertNotIn('navigate(',self.ready)

    def test_02_real_lazy_action_wait_not_only_early_selected_tab(self):
        self.assertIn('seek(contentAction)',self.ready)
        self.assertIn('hasClickAction()',self.ready)
        self.assertIn('alternativeActionResId: Int? = null',self.kt)
        self.assertIn('SFDT22_INNER_PANE_NOT_READY',self.ready)

    def test_03_review_action_accounts_for_due_and_empty_queue(self):
        self.assertIn('private fun clickReviewCenterEntry()',self.kt)
        self.assertIn('click(WorkspaceR.string.open_review_center)',self.kt)
        self.assertIn('click(WorkspaceR.string.review_now)',self.kt)
        self.assertIn('WorkspaceR.string.review_now,',self.kt)

    def test_04_session_and_practice_routes_are_staged_and_waited(self):
        for stage in ('session_open_overview','practice_open_questions','practice_open_reviews'):
            self.assertIn('stage("'+stage+'")',self.kt)
            self.assertIn(stage,v.STAGES)
        self.assertIn('openInnerTabReady(WorkspaceR.string.plan_overview, WorkspaceR.string.study_sessions)',self.kt)
        self.assertIn('openInnerTabReady(WorkspaceR.string.practice_questions, WorkspaceR.string.open_question_bank)',self.kt)

    def test_05_cold_restart_uses_real_pane_and_same_saved_subject_checks(self):
        for stage in ('restart_open_content','restart_open_overview','restart_open_questions','restart_open_reviews'):
            self.assertIn('stage("'+stage+'")',self.cold)
            self.assertIn(stage,v.STAGES)
        for term in ('stage("restart_open_content") { openPlanContentTab() }',
                     'awaitSubjectSaved()', 'awaitTopicSaved()', 'visible(questionStatement)',
                     'visible(flashcardFront)'):
            self.assertIn(term,self.cold)

    def test_06_allowlisted_errors_include_proper_stage_and_no_raw_leak(self):
        for marker,reason in (('INNER_TAB_MISSING','inner_tab_missing'),
                              ('INNER_TAB_TOUCH_FAILED','inner_tab_touch_failed'),
                              ('INNER_TAB_NOT_SELECTED','inner_tab_not_selected'),
                              ('INNER_PANE_NOT_READY','inner_pane_not_ready')):
            self.assertIn('SFDT22_'+marker,self.kt)
            self.assertIn(reason,v.REASON_CODES)
            error=v.summarize_failure('SFDT22_STEP_practice_open_reviews SFDT22_'+marker+' PRIVATE=123',v.TESTS[3],2)
            self.assertEqual((error.stage,error.reason),('practice_open_reviews',reason))
            self.assertNotIn('PRIVATE',str(error))

    def test_07_success_receipt_still_requires_exact_five_real_tests(self):
        self.assertEqual(self.kt.count('@Test fun '),5)
        for name in v.TESTS:self.assertIn('fun '+name+'(',self.kt)
        for name in v.SCREENS:self.assertIn('screenshot("'+name+'")',self.kt)
        self.assertIn('force_stopped_app_and_verified_new_pid=True',self.ver)
        self.assertIn('real_process_restart()',self.ver)

    def test_08_failed_receipt_honestly_counts_completed_tests(self):
        self.assertIn("evidence['completed_before_failure'] = len(results)",self.ver)
        self.assertIn("evidence.get('test_count', evidence.get('completed_before_failure', 0))",self.ver)
        self.assertIn("overall='FAILED_OR_NOT_RUN'",self.ver)
        self.assertIn('raw_logs_retained=False',self.ver)

    def test_09_previous_hf11_topic_scope_and_persistence_unchanged(self):
        for term in ('private fun awaitSubjectSaved()', 'private fun awaitTopicSaved()',
                     'stage("subject_confirm") { awaitSubjectSaved() }',
                     'stage("topic_confirm") { awaitTopicSaved() }',
                     'hasAnyAncestor(subjectCardTag)', 'action.performClick()',
                     'SFDT22_TOPIC_EDITOR_TAG_MISSING', 'editor.assertTextContains(value)'):
            self.assertIn(term,self.kt)

    def test_10_all_source_hashes_pinned_and_previous_checks_kept(self):
        for path in (K,V,D,H11,H12):
            self.assertIn(hashlib.sha256((ROOT/path).read_bytes()).hexdigest()+'  '+path,self.wf)
        for n in range(1,13):
            self.assertIn('python3 -B scripts/test_sfdt22_hf%02d_contract.py'%n,self.wf)

    def test_11_hosted_api36_gate_not_degraded(self):
        for s in ('api-level: 36','needs: [source, emulator]',
                  'test "$SOURCE_RESULT" = success','test "$EMULATOR_RESULT" = success',
                  ':app:assembleDebugAndroidTest','--verify'):
            self.assertIn(s,self.wf)
        for forbidden in ('continue-on-error:','allow-failure','if: false'):
            self.assertNotIn(forbidden,self.wf)

    def test_12_no_fake_ui_or_repository_injection(self):
        for s in ('setContent {','mockk(','SamplePlanProvisioner','assumeTrue(','runBlocking {','container.manageStudyPlan'):
            self.assertNotIn(s,self.kt)
        self.assertIn('createAndroidComposeRule<MainActivity>()',self.kt)

    def test_13_topic_editor_dialog_container_has_real_test_tag(self):
        block=self.dialog.split('internal fun TopicEditorDialog(',1)[1].split('internal fun ConfirmDeleteDialog',1)[0]
        self.assertIn('modifier = Modifier.testTag("studyflow.topic_editor.dialog")',block)
        self.assertEqual(self.dialog.count('Modifier.testTag("studyflow.topic_editor.dialog")'),1)
        self.assertIn('testTag = "studyflow.topic_editor.name"',block)

    def test_14_nonmutating_topic_open_uses_guarded_single_recovery(self):
        helper=self.kt.split('private fun openTopicEditorFromSubject() {',1)[1].split('private fun replaceTopicNameInDialog',1)[0]
        for term in ('action.performClick()', 'val dialogTag = hasTestTag("studyflow.topic_editor.dialog")',
                     'val modalMerged = rule.onAllNodes(dialogTag)',
                     'val modalUnmerged = rule.onAllNodes(dialogTag, useUnmergedTree = true)',
                     'fun editorVisible(): Boolean',
                     'if (!editorVisible()) {',
                     'retry.performTouchInput { click() }',
                     'SFDT22_TOPIC_DIALOG_MISSING'):
            self.assertIn(term,helper)
        self.assertEqual(helper.count('retry.performTouchInput { click() }'),1)
        self.assertEqual(helper.count('action.performClick()'),1)
        self.assertNotIn('content.saveTopic(',helper)
        self.assertNotIn('clickDialog(SubjectsR.string.save)',helper)

    def test_15_recovery_waits_for_real_transient_snackbar_to_clear(self):
        helper=self.kt.split('private fun openTopicEditorFromSubject() {',1)[1].split('private fun replaceTopicNameInDialog',1)[0]
        self.assertIn('SubjectsR.string.subject_saved',helper)
        self.assertIn('saveSnackbar.fetchSemanticsNodes().isEmpty()',helper)
        self.assertLess(helper.index('saveSnackbar.fetchSemanticsNodes().isEmpty()'),
                        helper.index('retry.performTouchInput { click() }'))
        self.assertIn('SFDT22_TOPIC_SNACKBAR_OBSTRUCTION',helper)
        self.assertIn('SFDT22_TOPIC_FALLBACK_TOUCH_FAILED',helper)

    def test_16_diagnostic_reasons_remain_sanitized(self):
        for marker,reason in (('SFDT22_TOPIC_SNACKBAR_OBSTRUCTION','topic_snackbar_obstruction'),
                              ('SFDT22_TOPIC_FALLBACK_TOUCH_FAILED','topic_fallback_touch_failed')):
            self.assertIn(reason,v.REASON_CODES)
            f=v.summarize_failure('SFDT22_STEP_topic_open_dialog '+marker+' TOP_SECRET',v.TESTS[1],1)
            self.assertEqual((f.stage,f.reason),('topic_open_dialog',reason))
            self.assertNotIn('TOP_SECRET',str(f))

    def test_17_hf11_saved_data_and_full_android_gate_still_required(self):
        for term in ('private fun awaitSubjectSaved()', 'private fun awaitTopicSaved()',
                     'stage("topic_confirm") { awaitTopicSaved() }',
                     'stage("subject_confirm") { awaitSubjectSaved() }'):
            self.assertIn(term,self.kt)
        for term in ('needs: [source, emulator]', ':app:assembleDebugAndroidTest',
                     'test "$EMULATOR_RESULT" = success', 'api-level: 36'):
            self.assertIn(term,self.wf)
        self.assertNotIn('continue-on-error:',self.wf)
        self.assertEqual(self.kt.count('@Test fun '),5)

if __name__=='__main__':unittest.main(verbosity=2)
