#!/usr/bin/env python3
"""HF28: practice/review real-UI journey and fail-closed source/runtime contracts."""
from __future__ import annotations
import hashlib, pathlib, sys, unittest
ROOT=pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'scripts'))
import sfdt22_emulator_verify as host
UI='app/src/androidTest/java/com/mikeyphw/studyflow/Sfdt22HostedStudyJourneyTest.kt'
RUNNER='scripts/sfdt22_emulator_verify.py'
DOC='docs/actions/SFDT22_HF28_PRACTICE_REVIEW_FULL_GATE.md'
THIS='scripts/test_sfdt22_hf28_practice_contract.py'
FULL='.github/workflows/studyflow-sfdt22-hosted-journeys.yml'
STAGES=(
 'practice_open_root','practice_open_questions','practice_enter_question_bank',
 'practice_open_question_editor','practice_choose_true_false','practice_fill_statement',
 'practice_save_question','practice_question_visible','practice_create_test',
 'practice_test_ready','practice_start_test','practice_runner_question',
 'practice_answer_true','practice_answer_acknowledged','practice_finish_test',
 'practice_test_finished','practice_capture_question','practice_back_to_workspace',
 'practice_open_reviews','practice_review_center','practice_card_front',
 'practice_card_back','practice_save_card','practice_card_visible','practice_capture_review')

class PracticeHF28(unittest.TestCase):
    def get(self,p):return (ROOT/p).read_text()
    def body(self):
        ui=self.get(UI)
        return ui.split('    @Test fun realUserAnswersQuestionAndOpensReviewQueue() {',1)[1].split('    @Test fun realUserSeesPersistedProgressAfterColdRestart()',1)[0]
    def test_real_production_controls_and_all_steps_guarded(self):
        src=self.body()
        self.assertEqual(len(STAGES),25)
        self.assertEqual(src.count('stage("'),len(STAGES))
        self.assertEqual([s for s in STAGES if s in host.STAGES],list(STAGES))
        for stage in STAGES:
            self.assertEqual(src.count('stage("'+stage+'")'),1,stage)
        for real in ('openRoot("practice")','openInnerTabReady(',
                     'click(WorkspaceR.string.open_question_bank)',
                     'onNodeWithContentDescription(text(PracticeR.string.add_question))',
                     'clickLabel("true false")','fill(PracticeR.string.statement, questionStatement)',
                     'click(PracticeR.string.save_question)','visible(questionStatement)',
                     'click(PracticeR.string.create_practice_test)',
                     'visible(text(PracticeR.string.start_or_resume))',
                     'click(PracticeR.string.start_or_resume)',
                     'clickLabel("True")','visible(text(PracticeR.string.answer_saved))',
                     'click(PracticeR.string.finish_attempt)',
                     'visible(text(PracticeR.string.attempt_finished))',
                     'clickReviewCenterEntry()',
                     'fill(ReviewsR.string.flashcard_front, flashcardFront)',
                     'fill(ReviewsR.string.flashcard_back, flashcardBack)',
                     'click(ReviewsR.string.create_flashcard)',
                     'visible(flashcardFront)'):
            self.assertIn(real,src)
        self.assertLess(src.index('practice_create_test'),src.index('practice_test_ready'))
        self.assertLess(src.index('practice_test_ready'),src.index('practice_start_test'))
        for forbidden in ('assumeTrue(', 'setContent(', 'Thread.sleep(', 'mock', 'fallbackToDestructiveMigration', 'runCatching { click('):
            self.assertNotIn(forbidden,src)
    def test_nonqualifying_focused_and_strict_full_gates(self):
        ui=self.get(UI);full=self.get(FULL)
        self.assertEqual(ui.count('@Test fun '),5)
        self.assertEqual(ui.count('screenshot("'),7)
        self.assertEqual(len(host.TESTS),5)
        self.assertEqual(len(host.SCREENS),7)
        self.assertIn('needs: [source, emulator]',full)
        self.assertIn('api-level: 36',full)
        self.assertIn('seven unique screenshots',full)
        self.assertIn('Independently verify five test receipts',full)
        self.assertNotIn('continue-on-error:',full)
        self.assertNotIn('if: false',full)
        for p in (UI,RUNNER,DOC,THIS):
            h=hashlib.sha256((ROOT/p).read_bytes()).hexdigest()
            self.assertIn(h+'  '+p,full,p)
        self.assertIn('python3 -B '+THIS,full)
    def test_25_safe_and_specific_host_reason_categories(self):
        case=host.TESTS[3]
        for stage in STAGES:
            receipt=host.summarize_failure('java.lang.AssertionError: SFDT22_STEP_'+stage+'\n'+
                                           'Caused by: ComposeTimeoutException',case,0)
            self.assertEqual(receipt.stage,stage)
            self.assertEqual(receipt.reason,'compose_wait_timeout')
            self.assertNotEqual(receipt.reason,'passed')
        unknown=host.summarize_failure('SFDT22_STEP_practice_raw_private_identity\n'+
                                       'ComposeTimeoutException',case,0)
        self.assertEqual(unknown.stage,'unavailable')
        self.assertEqual(unknown.reason,'compose_wait_timeout')
        self.assertIn('raw_logs_retained=False',self.get(RUNNER))
        self.assertNotIn('print(raw)',self.get(RUNNER))
    def test_negative_missing_stage_and_screenshot_fail(self):
        body=self.body()
        for stage in ('practice_create_test','practice_test_ready','practice_save_card'):
            self.assertEqual(body.count('stage("'+stage+'")'),1)
            removed=body.replace('stage("'+stage+'")','stage("UNTRUSTED")')
            self.assertNotEqual([s for s in STAGES if 'stage("'+s+'")' in removed],list(STAGES))
        self.assertNotEqual(body.replace('screenshot("review")','').count('screenshot("'),2)

if __name__=='__main__': unittest.main(verbosity=2)
