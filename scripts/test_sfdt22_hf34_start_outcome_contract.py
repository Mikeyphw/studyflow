#!/usr/bin/env python3
"""HF34: production start-action outcome diagnostics with immutable real Android gates.

Offline source-only negative tests; never a substitute for hosted API36 acceptance.
"""
from __future__ import annotations
import hashlib
import pathlib
import sys
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
import sfdt22_emulator_verify as host

UI = 'app/src/androidTest/java/com/mikeyphw/studyflow/Sfdt22HostedStudyJourneyTest.kt'
PROD = 'feature/practice/src/main/java/com/mikeyphw/studyflow/feature/practice/PracticeScreen.kt'
HOST = 'scripts/sfdt22_emulator_verify.py'
SELF = 'scripts/test_sfdt22_hf34_start_outcome_contract.py'
WF = '.github/workflows/studyflow-sfdt22-hosted-journeys.yml'
STATES = ('idle', 'requested', 'committed', 'result_uncommitted',
          'rejected_argument', 'rejected_state', 'rejected_sqlite', 'rejected_other')
CODES = ('START_ACTION_NOT_DELIVERED', 'START_ACTION_STALLED',
         'START_COMMITTED_BUT_ROW_MISSING', 'START_RESULT_UNCOMMITTED',
         'START_REJECTED_ARGUMENT', 'START_REJECTED_STATE',
         'START_REJECTED_SQLITE', 'START_REJECTED_OTHER',
         'START_OUTCOME_AMBIGUOUS')

class HF34ProductionStartReceipt(unittest.TestCase):
    def read(self, path):
        return (ROOT / path).read_text(encoding='utf-8')

    def test_01_real_production_start_result_precedes_runner(self):
        p = self.read(PROD)
        callback = p.split('                    onStart = { test ->', 1)[1].split('                )\n            }', 1)[0]
        for required in ('startOutcome = "requested"', 'runRecoverableOperation {',
                         'practice.startPracticeTest(plan.id, test.id)',
                         'started.startedAtEpochMillis == null',
                         'startOutcome = "result_uncommitted"',
                         'startOutcome = "committed"',
                         'activeTestId = started.id.value',
                         'startOutcome = when (failure)',
                         'snackbar.showSnackbar(operationFailed)'):
            self.assertIn(required, callback)
        # The Kotlin when expression assigns the result of each branch, rather
        # than repeating `startOutcome =` for every case. Match the real syntax.
        for exception, status in (
            ('IllegalArgumentException', 'rejected_argument'),
            ('IllegalStateException', 'rejected_state'),
            ('android.database.sqlite.SQLiteException', 'rejected_sqlite'),
        ):
            self.assertIn('is ' + exception + ' -> "' + status + '"', callback)
        self.assertIn('else -> "rejected_other"', callback)
        self.assertLess(callback.index('startOutcome = "requested"'), callback.index('scope.launch'))
        self.assertLess(callback.index('started.startedAtEpochMillis == null'),
                        callback.index('activeTestId = started.id.value'))
        self.assertLess(callback.index('startOutcome = "committed"'),
                        callback.index('activeTestId = started.id.value'))
        self.assertIn('modifier = modifier.testTag("studyflow.practice.start.$startOutcome")', p)
        self.assertIn('var startOutcome by remember { mutableStateOf("idle") }', p)
        self.assertIn('modifier = Modifier.testTag("studyflow.practice.test.start")',p)
        self.assertNotIn('startOutcome = test.id.value', callback)

    def test_02_readonly_room_and_android_runner_guard_preserved(self):
        src=self.read(UI)
        self.assertIn('private fun persistedPracticeAttemptStartedCount(): Long?',src)
        self.assertIn('tests.startedAtEpochMillis IS NOT NULL',src)
        self.assertIn('SQLiteDatabase.OPEN_READONLY',src)
        self.assertIn('persistedPracticeAttemptStartedCount() == 1L',src)
        block=src.split('    private fun startOutcomeCategory(): String? {',1)[1].split(
            '    private fun awaitRealPracticeAttemptStarted() {',1)[0]
        for state in STATES:
            self.assertIn('"'+state+'" to ',block)
        self.assertIn('useUnmergedTree = true',block)
        self.assertIn('fetchSemanticsNodes().isNotEmpty()',block)
        for forbidden in ('execSQL(', 'INSERT INTO','UPDATE study_tests', 'DELETE FROM',
                          'printStackTrace(', 'openFileOutput(', 'println('):
            self.assertNotIn(forbidden,block)
        self.assertIn('startOutcomeCategory() ?: diagnosePracticeAttemptStart()',src)
        self.assertEqual(src.count('@Test fun '),5)
        self.assertEqual(src.count('screenshot("'),7)
        journey=src.split('    @Test fun realUserAnswersQuestionAndOpensReviewQueue() {',1)[1].split(
            '    @Test fun realUserSeesPersistedProgressAfterColdRestart()',1)[0]
        seq=('click(PracticeR.string.start_or_resume)', 'awaitRealPracticeAttemptStarted()',
             'awaitRunnerQuestionReady()', 'clickLabel("True")',
             'awaitRealPracticeAnswerPersisted()', 'click(PracticeR.string.finish_attempt)')
        self.assertEqual([journey.index(s) for s in seq],sorted(journey.index(s) for s in seq))

    def test_03_stage_scoped_bounded_allowlist_and_no_private_values(self):
        for suffix in CODES:
            self.assertIn(suffix,host.PRACTICE_DIAG_CODES)
            raw='java.lang.AssertionError: SFDT22_STEP_practice_start_test\n' \
                'Caused by: SFDT22_PRACTICE_DIAG_'+suffix+'\nSECRET_PLAN_1234'
            outcome=host.summarize_failure(raw,host.TESTS[3],2)
            self.assertEqual(outcome.reason,'practice_'+suffix.lower())
            self.assertEqual(outcome.stage,'practice_start_test')
            self.assertNotIn('SECRET_PLAN_1234',str(vars(outcome)))
            bad=host.summarize_failure(raw.replace('practice_start_test','practice_answer_acknowledged'),host.TESTS[3],2)
            self.assertEqual(bad.reason,'practice_diagnostic_unreviewed')
        mixed=host.summarize_failure('SFDT22_STEP_practice_start_test\n'+
          'SFDT22_PRACTICE_DIAG_START_ACTION_STALLED\n'+
          'SFDT22_PRACTICE_DIAG_START_REJECTED_OTHER',host.TESTS[3],2)
        self.assertEqual(mixed.reason,'practice_diagnostic_ambiguous')
        unrelated=host.summarize_failure('SFDT22_STEP_practice_start_test\n'+
          'SFDT22_PRACTICE_DIAG_START_REJECTED_OTHER',host.TESTS[0],2)
        self.assertNotEqual(unrelated.reason,'practice_start_rejected_other')

    def test_04_immutable_workflow_sha_pins_and_strict_qualification(self):
        wf=self.read(WF)
        for path in (UI,PROD,HOST,SELF):
            digest=hashlib.sha256((ROOT/path).read_bytes()).hexdigest()
            self.assertEqual(wf.count(digest+'  '+path),1,path)
        self.assertIn('python3 -B '+SELF,wf)
        for phrase in ('api-level: 36', 'needs: [source, emulator]',
                       'seven unique screenshots', ':app:assembleDebugAndroidTest',
                       'Independently verify five test receipts'):
            self.assertIn(phrase,wf)
        for forbidden in ('continue-on-error:', 'if: false', 'pull_request_target:',
                          'contents: write'):
            self.assertNotIn(forbidden,wf)

    def test_05_mutation_negatives_refuse_removed_proofs(self):
        prod=self.read(PROD)
        ui=self.read(UI)
        callback=prod.split('                    onStart = { test ->',1)[1].split(
            '                )\n            }',1)[0]
        def retains_production_proof(source):
            return all(source.count(token)==1 for token in (
                'startOutcome = "requested"', 'startOutcome = "committed"',
                'started.startedAtEpochMillis == null',
                'activeTestId = started.id.value',
                'startOutcome = when (failure)',
            ))
        self.assertTrue(retains_production_proof(callback))
        for token in ('startOutcome = "requested"', 'startOutcome = "committed"',
                      'started.startedAtEpochMillis == null',
                      'activeTestId = started.id.value', 'startOutcome = when (failure)'):
            self.assertFalse(retains_production_proof(callback.replace(token,'',1)),token)
        # The helper's function declaration and the actual call share names.
        # Reject removal of the invocation *within the real journey*, not one
        # arbitrary occurrence across the whole 1600-line test source.
        journey=ui.split('    @Test fun realUserAnswersQuestionAndOpensReviewQueue() {',1)[1].split(
            '    @Test fun realUserSeesPersistedProgressAfterColdRestart()',1)[0]
        sequence=('click(PracticeR.string.start_or_resume)',
                  'awaitRealPracticeAttemptStarted()', 'awaitRunnerQuestionReady()',
                  'clickLabel("True")','awaitRealPracticeAnswerPersisted()',
                  'click(PracticeR.string.finish_attempt)')
        def qualifies_journey(source):
            if any(source.count(token)!=1 for token in sequence):
                return False
            positions=[source.index(token) for token in sequence]
            return positions==sorted(positions)
        self.assertTrue(qualifies_journey(journey))
        for token in sequence:
            self.assertFalse(qualifies_journey(journey.replace(token,'',1)),token)
        # The independent DB witness lives inside the start helper, outside
        # the journey. It must remain exact, read-only, and fail closed.
        start=ui.split('    private fun awaitRealPracticeAttemptStarted() {',1)[1].split(
            '    /** Ignore identically named question-bank text',1)[0]
        proof='persistedPracticeAttemptStartedCount() == 1L'
        self.assertEqual(start.count(proof),1)
        self.assertNotIn(proof,start.replace(proof,'',1))

if __name__=='__main__':
    unittest.main(verbosity=2)
