#!/usr/bin/env python3
"""HF20: real search fallback, exact visible topic, strict hosted gates and source pins."""
from __future__ import annotations
import hashlib, importlib.util, json, sys, types, unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
KT='app/src/androidTest/java/com/mikeyphw/studyflow/Sfdt22HostedStudyJourneyTest.kt'
UI='feature/subjects/src/main/java/com/mikeyphw/studyflow/feature/subjects/StudyContentScreen.kt'
VER='scripts/sfdt22_emulator_verify.py'
WF='.github/workflows/studyflow-sfdt22-hosted-journeys.yml'
DOC='docs/actions/SFDT22_HF20_TOPIC_ROW_VISIBLE_SEARCH_RECOVERY.md'
TEST='scripts/test_sfdt22_hf20_contract.py'
if 'sfdt21_emulator_verify' not in sys.modules:
    sys.modules['sfdt21_emulator_verify']=types.ModuleType('sfdt21_emulator_verify')
spec=importlib.util.spec_from_file_location('hf20_reader',ROOT/VER)
v=importlib.util.module_from_spec(spec);spec.loader.exec_module(v)
class HF20(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
    cls.kt=(ROOT/KT).read_text();cls.ui=(ROOT/UI).read_text()
    cls.ver=(ROOT/VER).read_text();cls.wf=(ROOT/WF).read_text()
    cls.helper=cls.kt.split('private fun findSavedTopicThroughSearch(): Boolean {',1)[1].split('private fun awaitTopicSaved() {',1)[0]
    cls.confirm=cls.kt.split('private fun awaitTopicSaved() {',1)[1].split('private fun openTopicEditorFromSubject()',1)[0]
 def test_01_search_field_is_actual_production_input(self):
    self.assertEqual(self.ui.count('testTag("studyflow.syllabus.search")'),1)
    self.assertIn('onValueChange = { query = it }',self.ui)
    self.assertIn('expanded = visible.subject.id.value in expandedSubjectIds || query.isNotBlank()',self.ui)
    self.assertIn('topicTree.forEach { node ->',self.ui)
 def test_02_recovery_uses_only_real_ui_not_direct_writes(self):
    for required in ('hasScrollToIndexAction()', 'performScrollToNode(searchTag)',
      'hasSetTextAction() and (searchTag or hasAnyAncestor(searchTag))',
      'searchEditor.assertIsDisplayed().performTextReplacement(topicName)',
      'searchEditor.assertTextContains(topicName)',
      'studyflow.topic_row.name.$topicName',
      'matchingRows.onFirst().assertIsDisplayed()',
      'return false'):
        self.assertIn(required,self.helper)
    for banned in ('content.saveTopic(', 'sessionManager.', 'setContent {', 'mockk(',
                   'assumeTrue(', 'database.', 'repository.', 'Thread.sleep('):
        self.assertNotIn(banned,self.helper)
 def test_03_failure_path_never_accepts_snackbar_alone(self):
    self.assertIn('topicLists.onFirst().performScrollToNode(topicTag)',self.confirm)
    self.assertIn('if (findSavedTopicThroughSearch()) return',self.confirm)
    self.assertIn('if (topicSaveSuccessSeen)',self.confirm)
    self.assertIn('SFDT22_TOPIC_ROW_NOT_COMPOSED',self.confirm)
    self.assertIn('SFDT22_TOPIC_ROW_MISSING',self.confirm)
    self.assertIn('SFDT22_SUBJECT_NOT_EXPANDED',self.confirm)
    self.assertIn('rows.onFirst().assertIsDisplayed()',self.confirm)
    self.assertLess(self.confirm.index('if (findSavedTopicThroughSearch()) return'),
                    self.confirm.index('SFDT22_TOPIC_ROW_NOT_COMPOSED'))
 def test_04_new_search_failure_codes_are_safe_and_stable(self):
    expected={'TOPIC_SEARCH_EDITOR_MISSING':'topic_search_editor_missing',
              'TOPIC_SEARCH_WRITE_FAILED':'topic_search_write_failed'}
    for marker,reason in expected.items():
        self.assertIn('SFDT22_'+marker,self.helper)
        self.assertIn(reason,v.REASON_CODES)
        result=v.summarize_failure('SFDT22_STEP_topic_confirm SFDT22_'+marker+' SECRET=omit',v.TESTS[1],1)
        self.assertEqual((result.reason,result.stage),(reason,'topic_confirm'))
        self.assertNotIn('omit',str(result))
 def test_05_existing_behavior_and_test_count_preserved(self):
    self.assertIn('stage("topic_confirm") { awaitTopicSaved() }',self.kt)
    self.assertIn('stage("topic_expand") { ensureSavedSubjectExpanded() }',self.kt)
    self.assertEqual(self.kt.count('@Test fun '),5)
    self.assertEqual(len(v.SCREENS),7)
    self.assertIn('real_process_restart()',self.ver)
    self.assertIn('force_stopped_app_and_verified_new_pid=True',self.ver)
 def test_06_source_hashes_and_hosted_gate_intact(self):
    for path in (KT,UI,VER,TEST,DOC):
        digest=hashlib.sha256((ROOT/path).read_bytes()).hexdigest()
        self.assertIn(digest+'  '+path,self.wf)
    for previous in ('13','15','16','17','18','19'):
        self.assertIn('python3 -B scripts/test_sfdt22_hf'+previous+'_contract.py',self.wf)
    for gate in ('api-level: 36', ':app:assembleDebugAndroidTest',
                 'needs: [source, emulator]', 'test "$EMULATOR_RESULT" = success'):
        self.assertIn(gate,self.wf)
    self.assertNotIn('continue-on-error:',self.wf)
 def test_07_historical_provenance_unmodified(self):
    original=json.loads((ROOT/'devtool-overlay.json').read_text())
    self.assertEqual(original['name'],'studyflow_failure_audit_ov02_sflp_programming_contracts_v1')
    self.assertEqual(original['target'],'studyflow_android')
if __name__=='__main__':unittest.main(verbosity=2)
