#!/usr/bin/env python3
"""HF08: fail-closed source contract for executable AndroidJUnitRunner on core:data."""
from __future__ import annotations
import hashlib
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
VERSIONS='gradle/libs.versions.toml'
DATA='core/data/build.gradle.kts'
FULL='.github/workflows/studyflow-sfdt22-hosted-journeys.yml'
TEST='scripts/test_sfdt22_hf30_hf08_runner_contract.py'
DOC='docs/actions/SFDT22_HF30_HF08_ANDROIDX_RUNNER_DEPENDENCY.md'
DIAG='scripts/sfdt22_hf30_hf07_diagnostics.py'
SH='scripts/sfdt22_emulator_ci.sh'

def read(p):
    return (ROOT/p).read_text(encoding='utf-8')

class AndroidxTestRunnerBootstrap(unittest.TestCase):
    def test_stable_compatible_runner_is_pinned_and_declared(self):
        ver=read(VERSIONS)
        self.assertEqual(ver.count('androidxTestRunner = "1.7.0"'),1)
        self.assertEqual(ver.count('androidx-test-runner = { module = "androidx.test:runner", version.ref = "androidxTestRunner" }'),1)
        self.assertIn('androidxJunit = "1.3.0"',ver)
    def test_core_data_runtime_contains_runner_explicitly(self):
        data=read(DATA)
        self.assertEqual(data.count('androidTestImplementation(libs.androidx.test.runner)'),1)
        self.assertIn('androidTestImplementation(libs.androidx.test.ext.junit)',data)
        self.assertIn('androidTestImplementation(libs.androidx.room.testing)',data)
        self.assertNotIn('testImplementation(libs.androidx.test.runner)',data.replace('androidTestImplementation',''))
    def test_true_android_connected_gate_is_still_required(self):
        debug=read(DIAG); wf=read(FULL); sh=read(SH)
        for required in ('-Pandroid.testInstrumentationRunnerArguments.class=',
                          ':core:data:assembleDebugAndroidTest',
                          ':core:data:connectedDebugAndroidTest',
                          'set(record[\'room\'][\'reported_tests\']) != ROOM_METHODS'):
            self.assertIn(required,debug)
        self.assertIn('scripts/sfdt22_hf30_hf07_diagnostics.py room-run',sh)
        self.assertIn('python3 -B scripts/sfdt22_emulator_verify.py --run',sh)
        self.assertIn('api-level: 36',wf)
        self.assertIn('needs: [source, emulator]',wf)
        self.assertIn('Independently verify five test receipts',wf)
        self.assertIn('seven unique screenshots',wf)
        self.assertNotIn('continue-on-error:',wf)
    def test_new_files_exact_hashes_checked_before_android(self):
        wf=read(FULL)
        for p in (VERSIONS,DATA,TEST,DOC):
            sha=hashlib.sha256((ROOT/p).read_bytes()).hexdigest()
            self.assertEqual(wf.count(sha+'  '+p),1,p)
        self.assertEqual(wf.count('python3 -B '+TEST),1)
    def test_nonqualifying_failure_policy_remains(self):
        wf=read(FULL); diag=read(DIAG)
        self.assertIn('test "$SOURCE_RESULT" = success',wf)
        self.assertIn('test "$EMULATOR_RESULT" = success',wf)
        self.assertIn('raw_logs_retained=False',diag)
        self.assertIn('release_qualified=False',diag)
        self.assertIn('publication_allowed=False',diag)

if __name__=='__main__':
    unittest.main(verbosity=2)
