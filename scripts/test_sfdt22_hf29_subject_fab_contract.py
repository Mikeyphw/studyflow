#!/usr/bin/env python3
"""HF29: reject ambiguous subject FAB selectors, stale source and CI bypasses."""
from __future__ import annotations
from pathlib import Path
import hashlib,sys,unittest
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'scripts'))
import sfdt22_emulator_verify as verify
UI='app/src/androidTest/java/com/mikeyphw/studyflow/Sfdt22HostedStudyJourneyTest.kt'
SUBJECT='feature/subjects/src/main/java/com/mikeyphw/studyflow/feature/subjects/StudyContentScreen.kt'
RUNNER='scripts/sfdt22_emulator_verify.py'
FOCUSED='.github/workflows/studyflow-sfdt22-hotfix-session.yml'
FULL='.github/workflows/studyflow-sfdt22-hosted-journeys.yml'
DOC='docs/actions/SFDT22_HF29_SUBJECT_FAB_SELECTOR.md'
THIS='scripts/test_sfdt22_hf29_subject_fab_contract.py'
TAG='studyflow.syllabus.add_subject_fab'

class SubjectFabAcceptance(unittest.TestCase):
    def read(self,path):return (ROOT/path).read_text()
    def test_01_real_subject_fab_identity(self):
        prod=self.read(SUBJECT)
        self.assertEqual(prod.count('Modifier.testTag("'+TAG+'")'),1)
        self.assertIn('FloatingActionButton(\n                onClick = { addingSubject = true },',prod)
        self.assertIn('Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.add_subject))',prod)
        self.assertNotIn('assumeTrue(',prod)
    def test_02_real_compose_single_action_and_editor(self):
        src=self.read(UI)
        self.assertEqual(src.count('@Test fun '),5)
        self.assertEqual(src.count('screenshot("'),7)
        self.assertEqual(src.count('stage("subject_fab_ready")'),1)
        self.assertEqual(src.count('stage("subject_open_dialog")'),1)
        self.assertEqual(src.count('stage("subject_editor_ready")'),1)
        self.assertIn('hasTestTag("'+TAG+'") and hasClickAction()',src)
        self.assertEqual(src.count('hasTestTag("'+TAG+'") and hasClickAction()'),2)
        self.assertIn('fab.fetchSemanticsNodes().size == 1',src)
        self.assertIn('fab.onFirst().assertIsDisplayed().assertIsEnabled()',src)
        self.assertIn(').onFirst().performTouchInput { click() }',src)
        self.assertIn('hasTestTag("studyflow.subject_editor.name") and hasSetTextAction()',src)
        self.assertIn('editor.fetchSemanticsNodes().size == 1',src)
        self.assertIn('editor.onFirst().assertIsDisplayed()',src)
        self.assertIn('stage("subject_confirm") { awaitSubjectSaved() }',src)
        self.assertNotIn('onNodeWithContentDescription(text(SubjectsR.string.add_subject)).performClick()',src)
        self.assertNotIn('assumeTrue(',src)
        self.assertNotIn('setContent(',src)
    def test_03_stage_parser_and_failure_observable(self):
        for stage in ('subject_fab_ready','subject_open_dialog','subject_editor_ready'):
            self.assertIn(stage,verify.STAGES)
            f=verify.summarize_failure('SFDT22_STEP_'+stage+' ComposeTimeoutException',
                                       verify.TESTS[1],0)
            self.assertEqual(f.stage,stage)
            self.assertNotEqual(f.reason,'unexpected_runner_status')
        self.assertEqual(len(verify.TESTS),5)
        self.assertEqual(len(verify.SCREENS),7)
    def test_04_pinned_full_gate_and_focused_checks(self):
        full=self.read(FULL);focused=self.read(FOCUSED)
        for path in (UI,SUBJECT,RUNNER,FOCUSED,THIS,DOC):
            self.assertIn(hashlib.sha256((ROOT/path).read_bytes()).hexdigest()+'  '+path,full)
        self.assertIn('python3 -B '+THIS,full)
        self.assertIn('python3 -B '+THIS,focused)
        self.assertIn('needs: [source, emulator]',full)
        self.assertIn('api-level: 36',full)
        self.assertIn('seven unique screenshots',full)
        self.assertIn('Independently verify five test receipts',full)
        for workflow in (full,focused):
            self.assertNotIn('continue-on-error:',workflow)
            self.assertNotIn('if: false',workflow)
    def test_05_prior_practice_and_room_still_required(self):
        src=self.read(UI);full=self.read(FULL)
        for token in ('realUserCreatesAStudyPlan','realUserAddsSubjectAndTopic',
                      'realUserStartsPausesResumesAndCompletesASession',
                      'realUserAnswersQuestionAndOpensReviewQueue',
                      'realUserSeesPersistedProgressAfterColdRestart',
                      'stage("practice_test_ready")','screenshot("question")',
                      'screenshot("review")','screenshot("restart")'):
            self.assertIn(token,src)
        for token in ('scripts/test_sfdt22_hf28_practice_contract.py',
                      'scripts/test_sfdt27_fix03_hf03_contract.py',
                      'scripts/sfdt22_emulator_verify.py --verify'):
            self.assertIn(token,full)

if __name__=='__main__':unittest.main(verbosity=2)
