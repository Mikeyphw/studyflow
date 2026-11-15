#!/usr/bin/env python3
"""HF30 HF04: preserve all HF28 practice UI actions and order, plus three safety guards.

Source regression only, never release or Android-runtime qualification.
"""
from __future__ import annotations
import hashlib
import pathlib
import re
import sys
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT/'scripts'))
import sfdt22_emulator_verify as host

UI='app/src/androidTest/java/com/mikeyphw/studyflow/Sfdt22HostedStudyJourneyTest.kt'
OLD='scripts/test_sfdt22_hf28_practice_contract.py'
FULL='.github/workflows/studyflow-sfdt22-hosted-journeys.yml'
FOCUSED='.github/workflows/studyflow-sfdt22-hotfix-session.yml'
RUNNER='scripts/sfdt22_emulator_verify.py'
THIS='scripts/test_sfdt22_hf30_hf04_contract.py'
DOC='docs/actions/SFDT22_HF30_HF04_PRACTICE_STAGE_CONTRACT.md'

ORIGINAL = (
 'practice_open_root','practice_open_questions','practice_enter_question_bank',
 'practice_open_question_editor','practice_choose_true_false','practice_fill_statement',
 'practice_save_question','practice_question_visible','practice_create_test',
 'practice_test_ready','practice_start_test','practice_runner_question',
 'practice_answer_true','practice_answer_acknowledged','practice_finish_test',
 'practice_test_finished','practice_capture_question','practice_back_to_workspace',
 'practice_open_reviews','practice_review_center','practice_card_front',
 'practice_card_back','practice_save_card','practice_card_visible','practice_capture_review',
)
EXPECTED = ('practice_session_closed','practice_shell_ready',ORIGINAL[0],
            'practice_tab_selected',*ORIGINAL[1:])

class HF04PracticeStageMigration(unittest.TestCase):
    def read(self,p):return (ROOT/p).read_text()
    def body(self):
        src=self.read(UI)
        return src.split('    @Test fun realUserAnswersQuestionAndOpensReviewQueue() {',1)[1].split(
            '    @Test fun realUserSeesPersistedProgressAfterColdRestart()',1)[0]
    def test_01_25_historical_plus_3_new_in_exact_real_order(self):
        body=self.body()
        self.assertEqual(len(ORIGINAL),25)
        self.assertEqual(len(EXPECTED),28)
        observed=re.findall(r'stage\("([a-z][a-z0-9_]*)"\)',body)
        self.assertEqual(observed,list(EXPECTED))
        self.assertEqual(len(set(observed)),28)
        self.assertTrue(all(stage in host.STAGES for stage in EXPECTED))
        self.assertIn('stage("practice_session_closed") { assertSessionClosedInDatabase() }',body)
        self.assertIn('stage("practice_shell_ready") { shell() }',body)
        self.assertIn('stage("practice_open_root") { openRoot("practice") }',body)
        self.assertIn('stage("practice_tab_selected") { tab("practice").assertIsDisplayed().assertIsSelected() }',body)
    def test_02_original_25_step_contract_stronger_not_removed(self):
        historical=self.read(OLD)
        self.assertIn('self.assertEqual(len(STAGES),25)',historical)
        self.assertIn('self.assertEqual(observed, list(expected))',historical)
        self.assertIn('self.assertEqual(len(expected), 28)',historical)
        self.assertIn('self.assertTrue(all(name in host.STAGES for name in expected))',historical)
        self.assertIn("'practice_open_root', 'practice_tab_selected', *STAGES[1:]",historical)
        self.assertIn('self.assertIn(real,src)',historical)
        self.assertIn('self.assertNotIn(forbidden,src)',historical)
        self.assertIn("self.assertEqual(ui.count('@Test fun '),5)",historical)
        self.assertIn('screenshot(',historical)
        self.assertNotIn('self.assertEqual(src.count(\'stage("\'),len(STAGES))',historical)
    def test_03_no_screenshot_or_release_bypass(self):
        ui=self.read(UI);full=self.read(FULL);focus=self.read(FOCUSED)
        self.assertEqual(ui.count('@Test fun '),5)
        self.assertEqual(ui.count('screenshot("'),7)
        self.assertEqual(full.count('needs: [source, emulator]'),1)
        self.assertIn('Independently verify five test receipts',full)
        self.assertIn('seven unique screenshots',full)
        self.assertIn('force_stopped_app_and_verified_new_pid=True',self.read(RUNNER))
        self.assertEqual(focus.count('--diagnostic-through practice'),2)
        self.assertIn('NOT full qualification',focus)
        for gate in (full,focus):
            self.assertNotIn('continue-on-error:',gate)
    def test_04_full_hosted_pin_and_execution_immutable(self):
        full=self.read(FULL)
        for p in (UI,OLD,THIS,DOC):
            digest=hashlib.sha256((ROOT/p).read_bytes()).hexdigest()
            self.assertIn(digest+'  '+p,full,p)
        for p in (OLD,THIS):
            self.assertEqual(full.count('python3 -B '+p),1,p)
        self.assertIn('scripts/sfdt22_emulator_verify.py --verify ',full)
        self.assertIn('test "$SOURCE_RESULT" = success',full)
        self.assertIn('test "$EMULATOR_RESULT" = success',full)
    def test_05_mutation_rejections_are_order_sensitive(self):
        observed=list(EXPECTED)
        for mutated in (
            observed[1:],
            observed[:3]+observed[4:],
            observed[:4]+['practice_open_root']+observed[4:],
            observed[:9]+observed[10:11]+observed[9:10]+observed[11:],
        ):
            self.assertNotEqual(mutated,list(EXPECTED))

if __name__=='__main__':unittest.main(verbosity=2)
