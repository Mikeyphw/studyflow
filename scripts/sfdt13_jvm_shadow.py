#!/usr/bin/env python3
"""SFDT13: source-guarded, non-release native JVM evidence for core:model.

The actual Gradle build is performed by a separate GitHub Actions step. This
script never invokes Gradle and never claims RM21 acceptance without a receipt.
"""
from __future__ import annotations

import argparse
from hashlib import sha256
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
DEVTOOL = 'bf8ed44753f1bdbe066cac3d4ea3f748e4af2a0b'
MODULE = ':core:model'
SHA = re.compile(r'[a-f0-9]{40}\Z')
DIGEST = re.compile(r'[a-f0-9]{64}\Z')

class GateError(ValueError):
    pass


def need(ok: bool, reason: str) -> None:
    if not ok: raise GateError(reason)


def git(*args: str, root: Path = ROOT) -> str:
    p = subprocess.run(['git', '-C', str(root), *args], capture_output=True, text=True, timeout=20)
    need(p.returncode == 0, 'Git source verification failed: '+p.stderr[-200:])
    return p.stdout.strip()


def source_check() -> dict:
    settings = (ROOT / 'settings.gradle.kts').read_text()
    module = (ROOT / 'core/model/build.gradle.kts').read_text()
    wrapper = (ROOT / 'gradle/wrapper/gradle-wrapper.properties').read_text()
    ci = (ROOT / '.github/workflows/studyflow-ci.yml').read_text()
    need('includeBuild("build-logic")' in settings, 'missing reviewed composite build')
    need(all(x in settings for x in ('GITHUB_ACTIONS', 'RUNNER_OS', 'CI', 'Linux')), 'GitHub-only Gradle guard drift')
    need('":core:model"' in settings, 'JVM source module missing')
    need('id("studyflow.kotlin.library")' in module, 'unexpected module plugin')
    need('gradle-9.7.1-' in wrapper, 'reviewed Gradle wrapper pin changed')
    need('name: StudyFlow CI' in ci and ':desktopApp:classes :desktopApp:test' in ci, 'legacy desktop CI displaced')
    wf = (ROOT / '.github/workflows/studyflow-sfdt13-jvm-hosted-shadow.yml').read_text()
    need('permissions:\n  contents: read' in wf, 'new workflow gained privileges')
    need('DEVTOOL_SHA: '+DEVTOOL in wf, 'Devtool source pin changed')
    need('core:model:jar' in wf and 'Gradle :core:model' in wf, 'JVM build selected wrong module')
    need(not any(s in wf for s in ('secrets.', 'id-token: write', 'gh release', 'continue-on-error:', 'git push ', 'gh pr merge')),
         'unreviewed workflow authority')
    qualification = (ROOT / '.github/workflows/studyflow-sfdt14-rm21-jvm-acceptance.yml').read_text()
    need('DEVTOOL_SHA: '+DEVTOOL in qualification, 'RM21 JVM Devtool SHA drift')
    need('independent_consumer_qualified' in qualification and '--gradle-version \'9.7.1\'' in qualification,
         'RM21 JVM exact source/hosted Gradle lane removed')
    need('permissions:\n  contents: read' in qualification, 'RM21 JVM workflow gained privileges')
    need(not any(s in qualification for s in ('secrets.', 'id-token: write', 'contents: write',
                                             'continue-on-error:', 'gh release', 'git push ', 'gh pr merge')),
         'RM21 JVM workflow unreviewed authority')
    return {'kind':'studyflow.sfdt13.jvm-source','selected_module':MODULE,
            'gradle_wrapper':'9.7.1','devtool_sha':DEVTOOL,
            'rm21_qualified':False,'release_qualified':False}


def sha_file(path: Path) -> str:
    return sha256(path.read_bytes()).hexdigest()


def verification_metadata(path: Path) -> dict:
    need(path.is_file() and not path.is_symlink(), 'dependency metadata missing or linked')
    data=path.read_bytes()
    need(256 <= len(data) <= 10*1024*1024, 'dependency metadata size invalid')
    need(b'<!DOCTYPE' not in data.upper() and b'<!ENTITY' not in data.upper(), 'unsafe dependency metadata XML')
    root=ET.fromstring(data)
    need(root.tag.rsplit('}',1)[-1]=='verification-metadata', 'wrong metadata root')
    children=list(root.iter())
    components=[x for x in children if x.tag.rsplit('}',1)[-1]=='component']
    digests=[x.attrib.get('value','') for x in children if x.tag.rsplit('}',1)[-1]=='sha256']
    need(components and digests and all(DIGEST.fullmatch(d) for d in digests),
         'metadata needs actual components with SHA-256 digests')
    return {'components':len(components),'sha256_entries':len(digests),
            'file_sha256':sha256(data).hexdigest(),'bytes':len(data)}


def admission(out: Path) -> dict:
    # Read-only RM12 review; its checked-in metadata requirement is never bypassed.
    from devtool.actions_rm12_jvm import JvmError, jvm_project
    identity = git('rev-parse','HEAD')
    try:
        reviewed = jvm_project(ROOT, manager='gradle', mixed_module=MODULE)
        status='SOURCE_REVIEW_ADMITTED_NATIVE_NOT_RUN'
        reason='RM12 project static review passed; hosted JAR pending'
        digest=reviewed['source_digest']
    except JvmError as exc:
        status='SOURCE_REVIEW_BLOCKED_NO_CUTOVER'
        reason=str(exc)
        digest=None
    evidence={'kind':'studyflow.sfdt13.rm12-source-admission', 'status':status,
              'reason':reason,'source_commit':identity,'devtool_sha':DEVTOOL,
              'selected_module':MODULE,'source_digest':digest,
              'rm21_qualified':False, 'release_qualified':False,
              'signing_qualified':False,'device_qualified':False}
    out.parent.mkdir(parents=True,exist_ok=True)
    out.write_text(json.dumps(evidence,sort_keys=True,indent=2)+'\n')
    print('SFDT13_RM12_SOURCE_ADMISSION:',status,reason)
    return evidence


def candidate(output: Path, jar_copy: Path) -> dict:
    from devtool.actions_rm12_jvm import verify_jar
    need(os.environ.get('CI')=='true' and os.environ.get('GITHUB_ACTIONS')=='true' and
         os.environ.get('RUNNER_OS')=='Linux', 'hosted Linux CI required for JVM candidate')
    expected = os.environ.get('CANDIDATE_SHA','')
    need(SHA.fullmatch(expected) is not None and git('rev-parse','HEAD')==expected, 'source commit drift')
    need(not git('status','--porcelain','--untracked-files=normal'), 'tracked/untracked source drift')
    directory = ROOT/'core/model/build/libs'
    jars=list(directory.glob('*.jar')) if directory.is_dir() else []
    need(len(jars)==1 and jars[0].is_file() and not jars[0].is_symlink(), 'requires exactly one genuine core:model JAR')
    jar=jars[0]
    result=verify_jar(jar)
    digest=sha_file(jar)
    need(result.get('sha256')==digest, 'RM12 artifact verification digest mismatch')
    jar_copy.parent.mkdir(parents=True,exist_ok=True)
    need(not jar_copy.exists(), 'existing output cannot be replaced')
    shutil.copyfile(jar,jar_copy)
    need(sha_file(jar_copy)==digest,'copied output byte mismatch')
    evidence={'kind':'studyflow.sfdt13.jvm-native-candidate','status':'NATIVE_JVM_JAR_BYTE_VERIFIED_NOT_RM21_QUALIFIED',
              'source_sha':expected,'source_tree':git('rev-parse','HEAD^{tree}'),
              'selected_module':MODULE,'tool':'Gradle wrapper 9.7.1 / JDK 21',
              'devtool_sha':DEVTOOL,'jar_file':jar.name,'jar_sha256':digest,
              'jar_bytes':jar.stat().st_size,'rm12_archive':result,
              'native_execution_completed':True,'rm21_qualified':False,
              'release_qualified':False,'signing_qualified':False,'device_qualified':False}
    output.parent.mkdir(parents=True,exist_ok=True)
    output.write_text(json.dumps(evidence,sort_keys=True,indent=2)+'\n')
    print('SFDT13_NATIVE_JVM_JAR_BYTES_VERIFIED_RM21_PENDING',digest)
    return evidence


def main()->int:
    p=argparse.ArgumentParser()
    g=p.add_mutually_exclusive_group(required=True)
    g.add_argument('--source-check',action='store_true')
    g.add_argument('--admission',action='store_true')
    g.add_argument('--candidate',action='store_true')
    g.add_argument('--verify-metadata',type=Path)
    p.add_argument('--out',type=Path)
    p.add_argument('--jar-copy',type=Path)
    a=p.parse_args()
    try:
        if a.source_check:result=source_check()
        elif a.admission:
            need(a.out is not None,'--out required');result=admission(a.out)
        elif a.candidate:
            need(a.out is not None and a.jar_copy is not None,'--out/--jar-copy required')
            result=candidate(a.out,a.jar_copy)
        else:result=verification_metadata(a.verify_metadata)
        if a.source_check or a.verify_metadata:print(json.dumps(result,sort_keys=True))
        return 0
    except (GateError, OSError, ValueError, ET.ParseError, subprocess.TimeoutExpired) as e:
        print('SFDT13_JVM_REFUSED:',str(e)[:360],file=sys.stderr)
        return 2

if __name__=='__main__':raise SystemExit(main())
