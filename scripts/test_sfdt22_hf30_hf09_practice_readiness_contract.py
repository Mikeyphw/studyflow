#!/usr/bin/env python3
"""HF09 practice test readiness source + negative-marker tests; never device proof."""
from __future__ import annotations
import hashlib
from pathlib import Path
import sys
import unittest

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'scripts'))
import sfdt22_emulator_verify as verifier
UI='app/src/androidTest/java/com/mikeyphw/studyflow/Sfdt22HostedStudyJourneyTest.kt'
HOST='scripts/sfdt22_emulator_verify.py'
WF='.github/workflows/studyflow-sfdt22-hosted-journeys.yml'
THIS='scripts/test_sfdt22_hf30_hf09_practice_readiness_contract.py'
DOC='docs/actions/SFDT22_HF30_HF09_PRACTICE_READINESS.md'

class PracticeReadiness(unittest.TestCase):
    def read(self,p):return (ROOT/p).read_text()
    def test_01_true_database_check_precedes_ui_assertion(self):
        src=self.read(UI)
        body=src.split('    @Test fun realUserAnswersQuestionAndOpensReviewQueue() {',1)[1].split('    @Test fun realUserSeesPersistedProgressAfterColdRestart()',1)[0]
        self.assertIn('stage("practice_test_ready") {\n            awaitRealPracticeTestReady()\n            visible(text(PracticeR.string.start_or_resume))',body)
        self.assertLess(body.index('stage("practice_create_test")'),body.index('stage("practice_test_ready")'))
        self.assertLess(body.index('stage("practice_test_ready")'),body.index('stage("practice_start_test")'))
        helper=src.split('    private fun persistedPracticeTestCount()',1)[1].split('    private fun click(resId',1)[0]
        for token in ('SQLiteDatabase.OPEN_READONLY', 'study_tests AS tests',
                      'JOIN study_plans AS plans', 'plans.id = tests.studyPlanId',
                      'plans.name = ?', 'tests.title = ?',
                      'tests.submittedAtEpochMillis IS NULL',
                      'arrayOf(planName, "$planName practice")',
                      'persistedPracticeTestCount() == 1L',
                      'hasText(text(PracticeR.string.start_or_resume)) and hasClickAction()',
                      'performScrollToNode(panel)', 'performScrollToNode(actionable)',
                      'assertIsDisplayed().assertIsEnabled()'):
            self.assertIn(token,helper,token)
        self.assertLess(helper.index('persistedPracticeTestCount() == 1L'),helper.index('performScrollToNode(panel)'))
        self.assertNotIn('Thread.sleep(',helper)
        self.assertNotIn('setContent(',helper)
        self.assertNotIn('mockk(',helper)
        self.assertNotIn('practice.createPracticeTest(',helper)
    def test_02_mandatory_original_actions_and_evidence_retained(self):
        src=self.read(UI)
        body=src.split('    @Test fun realUserAnswersQuestionAndOpensReviewQueue() {',1)[1].split('    @Test fun realUserSeesPersistedProgressAfterColdRestart()',1)[0]
        self.assertIn('click(PracticeR.string.create_practice_test)',body)
        self.assertIn('click(PracticeR.string.start_or_resume)',body)
        self.assertIn('click(PracticeR.string.finish_attempt)',body)
        self.assertIn('clickReviewCenterEntry()',body)
        self.assertEqual(src.count('@Test fun '),5)
        self.assertEqual(src.count('screenshot("'),7)
        self.assertEqual(body.count('stage("practice_test_ready")'),1)
        self.assertEqual(len(verifier.TESTS),5)
        self.assertEqual(len(verifier.SCREENS),7)
    def test_03_safe_distinct_failure_classification(self):
        cases={
           'SFDT22_PRACTICE_TEST_CREATE_OPERATION_FAILED':'practice_test_create_operation_failed',
           'SFDT22_PRACTICE_TEST_NOT_PERSISTED':'practice_test_not_persisted',
           'SFDT22_PRACTICE_TEST_ACTION_NOT_VISIBLE':'practice_test_action_not_visible',
        }
        for marker,reason in cases.items():
            record=verifier.summarize_failure('SFDT22_STEP_practice_test_ready '+marker+' PRIVATE_DATA',verifier.TESTS[3],2)
            self.assertEqual((record.stage,record.reason),('practice_test_ready',reason))
            self.assertIn(reason,verifier.REASON_CODES)
            self.assertNotIn('PRIVATE_DATA',str(record))
            # Other test methods must not claim this specifically scoped reason.
            unrelated=verifier.summarize_failure('SFDT22_STEP_practice_test_ready '+marker,verifier.TESTS[2],2)
            self.assertNotEqual(unrelated.reason,reason)
    def test_04_all_pins_executed_no_qualification_relaxed(self):
        f=self.read(WF)
        for p in (UI,HOST,THIS,DOC):
            self.assertEqual(f.count(hashlib.sha256((ROOT/p).read_bytes()).hexdigest()+'  '+p),1,p)
        self.assertEqual(f.count('python3 -B '+THIS),1)
        for s in ('needs: [source, emulator]','api-level: 36',
                  'seven unique screenshots','Independently verify five test receipts',
                  'scripts/sfdt22_emulator_verify.py --verify ',
                  'script: bash scripts/sfdt22_emulator_ci.sh',
                  'test "$SOURCE_RESULT" = success', 'test "$EMULATOR_RESULT" = success'):
            self.assertIn(s,f)
        self.assertNotIn('continue-on-error:',f)
        self.assertNotIn('if: false',f)
    def test_05_absent_scroll_or_database_wait_detected(self):
        src=self.read(UI)
        helper=src.split('    private fun persistedPracticeTestCount()',1)[1].split('    private fun click(resId',1)[0]
        self.assertNotEqual(helper.replace('persistedPracticeTestCount() == 1L','true'),helper)
        self.assertNotEqual(helper.replace('performScrollToNode(actionable)',''),helper)
        self.assertNotEqual(helper.replace('SQLiteDatabase.OPEN_READONLY','0'),helper)

if __name__=='__main__':unittest.main(verbosity=2)
