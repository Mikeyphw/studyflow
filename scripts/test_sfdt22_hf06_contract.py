#!/usr/bin/env python3
"""SFDT22 HF06 negative-first source contracts for real subject/topic dialogs."""
from __future__ import annotations
import hashlib,importlib.util,sys,types,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
if 'sfdt21_emulator_verify' not in sys.modules:
    stub=types.ModuleType('sfdt21_emulator_verify')
    stub.adb=lambda *args,**kwargs:b''
    sys.modules['sfdt21_emulator_verify']=stub
spec=importlib.util.spec_from_file_location('sfdt22_hf06_verify',ROOT/'scripts/sfdt22_emulator_verify.py')
assert spec and spec.loader
m=importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)
K='app/src/androidTest/java/com/mikeyphw/studyflow/Sfdt22HostedStudyJourneyTest.kt'
D='feature/subjects/src/main/java/com/mikeyphw/studyflow/feature/subjects/StudyContentDialogs.kt'
V='scripts/sfdt22_emulator_verify.py'
WF='.github/workflows/studyflow-sfdt22-hosted-journeys.yml'
class HF06(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.kt=(ROOT/K).read_text()
        cls.dialog=(ROOT/D).read_text()
        cls.wf=(ROOT/WF).read_text()
    def test_01_real_dialogs_use_distinct_name_input_tags(self):
        self.assertEqual(self.dialog.count('testTag = "studyflow.subject_editor.name"'),1)
        self.assertEqual(self.dialog.count('testTag = "studyflow.topic_editor.name"'),1)
        self.assertIn('import androidx.compose.ui.platform.testTag',self.dialog)
        self.assertIn('modifier = if (testTag == null) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().testTag(testTag)',self.dialog)
    def test_02_real_text_fields_unchanged_behavior(self):
        self.assertIn("""OutlinedTextField(
        value = value,
        onValueChange = onValueChange,""",self.dialog)
        self.assertIn("""onSave(
                        SubjectEditorValue(""",self.dialog)
        self.assertIn("""onSave(
                        TopicEditorValue(""",self.dialog)
        self.assertIn('singleLine = minLines == 1',self.dialog)
    def test_03_instrumentation_targets_real_editable_nodes(self):
        for type in ('subject','topic'):
            self.assertIn('rule.onNodeWithTag("studyflow.'+type+'_editor.name").performTextReplacement('+type+'Name)',self.kt)
        self.assertIn('createAndroidComposeRule<MainActivity>()',self.kt)
        self.assertNotIn('fill(SubjectsR.string.name,',self.kt)
        self.assertIn('clickDialog(SubjectsR.string.save)',self.kt)
    def test_04_each_subject_step_is_allowlisted_and_reportable(self):
        stages=['subject_open_root','subject_open_content','subject_open_manager',
                'subject_open_dialog','subject_fill_name','subject_save','subject_confirm',
                'subject_capture','topic_open_dialog','topic_fill_name','topic_save',
                'topic_expand','topic_confirm','topic_capture']
        for name in stages:
            self.assertIn('stage("'+name+'")',self.kt)
            self.assertIn("'"+name+"'",(ROOT/V).read_text())
        self.assertEqual([self.kt.index('stage("'+name+'")') for name in stages],
                         sorted(self.kt.index('stage("'+name+'")') for name in stages))
    def test_05_ambiguous_label_single_node_dependency_removed(self):
        second=self.kt.split('@Test fun realUserAddsSubjectAndTopic()')[1].split('@Test fun realUserStartsPauses')[0]
        self.assertNotIn('rule.onNode(hasSetTextAction()',second)
        self.assertNotIn('fill(',second)
    def test_06_tab_disambiguation_remains_type_and_text_scoped(self):
        self.assertIn('private fun innerTab(resId: Int) = rule.onAllNodes(',self.kt)
        self.assertIn('SemanticsProperties.Role, Role.Tab',self.kt)
        self.assertIn(').onFirst()',self.kt)
    def test_07_exact_real_journeys_screens_cold_restart(self):
        self.assertEqual(self.kt.count('@Test fun '),5)
        for name in m.TESTS:self.assertIn('fun '+name+'(',self.kt)
        for shot in m.SCREENS:self.assertIn('screenshot("'+shot+'")',self.kt)
        self.assertIn('force_stopped_app_and_verified_new_pid=True',(ROOT/V).read_text())
    def test_08_source_pins_cover_dialog_and_test(self):
        for rel in (K,D,V):
            self.assertIn(hashlib.sha256((ROOT/rel).read_bytes()).hexdigest()+'  '+rel,self.wf)
    def test_09_no_gate_downgrade(self):
        for term in ('needs: [source, emulator]','test "$EMULATOR_RESULT" = success',
                     'api-level: 36',':app:assembleDebugAndroidTest',
                     'python3 -B scripts/test_sfdt22_hf06_contract.py'):
            self.assertIn(term,self.wf)
        for forbidden in ('continue-on-error:','allow-failure'):
            self.assertNotIn(forbidden,self.wf)
    def test_10_diagnostic_codes_do_not_leak_raw_output(self):
        e=m.summarize_failure('SFDT22_STEP_subject_fill_name Expected exactly 1 node secret=abc',m.TESTS[1],0)
        self.assertEqual((e.test,e.stage,e.reason),(m.TESTS[1],'subject_fill_name','ambiguous_compose_node'))
        self.assertNotIn('secret',str(e))
        self.assertFalse(m.blank('a'*40,'1','1')['raw_logs_retained'])
    def test_11_no_test_data_injection_or_semantics_bypass(self):
        for forbidden in ('setContent {','mockk(','SamplePlanProvisioner','container.manageStudyPlan','runBlocking {','assumeTrue('):
            self.assertNotIn(forbidden,self.kt)
        self.assertIn('performTextReplacement(subjectName)',self.kt)
        self.assertIn('performTextReplacement(topicName)',self.kt)
    def test_12_no_release_permissions(self):
        b=m.blank('a'*40,'1','1')
        self.assertFalse(b['publication_allowed'])
        self.assertFalse(b['physical_device_qualified'])
        self.assertFalse(b['release_qualified'])
if __name__=='__main__':unittest.main(verbosity=2)
