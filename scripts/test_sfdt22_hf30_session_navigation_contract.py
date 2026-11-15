#!/usr/bin/env python3
"""HF30: fail-closed actual session completion and session→practice Android acceptance."""
from __future__ import annotations
from pathlib import Path
import hashlib,sys,unittest
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'scripts'))
import sfdt22_emulator_verify as verify
UI='app/src/androidTest/java/com/mikeyphw/studyflow/Sfdt22HostedStudyJourneyTest.kt'
SHELL='app/src/main/java/com/mikeyphw/studyflow/StudyFlowShell.kt'
PRIMITIVES='core/designsystem/src/main/java/com/mikeyphw/studyflow/core/designsystem/StudyFlowShellPrimitives.kt'
SHELL_TEST='app/src/test/java/com/mikeyphw/studyflow/StudyFlowShellFoundationContractTest.kt'
RUNNER='scripts/sfdt22_emulator_verify.py'
FOCUSED='.github/workflows/studyflow-sfdt22-hotfix-session.yml'
FULL='.github/workflows/studyflow-sfdt22-hosted-journeys.yml'
THIS='scripts/test_sfdt22_hf30_session_navigation_contract.py'
DOC='docs/actions/SFDT22_HF30_SESSION_TO_PRACTICE_QUALIFICATION.md'
STAGES=('session_completion_ui','session_completion_persisted',
        'session_return_to_workspace','session_workspace_ready',
        'practice_session_closed','practice_shell_ready','practice_tab_selected')

class SessionNavigationAcceptance(unittest.TestCase):
    def source(self,path):return (ROOT/path).read_text()
    def test_01_independent_session_completion(self):
        src=self.source(UI)
        self.assertEqual(src.count('@Test fun '),5)
        self.assertEqual(src.count('screenshot("'),7)
        for stage in STAGES:
            self.assertEqual(src.count('stage("'+stage+'")'),1,stage)
            self.assertIn(stage,verify.STAGES)
        self.assertIn('SQLiteDatabase.OPEN_READONLY',src)
        self.assertIn('SELECT COUNT(*)',src)
        self.assertIn("s.status = 'COMPLETED'",src)
        self.assertIn('s.endedAtEpochMillis IS NOT NULL',src)
        self.assertIn("s.status IN ('RUNNING','PAUSED')",src)
        self.assertIn('s.notes = ?',src)
        self.assertIn('arrayOf(sessionName, planName)',src)
        self.assertIn('counts != Triple(1L, 1L, 0L)',src)
        self.assertIn('SFDT22_SESSION_STATUS_LOOKUP_FAILED',src)
        self.assertIn('SFDT22_SESSION_STATUS_NOT_COMPLETED',src)
        self.assertIn('rule.onNodeWithContentDescription(text(SessionsR.string.go_back))',src)
        self.assertIn('text(SessionsR.string.no_active_session)',src)
        self.assertIn('stage("practice_session_closed") { assertSessionClosedInDatabase() }',src)
        self.assertNotIn('assumeTrue(',src)
        self.assertNotIn('setContent(',src)
    def test_02_navigation_stays_available_while_active(self):
        shell=self.source(SHELL);prim=self.source(PRIMITIVES)
        self.assertIn('StudyFlowCompactDock(destinations = destinations)',shell)
        self.assertIn('StudyFlowExpandedRail(destinations = destinations)',shell)
        self.assertIn('// Always preserve access to Today/Plan/Practice/Progress',shell)
        compact=shell.split('// Always preserve access to Today/Plan/Practice/Progress',1)[1]
        self.assertIn('AnimatedVisibility(',compact)
        self.assertTrue(compact.index('StudyFlowActiveSessionBar(') < compact.index('StudyFlowCompactDock(destinations = destinations)'))
        self.assertIn('StudyFlowShellDefaults.CompactDockHeight + 20.dp',shell)
        self.assertIn('StudyFlowPracticeTokens.SessionBarMinHeight + 28.dp',shell)
        self.assertNotIn('studyflow-session-dock-morph',shell)
        self.assertIn('testTag("studyflow.shell.root.${destination.key.lowercase()}")',prim)
        self.assertIn('role = Role.Tab',prim)
        self.assertIn('onClick = destination.onClick',prim)
        self.assertIn('assertFalse("studyflow-session-dock-morph" in shell)',self.source(SHELL_TEST))
    def test_03_unambiguous_compose_real_controls(self):
        ui=self.source(UI)
        self.assertIn('hasTestTag("studyflow.shell.root.$name") and',ui)
        self.assertIn('useUnmergedTree = true,',ui)
        self.assertIn('roots.all { name ->',ui)
        self.assertIn(').fetchSemanticsNodes().size == 1',ui)
        self.assertIn('SFDT22_ROOT_NAVIGATION_MISSING',ui)
        self.assertIn('root.assertIsDisplayed().assertIsEnabled().performTouchInput { click() }',ui)
        self.assertIn('tab(name).assertIsDisplayed().assertIsSelected()',ui)
        self.assertIn('stage("practice_open_questions")',ui)
        self.assertIn('stage("practice_test_ready")',ui)
        self.assertIn('stage("practice_capture_review")',ui)
    def test_04_fixed_failure_taxonomy(self):
        for stage,code in [('session_completion_persisted','SFDT22_SESSION_STATUS_NOT_COMPLETED'),
                           ('practice_session_closed','SFDT22_SESSION_STATUS_LOOKUP_FAILED'),
                           ('practice_shell_ready','SFDT22_ROOT_NAVIGATION_MISSING')]:
            error=verify.summarize_failure('SFDT22_STEP_'+stage+' '+code+' ComposeTimeoutException',
                                           verify.TESTS[3],0)
            self.assertEqual(error.stage,stage)
            self.assertIn(error.reason,{'session_status_not_completed',
                                        'session_status_lookup_failed','root_navigation_missing'})
            self.assertNotEqual(error.reason,'compose_wait_timeout')
        self.assertEqual(len(verify.TESTS),5)
        self.assertEqual(len(verify.SCREENS),7)
    def test_05_focused_four_journeys_not_release(self):
        f=self.source(FOCUSED)
        self.assertIn('first FOUR real hosted journeys',f)
        self.assertEqual(f.count('--diagnostic-through practice'),2)
        self.assertNotIn('--diagnostic-through session',f)
        self.assertIn('python3 -B '+THIS,f)
        self.assertIn('NOT full qualification',f)
        self.assertIn('scripts/sfdt22_emulator_verify.py --verify-diagnostic',f)
        self.assertIn('session-diagnostic:',f)
        self.assertNotIn('continue-on-error:',f)
    def test_06_immutable_full_gate_and_old_regressions(self):
        f=self.source(FULL)
        for p in (UI,SHELL,PRIMITIVES,SHELL_TEST,RUNNER,FOCUSED,THIS,DOC):
            h=hashlib.sha256((ROOT/p).read_bytes()).hexdigest()
            self.assertIn(h+'  '+p,f,p)
        for token in ('needs: [source, emulator]','api-level: 36',
                      'seven unique screenshots','Independently verify five test receipts',
                      'script: bash scripts/sfdt22_emulator_ci.sh',
                      'scripts/test_sfdt22_hf29_subject_fab_contract.py',
                      'scripts/test_sfdt22_hf28_practice_contract.py',
                      'scripts/test_sfdt27_fix03_hf03_contract.py',
                      'scripts/sfdt22_emulator_verify.py --verify'):
            self.assertIn(token,f)
        self.assertNotIn('continue-on-error:',f)
        self.assertNotIn('if: false',f)
        self.assertIn('python3 -B '+THIS,f)
    def test_07_no_fake_evidence_or_bypass(self):
        u=self.source(UI)
        for token in ('realUserCreatesAStudyPlan','realUserAddsSubjectAndTopic',
                      'realUserStartsPausesResumesAndCompletesASession',
                      'realUserAnswersQuestionAndOpensReviewQueue',
                      'realUserSeesPersistedProgressAfterColdRestart',
                      'screenshot("plan")','screenshot("subject")','screenshot("topic")',
                      'screenshot("session")','screenshot("question")','screenshot("review")',
                      'screenshot("restart")'):
            self.assertIn(token,u)
        self.assertNotIn('assumeTrue(',u)
        self.assertNotIn('skipTest(',u)
        self.assertNotIn('Thread.sleep(',u)
        self.assertNotIn('SystemClock.sleep(',u)
if __name__=='__main__':unittest.main(verbosity=2)
