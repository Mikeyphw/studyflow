#!/usr/bin/env python3
"""SFDT11 one-command GitHub acceptance wave coordinator, Termux/Zsh-safe.

Only `start`, `compare`, and `rollup` issue narrowly allowlisted GitHub
workflow_dispatch events using the user's GH CLI login. `status` is read-only.
Never signs, merges, changes protections, runs local Gradle or launches a daemon.
"""
from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
import time
from typing import Any

from sfdt11_contract import DEVTOOL

REPO='Mikeyphw/studyflow'
SHA=re.compile(r'[0-9a-f]{40}\Z')
WF={
    'legacy':'studyflow-ci.yml',
    'legacy_deep':'studyflow-ci.yml',
    'source':'studyflow-devtool-shadow.yml',
    'expanded':'studyflow-devtool-native-expanded.yml',
    'fast':'studyflow-devtool-native-coverage.yml',
    'deep':'studyflow-devtool-native-coverage.yml',
    'android':'studyflow-devtool-sfdt10-debug-shadow.yml',
    'compare':'studyflow-devtool-sfdt10-r2-compare.yml',
    'rollup':'studyflow-devtool-sfdt11-rollup.yml',
}
DEEP=frozenset('native-'+x for x in ('android-contracts','tv-contracts','desktop-contracts',
                                     'quality-contracts','parity-contracts'))
FAST=frozenset('native-'+x for x in ('phone-release-jvm','desktop-fast','web-full'))

class WaveError(ValueError):
    pass


def need(ok:bool, msg:str)->None:
    if not ok:raise WaveError(msg)


def command(args:list[str],*,timeout:int=55)->str:
    try:p=subprocess.run(args,check=False,capture_output=True,text=True,timeout=timeout,
                         env={**os.environ,'GH_PROMPT_DISABLED':'1','GIT_TERMINAL_PROMPT':'0'})
    except (OSError,subprocess.TimeoutExpired) as exc:raise WaveError('Command unavailable/timed out') from exc
    need(p.returncode==0,'GitHub CLI refused '+args[0]+': '+p.stderr[-240:])
    return p.stdout


def api_json(path:str)->dict:
    need(path.startswith('repos/Mikeyphw/studyflow/') and
         re.fullmatch(r'[A-Za-z0-9_./?=&-]+',path) is not None,'Unsafe GH endpoint')
    raw=command(['gh','api','--method','GET',path])
    try:j=json.loads(raw)
    except (ValueError,UnicodeError) as exc:raise WaveError('Malformed GitHub API data') from exc
    need(isinstance(j,dict),'Invalid GitHub API response')
    return j


def main_sha()->str:
    c=api_json('repos/Mikeyphw/studyflow/commits/main')
    sha=c.get('sha')
    need(isinstance(sha,str) and SHA.fullmatch(sha) is not None,
         'Unknown StudyFlow main SHA')
    return sha


def candidates(name:str,sha:str)->list[dict]:
    need(name in WF and SHA.fullmatch(sha) is not None,'Invalid source/workflow')
    flow=WF[name]
    x=api_json('repos/Mikeyphw/studyflow/actions/workflows/'+flow+
               '/runs?head_sha='+sha+'&per_page=70')
    rows=x.get('workflow_runs')
    need(isinstance(rows,list) and len(rows)<=70 and x.get('total_count',0)>=len(rows),
         'Incomplete workflow runs listing')
    selected=[]
    for row in rows:
        if (row.get('path')!='.github/workflows/'+flow or row.get('head_sha')!=sha or
            row.get('head_branch')!='main' or
            row.get('event')!=('push' if name=='legacy' else 'workflow_dispatch')):
            continue
        selected.append(row)
    return sorted(selected,key=lambda x:x['id'],reverse=True)


def classify_jobs(name:str,job_rows:list[dict],*,event:str)->str:
    active={j.get('name') for j in job_rows if j.get('conclusion')!='skipped'}
    if name in ('fast','deep'):
        # Early queued runs have no jobs yet. Treat them as a blocking unknown.
        if not active:return 'unknown'
        if active<=FAST:return 'fast'
        if active<=DEEP:return 'deep'
        return 'unexpected'
    if name=='legacy_deep':
        if not active:return 'unknown'
        deep={x for x in active if isinstance(x,str) and x.startswith('Devtool ')}
        return 'legacy_deep' if deep else 'other'
    return name


def latest(name:str,sha:str,*,api=api_json)->dict|None:
    rows=candidates(name,sha)
    if name in ('fast','deep','legacy_deep'):
        for row in rows:
            r=api(f'repos/Mikeyphw/studyflow/actions/runs/{row["id"]}/jobs?per_page=100')
            jobs=r.get('jobs')
            need(isinstance(jobs,list) and r.get('total_count')==len(jobs),
                 'Workflow job listing incomplete')
            typ=classify_jobs(name,jobs,event=row['event'])
            if typ==name or (typ=='unknown' and row.get('status')!='completed'):
                return row
        return None
    return rows[0] if rows else None


def passed(row:dict|None)->bool:
    return bool(row and row.get('status')=='completed' and row.get('conclusion')=='success')


def dispatch(workflow:str,kwargs:dict[str,str])->None:
    need(workflow in WF.values(),'Unreviewed workflow')
    need(all(re.fullmatch(r'[A-Za-z0-9_-]+',k) and
             re.fullmatch(r'[A-Za-z0-9_.-]+',v) for k,v in kwargs.items()),
         'Unreviewed workflow input')
    argv=['gh','workflow','run',workflow,'-R',REPO,'--ref','main']
    for k,v in kwargs.items():argv+=['-f',k+'='+v]
    command(argv)
    print('DISPATCHED '+workflow+' '+str(kwargs))


def wave_status(sha:str)->dict[str,dict|None]:
    record={}
    for name in ('legacy','legacy_deep','source','expanded','fast','deep','android','compare'):
        record[name]=latest(name,sha)
        x=record[name]
        print(f'{name:13}  '+(f'{x["id"]} {x["status"]} {x.get("conclusion")}' if x else 'MISSING'))
    return record


def begin(sha:str)->None:
    # Five source-only/native/bridge workflows plus a separate canonical legacy
    # deep workflow, never additional local Gradle and never required checks.
    actions=(
        ('legacy_deep',{'lane':'deep','expected_sha':sha}),
        ('source',{'expected_sha':sha}),
        ('expanded',{'expected_sha':sha}),
        ('fast',{'expected_sha':sha,'devtool_sha':DEVTOOL,'scope':'fast'}),
        ('deep',{'expected_sha':sha,'devtool_sha':DEVTOOL,'scope':'deep'}),
        ('android',{'expected_sha':sha}),
    )
    # Snapshot the two scopes *before* dispatching either. GH may not expose
    # matrix jobs yet for the first freshly queued run; an unspecific queued
    # fast run must never be misclassified as proof that deep was dispatched.
    coverage_at_start={scope:latest(scope,sha) for scope in ('fast','deep')}
    if (coverage_at_start['fast'] and coverage_at_start['deep'] and
        coverage_at_start['fast']['id']==coverage_at_start['deep']['id']):
        raise WaveError('An in-flight SFDT08 run has unknown scope: retry when its matrix jobs are visible')
    for name,inputs in actions:
        x=coverage_at_start[name] if name in coverage_at_start else latest(name,sha)
        if x and (passed(x) or x.get('status') in ('in_progress','queued','requested','waiting','pending')):
            print('SKIP existing '+name+' '+str(x['id'])+' '+str(x.get('status')))
        else:
            dispatch(WF[name],inputs)
    print('SFDT11_WAVE_DISPATCHED_UNQUALIFIED; check `status`, then `compare`, then `rollup`.')


def compare(sha:str)->None:
    legacy=latest('legacy',sha);android=latest('android',sha)
    need(passed(legacy) and passed(android),'Require same-SHA completed legacy push and SFDT10 R1')
    now=latest('compare',sha)
    if passed(now) or now and now.get('status') in ('in_progress','queued','requested','waiting','pending'):
        print('Existing comparator run '+str(now['id'])+' '+str(now['status']))
        return
    dispatch(WF['compare'],{'expected_sha':sha,'legacy_run_id':str(legacy['id']),
                            'shadow_run_id':str(android['id'])})


def rollup(sha:str)->None:
    record=wave_status(sha)
    missing=[name for name,r in record.items() if not passed(r)]
    need(not missing,'Cannot roll up missing/failed/running runs: '+', '.join(missing))
    ids=[r['id'] for r in record.values()]
    need(len(set(ids))==8,'Duplicate evidence run identities')
    inputs={'expected_sha':sha}
    for name,r in record.items():
        inputs[name+'_run_id']=str(r['id'])
    dispatch(WF['rollup'],inputs)


def drive(sha: str, *, poll_seconds: int = 45, max_hours: int = 3) -> None:
    """Synchronous one-command Termux coordinator; no background work/daemon.

    Starts only the reviewed six workflows, then waits for successful GitHub
    readback before invoking the comparator and final rollup in sequence.
    Any failed, canceled, stale or mismatched run stops with a nonzero result.
    """
    need(30 <= poll_seconds <= 180 and 1 <= max_hours <= 8,
         'Bounded polling interval/horizon required')
    begin(sha)
    until = time.monotonic() + max_hours * 3600
    has_compared = False
    has_rollup = False
    while time.monotonic() < until:
        need(main_sha() == sha, 'StudyFlow main moved while evidence wave was running')
        if has_rollup:
            r=latest('rollup',sha)
            if passed(r):
                print('SFDT11_CONSOLIDATED_WAVE_HOSTED_PASS_UNQUALIFIED:',r['id'],
                      'CUTOVER=NO_GO; first-class Web/Desktop and device=NOT_QUALIFIED')
                return
            if r and r.get('status')=='completed':
                raise WaveError('Consolidated hosted rollup did not pass; run '+str(r['id']))
        elif has_compared:
            r=latest('compare',sha)
            if passed(r):
                rollup(sha)
                has_rollup=True
            elif r and r.get('status')=='completed':
                raise WaveError('SFDT10 R2 comparator failed: run '+str(r['id']))
        else:
            check={k:latest(k,sha) for k in ('legacy','legacy_deep','source','expanded',
                                              'fast','deep','android')}
            for k,r in check.items():
                if r and r.get('status')=='completed' and r.get('conclusion')!='success':
                    raise WaveError('Wave run failed '+k+': '+str(r['id']))
            if all(passed(r) for r in check.values()):
                compare(sha)
                has_compared=True
        print('SFDT11_WAVE_WAITING_READBACK: compare='+str(has_compared)+
              ', rollup='+str(has_rollup),flush=True)
        time.sleep(poll_seconds)
    raise WaveError('Wave exhausted bounded wait horizon; inspect `status`, no cutover')


def main()->int:
    ap=argparse.ArgumentParser(description=__doc__)
    ap.add_argument('command',choices=('start','status','compare','rollup','drive'))
    ap.add_argument('--expected-sha',default='',help='Optional exact current main commit guard')
    ap.add_argument('--poll-seconds',type=int,default=45,help='Drive mode polling, 30..180 s')
    ap.add_argument('--max-hours',type=int,default=3,help='Drive mode horizon, 1..8 h')
    a=ap.parse_args()
    try:
        sha=main_sha()
        need(not a.expected_sha or a.expected_sha==sha,
             'Remote main differs from explicit user source guard')
        print('SFDT11_SOURCE',sha)
        if a.command=='start':begin(sha)
        elif a.command=='status':wave_status(sha)
        elif a.command=='compare':compare(sha)
        elif a.command=='rollup':rollup(sha)
        elif a.command=='drive':drive(sha,poll_seconds=a.poll_seconds,max_hours=a.max_hours)
    except (WaveError,OSError,ValueError,TypeError,KeyError,
            subprocess.TimeoutExpired) as exc:
        print('SFDT11_WAVE_REFUSED:',str(exc)[:370],file=sys.stderr)
        return 2
    return 0

if __name__=='__main__':
    raise SystemExit(main())
