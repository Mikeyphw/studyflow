#!/usr/bin/env python3
"""HF05: canonical real-UI navigation independent of Today empty state."""
from __future__ import annotations
import hashlib
from pathlib import Path
import sys
import unittest
ROOT=Path(__file__).resolve().parents[1]
K='app/src/androidTest/java/com/mikeyphw/studyflow/Sfdt22HostedStudyJourneyTest.kt'
W='feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/WorkspaceUtilityScreens.kt'
P='feature/plans/src/main/java/com/mikeyphw/studyflow/feature/plans/PlanListScreen.kt'
WF='.github/workflows/studyflow-sfdt22-hosted-journeys.yml'
V='scripts/sfdt22_emulator_verify.py'
class HF05(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.kt=(ROOT/K).read_text()
        cls.system=(ROOT/W).read_text()
        cls.plans=(ROOT/P).read_text()
        cls.wf=(ROOT/WF).read_text()
        cls.verify=(ROOT/V).read_text()
    def test_01_system_hub_real_icon_click(self):
        self.assertIn('fun openSystemHub()',self.kt)
        self.assertIn('rule.onNodeWithContentDescription(text(WorkspaceR.string.more))',self.kt)
        self.assertIn('more.assertIsDisplayed().performTouchInput { click() }',self.kt)
    def test_02_real_hub_all_plans_row_has_tag(self):
        self.assertIn('title = stringResource(R.string.all_plans),',self.system)
        self.assertIn('modifier = Modifier.testTag("studyflow.system.all_plans")',self.system)
        self.assertIn('onClick = onAllPlans',self.system)
        self.assertIn('import androidx.compose.ui.platform.testTag',self.system)
        self.assertIn('row.assertIsDisplayed().performTouchInput { click() }',self.kt)
    def test_03_real_fab_tagged_for_create(self):
        self.assertIn('FloatingActionButton(',self.plans)
        self.assertIn('modifier = Modifier.testTag("studyflow.plan_list.create_plan")',self.plans)
        self.assertIn('onClick = onCreatePlan',self.plans)
        self.assertIn('import androidx.compose.ui.platform.testTag',self.plans)
        self.assertIn('button.assertIsDisplayed().performTouchInput { click() }',self.kt)
    def test_04_no_empty_today_assumption(self):
        self.assertNotIn('rule.onNodeWithTag("studyflow.no_plan.create_plan"',self.kt)
        self.assertIn('fun tapCreatePlan()',self.kt)
        self.assertIn('rule.onNodeWithTag("studyflow.plan_list.create_plan", useUnmergedTree = true)',self.kt)
    def test_05_exact_sequence_precedes_real_editor(self):
        seq=['stage("plan_shell") { shell() }','stage("plan_system_hub") { openSystemHub() }',
          'stage("plan_all_plans") { openAllPlans() }','stage("plan_open") { tapCreatePlan() }',
          'stage("plan_editor_ready") {','stage("plan_name") { fill(PlansR.string.plan_name, planName) }']
        indices=[self.kt.index(x) for x in seq]
        self.assertEqual(indices,sorted(indices))
    def test_06_no_direct_route_no_mock(self):
        for disallowed in ('setContent {','container.manageStudyPlan','mockk(','SamplePlanProvisioner','runBlocking {','assumeTrue('):
            self.assertNotIn(disallowed,self.kt)
        self.assertIn('createAndroidComposeRule<MainActivity>()',self.kt)
    def test_07_hard_five_journeys_and_seven_screens(self):
        self.assertEqual(self.kt.count('@Test fun '),5)
        for name in ('plan','subject','topic','session','question','review','restart'):
            self.assertIn('screenshot("'+name+'")',self.kt)
        self.assertIn('force_stopped_app_and_verified_new_pid=True',self.verify)
    def test_08_diagnostic_stage_allowlist(self):
        for stage in ('plan_system_hub','plan_all_plans','plan_open'):
            self.assertIn("'"+stage+"'",self.verify)
            self.assertIn('stage("'+stage+'")',self.kt)
        self.assertIn('raise summarize_failure(raw, name, p.returncode)',self.verify)
    def test_09_source_hashes_exact(self):
        for rel in (K,W,P,V):
            self.assertIn(hashlib.sha256((ROOT/rel).read_bytes()).hexdigest()+'  '+rel,self.wf)
    def test_10_source_and_emulator_gates_still_hard(self):
        self.assertIn('needs: [source, emulator]',self.wf)
        self.assertIn('test "$EMULATOR_RESULT" = success',self.wf)
        self.assertNotIn('continue-on-error:',self.wf)
        self.assertNotIn('allow-failure',self.wf)
        self.assertIn('api-level: 36',self.wf)
        self.assertIn(':app:assembleDebugAndroidTest',self.wf)
        self.assertIn('python3 -B scripts/test_sfdt22_hf05_contract.py',self.wf)
    def test_11_existing_target_semantics_preserved(self):
        self.assertIn('create_plan',self.plans)
        self.assertIn('studyflow.no_plan.create_plan', (ROOT/'feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/WorkspaceCommon.kt').read_text())
    def test_12_unchanged_safety_and_qualification_gates(self):
        self.assertIn("raw_logs_retained=False",self.verify)
        self.assertIn('physical_device_qualified=False',self.verify)
        self.assertIn('release_qualified=False',self.verify)
        self.assertIn('publication_allowed=False',self.verify)
if __name__=='__main__':unittest.main(verbosity=2)
