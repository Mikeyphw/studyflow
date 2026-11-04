#!/usr/bin/env python3
"""SFDT07: inspect bounded extracted GitHub shadow artifacts, without trust promotion.

GitHub artifact transfer integrity is separate from source/graph/attempt binding.
This checker verifies the latter and member byte digests; it does not authenticate
signers, attest remote provenance, or authorize a CI cutover.
"""
from __future__ import annotations

import hashlib
import json
from pathlib import Path
import re
import stat
import xml.etree.ElementTree as ET
import zipfile

LANES = {
    'phone-debug': ('sfdt07-phone-debug', 'android-gradle', 'app/build/outputs/apk/debug/app-debug.apk'),
    'tv-debug': ('sfdt07-tv-debug', 'android-gradle', 'app-tv/build/outputs/apk/debug/app-tv-debug.apk'),
    'web-pytest': ('sfdt07-web-pytest', 'python-shell', None),
    'quality-source': ('sfdt07-quality-source', 'python-shell', None),
}
SHA40 = re.compile(r'[0-9a-f]{40}\Z')
SHA64 = re.compile(r'[0-9a-f]{64}\Z')
QUALITY = {
    'test_studyflow_ov01_r4_contracts.py',
    'test_sfdt03_ownership_contract.py',
    'test_sfdt06_shadow_contract.py',
    'test_sfdt06_same_sha_comparison.py',
}

class MemberError(ValueError):
    pass


def need(ok: bool, message: str) -> None:
    if not ok:
        raise MemberError(message)


def regular(root: Path, name: str, *, cap: int = 8 * 1024 * 1024) -> Path:
    need(isinstance(name, str) and name and not name.startswith('/') and '\\' not in name
         and all(s not in ('', '.', '..') for s in name.split('/')), 'Unsafe member path')
    root = root.resolve()
    p = root
    for part in name.split('/'):
        p = p / part
        need(not p.is_symlink(), 'Symlinked artifact member')
    need(p.resolve().is_relative_to(root) and p.is_file() and
         stat.S_ISREG(p.stat().st_mode) and 0 < p.stat().st_size <= cap,
         'Missing, nonregular or oversized artifact member: ' + name)
    return p


def doc(root: Path, name: str, *, cap: int = 200000) -> dict:
    p = regular(root, name, cap=cap)
    try: obj = json.loads(p.read_text(encoding='utf-8'))
    except (ValueError, UnicodeError) as exc: raise MemberError('Malformed JSON member: ' + name) from exc
    need(isinstance(obj, dict), 'Unexpected member JSON root: ' + name)
    return obj


def digest(data: dict) -> str:
    return hashlib.sha256(json.dumps(data, sort_keys=True, separators=(',', ':'),
                                      ensure_ascii=True).encode('ascii')).hexdigest()


def junit(root: Path, name: str, expected_sha: str) -> dict:
    need(isinstance(expected_sha, str) and SHA64.fullmatch(expected_sha) is not None,
         'Malformed expected JUnit digest')
    p = regular(root, name, cap=4*1024*1024)
    need(hashlib.sha256(p.read_bytes()).hexdigest() == expected_sha, 'JUnit member SHA256 mismatch')
    try: xml = ET.parse(p).getroot()
    except (ET.ParseError, OSError) as exc: raise MemberError('Malformed JUnit member') from exc
    suites = [xml] if xml.tag == 'testsuite' else list(xml)
    need(bool(suites) and all(s.tag == 'testsuite' for s in suites), 'Non-JUnit member')
    counts={k:0 for k in ('tests','failures','errors','skipped')}
    for suite in suites:
        cases=suite.findall('testcase')
        for k in counts:
            v=suite.get(k,'0')
            need(v.isdecimal() and int(v)<=100000, 'Invalid JUnit count')
            if k=='tests':
                need(len(cases)==int(v), 'JUnit count unsupported by testcase members')
            else:
                observed=sum(bool(case.findall({'failures':'failure','errors':'error','skipped':'skipped'}[k]))
                             for case in cases)
                need(observed==int(v), 'JUnit status count unsupported by testcase members')
            counts[k]+=int(v)
    return counts


def verify_members(root: Path, lane: str, *, source_sha: str, run_id: int, attempt: int) -> dict:
    need(lane in LANES and SHA40.fullmatch(source_sha) is not None and
         type(run_id) is int and run_id>0 and type(attempt) is int and attempt>0,
         'Invalid artifact source/run/lane identity')
    job, provider, output = LANES[lane]
    receipt=doc(root,'sfdt07/receipt.json')
    need(receipt.get('schema_version')==1 and receipt.get('kind')=='studyflow.sfdt07.native-hosted-shadow'
         and receipt.get('lane')==lane and receipt.get('status')=='NATIVE_SHADOW_PASSED'
         and receipt.get('native_graph_executed') is True,
         'Native lane receipt missing successful owned execution')
    for k in ('legacy_equivalent','deep_profiles_qualified','device_qualified',
              'signing_qualified','release_qualified','cutover_authorized'):
        need(receipt.get(k) is False, 'Unqualified native lane claimed authority: '+k)
    source=receipt.get('source') or {}
    need(source.get('source_sha')==source_sha and source.get('run_id')==run_id
         and source.get('attempt')==attempt and isinstance(source.get('source_tree'),str)
         and SHA40.fullmatch(source['source_tree']) is not None,
         'Native lane source/run/attempt mismatch')
    node=receipt.get('node') or {}
    jid=node.get('job_id')
    need(isinstance(jid,str) and re.fullmatch(r'node-[0-9]{2}(?:-[a-z0-9-]+)?',jid) is not None
         and node.get('job')==job and node.get('provider')==provider
         and node.get('output_paths')==([output] if output else [])
         and node.get('remote_authenticated') is False,
         'Native job/provider/output identity mismatch')
    native=doc(root,f'rm06-evidence/{jid}/node-evidence.json')
    claimed=native.get('receipt_sha256')
    content={k:v for k,v in native.items() if k!='receipt_sha256'}
    need(isinstance(claimed,str) and SHA64.fullmatch(claimed) is not None
         and digest(content)==claimed and node.get('native_receipt_sha256')==claimed,
         'Native typed evidence canonical digest mismatch')
    need(native.get('source_commit')==source_sha and native.get('source_tree')==source['source_tree']
         and native.get('run_id')==run_id and native.get('attempt')==attempt
         and native.get('job_id')==jid and native.get('provider')==provider
         and native.get('graph_sha256')==receipt.get('graph_sha256')
         and native.get('authenticated_remote') is False
         and native.get('claimed_remote_authority') is False
         and native.get('release_qualified') is False,
         'Native typed evidence source/attempt/trust binding mismatch')
    typed=native.get('typed_evidence') or {}
    identity=typed.get('source') or {}
    execution=typed.get('execution') or {}
    gates=typed.get('gates') or {}
    need(identity.get('commit')==source_sha and identity.get('tree')==source['source_tree']
         and execution.get('run_id')==run_id and execution.get('attempt')==attempt
         and gates.get('source',{}).get('state')=='PASS'
         and gates.get('build',{}).get('state')=='PASS'
         and gates.get('test',{}).get('state')=='NOT_RUN',
         'Native typed evidence contradicts canonical source/build/test gates')
    arts=typed.get('artifacts')
    need(isinstance(arts,list) and len(arts)==(1 if output else 0),
         'Native typed output inventory disagrees with declared graph')
    apk=None
    if output:
        row=arts[0]
        need(row.get('path')==output and row.get('role')=='node-output'
             and row.get('type')=='unverified-bytes' and row.get('signature_state')=='UNKNOWN'
             and SHA64.fullmatch(row.get('sha256','')) is not None,
             'Unexpected claimed APK source/digest/authority')
        path=regular(root,f'rm06-output/{jid}/payload/{output}',cap=150*1024*1024)
        need(hashlib.sha256(path.read_bytes()).hexdigest()==row['sha256'],
             'Native debug APK bytes differ from typed evidence')
        need(zipfile.is_zipfile(path), 'Native APK is not an archive')
        apk={'path':output,'sha256':row['sha256'],'size_bytes':path.stat().st_size,
             'signature_verified':False,'producer_authenticated':False}
    junit_result=None
    report=receipt.get('junit')
    if lane in ('tv-debug','web-pytest'):
        need(isinstance(report,dict) and report.get('verified') is True and
             isinstance(report.get('report_sha256'),dict) and report['report_sha256'],
             'Missing reviewed native JUnit receipt')
        summaries={k:0 for k in ('tests','failures','errors','skipped')}
        for name,sha in report['report_sha256'].items():
            need(isinstance(name,str) and name.startswith('TEST-') and name.endswith('.xml')
                 if lane=='tv-debug' else name=='web-pytest-junit.xml',
                 'Unexpected native JUnit filename')
            path=('sfdt07/tv-junit/' if lane=='tv-debug' else 'sfdt07/')+name
            counts=junit(root,path,sha)
            for k in summaries:summaries[k]+=counts[k]
        need(all(report.get(k)==v for k,v in summaries.items()) and
             summaries['tests']>summaries['skipped'] and
             summaries['failures']==0 and summaries['errors']==0,
             'Native JUnit summary differs from actual report members')
        junit_result={**summaries,'reports':len(report['report_sha256'])}
    else:
        need(report is None, 'Unexpected JUnit claim from non-test native lane')
    quality=None
    if lane=='quality-source':
        q=doc(root,'sfdt07/quality-source-checks.json')
        rows=q.get('verifiers')
        need(q.get('schema_version')==1 and q.get('kind')=='studyflow.sfdt07.source-quality-subset'
             and q.get('gradle_executed') is False and q.get('full_quality_equivalence') is False
             and q.get('release_qualified') is False and isinstance(rows,list)
             and {r.get('script') for r in rows if isinstance(r,dict)}==QUALITY
             and len(rows)==len(QUALITY) and
             all(r.get('exit_code')==0 and SHA64.fullmatch(r.get('sha256','')) is not None for r in rows),
             'Invalid source-only quality evidence inventory')
        quality={'source_verifiers':len(rows),'full_quality_equivalence':False}
    return {'status':'MEMBER_BYTES_VERIFIED_UNQUALIFIED','lane':lane,
            'source_sha':source_sha,'run_id':run_id,'attempt':attempt,
            'native_receipt_sha256':claimed,'apk':apk,'junit':junit_result,
            'quality':quality,'remote_authenticated':False,'cutover_authorized':False,
            'release_qualified':False}
