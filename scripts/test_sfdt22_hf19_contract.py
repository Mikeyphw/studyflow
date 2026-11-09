#!/usr/bin/env python3
"""HF19 source contracts: distinct missing-vs-lookup errors, real UI and pinned CI."""
from __future__ import annotations
import hashlib, importlib.util, json, sys, types, unittest
from pathlib import Path
R=Path(__file__).resolve().parents[1]
DOM='core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ManageStudySession.kt'
SCR='feature/sessions/src/main/java/com/mikeyphw/studyflow/feature/sessions/StudySessionScreen.kt'
KT='app/src/androidTest/java/com/mikeyphw/studyflow/Sfdt22HostedStudyJourneyTest.kt'
VER='scripts/sfdt22_emulator_verify.py'
WF='.github/workflows/studyflow-sfdt22-hosted-journeys.yml'
DOC='docs/actions/SFDT22_HF19_PLAN_SETTINGS_FAILURE_SPLIT.md'
TEST='scripts/test_sfdt22_hf19_contract.py'
if 'sfdt21_emulator_verify' not in sys.modules:
    sys.modules['sfdt21_emulator_verify']=types.ModuleType('sfdt21_emulator_verify')
spec=importlib.util.spec_from_file_location('hf19_reader',R/VER)
v=importlib.util.module_from_spec(spec);spec.loader.exec_module(v)
class HF19(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
    cls.dom=(R/DOM).read_text();cls.screen=(R/SCR).read_text()
    cls.kt=(R/KT).read_text();cls.ver=(R/VER).read_text();cls.wf=(R/WF).read_text()
 def test_01_preserve_existing_categories_and_original_exception_types(self):
    start=self.dom.split('suspend fun startQuickSession(',1)[1].split('suspend fun startFromActivity(',1)[0]
    for required in ('classify(QuickSessionStartFailureCategory.PLAN_SETTINGS)',
      'PLAN_SETTINGS_MISSING','PLAN_SETTINGS_LOOKUP_FAILED',
      'throw failure','throw cancelled','requireNotNull(storedSettings)',
      'repository.findAnyActiveSession()', 'repository.createSession(',
      'StudySessionStatus.RUNNING','runtimeClock.register(created)'):
        self.assertIn(required,start)
    self.assertIn('var planSettingsSpecificFailure = false',start)
    self.assertIn('!planSettingsSpecificFailure',start)
    self.assertLess(start.index('onFailureCategory(QuickSessionStartFailureCategory.PLAN_SETTINGS_MISSING)'),start.index('requireNotNull(storedSettings)'))
    self.assertLess(start.index('PLAN_SETTINGS_LOOKUP_FAILED'),start.index('PLAN_SETTINGS_MISSING'))
 def test_02_lookup_failure_and_missing_row_are_not_conflated(self):
    block=self.dom.split('val resolvedTargetDurationMinutes = classify(QuickSessionStartFailureCategory.PLAN_SETTINGS)',1)[1].split('val existingActive',1)[0]
    self.assertIn('plans.observeStoredPlanSettings(studyPlanId).first()',block)
    self.assertIn('catch (cancelled: java.util.concurrent.CancellationException)',block)
    self.assertIn('catch (failure: Exception)',block)
    self.assertIn('onFailureCategory(QuickSessionStartFailureCategory.PLAN_SETTINGS_LOOKUP_FAILED)',block)
    self.assertIn('if (storedSettings == null)',block)
    self.assertIn('onFailureCategory(QuickSessionStartFailureCategory.PLAN_SETTINGS_MISSING)',block)
    self.assertIn('requireNotNull(storedSettings)',block)
    self.assertNotIn('StudyPlanSettings.defaults(',block)
 def test_03_production_snackbar_contains_codes_only(self):
    for suffix in ('plan_settings','plan_settings_missing','plan_settings_lookup_failed'):
        self.assertEqual(self.screen.count('"studyflow.session.start.failure.'+suffix+'"'),1)
        self.assertIn('"studyflow.session.start.failure.'+suffix+'"',self.kt)
    self.assertIn('SnackbarHost(snackbar, modifier = Modifier.testTag(categoryTag))',self.screen)
    self.assertNotIn('exception.message',self.screen)
 def test_04_host_sanitizes_both_distinct_errors(self):
    expected={'SESSION_START_PLAN_SETTINGS_MISSING':'session_start_plan_settings_missing',
              'SESSION_START_PLAN_SETTINGS_LOOKUP_FAILED':'session_start_plan_settings_lookup_failed'}
    for marker,reason in expected.items():
        result=v.summarize_failure('SFDT22_STEP_session_running SFDT22_'+marker+' SECRET=never-export',v.TESTS[2],1)
        self.assertEqual((result.reason,result.stage),(reason,'session_running'))
        self.assertNotIn('never-export',str(result))
        self.assertIn(reason,v.REASON_CODES)
 def test_05_source_pins_match_current_files(self):
    for path in (DOM,SCR,KT,VER,TEST,DOC):
        self.assertIn(hashlib.sha256((R/path).read_bytes()).hexdigest()+'  '+path,self.wf)
    self.assertIn('python3 -B scripts/test_sfdt22_hf19_contract.py',self.wf)
    for older in ('15','16','17','18'):
        self.assertIn('python3 -B scripts/test_sfdt22_hf'+older+'_contract.py',self.wf)
 def test_06_keep_real_hosted_acceptance(self):
    self.assertIn('needs: [source, emulator]',self.wf)
    self.assertIn('test "$EMULATOR_RESULT" = success',self.wf)
    self.assertIn(':app:assembleDebugAndroidTest',self.wf)
    self.assertIn('api-level: 36',self.wf)
    self.assertNotIn('continue-on-error:',self.wf)
    self.assertEqual(self.kt.count('@Test fun '),5)
    self.assertEqual(len(v.SCREENS),7)
    self.assertIn('real_process_restart()',self.ver)
 def test_07_historical_manifest_untouched(self):
    root=json.loads((R/'devtool-overlay.json').read_text())
    self.assertEqual(root['name'],'studyflow_failure_audit_ov02_sflp_programming_contracts_v1')
if __name__=='__main__':unittest.main(verbosity=2)
