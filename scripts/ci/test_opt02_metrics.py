#!/usr/bin/env python3
"""OPT02 P03: offline, adversarial, no GH/Gradle/ADB tests of read-only metrics."""
from __future__ import annotations

from copy import deepcopy
from pathlib import Path
import json
import sys
import unittest
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parent))
import opt02_metrics as m

BASE='a'*40
HEAD='b'*40

def run(rid:int, workflow:str, event:str, sha:str)->dict:
    return dict(id=rid, path=workflow, event=event, head_sha=sha,
                head_branch=m.BRANCH, run_attempt=1, repository={'full_name':m.REPO},
                head_repository={'full_name':m.REPO}, status='completed', conclusion='success')

def step(name:str, result:str, start:str, end:str)->dict:
    return dict(name=name, conclusion=result, started_at='2026-11-15T'+start+'Z',
                completed_at='2026-11-15T'+end+'Z')

def job(name:str, jid:int, start:str, end:str, steps:list)->dict:
    return dict(name=name, id=jid, conclusion='success',
                started_at='2026-11-15T'+start+'Z',
                completed_at='2026-11-15T'+end+'Z', steps=steps)

def fixture()->dict:
    baseline=run(101,m.TARGET_WORKFLOW,'pull_request',BASE)
    targeted=run(102,m.TARGET_WORKFLOW,'pull_request',HEAD)
    producer=run(103,m.PRODUCER_WORKFLOW,'push',HEAD)
    base_job=job(m.JOB,5001,'16:25:14','16:35:38',
                 [step(m.COMPILE,'success','16:25:51','16:35:33')])
    target_job=job(m.JOB,5002,'19:16:37','19:24:08',[
        step(m.REUSE,'success','19:17:10','19:24:04'),
        step(m.COMPILE,'skipped','19:24:04','19:24:04'),
        step(m.VERIFY,'success','19:24:04','19:24:05')])
    producer_job=job(m.BUILDER,5003,'19:16:20','19:23:54',[
        step(m.BUILDER_STEP,'success','19:16:44','19:23:45')])
    return dict(baseline=baseline,targeted=targeted,producer=producer,
                baseline_jobs={'jobs':[base_job]},
                targeted_jobs={'jobs':[target_job,job(m.GATE,5004,'19:24:10','19:24:12',[])]},
                producer_jobs={'jobs':[producer_job]},
                producer_artifacts={'artifacts':[dict(name='sfdt22-physical-apks-103-1',
                                                       id=9001,expired=False,size_in_bytes=28_097_930)]},
                consumer_log='2026-11-15T19:24:03Z OPT02_SHARED_APKS_INFO DOWNLOAD_PASS attempts=1\n'+
                             '2026-11-15T19:24:04Z '+m.HIT+'\n',
                baseline_run_id=101,target_run_id=102,producer_run_id=103)

class Metrics(unittest.TestCase):
    def test_01_realistic_evidence_and_metrics(self):
        x=m.analyze(**fixture())
        self.assertEqual(x['verification'],'PASS')
        self.assertEqual(x['baseline']['android_gradle_compile_seconds'],582)
        self.assertEqual(x['current']['reuse_step_seconds'],414)
        self.assertEqual(x['baseline']['android_job_seconds'],624)
        self.assertEqual(x['current']['android_job_seconds'],451)
        self.assertEqual(x['observation']['job_wall_time_delta_seconds'],173)
        self.assertEqual(x['observation']['job_wall_time_delta_percent'],27.7)
        self.assertEqual(x['observation']['duplicate_android_gradle_compilations_avoided'],1)
        self.assertFalse(x['qualifications']['release_qualified_by_report'])

    def test_02_wrong_source_and_wrong_producer_event_refused(self):
        for obj,key,value in [('targeted','head_sha','c'*40),('producer','head_sha','c'*40),
                              ('producer','event','pull_request'),('producer','path',m.TARGET_WORKFLOW),
                              ('baseline','head_sha',HEAD),('targeted','head_branch','main')]:
            with self.subTest(obj=obj,key=key):
                f=fixture();f[obj][key]=value
                with self.assertRaises(m.Refused):m.analyze(**f)

    def test_03_wrong_repo_and_head_repo_refused(self):
        for section in ('targeted','producer','baseline'):
            for key in ('repository','head_repository'):
                f=fixture();f[section][key]={'full_name':'malicious/fork'}
                with self.subTest(section=section,key=key),self.assertRaises(m.Refused):m.analyze(**f)

    def test_04_non_success_job_or_gate_refused(self):
        for which in ('baseline_jobs','targeted_jobs','producer_jobs'):
            f=fixture();f[which]['jobs'][0]['conclusion']='failure'
            with self.subTest(which=which),self.assertRaises(m.Refused):m.analyze(**f)
        f=fixture();f['targeted_jobs']['jobs'][1]['conclusion']='failure'
        with self.assertRaises(m.Refused):m.analyze(**f)

    def test_05_wrong_step_or_non_skipped_gradle_refused(self):
        for steps_key,index,outcome in [('baseline_jobs',0,'skipped'),
                                        ('targeted_jobs',1,'success'),
                                        ('targeted_jobs',0,'failure'),
                                        ('targeted_jobs',2,'skipped'),
                                        ('producer_jobs',0,'skipped')]:
            f=fixture();f[steps_key]['jobs'][0]['steps'][index]['conclusion']=outcome
            with self.subTest(which=steps_key,index=index),self.assertRaises(m.Refused):m.analyze(**f)

    def test_06_missing_duplicate_jobs_or_steps_refused(self):
        for key in ('baseline_jobs','targeted_jobs','producer_jobs'):
            f=fixture();f[key]['jobs'].append(deepcopy(f[key]['jobs'][0]))
            with self.subTest(key=key),self.assertRaises(m.Refused):m.analyze(**f)
        f=fixture();f['targeted_jobs']['jobs'][0]['steps'].append(deepcopy(f['targeted_jobs']['jobs'][0]['steps'][0]))
        with self.assertRaises(m.Refused):m.analyze(**f)

    def test_07_log_marker_absent_spoofed_or_failing_refused(self):
        cases=('random output',m.HIT+'\n'+m.HIT+'\n',
               m.HIT+'\n'+m.REFUSED+' blah\n',
               m.HIT+'\n'+m.MISS+' blah\n',
               m.HIT+'\nOPT02_SHARED_APKS_INFO DOWNLOAD_PASS attempts=9\n')
        for value in cases:
            f=fixture();f['consumer_log']=value
            with self.subTest(log=value[:40]),self.assertRaises(m.Refused):m.analyze(**f)

    def test_08_artifact_wrong_attempt_expired_duplicate_or_size_refused(self):
        mutations=[{'name':'sfdt22-physical-apks-104-1'}, {'expired':True},
                   {'size_in_bytes':0},{'id':0}]
        for mutation in mutations:
            f=fixture();f['producer_artifacts']['artifacts'][0].update(mutation)
            with self.subTest(change=mutation),self.assertRaises(m.Refused):m.analyze(**f)
        f=fixture();f['producer_artifacts']['artifacts'].append(deepcopy(f['producer_artifacts']['artifacts'][0]))
        with self.assertRaises(m.Refused):m.analyze(**f)

    def test_09_timestamp_and_duration_refused(self):
        for value in ('2026-11-15T19:24:05+00:00','2026-11-15T99:00:00Z','NOW',None):
            f=fixture();f['targeted_jobs']['jobs'][0]['completed_at']=value
            with self.subTest(value=value),self.assertRaises(m.Refused):m.analyze(**f)
        f=fixture();f['targeted_jobs']['jobs'][0]['completed_at']='2026-11-15T19:00:00Z'
        with self.assertRaises(m.Refused):m.analyze(**f)

    def test_10_run_id_validation_and_bool_refusal(self):
        for v in (0,-1,True,'3',10**16):
            with self.subTest(v=v),self.assertRaises(m.Refused):m.run_id(v)

    def test_11_readonly_collector_never_invokes_mutating_host_tools(self):
        source=Path(m.__file__).read_text()
        self.assertNotIn('git push',source)
        self.assertNotIn('gradlew',source)
        self.assertNotIn('adb install',source)
        self.assertNotIn('gh run rerun',source)
        self.assertNotIn('gh workflow run',source)
        self.assertNotIn('POST',source)
        self.assertNotIn('DELETE',source)
        self.assertIn("'--method', 'GET'",source)

    def test_12_pure_analysis_never_calls_network(self):
        with mock.patch.object(m,'gh_json',side_effect=AssertionError('network called')):
            self.assertEqual(m.analyze(**fixture())['verification'],'PASS')

    def test_13_job_log_and_api_failed_paths_redacted(self):
        with mock.patch.object(m.subprocess,'run',return_value=mock.Mock(returncode=1,stdout=b'token=SECRET',stderr=b'secret')):
            with self.assertRaisesRegex(m.Refused,'GH_API_NONZERO') as cm:
                m.gh_json('repos/'+m.REPO+'/actions/runs/101')
            self.assertNotIn('SECRET',str(cm.exception))
            with self.assertRaisesRegex(m.Refused,'GH_JOB_LOG_UNAVAILABLE_OR_OVERSIZE'):
                m.gh_job_log(101)

    def test_14_api_path_restrictions(self):
        for path in ('repos/bad/repo/actions/runs/1', 'repos/'+m.REPO+'/issues/1',
                     'repos/'+m.REPO+'/actions/runs/1\nanything'):
            with self.subTest(path=path),self.assertRaises(m.Refused):m.gh_json(path)

    def test_15_report_is_serializable_and_has_no_raw_logs(self):
        s=json.dumps(m.analyze(**fixture()),sort_keys=True)
        self.assertNotIn('19:24:03Z OPT02_',s)
        self.assertNotIn('token=',s)
        self.assertIn('duplicate_android_gradle_compilations_avoided',s)

if __name__=='__main__':unittest.main(verbosity=2)
