#!/usr/bin/env python3
"""SFDT22 HF25 source contracts: plan identity, saved stack isolation and privacy."""
from __future__ import annotations
import hashlib
import importlib.util
from pathlib import Path
import sqlite3
import sys
import types
import unittest

ROOT=Path(__file__).resolve().parents[1]
NAV='app/src/main/java/com/mikeyphw/studyflow/StudyFlowV2Navigation.kt'
APP='app/src/main/java/com/mikeyphw/studyflow/StudyFlowApp.kt'
KT='app/src/androidTest/java/com/mikeyphw/studyflow/Sfdt22HostedStudyJourneyTest.kt'
VERIFY='scripts/sfdt22_emulator_verify.py'
WF='.github/workflows/studyflow-sfdt22-hosted-journeys.yml'
DOC='docs/actions/SFDT22_HF25_WORKSPACE_PLAN_OWNERSHIP.md'
SELF='scripts/test_sfdt22_hf25_contract.py'

class HF25(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.nav=(ROOT/NAV).read_text()
        cls.app=(ROOT/APP).read_text()
        cls.kt=(ROOT/KT).read_text()
        cls.wf=(ROOT/WF).read_text()
        cls.ver=(ROOT/VERIFY).read_text()

    def test_01_dashboard_selection_clears_previous_workspace_stacks(self):
        src=self.app.split('composable<DashboardRoute> { entry ->',1)[1].split('composable<AnalyticsRoute>',1)[0]
        self.assertIn('LegacyWorkspaceRedirect(',src)
        self.assertIn('TodayRoute(route.planId)',src)
        self.assertIn('clearSavedWorkspaceStacks = true',src)
        self.assertNotIn('LegacyWorkspaceRedirect(navController, TodayRoute(route.planId))',src)

    def test_02_reset_precedes_redirect_in_one_effect(self):
        f=self.nav.split('internal inline fun <reified T : Any> LegacyWorkspaceRedirect(',1)[1].split('StudyFlowLoadingPane()',1)[0]
        for marker in ('clearSavedWorkspaceStacks: Boolean = false',
                       'LaunchedEffect(target, clearSavedWorkspaceStacks)',
                       'if (clearSavedWorkspaceStacks) navController.clearWorkspaceSavedStacks()',
                       'navController.navigate(target)'):
            self.assertIn(marker,f)
        self.assertLess(f.index('navController.clearWorkspaceSavedStacks()'),f.index('navController.navigate(target)'))
        self.assertEqual(f.count('LaunchedEffect('),1)
        for typ in ('TodayRoute','PlanWorkspaceRoute','PracticeWorkspaceRoute','ProgressWorkspaceRoute'):
            self.assertIn('clearBackStack<'+typ+'>()',self.nav)
        self.assertIn('restoreState = restoreSavedStack',self.nav)
        self.assertIn('if (requestedPlanId.isNotBlank()) {',self.nav)

    def test_03_all_existing_dashboard_plan_routes_still_work(self):
        for marker in ('container.createStudyPlan(plan, form.toSettings(plan.id))',
                       'navController.navigate(DashboardRoute(plan.id.value))',
                       'onPlanSelected = { plan -> navController.navigate(DashboardRoute(plan.id.value)) }',
                       'navController.navigate(DashboardRoute(importedPlanId))'):
            self.assertIn(marker,self.app)
        self.assertIn('navController.clearWorkspaceSavedStacks()',self.nav)  # existing picker

    def test_04_real_plan_header_must_match_before_content_mutation(self):
        src=self.kt.split('@Test fun realUserAddsSubjectAndTopic()',1)[1].split('@Test fun ',1)[0]
        self.assertIn('stage("subject_plan_identity") { assertCreatedPlanWorkspaceVisible() }',src)
        self.assertLess(src.index('stage("subject_open_root")'),src.index('stage("subject_plan_identity")'))
        self.assertLess(src.index('stage("subject_plan_identity")'),src.index('stage("subject_save")'))
        helper=self.kt.split('private fun assertCreatedPlanWorkspaceVisible()',1)[1].split('private fun assertCreatedPlanOwnsSavedSubject()',1)[0]
        for marker in ('hasText(planName, substring = false)',
                       'assertIsDisplayed()', 'SFDT22_WORKSPACE_PLAN_MISMATCH'):
            self.assertIn(marker,helper)
        self.assertNotIn('setContent(',src)

    def test_05_subject_is_joined_to_exact_created_plan_not_a_similarly_named_ui_card(self):
        src=self.kt.split('@Test fun realUserAddsSubjectAndTopic()',1)[1].split('@Test fun ',1)[0]
        self.assertIn('stage("subject_owner_confirm") { assertCreatedPlanOwnsSavedSubject() }',src)
        self.assertLess(src.index('stage("subject_confirm")'),src.index('stage("subject_owner_confirm")'))
        self.assertLess(src.index('stage("subject_owner_confirm")'),src.index('stage("topic_save")'))
        helper=self.kt.split('private fun assertCreatedPlanOwnsSavedSubject()',1)[1].split('private fun openRoot(',1)[0]
        for marker in ('SQLiteDatabase.OPEN_READONLY',
                       'JOIN study_plans AS owner ON owner.id = content.studyPlanId',
                       'WHERE owner.name = ? AND content.name = ?',
                       'arrayOf(planName, subjectName)',
                       'SFDT22_SUBJECT_OWNER_MISMATCH',
                       'SFDT22_SUBJECT_OWNER_LOOKUP_FAILED'):
            self.assertIn(marker,helper)
        for forbidden in ('INSERT INTO','UPDATE study_plans','DELETE FROM','openOrCreateDatabase(',
                          'container.manageStudy','runBlocking {','setContent {'):
            self.assertNotIn(forbidden,helper)

    def test_06_sqlite_fixture_reproduces_previous_false_positive_not_actual_android(self):
        with sqlite3.connect(':memory:') as db:
            db.execute('PRAGMA foreign_keys=ON')
            db.executescript('''
                CREATE TABLE study_plans(id TEXT PRIMARY KEY, name TEXT);
                CREATE TABLE study_plan_settings(studyPlanId TEXT PRIMARY KEY REFERENCES study_plans(id));
                CREATE TABLE subjects(id TEXT PRIMARY KEY, studyPlanId TEXT REFERENCES study_plans(id), name TEXT);
                INSERT INTO study_plans VALUES('created','SFDT22 Study Journey'),('old','Other plan');
                INSERT INTO study_plan_settings VALUES('created');
                INSERT INTO subjects VALUES('s','old','SFDT22 Ecology');
            ''')
            # HF24 observed the settings for the named created plan, not the active content owner.
            old=db.execute('SELECT COUNT(*) FROM study_plan_settings AS settings JOIN study_plans AS plans ON plans.id=settings.studyPlanId WHERE plans.name=?',('SFDT22 Study Journey',)).fetchone()[0]
            owned=db.execute('SELECT COUNT(*) FROM subjects AS content JOIN study_plans AS owner ON owner.id=content.studyPlanId WHERE owner.name=? AND content.name=?',('SFDT22 Study Journey','SFDT22 Ecology')).fetchone()[0]
            self.assertEqual((old,owned),(1,0))
            db.execute("UPDATE subjects SET studyPlanId='created' WHERE id='s'")
            owned=db.execute('SELECT COUNT(*) FROM subjects AS content JOIN study_plans AS owner ON owner.id=content.studyPlanId WHERE owner.name=? AND content.name=?',('SFDT22 Study Journey','SFDT22 Ecology')).fetchone()[0]
            self.assertEqual(owned,1)

    def test_07_host_failure_codes_are_allowlisted_and_redacted(self):
        prior=sys.modules.get('sfdt21_emulator_verify')
        if prior is None:
            stub=types.ModuleType('sfdt21_emulator_verify');stub.adb=lambda *a,**k:b''
            sys.modules['sfdt21_emulator_verify']=stub
        try:
            spec=importlib.util.spec_from_file_location('sfdt22_hf25_verifier',ROOT/VERIFY)
            assert spec and spec.loader
            module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
            for stage,marker,reason in (
                ('subject_plan_identity','SFDT22_WORKSPACE_PLAN_MISMATCH','workspace_plan_mismatch'),
                ('subject_owner_confirm','SFDT22_SUBJECT_OWNER_LOOKUP_FAILED','subject_owner_lookup_failed'),
                ('subject_owner_confirm','SFDT22_SUBJECT_OWNER_MISMATCH','subject_owner_mismatch')):
                self.assertIn(stage,module.STAGES)
                self.assertIn(reason,module.REASON_CODES)
                result=module.summarize_failure('SFDT22_STEP_'+stage+' '+marker+' PRIVATE=secret',module.TESTS[1],2)
                self.assertEqual((result.stage,result.reason),(stage,reason))
                self.assertNotIn('secret',str(result))
            self.assertEqual(len(module.TESTS),5)
            self.assertEqual(len(module.SCREENS),7)
        finally:
            if prior is None:sys.modules.pop('sfdt21_emulator_verify',None)

    def test_08_immutable_full_gate_source_pins(self):
        for path in (APP,NAV,KT,VERIFY,SELF,DOC):
            self.assertIn(hashlib.sha256((ROOT/path).read_bytes()).hexdigest()+'  '+path,self.wf)
        self.assertIn('python3 -B scripts/test_sfdt22_hf25_contract.py',self.wf)
        for old in ("scripts/test_sfdt22_hf24_contract.py",'needs: [source, emulator]',
                    'test "$SOURCE_RESULT" = success','test "$EMULATOR_RESULT" = success',
                    ':app:assembleDebugAndroidTest','api-level: 36','--verify'):
            self.assertIn(old,self.wf)
        for forbidden in ('continue-on-error:','allow-failure','if: false'):
            self.assertNotIn(forbidden,self.wf)
        self.assertEqual(self.kt.count('@Test fun '),5)
        for shot in ('plan','subject','topic','session','question','review','restart'):
            self.assertIn('screenshot("'+shot+'")',self.kt)
        for marker in ('SFDT22_TOPIC_CAUSE_','recordBoundary(',
                       'persistedSettingsBoundary("after_topic")'):
            self.assertIn(marker,self.kt)

    def test_09_no_settings_fallback_or_legacy_ci_mutation(self):
        plan=(ROOT/'core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/RoomStudyContentRepository.kt').read_text()
        self.assertIn('val settingsBeforeTopic = requireNotNull(',plan)
        self.assertIn('PLAN_SETTINGS_MISSING_BEFORE_TOPIC_SAVE',plan)
        self.assertIn('check(database.studySupportDao().findPlanSettings(topic.studyPlanId.value) == settingsBeforeTopic)',plan)
        self.assertNotIn('StudyPlanSettings.defaults(',plan)
        legacy=(ROOT/'.github/workflows/studyflow-ci.yml').read_text()
        self.assertIn("options: [all, android, desktop, web, deep]",legacy)
        self.assertIn("if: ${{ github.event_name == 'workflow_dispatch' && (inputs.lane == 'all' || inputs.lane == 'web') }}",legacy)

if __name__=='__main__':unittest.main(verbosity=2)
