#!/usr/bin/env python3
"""HF27 DIAG-02 adversarial source and parser contracts; no simulated Android credit."""
from __future__ import annotations
import hashlib
from pathlib import Path
import sys
import unittest

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'scripts'))
import sfdt22_emulator_verify as host

D='core/diagnostics/src/main/kotlin/com/mikeyphw/studyflow/core/diagnostics/Sfdt27DiagnosticTrace.kt'
B='core/database/src/main/java/com/mikeyphw/studyflow/core/database/StudyFlowDatabase.kt'
F='feature/subjects/src/main/java/com/mikeyphw/studyflow/feature/subjects/StudyContentScreen.kt'
L='core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/StudyFlowDataLayer.kt'
S='core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/Sfdt27AndroidTraceSink.kt'
UI='app/src/androidTest/java/com/mikeyphw/studyflow/Sfdt22HostedStudyJourneyTest.kt'
HELP='app/src/androidTest/java/com/mikeyphw/studyflow/Sfdt27ProductionSettingsProbe.kt'
WF='.github/workflows/studyflow-sfdt22-hosted-journeys.yml'
HOT='.github/workflows/studyflow-sfdt22-hotfix-session.yml'
THIS='scripts/test_sfdt27_diag02_contract.py'
DOC='docs/actions/SFDT22_HF27_DIAG02_WRITER_ATTRIBUTION.md'

class Diag02(unittest.TestCase):
    def get(self,path):return (ROOT/path).read_text()
    def test_01_debug_only_sql_callback_is_categorical(self):
        database=self.get(B)
        trace=self.get(D)
        self.assertIn('ApplicationInfo.FLAG_DEBUGGABLE',database)
        self.assertIn('setQueryCallback(',database)
        self.assertIn('onDebugSql(query)',database)
        self.assertIn('create(context, null, null)',database)
        self.assertIn('StudyFlowIntegrity.install(db)',database)
        self.assertIn('observeSql(sql: String)',trace)
        self.assertIn('"study_plans" -> "SQL_PLAN_MUTATION"',trace)
        self.assertIn('"study_plan_settings" -> "SQL_SETTINGS_MUTATION"',trace)
        self.assertIn('transaction = "unknown", observer = "sql_callback"',trace)
        self.assertNotIn('record("database_sql", sql',trace)
        self.assertNotIn('bindArgs',trace)
        self.assertNotIn('Log.',database)
    def test_02_real_app_instance_and_query_only(self):
        layer=self.get(L);sink=self.get(S);trace=self.get(D)
        self.assertIn('StudyFlowDatabase.create(',layer)
        self.assertIn('onDebugSql = Sfdt27DiagnosticTrace::observeSql',layer)
        self.assertIn('Sfdt27AndroidTraceSink.install(context, database)',layer)
        self.assertIn('installProductionRead',trace)
        self.assertIn('fun install(context: Context, database: StudyFlowDatabase)',sink)
        self.assertIn('database.studyPlanDao().countById(planId) == 1',sink)
        self.assertIn('database.studySupportDao().findPlanSettings(planId) != null',sink)
        self.assertNotIn('StudyPlanSettings.defaults(',sink)
        self.assertNotIn('Room.databaseBuilder(',sink)
    def test_03_production_read_at_real_ui_and_independent_checkpoints(self):
        screen=self.get(F);ui=self.get(UI);helper=self.get(HELP)
        self.assertIn('Sfdt27DiagnosticTrace.probeProduction(plan.id.value, "PRODUCTION_BEFORE_FEEDBACK")',screen)
        self.assertIn('Sfdt27DiagnosticTrace.probeProduction(plan.id.value, "PRODUCTION_AFTER_FEEDBACK")',screen)
        self.assertLess(screen.index('"PRODUCTION_BEFORE_FEEDBACK"'),screen.index('snackbar.showSnackbar(success)'))
        self.assertIn('Sfdt27ProductionSettingsProbe.record(exactId, point)',ui)
        self.assertIn('runBlocking(Dispatchers.IO)',helper)
        self.assertNotIn('runBlocking(',ui)
        self.assertEqual(ui.count('@Test fun '),5)
        self.assertEqual(len(host.SCREENS),7)
    def test_04_restore_and_provision_paths_are_instrumented(self):
        paths={
        'core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/RoomStudyPlanPortabilityRepository.kt':['PORTABILITY_IMPORT_START','PORTABILITY_SNAPSHOT_RESTORE_START'],
        'core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/StudyFlowDatabaseSnapshotRestorer.kt':['SYNC_SNAPSHOT_RESTORE_START','SYNC_SNAPSHOT_RESTORE_COMMIT','SYNC_SNAPSHOT_RESTORE_ROLLBACK','database.withTransaction'],
        'core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/LocalStudyCloudSyncRepository.kt':['SYNC_RESTORE_REQUESTED','StudyFlowDatabaseSnapshotRestorer.restore'],
        'core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/RoomSamplePlanProvisioningRepository.kt':['SAMPLE_PLAN_INSERTED','database.withTransaction'],
        }
        for path,markers in paths.items():
            content=self.get(path)
            for m in markers:self.assertIn(m,content,path)
        cr=self.get('core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/RoomStudyContentRepository.kt')
        self.assertIn('insertMissingPlanSettings(settingsBeforeTopic)',cr)
        self.assertIn('TOPIC_TRANSACTION_COMMIT',cr)
        self.assertIn('PLAN_SETTINGS_CHANGED_DURING_TOPIC_SAVE',cr)
    def test_05_strict_host_parser_unknown_mutations_and_no_raw_export(self):
        self.assertIn('database_sql',host.SFDT27_TRACE_AREAS)
        self.assertIn('sql_callback',host.SFDT27_TRACE_OBSERVERS)
        raw=b'V2\n1|android_test|TRACE_RESET|none|unknown|none|unknown|unknown|none\n2|database_sql|SQL_PLAN_MUTATION|sql_replace|unknown|sql_callback|unknown|unknown|none\n'
        self.assertEqual(host.sfdt27_diag_parse(raw)[1]['operation'],'sql_replace')
        for bad in (raw.replace(b'sql_replace',b'raw select * from study_plans'),
                    raw.replace(b'database_sql',b'private'),
                    raw.replace(b'unknown|none\n',b'deleted|none\n'),
                    raw + b'3|sql_query|DROP TABLE|sql_replace|unknown|sql_callback|unknown|unknown|none\n'):
            with self.assertRaises((ValueError,UnicodeError)):
                host.sfdt27_diag_parse(bad)
        self.assertIn("'writer_unresolved' if missing",self.get('scripts/sfdt22_emulator_verify.py'))
    def test_06_immutable_full_gate_pins_and_failed_closed_qualifications(self):
        full=self.get(WF);focused=self.get(HOT)
        for path in (D,B,F,L,S,UI,HELP,THIS,DOC):
            self.assertIn(hashlib.sha256((ROOT/path).read_bytes()).hexdigest()+'  '+path,full,path)
        self.assertIn('python3 -B '+THIS,full)
        self.assertIn('python3 -B '+THIS,focused)
        self.assertIn('needs: [source, emulator]',full)
        self.assertIn('seven unique screenshots',full)
        self.assertIn('api-level: 36',full)
        self.assertNotIn('continue-on-error:',full+focused)
    def test_07_same_limits_no_release_or_read_write_fallback(self):
        trace=self.get(D);hosttxt=self.get('scripts/sfdt22_emulator_verify.py')
        for marker in ('MAX_EVENTS = 64','MAX_BYTES = 16_384','@Synchronized'):
            self.assertIn(marker,trace)
        self.assertIn("len(raw) <= 16384",hosttxt)
        self.assertIn("1 <= len(rows)-1 <= 64",hosttxt)
        self.assertIn('raw_logs_retained=False',hosttxt)
        self.assertNotIn('StudyPlanSettings.defaults(',self.get(HELP))
        self.assertNotIn('continue-on-error:',self.get(HOT))
        self.assertIn('nonqualifying',self.get(DOC))

if __name__=='__main__':unittest.main(verbosity=2)
