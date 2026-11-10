#!/usr/bin/env python3
"""HF03 source and hostile receipt contracts: no runtime credit from synthetic tests."""
from __future__ import annotations
import hashlib,sys,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'scripts'))
import sfdt22_emulator_verify as host

UI='app/src/androidTest/java/com/mikeyphw/studyflow/Sfdt27SflpReplayRegressionTest.kt'
FACTORY='core/data/src/debug/kotlin/com/mikeyphw/studyflow/core/data/Sfdt27SflpDebugBridgeFactory.kt'
RUNNER='scripts/sfdt22_emulator_verify.py'
FIX03='scripts/test_sfdt27_fix03_contract.py'
HF02='scripts/test_sfdt27_fix03_hf02_contract.py'
THIS='scripts/test_sfdt27_fix03_hf03_contract.py'
DOC='docs/actions/SFDT22_HF27_FIX03_HF03_ISOLATED_SFLP_ACCEPTANCE.md'
FULL='.github/workflows/studyflow-sfdt22-hosted-journeys.yml'
FOCUSED='.github/workflows/studyflow-sfdt22-hotfix-session.yml'

class HardenedSflpAcceptance(unittest.TestCase):
    def read(self,path): return (ROOT/path).read_text()
    def test_01_real_production_replay_with_disposable_database_and_one_handle(self):
        test=self.read(UI)
        self.assertIn('Room.databaseBuilder(',test)
        self.assertIn('context, StudyFlowDatabase::class.java, isolatedDatabaseName',test)
        self.assertIn('StudyFlowIntegrity.install(db)',test)
        self.assertIn('check(context.deleteDatabase(isolatedDatabaseName)',test)
        self.assertIn('database = openIsolatedDatabase()',test)
        self.assertIn('createSfdt27DebugSflpReplayBridge(database)',test)
        self.assertNotIn('createStudyFlowDataLayer(context',test)
        self.assertNotIn('database = StudyFlowDatabase.create(context)',test)
        self.assertNotIn('StudyFlowDatabase.DATABASE_NAME',test)
        self.assertIn('database.close()',test)
        self.assertEqual(test.count('@Test fun '),3)
        self.assertEqual(test.count('recordStage(stage, "FAIL")'),5)
        self.assertIn('SFLP_DELETE_DB',test)
        factory=self.read(FACTORY)
        self.assertIn('RoomStudyFlowSflpProductStateBridge(database)',factory)
        self.assertIn('fun createSfdt27DebugSflpReplayBridge(',factory)
        self.assertNotIn('StudyFlowDatabase.create(',factory)
        self.assertNotIn('insert(',factory)
        self.assertFalse((ROOT/'core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/Sfdt27SflpDebugBridgeFactory.kt').exists())
        self.assertIn('UPDATE ${quote(table)} SET $assigns WHERE $where',
                      self.read('core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/RoomStudyFlowSflpProductStateBridge.kt'))

    def test_02_two_independent_proofs_required_for_every_real_android_test(self):
        s=self.read(RUNNER)
        self.assertIn('sfdt27_sflp_clear_stage()',s)
        self.assertIn('sfdt27_sflp_read_stage(test)',s)
        self.assertIn("if reason == 'passed':",s)
        self.assertIn("if status != 'complete':",s)
        self.assertIn('Sfdt27RoomFailure(test, code)',s)
        self.assertIn('sfdt27_room_result(process.stdout, test, process.returncode)',s)
        self.assertIn('reason = sfdt27_room_result(',s)
        self.assertIn('SFDT27_SFLP_STAGE_FILE',s)
        self.assertNotIn('print(process.stdout)',s)
        self.assertNotIn('print(result.stdout)',s)
        self.assertEqual(len(host.SFDT27_SFLP_TESTS),3)
        self.assertEqual(len(host.SFDT27_ROOM_TESTS),2)
        self.assertEqual(len(host.TESTS),5)
        self.assertEqual(len(host.SCREENS),7)
        self.assertIn('raw_logs_retained=False',s)

    def test_03_stage_parser_accepts_only_exact_bounded_private_receipts(self):
        test=host.SFDT27_SFLP_TESTS[0]
        a=f'{test}|SFLP_SETUP|ENTER\n'.encode()
        b=f'{test}|SFLP_BRIDGE|ENTER\n'.encode()
        c=f'{test}|SFLP_COMPLETE|PASS\n'.encode()
        good=b'SFLP_V1\n'+a+b+c
        self.assertEqual(host.sfdt27_sflp_parse_stage(good,test),('complete','SFLP_COMPLETE'))
        self.assertEqual(host.sfdt27_sflp_parse_stage(b'SFLP_V1\n'+a+b,test),('incomplete','SFLP_BRIDGE'))
        failed=b'SFLP_V1\n'+a+b+f'{test}|SFLP_BRIDGE|FAIL\n'.encode()
        self.assertEqual(host.sfdt27_sflp_parse_stage(failed,test),('failed','SFLP_BRIDGE'))
        for bad in [good+b'extra',good.replace(b'PASS',b'UNKNOWN'),good.replace(test.encode(),b'other'),
                    good.replace(b'SFLP_BRIDGE',b'PRIVATE_USER_NAME'),good+c,
                    good.replace(b'SFLP_V1',b'SFLP_V0'),good[:-1],good+b'\x00',
                    good.replace(b'SFLP_SETUP|ENTER',b'SFLP_SETUP|PASS'),
                    b'\xff'+good,good+b'x'*3000,b'SFLP_V1\n'+a*38]:
            self.assertEqual(host.sfdt27_sflp_parse_stage(bad,test)[0],'invalid')
        # A synthetic device receipt must NEVER replace Android's real JUnit result.
        junit=(f'INSTRUMENTATION_STATUS: test={test}\nINSTRUMENTATION_STATUS_CODE: 1\n'
               f'INSTRUMENTATION_STATUS: test={test}\nINSTRUMENTATION_STATUS_CODE: -2\n'
               'INSTRUMENTATION_CODE: -1\n').encode()
        self.assertNotEqual(host.sfdt27_room_result(junit,test,0),'passed')

    def test_04_invalid_or_ambiguous_evidence_always_fails_closed(self):
        s=self.read(RUNNER)
        for reason in ('runner_stage_receipt_unavailable','runner_stage_receipt_invalid',
                       'runner_stage_receipt_incomplete','runner_stage_receipt_reset_failed'):
            self.assertIn(reason,host.SFDT27_ROOM_CODES)
        for marker in ('capture_output=True',"'exec-out', 'run-as', PACKAGE", "'rm', '-f', SFDT27_SFLP_STAGE_FILE"):
            self.assertIn(marker,s)
        test=self.read(UI)
        self.assertIn('context.openFileOutput("sfdt27-sflp-stage-v1.txt", mode)',test)
        self.assertIn('require(method in allowedTests)',test)
        self.assertIn('require(stage in allowedStages',test)
        self.assertIn('recordStage("SFLP_COMPLETE", "PASS")',test)
        for forbidden in ('Log.d(', 'println(', 'System.out.println(', 'rawException'):
            self.assertNotIn(forbidden,test)
        self.assertNotIn('continue-on-error:',self.read(FULL)+self.read(FOCUSED))

    def test_05_stale_contracts_pins_and_original_full_gate_preserved(self):
        old=self.read(FIX03);hf02=self.read(HF02)
        self.assertIn('createSfdt27DebugSflpReplayBridge(database)',old+hf02)
        self.assertNotIn('createStudyFlowDataLayer(context',old+hf02)
        full=self.read(FULL);focused=self.read(FOCUSED)
        for p in (UI,RUNNER,FIX03,HF02,THIS,DOC,FACTORY,FOCUSED):
            self.assertIn(hashlib.sha256((ROOT/p).read_bytes()).hexdigest()+'  '+p,full,p)
        for wf in (full,focused):
            self.assertIn('python3 -B scripts/test_sfdt27_fix03_contract.py',wf)
            self.assertIn('python3 -B scripts/test_sfdt27_fix03_hf02_contract.py',wf)
            self.assertIn('python3 -B scripts/test_sfdt27_fix03_hf03_contract.py',wf)
        for phrase in ('needs: [source, emulator]','seven unique screenshots','api-level: 36'):
            self.assertIn(phrase,full)
        self.assertNotIn('if: false',full)
        self.assertNotIn('continue-on-error:',full+focused)
        self.assertIn('nonqualifying',self.read(DOC))

if __name__=='__main__':unittest.main(verbosity=2)
