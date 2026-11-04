#!/usr/bin/env python3
"""Read-only GitHub same-SHA SFDT08 acceptance and native JUnit member verifier.

No release/CI authority: a green workflow is insufficient without receipt bytes.
Requires `gh` authentication; downloads only into temporary directories.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET

from sfdt08_contract import LANE_TARGET, JUNIT

REPO=re.compile(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+\Z')
SHA=re.compile(r'[a-f0-9]{40}\Z')

class ComparisonError(ValueError):pass

def need(ok,why):
    if not ok:raise ComparisonError(why)

def sha(data):return hashlib.sha256(data).hexdigest()

def get(repo,endpoint):
    p=subprocess.run(['gh','api','--method','GET','/repos/'+repo+endpoint],
                     capture_output=True,text=True,timeout=45,check=False,
                     env={**os.environ,'GH_PROMPT_DISABLED':'1'})
    need(p.returncode==0,'GitHub API failed: '+endpoint)
    try:row=json.loads(p.stdout)
    except (ValueError,UnicodeError):raise ComparisonError('Malformed GitHub JSON') from None
    need(isinstance(row,dict),'Unexpected GitHub API response')
    return row

def count_xml(raw):
    try:root=ET.fromstring(raw)
    except ET.ParseError:raise ComparisonError('Malformed JUnit member') from None
    def tag(el):return el.tag.rsplit('}',1)[-1]
    need(tag(root) in ('testsuite','testsuites'),'Unsupported JUnit root')
    cases=[el for el in root.iter() if tag(el)=='testcase']
    need(bool(cases),'JUnit member contains no testcases')
    failed=sum(any(tag(ch) in ('failure','error') for ch in case) for case in cases)
    skipped=sum(any(tag(ch)=='skipped' for ch in case) for case in cases)
    for suite in (el for el in root.iter() if tag(el)=='testsuite'):
        sub=[c for c in suite.iter() if tag(c)=='testcase']
        for label,actual in (('tests',len(sub)),
           ('failures',sum(any(tag(x) in ('failure','error') for x in case) for case in sub)),
           ('skipped',sum(any(tag(x)=='skipped' for x in case) for case in sub))):
            declared=suite.get(label)
            need(declared is None or (declared.isdecimal() and int(declared)==actual),
                 'Contradictory JUnit suite header '+label)
    return len(cases),failed,skipped

def verify_members(root:Path,lane:str,source:str,runid:int,attempt:int):
    expected={'sfdt08/receipt.json','rm06-evidence/node-01/node-evidence.json'}
    receipt_path=root/'sfdt08/receipt.json';native_path=root/'rm06-evidence/node-01/node-evidence.json'
    need(receipt_path.is_file() and native_path.is_file(),'Missing native receipt/evidence')
    receipt=json.loads(receipt_path.read_text())
    native=json.loads(native_path.read_text())
    need(receipt.get('kind')=='studyflow.sfdt08.native-coverage' and
         receipt.get('status')=='NATIVE_COVERAGE_PASSED' and receipt.get('lane')==lane,
         'Native lane did not actually pass')
    stamp=receipt.get('source',{})
    need(stamp.get('source_sha')==source and stamp.get('run_id')==runid and
         stamp.get('attempt')==attempt and SHA.fullmatch(stamp.get('runtime_sha','')) is not None,
         'Native source/run/attempt mismatch')
    need(native.get('source_commit')==source and native.get('run_id')==runid and
         native.get('attempt')==attempt and native.get('graph_sha256')==receipt.get('graph_sha256') and
         native.get('receipt_sha256')==receipt.get('native_receipt_sha256'),
         'Devtool graph/run/typed receipt identity mismatch')
    raw={k:v for k,v in native.items() if k!='receipt_sha256'}
    digest=sha(json.dumps(raw,sort_keys=True,separators=(',',':'),ensure_ascii=True).encode('ascii'))
    need(digest==native['receipt_sha256'],'Native typed receipt digest mismatch')
    typed=native.get('typed_evidence',{})
    need(typed.get('source',{}).get('commit')==source and
         typed.get('execution',{}).get('run_id')==runid and
         typed.get('execution',{}).get('attempt')==attempt,
         'Nested Devtool typed source/run identity mismatch')
    for key in ('cutover_authorized','legacy_equivalent','deep_equivalent','release_qualified','device_qualified'):
        need(receipt.get(key) is False,'Illegal promotion of '+key)
    for key in ('release_qualified','authenticated_remote','claimed_remote_authority'):
        need(native.get(key) is False,'Unqualified native evidence promoted '+key)
    tests=typed.get('tests',{})
    reported_gate=typed.get('gates',{}).get('test',{}).get('state')
    required=lane in JUNIT
    need(reported_gate==('PASS' if required else 'NOT_RUN'),'Misrepresented native test gate')
    junit=receipt.get('junit')
    if required:
        need(isinstance(junit,dict) and junit.get('local_verified') is True,
             'Missing verified local JUnit report proof')
        folder=root/'sfdt08/junit'
        rows=[];total=failed=skipped=0
        names={item['filename'] for item in junit.get('files',[])}
        need(names and len(names)==len(junit['files']) and len(names)<=192,'Invalid JUnit filename inventory')
        for item in sorted(junit['files'],key=lambda r:r['filename']):
            name=item['filename']
            need(re.fullmatch('[A-Za-z0-9_][A-Za-z0-9_.$-]{0,180}\\.xml',name) is not None,
                 'Unsafe JUnit filename')
            f=folder/name
            need(f.is_file() and not f.is_symlink(),'Missing/symlinked JUnit member')
            rawbytes=f.read_bytes();n,fails,skips=count_xml(rawbytes)
            need(sha(rawbytes)==item['sha256'] and len(rawbytes)==item['size'],
                 'JUnit member digest or size mismatch')
            total+=n;failed+=fails;skipped+=skips
            rows.append({'path':JUNIT[lane]+'/'+name,'size':len(rawbytes),
                         'sha256':sha(rawbytes),'total':n,'failed':fails,'skipped':skips})
        group=sha(json.dumps(rows,sort_keys=True,separators=(',',':')).encode('ascii'))
        need(group==tests.get('report_sha256')==junit.get('report_sha256') and
             total==tests.get('total')==junit.get('total') and failed==0 and
             skipped==tests.get('skipped')==junit.get('skipped'),
             'Local JUnit rows disagree with native typed test gate')
    else:
        need(not junit and tests.get('format')=='none' and reported_gate=='NOT_RUN',
             'Non-test verifier spuriously claims JUnit evidence')
    return {'lane':lane,'source':source,'provider':receipt['provider'],'test_gate':reported_gate,
            'tests':tests.get('total',0),'failed':tests.get('failed',0),
            'skipped':tests.get('skipped',0),'remote_authenticated':False,
            'cutover_authorized':False}

def run(argv=None)->int:
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--repo',default='Mikeyphw/studyflow')
    p.add_argument('--legacy-run',type=int,required=True)
    p.add_argument('--source-run',type=int,required=True)
    p.add_argument('--expanded-run',type=int,required=True)
    p.add_argument('--coverage-run',type=int,required=True)
    p.add_argument('--scope',choices=('fast','deep','all'),default='fast')
    p.add_argument('--json',action='store_true')
    a=p.parse_args(argv)
    try:
        need(REPO.fullmatch(a.repo) is not None,'Unsafe repo')
        ids=[a.legacy_run,a.source_run,a.expanded_run,a.coverage_run]
        need(min(ids)>0 and len(set(ids))==4,'Four distinct positive run IDs required')
        runs=[get(a.repo,f'/actions/runs/{i}') for i in ids]
        need(all(r.get('id')==i for i,r in zip(ids,runs)),'GitHub run identity mismatch')
        source=runs[0].get('head_sha')
        need(SHA.fullmatch(source or '') is not None and
             all(r.get('head_sha')==source and r.get('status')=='completed' and
                 r.get('conclusion')=='success' for r in runs),
             'Same SHA four-run success requirement not met')
        expect=[lane for lane in LANE_TARGET if a.scope=='all' or
                ('-' in lane and lane.endswith('-contracts'))==(a.scope=='deep')]
        jobs=get(a.repo,f'/actions/runs/{a.coverage_run}/jobs?per_page=100')
        need(jobs.get('total_count')==len(expect),'Expected explicit fast/deep native job coverage missing')
        for job in jobs.get('jobs',[]):
            need(job.get('name') in {'native-'+k for k in expect} and job.get('conclusion')=='success',
                 'Missing/failed native lane or unreviewed job')
        artifacts=get(a.repo,f'/actions/runs/{a.coverage_run}/artifacts?per_page=100')
        need(artifacts.get('total_count')==len(expect),'Missing native per-lane evidence archives')
        attempt=runs[3].get('run_attempt',1)
        verified=[]
        with tempfile.TemporaryDirectory(prefix='sfdt08-compare-') as tmp:
            for lane in expect:
                name=f'studyflow-sfdt08-{lane}-{a.coverage_run}-{attempt}'
                matches=[x for x in artifacts['artifacts'] if x.get('name')==name and not x.get('expired')]
                need(len(matches)==1 and 0<matches[0].get('size_in_bytes',0)<120*1024*1024,
                     'Missing/oversized native evidence '+lane)
                folder=Path(tmp)/lane
                folder.mkdir()
                proc=subprocess.run(['gh','run','download',str(a.coverage_run),'-R',a.repo,
                                     '-n',name,'-D',str(folder)],capture_output=True,text=True,
                                    timeout=180,check=False,env={**os.environ,'GH_PROMPT_DISABLED':'1'})
                need(proc.returncode==0,'Cannot download native evidence '+lane)
                paths=list(folder.rglob('*'))
                need(len(paths)<500 and all(not x.is_symlink() for x in paths),
                     'Unsafe native artifact member tree')
                verified.append(verify_members(folder,lane,source,a.coverage_run,attempt))
        record={'schema_version':1,'kind':'studyflow.sfdt08.same-sha-coverage-comparison',
                'source_sha':source,'status':'HOSTED_NATIVE_COVERAGE_VERIFIED_UNQUALIFIED',
                'lanes':verified,'legacy_run':a.legacy_run,'source_run':a.source_run,
                'expanded_run':a.expanded_run,'coverage_run':a.coverage_run,'scope':a.scope,
                'legacy_equivalent':False,'deep_equivalent':False,'cutover_authorized':False,
                'release_qualified':False,'device_qualified':False}
        if a.json:print(json.dumps(record,indent=2,sort_keys=True))
        else:print('SFDT08_SAME_SHA_COVERAGE_VERIFIED_UNQUALIFIED:',source[:12],len(verified),'lanes; cutover BLOCKED')
        return 0
    except (ComparisonError,OSError,ValueError,TypeError,KeyError,subprocess.TimeoutExpired) as exc:
        print('SFDT08_COMPARE_REFUSED:',str(exc)[:300],file=sys.stderr)
        return 2

if __name__=='__main__':raise SystemExit(run())
