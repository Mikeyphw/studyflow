#!/usr/bin/env python3
"""HF38 physical ARM64 automatic artifacts / additive installer / hosted-pause contracts.

Offline fail-closed source checks; hosted and physical runtime remain independent.
"""
from __future__ import annotations
import hashlib
import json
import tempfile
from pathlib import Path
import sys
from unittest import mock
import unittest

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'scripts'))
import sfdt22_physical as p
import sfdt22_emulator_verify as host
WF='.github/workflows/studyflow-sfdt22-hosted-journeys.yml'
BUILD='.github/workflows/studyflow-sfdt22-physical-apks.yml'
DOC='docs/actions/SFDT22_HF37_PHYSICAL_ARM64_HANDOFF.md'
SELF='scripts/test_sfdt22_hf38_contract.py'
RUNNER='scripts/sfdt22_physical.py'

class HF38Contracts(unittest.TestCase):
    def read(self,path):return (ROOT/path).read_text(encoding='utf-8')
    def test_01_one_consolidated_paused_hosted_full_real_gates(self):
        wf=self.read(WF)
        for required in ("vars.STUDYFLOW_SFDT22_HOSTED_ENABLED == 'true'",
             "github.event_name == 'workflow_dispatch'",'needs: [source, emulator]',
             'seven unique screenshots',':app:assembleDebugAndroidTest',
             'Independently verify five test receipts',
             'python3 -B scripts/test_sfdt22_hf36_start_delivery_contract.py',
             'python3 -B scripts/test_sfdt22_physical_contract.py',
             'python3 -B '+SELF):
            self.assertIn(required,wf)
        for forbidden in ('continue-on-error:', 'if: false','pull_request_target:',
                          'contents: write','workflow_run:'):
            self.assertNotIn(forbidden,wf)
        runner=self.read(RUNNER)
        self.assertEqual(p.EXPECTED_METHODS,tuple(host.TESTS))
        self.assertEqual(len(p.ROOM_METHODS),2)
        self.assertEqual(len(p.SFLP_METHODS),3)
        self.assertEqual(len(p.SCREEN_NAMES),7)
        self.assertIn('journey.real_process_restart()',runner)
        self.assertIn('journey.run_test(name)',runner)
        self.assertIn('journey.read_boundaries()',runner)
        self.assertIn('journey.sfdt27_read_probes()',runner)

    def test_02_exact_source_github_build_only_and_doc(self):
        build=self.read(BUILD)
        for marker in ('workflow_dispatch:', 'expected_sha:',
             'test "$GITHUB_SHA" = "$EXPECTED_SHA"',
             'test "$(git rev-parse HEAD)" = "$EXPECTED_SHA"',
             ':app:assembleDebug :app:assembleDebugAndroidTest',
             'SFDT22_PHYSICAL_APKS.json','apksigner',
             'python3 -B '+SELF):
            self.assertIn(marker,build)
        for forbidden in ('android-emulator-runner@','am instrument','adb install','pm clear'):
            self.assertNotIn(forbidden,build)
        doc=self.read(DOC)
        for marker in ('automatic branch switching','automatic instrumentation',
                       'localhost:5555','--accept-device-tests','--accept-existing-data',
                       'git fetch','PHYSICAL_PASS','DIAGNOSTIC_ONLY'):
            self.assertIn(marker.lower(),doc.lower())
        hosted=self.read(WF)
        for path in (RUNNER,BUILD,DOC,SELF):
            digest=hashlib.sha256((ROOT/path).read_bytes()).hexdigest()+'  '+path
            self.assertEqual(hosted.count(digest),1,path)

    def test_03_never_overwrite_unreviewed_installation(self):
        manifest={'app_sha256':'a'*64,'test_sha256':'b'*64}
        apks={'app':Path('/tmp/fake-app.apk'),'test':Path('/tmp/fake-test.apk'),
              'manifest':manifest}
        with mock.patch.object(p,'adb') as adb, mock.patch.object(p,'verify_installed') as verify,\
             mock.patch.object(p,'installed_apk_sha',return_value='a'*64),\
             mock.patch.object(p,'report'):
            self.assertTrue(p.maybe_install('localhost:5555',
                   {'app_installed':False,'test_installed':False},apks,allow_install=True))
            self.assertEqual(adb.call_count,2)
            adb.reset_mock(); verify.reset_mock()
            self.assertFalse(p.maybe_install('localhost:5555',
                   {'app_installed':True,'test_installed':False},apks,allow_install=True))
            adb.assert_called_once_with('localhost:5555','install','-t',str(apks['test']),timeout=160)
            verify.assert_called_once()
        with mock.patch.object(p,'installed_apk_sha',return_value='f'*64),\
             mock.patch.object(p,'adb') as adb:
            with self.assertRaisesRegex(p.Refused,'INSTALLED_APP_HASH_MISMATCH_NO_REPLACEMENT'):
                p.maybe_install('localhost:5555',
                     {'app_installed':True,'test_installed':False},apks,allow_install=True)
            adb.assert_not_called()
        with mock.patch.object(p,'adb') as adb:
            with self.assertRaisesRegex(p.Refused,'ORPHAN_INSTRUMENTATION_REFUSED_NO_REPLACEMENT'):
                p.maybe_install('localhost:5555',
                     {'app_installed':False,'test_installed':True},apks,allow_install=True)
            adb.assert_not_called()

    def test_04_find_successful_exact_head_only(self):
        sha='a'*40
        data=[{'headSha':'b'*40,'databaseId':13,'status':'completed','conclusion':'success'},
              {'headSha':sha,'databaseId':15,'status':'completed','conclusion':'failure'},
              {'headSha':sha,'databaseId':21,'status':'completed','conclusion':'success','createdAt':'2026-11-14T23:00:00Z'}]
        with mock.patch.object(p,'gh_json',return_value=data) as cli:
            self.assertEqual(p.discover_exact_build_run(sha),21)
            self.assertIn('--workflow',cli.call_args.args)
        with mock.patch.object(p,'gh_json',return_value=data[:2]):
            with self.assertRaisesRegex(p.Refused,'EXACT_SOURCE_APK_BUILD_FAILED'):
                p.discover_exact_build_run(sha)
        with self.assertRaisesRegex(p.Refused,'INVALID_SOURCE_SHA'):
            p.discover_exact_build_run('malformed')

    def test_05_persisted_data_flag_before_test_install_and_no_release_claim(self):
        args=mock.Mock(serial='localhost:5555',accept_existing_data=False)
        info={'app_installed':True,'test_installed':False}
        apks={'manifest':{'app_sha256':'a'*64,'test_sha256':'b'*64}}
        with mock.patch.object(p,'maybe_install') as installer:
            with self.assertRaisesRegex(p.Refused,'EXISTING_DATA_AUTHORIZATION_REQUIRED'):
                p.run_with_apks(args,info,apks,'a'*40)
            installer.assert_not_called()
        with mock.patch.object(p,'maybe_install',return_value=True),\
             mock.patch.object(p,'run_suite',return_value=0) as suite:
            args.accept_existing_data=True
            self.assertEqual(p.run_with_apks(args,info,apks,'a'*40),0)
            self.assertTrue(suite.call_args.args[-1])
        runner=self.read(RUNNER)
        for forbidden in ('pm clear','adb uninstall','svc wifi disable',
                          'svc data disable','settings put system font_scale',
                          'print(process.stdout','print(process.stderr'):
            self.assertNotIn(forbidden,runner)
        self.assertIn('release_qualified=False',runner)
        self.assertIn('publication_allowed=False',runner)

    def test_06_exact_matching_instrumentation_and_provenance(self):
        self.assertTrue(p.matching_instrumentation((
            'instrumentation:'+p.INSTRUMENTATION+' (target='+p.PACKAGE+')\n').encode()))
        self.assertFalse(p.matching_instrumentation((
            'instrumentation:another.test/Runner (target=another)\n').encode()))
        self.assertEqual(p.GITHUB_REPO,'Mikeyphw/studyflow')
        self.assertEqual(p.SOURCE_BRANCH,'sfdt22-hotfix/hf30-hf04-pr-20261110')
        self.assertEqual(p.BUILD_WORKFLOW,'studyflow-sfdt22-physical-apks.yml')
        runner=self.read(RUNNER)
        self.assertIn("'--apk-dir'",runner)
        self.assertIn('download_exact_apks(repo,source,Path(temporary))',runner)
        self.assertIn('qualifies = freshly_installed',runner)
        self.assertIn('raw_logs_retained=False',runner)

    def test_07_actual_download_manifest_run_binding_and_digest(self):
        source='a'*40; run_id=75; attempt=2
        app=b'APP'+b'a'*4096; test=b'TEST'+b'b'*4096
        with tempfile.TemporaryDirectory(prefix='sfdt38-download-test-') as tmp:
            root=Path(tmp)
            artifact={'name':'sfdt22-physical-apks-75-2','expired':False}
            manifest={'schema':1,'source_sha':source,'run_id':str(run_id),
                      'run_attempt':str(attempt),
                      'app_sha256':hashlib.sha256(app).hexdigest(),
                      'test_sha256':hashlib.sha256(test).hexdigest(),
                      'app_certificate_sha256':'b'*64,'test_certificate_sha256':'b'*64}
            def fake_download(cmd, **kwargs):
                self.assertEqual(cmd[:4],['gh','run','download','75'])
                (root/'app-debug.apk').write_bytes(app)
                (root/'app-debug-androidTest.apk').write_bytes(test)
                (root/'SFDT22_PHYSICAL_APKS.json').write_text(json.dumps(manifest))
                return mock.Mock(returncode=0)
            with mock.patch.object(p,'discover_exact_build_run',return_value=run_id),\
                 mock.patch.object(p,'gh_json',return_value={'artifacts':[artifact]}),\
                 mock.patch.object(p.subprocess,'run',side_effect=fake_download):
                bundle=p.download_exact_apks(ROOT,source,root)
                self.assertEqual(bundle['manifest']['run_id'],'75')
                self.assertEqual(bundle['manifest']['run_attempt'],'2')
            manifest['run_id']='76'
            (root/'SFDT22_PHYSICAL_APKS.json').write_text(json.dumps(manifest))
            # Malicious/stale manifests cannot use a successful run's name alone.
            with mock.patch.object(p,'discover_exact_build_run',return_value=run_id),\
                 mock.patch.object(p,'gh_json',return_value={'artifacts':[artifact]}),\
                 mock.patch.object(p.subprocess,'run',return_value=mock.Mock(returncode=0)):
                with self.assertRaisesRegex(p.Refused,'ARTIFACT_RUN_ATTEMPT_MISMATCH'):
                    p.download_exact_apks(ROOT,source,root)

    def test_08_no_gradle_and_automatic_run_without_apk_dir(self):
        args=mock.Mock(serial='localhost:5555',preflight=False,run_existing=False,
                       accept_device_tests=True,apk_dir=None)
        info={'app_installed':False,'test_installed':False,'instrumentation_ready':False}
        with mock.patch.object(p,'repo_source',return_value='a'*40),\
             mock.patch.object(p,'check_physical',return_value=info),\
             mock.patch.object(p,'download_exact_apks',return_value={'manifest':{}}) as download,\
             mock.patch.object(p,'run_with_apks',return_value=0) as suite,\
             mock.patch.object(p,'report'):
            self.assertEqual(p.run(args),0)
            download.assert_called_once()
            suite.assert_called_once()
        runner=self.read(RUNNER)
        self.assertNotIn('./gradlew',runner)
        self.assertNotIn('gradle build',runner)

    def test_09_dual_focused_sha_pins_are_identical_and_source_bound(self):
        focused='.github/workflows/studyflow-sfdt22-hotfix-session.yml'
        source=self.read(focused)
        wf=self.read(WF)
        digest=hashlib.sha256(source.encode('utf-8')).hexdigest()
        target=f'{digest}  {focused}'
        self.assertEqual(wf.count(target),2,'Both historical manifest sections must be pinned')
        # The workflow must not have a second, stale hash for the same path.
        import re
        pins=re.findall(r'(?m)^[^\n]*?([0-9a-f]{64})  '+
                        re.escape(focused)+r'[^\n]*$',wf)
        self.assertEqual(pins,[digest,digest])
        self.assertIn("vars.STUDYFLOW_SFDT22_HOSTED_ENABLED == 'true'",source)

if __name__=='__main__':unittest.main(verbosity=2)
