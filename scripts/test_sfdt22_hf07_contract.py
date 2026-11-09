#!/usr/bin/env python3
"""SFDT22 HF07 compiler regression: block missing comma from HF06."""
from __future__ import annotations
import hashlib
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
D = 'feature/subjects/src/main/java/com/mikeyphw/studyflow/feature/subjects/StudyContentDialogs.kt'
WF = '.github/workflows/studyflow-sfdt22-hosted-journeys.yml'
TEST = 'scripts/test_sfdt22_hf07_contract.py'

MISSING = ('modifier = if (testTag == null) Modifier.fillMaxWidth() '
           'else Modifier.fillMaxWidth().testTag(testTag)\n        label =')
FIXED = ('modifier = if (testTag == null) Modifier.fillMaxWidth() '
         'else Modifier.fillMaxWidth().testTag(testTag),\n        label =')

def valid_tagged_text_field(source: str) -> bool:
    return source.count(FIXED) == 1 and MISSING not in source

class HF07(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.dialog = (ROOT / D).read_text(encoding='utf-8')
        cls.workflow = (ROOT / WF).read_text(encoding='utf-8')

    def test_01_hf06_missing_comma_rejected(self):
        self.assertFalse(valid_tagged_text_field(MISSING))
        self.assertFalse(valid_tagged_text_field(self.dialog.replace(FIXED, MISSING)))

    def test_02_corrected_kotlin_parameter_is_valid(self):
        self.assertTrue(valid_tagged_text_field(self.dialog))
        self.assertEqual(self.dialog.count(FIXED), 1)
        self.assertIn('singleLine = minLines == 1,', self.dialog)

    def test_03_named_real_compose_fields_preserved(self):
        for name in ('studyflow.subject_editor.name','studyflow.topic_editor.name'):
            self.assertEqual(self.dialog.count('testTag = "'+name+'"'), 1)
        self.assertIn('import androidx.compose.ui.platform.testTag', self.dialog)
        self.assertIn('OutlinedTextField(', self.dialog)

    def test_04_dialog_content_validation_still_present(self):
        self.assertIn('SubjectEditorValue(', self.dialog)
        self.assertIn('TopicEditorValue(', self.dialog)
        self.assertIn('enabled = valid,', self.dialog)

    def test_05_dialog_source_sha256_is_pinned(self):
        self.assertIn(hashlib.sha256((ROOT/D).read_bytes()).hexdigest()+'  '+D, self.workflow)

    def test_06_hf07_regression_file_is_source_pinned(self):
        self.assertIn(hashlib.sha256((ROOT/TEST).read_bytes()).hexdigest()+'  '+TEST, self.workflow)
        self.assertIn('python3 -B '+TEST, self.workflow)

    def test_07_existing_source_and_emulator_checks_remain(self):
        self.assertIn('python3 -B scripts/test_sfdt22_hf06_contract.py',self.workflow)
        self.assertIn('python3 -B scripts/test_sfdt22_hf05_contract.py',self.workflow)
        self.assertIn('needs: [source, emulator]',self.workflow)
        self.assertIn(':app:assembleDebugAndroidTest',self.workflow)
        self.assertIn('api-level: 36',self.workflow)
        self.assertIn('test "$EMULATOR_RESULT" = success',self.workflow)

    def test_08_no_weakened_release_gates(self):
        self.assertNotIn('continue-on-error:',self.workflow)
        self.assertNotIn('allow-failure',self.workflow)
        self.assertNotIn('if: false',self.workflow)

if __name__ == '__main__': unittest.main(verbosity=2)
