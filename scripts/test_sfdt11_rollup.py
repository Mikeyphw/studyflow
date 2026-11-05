#!/usr/bin/env python3
"""SFDT11 consolidated evidence: positive and fail-closed negative tests."""
from __future__ import annotations

from pathlib import Path
import json
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0,str(Path(__file__).resolve().parent))
import sfdt11_rollup as a

SRC='a'*40
RID={name:i+900 for i,name in enumerate(a.PATHS)}


def run(name,sha=SRC):
    return {'id':RID[name],'head_sha':sha,'head_branch':'main',
            'repository':{'full_name':a.REPO},'path':a.PATHS[name],
            'status':'completed','conclusion':'success',
            'event':'push' if name=='legacy' else 'workflow_dispatch','run_attempt':1}


def jobs(name):
    j=[{'name':label,'conclusion':'success','status':'completed','run_id':RID[name]}
       for label in a.expected_jobs(name)]
    return {'total_count':len(j),'jobs':j}


def inventory(name):
    templates={
        'legacy': [f'studyflow-app-ci-debug-{RID[name]}',f'studyflow-app-tv-ci-debug-{RID[name]}',
                   f'studyflow-app-diagnostics-{RID[name]}-1',f'studyflow-app-tv-diagnostics-{RID[name]}-1'],
        'legacy_deep':[f'studyflow-devtool-{x}-{RID[name]}-1' for x in a.DEEP],
        'source':[f'studyflow-sfdt06-native-shadow-{RID[name]}-1'],
        'expanded':[f'studyflow-sfdt07-{x}-{RID[name]}-1' for x in a.EXPANDED],
        'fast':[f'studyflow-sfdt08-{x}-{RID[name]}-1' for x in a.FAST],
        'deep':[f'studyflow-sfdt08-{x}-{RID[name]}-1' for x in a.DEEP],
        'android':[f'studyflow-sfdt10-{x}-{RID[name]}-1' for x in a.ANDROID],
        'compare':[f'studyflow-sfdt10-r2-compare-{RID[name]}-1'],
    }
    rows=[{'name':s,'expired':False,'size_in_bytes':1000,'digest':'sha256:'+'f'*64}
          for s in templates[name]]
    return {'total_count':len(rows),'artifacts':rows}


def android_report():
    return {'kind':'studyflow.sfdt10.r2.android-same-source-evidence',
            'status':'ANDROID_MEMBER_CONTRACT_MATCHED_UNQUALIFIED',
            'source_sha':SRC,'legacy_run':RID['legacy'],'shadow_run':RID['android'],
            'lanes':[{'lane':'phone-debug','apk':{'signatures_valid':True},
                      'lint':{'matching_issues':True},'junit_cases':0},
                     {'lane':'tv-debug','apk':{'signatures_valid':True},
                      'lint':{'matching_issues':True},'junit_cases':11},
                     {'lane':'phone-release-jvm','junit_cases':21}],
            **{k:False for k in ('legacy_equivalent','cutover_authorized','release_qualified',
                                  'device_qualified','github_archive_bytes_sha256_verified')}}


def source_report():
    return {'kind':'studyflow.sfdt06.native-source-shadow','status':'NATIVE_SOURCE_SHADOW_PASSED',
            'nodes':[{'status':'NATIVE_NODE_PASSED'}]*4,
            'source':{'source_commit':SRC,'run_id':RID['source']},
            'release_qualified':False,'legacy_equivalent':False}


def coverage_report(scope):
    lanes=a.FAST if scope=='fast' else a.DEEP
    return {'source_sha':SRC,'scope':scope,'status':'HOSTED_NATIVE_COVERAGE_VERIFIED_UNQUALIFIED',
            'lanes':[{'lane':x,'tests':0,'failed':0,'cutover_authorized':False} for x in lanes],
            **{k:False for k in ('cutover_authorized','release_qualified','device_qualified',
                                   'legacy_equivalent','deep_equivalent')}}


def expanded_report():
    return {'source_sha':SRC,'same_sha':True,'status':'ALL_THREE_HOSTED_RUNS_GREEN_PARTIAL_COVERAGE',
            'member_evidence_verified':True,'evidence_verification':'NATIVE_MEMBER_BYTES_VERIFIED_UNQUALIFIED',
            'legacy_fast_passed':True,'source_shadow_passed':True,'expanded_shadow_passed':True,
            'cutover_authorized':False,'member_details':{x:{} for x in a.EXPANDED}}

class RollupTests(unittest.TestCase):
    def test_exact_same_source_and_event(self):
        for n in a.PATHS:self.assertEqual(a.run_identity(run(n),rid=RID[n],name=n,source=SRC),1)
        with self.assertRaises(a.EvidenceError):a.run_identity(run('android',sha='b'*40),rid=RID['android'],name='android',source=SRC)
        r=run('compare');r['event']='push'
        with self.assertRaises(a.EvidenceError):a.run_identity(r,rid=RID['compare'],name='compare',source=SRC)

    def test_wrong_branch_or_failed_run_refused(self):
        r=run('source');r['head_branch']='feature'
        with self.assertRaises(a.EvidenceError):a.run_identity(r,rid=RID['source'],name='source',source=SRC)
        r=run('source');r['conclusion']='failure'
        with self.assertRaises(a.EvidenceError):a.run_identity(r,rid=RID['source'],name='source',source=SRC)

    def test_job_counts_and_skips(self):
        for n in a.PATHS:a.job_names(jobs(n),rid=RID[n],expected=a.expected_jobs(n),label=n)
        j=jobs('legacy');j['jobs'].append({'name':'Devtool ${{ matrix.profile }}','conclusion':'skipped'})
        j['total_count']+=1
        a.job_names(j,rid=RID['legacy'],expected=a.expected_jobs('legacy'),label='legacy')
        j['jobs'][0]['conclusion']='failure'
        with self.assertRaises(a.EvidenceError):a.job_names(j,rid=RID['legacy'],expected=a.expected_jobs('legacy'),label='legacy')

    def test_job_pagination_or_duplicates_refused(self):
        j=jobs('deep');j['total_count']+=1
        with self.assertRaises(a.EvidenceError):a.job_names(j,rid=RID['deep'],expected=a.expected_jobs('deep'),label='deep')
        j=jobs('deep');j['jobs'][0]['name']=j['jobs'][1]['name']
        with self.assertRaises(a.EvidenceError):a.job_names(j,rid=RID['deep'],expected=a.expected_jobs('deep'),label='deep')

    def test_artifact_digests_and_required_lanes(self):
        for n in a.PATHS:
            x=a.artifact_inventory(inventory(n))
            self.assertTrue(a.require_artifacts(x,rid=RID[n],attempt=1,name=n))
        j=inventory('deep');j['artifacts'][0]['digest']='sha256:bad'
        with self.assertRaises(a.EvidenceError):a.artifact_inventory(j)
        j=inventory('expanded');j['artifacts'].pop();j['total_count']-=1
        with self.assertRaises(a.EvidenceError):
            a.require_artifacts(a.artifact_inventory(j),rid=RID['expanded'],attempt=1,name='expanded')

    def test_source_native_receipt(self):
        a.source_receipt(source_report(),source=SRC,rid=RID['source'])
        r=source_report();r['nodes'][0]['status']='FAILED'
        with self.assertRaises(a.EvidenceError):a.source_receipt(r,source=SRC,rid=RID['source'])

    def test_android_member_comparison(self):
        self.assertEqual(a.android_receipt(android_report(),source=SRC,legacy=RID['legacy'],android=RID['android'])['lanes'],3)
        r=android_report();r['lanes'][0]['lint']={'matching_issues':False}
        with self.assertRaises(a.EvidenceError):a.android_receipt(r,source=SRC,legacy=RID['legacy'],android=RID['android'])
        r=android_report();r['cutover_authorized']=True
        with self.assertRaises(a.EvidenceError):a.android_receipt(r,source=SRC,legacy=RID['legacy'],android=RID['android'])

    def test_full_fast_and_deep_coverage(self):
        for scope,n in [('fast',3),('deep',5)]:
            self.assertEqual(a.external_compare_report(coverage_report(scope),source=SRC,scope=scope)['lanes'],n)
        r=coverage_report('fast');r['lanes'].pop()
        with self.assertRaises(a.EvidenceError):a.external_compare_report(r,source=SRC,scope='fast')
        r=coverage_report('deep');r['lanes'][0]['failed']=1
        with self.assertRaises(a.EvidenceError):a.external_compare_report(r,source=SRC,scope='deep')

    def test_expanded_native_member_evidence(self):
        self.assertEqual(a.expanded_report(expanded_report(),source=SRC)['lanes'],4)
        r=expanded_report();r['member_evidence_verified']=False
        with self.assertRaises(a.EvidenceError):a.expanded_report(r,source=SRC)

    def test_source_typed_receipt_members_forgery_refused(self):
        from hashlib import sha256
        with tempfile.TemporaryDirectory() as d:
            base=Path(d);sha='d'*64
            src=source_report();src['graph_sha256']=sha
            nodes=[]
            for i in range(1,5):
                nid=f'node-{i:02d}'
                typed={'source_commit':SRC,'run_id':RID['source'],'attempt':1,
                       'graph_sha256':sha,'release_qualified':False,
                       'authenticated_remote':False}
                digest=a.sha256(json.dumps(typed,sort_keys=True,separators=(',',':'),ensure_ascii=True).encode()).hexdigest()
                typed['receipt_sha256']=digest
                f=base/'rm06-evidence'/nid/'node-evidence.json';f.parent.mkdir(parents=True)
                f.write_text(json.dumps(typed))
                nodes.append({'id':nid,'status':'NATIVE_NODE_PASSED','evidence_sha256':digest})
            src['nodes']=nodes
            self.assertEqual(a.source_members(base,src,source=SRC,rid=RID['source'],attempt=1),4)
            f=base/'rm06-evidence/node-01/node-evidence.json'
            forged=json.loads(f.read_text());forged['authenticated_remote']=True
            f.write_text(json.dumps(forged))
            with self.assertRaises(a.EvidenceError):
                a.source_members(base,src,source=SRC,rid=RID['source'],attempt=1)

    def test_deep_gradle_receipt_source_bound_and_actual(self):
        plan={'target':'studyflow_quality','operation':'tasks.build',
              'tasks':['verifyOne','verifyTwo']}
        row={'schema':'studyflow.devtool.github-gradle-plan.v1',
             'profile':'quality-contracts','git_sha':SRC,
             'github_run_id':str(RID['legacy_deep']),'github_run_attempt':'1',
             'status':'passed','exit_code':0,'build_executed':True,
             'native_exo_gradle_execution':False,'qualified_release':False,
             'execution_authority':'GitHub Actions only','config_sha256':'a'*64,
             'target':plan['target'],'operation':plan['operation'],'tasks':plan['tasks'],
             'gradle_argv':['./gradlew','--no-daemon','--max-workers=2',*plan['tasks']]}
        def check():
            a.deep_receipt(row,profile='quality-contracts',source=SRC,
                           rid=RID['legacy_deep'],attempt=1,
                           reviewed_plan=plan,config_sha256='a'*64)
        check()
        for key,value in [('build_executed',False),('exit_code',1),('native_exo_gradle_execution',True),
                          ('qualified_release',True),('github_run_id','77')]:
            with self.subTest(key=key):
                r=row.copy();r[key]=value
                with self.assertRaises(a.EvidenceError):
                    a.deep_receipt(r,profile='quality-contracts',source=SRC,
                                   rid=RID['legacy_deep'],attempt=1,
                                   reviewed_plan=plan,config_sha256='a'*64)

    def test_duplicate_or_zero_evidence_run_ids(self):
        with tempfile.TemporaryDirectory() as d:
            r=dict(RID);r['compare']=r['source']
            with self.assertRaises(a.EvidenceError):a.collect(source=SRC,run_ids=r,temp=Path(d))
            r=dict(RID);r['android']=0
            with self.assertRaises(a.EvidenceError):a.collect(source=SRC,run_ids=r,temp=Path(d))

    def test_consolidated_eight_run_positive_mock(self):
        def mocked_api(path):
            rid=int(path.split('runs/')[1].split('/')[0]);name=next(n for n,i in RID.items() if i==rid)
            if '/jobs' in path:return jobs(name)
            if '/artifacts' in path:return inventory(name)
            return run(name)
        def download_mock(name,*,rid,to):
            to.mkdir()
            if rid==RID['source']:
                p=to/'sfdt06/native-source-shadow.json';p.parent.mkdir();p.write_text(json.dumps(source_report()))
            elif rid==RID['legacy_deep']:
                p=to/'studyflow-devtool-profile.json';p.write_text('{}')
            else:
                p=to/'sfdt10-r2-report.json';p.write_text(json.dumps(android_report()))
            return to

        def ext_mock(script,args):
            if script=='sfdt07_compare_runs.py':return expanded_report()
            return coverage_report(args[args.index('--scope')+1])
        with tempfile.TemporaryDirectory() as d, patch.object(a,'api',side_effect=mocked_api),\
             patch.object(a,'extracted',side_effect=download_mock),\
             patch.object(a,'run_external',side_effect=ext_mock),\
             patch.object(a,'source_members',return_value=4),\
             patch.object(a,'deep_receipt'),\
             patch.object(a,'reviewed_deep_plan',return_value={}),\
             patch.object(a,'ROOT',Path(d)):
            (Path(d)/'.devtool.toml').write_text('synthetic config')
            r=a.collect(source=SRC,run_ids=dict(RID),temp=Path(d))
            self.assertEqual(r['decision'],'NO_GO_CI_CUTOVER')
            self.assertEqual(r['deep']['lanes'],5)
            self.assertEqual(r['fast']['lanes'],3)
            self.assertEqual(r['android_parity']['lanes'],3)
            self.assertFalse(r['web_node_first_class'])
            self.assertFalse(r['cutover_authorized'])

if __name__=='__main__':unittest.main(verbosity=2)
