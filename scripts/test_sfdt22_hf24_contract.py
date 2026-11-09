#!/usr/bin/env python3
"""HF24: fail-closed sanitized topic-save failure classification, source only.

This suite never runs Android, Gradle, or a database. Runtime truth still
requires GitHub-hosted API36 UI journeys.
"""
from __future__ import annotations
import hashlib
import importlib.util
from pathlib import Path
import re
import sys
import types
import unittest

ROOT=Path(__file__).resolve().parents[1]
UI='feature/subjects/src/main/java/com/mikeyphw/studyflow/feature/subjects/StudyContentScreen.kt'
KT='app/src/androidTest/java/com/mikeyphw/studyflow/Sfdt22HostedStudyJourneyTest.kt'
V='scripts/sfdt22_emulator_verify.py'
WF='.github/workflows/studyflow-sfdt22-hosted-journeys.yml'
TEST='scripts/test_sfdt22_hf24_contract.py'
DOC='docs/actions/SFDT22_HF24_SANITIZED_TOPIC_SAVE_FAILURES.md'
CODES=('settings_before_topic','settings_before_progress','settings_changed_progress','settings_changed_topic',
       'sqlite_constraint','sqlite_locked','sqlite_other','invalid_input','unexpected')

if 'sfdt21_emulator_verify' not in sys.modules:
    m=types.ModuleType('sfdt21_emulator_verify')
    m.adb=lambda *a,**kw:b''
    sys.modules['sfdt21_emulator_verify']=m
spec=importlib.util.spec_from_file_location('sfdt22_hf24_verify',ROOT/V)
assert spec and spec.loader
verify=importlib.util.module_from_spec(spec)
spec.loader.exec_module(verify)

class HF24(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.ui=(ROOT/UI).read_text()
        cls.kt=(ROOT/KT).read_text()
        cls.v=(ROOT/V).read_text()
        cls.wf=(ROOT/WF).read_text()

    def test_01_failure_code_allowlists_are_identical(self):
        self.assertEqual(set(CODES),verify.TOPIC_CAUSE_CODES)
        for code in CODES:
            self.assertIn('"'+code+'"',self.ui)
            self.assertIn('"'+code+'"',self.kt)
            self.assertIn('topic_save_'+code,verify.REASON_CODES)
        self.assertIn('internal val topicSaveDiagnosticCodes = setOf(',self.ui)
        self.assertIn('it in setOf(',self.kt)
        self.assertNotIn('Exception.toString(',self.ui)

    def test_02_only_known_exception_names_can_escape_production(self):
        for term in (
          'cause.message == "PLAN_SETTINGS_MISSING_BEFORE_TOPIC_SAVE"',
          'cause.message == "PLAN_SETTINGS_MISSING_BEFORE_CONTENT_PROGRESS"',
          'cause.message == "PLAN_SETTINGS_CHANGED_DURING_CONTENT_PROGRESS"',
          'cause.message == "PLAN_SETTINGS_CHANGED_DURING_TOPIC_SAVE"',
          'cause.javaClass.name == "android.database.sqlite.SQLiteConstraintException"',
          'cause.javaClass.name == "android.database.sqlite.SQLiteDatabaseLockedException"',
          'cause is IllegalArgumentException',
          'current = cause.cause?.takeUnless { it === cause }',
          'return "unexpected"',
        ):self.assertIn(term,self.ui)
        method=self.ui.split('internal fun classifyTopicSaveFailure(',1)[1].split('internal fun writeTopicSaveDiagnostic(',1)[0]
        for forbidden in ('println(', 'Log.', 'printStackTrace(', 'throw cause', 'error.toString()', 'cause.toString()',
                          'cause.message.toString()', 'cause.stackTrace', 'error.message ?:'):
            self.assertNotIn(forbidden,method)

    def test_03_debug_only_private_exact_code_and_release_no_write(self):
        block=self.ui.split('internal fun writeTopicSaveDiagnostic(',1)[1].split('private data class TopicEditorRequest(',1)[0]
        for term in ('ApplicationInfo.FLAG_DEBUGGABLE', 'return',
                     'code in topicSaveDiagnosticCodes', 'Context.MODE_PRIVATE',
                     'context.openFileOutput("sfdt22-topic-failure.txt"',
                     'context.deleteFile("sfdt22-topic-failure.txt")',
                     'code.toByteArray(Charsets.US_ASCII)'):
            self.assertIn(term,block)
        self.assertLess(block.index('ApplicationInfo.FLAG_DEBUGGABLE'),block.index('context.openFileOutput('))
        self.assertNotIn('stackTraceToString(',self.ui)

    def test_04_real_ui_error_stays_red_and_readable(self):
        self.assertIn('runRecoverableOperation { action() }',self.ui)
        self.assertIn('if (success == topicSaved) writeTopicSaveDiagnostic(diagnosticsContext, null)',self.ui)
        self.assertIn('writeTopicSaveDiagnostic(diagnosticsContext, classifyTopicSaveFailure(failure))',self.ui)
        self.assertIn('snackbar.showSnackbar(operationFailed)',self.ui)
        self.assertIn('launchAction(topicSaved) { content.saveTopic(topic) }',self.ui)
        self.assertEqual(self.kt.count('@Test fun '),5)
        self.assertIn('if (failedSeen) throw AssertionError("SFDT22_TOPIC_SAVE_FAILED")',self.kt)
        self.assertIn('stage("topic_save_feedback") { observeTopicSaveFeedback() }',self.kt)
        self.assertIn('throw AssertionError("SFDT22_TOPIC_CAUSE_" + diagnosticCode.uppercase())',self.kt)
        self.assertNotIn('assumeTrue(',self.kt)

    def test_05_hosted_android_diagnostic_read_is_bounded_and_allowlisted(self):
        source=self.kt.split('private fun observeTopicSaveFeedback() {',1)[1].split('private fun ensureSavedSubjectExpanded()',1)[0]
        for term in ('ByteArray(64)','if (length <= 0 || input.read() != -1) null',
                     'Charsets.US_ASCII', 'it in setOf(','.getOrNull()',
                     'SFDT22_TOPIC_CAUSE_','if (failedSeen) throw AssertionError("SFDT22_TOPIC_SAVE_FAILED")'):
            self.assertIn(term,source)
        self.assertNotIn('printStackTrace(',source)
        self.assertNotIn('println(',source)

    def test_06_verifier_exactly_maps_allowlisted_failure(self):
        name=verify.TESTS[1]
        for code in CODES:
            raw='java.lang.AssertionError: SFDT22_STEP_topic_save_feedback\nSFDT22_TOPIC_CAUSE_'+code.upper()+'  SECRET=NEVER_ECHO'
            result=verify.summarize_failure(raw,name,1)
            self.assertEqual((result.reason,result.stage),('topic_save_'+code,'topic_save_feedback'))
            self.assertNotIn('SECRET',str(result))
            self.assertEqual(set(vars(result)),{'reason','stage','test'})

    def test_07_unreviewed_code_and_wrong_stage_cannot_claim_identity(self):
        name=verify.TESTS[1]
        a=verify.summarize_failure('SFDT22_STEP_topic_save_feedback SFDT22_TOPIC_CAUSE_PRIVATE_DATA',name,1)
        self.assertNotIn('private_data',str(a))
        self.assertNotIn('PRIVATE_DATA',str(a))
        b=verify.summarize_failure('SFDT22_STEP_subject_save SFDT22_TOPIC_CAUSE_SQLITE_CONSTRAINT',name,1)
        self.assertNotEqual(b.reason,'topic_save_sqlite_constraint')
        c=verify.summarize_failure('SFDT22_STEP_topic_save_feedback SFDT22_TOPIC_CAUSE_SQLITE_CONSTRAINT',verify.TESTS[0],1)
        self.assertNotEqual(c.reason,'topic_save_sqlite_constraint')
        d=verify.summarize_failure('SFDT22_STEP_topic_save_feedback SFDT22_TOPIC_SAVE_FAILED PRIVATE=SECRETS',name,1)
        self.assertEqual(d.reason,'topic_save_failed')
        self.assertNotIn('SECRETS',str(d))

    def test_08_no_raw_android_data_or_weakened_qualifications(self):
        for marker in ('raw_logs_retained=False','force_stopped_app_and_verified_new_pid=True',
                       "evidence['failure_reason_code'] = exc.reason",
                       "evidence['failure_step'] = exc.stage"):
            self.assertIn(marker,self.v)
        for gate in ('needs: [source, emulator]','test "$SOURCE_RESULT" = success',
                     'test "$EMULATOR_RESULT" = success', 'api-level: 36',
                     ':app:assembleDebugAndroidTest','--verify'):
            self.assertIn(gate,self.wf)
        for bad in ('continue-on-error:', 'allow-failure', 'if: false'):
            self.assertNotIn(bad,self.wf)
        for name in verify.TESTS:self.assertIn('fun '+name+'(',self.kt)
        for screen in verify.SCREENS:self.assertIn('screenshot("'+screen+'")',self.kt)
        self.assertEqual(len(verify.TESTS),5)
        self.assertEqual(len(verify.SCREENS),7)

    def test_09_every_modified_file_and_contract_exactly_pinned(self):
        for path in (UI,KT,V,TEST,DOC):
            digest=hashlib.sha256((ROOT/path).read_bytes()).hexdigest()
            self.assertIn(digest+'  '+path,self.wf)
        for i in (20,21,22,23,24):
            self.assertIn('python3 -B scripts/test_sfdt22_hf%02d_contract.py'%i,self.wf)
        legacy=(ROOT/'.github/workflows/studyflow-ci.yml')
        if legacy.is_file():
            self.assertIn('options: [all, android, desktop, web, deep]',legacy.read_text())

    def test_10_existing_hf23_settings_preservation_unchanged(self):
        data=(ROOT/'core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/RoomStudyContentRepository.kt').read_text()
        for token in ('PLAN_SETTINGS_MISSING_BEFORE_TOPIC_SAVE',
                      'PLAN_SETTINGS_CHANGED_DURING_TOPIC_SAVE',
                      'PLAN_SETTINGS_CHANGED_DURING_CONTENT_PROGRESS',
                      'database.studySupportDao().insertMissingPlanSettings(settingsBeforeTopic)',
                      'check(settingsDao.findPlanSettings(planId.value) == settingsBefore)'):
            self.assertIn(token,data)
        self.assertNotIn('StudyPlanSettings.defaults(',data.split('override suspend fun upsertTopic',1)[1].split('override suspend fun deleteSubject',1)[0])

if __name__=='__main__':unittest.main(verbosity=2)
