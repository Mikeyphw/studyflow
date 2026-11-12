#!/usr/bin/env python3
"""HF10 source and negative tests. These are never Android runtime qualification."""
from __future__ import annotations
import hashlib
from pathlib import Path
import sys
import unittest
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'scripts'))
import sfdt21_emulator_verify as sfdt21
import sfdt22_emulator_verify as sfdt22

UI22='app/src/androidTest/java/com/mikeyphw/studyflow/Sfdt22HostedStudyJourneyTest.kt'
UI21='app/src/androidTest/java/com/mikeyphw/studyflow/Sfdt21HostedRealUiTest.kt'
HOST21='scripts/sfdt21_emulator_verify.py'
WF22='.github/workflows/studyflow-sfdt22-hosted-journeys.yml'
WF21='.github/workflows/studyflow-sfdt21-hosted-ui.yml'
TEST='scripts/test_sfdt22_hf30_hf10_dual_android_contract.py'
DOC='docs/actions/SFDT22_HF30_HF10_PRACTICE_ANSWER_AND_SEARCH_RETURN.md'
SEARCH_STAGES=('search_shell_ready','search_action_ready','search_open_action',
    'search_editor_ready','search_back_navigation','search_route_closed',
    'search_today_selected')

class HF10SourceContracts(unittest.TestCase):
    def read(self,p):return (ROOT/p).read_text(encoding='utf-8')
    def test_room_committed_answer_and_negative_categories(self):
        ui=self.read(UI22)
        a=ui.split('    private fun persistedPracticeAnswerCount()',1)[1].split('    private fun click(resId:',1)[0]
        for token in ('SQLiteDatabase.OPEN_READONLY','study_tests AS tests',
            'JOIN study_plans AS plans', 'startedAtEpochMillis IS NOT NULL',
            'submittedAtEpochMillis IS NULL', 'tests.questionCount = 1 AND tests.answeredCount = 1',
            'arrayOf(planName, "$planName practice")', 'rule.waitUntil(35_000L)',
            'SFDT22_PRACTICE_ANSWER_OPERATION_FAILED','SFDT22_PRACTICE_ANSWER_NOT_PERSISTED',
            'persistedPracticeAnswerCount() == 1L'):
            self.assertIn(token,a,token)
        for forbidden in ('INSERT INTO','UPDATE study_tests','DELETE FROM','assumeTrue(',
                          'setContent(', 'Thread.sleep(', 'practice.answerPracticeQuestion('):
            self.assertNotIn(forbidden,a)
        self.assertLess(a.index('persistedPracticeAnswerCount() == 1L'),a.index('throw AssertionError("SFDT22_PRACTICE_ANSWER_NOT_PERSISTED"'))
        b=ui.split('    @Test fun realUserAnswersQuestionAndOpensReviewQueue() {',1)[1].split('    @Test fun realUserSeesPersistedProgressAfterColdRestart()',1)[0]
        self.assertIn('stage("practice_answer_acknowledged") {\n',b)
        self.assertIn('awaitRealPracticeAnswerPersisted()',b)
        self.assertIn('clickLabel("True")',b)
        self.assertIn('click(PracticeR.string.finish_attempt)',b)
        self.assertIn('assertOptionalPracticeAckVisible()',b)
        self.assertIn('clickReviewCenterEntry()',b)
        self.assertEqual(ui.count('@Test fun '),5)
        self.assertEqual(ui.count('screenshot("'),7)

    def test_exact_4_sfdt21_real_ui_tests_preserved_and_waits(self):
        src=self.read(UI21)
        self.assertEqual(src.count('    fun realActivity'),4)
        self.assertIn('createAndroidComposeRule<MainActivity>()',src)
        self.assertIn('rule.onNodeWithContentDescription(searchLabel)',src)
        self.assertIn('searchAction.performClick()',src)
        self.assertIn('hasSetTextAction()',src)
        self.assertIn('Espresso.pressBack()',src)
        self.assertIn('tab("today").assertIsDisplayed().assertIsSelected()',src)
        body=src.split('fun realActivityOpensGlobalSearchAndReturnsToToday() {',1)[1].split('    @Test',1)[0]
        for stage in SEARCH_STAGES:
            self.assertEqual(body.count('searchStage("'+stage+'")'),1)
        offsets=[body.index('searchStage("'+s+'")') for s in SEARCH_STAGES]
        self.assertEqual(offsets,sorted(offsets))
        self.assertIn('editors.fetchSemanticsNodes().isEmpty()',body)
        # Kotlin Compose UI test collections are indexed; onFirst() is not supported.
        self.assertNotIn('.onFirst()',src)
        self.assertIn('editors[0].assertIsDisplayed()',body)
        self.assertIn('awaitRealShell()',body)
        for forbidden in ('setContent(', 'assumeTrue(', 'Thread.sleep(', 'mockk(', 'runCatching { Espresso.pressBack() }'):
            self.assertNotIn(forbidden,body)

    def test_sanitized_sfdt21_stage_failure_and_good_results(self):
        self.assertEqual(tuple(sfdt21.SEARCH_FAILURE_STAGES),SEARCH_STAGES)
        name=sfdt21.TESTS[3]
        raw=('INSTRUMENTATION_STATUS: test='+name+'\nINSTRUMENTATION_STATUS_CODE: 1\n'
             'java.lang.AssertionError: SFDT21_STEP_search_editor_ready SECRET_USER_STRING\n'
             'INSTRUMENTATION_STATUS: test='+name+'\nINSTRUMENTATION_STATUS_CODE: -2\n')
        receipt=sfdt21.summarize(raw)
        self.assertEqual(receipt['failure_stage'],'search_editor_ready')
        self.assertIn(name,receipt['failed_tests'])
        self.assertFalse(sfdt21.valid_summary(receipt))
        self.assertNotIn('SECRET_USER_STRING',str(receipt))
        for bogus in ('SFDT21_STEP_sensitive_private_user', 'SFDT21_STEP_search_editor_readyx'):
            changed=raw.replace('SFDT21_STEP_search_editor_ready',bogus)
            self.assertEqual(sfdt21.summarize(changed)['failure_stage'],'unavailable')
        # A stage marker on an unrelated test must not be accepted.
        unrelated=raw.replace(name,sfdt21.TESTS[0])
        self.assertEqual(sfdt21.summarize(unrelated)['failure_stage'],'unavailable')

    def test_strict_source_pins_in_both_workflows(self):
        w22,w21=self.read(WF22),self.read(WF21)
        for wf,paths in ((w22,(UI22,TEST,DOC)),(w21,(UI21,HOST21,TEST,DOC))):
            for p in paths:
                pin=hashlib.sha256((ROOT/p).read_bytes()).hexdigest()+'  '+p
                self.assertEqual(wf.count(pin),1,(p,wf[:100]))
            self.assertEqual(wf.count('python3 -B '+TEST),1)
            self.assertNotIn('continue-on-error:',wf)
            self.assertNotIn('if: false',wf)
            self.assertNotIn('pull_request_target:',wf)
            self.assertNotIn('contents: write',wf)
        self.assertIn('api-level: 36',w21)
        self.assertIn('api-level: 36',w22)
        self.assertIn('seven unique screenshots',w22)
        self.assertIn('needs: [source, emulator]',w22)

    def test_historical_contracts_preserved(self):
        h28=self.read('scripts/test_sfdt22_hf28_practice_contract.py')
        h09=self.read('scripts/test_sfdt22_hf30_hf09_practice_readiness_contract.py')
        self.assertIn("self.assertEqual(ui.count('@Test fun '),5)",h28)
        self.assertIn('awaitRealPracticeTestReady()',self.read(UI22))
        self.assertIn('practice_test_ready',h09)
        self.assertIn('source_sha',self.read('scripts/sfdt22_hf30_hf07_diagnostics.py'))

if __name__=='__main__':unittest.main(verbosity=2)
