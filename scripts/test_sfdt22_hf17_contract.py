#!/usr/bin/env python3
"""HF17 fail-closed CI contract for real session-start evidence classifications."""
from __future__ import annotations
import hashlib,importlib.util,sys,types,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
KT='app/src/androidTest/java/com/mikeyphw/studyflow/Sfdt22HostedStudyJourneyTest.kt'
V='scripts/sfdt22_emulator_verify.py'
WF='.github/workflows/studyflow-sfdt22-hosted-journeys.yml'
T='scripts/test_sfdt22_hf17_contract.py'
DOC='docs/actions/SFDT22_HF17_SESSION_START_DIAGNOSTICS.md'
if 'sfdt21_emulator_verify' not in sys.modules:
    sys.modules['sfdt21_emulator_verify']=types.ModuleType('sfdt21_emulator_verify')
spec=importlib.util.spec_from_file_location('sfdt22_hf17_verify',ROOT/V)
v=importlib.util.module_from_spec(spec);spec.loader.exec_module(v)
REASONS={
 'SESSION_START_FAILED':'session_start_failed',
 'SESSION_START_SUCCEEDED_NO_RUNNING_UI':'session_start_success_without_running_ui',
 'SESSION_RUNNING_CONTROL_MISSING':'session_running_control_missing',
 'SESSION_RUNNING_ACTION_MISSING':'session_running_action_missing',
 'SESSION_RUNNING_CONTROL_AMBIGUOUS':'session_running_control_ambiguous',
 'SESSION_RUNNING_CONTROL_DISABLED':'session_running_control_disabled',
 'SESSION_RUNNING_CONTROL_OFFSCREEN':'session_running_control_offscreen',
}
class HF17(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
    cls.kt=(ROOT/KT).read_text();cls.ver=(ROOT/V).read_text();cls.wf=(ROOT/WF).read_text()
    cls.block=cls.kt.split('private fun awaitSessionStartedAndRunning() {',1)[1].split('    @Test fun realUserStartsPauses',1)[0]
 def test_01_real_production_feedback_and_control_evidence(self):
    for s in ('SessionsR.string.operation_failed','SessionsR.string.session_started',
      'failures.fetchSemanticsNodes()','successes.fetchSemanticsNodes()',
      'hasTestTag("studyflow.session.active.pause")','hasClickAction()',
      'assertIsDisplayed().assertIsEnabled()','button.performScrollTo()'):
      self.assertIn(s,self.block)
    self.assertLess(self.block.index('failureSeen = failureSeen ||'),self.block.index('if (failureSeen)'))
 def test_02_fail_closed_and_no_domain_shortcut(self):
    for s in ('if (taggedCount > 1 || actionCount > 1)',
        'if (taggedCount == 0)','if (actionCount == 0)',
        'button.assertIsDisplayed().assertIsEnabled()',
        'throw AssertionError("SFDT22_SESSION_START_FAILED")'):
        self.assertIn(s,self.block)
    for bad in ('mockk(', 'setContent {','sessionManager.startQuickSession','sessionManager.pause(',
                'sessionManager.complete(', 'return true'):
        self.assertNotIn(bad,self.block)
 def test_03_only_running_checkpoint_changed_and_qualification_preserved(self):
    self.assertIn('stage("session_running") { awaitSessionStartedAndRunning() }',self.kt)
    for stage in ('session_start','session_pause','session_paused','session_resume',
                  'session_resumed','session_finish_open','session_finish_confirm','session_finished'):
        self.assertIn('stage("'+stage+'") {',self.kt)
    self.assertEqual(self.kt.count('@Test fun '),5)
    self.assertEqual(len(v.SCREENS),7)
    self.assertIn('real_process_restart()',self.ver)
    self.assertIn('force_stopped_app_and_verified_new_pid=True',self.ver)
 def test_04_reason_mapping_exact_and_redaction(self):
    for key,code in REASONS.items():
        self.assertIn('SFDT22_'+key,self.block)
        self.assertIn(code,v.REASON_CODES)
        x=v.summarize_failure('SFDT22_STEP_session_running SFDT22_'+key+' PRIVATE=secret',v.TESTS[2],1)
        self.assertEqual((x.reason,x.stage),(code,'session_running'))
        self.assertNotIn('PRIVATE',str(x))
    z=v.summarize_failure('SFDT22_STEP_session_running SFDT22_SESSION_START_FAILED '+
                          'SFDT22_SESSION_CONTROL_NOT_READY PRIVATE=secret',v.TESTS[2],1)
    self.assertEqual(z.reason,'session_start_failed')
 def test_05_workflow_keeps_sha_pins_and_mandatory_gates(self):
    for path in (KT,V,T,DOC):
      self.assertIn(hashlib.sha256((ROOT/path).read_bytes()).hexdigest()+'  '+path,self.wf)
    for s in ('python3 -B scripts/test_sfdt22_hf15_contract.py',
              'python3 -B scripts/test_sfdt22_hf16_contract.py',
              'python3 -B scripts/test_sfdt22_hf17_contract.py',
              ':app:assembleDebugAndroidTest','api-level: 36',
              'needs: [source, emulator]', 'test "$EMULATOR_RESULT" = success'):
      self.assertIn(s,self.wf)
    for bad in ('continue-on-error:', 'allow-failure','if: false'):
      self.assertNotIn(bad,self.wf)
 def test_06_historical_manifest_stays_untouched(self):
    import json
    manifest=json.loads((ROOT/'devtool-overlay.json').read_text())
    self.assertEqual(manifest['name'],'studyflow_failure_audit_ov02_sflp_programming_contracts_v1')
    self.assertEqual(manifest['target'],'studyflow_android')
if __name__=='__main__':unittest.main(verbosity=2)
