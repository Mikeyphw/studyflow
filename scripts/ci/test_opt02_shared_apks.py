#!/usr/bin/env python3
"""OPT02 P01 immutable artifact reuse contract: offline, adversarial, no Gradle/ADB."""
from __future__ import annotations
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest import mock

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / 'scripts/ci/opt02_shared_apks.py'
spec = importlib.util.spec_from_file_location('opt02_shared_apks', SCRIPT)
opt = importlib.util.module_from_spec(spec)
spec.loader.exec_module(opt)
SHA = 'a' * 40


def run_fixture():
    return dict(id=123, run_attempt=2, head_sha=SHA,
                status='completed', conclusion='success', event='push',
                head_branch=opt.BRANCH, repository={'full_name': opt.REPO})


def bundle(root: Path, *, source: str = SHA, attempt: str = '2'):
    app, test = b'APP' * 600, b'TEST' * 600
    (root / 'app-debug.apk').write_bytes(app)
    (root / 'app-debug-androidTest.apk').write_bytes(test)
    manifest = dict(schema=1, source_sha=source, run_id='123', run_attempt=attempt,
                    app_sha256=hashlib.sha256(app).hexdigest(),
                    test_sha256=hashlib.sha256(test).hexdigest(),
                    app_certificate_sha256='b'*64, test_certificate_sha256='b'*64,
                    executed_in_hosted_emulator=False, physical_device_qualified=False,
                    release_qualified=False, publication_allowed=False)
    (root / 'SFDT22_PHYSICAL_APKS.json').write_text(json.dumps(manifest)+'\n')
    return manifest


class Opt02SharedAPKContracts(unittest.TestCase):
    def test_01_exact_successful_producer_and_cache_misses(self):
        x=run_fixture()
        self.assertEqual(opt.select_run({'workflow_runs':[x]},SHA),x)
        self.assertIsNone(opt.select_run({'workflow_runs':[]},SHA))
        self.assertIsNone(opt.select_run({'workflow_runs':[{**x,'conclusion':'failure'}]},SHA))
        self.assertIsNone(opt.select_run({'workflow_runs':[{**x,'status':'in_progress'}]},SHA))
        self.assertIsNone(opt.select_run({'workflow_runs':[x]},'c'*40))
        self.assertIsNone(opt.select_run({'workflow_runs':[{**x,'head_sha':'c'*40}]},SHA))
        # Other events and branches never qualify as a producer.
        self.assertIsNone(opt.select_run({'workflow_runs':[{**x,'event':'workflow_dispatch'}]},SHA))
        self.assertIsNone(opt.select_run({'workflow_runs':[{**x,'head_branch':'main'}]},SHA))
        self.assertEqual(opt.select_run({'workflow_runs':[{**x,'event':'workflow_dispatch'},x]},SHA),x)
        for key,value in [('repository',{'full_name':'attacker/other'}),('id',0),('run_attempt',0)]:
            with self.subTest(field=key), self.assertRaises(opt.Refused):
                opt.select_run({'workflow_runs':[{**x,key:value}]},SHA)
        with self.assertRaises(opt.Refused): opt.select_run({'workflow_runs':{}},SHA)
        with self.assertRaises(opt.Refused): opt.select_run({'workflow_runs':[]},'invalid')

    def test_02_artifact_exact_attempt_and_ambiguity(self):
        x=run_fixture();item={'id':456,'name':'sfdt22-physical-apks-123-2','expired':False}
        self.assertEqual(opt.select_artifact({'artifacts':[item]},x),item)
        self.assertIsNone(opt.select_artifact({'artifacts':[{**item,'expired':True}]},x))
        for bad in ({'artifacts':[]},{'artifacts':[item,item]},
                    {'artifacts':[{**item,'name':'sfdt22-physical-apks-123-1'}]},
                    {'artifacts':[{**item,'expired':'false'}]},
                    {'artifacts':[{'name':item['name'],'expired':False,'id':0}]}):
            with self.assertRaises(opt.Refused):opt.select_artifact(bad,x)

    def test_03_signed_bundle_hash_and_manifest_provenance(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td);manifest=bundle(root)
            with mock.patch.object(opt,'signer',return_value='b'*64):
                app,test=opt.verify_bundle(root,SHA,run_fixture(),root)
                self.assertEqual(app.name,'app-debug.apk')
                self.assertEqual(test.name,'app-debug-androidTest.apk')
                for change in ({'source_sha':'c'*40},{'run_id':'999'}, {'run_attempt':'1'},
                               {'physical_device_qualified':True}, {'release_qualified':True},
                               {'publication_allowed':True}, {'executed_in_hosted_emulator':True},
                               {'app_sha256':'d'*64}, {'app_certificate_sha256':'d'*64}):
                    with self.subTest(change=change):
                        (root/'SFDT22_PHYSICAL_APKS.json').write_text(json.dumps({**manifest,**change})+'\n')
                        with self.assertRaises(opt.Refused):opt.verify_bundle(root,SHA,run_fixture(),root)
                (root/'SFDT22_PHYSICAL_APKS.json').write_text(json.dumps(manifest))
                (root/'app-debug.apk').write_bytes(b'tampered' * 160)
                with self.assertRaises(opt.Refused):opt.verify_bundle(root,SHA,run_fixture(),root)

    def test_04_extra_file_symlink_and_signer_split_refused(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td);bundle(root)
            with mock.patch.object(opt,'signer',side_effect=['b'*64,'c'*64]):
                with self.assertRaises(opt.Refused):opt.verify_bundle(root,SHA,run_fixture(),root)
            with mock.patch.object(opt,'signer',return_value='b'*64):
                (root/'PRIVATE.txt').write_text('should not be in artifact')
                with self.assertRaises(opt.Refused):opt.verify_bundle(root,SHA,run_fixture(),root)
                (root/'PRIVATE.txt').unlink()
                app=root/'app-debug.apk';app.rename(root/'source.apk');app.symlink_to(root/'source.apk')
                with self.assertRaises(opt.Refused):opt.verify_bundle(root,SHA,run_fixture(),root)

    def test_05_deliver_copy_and_preexisting_refusal(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td);incoming=root/'incoming';incoming.mkdir();bundle(incoming)
            target=root/'repo';target.mkdir()
            opt.deliver(incoming/'app-debug.apk',incoming/'app-debug-androidTest.apk',target)
            self.assertEqual(opt.sha(target/opt.APP),opt.sha(incoming/'app-debug.apk'))
            self.assertEqual(opt.sha(target/opt.TEST),opt.sha(incoming/'app-debug-androidTest.apk'))
            with self.assertRaises(opt.Refused):
                opt.deliver(incoming/'app-debug.apk',incoming/'app-debug-androidTest.apk',target)

    def test_06_cache_miss_does_not_download_or_mutate(self):
        with tempfile.TemporaryDirectory() as td:
            with mock.patch.object(opt,'github_json',return_value={'workflow_runs':[]}) as api:
                ok,reason=opt.try_reuse(Path(td),SHA,Path(td))
            self.assertFalse(ok);self.assertEqual(reason,'NO_SUCCESSFUL_EXACT_SHA_PRODUCER')
            self.assertEqual(len(list(Path(td).iterdir())),0)
            self.assertEqual(api.call_count,1)

    def test_07_tampered_artifact_never_fallbacks(self):
        run=run_fixture()
        def api(path):
            if '/runs?' in path:return {'workflow_runs':[run]}
            return {'artifacts':[{'name':'sfdt22-physical-apks-123-2','id':456,'expired':False}]}
        def download(argv,**kwargs):
            bundle(Path(argv[-1])); (Path(argv[-1])/'app-debug.apk').write_bytes(b'bad'*500)
            return b''
        with tempfile.TemporaryDirectory() as td, \
             mock.patch.object(opt,'github_json',side_effect=api), \
             mock.patch.object(opt,'command',side_effect=download), \
             mock.patch.object(opt,'signer',return_value='b'*64):
            with self.assertRaises(opt.Refused):
                opt.try_reuse(Path(td),SHA,Path(td))
            self.assertEqual(len(list(Path(td).iterdir())),0)

    def test_08_successful_verified_apk_pair_reused_without_gradle(self):
        producer=run_fixture()
        def api(path):
            if '/runs?' in path:return {'workflow_runs':[producer]}
            return {'artifacts':[{'name':'sfdt22-physical-apks-123-2','id':456,'expired':False}]}
        def downloaded(argv,**kwargs):
            self.assertEqual(argv[:4],['gh','run','download','123'])
            bundle(Path(argv[-1]))
            return b''
        with tempfile.TemporaryDirectory() as td, \
             mock.patch.object(opt,'github_json',side_effect=api), \
             mock.patch.object(opt,'command',side_effect=downloaded), \
             mock.patch.object(opt,'signer',return_value='b'*64):
            root=Path(td)
            self.assertEqual(opt.try_reuse(root,SHA,root),
                             (True,'EXACT_SHA_SIGNED_APK_PAIR_VERIFIED'))
            self.assertTrue((root/opt.APP).is_file())
            self.assertTrue((root/opt.TEST).is_file())
            self.assertFalse((root/'app/build/outputs/apk/release/app-release.apk').exists())

    def test_09_cross_workflow_consumer_and_unchanged_mandatory_gates(self):
        wf=(ROOT/'.github/workflows/studyflow-targeted.yml').read_text()
        full=(ROOT/'.github/workflows/studyflow-sfdt22-hosted-journeys.yml').read_text()
        prod=(ROOT/'.github/workflows/studyflow-sfdt22-physical-apks.yml').read_text()
        self.assertIn('actions: read',wf)
        self.assertIn('id: shared',wf)
        self.assertIn('python3 -B scripts/ci/opt02_shared_apks.py --reuse',wf)
        self.assertIn("if: steps.shared.outputs.reused != 'true'",wf)
        self.assertIn(':app:assembleDebug :app:assembleDebugAndroidTest',wf)
        self.assertIn('needs: [scope, source]',wf)
        self.assertIn('test ! -e app/build/outputs/apk/release/app-release.apk',wf)
        self.assertIn('test "$SOURCE_RESULT" = success',full)
        self.assertIn('needs: [source, emulator]',full)
        self.assertIn("vars.STUDYFLOW_SFDT22_HOSTED_ENABLED == 'true'",full)
        self.assertIn('sfdt22-physical-apks-${{ github.run_id }}-${{ github.run_attempt }}',prod)
        self.assertIn('executed_in_hosted_emulator=False,physical_device_qualified=False',prod)
        self.assertIn('python3 -B scripts/ci/test_opt02_shared_apks.py',wf)
        self.assertIn('python3 -B scripts/ci/test_opt02_shared_apks.py',full)
        for path in ('scripts/ci/opt02_shared_apks.py','scripts/ci/test_opt02_shared_apks.py',
                     '.github/workflows/studyflow-targeted.yml',
                     'docs/actions/STUDYFLOW_OPT02_P01_SHARED_ANDROID_APKS.md'):
            digest=hashlib.sha256((ROOT/path).read_bytes()).hexdigest()+'  '+path
            self.assertEqual(full.count(digest),1,path)
        for forbidden in ('continue-on-error:', 'pull_request_target:', 'contents: write', 'pm clear'):
            self.assertNotIn(forbidden,wf)


if __name__=='__main__':unittest.main(verbosity=2)
