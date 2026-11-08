#!/usr/bin/env python3
"""HF08: topic-editor real UI semantics regression and fail-closed diagnostics."""
from __future__ import annotations
import hashlib
import importlib.util
import re
import sys
import types
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
if 'sfdt21_emulator_verify' not in sys.modules:
    stub=types.ModuleType('sfdt21_emulator_verify')
    stub.adb=lambda *a,**kw:b''
    sys.modules['sfdt21_emulator_verify']=stub
spec=importlib.util.spec_from_file_location('sfdt22_hf08_verifier', ROOT/'scripts/sfdt22_emulator_verify.py')
assert spec and spec.loader
v=importlib.util.module_from_spec(spec)
spec.loader.exec_module(v)
K='app/src/androidTest/java/com/mikeyphw/studyflow/Sfdt22HostedStudyJourneyTest.kt'
V='scripts/sfdt22_emulator_verify.py'
H6='scripts/test_sfdt22_hf06_contract.py'
H8='scripts/test_sfdt22_hf08_contract.py'
WF='.github/workflows/studyflow-sfdt22-hosted-journeys.yml'
DOC='docs/actions/SFDT22_HF08_TOPIC_DIALOG_SEMANTICS.md'
class HF08(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.kt=(ROOT/K).read_text()
        cls.wf=(ROOT/WF).read_text()
        cls.ver=(ROOT/V).read_text()
        cls.h6=(ROOT/H6).read_text()

    def test_01_failing_exact_tag_unique_match_removed(self):
        self.assertNotIn('rule.onNodeWithTag("studyflow.topic_editor.name").performTextReplacement(topicName)',self.kt)
        self.assertIn('stage("topic_fill_name") { replaceTopicNameInDialog(topicName) }',self.kt)

    def test_02_real_dialog_scoping_only(self):
        for needle in ('rule.onAllNodes(isDialog(), useUnmergedTree = true)',
                       'hasSetTextAction() and hasAnyAncestor(isDialog())',
                       'useUnmergedTree = true,',
                       'private fun replaceTopicNameInDialog(value: String)'):
            self.assertIn(needle,self.kt)
        for needle in ('import androidx.compose.ui.test.hasAnyAncestor',
                       'import androidx.compose.ui.test.hasTestTag',
                       'import androidx.compose.ui.test.isDialog'):
            self.assertIn(needle,self.kt)

    def test_03_both_preferred_tagged_and_safe_dialog_first_fallback(self):
        self.assertIn('hasTestTag("studyflow.topic_editor.name")',self.kt)
        self.assertIn('if (tagged.fetchSemanticsNodes().size == 1)',self.kt)
        self.assertIn('tagged.onFirst()',self.kt)
        self.assertIn('inDialog.onFirst()',self.kt)
        self.assertIn('editor.assertIsDisplayed().performTextReplacement(value)',self.kt)

    def test_04_real_editable_text_verified_before_save(self):
        index=self.kt.index('stage("topic_fill_name") { replaceTopicNameInDialog(topicName) }')
        self.assertLess(index,self.kt.index('stage("topic_save")'))
        self.assertIn('hasSetTextAction() and hasAnyAncestor(isDialog()) and hasText(value)',self.kt)
        self.assertIn('SFDT22_TOPIC_VALUE_NOT_WRITTEN',self.kt)

    def test_05_no_background_search_fallback(self):
        helper=self.kt.split('private fun replaceTopicNameInDialog(value: String) {')[1].split('    private fun click(resId: Int)')[0]
        self.assertNotIn('rule.onAllNodes(hasSetTextAction()).onFirst()',helper)
        self.assertNotIn('setContent {',helper)
        self.assertNotIn('runBlocking',helper)
        self.assertIn('hasAnyAncestor(isDialog())',helper)

    def test_06_each_diagnostic_enum_allowlisted(self):
        pair=(('SFDT22_TOPIC_DIALOG_MISSING','topic_dialog_missing'),
              ('SFDT22_TOPIC_DIALOG_AMBIGUOUS','topic_dialog_ambiguous'),
              ('SFDT22_TOPIC_EDITABLE_MISSING','topic_editable_missing'),
              ('SFDT22_TOPIC_VALUE_NOT_WRITTEN','topic_value_not_written'))
        for marker,reason in pair:
            self.assertIn(marker,self.kt)
            self.assertIn(reason,v.REASON_CODES)
            failure=v.summarize_failure('java.lang.AssertionError: SFDT22_STEP_topic_fill_name\n'+marker+' token=SENSITIVE',v.TESTS[1],0)
            self.assertEqual((failure.reason,failure.stage),(reason,'topic_fill_name'))
            self.assertNotIn('SENSITIVE',str(failure))

    def test_07_original_compose_reason_codes_kept(self):
        for token in ('no_matching_compose_node','ambiguous_compose_node','compose_wait_timeout','compose_assertion'):
            self.assertIn(token,v.REASON_CODES)
        failure=v.summarize_failure('SFDT22_STEP_topic_fill_name Expected exactly 1 node found 2',v.TESTS[1],0)
        self.assertEqual(failure.reason,'ambiguous_compose_node')

    def test_08_hf06_contract_tracks_scoped_ui(self):
        self.assertIn('rule.onNodeWithTag("studyflow.subject_editor.name").performTextReplacement(subjectName)',self.h6)
        self.assertIn('replaceTopicNameInDialog(topicName)',self.h6)
        self.assertNotIn("for type in ('subject','topic'):",self.h6)

    def test_09_all_five_real_tests_and_seven_png_requirements_remain(self):
        self.assertEqual(self.kt.count('@Test fun '),5)
        for name in v.TESTS:self.assertIn('fun '+name+'(',self.kt)
        for shot in v.SCREENS:self.assertIn('screenshot("'+shot+'")',self.kt)
        self.assertIn('force_stopped_app_and_verified_new_pid=True',self.ver)
        self.assertIn('createAndroidComposeRule<MainActivity>()',self.kt)

    def test_10_all_modified_code_and_tests_sha_pinned(self):
        for path in (K,V,H6,H8):
            self.assertIn(hashlib.sha256((ROOT/path).read_bytes()).hexdigest()+'  '+path,self.wf)
        self.assertIn('python3 -B '+H8,self.wf)
        self.assertIn('python3 -B '+H6,self.wf)
        self.assertTrue((ROOT/DOC).is_file())

    def test_11_mandatory_hosted_acceptance_retained(self):
        for fragment in ('needs: [source, emulator]', 'test "$EMULATOR_RESULT" = success',
                         ':app:assembleDebugAndroidTest','api-level: 36',
                         'scripts/sfdt22_emulator_verify.py --preflight-apks'):
            self.assertIn(fragment,self.wf)
        for forbidden in ('continue-on-error:','allow-failure','if: false'):
            self.assertNotIn(forbidden,self.wf)

    def test_13_hf06_legacy_direct_topic_assertion_removed(self):
        # HF08 HF01 regression: original HF08 failed its installer at the
        # previously-unmodified HF06 test_11 assertion.
        self.assertNotIn("self.assertIn('performTextReplacement(topicName)',self.kt)", self.h6)
        self.assertIn("self.assertIn('editor.assertIsDisplayed().performTextReplacement(value)',self.kt)",self.h6)
        self.assertIn('stage("topic_fill_name") { replaceTopicNameInDialog(topicName) }',self.kt)
        self.assertIn('editor.assertIsDisplayed().performTextReplacement(value)',self.kt)

    def test_12_no_account_or_release_data_retained(self):
        blank=v.blank('a'*40,'1','1')
        self.assertFalse(blank['raw_logs_retained'])
        self.assertFalse(blank['physical_device_qualified'])
        self.assertFalse(blank['release_qualified'])
        self.assertFalse(blank['publication_allowed'])

if __name__=='__main__':unittest.main(verbosity=2)
