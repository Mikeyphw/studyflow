#!/usr/bin/env python3
"""HF27 source-only/negative contract; no Gradle and no synthetic runtime credit."""
from pathlib import Path
import hashlib, importlib.util, sys, types, unittest
ROOT=Path(__file__).resolve().parents[1]
K='app/src/androidTest/java/com/mikeyphw/studyflow/Sfdt22HostedStudyJourneyTest.kt'
UI='feature/subjects/src/main/java/com/mikeyphw/studyflow/feature/subjects/StudyContentScreen.kt'
PROBE='app/src/androidTest/java/com/mikeyphw/studyflow/Sfdt27RoomSettingsProbe.kt'
ROOM='app/src/androidTest/java/com/mikeyphw/studyflow/Sfdt27RoomSettingsRegressionTest.kt'
GRADLE='app/build.gradle.kts'
V='scripts/sfdt22_emulator_verify.py'
WF='.github/workflows/studyflow-sfdt22-hosted-journeys.yml'
DOC='docs/actions/SFDT22_HF27_SAME_PLAN_DIAGNOSTICS.md'
THIS='scripts/test_sfdt22_hf27_contract.py'
if 'sfdt21_emulator_verify' not in sys.modules:
    m=types.ModuleType('sfdt21_emulator_verify')
    m.adb=lambda *a,**kw:b''
    sys.modules['sfdt21_emulator_verify']=m
spec=importlib.util.spec_from_file_location('verify_hf27',ROOT/V)
assert spec and spec.loader
mod=importlib.util.module_from_spec(spec);spec.loader.exec_module(mod)
class HF27(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.kt=(ROOT/K).read_text();cls.ui=(ROOT/UI).read_text()
        cls.room=(ROOT/ROOM).read_text();cls.probe=(ROOT/PROBE).read_text()
        cls.ver=(ROOT/V).read_text();cls.wf=(ROOT/WF).read_text()
    def test_01_existing_full_gates_unmodified(self):
        self.assertEqual(self.kt.count('@Test fun '),5)
        self.assertEqual(len(mod.TESTS),5)
        self.assertEqual(len(mod.SCREENS),7)
        for x in ('needs: [source, emulator]', 'api-level: 36', 'force_stop', 'raw_logs_retained=False'):
            self.assertIn(x, self.wf + self.ver)
        for x in ('continue-on-error:', 'if: false', 'allow-failure'):
            self.assertNotIn(x,self.wf)
        self.assertIn('PLAN_SETTINGS_MISSING_BEFORE_TOPIC_SAVE',(ROOT/'core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/RoomStudyContentRepository.kt').read_text())
    def test_02_real_android_room_transaction_and_cold_reopen(self):
        for x in ('@RunWith(AndroidJUnit4::class)', 'Room.databaseBuilder(', 'StudyFlowIntegrity.install(',
                  'database.studySupportDao().findPlanSettings(id)', 'database.studyStructureDao().upsertSubject(',
                  'database.studyStructureDao().upsertTopic(', 'database.withTransaction {',
                  'database.close()', 'database = open()', 'HF27_INJECTED_ROLLBACK'):
            self.assertIn(x,self.room)
        self.assertEqual(self.room.count('@Test fun '),2)
        self.assertIn('sfdt27_hosted_room_regression()',self.ver)
        self.assertIn("evidence['hf27_room_regression'] = 'passed'",self.ver)
    def test_03_private_only_ui_marker_and_exact_plan(self):
        for x in ('FLAG_DEBUGGABLE','MessageDigest.getInstance("SHA-256")',
                  'Context.MODE_PRIVATE','sfdt27-', 'writeSfdt27UiPlanDigest('):
            self.assertIn(x,self.ui)
        for x in ('SELECT id FROM study_plans WHERE name = ?', 'digest != recorded',
                  'studyPlanId = ? AND name = ?', 'SQLiteDatabase.OPEN_READONLY',
                  'Sfdt27RoomSettingsProbe.read(context, exactId)', 'sfdt27-probes.txt'):
            self.assertIn(x,self.kt)
        for x in ('Room.databaseBuilder(', 'StudyFlowDatabase.DATABASE_NAME',
                  'secondHandle.studySupportDao().findPlanSettings(planId)', 'runBlocking(Dispatchers.IO)'):
            self.assertIn(x,self.probe)
        for forbidden in ('runBlocking {', 'runBlocking(', 'import kotlinx.coroutines.runBlocking',
                          'container.manageStudy', 'setContent('):
            self.assertNotIn(forbidden,self.kt)
        self.assertNotIn('println(',self.kt.split('private fun recordSfdt27Probe(',1)[1].split('private fun sessionRouteBoundary(',1)[0])
    def test_04_probe_receipts_cannot_export_private_data(self):
        self.assertEqual(mod.SFDT27_POINTS,('after_subject','before_topic','after_topic_action','after_topic_saved'))
        self.assertEqual(mod.sfdt27_parse_probes(b''),[])
        good=b'after_subject|matched\nbefore_topic|matched\nafter_topic_action|row_missing\n'
        self.assertEqual(mod.sfdt27_parse_probes(good)[-1],{'point':'after_topic_action','state':'row_missing'})
        for bad in (b'after_subject|SECRET',b'after_subject|matched|PRIVATE',b'after_topic_saved|matched',
                    b'after_subject|matched\nafter_subject|matched',b'after_subject|matched\nprivateId=abc',
                    b'after_subject|matched\n'*100):
            with self.assertRaises((ValueError,UnicodeError)):mod.sfdt27_parse_probes(bad)
        self.assertNotIn('sfdt27-topic-plan.sha256', self.ver)
    def test_05_full_workflow_exact_source_pins_and_runner(self):
        for path in (K,UI,V,PROBE,ROOM,GRADLE,THIS,DOC):
            self.assertIn(hashlib.sha256((ROOT/path).read_bytes()).hexdigest()+'  '+path,self.wf)
        self.assertIn('python3 -B '+THIS,self.wf)
        self.assertIn('Sfdt27RoomSettingsRegressionTest',self.ver)
        self.assertIn('androidTestImplementation(project(":core:database"))',(ROOT/GRADLE).read_text())
        self.assertIn('androidTestImplementation(libs.androidx.room.ktx)',(ROOT/GRADLE).read_text())
        self.assertIn('androidTestImplementation(libs.androidx.room.runtime)',(ROOT/GRADLE).read_text())
        self.assertNotIn('    implementation(project(":core:database"))',(ROOT/GRADLE).read_text())
        self.assertIn('sfdt27_probes=sfdt27_read_probes()',self.ver)
        self.assertIn('sfdt27_expected_probes()',self.ver)
    def test_06_hf22_rejects_inline_coroutine_regression(self):
        self.assertNotIn('runBlocking {',self.kt)
        self.assertNotIn('Room.databaseBuilder(',self.kt)
        self.assertIn('Sfdt27RoomSettingsProbe.read(context, exactId)',self.kt)
        self.assertEqual(self.probe.count('runBlocking(Dispatchers.IO)'),1)
        # HF27 HF02: the actual API-36 compile rejected RoomDatabase.use().
        # Require explicit try/finally cleanup, not a Closeable extension.
        self.assertNotIn('.build().use {', self.probe)
        self.assertIn('return try {', self.probe)
        self.assertIn('} finally {', self.probe)
        self.assertIn('secondHandle.close()', self.probe)
        self.assertNotIn('@Test fun ',self.probe)
        self.assertNotIn('upsertPlanSettings(',self.probe)
        self.assertNotIn('StudyPlanSettings.defaults(',self.probe)
    def test_08_two_real_room_instrumentations_and_bounded_failure(self):
        self.assertEqual(len(mod.SFDT27_ROOM_TESTS), 2)
        self.assertEqual(self.room.count('@Test fun '), 2)
        self.assertEqual(self.room.count('SFDT27_ROOM_STAGE_$hf27RoomStage'), 2)
        self.assertIn("'am', 'instrument', '-w', '-r'", self.ver)
        self.assertNotIn('OK (2 tests)', self.ver)
        self.assertIn("evidence['hf27_room_failure_reason_code']", self.ver)
        self.assertIn("evidence['hf27_room_failure_test']", self.ver)
        self.assertIn("evidence['overall'] = 'FAILED_OR_NOT_RUN'", self.ver)
        self.assertIn('raw_logs_retained=False', self.ver)
        self.assertIn('StudyFlowIntegrity.install(', self.room)
        for test in mod.SFDT27_ROOM_TESTS:
            ok = ('INSTRUMENTATION_STATUS: test=' + test + '\n'
                  'INSTRUMENTATION_STATUS_CODE: 1\n'
                  'INSTRUMENTATION_STATUS: test=' + test + '\n'
                  'INSTRUMENTATION_STATUS_CODE: 0\n'
                  'INSTRUMENTATION_CODE: -1\n').encode('ascii')
            self.assertEqual(mod.sfdt27_room_result(ok, test, 0), 'passed')
            self.assertEqual(mod.sfdt27_room_result(ok, test, 1), 'runner_nonzero')
            self.assertEqual(mod.sfdt27_room_result(ok + b'PRIVATENAME=SECRET', test, 0), 'passed')
            self.assertEqual(mod.sfdt27_room_result(b'SFDT27_ROOM_STAGE_COLD_REOPEN', test, 0),
                             'stage_cold_reopen')
            self.assertEqual(mod.sfdt27_room_result(b'SFDT27_ROOM_STAGE_PRIVATE_IDENTIFIER', test, 0),
                             'runner_no_test_status')
            self.assertEqual(mod.sfdt27_room_result(b'x' * 65537, test, 0),
                             'runner_output_oversize')
        self.assertEqual(mod.sfdt27_room_result(b'private=SECRET', mod.SFDT27_ROOM_TESTS[0], 0),
                         'runner_no_test_status')

    def test_07_no_production_mutation_or_release_qualification(self):
        self.assertIn('nonqualifying',(ROOT/DOC).read_text().lower())
        self.assertNotIn('StudyPlanSettings.defaults(',self.ui)
        self.assertNotIn('System.out.println',self.room)
        self.assertNotIn('pass\n    except',self.ver)
if __name__=='__main__':unittest.main(verbosity=2)
