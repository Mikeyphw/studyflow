#!/usr/bin/env python3
"""HF18: preserve real start failures and expose only allowlisted stage categories."""
from __future__ import annotations
import hashlib, importlib.util, json, sys, types, unittest
from pathlib import Path
R=Path(__file__).resolve().parents[1]
KT='app/src/androidTest/java/com/mikeyphw/studyflow/Sfdt22HostedStudyJourneyTest.kt'
S='feature/sessions/src/main/java/com/mikeyphw/studyflow/feature/sessions/StudySessionScreen.kt'
DOM='core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ManageStudySession.kt'
VER='scripts/sfdt22_emulator_verify.py'
WF='.github/workflows/studyflow-sfdt22-hosted-journeys.yml'
T='scripts/test_sfdt22_hf18_contract.py'
DOC='docs/actions/SFDT22_HF18_QUICK_SESSION_FAILURE_CLASSIFICATION.md'
if 'sfdt21_emulator_verify' not in sys.modules:
    sys.modules['sfdt21_emulator_verify']=types.ModuleType('sfdt21_emulator_verify')
sp=importlib.util.spec_from_file_location('hf18_verifier',R/VER)
v=importlib.util.module_from_spec(sp);sp.loader.exec_module(v)
REASONS={
 'SESSION_START_VALIDATION_FAILED':'session_start_validation_failed',
 'SESSION_START_PLAN_SETTINGS_FAILED':'session_start_plan_settings_failed',
 'SESSION_START_ALREADY_ACTIVE':'session_start_already_active',
 'SESSION_START_ACTIVE_LOOKUP_FAILED':'session_start_active_lookup_failed',
 'SESSION_START_PERSISTENCE_FAILED':'session_start_persistence_failed',
 'SESSION_START_DIAGNOSTIC_AMBIGUOUS':'session_start_diagnostic_ambiguous',
}
class HF18(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
    cls.dom=(R/DOM).read_text();cls.screen=(R/S).read_text()
    cls.kt=(R/KT).read_text();cls.ver=(R/VER).read_text();cls.wf=(R/WF).read_text()
 def test_01_precondition_stages_preserve_real_domain_errors(self):
    block=self.dom.split('suspend fun startQuickSession(',1)[1].split('suspend fun startFromActivity(',1)[0]
    for token in ('onFailureCategory: (QuickSessionStartFailureCategory) -> Unit = {}',
                  'classify(QuickSessionStartFailureCategory.VALIDATION)',
                  'classify(QuickSessionStartFailureCategory.PLAN_SETTINGS)',
                  'classify(QuickSessionStartFailureCategory.ACTIVE_LOOKUP)',
                  'QuickSessionStartFailureCategory.ACTIVE_SESSION',
                  'classify(QuickSessionStartFailureCategory.PERSISTENCE)',
                  'repository.createSession(', 'repository.findAnyActiveSession()',
                  'throw failure', 'throw cancelled',
                  'StudySessionStatus.RUNNING'):
        self.assertIn(token,block)
    self.assertLess(block.index('PLAN_SETTINGS'),block.index('ACTIVE_LOOKUP'))
    self.assertLess(block.index('ACTIVE_LOOKUP'),block.index('PERSISTENCE'))
 def test_02_existing_calls_remain_compatible_and_room_untouched(self):
    self.assertIn('onFailureCategory: (QuickSessionStartFailureCategory) -> Unit = {}',self.dom)
    self.assertIn('sessionManager.startQuickSession(',self.screen)
    self.assertIn('onFailureCategory = { category ->',self.screen)
    for bad in ('setContent {','mockk(','sessionManager.repository', 'System.out.println'):
        self.assertNotIn(bad,self.screen)
 def test_03_host_tags_fixed_enum_only_and_reset_each_start(self):
    self.assertIn('var sessionStartFailureCategory by remember { mutableStateOf<QuickSessionStartFailureCategory?>(null) }',self.screen)
    self.assertIn('sessionStartFailureCategory = null',self.screen)
    self.assertIn('sessionStartFailureCategory = category',self.screen)
    self.assertIn('SnackbarHost(snackbar, modifier = Modifier.testTag(categoryTag))',self.screen)
    for tag in ('validation','plan_settings','active_session','active_lookup','persistence'):
        self.assertIn('"studyflow.session.start.failure.'+tag+'"',self.screen)
    self.assertNotIn('exception.message',self.screen)
 def test_04_real_ui_failure_never_counts_as_success(self):
    block=self.kt.split('private fun awaitSessionStartedAndRunning() {',1)[1].split('    @Test fun realUserStartsPauses',1)[0]
    for marker in REASONS:
        self.assertIn('"SFDT22_'+marker+'"',block)
    self.assertIn('failureSeen || failures.fetchSemanticsNodes().isNotEmpty()',block)
    self.assertIn('throw AssertionError("SFDT22_SESSION_START_FAILED")',block)
    self.assertIn('button.assertIsDisplayed().assertIsEnabled()',block)
    self.assertIn('rule.onAllNodes(hasTestTag(tag), useUnmergedTree = true)',block)
    self.assertNotIn('sessionManager.startQuickSession(',block)
 def test_05_sanitizer_all_reason_codes_are_redacted(self):
    for marker,reason in REASONS.items():
        self.assertIn(reason,v.REASON_CODES)
        res=v.summarize_failure('SFDT22_STEP_session_running SFDT22_'+marker+' PRIVATE=SECRET',v.TESTS[2],1)
        self.assertEqual((res.reason,res.stage),(reason,'session_running'))
        self.assertNotIn('SECRET',str(res))
 def test_06_all_exact_sha_pins_and_old_gates_preserved(self):
    for path in (KT,S,DOM,VER,T,DOC):
        self.assertIn(hashlib.sha256((R/path).read_bytes()).hexdigest()+'  '+path,self.wf)
    self.assertIn('python3 -B '+T,self.wf)
    for old in ('scripts/test_sfdt22_hf15_contract.py','scripts/test_sfdt22_hf16_contract.py','scripts/test_sfdt22_hf17_contract.py'):
        self.assertIn('python3 -B '+old,self.wf)
    for gate in ('api-level: 36','needs: [source, emulator]', ':app:assembleDebugAndroidTest',
                 'test "$EMULATOR_RESULT" = success'):
        self.assertIn(gate,self.wf)
    self.assertNotIn('continue-on-error:',self.wf)
    self.assertEqual(self.kt.count('@Test fun '),5)
    self.assertIn('real_process_restart()',self.ver)
    self.assertEqual(len(v.SCREENS),7)
 def test_07_root_provenance_still_immutable(self):
    self.assertEqual(json.loads((R/'devtool-overlay.json').read_text())['name'],
      'studyflow_failure_audit_ov02_sflp_programming_contracts_v1')
if __name__=='__main__':unittest.main(verbosity=2)
