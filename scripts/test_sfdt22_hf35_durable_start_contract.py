#!/usr/bin/env python3
"""HF35: exact-attempt, debuggable-only durable UI start receipts and immutable real gates.

Source-only tests are diagnostic contract validation, not Android qualification.
"""
from __future__ import annotations
import hashlib
from pathlib import Path
import sys
import unittest
ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
import sfdt22_emulator_verify as host
PROD = 'feature/practice/src/main/java/com/mikeyphw/studyflow/feature/practice/PracticeScreen.kt'
UI = 'app/src/androidTest/java/com/mikeyphw/studyflow/Sfdt22HostedStudyJourneyTest.kt'
HOST = 'scripts/sfdt22_emulator_verify.py'
TEST = 'scripts/test_sfdt22_hf35_durable_start_contract.py'
WF = '.github/workflows/studyflow-sfdt22-hosted-journeys.yml'
NEW = ('START_RECEIPT_BASELINE_MISSING','START_RECEIPT_LOOKUP_FAILED',
       'START_RECEIPT_WRONG_ATTEMPT','START_RECEIPT_MALFORMED',
       'START_RECEIPT_NOT_DURABLE','START_SCREEN_RECREATED_AFTER_REQUEST',
       'START_RECEIPT_WRITE_FAILED')

class HF35DurableStart(unittest.TestCase):
    def read(self, p): return (ROOT / p).read_text(encoding='utf-8')
    def journey(self):
        return self.read(UI).split('    @Test fun realUserAnswersQuestionAndOpensReviewQueue() {',1)[1].split(
            '    @Test fun realUserSeesPersistedProgressAfterColdRestart()',1)[0]

    def test_debug_only_receipt_and_no_exported_private_values(self):
        p = self.read(PROD)
        helper = p.split('    fun writeDebugStartReceipt(testId: String, phase: String) {',1)[1].split(
            '    val activeTest =',1)[0]
        for part in ('ApplicationInfo.FLAG_DEBUGGABLE','getSharedPreferences("sfdt35-practice-start"',
                     'MessageDigest.getInstance("SHA-256")', 'putString("fingerprint"',
                     'putString("phase"','putLong("sequence"','commit()',
                     'startOutcome = "receipt_write_failed"'):
            self.assertIn(part,p)
        for part in ('testId.toByteArray(Charsets.UTF_8)', 'val preferences = debugStartReceipt ?: return',
                     'if (phase !in setOf(', 'sequence + 1L', 'runCatching {'):
            self.assertIn(part,helper)
        for forbidden in ('println(', 'Log.', 'printStackTrace(', 'openFileOutput(',
                          'putString("test_id"', 'putString("plan_name"',
                          'putString("question"', 'writeText('):
            self.assertNotIn(forbidden,helper)

    def test_callback_receipt_before_coroutine_then_exact_outcomes(self):
        p=self.read(PROD)
        cb=p.split('                    onStart = { test ->',1)[1].split('                )\n            }',1)[0]
        self.assertLess(cb.index('startOutcome = "requested"'), cb.index('writeDebugStartReceipt(test.id.value, "requested")'))
        self.assertLess(cb.index('writeDebugStartReceipt(test.id.value, "requested")'), cb.index('scope.launch'))
        for state in ('result_uncommitted','committed'):
            self.assertLess(cb.index('startOutcome = "'+state+'"'),
                cb.index('writeDebugStartReceipt(test.id.value, "'+state+'")'))
        for required in ('practice.startPracticeTest(plan.id, test.id)',
                         'started.startedAtEpochMillis == null', 'activeTestId = started.id.value',
                         'writeDebugStartReceipt(test.id.value, startOutcome)',
                         'is IllegalArgumentException', 'is IllegalStateException',
                         'is android.database.sqlite.SQLiteException'):
            self.assertIn(required,cb)

    def test_test_receipt_is_read_only_exact_row_and_pre_click_baselined(self):
        src=self.read(UI)
        block=src.split('    private fun captureStartReceiptBaseline() {',1)[1].split(
            '    private fun startOutcomeCategory(): String? {',1)[0]
        for required in ('startReceiptBaseline =', 'getLong("sequence", 0L)',
                         'SQLiteDatabase.OPEN_READONLY', 'SELECT tests.id',
                         'arrayOf(planName, "$planName practice")', 'rows.moveToNext()',
                         'MessageDigest.getInstance("SHA-256")',
                         'getString("fingerprint", null)', 'getString("phase", null)',
                         'sequence <= startReceiptBaseline', 'expectedFingerprint != fingerprint',
                         'START_RECEIPT_WRONG_ATTEMPT','START_SCREEN_RECREATED_AFTER_REQUEST'):
            self.assertIn(required,block,required)
        for forbidden in ('execSQL(', 'INSERT INTO', 'UPDATE study_tests', 'DELETE FROM',
                          '.edit()', 'putString(', 'putLong(', 'openFileOutput(', 'println('):
            self.assertNotIn(forbidden,block)
        j=self.journey()
        self.assertIn('captureStartReceiptBaseline()', j)
        seq=['captureStartReceiptBaseline()', 'click(PracticeR.string.start_or_resume)',
             'awaitRealPracticeAttemptStarted()', 'awaitRunnerQuestionReady()',
             'clickLabel("True")','awaitRealPracticeAnswerPersisted()']
        self.assertEqual([j.index(s) for s in seq],sorted(j.index(s) for s in seq))
        self.assertIn('durableStartReceiptCategory()',src)
        self.assertIn('startOutcomeCategory() ?: diagnosePracticeAttemptStart()',src)

    def test_allowlisted_host_codes_and_stage_refusals(self):
        for code in NEW:
            self.assertIn(code,host.PRACTICE_DIAG_CODES)
            raw='SFDT22_STEP_practice_start_test\nSFDT22_PRACTICE_DIAG_'+code+' SECRET_1234'
            observed=host.summarize_failure(raw,host.TESTS[3],2)
            self.assertEqual(observed.reason,'practice_'+code.lower())
            self.assertNotIn('SECRET_1234',str(vars(observed)))
            wrong=host.summarize_failure(raw.replace('practice_start_test','practice_answer_acknowledged'),host.TESTS[3],2)
            self.assertEqual(wrong.reason,'practice_diagnostic_unreviewed')
        raw='SFDT22_STEP_practice_start_test\nSFDT22_PRACTICE_DIAG_START_RECEIPT_WRONG_ATTEMPT'
        other=host.summarize_failure(raw,host.TESTS[0],2)
        self.assertNotEqual(other.reason,'practice_start_receipt_wrong_attempt')
        both=host.summarize_failure(raw+'\nSFDT22_PRACTICE_DIAG_START_RECEIPT_LOOKUP_FAILED',host.TESTS[3],2)
        self.assertEqual(both.reason,'practice_diagnostic_ambiguous')

    def test_immutable_android_and_room_acceptance(self):
        u=self.read(UI);p=self.read(PROD);w=self.read(WF)
        self.assertEqual(u.count('@Test fun '),5)
        self.assertEqual(u.count('screenshot("'),7)
        for guard in ('persistedPracticeAttemptStartedCount() == 1L',
                      'persistedPracticeAnswerCount() == 1L', 'SQLiteDatabase.OPEN_READONLY',
                      'awaitRunnerQuestionReady()', 'assertOptionalPracticeAckVisible()'):
            self.assertIn(guard,u)
        self.assertIn('modifier = Modifier.testTag("studyflow.practice.test.start")',p)
        for required in ('api-level: 36','needs: [source, emulator]',
                         'seven unique screenshots', ':app:assembleDebugAndroidTest',
                         'Independently verify five test receipts'):
            self.assertIn(required,w)
        for forbidden in ('continue-on-error:', 'if: false','pull_request_target:', 'contents: write'):
            self.assertNotIn(forbidden,w)
        for path in (PROD, UI, HOST, TEST):
            pin=hashlib.sha256((ROOT/path).read_bytes()).hexdigest()+'  '+path
            self.assertEqual(w.count(pin),1,path)
        self.assertIn('python3 -B '+TEST,w)

    def test_adversarial_source_removal_is_detected(self):
        p=self.read(PROD);u=self.read(UI)
        for s in ('writeDebugStartReceipt(test.id.value, "requested")',
                  'writeDebugStartReceipt(test.id.value, "committed")',
                  'writeDebugStartReceipt(test.id.value, "result_uncommitted")'):
            self.assertEqual(p.count(s),1)
            self.assertNotIn(s,p.replace(s,'',1))
        for s in ('captureStartReceiptBaseline()', 'durableStartReceiptCategory()',
                  'persistedPracticeAttemptStartedCount() == 1L'):
            self.assertIn(s,u)

if __name__ == '__main__': unittest.main(verbosity=2)
