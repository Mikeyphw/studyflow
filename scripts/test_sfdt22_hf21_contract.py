#!/usr/bin/env python3
"""HF21: atomic legacy settings repair must preserve stored preferences and strict UI acceptance."""
from __future__ import annotations
import hashlib, json, re, sqlite3, unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
DAO='core/database/src/main/java/com/mikeyphw/studyflow/core/database/dao/StudyPlanDao.kt'
SUP='core/database/src/main/java/com/mikeyphw/studyflow/core/database/dao/StudySupportDao.kt'
REP='core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/RoomStudyPlanRepository.kt'
WF='.github/workflows/studyflow-sfdt22-hosted-journeys.yml'
DOC='docs/actions/SFDT22_HF21_LEGACY_PLAN_SETTINGS_REPAIR.md'
TEST='scripts/test_sfdt22_hf21_contract.py'

class HF21(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
    cls.dao=(ROOT/DAO).read_text();cls.sup=(ROOT/SUP).read_text()
    cls.rep=(ROOT/REP).read_text();cls.wf=(ROOT/WF).read_text()
 def test_01_query_matches_missing_only_and_sql_executes(self):
    self.assertIn('suspend fun findPlanIdsMissingSettings(): List<String>',self.dao)
    pattern=r'@Query\(\s*"""([^"]*SELECT plans\.id FROM study_plans AS plans[^"]*)""",\s*\)\s*suspend fun findPlanIdsMissingSettings'
    match=re.search(pattern,self.dao)
    self.assertIsNotNone(match)
    query=match.group(1).strip()
    self.assertIn('WHERE settings.studyPlanId IS NULL',query)
    db=sqlite3.connect(':memory:')
    db.execute('PRAGMA foreign_keys=ON')
    db.execute('CREATE TABLE study_plans(id TEXT PRIMARY KEY)')
    db.execute('CREATE TABLE study_plan_settings(studyPlanId TEXT PRIMARY KEY REFERENCES study_plans(id) ON DELETE CASCADE, preferredSessionMinutes INTEGER NOT NULL)')
    db.executemany('INSERT INTO study_plans VALUES(?)',[('new-plan',),('legacy-plan',),('archived-plan',)])
    db.execute('INSERT INTO study_plan_settings VALUES(?,?)',('new-plan',70))
    self.assertEqual(set(r[0] for r in db.execute(query)),{'legacy-plan','archived-plan'})
    for planId, in db.execute(query).fetchall():
        db.execute('INSERT OR IGNORE INTO study_plan_settings VALUES(?,?)',(planId,45))
    self.assertEqual(db.execute('SELECT preferredSessionMinutes FROM study_plan_settings WHERE studyPlanId=?',('new-plan',)).fetchone()[0],70)
    self.assertEqual(db.execute(query).fetchall(),[])
    self.assertEqual(db.execute('SELECT COUNT(*) FROM study_plan_settings').fetchone()[0],3)
    # Repeated initialization must preserve all personalized values.
    self.assertEqual(db.execute('SELECT preferredSessionMinutes FROM study_plan_settings WHERE studyPlanId=?',('new-plan',)).fetchone()[0],70)
    db.close()
 def test_02_dao_insert_is_ignore_not_replace(self):
    self.assertIn('import androidx.room.Insert',self.sup)
    self.assertIn('import androidx.room.OnConflictStrategy',self.sup)
    self.assertIn('@Insert(onConflict = OnConflictStrategy.IGNORE)\n    suspend fun insertMissingPlanSettings(settings: StudyPlanSettingsEntity): Long',self.sup)
    self.assertIn('@Upsert\n    suspend fun upsertPlanSettings(settings: StudyPlanSettingsEntity)',self.sup)
    self.assertNotIn('OnConflictStrategy.REPLACE',self.sup)
 def test_03_repair_transaction_and_every_app_launch(self):
    seed=self.rep.split('suspend fun seedFoundationSamplesOnce() {',1)[1].split('private suspend fun capturePhysicalOwnership',1)[0]
    self.assertIn('database.withTransaction {',seed)
    self.assertIn('if (metadataDao.findValue(FOUNDATION_SEED_KEY) == null)',seed)
    self.assertIn('studyPlanDao.findPlanIdsMissingSettings().forEach { missingId ->',seed)
    self.assertIn('studySupportDao.insertMissingPlanSettings(',seed)
    self.assertIn('StudyPlanSettings.defaults(StudyPlanId(missingId)).asEntity()',seed)
    self.assertGreater(seed.index('findPlanIdsMissingSettings()'),seed.index('metadataDao.upsert('))
    self.assertLess(seed.rindex('findPlanIdsMissingSettings()'),seed.rindex('\n        }'))
    self.assertNotIn('studySupportDao.upsertPlanSettings(StudyPlanSettings.defaults(StudyPlanId(missingId))',seed)
    data_layer=(ROOT/'core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/StudyFlowDataLayer.kt').read_text()
    self.assertIn('initializeData = {\n            studyPlanRepository.seedFoundationSamplesOnce()',data_layer)
 def test_04_new_plan_still_created_atomically_and_no_session_bypass(self):
    self.assertIn('studyPlanDao.insert(plan.asEntity(nowEpochMillis = clock()))\n            studySupportDao.upsertPlanSettings(settings.asEntity())',self.rep)
    dom=(ROOT/'core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ManageStudySession.kt').read_text()
    self.assertIn('requireNotNull(storedSettings)',dom)
    self.assertIn('PLAN_SETTINGS_MISSING',dom)
    self.assertNotIn('StudyPlanSettings.defaults(',dom)
 def test_05_exact_ci_pins_and_all_previous_hosted_gates(self):
    for p in (DAO,SUP,REP,TEST,DOC):
        self.assertIn(hashlib.sha256((ROOT/p).read_bytes()).hexdigest()+'  '+p,self.wf)
    self.assertIn('python3 -B scripts/test_sfdt22_hf21_contract.py',self.wf)
    for prev in ('13','15','16','17','18','19','20'):
        self.assertIn('python3 -B scripts/test_sfdt22_hf'+prev+'_contract.py',self.wf)
    for gate in ('api-level: 36',':app:assembleDebugAndroidTest','needs: [source, emulator]','test "$EMULATOR_RESULT" = success'):
        self.assertIn(gate,self.wf)
    self.assertNotIn('continue-on-error:',self.wf)
    kt=(ROOT/'app/src/androidTest/java/com/mikeyphw/studyflow/Sfdt22HostedStudyJourneyTest.kt').read_text()
    self.assertEqual(kt.count('@Test fun '),5)
 def test_06_historical_metadata_and_security_intact(self):
    original=json.loads((ROOT/'devtool-overlay.json').read_text())
    self.assertEqual(original['name'],'studyflow_failure_audit_ov02_sflp_programming_contracts_v1')
    self.assertEqual(original['target'],'studyflow_android')
if __name__=='__main__':unittest.main(verbosity=2)
