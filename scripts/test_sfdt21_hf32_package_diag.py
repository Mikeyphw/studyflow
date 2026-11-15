#!/usr/bin/env python3
"""HF32 offline adversarial tests for bounded debug package evidence."""
from __future__ import annotations
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import sfdt21_hf32_package_diag as d

class PackageDiagnostics(unittest.TestCase):
    def test_original_packaging_failure_classification(self):
        raw = "Execution failed for task ':app:packageDebug'\n" + \
              'A failure occurred while executing PackageAndroidArtifact$IncrementalSplitterRunnable\nSECRET_TOKEN\n'
        data = d.classify(raw, 1)
        self.assertEqual(data['category'], 'incremental_splitter')
        self.assertTrue(data['package_task_failed'])
        self.assertNotIn('SECRET', str(data))
        self.assertFalse(data['publication_allowed'])
    def test_specific_causes_have_precedence(self):
        for raw, expected in [('Caused by: java.io.IOException: No space left on device','disk_full'),
                              ('java.lang.OutOfMemoryError: Java heap space','out_of_memory'),
                              ('java.util.zip.ZipException: invalid entry','archive_corrupt'),
                              ('java.nio.file.AccessDeniedException: /user/secret','permission_denied')]:
            data = d.classify(raw+'\nIncrementalSplitterRunnable', 1)
            self.assertEqual(data['category'], expected)
    def test_unclassified_failure_cannot_pass(self):
        x = d.classify('SECRET_UNRECOGNIZED_DIAGNOSTIC', 3)
        self.assertEqual(x['overall'], 'FAILED_OR_NOT_RUN')
        self.assertEqual(x['category'], 'unclassified_failure')
        self.assertNotIn('SECRET', str(x))
    def test_receipt_has_no_raw_data(self):
        with tempfile.TemporaryDirectory() as f:
            d.write_receipt(Path(f), d.classify('SECRET_PACKAGE_STRING\nNo space left on device', 1))
            raw = (Path(f)/'SFDT21_PACKAGE_DIAGNOSTIC.json').read_text()
            self.assertNotIn('SECRET', raw)
            self.assertFalse(json.loads(raw)['raw_logs_retained'])
    def test_untrusted_task_invocation_is_rejected(self):
        with patch.dict('os.environ', {'GITHUB_ACTIONS':'false'}, clear=True):
            with self.assertRaisesRegex(ValueError, 'TASK_ALLOWLIST'):
                d.hosted_preflight(Path('/tmp'), [':app:assembleRelease'])
    def test_qualified_success_requires_zero_exit(self):
        self.assertEqual(d.classify('BUILD SUCCESSFUL', 0)['overall'], 'PASSED')
        self.assertEqual(d.classify('BUILD SUCCESSFUL', 1)['overall'], 'FAILED_OR_NOT_RUN')

if __name__ == '__main__':
    unittest.main(verbosity=2)
