#!/usr/bin/env python3
"""Execute an exact StudyFlow SFDT08 job via Devtool's actual native RM-06 executor.

No secondary DAG or provider. Fail-closed receipts, source/run/attempt identity,
local JUnit member staging. This NEVER grants CI cutover, signer or device trust.
"""
from __future__ import annotations
import argparse
from datetime import datetime,timezone
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys

ROOT=Path(__file__).resolve().parents[1]
# lane => (target, reviewed workflow, job, expected provider, has JUnit)
LANES={
 'phone-release-jvm':('studyflow_android','ci-sfdt08-phone-release-jvm','sfdt08-phone-release-jvm','android-gradle',True),
 'desktop-fast':('studyflow_desktop','ci-sfdt08-desktop-fast','sfdt08-desktop-fast','python-shell',True),
 'web-full':('studyflow_android','ci-sfdt08-web-full','sfdt08-web-full','python-shell',True),
 'android-contracts':('studyflow_android','ci-sfdt08-deep-android','sfdt08-deep-android','python-shell',True),
 'tv-contracts':('studyflow_tv','ci-sfdt08-deep-tv','sfdt08-deep-tv','python-shell',True),
 'desktop-contracts':('studyflow_desktop','ci-sfdt08-deep-desktop','sfdt08-deep-desktop','python-shell',True),
 'quality-contracts':('studyflow_quality','ci-sfdt08-deep-quality','sfdt08-deep-quality','python-shell',False),
 'parity-contracts':('studyflow_parity','ci-sfdt08-deep-parity','sfdt08-deep-parity','python-shell',False),
}


def require(test:bool,reason:str)->None:
    if not test:raise ValueError('SFDT08 '+reason)


def git(root:Path,*args:str)->str:
    result=subprocess.run(['git','-C',str(root),*args],capture_output=True,text=True,check=False,timeout=12)
    require(result.returncode==0,'cannot read Git source identity')
    return result.stdout.strip()


def identity(env:dict,root:Path)->dict:
    require(env.get('GITHUB_ACTIONS')=='true' and env.get('RUNNER_OS')=='Linux' and
            env.get('RUNNER_ARCH')=='X64' and env.get('GITHUB_EVENT_NAME')=='workflow_dispatch',
            'hosted manual Ubuntu identity unavailable')
    source=env.get('GITHUB_SHA','');runtime=env.get('DEVTOOL_SOURCE_SHA','')
    require(all(re.fullmatch('[0-9a-f]{40}',v or '') for v in (source,runtime)),
            'source/runtime SHA not an exact reviewed identity')
    require(git(root,'rev-parse','HEAD')==source,'Git checkout SHA mismatched')
    require(env.get('EXPECTED_SHA')==source,'manual dispatch SHA mismatched')
    require(git(Path(env['RUNNER_TEMP'])/'sfdt08-devtool-source','rev-parse','HEAD')==runtime,
            'actual Devtool runtime SHA mismatch')
    run,attempt=env.get('GITHUB_RUN_ID',''),env.get('GITHUB_RUN_ATTEMPT','')
    require(run.isdecimal() and attempt.isdecimal() and int(run)>0 and int(attempt)>0,
            'GitHub run and attempt missing')
    return {'source_sha':source,'source_tree':git(root,'rev-parse','HEAD^{tree}'),
            'runtime_sha':runtime,'run_id':int(run),'attempt':int(attempt),
            'repository':env.get('GITHUB_REPOSITORY',''),
            'cutover_authorized':False,'release_qualified':False,'device_qualified':False}


def _stage_junit(root:Path,temp:Path,local:dict|None,relative:str)->dict|None:
    if not local:return None
    dst=temp/'sfdt08/junit'
    require(not dst.exists() and not dst.is_symlink(),'pre-existing JUnit staging directory')
    dst.mkdir(parents=True,exist_ok=False)
    proof=local['tests'];rows=local['files']
    require(rows and proof['total']>0 and proof['failed']==0 and local['local_verified'],
            'native JUnit evidence lacks real passing tests')
    manifest=[]
    for row in rows:
        name=Path(row['path']).name
        require(row['path']==relative+'/'+name,'JUnit report outside reviewed source directory')
        source=root/row['path']
        require(source.is_file() and not source.is_symlink(),'unsafe JUnit source member')
        blob=source.read_bytes()
        require(hashlib.sha256(blob).hexdigest()==row['sha256'],'JUnit source bytes mutated')
        target=dst/name
        require(not target.exists(),'duplicate JUnit member')
        target.write_bytes(blob)
        manifest.append({'filename':name,'sha256':row['sha256'],'size':len(blob)})
    require(len(manifest)==len(rows),'JUnit member staging count mismatch')
    return {'report_sha256':proof['report_sha256'], 'total':proof['total'],
            'failed':proof['failed'],'skipped':proof['skipped'],
            'files':manifest,'local_verified':True,'remote_authenticated':False}


def execute(root:Path,lane:str,output:Path,*,env:dict|None=None)->dict:
    require(lane in LANES,'unreviewed native lane')
    env=dict(os.environ if env is None else env)
    stamp=identity(env,root)
    temp=Path(env['RUNNER_TEMP']).resolve()
    require(output.resolve().is_relative_to(temp),'receipt output must remain under runner temp')
    require(not output.exists() and not output.is_symlink(),'stale receipt may not be overwritten')
    require(not (root/'.venv').exists(),'native shadow cannot create a checkout .venv')
    target,workflow,job_name,provider,needed=LANES[lane]
    record={'schema_version':1,'kind':'studyflow.sfdt08.native-coverage',
            'lane':lane,'source':stamp,'status':'FAILED_INCOMPLETE',
            'native_graph_executed':False,'test_gate':'NOT_RUN',
            'legacy_equivalent':False,'deep_equivalent':False,
            'cutover_authorized':False,'release_qualified':False,'device_qualified':False,
            'started_at':datetime.now(timezone.utc).isoformat()}
    try:
        from devtool.actions_graph_export import compile_graph,run_node
        graph=compile_graph(root,target=target,workflow=workflow,devtool_sha=stamp['runtime_sha'])
        require(graph['consumer_commit']==stamp['source_sha'] and
                graph['runtime_sha']==stamp['runtime_sha'],'compiled graph source/runtime mismatch')
        require(len(graph['jobs'])==1,'workflow must have exactly one native job')
        selected=graph['jobs'][0]
        require(selected['job']==job_name and selected['provider']==provider and
                not selected['outputs'] and not selected['handoffs'], 'frozen provider/job/output mismatch')
        require(bool(selected.get('junit_report_dir')) is needed,
                'missing/extra JUnit result requirement')
        receipt=run_node(root,target=target,workflow=workflow,devtool_sha=stamp['runtime_sha'],
                         graph_sha=graph['graph_sha256'],job_id=selected['id'],
                         inputs=temp/'rm06-inputs',outputs=temp/'rm06-output')
        typed=receipt.get('typed_node_evidence') or {}
        require(receipt.get('status')=='LOCAL_JOB_SUCCEEDED' and
                typed.get('source_commit')==stamp['source_sha'] and
                typed.get('graph_sha256')==graph['graph_sha256'] and
                typed.get('job_id')==selected['id'] and
                typed.get('release_qualified') is False and
                typed.get('authenticated_remote') is False,
                'native receipt source/run/graph mismatch')
        test_state=typed.get('typed_evidence',{}).get('gates',{}).get('test',{}).get('state')
        require(test_state==('PASS' if needed else 'NOT_RUN'),'typed test gate not from reviewed JUnit bytes')
        report=_stage_junit(root,temp,receipt.get('local_junit_verification'),
                            selected.get('junit_report_dir',''))
        require(bool(report) is needed,'required JUnit member staging missing')
        record.update({'status':'NATIVE_COVERAGE_PASSED','native_graph_executed':True,
                       'graph_sha256':graph['graph_sha256'],'job_id':selected['id'],
                       'provider':provider,'test_gate':test_state,
                       'native_receipt_sha256':typed['receipt_sha256'],'junit':report})
        return record
    except Exception as exc:
        record['error']=type(exc).__name__+': '+str(exc)[:230]
        raise
    finally:
        record['completed_at']=datetime.now(timezone.utc).isoformat()
        output.parent.mkdir(parents=True,exist_ok=True)
        output.write_text(json.dumps(record,sort_keys=True,indent=2)+'\n',encoding='utf-8')


def fallback(lane:str,output:Path)->None:
    require(lane in LANES,'unknown fallback lane')
    require(not output.exists() and not output.is_symlink(),'cannot overwrite existing native receipt')
    output.parent.mkdir(parents=True,exist_ok=True)
    output.write_text(json.dumps({'schema_version':1,'kind':'studyflow.sfdt08.native-coverage',
        'lane':lane,'status':'FAILED_OR_NOT_RUN',
        'source_sha':os.environ.get('GITHUB_SHA',''),
        'run_id':os.environ.get('GITHUB_RUN_ID',''),
        'native_graph_executed':False,'legacy_equivalent':False,
        'cutover_authorized':False,'release_qualified':False,'device_qualified':False},sort_keys=True)+'\n')


def main()->int:
    ap=argparse.ArgumentParser()
    ap.add_argument('--lane',required=True,choices=sorted(LANES))
    ap.add_argument('--out',required=True,type=Path)
    g=ap.add_mutually_exclusive_group(required=True)
    g.add_argument('--execute',action='store_true');g.add_argument('--fallback',action='store_true')
    args=ap.parse_args()
    try:
        if args.fallback:fallback(args.lane,args.out)
        else:
            r=execute(ROOT,args.lane,args.out)
            print('SFDT08_NATIVE_COVERAGE_PASS:',args.lane,'test=',r['test_gate'],
                  'release/device/cutover=UNQUALIFIED')
    except Exception as exc:
        print('SFDT08_NATIVE_COVERAGE_REFUSED:',str(exc)[:260],file=sys.stderr)
        return 2
    return 0

if __name__=='__main__':raise SystemExit(main())
