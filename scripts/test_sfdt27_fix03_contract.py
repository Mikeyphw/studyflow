#!/usr/bin/env python3
"""HF27 FIX-03: reject stale SFLP parent replay and loosened acceptance gates."""
from pathlib import Path
import hashlib,unittest,sys
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'scripts'))
import sfdt22_emulator_verify as verify
BRIDGE='core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/RoomStudyFlowSflpProductStateBridge.kt'
TEST='app/src/androidTest/java/com/mikeyphw/studyflow/Sfdt27SflpReplayRegressionTest.kt'
DOC='docs/actions/SFDT22_HF27_FIX03_SFLP_PARENT_REPLAY.md'
WF='.github/workflows/studyflow-sfdt22-hosted-journeys.yml'
FOCUSED='.github/workflows/studyflow-sfdt22-hotfix-session.yml'
THIS='scripts/test_sfdt27_fix03_contract.py'
class Fix03Contracts(unittest.TestCase):
    def get(self,path):return (ROOT/path).read_text()
    def test_real_vulnerable_writer_fixed_not_fake_repairs(self):
        src=self.get(BRIDGE)
        self.assertNotIn('INSERT OR REPLACE INTO ${quote(table)}',src)
        self.assertIn('UPDATE ${quote(table)} SET $assigns WHERE $where',src)
        self.assertIn('INSERT INTO ${quote(table)}',src)
        self.assertIn('"SELECT 1 FROM ${quote(table)} WHERE $where LIMIT 1"',src)
        self.assertIn('settingsSnapshot(db, keyArgs.single())',src)
        self.assertIn('primaryKey.keys.toSet() == primaryColumns.toSet()',src)
        self.assertIn('primaryColumns.all { column -> values[column] == primaryKey[column] }',src)
        self.assertIn('values.keys.all { it in schemaColumns }',src)
        self.assertIn('originalSettings == settingsSnapshot',src)
        self.assertIn('db.execSQL("DELETE FROM ${quote(table)} WHERE $where"',src)
        self.assertNotIn('StudyPlanSettings.defaults(',src)
        self.assertNotIn('fallbackToDestructiveMigration',src)
        self.assertIn('baseline = captureRows(',src)
    def test_hosted_real_android_test_mandatory(self):
        code=self.get(TEST);runner=self.get('scripts/sfdt22_emulator_verify.py')
        for symbol in ('@RunWith(AndroidJUnit4::class)','@Test fun existingPlanReplayPreservesSettingsAndOwnedContentAfterColdReopen',
                       '@Test fun noncanonicalPeerKeysAndValuesFailWithoutMutation',
                       '@Test fun newPlanReplayInsertsAndExplicitDeleteStillWorks',
                       'createSfdt27DebugSflpReplayBridge(database)','sfdt27-sflp-stage-v1.txt',
                       'assertEquals(originalSettings, database.studySupportDao().findPlanSettings(existingId))',
                       'openIsolatedDatabase()', 'database.close()',
                       'assertNotNull(database.studyStructureDao().findSubject('):
            self.assertIn(symbol,code)
        self.assertEqual(code.count('@Test fun '),3)
        self.assertIn('sfdt27_hosted_sflp_regression()',runner)
        self.assertIn("evidence['hf27_sflp_regression'] = 'passed'",runner)
        self.assertIn("data.get('hf27_sflp_regression') == 'passed'",runner)
        self.assertEqual(len(verify.SFDT27_ROOM_TESTS),2)
        self.assertEqual(len(verify.SFDT27_SFLP_TESTS),3)
        self.assertIn('Sfdt27SflpReplayRegressionTest#',runner)
        self.assertIn("if sflp_error is not None:",runner)
        self.assertIn('raw_logs_retained=False',runner)
        # HF02: setup, peer replay, and cold reopen must report distinct fixed
        # failure categories rather than burying JUnit lifecycle failures.
        for stage in ('SFLP_SETUP','SFLP_FIXTURE','SFLP_BRIDGE',
                      'SFLP_CAPTURE','SFLP_REPLAY','SFLP_VERIFY',
                      'SFLP_REOPEN','SFLP_CLEANUP','SFLP_CLOSE'):
            self.assertIn(stage,code)
            self.assertIn(stage,verify.SFDT27_ROOM_STAGES)
        self.assertIn('runner_test_failed_unstaged',verify.SFDT27_ROOM_CODES)
        self.assertIn('runner_instrumentation_failed',verify.SFDT27_ROOM_CODES)
        self.assertNotIn('assumeTrue(',code)
        self.assertNotIn('setContent(',code)
    def test_strict_privacy_and_full_gate(self):
        full=self.get(WF);focused=self.get(FOCUSED)
        for path in (BRIDGE,TEST,THIS,DOC,'scripts/sfdt22_emulator_verify.py',FOCUSED):
            self.assertIn(hashlib.sha256((ROOT/path).read_bytes()).hexdigest()+'  '+path,full,path)
        self.assertIn('python3 -B scripts/test_sfdt27_fix03_contract.py',full)
        self.assertIn('python3 -B scripts/test_sfdt27_fix03_contract.py',focused)
        self.assertIn('needs: [source, emulator]',full)
        self.assertIn('seven unique screenshots',full)
        self.assertIn('api-level: 36',full)
        self.assertNotIn('continue-on-error:',full+focused)
        self.assertEqual(len(verify.TESTS),5)
        self.assertEqual(len(verify.SCREENS),7)
    def test_sflp_payload_cannot_inject_sql_or_suppress_failure(self):
        src=self.get(BRIDGE)
        self.assertIn('SafeIdentifier.matches(table)',src)
        self.assertIn('require(SafeIdentifier.matches(value))',src)
        # Android/Kotlin requires List<Any>, not merely a runtime null check
        # on List<Any?>, for settingsSnapshot(db, keyArgs.single()).
        self.assertIn('val keyArgs: List<Any> = primaryColumns.map { column ->',src)
        self.assertIn('requireNotNull(decodeValue(primaryKey.getValue(column)))',src)
        self.assertNotIn('val keyArgs = primaryColumns.map { decodeValue(',src)
        self.assertNotIn('require(keyArgs.all { it != null })',src)
        self.assertIn('settingsSnapshot(db, keyArgs.single())',src)
        self.assertIn('require(primaryColumns.isNotEmpty()',src)
        self.assertIn('check(originalSettings == settingsSnapshot(',src)
        self.assertNotIn('runCatching { db.execSQL',src)
        self.assertNotIn('Log.',src)
        self.assertIn('kind = com.mikeyphw.studyflow.core.link.StudyFlowOperationKind.TOMBSTONE',self.get(TEST))
if __name__=='__main__':unittest.main(verbosity=2)
