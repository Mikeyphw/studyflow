#!/usr/bin/env python3
"""SFDT22 HF16: independently assert the exact hosted session journey contract."""
from __future__ import annotations
import hashlib
import importlib.util
from pathlib import Path
import sys
import types
import unittest
ROOT=Path(__file__).resolve().parents[1]
K='app/src/androidTest/java/com/mikeyphw/studyflow/Sfdt22HostedStudyJourneyTest.kt'
S='feature/sessions/src/main/java/com/mikeyphw/studyflow/feature/sessions/StudySessionScreen.kt'
D='feature/sessions/src/main/java/com/mikeyphw/studyflow/feature/sessions/StudySessionDialogs.kt'
V='scripts/sfdt22_emulator_verify.py'
WF='.github/workflows/studyflow-sfdt22-hosted-journeys.yml'
T='scripts/test_sfdt22_hf16_contract.py'
DOC='docs/actions/SFDT22_HF16_SESSION_WORKFLOW_ACCEPTANCE.md'
if 'sfdt21_emulator_verify' not in sys.modules:
    sys.modules['sfdt21_emulator_verify']=types.ModuleType('sfdt21_emulator_verify')
spec=importlib.util.spec_from_file_location('sfdt22_hf16_reader',ROOT/V)
v=importlib.util.module_from_spec(spec)
spec.loader.exec_module(v)
STAGES=('session_open_root','session_open_overview','session_open_sessions',
        'session_quick_dialog','session_title','session_start','session_running',
        'session_pause','session_paused','session_resume','session_resumed',
        'session_finish_open','session_finish_dialog','session_finish_notes',
        'session_finish_confirm','session_finished','session_capture')
class HF16(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.kt=(ROOT/K).read_text()
        cls.screen=(ROOT/S).read_text()
        cls.dialogs=(ROOT/D).read_text()
        cls.wf=(ROOT/WF).read_text()
        cls.ver=(ROOT/V).read_text()
    def test_01_production_snackbar_no_longer_blocks_mutations(self):
        start=self.screen.split('    fun launchAction(success: String, action: suspend () -> Unit) {',1)[1].split('    Scaffold(',1)[0]
        self.assertIn('val message = try {',start)
        self.assertIn('mutationInFlight = false',start)
        self.assertIn('snackbar.showSnackbar(message)',start)
        self.assertLess(start.index('mutationInFlight = false'),start.index('snackbar.showSnackbar(message)'))
        self.assertIn('java.util.concurrent.CancellationException',start)
        self.assertIn('throw cancelled',start)
        self.assertIn('if (mutationInFlight || !sessionsReady) return',start)
    def test_02_real_production_control_tags(self):
        for tag in ('studyflow.session.active.pause','studyflow.session.active.resume','studyflow.session.active.finish'):
            self.assertEqual(self.screen.count('Modifier.testTag("'+tag+'")'),1)
        for tag in ('studyflow.session.quick.title','studyflow.session.quick.start','studyflow.session.finish.notes','studyflow.session.finish.confirm'):
            self.assertEqual(self.dialogs.count('testTag("'+tag+'")'),1)
        self.assertIn('Modifier.verticalScroll(rememberScrollState())',self.dialogs)
    def test_03_session_ui_uses_exact_semantics_and_writes_no_repository(self):
        body=self.kt.split('    /** Exact production session control;',1)[1].split('    @Test fun realUserAnswersQuestionAndOpensReviewQueue()',1)[0]
        for snippet in ('assertIsEnabled()', 'hasSetTextAction() and (tagged or hasAnyAncestor(tagged))',
                        'performTextReplacement(value)','assertTextContains(value)','performClick()',
                        'SFDT22_SESSION_CONTROL_NOT_READY', 'SFDT22_SESSION_EDITOR_NOT_READY'):
            self.assertIn(snippet,body)
        for banned in ('sessionManager.startQuickSession','sessionManager.pause','sessionManager.complete','setContent {','mockk('):
            self.assertNotIn(banned,body)
    def test_04_checkpoints_match_actual_ui_sequence(self):
        body=self.kt.split('    @Test fun realUserStartsPausesResumesAndCompletesASession() {',1)[1].split('    @Test fun realUserAnswersQuestionAndOpensReviewQueue()',1)[0]
        for stage in STAGES:
            self.assertIn('stage("'+stage+'") {',body)
            self.assertIn(stage,v.STAGES)
        indexes=[body.index('stage("'+stage+'") {') for stage in STAGES]
        self.assertEqual(indexes,sorted(indexes))
        for s in ('studyflow.session.quick.title','studyflow.session.quick.start',
                  'studyflow.session.active.pause','studyflow.session.active.resume',
                  'studyflow.session.active.finish','studyflow.session.finish.notes',
                  'studyflow.session.finish.confirm','visible(sessionName)','screenshot("session")'):
            self.assertIn(s,body)
    def test_05_sanitized_failure_types_are_precise(self):
        test=v.TESTS[2]
        for token,code in [('SESSION_CONTROL_NOT_READY','session_control_not_ready'),
                           ('SESSION_EDITOR_NOT_READY','session_editor_not_ready')]:
            self.assertIn(code,v.REASON_CODES)
            result=v.summarize_failure('SFDT22_STEP_session_paused SFDT22_'+token+' PRIVATE=SECRET',test,1)
            self.assertEqual(result.reason,code)
            self.assertEqual(result.stage,'session_paused')
            self.assertNotIn('SECRET',str(result))
    def test_06_source_hashes_and_existing_hosted_proof(self):
        for path in (K,S,D,V,T,DOC):
            self.assertIn(hashlib.sha256((ROOT/path).read_bytes()).hexdigest()+'  '+path,self.wf)
        for code in ('python3 -B scripts/test_sfdt22_hf13_contract.py',
                     'python3 -B scripts/test_sfdt22_hf15_contract.py',
                     'python3 -B scripts/test_sfdt22_hf16_contract.py',
                     ':app:assembleDebugAndroidTest', 'api-level: 36',
                     'needs: [source, emulator]', 'test "$EMULATOR_RESULT" = success'):
            self.assertIn(code,self.wf)
        self.assertNotIn('continue-on-error:',self.wf)
        self.assertEqual(self.kt.count('@Test fun '),5)
        self.assertEqual(len(v.SCREENS),7)
        self.assertIn('real_process_restart()',self.ver)
        self.assertIn('force_stopped_app_and_verified_new_pid=True',self.ver)
    def test_07_manifest_stays_immutable(self):
        import json
        root=json.loads((ROOT/'devtool-overlay.json').read_text())
        self.assertEqual(root['name'],'studyflow_failure_audit_ov02_sflp_programming_contracts_v1')
if __name__=='__main__':unittest.main(verbosity=2)
