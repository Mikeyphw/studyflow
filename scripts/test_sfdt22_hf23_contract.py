#!/usr/bin/env python3
"""HF23 source-only contracts. No Gradle, network, real database, secrets or Android runtime."""
from __future__ import annotations
import hashlib
from pathlib import Path
import sqlite3
import unittest

ROOT=Path(__file__).resolve().parents[1]
DATA='core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/RoomStudyContentRepository.kt'
WF='.github/workflows/studyflow-sfdt22-hosted-journeys.yml'
TEST='scripts/test_sfdt22_hf23_contract.py'
DOC='docs/actions/SFDT22_HF23_SETTINGS_PRESERVATION.md'

class HF23(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source=(ROOT/DATA).read_text()
        cls.workflow=(ROOT/WF).read_text()
        cls.section=cls.source.split('private suspend fun recalculateProgress(planId: StudyPlanId) {',1)[1].split('private suspend fun requirePlan(',1)[0]

    def test_01_snapshot_precedes_every_progress_write(self):
        s=self.section
        self.assertIn('val settingsBefore = requireNotNull(settingsDao.findPlanSettings(planId.value))',s)
        self.assertLess(s.index('val settingsBefore ='),s.index('structureDao.upsertSubjects'))
        self.assertLess(s.index('val settingsBefore ='),s.index('planDao.updateProgress('))
        self.assertIn('"PLAN_SETTINGS_MISSING_BEFORE_CONTENT_PROGRESS"',s)
        self.assertNotIn('StudyPlanSettings.defaults(',s)

    def test_02_original_preferences_only_restored_when_missing(self):
        s=self.section
        expected='''if (settingsDao.findPlanSettings(planId.value) == null) {
            settingsDao.insertMissingPlanSettings(settingsBefore)
        }
        check(settingsDao.findPlanSettings(planId.value) == settingsBefore)'''
        self.assertIn(expected,s)
        self.assertLess(s.index('planDao.updateProgress('),s.index('settingsDao.insertMissingPlanSettings('))
        self.assertIn('"PLAN_SETTINGS_CHANGED_DURING_CONTENT_PROGRESS"',s)
        self.assertNotIn('upsertPlanSettings(',s)
        self.assertNotIn('deletePlanSettings(',s)

    def test_03_topic_snapshots_preferences_before_insert_and_checks_after(self):
        body=self.source.split('override suspend fun upsertTopic(topic: Topic)',1)[1].split('override suspend fun deleteSubject(',1)[0]
        self.assertIn('val settingsBeforeTopic = requireNotNull(database.studySupportDao().findPlanSettings(topic.studyPlanId.value))',body)
        self.assertLess(body.index('val settingsBeforeTopic'),body.index('structureDao.upsertTopic('))
        self.assertIn('database.studySupportDao().insertMissingPlanSettings(settingsBeforeTopic)',body)
        self.assertLess(body.index('structureDao.upsertTopic('),body.index('insertMissingPlanSettings(settingsBeforeTopic)'))
        self.assertLess(body.index('insertMissingPlanSettings(settingsBeforeTopic)'),body.index('recalculateProgress(topic.studyPlanId)'))
        self.assertIn('"PLAN_SETTINGS_CHANGED_DURING_TOPIC_SAVE"',body)
        self.assertNotIn('StudyPlanSettings.defaults(',body)

    def test_04_both_subject_and_topic_recalculate_inside_existing_transactions(self):
        for marker in ('override suspend fun upsertSubject(subject: Subject): Subject = database.withTransaction {',
                       'override suspend fun upsertTopic(topic: Topic): Topic = database.withTransaction {',
                       'recalculateProgress(subject.studyPlanId)',
                       'recalculateProgress(topic.studyPlanId)'):
            self.assertIn(marker,self.source)
        dao=(ROOT/'core/database/src/main/java/com/mikeyphw/studyflow/core/database/dao/StudySupportDao.kt').read_text()
        self.assertIn('@Insert(onConflict = OnConflictStrategy.IGNORE)',dao)
        self.assertIn('suspend fun insertMissingPlanSettings(settings: StudyPlanSettingsEntity): Long',dao)

    def test_05_sqlite_cascade_recovery_preserves_exact_personalized_values(self):
        # Synthetic adversarial SQL model, NOT a claim of actual Android behavior.
        db=sqlite3.connect(':memory:')
        db.execute('PRAGMA foreign_keys=ON')
        db.execute('CREATE TABLE study_plans(id TEXT PRIMARY KEY, progressPercent INTEGER NOT NULL)')
        db.execute('CREATE TABLE study_plan_settings(studyPlanId TEXT PRIMARY KEY REFERENCES study_plans(id) ON DELETE CASCADE, preferredSessionMinutes INTEGER NOT NULL, configurationJson TEXT NOT NULL)')
        db.execute("INSERT INTO study_plans VALUES('p',0)")
        db.execute("INSERT INTO study_plan_settings VALUES('p',83,'personalized-settings')")
        # Emulates an unexpected destructive legacy operation on progress update.
        db.execute("CREATE TRIGGER legacy_progress AFTER UPDATE OF progressPercent ON study_plans BEGIN DELETE FROM study_plan_settings WHERE studyPlanId=NEW.id; END")
        before=db.execute('SELECT * FROM study_plan_settings WHERE studyPlanId=?',('p',)).fetchone()
        with db:
            db.execute("UPDATE study_plans SET progressPercent=20 WHERE id='p'")
            if db.execute('SELECT 1 FROM study_plan_settings WHERE studyPlanId=?',('p',)).fetchone() is None:
                db.execute('INSERT OR IGNORE INTO study_plan_settings VALUES(?,?,?)',before)
            self.assertEqual(db.execute('SELECT * FROM study_plan_settings WHERE studyPlanId=?',('p',)).fetchone(),before)
        # Another normal update never overwrites an existing personalized value.
        db.execute('DROP TRIGGER legacy_progress')
        with db:
            db.execute("UPDATE study_plans SET progressPercent=40 WHERE id='p'")
            self.assertEqual(db.execute('SELECT * FROM study_plan_settings WHERE studyPlanId=?',('p',)).fetchone(),before)
        db.close()

    def test_06_full_hosted_gate_and_exact_sha_pins(self):
        for path in (DATA,TEST,DOC):
            self.assertIn(hashlib.sha256((ROOT/path).read_bytes()).hexdigest()+'  '+path,self.workflow)
        self.assertIn('python3 -B scripts/test_sfdt22_hf23_contract.py',self.workflow)
        for hf in ('19','20','21','22'):
            self.assertIn('python3 -B scripts/test_sfdt22_hf'+hf+'_contract.py',self.workflow)
        for gate in ('needs: [source, emulator]','test "$SOURCE_RESULT" = success','test "$EMULATOR_RESULT" = success','api-level: 36',':app:assembleDebugAndroidTest','--verify'):
            self.assertIn(gate,self.workflow)
        for forbidden in ('continue-on-error:','if: false','allow-failure'):
            self.assertNotIn(forbidden,self.workflow)

    def test_07_session_still_fails_closed_without_real_settings(self):
        domain=(ROOT/'core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ManageStudySession.kt').read_text()
        self.assertIn('requireNotNull(storedSettings)',domain)
        self.assertIn('PLAN_SETTINGS_MISSING',domain)
        self.assertNotIn('StudyPlanSettings.defaults(',domain)

if __name__=='__main__':unittest.main(verbosity=2)
