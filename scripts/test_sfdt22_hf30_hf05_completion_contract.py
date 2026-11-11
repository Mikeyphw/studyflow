#!/usr/bin/env python3
"""HF30 HF05: fail-closed real session persistence + actual Compose UI evidence."""
from __future__ import annotations
import hashlib,pathlib,sys,unittest,re
ROOT=pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'scripts'))
import sfdt22_emulator_verify as verify
UI='app/src/androidTest/java/com/mikeyphw/studyflow/Sfdt22HostedStudyJourneyTest.kt'
SCREEN='feature/sessions/src/main/java/com/mikeyphw/studyflow/feature/sessions/StudySessionScreen.kt'
RUNNER='scripts/sfdt22_emulator_verify.py'
FULL='.github/workflows/studyflow-sfdt22-hosted-journeys.yml'
FOCUSED='.github/workflows/studyflow-sfdt22-hotfix-session.yml'
PUSHWF='.github/workflows/studyflow-sfdt26-hotfix-push.yml'
TEST='scripts/test_sfdt22_hf30_hf05_completion_contract.py'
DOC='docs/actions/SFDT22_HF30_HF05_SESSION_COMPLETION_TRIAGE.md'

class CompletionHF05(unittest.TestCase):
    def read(self,path):return (ROOT/path).read_text()
    def test_real_session_closes_before_compose_proof(self):
        src=self.read(UI)
        self.assertEqual(src.count('@Test fun '),5)
        self.assertEqual(src.count('screenshot("'),7)
        block=src.split('    @Test fun realUserStartsPausesResumesAndCompletesASession() {',1)[1].split('    @Test fun realUserAnswersQuestionAndOpensReviewQueue()',1)[0]
        self.assertLess(block.index('stage("session_finish_confirm")'),block.index('stage("session_completion_persisted")'))
        self.assertLess(block.index('stage("session_completion_persisted")'),block.index('stage("session_completion_ui")'))
        self.assertLess(block.index('stage("session_completion_ui")'),block.index('stage("session_finished")'))
        self.assertIn('rule.waitUntil(35_000L)',block)
        self.assertIn('assertSessionClosedInDatabase()',block)
        self.assertIn('SFDT22_SESSION_FINISH_OPERATION_FAILED',block)
        self.assertIn('SFDT22_SESSION_UI_NOT_SETTLED',block)
        self.assertIn('hasTestTag("studyflow.session.active.empty")',block)
        self.assertIn('runCatching { empty.onFirst().assertIsDisplayed() }',block)
        self.assertIn('hasText(text(SessionsR.string.operation_failed))',block)
        self.assertIn('controlTags.all { tag ->',block)
        self.assertIn('stage("session_return_to_workspace")',block)
        self.assertIn('stage("session_workspace_ready")',block)
        self.assertNotIn('Thread.sleep(',block)
        self.assertNotIn('assumeTrue(',block)
        self.assertNotIn('setContent(',block)
        self.assertNotIn('sessionManager.complete(',block)
    def test_actual_production_empty_card_is_tagged(self):
        screen=self.read(SCREEN)
        self.assertEqual(screen.count('Modifier.testTag("studyflow.session.active.empty")'),1)
        assert screen.index('if (session == null) {') < screen.index('Modifier.testTag("studyflow.session.active.empty")')
        self.assertIn('text = stringResource(R.string.no_active_session)',screen)
        self.assertIn('sessionManager.complete(',screen)
    def test_safe_specific_reason_codes_and_private_receipt(self):
        for marker,code in [('SESSION_FINISH_OPERATION_FAILED','session_finish_operation_failed'),
                            ('SESSION_UI_NOT_SETTLED','session_ui_not_settled')]:
            outcome=verify.summarize_failure('SFDT22_STEP_session_completion_ui SFDT22_'+marker+' USERSECRET',verify.TESTS[2],2)
            self.assertEqual(outcome.reason,code)
            self.assertEqual(outcome.stage,'session_completion_ui')
            self.assertNotIn('USERSECRET',str(outcome))
            self.assertIn(code,verify.REASON_CODES)
        self.assertIn('raw_logs_retained=False',self.read(RUNNER))
        self.assertIn('publication_allowed=False',self.read(RUNNER))
        self.assertNotIn('print(raw)',self.read(RUNNER))
    def test_full_gate_source_pins_and_nonqualifying_gate(self):
        f=self.read(FULL);h=self.read(FOCUSED);p=self.read(PUSHWF)
        for path in (UI,SCREEN,RUNNER,FOCUSED,PUSHWF,TEST,DOC):
            token=hashlib.sha256((ROOT/path).read_bytes()).hexdigest()+'  '+path
            self.assertIn(token,f,path)
            if path==FOCUSED:self.assertEqual(f.count(token),2)
        self.assertIn('python3 -B '+TEST,f)
        self.assertIn('python3 -B '+TEST,h)
        self.assertIn('needs: [source, emulator]',f)
        self.assertIn('seven unique screenshots',f)
        self.assertIn('Independently verify five test receipts',f)
        self.assertIn('--diagnostic-through practice',h)
        self.assertIn('four real UI journeys',p)
        self.assertNotIn('continue-on-error:',f)
        self.assertNotIn('continue-on-error:',h)
        self.assertEqual(len(verify.TESTS),5)
        self.assertEqual(len(verify.SCREENS),7)
    def test_backwards_compatibility_and_no_test_shortcuts(self):
        src=self.read(UI)
        for stage in ('session_start','session_running','session_pause','session_paused',
                      'session_resume','session_resumed','session_finish_open',
                      'session_finish_confirm','session_finished','session_capture',
                      'practice_session_closed','practice_open_root','practice_test_ready',
                      'practice_capture_review'):
            self.assertIn('stage("'+stage+'")',src)
        for important in ('SELECT COUNT(*)','SQLiteDatabase.OPEN_READONLY',
                          "s.status = 'COMPLETED'",'s.endedAtEpochMillis IS NOT NULL',
                          "s.status IN ('RUNNING','PAUSED')",'counts != Triple(1L, 1L, 0L)'):
            self.assertIn(important,src)

if __name__=='__main__': unittest.main(verbosity=2)
