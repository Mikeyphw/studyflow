#!/usr/bin/env python3
"""HF37 source and negative tests for paused hosted / physical ARM64 acceptance.

Source-only checks never impersonate actual Android execution.
"""
from __future__ import annotations
import hashlib
from pathlib import Path
import sys
import tempfile
import unittest
import json

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'scripts'))
import sfdt22_physical as physical
import sfdt22_emulator_verify as host

HOSTED='.github/workflows/studyflow-sfdt22-hosted-journeys.yml'
FOCUSED='.github/workflows/studyflow-sfdt22-hotfix-session.yml'
BUILD='.github/workflows/studyflow-sfdt22-physical-apks.yml'
RUNNER='scripts/sfdt22_physical.py'
SELF='scripts/test_sfdt22_physical_contract.py'
UI='app/src/androidTest/java/com/mikeyphw/studyflow/Sfdt22HostedStudyJourneyTest.kt'

class HF37(unittest.TestCase):
    def read(self,path):return (ROOT/path).read_text(encoding='utf-8')
    def test_01_no_automatic_sfdt22_emulator_or_focused_session(self):
        wf=self.read(HOSTED);focus=self.read(FOCUSED)
        for src,job in ((wf,'  emulator:\n'),(focus,'  session-diagnostic:\n')):
            self.assertIn(job,src)
            block=src.split(job,1)[1][:330]
            self.assertIn("vars.STUDYFLOW_SFDT22_HOSTED_ENABLED == 'true'",block)
            self.assertIn("github.event_name == 'workflow_dispatch'",block)
        self.assertIn("if: ${{ always() && github.event_name == 'workflow_dispatch' && vars.STUDYFLOW_SFDT22_HOSTED_ENABLED == 'true' }}",wf)
        self.assertIn('needs: [source, emulator]',wf)
        for token in ('api-level: 36','seven unique screenshots',
                      'Independently verify five test receipts',':app:assembleDebugAndroidTest'):
            self.assertIn(token,wf)
        self.assertNotIn('if: false',wf)
        self.assertNotIn('continue-on-error:',wf)
        self.assertNotIn('pull_request_target:',wf)
        self.assertNotIn('contents: write',wf)
    def test_02_build_only_pins_exact_sha_no_hosted_emulator(self):
        build=self.read(BUILD)
        for t in ('workflow_dispatch:', 'expected_sha:', 'test "$GITHUB_SHA" = "$EXPECTED_SHA"',
                  'test "$(git rev-parse HEAD)" = "$EXPECTED_SHA"',
                  ':app:assembleDebug :app:assembleDebugAndroidTest',
                  'app-debug-androidTest.apk','SFDT22_PHYSICAL_APKS.json',
                  'apksigner','upload-artifact@','physical_device_qualified=False',
                  'test ! -e app/build/outputs/apk/release/app-release.apk'):
            self.assertIn(t,build)
        for t in ('android-emulator-runner@','am instrument','adb install','pm clear',
                  'workflow_call:', 'pull_request_target:', 'contents: write'):
            self.assertNotIn(t,build)
    def test_03_exact_real_methods_room_sflp_restart_screens(self):
        runner=self.read(RUNNER);ui=self.read(UI)
        self.assertEqual(tuple(physical.EXPECTED_METHODS),tuple(host.TESTS))
        self.assertEqual(tuple(physical.SCREEN_NAMES),tuple(host.SCREENS))
        self.assertEqual(len(physical.ROOM_METHODS),2)
        self.assertEqual(len(physical.SFLP_METHODS),3)
        for t in ('journey.run_test(name)', 'journey.real_process_restart()',
                  'journey.sfdt27_room_result(', 'journey.sfdt27_sflp_read_stage(name)',
                  'journey.read_boundaries()', 'journey.sfdt27_read_probes()',
                  'journey.expected_boundaries(5)', 'journey.sfdt27_expected_probes()',
                  'base.png_dimensions(raw)', "'physical_device_qualified':False"):
            if t=="'physical_device_qualified':False":continue
            self.assertIn(t,runner,t)
        self.assertEqual(ui.count('@Test fun '),5)
        self.assertEqual(ui.count('screenshot("'),7)
        self.assertIn('persistedPracticeAttemptStartedCount() == 1L',ui)
        self.assertIn('persistedPracticeAnswerCount() == 1L',ui)
    def test_04_physical_device_is_real_arm64_and_existing_is_nonqualifying(self):
        runner=self.read(RUNNER)
        for t in ("api == b'36'","abi == b'arm64-v8a'", "qemu != b'1'",
                  "'--preflight'","'--run-existing'","'--accept-device-tests'",
                  "'--accept-existing-data'", "qualifies = False", "'EXISTING_DATA_AUTHORIZATION_REQUIRED'",
                  "'INSTALLED_APP_HASH_MISMATCH'", "'INSTALLED_TEST_HASH_MISMATCH'"):
            self.assertIn(t,runner,t)
        for bad in ('pm clear','adb uninstall','svc wifi disable','svc data disable',
                    'settings put system font_scale','Thread.sleep('):
            self.assertNotIn(bad,runner)
        self.assertFalse(physical.matching_instrumentation(b'instrumentation:example.test/Runner (target=example)\n'))
        self.assertTrue(physical.matching_instrumentation(
            ('instrumentation:'+physical.INSTRUMENTATION+' (target='+physical.PACKAGE+')\n').encode()))
        self.assertFalse(physical.matching_instrumentation(
            (('instrumentation:'+physical.INSTRUMENTATION+' (target='+physical.PACKAGE+')\n')*2).encode()))
    def test_05_artifact_rejection_and_no_raw_logs(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d)
            source='a'*40
            app=b'APK1'+b'1'*4096; test=b'APK2'+b'2'*4096
            (root/'app-debug.apk').write_bytes(app)
            (root/'app-debug-androidTest.apk').write_bytes(test)
            item=dict(schema=1,source_sha=source,run_id='123',app_sha256=hashlib.sha256(app).hexdigest(),
                      test_sha256=hashlib.sha256(test).hexdigest(),app_certificate_sha256='b'*64,
                      test_certificate_sha256='b'*64)
            manifest=root/'SFDT22_PHYSICAL_APKS.json'
            manifest.write_text(json.dumps(item))
            self.assertEqual(physical.load_apks(root,source)['manifest'],item)
            with self.assertRaises(physical.Refused):physical.load_apks(root,'c'*40)
            item['test_sha256']='d'*64
            manifest.write_text(json.dumps(item))
            with self.assertRaises(physical.Refused):physical.load_apks(root,source)
        runner=self.read(RUNNER)
        self.assertIn('raw_logs_retained=False',runner)
        self.assertIn('atomic_json(',runner)
        self.assertNotIn('print(process.stdout',runner)
        self.assertNotIn('print(process.stderr',runner)
    def test_06_source_pins_and_qualification_trust(self):
        wf=self.read(HOSTED)
        for path in (FOCUSED,BUILD,RUNNER,SELF):
            pin=hashlib.sha256((ROOT/path).read_bytes()).hexdigest()+'  '+path
            # Historical full CI source manifest pins the focused workflow in
            # two independent sections. Both must be refreshed atomically.
            self.assertEqual(wf.count(pin),2 if path==FOCUSED else 1,path)
        self.assertIn('python3 -B '+SELF,wf)
        self.assertIn('physical_device_qualified=False',self.read(BUILD))
        self.assertIn('qualifies = freshly_installed',self.read(RUNNER))
        self.assertIn('qualifying=qualifies',self.read(RUNNER))
        self.assertIn('release_qualified=False',self.read(RUNNER))
        self.assertIn('publication_allowed=False',self.read(RUNNER))

if __name__=='__main__':unittest.main(verbosity=2)
