#!/usr/bin/env python3
"""SFDT22 HF13: exact-subject expansion, real save outcome, and cold-restart gates."""
from __future__ import annotations
import hashlib
import importlib.util
from pathlib import Path
import sys
import types
import unittest

ROOT = Path(__file__).resolve().parents[1]
K = "app/src/androidTest/java/com/mikeyphw/studyflow/Sfdt22HostedStudyJourneyTest.kt"
UI = "feature/subjects/src/main/java/com/mikeyphw/studyflow/feature/subjects/StudyContentScreen.kt"
V = "scripts/sfdt22_emulator_verify.py"
WF = ".github/workflows/studyflow-sfdt22-hosted-journeys.yml"
H13 = "scripts/test_sfdt22_hf13_contract.py"
DOC = "docs/actions/SFDT22_HF13_TOPIC_PERSISTENCE_AND_EXPANSION.md"
if "sfdt21_emulator_verify" not in sys.modules:
    sys.modules["sfdt21_emulator_verify"] = types.ModuleType("sfdt21_emulator_verify")
spec = importlib.util.spec_from_file_location("sfdt22_hf13_verifier", ROOT / V)
assert spec is not None and spec.loader is not None
verify = importlib.util.module_from_spec(spec)
spec.loader.exec_module(verify)

class HF13(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.kt = (ROOT / K).read_text()
        cls.ui = (ROOT / UI).read_text()
        cls.workflow = (ROOT / WF).read_text()
        cls.reader = (ROOT / V).read_text()

    def test_01_only_real_card_toggle_has_conditional_tag(self):
        self.assertIn('if (expanded) "studyflow.subject_card.collapse"', self.ui)
        self.assertIn('else "studyflow.subject_card.expand"', self.ui)
        self.assertEqual(self.ui.count('studyflow.subject_card.expand"'), 1)
        self.assertEqual(self.ui.count('studyflow.subject_card.collapse"'), 1)
        self.assertIn('onClick = onToggleExpanded', self.ui)
        self.assertIn('if (expanded) {\n                topicTree.forEach', self.ui)

    def test_02_exact_saved_subject_scoped_and_idempotent(self):
        block = self.kt.split('private fun ensureSavedSubjectExpanded() {', 1)[1].split('private fun awaitTopicSaved()', 1)[0]
        for needle in ('awaitSubjectSaved()', 'hasTestTag("studyflow.subject_card.name.$subjectName")',
                       'hasAnyAncestor(subjectCardTag)', 'hasClickAction()',
                       'if (expanded.fetchSemanticsNodes().size == 1) return',
                       'action.assertIsDisplayed().performTouchInput { click() }',
                       'SFDT22_SUBJECT_NOT_EXPANDED', 'SFDT22_SUBJECT_EXPAND_ACTION_MISSING'):
            self.assertIn(needle, block)
        self.assertEqual(block.count('performTouchInput { click() }'), 1)
        self.assertNotIn('content.saveTopic(', block)

    def test_03_async_save_feedback_cannot_replace_persisted_row(self):
        feedback = self.kt.split('private fun observeTopicSaveFeedback() {', 1)[1].split('private fun ensureSavedSubjectExpanded()', 1)[0]
        check = self.kt.split('private fun awaitTopicSaved() {', 1)[1].split('private fun openTopicEditorFromSubject()', 1)[0]
        self.assertIn('SubjectsR.string.topic_saved', feedback)
        self.assertIn('SubjectsR.string.operation_failed', feedback)
        self.assertIn('if (failedSeen) throw AssertionError("SFDT22_TOPIC_SAVE_FAILED")', feedback)
        self.assertIn('topicSaveSuccessSeen = savedSeen', feedback)
        for needle in ('ensureSavedSubjectExpanded()', 'studyflow.topic_row.name.$topicName',
                       'SFDT22_TOPIC_ROW_MISSING', 'SFDT22_TOPIC_ROW_NOT_COMPOSED',
                       'SFDT22_SUBJECT_NOT_EXPANDED', 'SFDT22_TOPIC_EDITOR_STILL_OPEN',
                       'rows.onFirst().assertIsDisplayed()'):
            self.assertIn(needle, check)
        self.assertNotIn('visible(topicName)', check)

    def test_04_save_then_confirm_then_real_snapshot(self):
        sequence = self.kt.split('@Test fun realUserAddsSubjectAndTopic()', 1)[1].split('@Test fun realUserStartsPauses', 1)[0]
        required = ['stage("topic_save") { clickDialog(SubjectsR.string.save) }',
                    'stage("topic_save_feedback") { observeTopicSaveFeedback() }',
                    'stage("topic_expand") { ensureSavedSubjectExpanded() }',
                    'stage("topic_confirm") { awaitTopicSaved() }',
                    'stage("topic_capture") { screenshot("topic") }']
        self.assertEqual(sorted(sequence.index(s) for s in required), [sequence.index(s) for s in required])

    def test_05_restart_is_real_and_does_not_blindly_toggle(self):
        restart = self.kt.split('@Test fun realUserSeesPersistedProgressAfterColdRestart()', 1)[1]
        self.assertIn('ensureSavedSubjectExpanded()', restart)
        self.assertIn('awaitTopicSaved()', restart)
        self.assertNotIn('click(SubjectsR.string.expand)', restart)
        self.assertIn('real_process_restart()', self.reader)
        self.assertIn('force_stopped_app_and_verified_new_pid=True', self.reader)

    def test_06_sanitized_diagnostics_preserve_stage_and_strip_user_strings(self):
        for marker, reason, stage in (
            ('TOPIC_SAVE_FAILED', 'topic_save_failed', 'topic_save_feedback'),
            ('SUBJECT_EXPAND_ACTION_MISSING', 'subject_expand_action_missing', 'topic_expand'),
            ('SUBJECT_NOT_EXPANDED', 'subject_not_expanded', 'topic_expand'),
            ('TOPIC_ROW_NOT_COMPOSED', 'topic_row_not_composed', 'topic_confirm'),
            ('TOPIC_ROW_MISSING', 'topic_row_missing', 'topic_confirm'),
        ):
            self.assertIn('SFDT22_' + marker, self.kt)
            self.assertIn(reason, verify.REASON_CODES)
            failure = verify.summarize_failure('SFDT22_STEP_' + stage + ' SFDT22_' + marker +
                                                ' SECRET=do-not-upload', verify.TESTS[1], 1)
            self.assertEqual((failure.reason, failure.stage), (reason, stage))
            self.assertNotIn('SECRET', str(failure))
        self.assertIn('topic_save_feedback', verify.STAGES)

    def test_07_sha_pins_and_existing_hosted_gates(self):
        for path in (K, UI, V, H13, DOC):
            digest = hashlib.sha256((ROOT / path).read_bytes()).hexdigest()
            self.assertIn(digest + '  ' + path, self.workflow)
        for required in ('api-level: 36', 'needs: [source, emulator]',
                         'test "$EMULATOR_RESULT" = success',
                         ':app:assembleDebugAndroidTest',
                         'python3 -B scripts/test_sfdt22_hf13_contract.py'):
            self.assertIn(required, self.workflow)
        self.assertNotIn('continue-on-error:', self.workflow)
        self.assertEqual(self.kt.count('@Test fun '), 5)
        self.assertEqual(len(verify.SCREENS), 7)

if __name__ == '__main__':
    unittest.main(verbosity=2)
