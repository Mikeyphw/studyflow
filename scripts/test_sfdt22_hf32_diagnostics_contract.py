#!/usr/bin/env python3
"""HF32 read-only practice-answer diagnostics / fail-closed hosted receipt contracts."""
from __future__ import annotations
import hashlib
from pathlib import Path
import re
import sys
import unittest
ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT/'scripts'))
import sfdt22_emulator_verify as host
UI = 'app/src/androidTest/java/com/mikeyphw/studyflow/Sfdt22HostedStudyJourneyTest.kt'
HOST = 'scripts/sfdt22_emulator_verify.py'
SELF = 'scripts/test_sfdt22_hf32_diagnostics_contract.py'
WF = '.github/workflows/studyflow-sfdt22-hosted-journeys.yml'

class HF32PracticeDiagnostics(unittest.TestCase):
    def read(self, path: str) -> str:
        return (ROOT/path).read_text(encoding='utf-8')

    def test_real_answer_write_and_strict_room_proof_retained(self):
        src=self.read(UI)
        self.assertEqual(src.count('@Test fun '),5)
        self.assertEqual(src.count('screenshot("'),7)
        journey=src.split('    @Test fun realUserAnswersQuestionAndOpensReviewQueue() {',1)[1].split('    @Test fun realUserSeesPersistedProgressAfterColdRestart()',1)[0]
        for token in ('stage("practice_answer_true") { clickLabel("True") }',
                      'stage("practice_answer_acknowledged")', 'awaitRealPracticeAnswerPersisted()',
                      'assertOptionalPracticeAckVisible()', 'click(PracticeR.string.finish_attempt)'):
            self.assertIn(token,journey)
        self.assertLess(journey.index('clickLabel("True")'),journey.index('awaitRealPracticeAnswerPersisted()'))
        self.assertLess(journey.index('awaitRealPracticeAnswerPersisted()'),journey.index('assertOptionalPracticeAckVisible()'))
        self.assertLess(journey.index('assertOptionalPracticeAckVisible()'),journey.index('click(PracticeR.string.finish_attempt)'))
        self.assertIn('persistedPracticeAnswerCount() == 1L',src)
        self.assertIn('SQLiteDatabase.OPEN_READONLY',src)
        self.assertNotIn('assumeTrue(',src)
        self.assertNotIn('Thread.sleep(',src)
        self.assertNotIn('runCatching { click(',journey)

    def test_bounded_private_sql_categories_not_user_data(self):
        ui=self.read(UI)
        block=ui.split('    private fun diagnosePracticeAnswerPersistence(): String {',1)[1].split(
            '    private fun awaitRealPracticeAnswerPersisted()',1)[0]
        for needed in ('SQLiteDatabase.OPEN_READONLY','sql.rawQuery(',
                       'arrayOf(planName, "$planName practice")',
                       'rows.moveToNext()', 'configurationJson',
                       '"ANSWER_RECORD_MISSING"','"ANSWER_COUNTER_ZERO"',
                       '"ANSWER_COUNTER_MISMATCH"','"LOOKUP_FAILED"',
                       '"TEST_MISSING"','"TEST_AMBIGUOUS"'):
            self.assertIn(needed,block)
        for forbidden in ('INSERT INTO','UPDATE study_tests','DELETE FROM',
                          'println(', 'Log.', 'printStackTrace(', 'openFileOutput('):
            self.assertNotIn(forbidden,block)

    def test_categories_are_allowlisted_and_are_error_not_success(self):
        for suffix in ('ANSWER_RECORD_MISSING','ANSWER_COUNTER_ZERO',
                       'ANSWER_COUNTER_MISMATCH','LOOKUP_FAILED','OPERATION_FAILED',
                       'TEST_MISSING','TEST_AMBIGUOUS'):
            raw=('java.lang.AssertionError: SFDT22_STEP_practice_answer_acknowledged\n'
                 'Caused by: java.lang.AssertionError: SFDT22_PRACTICE_DIAG_'+suffix+'\n'
                 'PRIVATE_PLAN_NAME secret-123')
            receipt=host.summarize_failure(raw,host.TESTS[3],2)
            self.assertEqual(receipt.reason,'practice_'+suffix.lower())
            self.assertEqual(receipt.stage,'practice_answer_acknowledged')
            self.assertNotIn('PRIVATE_PLAN',str(vars(receipt)))
            self.assertIn(receipt.reason,host.REASON_CODES)

    def test_unknown_duplicate_and_wrong_journey_are_refused(self):
        stage='java.lang.AssertionError: SFDT22_STEP_practice_answer_acknowledged\n'
        unknown=host.summarize_failure(stage+'SFDT22_PRACTICE_DIAG_SENSITIVE_USERNAME',host.TESTS[3],2)
        self.assertEqual(unknown.reason,'practice_diagnostic_unreviewed')
        mixed=host.summarize_failure(stage+'SFDT22_PRACTICE_DIAG_TEST_MISSING\n'+
            'SFDT22_PRACTICE_DIAG_LOOKUP_FAILED',host.TESTS[3],2)
        self.assertEqual(mixed.reason,'practice_diagnostic_ambiguous')
        unrelated=host.summarize_failure(stage+'SFDT22_PRACTICE_DIAG_TEST_MISSING',host.TESTS[0],2)
        self.assertNotEqual(unrelated.reason,'practice_test_missing')

    def test_existing_full_gate_and_source_sha_pins(self):
        wf=self.read(WF)
        for path in (UI,HOST,SELF):
            pin=hashlib.sha256((ROOT/path).read_bytes()).hexdigest()+'  '+path
            self.assertEqual(wf.count(pin),1,path)
        for phrase in ('api-level: 36', 'needs: [source, emulator]',
                       'seven unique screenshots', ':app:assembleDebugAndroidTest',
                       'python3 -B '+SELF):
            self.assertIn(phrase,wf)
        for forbidden in ('continue-on-error:','if: false','pull_request_target:',
                          'contents: write'):
            self.assertNotIn(forbidden,wf)

if __name__=='__main__':
    unittest.main(verbosity=2)
