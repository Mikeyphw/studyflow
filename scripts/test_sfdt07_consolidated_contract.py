#!/usr/bin/env python3
"""SFDT07 source-only adversarial tests, no Gradle/device/network."""
from __future__ import annotations

import copy
import io
import zipfile
import sfdt07_member_evidence as V
import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0,str(Path(__file__).resolve().parent))
import sfdt07_consolidated_contract as C
import sfdt07_native_lane as N
import sfdt07_compare_runs as M


def fake_cfg(root: Path) -> dict:
    targets={t:{'root':'.' if t!='studyflow_web' else 'web',
                'runner':'node' if t=='studyflow_web' else 'android' if t.startswith('studyflow_') and ('android' in t or 'tv' in t) else 'gradle',
                'affected_ownership':'inputs-only', 'jobs':{},'workflows':{}}
             for t in C.TARGETS}
    targets['studyflow_android']['workflows'].update({'ci-source':[], 'ci-preflight':[]})
    for lane,(target,job,wf,tasks,out) in C.ANDROID.items():
        targets[target]['jobs'][job]={
            'runner':'android','gradle_tasks':list(tasks),'outputs':[out],
            'metadata':{'ci_toolchain':'android-gradle','ci_android_java_version':'21',
                        'ci_android_host_profile':'studyflow-ubuntu24',
                        'ci_android_ndk_version':'29.0.14206865',
                        'ci_android_debug_variant':'debug',
                        'ci_android_sdk_packages':list(C.SDK),
                        'ci_android_wrapper_jar_sha256':C.WRAPPER_SHA,
                        'ci_android_expected_output':out}}
        targets[target]['workflows'][wf]=[{'id':lane,'ref':'job:'+job,'depends_on':[]}]
    for lane,(target,job,wf,script) in C.PYTHON.items():
        targets[target]['jobs'][job]={
            'runner':'command','command':['python3',script],
            'metadata':{'ci_toolchain':'python-shell','ci_python_version':'3.12',
                        'ci_python_script_sha256':hashlib.sha256((root/script).read_bytes()).hexdigest(),
                        'ci_python_script_args':[],'read_only':True}}
        targets[target]['workflows'][wf]=[{'id':lane,'ref':'job:'+job,'depends_on':[]}]
    return {'targets':targets,'wrapper':{'default_command':'ci-source',
           'commands':{'ci-source':{},'ci-preflight':{}}}}


def sample_runs():
    sha='a'*40
    def run(kind,rid, event='workflow_dispatch'):
        return {'id':rid,'run_attempt':1,'head_sha':sha,'status':'completed',
                'conclusion':'success','event':event,'path':M.PATHS[kind],
                'repository':{'full_name':'Mikeyphw/studyflow'}}
    runs={k:run(k,i, 'push' if k=='legacy' else 'workflow_dispatch')
          for i,k in enumerate(M.PATHS,1)}
    def job(name,kind):return {'name':name,'run_id':runs[kind]['id'],
                                'status':'completed','conclusion':'success'}
    jobs={
        'legacy': {'total_count':5,'jobs':[job(n,'legacy') for n in
          ['Actions integration source contract','web','desktop',
           'android (app, :app:testReleaseUnitTest)',
           'android (app-tv, :app-tv:testDebugUnitTest)']]},
        'source': {'total_count':1,'jobs':[job('native-source-shadow','source')]},
        'expanded': {'total_count':4,'jobs':[job(n,'expanded') for n in sorted(M.LANES)]}}
    arts={'total_count':4,'artifacts':[]}
    for lane in sorted(M.LANES):
        short=lane.removeprefix('native-')
        arts['artifacts'].append({'id':len(arts['artifacts'])+11,
          'name':f'studyflow-sfdt07-{short}-{runs["expanded"]["id"]}-1',
          'digest':'sha256:'+'b'*64,'size_in_bytes':20,'expired':False})
    return runs,jobs,arts


class SourceContractTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory()
        self.root=Path(self.temp.name)
        for _,(_,_,_,script) in C.PYTHON.items():
            src=Path(__file__).resolve().parent/script.rsplit('/',1)[1]
            dst=self.root/script
            dst.parent.mkdir(parents=True,exist_ok=True)
            dst.write_bytes(src.read_bytes())
        self.cfg=fake_cfg(self.root)
        self.workflow=(Path(__file__).resolve().parents[1]/C.WORKFLOW).read_text()

    def tearDown(self):self.temp.cleanup()

    def test_consolidated_four_lanes_and_exact_host_profiles(self):
        self.assertEqual(len(C.check_config(self.cfg,self.root)),4)
        C.check_workflow(self.workflow)

    def test_missing_precise_ownership_refused(self):
        self.cfg['targets']['studyflow_web'].pop('affected_ownership')
        with self.assertRaisesRegex(C.ContractError,'ownership'):C.check_config(self.cfg,self.root)

    def test_desktop_target_removed_refused(self):
        self.cfg['targets'].pop('studyflow_desktop')
        with self.assertRaisesRegex(C.ContractError,'target'):C.check_config(self.cfg,self.root)

    def test_source_wrapper_substituted_refused(self):
        self.cfg['wrapper']['default_command']='ci-native-phone'
        with self.assertRaisesRegex(C.ContractError,'wrapper'):C.check_config(self.cfg,self.root)

    def test_android_release_task_injection_refused(self):
        self.cfg['targets']['studyflow_android']['jobs']['sfdt07-phone-debug']['gradle_tasks'].append(':app:assembleRelease')
        with self.assertRaisesRegex(C.ContractError,'task'):C.check_config(self.cfg,self.root)

    def test_android_device_or_signer_injection_refused(self):
        self.cfg['targets']['studyflow_tv']['jobs']['sfdt07-tv-debug']['metadata']['signing_required']=True
        with self.assertRaisesRegex(C.ContractError,'toolchain'):C.check_config(self.cfg,self.root)

    def test_android_wrong_ndk_refused(self):
        self.cfg['targets']['studyflow_tv']['jobs']['sfdt07-tv-debug']['metadata']['ci_android_ndk_version']='28.0.0'
        with self.assertRaisesRegex(C.ContractError,'toolchain'):C.check_config(self.cfg,self.root)

    def test_android_wrong_apk_output_refused(self):
        self.cfg['targets']['studyflow_android']['jobs']['sfdt07-phone-debug']['outputs']=['dist/app.apk']
        with self.assertRaisesRegex(C.ContractError,'task'):C.check_config(self.cfg,self.root)

    def test_python_script_tamper_refused(self):
        p=self.root/'scripts/sfdt07_web_pytest.py'
        p.write_text(p.read_text()+'\n# tamper\n')
        with self.assertRaisesRegex(C.ContractError,'checksum'):C.check_config(self.cfg,self.root)

    def test_python_shell_injection_refused(self):
        self.cfg['targets']['studyflow_quality']['jobs']['sfdt07-quality-source']['command']=['sh','-c','exit 0']
        with self.assertRaisesRegex(C.ContractError,'argv'):C.check_config(self.cfg,self.root)

    def test_workflow_push_refused(self):
        with self.assertRaisesRegex(C.ContractError,'manual'):
            C.check_workflow(self.workflow+'\n  push:\n')

    def test_workflow_write_permissions_refused(self):
        with self.assertRaisesRegex(C.ContractError,'permissions'):
            C.check_workflow(self.workflow.replace('contents: read','contents: write'))

    def test_workflow_signing_secrets_refused(self):
        with self.assertRaisesRegex(C.ContractError,'permissions'):
            C.check_workflow(self.workflow+'\n# secrets.MY_PRIVATE_SIGNER\n')

    def test_workflow_missing_tv_lane_refused(self):
        with self.assertRaisesRegex(C.ContractError,'lane'):
            C.check_workflow(self.workflow.replace('lane: tv-debug\n','lane: removed\n'))

    def test_local_native_identity_refused(self):
        with self.assertRaisesRegex(N.NativeRefusal,'GitHub-hosted'):
            N.identity({},self.root)

    def test_native_provider_swap_refused(self):
        row={'job':'sfdt07-tv-debug','provider':'python-shell','runner':'ubuntu-24.04',
             'needs':[],'handoffs':[]}
        with self.assertRaisesRegex(N.NativeRefusal,'provider'):
            N.select_lane({'jobs':[row]},'tv-debug')

    def test_junit_real_pass_counts_and_digest(self):
        d=self.root/'sfdt07';d.mkdir()
        (d/'web-pytest-junit.xml').write_text('<testsuites><testsuite tests="3" failures="0" errors="0" skipped="1"><testcase name="a"/><testcase name="b"/><testcase name="c"><skipped/></testcase></testsuite></testsuites>')
        doc=N.junit(d)
        self.assertEqual(doc['tests'],3)
        self.assertEqual(doc['skipped'],1)

    def test_junit_declared_tests_without_testcases_refused(self):
        d=self.root/'sfdt07';d.mkdir()
        (d/'web-pytest-junit.xml').write_text('<testsuite tests="99" failures="0" errors="0" skipped="0"/>')
        with self.assertRaisesRegex(N.NativeRefusal,'testcase members'):N.junit(d)

    def test_junit_failed_suite_refused(self):
        d=self.root/'sfdt07';d.mkdir()
        (d/'web-pytest-junit.xml').write_text('<testsuite tests="2" failures="1" errors="0" skipped="0"><testcase name="a"/><testcase name="b"><failure/></testcase></testsuite>')
        with self.assertRaisesRegex(N.NativeRefusal,'failures'):
            N.junit(d)

    def test_junit_empty_pass_refused(self):
        d=self.root/'sfdt07';d.mkdir()
        (d/'web-pytest-junit.xml').write_text('<testsuite tests="0" failures="0" errors="0" skipped="0" />')
        with self.assertRaisesRegex(N.NativeRefusal,'tests'):N.junit(d)

    def test_junit_malformed_xml_refused(self):
        d=self.root/'sfdt07';d.mkdir()
        (d/'web-pytest-junit.xml').write_text('<bad')
        with self.assertRaisesRegex(N.NativeRefusal,'Malformed'):
            N.junit(d)


class RunComparisonTests(unittest.TestCase):
    def setUp(self):self.runs,self.jobs,self.arts=sample_runs()
    def run_compare(self):return M.compare(self.runs,self.jobs,self.arts)

    def test_three_green_runs_are_still_not_cutover(self):
        doc=self.run_compare()
        self.assertTrue(doc['legacy_fast_passed'])
        self.assertTrue(doc['expanded_shadow_passed'])
        self.assertFalse(doc['cutover_authorized'])
        self.assertFalse(doc['web_npm_pipeline_equivalent'])
        self.assertFalse(doc['release_qualified'])

    def test_mismatched_source_refused(self):
        self.runs['expanded']['head_sha']='b'*40
        with self.assertRaisesRegex(M.ComparisonError,'same exact'):self.run_compare()

    def test_wrong_repo_refused(self):
        self.runs['source']['repository']['full_name']='other/repo'
        with self.assertRaisesRegex(M.ComparisonError,'repository'):self.run_compare()

    def test_wrong_workflow_refused(self):
        self.runs['expanded']['path']='.github/workflows/malicious.yml'
        with self.assertRaisesRegex(M.ComparisonError,'workflow identity'):self.run_compare()

    def test_missing_android_phone_refused(self):
        self.jobs['legacy']['jobs'][-2]['name']='android (wrong, wrong)'
        with self.assertRaisesRegex(M.ComparisonError,'Both phone'):self.run_compare()

    def test_missing_native_lane_refused(self):
        self.jobs['expanded']['jobs'].pop()
        self.jobs['expanded']['total_count']=3
        with self.assertRaisesRegex(M.ComparisonError,'matrix'):self.run_compare()

    def test_incomplete_jobs_pagination_refused(self):
        self.jobs['source']['total_count']=2
        with self.assertRaisesRegex(M.ComparisonError,'paginated'):self.run_compare()

    def test_missing_artifact_refused(self):
        self.arts['artifacts'].pop()
        self.arts['total_count']=3
        with self.assertRaisesRegex(M.ComparisonError,'artifact missing'):self.run_compare()

    def test_expired_artifact_refused(self):
        self.arts['artifacts'][0]['expired']=True
        with self.assertRaisesRegex(M.ComparisonError,'expired'):self.run_compare()

    def test_wrong_artifact_digest_refused(self):
        self.arts['artifacts'][0]['digest']='none'
        with self.assertRaisesRegex(M.ComparisonError,'ambiguous'):self.run_compare()

    def test_expanded_failure_reported_not_qualified(self):
        self.runs['expanded']['conclusion']='failure'
        self.jobs['expanded']['jobs'][0]['conclusion']='failure'
        d=self.run_compare()
        self.assertFalse(d['expanded_shadow_passed'])
        self.assertEqual(d['status'],'INCOMPLETE_OR_FAILED')
        self.assertFalse(d['cutover_authorized'])

    def test_skipped_legacy_deep_not_promoted(self):
        d=self.run_compare()
        self.assertFalse(d['deep_profiles_qualified'])
        self.assertFalse(d['desktop_gradle_equivalent'])

    def test_fail_if_run_is_still_active(self):
        self.runs['legacy']['status']='in_progress'
        with self.assertRaisesRegex(M.ComparisonError,'not terminal'):self.run_compare()

    def test_missing_run_attempt_refused(self):
        self.runs['source'].pop('run_attempt')
        with self.assertRaisesRegex(M.ComparisonError,'identity'):self.run_compare()


class MemberEvidenceTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory(prefix='sfdt07-member-fixture-')
        self.root=Path(self.tmp.name)
        self.src='a'*40
        self.tree='b'*40
        self.graph='c'*64
        self.run=12345
        self.attempt=2

    def tearDown(self):
        self.tmp.cleanup()

    def put(self,name,content):
        p=self.root/name
        p.parent.mkdir(parents=True,exist_ok=True)
        p.write_bytes(content if isinstance(content,bytes) else content.encode())
        return p

    def json(self,name,obj):
        return self.put(name,json.dumps(obj,indent=2,sort_keys=True))

    def fixture(self,lane):
        job,provider,output=V.LANES[lane]
        jid='node-01'
        rawj='<testsuite tests="3" failures="0" errors="0" skipped="1"><testcase name="a"/><testcase name="b"/><testcase name="c"><skipped/></testcase></testsuite>'
        junit=None
        if lane in ('tv-debug','web-pytest'):
            name='TEST-app.xml' if lane=='tv-debug' else 'web-pytest-junit.xml'
            folder='sfdt07/tv-junit/' if lane=='tv-debug' else 'sfdt07/'
            p=self.put(folder+name,rawj)
            junit={'tests':3,'failures':0,'errors':0,'skipped':1,'verified':True,
                   'report_sha256':{name:hashlib.sha256(p.read_bytes()).hexdigest()}}
        arts=[]
        if output:
            bio=io.BytesIO()
            with zipfile.ZipFile(bio,'w') as z: z.writestr('AndroidManifest.xml','fixture')
            apk=bio.getvalue()
            self.put(f'rm06-output/{jid}/payload/{output}',apk)
            arts=[{'role':'node-output','type':'unverified-bytes','path':output,
                   'sha256':hashlib.sha256(apk).hexdigest(),
                   'signature_state':'UNKNOWN','producer':provider}]
        typed={'source':{'commit':self.src,'tree':self.tree},
               'execution':{'run_id':self.run,'attempt':self.attempt},
               'host':{'toolchain':provider},
               'gates':{'source':{'state':'PASS'},'build':{'state':'PASS'},
                        'test':{'state':'NOT_RUN'}},'artifacts':arts}
        evidence={'schema_version':1,'kind':'devtool.actions.node-typed-evidence',
                  'source_commit':self.src,'source_tree':self.tree,
                  'run_id':self.run,'attempt':self.attempt,'job_id':jid,
                  'graph_sha256':self.graph,'provider':provider,'typed_evidence':typed,
                  'claimed_remote_authority':False,'authenticated_remote':False,
                  'release_qualified':False}
        evidence['receipt_sha256']=V.digest(evidence)
        self.json(f'rm06-evidence/{jid}/node-evidence.json',evidence)
        receipt={'schema_version':1,'kind':'studyflow.sfdt07.native-hosted-shadow',
                 'lane':lane,'status':'NATIVE_SHADOW_PASSED',
                 'native_graph_executed':True,'source':{'source_sha':self.src,
                 'source_tree':self.tree,'run_id':self.run,'attempt':self.attempt},
                 'graph_sha256':self.graph,
                 'node':{'job_id':jid,'job':job,'provider':provider,
                         'output_paths':[output] if output else [],
                         'native_receipt_sha256':evidence['receipt_sha256'],
                         'remote_authenticated':False},'junit':junit,
                 'legacy_equivalent':False,'deep_profiles_qualified':False,
                 'device_qualified':False,'signing_qualified':False,
                 'release_qualified':False,'cutover_authorized':False}
        self.json('sfdt07/receipt.json',receipt)
        if lane=='quality-source':
            self.json('sfdt07/quality-source-checks.json',{'schema_version':1,
                'kind':'studyflow.sfdt07.source-quality-subset',
                'gradle_executed':False,'full_quality_equivalence':False,
                'release_qualified':False,
                'verifiers':[{'script':x,'sha256':'e'*64,'exit_code':0} for x in sorted(V.QUALITY)]})
        return receipt,evidence

    def verified(self,lane):
        return V.verify_members(self.root,lane,source_sha=self.src,run_id=self.run,attempt=self.attempt)

    def test_all_four_member_lanes_verified_without_promoting_trust(self):
        for lane in V.LANES:
            with self.subTest(lane=lane):
                receipt,evidence=self.fixture(lane)
                v=self.verified(lane)
                self.assertEqual(v['status'],'MEMBER_BYTES_VERIFIED_UNQUALIFIED')
                self.assertFalse(v['release_qualified'])
                self.assertFalse(v['cutover_authorized'])
                if lane in ('tv-debug','web-pytest'): self.assertEqual(v['junit']['tests'],3)
                if lane in ('phone-debug','tv-debug'): self.assertTrue(v['apk'])
                for p in list(self.root.rglob('*')):
                    if p.is_file():p.unlink()

    def test_missing_node_receipt_refused(self):
        self.fixture('phone-debug')
        (self.root/'rm06-evidence/node-01/node-evidence.json').unlink()
        with self.assertRaisesRegex(V.MemberError,'Missing'):self.verified('phone-debug')

    def test_node_digest_tamper_refused(self):
        self.fixture('phone-debug')
        p=self.root/'rm06-evidence/node-01/node-evidence.json'
        d=json.loads(p.read_text());d['source_tree']='d'*40;self.json('rm06-evidence/node-01/node-evidence.json',d)
        with self.assertRaisesRegex(V.MemberError,'digest'):self.verified('phone-debug')

    def test_apk_byte_tamper_refused(self):
        self.fixture('tv-debug')
        p=self.root/'rm06-output/node-01/payload/app-tv/build/outputs/apk/debug/app-tv-debug.apk'
        p.write_bytes(p.read_bytes()+b'evil')
        with self.assertRaisesRegex(V.MemberError,'APK bytes'):self.verified('tv-debug')

    def test_junit_byte_tamper_refused(self):
        self.fixture('web-pytest')
        (self.root/'sfdt07/web-pytest-junit.xml').write_text('<testsuite tests="2"/>')
        with self.assertRaisesRegex(V.MemberError,'SHA256'):self.verified('web-pytest')

    def test_junit_summary_mismatch_refused(self):
        self.fixture('tv-debug')
        p=self.root/'sfdt07/receipt.json';d=json.loads(p.read_text());d['junit']['tests']=12
        self.json('sfdt07/receipt.json',d)
        with self.assertRaisesRegex(V.MemberError,'summary'):self.verified('tv-debug')

    def test_run_attempt_mismatch_refused(self):
        self.fixture('quality-source')
        with self.assertRaisesRegex(V.MemberError,'run/attempt'):V.verify_members(
            self.root,'quality-source',source_sha=self.src,run_id=self.run,attempt=99)

    def test_quality_missing_verifier_refused(self):
        self.fixture('quality-source')
        p=self.root/'sfdt07/quality-source-checks.json'
        d=json.loads(p.read_text());d['verifiers'].pop();self.json('sfdt07/quality-source-checks.json',d)
        with self.assertRaisesRegex(V.MemberError,'quality'):self.verified('quality-source')

    def test_release_claim_refused(self):
        self.fixture('phone-debug')
        p=self.root/'sfdt07/receipt.json';d=json.loads(p.read_text());d['release_qualified']=True
        self.json('sfdt07/receipt.json',d)
        with self.assertRaisesRegex(V.MemberError,'authority'):self.verified('phone-debug')

    def test_symlinked_receipt_refused(self):
        self.fixture('phone-debug')
        p=self.root/'sfdt07/receipt.json';alias=self.root/'receipt-source.json'
        alias.write_bytes(p.read_bytes());p.unlink();p.symlink_to(alias)
        with self.assertRaisesRegex(V.MemberError,'Symlinked'):self.verified('phone-debug')

    def test_junit_absent_refused(self):
        self.fixture('tv-debug')
        (self.root/'sfdt07/tv-junit/TEST-app.xml').unlink()
        with self.assertRaisesRegex(V.MemberError,'Missing'):self.verified('tv-debug')

if __name__=='__main__':
    unittest.main(verbosity=2)
