#!/usr/bin/env python3
"""HF39: physical runner visibility, distinct Compose checkpoints, and immutable evidence."""
from __future__ import annotations
import hashlib
from pathlib import Path
import sys
import unittest

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'scripts'))
import sfdt22_emulator_verify as host

PROD='feature/practice/src/main/java/com/mikeyphw/studyflow/feature/practice/PracticeScreen.kt'
UI='app/src/androidTest/java/com/mikeyphw/studyflow/Sfdt22HostedStudyJourneyTest.kt'
HOST='scripts/sfdt22_emulator_verify.py'
WF='.github/workflows/studyflow-sfdt22-hosted-journeys.yml'
SELF='scripts/test_sfdt22_hf39_runner_navigation_contract.py'
DIAGS=('RUNNER_ROOT_MISSING','RUNNER_QUESTION_OFFSCREEN',
       'RUNNER_ANSWER_OFFSCREEN','RUNNER_ANSWER_DISABLED')

class HF39RunnerNavigation(unittest.TestCase):
    def read(self,path):return (ROOT/path).read_text(encoding='utf-8')
    def journey(self):
        return self.read(UI).split('    @Test fun realUserAnswersQuestionAndOpensReviewQueue() {',1)[1].split(
               '    @Test fun realUserSeesPersistedProgressAfterColdRestart()',1)[0]

    def test_01_real_production_scrolls_to_newly_inserted_runner(self):
        src=self.read(PROD)
        self.assertIn('import androidx.compose.foundation.lazy.rememberLazyListState',src)
        self.assertEqual(src.count('val practiceListState = rememberLazyListState()'),1)
        self.assertIn('LaunchedEffect(activeTest?.id?.value, activeMock?.id?.value)',src)
        self.assertIn('if (activeTest != null || activeMock != null)',src)
        self.assertIn('practiceListState.scrollToItem(1)',src)
        self.assertEqual(src.count('state = practiceListState,'),1)
        self.assertIn('val activeTest = activeTestId?.let',src)
        self.assertIn('val activeMock = activeMockId?.let',src)
        self.assertIn('activeTestId = started.id.value',src)
        self.assertIn('started.startedAtEpochMillis == null',src)

    def test_02_scoped_question_before_scoped_answer_without_simultaneous_visibility(self):
        ui=self.read(UI)
        j=self.journey()
        self.assertIn('stage("practice_runner_question") {\n            awaitRunnerQuestionReady()\n        }',j)
        self.assertNotIn('visible(questionStatement)\n            awaitRunnerQuestionReady()',j)
        helper=ui.split('    private fun awaitRunnerQuestionReady() {',1)[1].split(
            '    private fun persistedPracticeAnswerCount()',1)[0]
        for marker in ('hasTestTag("studyflow.practice.runner")',
                       'hasTestTag("studyflow.practice.runner.question")',
                       'hasText(questionStatement, substring = false)',
                       'hasTestTag("studyflow.practice.runner.answer")',
                       'hasText("True") and hasClickAction()',
                       'hasAnyAncestor(hasTestTag("studyflow.practice.runner"))',
                       'performScrollToNode(runner)',
                       'questions.onFirst().assertIsDisplayed()',
                       'answers.onFirst().assertIsDisplayed().assertIsEnabled()',
                       'RUNNER_ROOT_MISSING','RUNNER_ANSWER_OFFSCREEN'):
            self.assertIn(marker,helper,marker)
        self.assertLess(helper.index('questions.onFirst().assertIsDisplayed()'),
                        helper.index('answers.onFirst().assertIsDisplayed().assertIsEnabled()'))
        self.assertEqual(helper.count('hasAnyAncestor(hasTestTag("studyflow.practice.runner"))'),2)
        self.assertIn('useUnmergedTree = true',helper)
        self.assertNotIn('setContent(',helper)
        self.assertNotIn('assumeTrue(',helper)

    def test_03_room_start_and_answer_proofs_are_not_downgraded(self):
        ui=self.read(UI)
        j=self.journey()
        seq=['awaitRealPracticeAttemptStarted()', 'awaitRunnerQuestionReady()',
             'clickLabel("True")','awaitRealPracticeAnswerPersisted()',
             'click(PracticeR.string.finish_attempt)']
        for token in seq:self.assertEqual(j.count(token),1,token)
        self.assertEqual([j.index(s) for s in seq],sorted(j.index(s) for s in seq))
        for token in ('persistedPracticeAttemptStartedCount() == 1L',
                      'persistedPracticeAnswerCount() == 1L',
                      'SQLiteDatabase.OPEN_READONLY',
                      'hasTestTag("studyflow.practice.runner.question")'):
            self.assertIn(token,ui)
        self.assertEqual(ui.count('@Test fun '),5)
        self.assertEqual(ui.count('screenshot("'),7)

    def test_04_new_failures_are_bounded_to_real_practice_runner(self):
        for code in DIAGS:
            self.assertIn(code,host.PRACTICE_DIAG_CODES)
            raw='SFDT22_STEP_practice_runner_question\nSFDT22_PRACTICE_DIAG_'+code+' USER_SECRET_TEST'
            result=host.summarize_failure(raw,host.TESTS[3],2)
            self.assertEqual((result.stage,result.reason),
                             ('practice_runner_question','practice_'+code.lower()))
            self.assertNotIn('USER_SECRET_TEST',str(vars(result)))
            for wrong_stage in ('practice_start_test','practice_answer_acknowledged'):
                rejected=host.summarize_failure(raw.replace('practice_runner_question',wrong_stage),host.TESTS[3],2)
                self.assertEqual(rejected.reason,'practice_diagnostic_unreviewed')
            unrelated=host.summarize_failure(raw,host.TESTS[0],2)
            self.assertNotEqual(unrelated.reason,'practice_'+code.lower())

    def test_05_immutable_source_hashes_and_paused_hosted_gate(self):
        wf=self.read(WF)
        for path in (PROD,UI,HOST,SELF):
            digest=hashlib.sha256((ROOT/path).read_bytes()).hexdigest()
            self.assertEqual(wf.count(digest+'  '+path),1,path)
        self.assertIn('python3 -B '+SELF,wf)
        self.assertIn('needs: [source, emulator]',wf)
        self.assertIn('api-level: 36',wf)
        self.assertIn('seven unique screenshots',wf)
        self.assertIn(':app:assembleDebugAndroidTest',wf)
        self.assertEqual(wf.count("github.event_name == 'workflow_dispatch' && vars.STUDYFLOW_SFDT22_HOSTED_ENABLED == 'true'"),2)
        self.assertIn('test "$SOURCE_RESULT" = success',wf)
        self.assertIn('test "$EMULATOR_RESULT" = success',wf)
        for bad in ('continue-on-error:','if: false','pull_request_target:','contents: write'):
            self.assertNotIn(bad,wf)

    def test_06_mutation_negatives_reject_removed_navigation_and_proofs(self):
        prod=self.read(PROD);ui=self.read(UI)
        for marker in ('practiceListState.scrollToItem(1)','state = practiceListState,'):
            self.assertEqual(prod.count(marker),1)
            self.assertNotIn(marker,prod.replace(marker,'',1))
        runner=ui.split('    private fun awaitRunnerQuestionReady() {',1)[1].split(
            '    private fun persistedPracticeAnswerCount()',1)[0]
        for marker in ('performScrollToNode(runner)','questions.onFirst().assertIsDisplayed()',
                       'answers.onFirst().assertIsDisplayed().assertIsEnabled()'):
            self.assertIn(marker,runner)
            self.assertNotIn(marker,runner.replace(marker,'',1))

if __name__=='__main__':unittest.main(verbosity=2)
