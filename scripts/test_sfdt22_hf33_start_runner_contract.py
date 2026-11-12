#!/usr/bin/env python3
"""HF33: immutable, exact production runner start/answer contract and category negatives.

Offline source-contract only; not a substitute for hosted API36 instrumentation.
"""
from __future__ import annotations
import hashlib
from pathlib import Path
import sys
import unittest
ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
import sfdt22_emulator_verify as host
UI = 'app/src/androidTest/java/com/mikeyphw/studyflow/Sfdt22HostedStudyJourneyTest.kt'
PROD = 'feature/practice/src/main/java/com/mikeyphw/studyflow/feature/practice/PracticeScreen.kt'
HOST = 'scripts/sfdt22_emulator_verify.py'
SELF = 'scripts/test_sfdt22_hf33_start_runner_contract.py'
WF = '.github/workflows/studyflow-sfdt22-hosted-journeys.yml'

class HF33RealStartAndRunner(unittest.TestCase):
    def read(self, p): return (ROOT / p).read_text(encoding='utf-8')
    def journey(self):
        return self.read(UI).split('    @Test fun realUserAnswersQuestionAndOpensReviewQueue() {',1)[1].split(
            '    @Test fun realUserSeesPersistedProgressAfterColdRestart()',1)[0]

    def test_start_is_proved_before_runner_and_answer(self):
        src = self.read(UI); j = self.journey()
        self.assertIn('stage("practice_start_test") {',j)
        self.assertIn('click(PracticeR.string.start_or_resume)',j)
        self.assertIn('awaitRealPracticeAttemptStarted()',j)
        self.assertIn('stage("practice_runner_question") {',j)
        self.assertIn('awaitRunnerQuestionReady()',j)
        self.assertIn('stage("practice_answer_true") { clickLabel("True") }',j)
        places=[j.index(s) for s in (
            'click(PracticeR.string.start_or_resume)',
            'awaitRealPracticeAttemptStarted()',
            'awaitRunnerQuestionReady()',
            'clickLabel("True")',
            'awaitRealPracticeAnswerPersisted()',
            'click(PracticeR.string.finish_attempt)',
        )]
        self.assertEqual(places, sorted(places))
        start=src.split('    private fun persistedPracticeAttemptStartedCount(): Long? {',1)[1].split(
            '    private fun awaitRunnerQuestionReady() {',1)[0]
        for required in ('SQLiteDatabase.OPEN_READONLY', 'sql.rawQuery(',
                         'startedAtEpochMillis IS NOT NULL', 'submittedAtEpochMillis IS NULL',
                         'tests.questionCount = 1', 'arrayOf(planName, "$planName practice")',
                         'persistedPracticeAttemptStartedCount() == 1L',
                         'SFDT22_PRACTICE_DIAG_START_OPERATION_FAILED',
                         'diagnosePracticeAttemptStart()', 'rule.waitUntil(35_000L)'):
            self.assertIn(required,start)
        for forbidden in ('INSERT INTO','UPDATE study_tests','DELETE FROM','println(',
                          'Log.', 'openFileOutput(', 'Thread.sleep(', 'assumeTrue('):
            self.assertNotIn(forbidden,start)

    def test_real_production_tags_and_test_selector_guards(self):
        ui=self.read(UI); prod=self.read(PROD)
        for tag in ('studyflow.practice.test.start', 'studyflow.practice.runner',
                    'studyflow.practice.runner.question', 'studyflow.practice.runner.answer'):
            self.assertIn('Modifier.testTag("'+tag+'")' if tag != 'studyflow.practice.runner.answer'
                          else '.testTag("'+tag+'")',prod)
            self.assertIn('"'+tag+'"',ui)
        self.assertIn('import androidx.compose.ui.platform.testTag',prod)
        selector=ui.split('    private fun clickLabel(label: String) {',1)[1].split(
            '    private fun clickDialog(',1)[0]
        self.assertIn('text(PracticeR.string.start_or_resume)',selector)
        self.assertIn('"True" -> "studyflow.practice.runner.answer"',selector)
        self.assertIn('hasTestTag(exactPracticeTag) and hasText(label) and hasClickAction()',selector)
        self.assertIn('candidates.fetchSemanticsNodes().size != 1',selector)
        self.assertIn('SFDT22_PRACTICE_DIAG_',selector)
        ready=ui.split('    private fun awaitRunnerQuestionReady() {',1)[1].split(
            '    private fun persistedPracticeAnswerCount()',1)[0]
        self.assertIn('hasAnyAncestor(hasTestTag("studyflow.practice.runner"))',ready)
        self.assertIn('hasTestTag("studyflow.practice.runner.question")',ready)
        self.assertIn('hasTestTag("studyflow.practice.runner.answer")',ready)
        self.assertIn('assertIsDisplayed()',ready)
        self.assertIn('assertIsEnabled()',ready)
        self.assertNotIn('setContent(',ready)

    def test_qualified_practice_diagnostics_are_bounded_and_stage_scoped(self):
        codes=('START_DB_MISSING','START_LOOKUP_FAILED','START_TEST_MISSING',
               'START_TEST_AMBIGUOUS','START_ATTEMPT_NOT_STARTED',
               'START_ATTEMPT_SUBMITTED','START_QUESTION_COUNT_MISMATCH',
               'START_PREDICATE_MISMATCH','START_OPERATION_FAILED',
               'START_CONTROL_MISSING','START_CONTROL_AMBIGUOUS','START_CONTROL_NOT_READY',
               'RUNNER_QUESTION_MISSING','RUNNER_QUESTION_AMBIGUOUS',
               'RUNNER_ANSWER_MISSING','RUNNER_ANSWER_AMBIGUOUS', 'RUNNER_NOT_READY')
        for suffix in codes:
            self.assertIn(suffix,host.PRACTICE_DIAG_CODES)
            stage=('practice_start_test' if suffix.startswith('START_') else
                   'practice_runner_question')
            raw='SFDT22_STEP_'+stage+'\nSFDT22_PRACTICE_DIAG_'+suffix+' PRIVATE_PLAN_NAME'
            outcome=host.summarize_failure(raw,host.TESTS[3],2)
            self.assertEqual(outcome.reason,'practice_'+suffix.lower())
            self.assertEqual(outcome.stage,stage)
            self.assertNotIn('PRIVATE_PLAN_NAME',str(vars(outcome)))
            wrong='practice_answer_acknowledged' if suffix.startswith('START_') else 'practice_start_test'
            negative=host.summarize_failure(raw.replace(stage,wrong),host.TESTS[3],2)
            self.assertEqual(negative.reason,'practice_diagnostic_unreviewed')
        raw='SFDT22_STEP_practice_start_test\nSFDT22_PRACTICE_DIAG_START_OPERATION_FAILED'
        unrelated=host.summarize_failure(raw,host.TESTS[0],2)
        self.assertNotEqual(unrelated.reason,'practice_start_operation_failed')
        ambiguous=host.summarize_failure(raw+'\nSFDT22_PRACTICE_DIAG_START_TEST_MISSING',host.TESTS[3],2)
        self.assertEqual(ambiguous.reason,'practice_diagnostic_ambiguous')
        unreviewed=host.summarize_failure('SFDT22_STEP_practice_start_test\nSFDT22_PRACTICE_DIAG_START_USER_ID',host.TESTS[3],2)
        self.assertEqual(unreviewed.reason,'practice_diagnostic_unreviewed')

    def test_full_acceptance_and_exact_source_pins(self):
        wf=self.read(WF);ui=self.read(UI)
        for path in (UI,PROD,HOST,SELF):
            pin=hashlib.sha256((ROOT/path).read_bytes()).hexdigest()+'  '+path
            self.assertEqual(wf.count(pin),1,path)
        self.assertIn('python3 -B '+SELF,wf)
        self.assertEqual(ui.count('@Test fun '),5)
        self.assertEqual(ui.count('screenshot("'),7)
        for token in ('api-level: 36', 'needs: [source, emulator]',
                      'seven unique screenshots', ':app:assembleDebugAndroidTest',
                      'Independently verify five test receipts'):
            self.assertIn(token,wf)
        for forbidden in ('continue-on-error:', 'if: false', 'pull_request_target:',
                          'contents: write'):
            self.assertNotIn(forbidden,wf)

if __name__ == '__main__': unittest.main(verbosity=2)
