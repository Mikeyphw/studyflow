#!/usr/bin/env python3
"""HF27 FIX-03 HF02: real Android failure-stage hardening, no synthetic acceptance."""
from __future__ import annotations
import hashlib
from pathlib import Path
import sys
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
import sfdt22_emulator_verify as host

UI = 'app/src/androidTest/java/com/mikeyphw/studyflow/Sfdt27SflpReplayRegressionTest.kt'
RUNNER = 'scripts/sfdt22_emulator_verify.py'
DOC = 'docs/actions/SFDT22_HF27_FIX03_HF02_SFLP_FAILURE_CLASSIFICATION.md'
THIS = 'scripts/test_sfdt27_fix03_hf02_contract.py'
FULL = '.github/workflows/studyflow-sfdt22-hosted-journeys.yml'
FOCUSED = '.github/workflows/studyflow-sfdt22-hotfix-session.yml'

class Fix03HF02Contracts(unittest.TestCase):
    def read(self, path): return (ROOT/path).read_text()

    def test_real_junit_lifecycle_is_stage_guarded(self):
        src = self.read(UI)
        self.assertEqual(src.count('@Test fun '), 3)
        for marker in ('@RunWith(AndroidJUnit4::class)', '@Before fun prepare()',
                       '@After fun cleanup()', 'SFDT27_ROOM_STAGE_$stage',
                       'cleanFixture()', 'createSfdt27DebugSflpReplayBridge(database)',
                       'openIsolatedDatabase()',
                       'assertEquals(originalSettings, database.studySupportDao().findPlanSettings(existingId))',
                       'assertNotNull(database.studyStructureDao().findTopic('):
            self.assertIn(marker,src)
        for stage in ('SFLP_SETUP','SFLP_FIXTURE','SFLP_BRIDGE','SFLP_CAPTURE',
                      'SFLP_REPLAY','SFLP_VERIFY','SFLP_REOPEN','SFLP_CLEANUP','SFLP_CLOSE'):
            self.assertIn(stage,src)
            self.assertIn(stage,host.SFDT27_ROOM_STAGES)
        self.assertIn('if (!::context.isInitialized) return',src)
        # JUnit emits this fixed-category prefix by interpolating a stage
        # variable. The source never contains literal expanded stage strings.
        # Enforce the actual producer/consumer contract, not a stale spelling.
        self.assertEqual(src.count('throw AssertionError("SFDT27_ROOM_STAGE_$stage", failure)'), 5)
        teardown = src.split('    @After fun cleanup() {',1)[1].split('    private fun cleanFixture()',1)[0]
        self.assertIn('var stage = "SFLP_CLEANUP"',teardown)
        self.assertIn('stage = "SFLP_CLOSE"',teardown)
        self.assertIn('throw AssertionError("SFDT27_ROOM_STAGE_$stage", failure)',teardown)
        self.assertLess(teardown.index('cleanFixture()'),teardown.index('stage = "SFLP_CLOSE"'))
        self.assertLess(teardown.index('stage = "SFLP_CLOSE"'),teardown.index('database.close()'))
        self.assertNotIn('assumeTrue(',src)
        self.assertNotIn('setContent(',src)

    def test_one_real_start_and_one_real_finish_remain_mandatory(self):
        self.assertEqual(len(host.SFDT27_SFLP_TESTS),3)
        self.assertEqual(len(host.SFDT27_ROOM_TESTS),2)
        for test in host.SFDT27_SFLP_TESTS:
            good=(f'INSTRUMENTATION_STATUS: test={test}\n'
                  'INSTRUMENTATION_STATUS_CODE: 1\n'
                  f'INSTRUMENTATION_STATUS: test={test}\n'
                  'INSTRUMENTATION_STATUS_CODE: 0\n'
                  'INSTRUMENTATION_CODE: -1\n').encode('ascii')
            self.assertEqual(host.sfdt27_room_result(good,test,0),'passed')
            self.assertNotEqual(host.sfdt27_room_result(good.replace(b'CODE: 0',b'CODE: -2'),test,0),'passed')
            self.assertNotEqual(host.sfdt27_room_result(good,test,1),'passed')
            self.assertNotEqual(host.sfdt27_room_result(good.replace(b'CODE: -1',b'CODE: 0'),test,0),'passed')

    def test_fixture_replay_reopen_cleanup_have_distinct_failed_receipts(self):
        test=host.SFDT27_SFLP_TESTS[0]
        for stage in ('SFLP_SETUP','SFLP_FIXTURE','SFLP_BRIDGE','SFLP_CAPTURE',
                      'SFLP_REPLAY','SFLP_VERIFY','SFLP_REOPEN','SFLP_CLEANUP','SFLP_CLOSE'):
            raw=(f'INSTRUMENTATION_STATUS: test={test}\n'
                 'INSTRUMENTATION_STATUS_CODE: 1\n'
                 f'INSTRUMENTATION_STATUS: stack=java.lang.AssertionError: SFDT27_ROOM_STAGE_{stage}\n'
                 f'INSTRUMENTATION_STATUS: test={test}\n'
                 'INSTRUMENTATION_STATUS_CODE: -2\n'
                 'INSTRUMENTATION_CODE: -1\n').encode('ascii')
            result=host.sfdt27_room_result(raw,test,0)
            self.assertEqual(result,'stage_'+stage.lower())
            self.assertNotEqual(result,'passed')

    def test_unstaged_failure_and_runner_corruption_still_red(self):
        test=host.SFDT27_SFLP_TESTS[0]
        raw=(f'INSTRUMENTATION_STATUS: test={test}\n'
             'INSTRUMENTATION_STATUS_CODE: 1\n'
             f'INSTRUMENTATION_STATUS: test={test}\n'
             'INSTRUMENTATION_STATUS_CODE: -2\n'
             'INSTRUMENTATION_CODE: -1\n').encode()
        self.assertEqual(host.sfdt27_room_result(raw,test,0),'runner_test_failed_unstaged')
        self.assertEqual(host.sfdt27_room_result(raw+b'INSTRUMENTATION_FAILED\n',test,0),
                         'runner_instrumentation_failed')
        self.assertNotEqual(host.sfdt27_room_result(raw.replace(test.encode(),b'differentTest'),test,0),'passed')
        self.assertEqual(host.sfdt27_room_result(b'private user string',test,0),'runner_no_test_status')
        self.assertEqual(host.sfdt27_room_result(b'X'*65537,test,0),'runner_output_oversize')
        self.assertNotIn('private',self.read(RUNNER).split('def sfdt27_room_result(',1)[1].split('def sfdt27_hosted_room_regression(',1)[0])

    def test_fail_closed_runner_and_full_hosted_gate(self):
        runner=self.read(RUNNER)
        for marker in ("evidence['hf27_sflp_failure_test']", "evidence['hf27_sflp_failure_reason_code']",
                       "if sflp_error is not None:", "evidence['overall'] = 'FAILED_OR_NOT_RUN'",
                       "'am', 'instrument', '-w', '-r'",'raw_logs_retained=False'):
            self.assertIn(marker,runner)
        full=self.read(FULL);focused=self.read(FOCUSED)
        for path in (UI,RUNNER,THIS,DOC,FOCUSED):
            self.assertIn(hashlib.sha256((ROOT/path).read_bytes()).hexdigest()+'  '+path,full)
        for wf in (full,focused):
            self.assertIn('python3 -B scripts/test_sfdt27_fix03_contract.py',wf)
            self.assertIn('python3 -B scripts/test_sfdt27_fix03_hf02_contract.py',wf)
            self.assertNotIn('continue-on-error:',wf)
        self.assertIn('needs: [source, emulator]',full)
        self.assertIn('seven unique screenshots',full)
        self.assertIn('api-level: 36',full)
        self.assertEqual(len(host.TESTS),5)
        self.assertEqual(len(host.SCREENS),7)

if __name__=='__main__': unittest.main(verbosity=2)
