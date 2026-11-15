#!/usr/bin/env python3
"""HF07 fail-closed source, runtime and privacy contracts (not Android proof)."""
from __future__ import annotations
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
DEBUG = 'scripts/sfdt22_hf30_hf07_diagnostics.py'
CONTRACT = 'scripts/test_sfdt22_hf30_hf07_diagnostics_contract.py'
DOC = 'docs/actions/SFDT22_HF30_HF07_ANDROID_DIAGNOSTIC_REBUILD.md'
FULL = '.github/workflows/studyflow-sfdt22-hosted-journeys.yml'
FOCUSED = '.github/workflows/studyflow-sfdt22-hotfix-session.yml'
STAGING = '.github/workflows/studyflow-sfdt26-hotfix-push.yml'
BASH = 'scripts/sfdt22_emulator_ci.sh'
ROOM = 'core/data/src/androidTest/kotlin/com/mikeyphw/studyflow/core/data/StudySessionAtomicStateTest.kt'
HOST = 'scripts/sfdt22_emulator_verify.py'

spec = importlib.util.spec_from_file_location('hf07_diagnostics_contract_fixture', ROOT/DEBUG)
hf07 = importlib.util.module_from_spec(spec)
spec.loader.exec_module(hf07)

class Hf07DiagnosticsContract(unittest.TestCase):
    def read(self,path): return (ROOT/path).read_text(encoding='utf-8')

    def test_real_android_and_original_release_gate_still_mandatory(self):
        full = self.read(FULL)
        focus = self.read(FOCUSED)
        staging = self.read(STAGING)
        bash = self.read(BASH)
        host = self.read(HOST)
        self.assertIn('script: bash scripts/sfdt22_emulator_ci.sh', full)
        self.assertIn('api-level: 36',full)
        self.assertIn('needs: [source, emulator]',full)
        self.assertIn('test "$SOURCE_RESULT" = success',full)
        self.assertIn('test "$EMULATOR_RESULT" = success',full)
        self.assertIn('seven unique screenshots',full)
        self.assertIn('Independently verify five test receipts',full)
        self.assertIn('scripts/sfdt22_emulator_verify.py --verify ',full)
        self.assertNotIn('continue-on-error:',full)
        self.assertNotIn('continue-on-error:',focus)
        self.assertEqual(focus.count('--diagnostic-through practice'),2)
        self.assertIn('script: python3 -B scripts/sfdt22_emulator_verify.py --run --diagnostic-through practice',focus)
        self.assertIn('scripts/sfdt22_emulator_verify.py --verify-diagnostic --diagnostic-through practice',focus)
        self.assertIn('diagnostic_only":true',focus)
        self.assertIn('release_qualified":false',focus)
        self.assertIn('publication_allowed":false',focus)
        self.assertIn('script: bash scripts/sfdt22_emulator_ci.sh',full)
        self.assertIn('python3 -B scripts/sfdt22_emulator_verify.py --run',bash)
        self.assertIn('SFDT22_HF06_REAL_ROOM_REGRESSION_PASS',bash)
        self.assertIn('python3 -B scripts/sfdt22_hf30_hf07_diagnostics.py room-run',bash)
        self.assertIn('force_stopped_app_and_verified_new_pid=True',host)
        self.assertIn("'realUserSeesPersistedProgressAfterColdRestart'",host)
        self.assertIn("'sfdt22-hotfix/*-staging-*'",staging)
        self.assertNotIn("'sfdt22-hotfix/**'",staging)
        self.assertIn('expected_sha: ${{ github.sha }}',staging)
        self.assertNotIn('pull_request_target:',staging)
        self.assertNotIn('contents: write',staging)

    def test_real_room_compilation_and_execution_are_not_skipped(self):
        d=self.read(DEBUG)
        self.assertIn(":core:data:assembleDebugAndroidTest",d)
        self.assertIn(":core:data:connectedDebugAndroidTest",d)
        self.assertIn('android.testInstrumentationRunnerArguments.class=',d)
        self.assertIn('subprocess.run(cmd, cwd=repo',d)
        self.assertIn("set(record['room']['reported_tests']) != ROOM_METHODS",d)
        self.assertEqual(len(hf07.ROOM_METHODS),5)
        room=self.read(ROOM)
        for method in hf07.ROOM_METHODS:
            self.assertIn('fun '+method+'()',room)
        self.assertIn('RoomStudySessionRepository(database)',room)
        self.assertIn('Room.inMemoryDatabaseBuilder',room)

    def test_both_workflows_always_write_debug_receipt_before_artifacts(self):
        for wf, mode in ((FULL,'full'),(FOCUSED,'focused')):
            src=self.read(wf)
            self.assertIn('HF07 summarize',src)
            self.assertIn('if: always()',src)
            self.assertIn('sfdt22_hf30_hf07_diagnostics.py report --mode '+mode,src)
            self.assertLess(src.index('sfdt22_hf30_hf07_diagnostics.py report --mode '+mode),
                            src.index('actions/upload-artifact@'))
            self.assertIn('if-no-files-found: error',src)
        self.assertIn('python3 -B '+CONTRACT,self.read(FULL))
        self.assertIn('python3 -B '+CONTRACT,self.read(FOCUSED))

    def test_exact_immutable_sha_pins(self):
        full=self.read(FULL)
        for p in (DEBUG,CONTRACT,DOC,BASH):
            hash=hashlib.sha256((ROOT/p).read_bytes()).hexdigest()
            self.assertEqual(full.count(hash+'  '+p),1,p)
        p=FOCUSED
        hash=hashlib.sha256((ROOT/p).read_bytes()).hexdigest()
        self.assertEqual(full.count(hash+'  '+p),2,p)

    def test_diagnostic_sanitizes_user_data_and_failed_stage(self):
        class Args:
            source_sha='a'*40
            run_id='123'
            attempt='1'
        private={'overall':'FAILED_OR_NOT_RUN','failure_phase':'session',
                 'failure_test':'UNTRUSTED_PRIVATE_PERSON_NAME',
                 'failure_step':'session_completion_persisted',
                 'failure_reason_code':'session_finish_operation_failed',
                 'extra_private_string':'VERY_SECRET', 'completed_before_failure':2}
        r=hf07.build_summary(private,{'status':'FAILED','step':'execute_room_tests',
            'reason':'room_instrumentation_failed',
            'room':{'failing_tests':['quickSessionCompletesAndPersistsWithoutPlannedActivity',
                                   'MALICIOUS_SECRET']}},'full',Args())
        content=json.dumps(r)
        self.assertEqual(r['ui_stage'],'session_completion_persisted')
        self.assertEqual(r['ui_reason'],'session_finish_operation_failed')
        self.assertEqual(r['room_status'],'FAILED')
        self.assertEqual(r['verified_before_failure'],2)
        self.assertNotIn('VERY_SECRET',content)
        self.assertNotIn('MALICIOUS_SECRET',content)
        self.assertNotIn('UNTRUSTED_PRIVATE',content)
        self.assertFalse(r['publication_allowed'])
        self.assertFalse(r['release_qualified'])

    def test_malicious_or_unknown_values_do_not_enter_report(self):
        self.assertEqual(hf07.allow_code('PRIVATE;rm -rf /'),'unavailable')
        self.assertEqual(hf07.allow_code('foo'*50),'unavailable')
        self.assertEqual(hf07.allow_code('session',hf07.UI_PHASES),'session')
        self.assertEqual(hf07.allow_code('password',hf07.UI_PHASES),'unavailable')
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory)
            (root/'SFDT22_EVIDENCE.json').write_text(json.dumps({'outside':'SECRET'}))
            data=hf07.read_receipt(root/'SFDT22_EVIDENCE.json')
            self.assertEqual(data['outside'],'SECRET')
            (root/'SFDT22_EVIDENCE.json').write_text('x'*131073)
            with self.assertRaises(hf07.Refused):
                hf07.read_receipt(root/'SFDT22_EVIDENCE.json')

    def test_room_xml_reads_only_allowlisted_cases(self):
        with tempfile.TemporaryDirectory() as directory:
            repo=Path(directory)
            results=repo/'core/data/build/outputs/androidTest-results/connected/debug'
            results.mkdir(parents=True)
            (results/'TEST-std.xml').write_text('''<testsuite>
              <testcase classname="com.mikeyphw.studyflow.core.data.StudySessionAtomicStateTest"
                name="quickSessionCompletesAndPersistsWithoutPlannedActivity">
                <failure message="PRIVATE_UNTRUSTED">PRIVATE_UNTRUSTED</failure>
              </testcase>
              <testcase classname="com.example.Secret" name="PRIVATE_UNTRUSTED" />
            </testsuite>''')
            record=hf07.room_failures(repo)
            self.assertEqual(record['failing_tests'],['quickSessionCompletesAndPersistsWithoutPlannedActivity'])
            self.assertEqual(record['reported_tests'],['quickSessionCompletesAndPersistsWithoutPlannedActivity'])
            self.assertNotIn('PRIVATE_UNTRUSTED',json.dumps(record))

    def test_no_auto_release_or_false_pass(self):
        d=self.read(DEBUG)
        for prohibited in ('gh pr merge','git reset --hard','git push --force',
                           'continue-on-error:'):
            self.assertNotIn(prohibited,d)
        self.assertIn('source_sha_mismatch',d)
        self.assertIn('physical_device_refused',d)
        self.assertIn('api36_required',d)
        self.assertIn('GITHUB_SHA',d)
        self.assertIn('GITHUB_STEP_SUMMARY',d)
        self.assertIn('raw_logs_retained=False',d)
        self.assertIn('release_qualified=False',d)
        self.assertIn('publication_allowed=False',d)

if __name__=='__main__': unittest.main(verbosity=2)
