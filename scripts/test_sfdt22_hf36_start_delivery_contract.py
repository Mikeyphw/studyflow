#!/usr/bin/env python3
"""HF36: strict real UI Start tap, snackbar obstruction & safe retry contracts.

Tests source only; no fake Android qualification, device writes or mocks.
"""
from __future__ import annotations
import hashlib
from pathlib import Path
import sys
import unittest
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'scripts'))
import sfdt22_emulator_verify as host
UI='app/src/androidTest/java/com/mikeyphw/studyflow/Sfdt22HostedStudyJourneyTest.kt'
HOST='scripts/sfdt22_emulator_verify.py'
WF='.github/workflows/studyflow-sfdt22-hosted-journeys.yml'
SELF='scripts/test_sfdt22_hf36_start_delivery_contract.py'
CODES=('START_SNACKBAR_OBSTRUCTION','START_RETRY_UNSAFE_ROW','START_RETRY_NOT_DELIVERED')

class HF36StartDelivery(unittest.TestCase):
    def read(self,path):return (ROOT/path).read_text(encoding='utf-8')
    def journey(self):
        return self.read(UI).split('    @Test fun realUserAnswersQuestionAndOpensReviewQueue() {',1)[1].split(
            '    @Test fun realUserSeesPersistedProgressAfterColdRestart()',1)[0]

    def test_01_one_strict_retry_behind_real_production_ui(self):
        src=self.read(UI)
        block=src.split('    private fun retryOnlyUndeliveredPracticeStart() {',1)[1].split(
            '    /** Baseline before the real UI tap;',1)[0]
        for token in ('startReceiptBaseline < 0L','expectedPracticeTestFingerprint() == null',
                      'prefs.getLong("sequence", 0L) > startReceiptBaseline',
                      'persistedPracticeAttemptStartedCount() == 1L',
                      'sequence > startReceiptBaseline) return',
                      '1L -> return', '0L -> Unit',
                      'startOutcomeCategory() != "START_ACTION_NOT_DELIVERED"',
                      'awaitPracticeCreateSnackbarGone()',
                      'startRetryPerformed = true',
                      'click(PracticeR.string.start_or_resume)'):
            self.assertIn(token,block,token)
        self.assertEqual(block.count('click(PracticeR.string.start_or_resume)'),1)
        self.assertLess(block.index('sequence > startReceiptBaseline) return'),
                        block.index('startRetryPerformed = true'))
        self.assertLess(block.index('startOutcomeCategory() != "START_ACTION_NOT_DELIVERED"'),
                        block.index('startRetryPerformed = true'))
        self.assertLess(block.index('startRetryPerformed = true'),block.index('click(PracticeR.string.start_or_resume)'))
        for forbidden in ('startPracticeTest(', 'startTestIfNotStarted(', 'runBlocking {', 'Thread.sleep(',
                          'execSQL(', 'INSERT INTO','UPDATE study_tests','DELETE FROM','setContent(',
                          'assumeTrue(', '.edit()', 'putString(', 'putLong('):
            self.assertNotIn(forbidden,block)

    def test_02_wait_for_transient_snackbar_to_clear_before_first_touch(self):
        src=self.read(UI)
        helper=src.split('    private fun awaitPracticeCreateSnackbarGone() {',1)[1].split(
            '    private fun retryOnlyUndeliveredPracticeStart() {',1)[0]
        for part in ('PracticeR.string.practice_test_created', 'rule.waitUntil(12_000L)',
                     'useUnmergedTree = true', 'fetchSemanticsNodes().isEmpty()',
                     'SFDT22_PRACTICE_DIAG_START_SNACKBAR_OBSTRUCTION'):
            self.assertIn(part,helper)
        j=self.journey()
        seq=('captureStartReceiptBaseline()', 'awaitPracticeCreateSnackbarGone()',
             'click(PracticeR.string.start_or_resume)','retryOnlyUndeliveredPracticeStart()',
             'awaitRealPracticeAttemptStarted()','awaitRunnerQuestionReady()',
             'clickLabel("True")','awaitRealPracticeAnswerPersisted()',
             'click(PracticeR.string.finish_attempt)')
        self.assertTrue(all(j.count(x)==1 for x in seq),seq)
        positions=[j.index(x) for x in seq]
        self.assertEqual(positions,sorted(positions))

    def test_03_durable_receipt_and_room_proofs_unchanged(self):
        src=self.read(UI)
        self.assertIn('private var startReceiptBaseline = -1L',src)
        self.assertIn('private var startRetryPerformed = false',src)
        self.assertIn('private fun durableStartReceiptCategory(): String?',src)
        self.assertIn('if (expectedPracticeTestFingerprint() == null)',src)
        self.assertIn('persistedPracticeAttemptStartedCount() == 1L',src)
        self.assertIn('persistedPracticeAnswerCount() == 1L',src)
        self.assertIn('SQLiteDatabase.OPEN_READONLY',src)
        self.assertIn('if (observed.single().first == "idle" && startRetryPerformed)',src)
        self.assertEqual(src.count('@Test fun '),5)
        self.assertEqual(src.count('screenshot("'),7)
        self.assertNotIn('assumeTrue(',src)
        self.assertNotIn('Thread.sleep(',src)

    def test_04_real_failure_categories_are_allowlisted_not_private(self):
        for code in CODES:
            self.assertIn(code,host.PRACTICE_DIAG_CODES)
            raw='SFDT22_STEP_practice_start_test\nSFDT22_PRACTICE_DIAG_'+code+' SECRET_ANSWER'
            e=host.summarize_failure(raw,host.TESTS[3],2)
            self.assertEqual(e.reason,'practice_'+code.lower())
            self.assertEqual(e.stage,'practice_start_test')
            self.assertNotIn('SECRET_ANSWER',str(vars(e)))
            wrong=host.summarize_failure(raw.replace('practice_start_test','practice_answer_acknowledged'),host.TESTS[3],2)
            self.assertEqual(wrong.reason,'practice_diagnostic_unreviewed')
            other=host.summarize_failure(raw,host.TESTS[0],2)
            self.assertNotEqual(other.reason,'practice_'+code.lower())
        raw='SFDT22_STEP_practice_start_test\nSFDT22_PRACTICE_DIAG_START_SNACKBAR_OBSTRUCTION\nSFDT22_PRACTICE_DIAG_START_RETRY_NOT_DELIVERED'
        self.assertEqual(host.summarize_failure(raw,host.TESTS[3],2).reason,'practice_diagnostic_ambiguous')

    def test_05_fail_closed_workflow_exact_hashes(self):
        wf=self.read(WF)
        for p in (UI,HOST,SELF):
            self.assertEqual(wf.count(hashlib.sha256((ROOT/p).read_bytes()).hexdigest()+'  '+p),1,p)
        self.assertIn('python3 -B '+SELF,wf)
        for token in ('api-level: 36','needs: [source, emulator]','seven unique screenshots',
                      ':app:assembleDebugAndroidTest','Independently verify five test receipts'):
            self.assertIn(token,wf)
        for forbidden in ('continue-on-error:', 'if: false', 'pull_request_target:', 'contents: write'):
            self.assertNotIn(forbidden,wf)

    def test_06_mutation_of_callback_order_refuses_qualification(self):
        j=self.journey()
        req=('captureStartReceiptBaseline()', 'awaitPracticeCreateSnackbarGone()',
             'click(PracticeR.string.start_or_resume)','retryOnlyUndeliveredPracticeStart()',
             'awaitRealPracticeAttemptStarted()','awaitRunnerQuestionReady()')
        def qualifies(x):
            if any(x.count(t)!=1 for t in req):return False
            indexes=[x.index(t) for t in req]
            return indexes==sorted(indexes)
        self.assertTrue(qualifies(j))
        for t in req:self.assertFalse(qualifies(j.replace(t,'',1)),t)
        self.assertFalse(qualifies(j.replace('retryOnlyUndeliveredPracticeStart()',
            'retryOnlyUndeliveredPracticeStart()\n            captureStartReceiptBaseline()',1)))

if __name__=='__main__':unittest.main(verbosity=2)
