#!/usr/bin/env python3
"""SFDT08 conservative StudyFlow native coverage contract; never executes Gradle."""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path
import re
import sys
import tomllib

ROOT=Path(__file__).resolve().parents[1]
TARGETS={'studyflow_android','studyflow_tv','studyflow_desktop','studyflow_web',
         'studyflow_quality','studyflow_parity','studyflow_release','studyflow_tv_release'}
BRIDGE='scripts/sfdt08_hosted_bridge.py'
JUNIT={
 'phone-release-jvm':'app/build/test-results/testReleaseUnitTest',
 'desktop-fast':'desktopApp/build/test-results/test',
 'web-full':'web/build/test-results/sfdt08-pytest',
 'android-contracts':'app/build/test-results/testReleaseUnitTest',
 'tv-contracts':'app-tv/build/test-results/testDebugUnitTest',
 'desktop-contracts':'desktopApp/build/test-results/test',
}
LANE_TARGET={
 'phone-release-jvm':'studyflow_android','desktop-fast':'studyflow_desktop',
 'web-full':'studyflow_android','android-contracts':'studyflow_android',
 'tv-contracts':'studyflow_tv','desktop-contracts':'studyflow_desktop',
 'quality-contracts':'studyflow_quality','parity-contracts':'studyflow_parity',
}
WORKFLOW={
 'phone-release-jvm':'ci-sfdt08-phone-release-jvm',
 'desktop-fast':'ci-sfdt08-desktop-fast','web-full':'ci-sfdt08-web-full',
 **{'%s-contracts'%x:'ci-sfdt08-deep-'+x for x in ('android','tv','desktop','quality','parity')},
}
WRAPPER_SHA='7a9ce74cff467ca1bf60a4fcd9f05185acceda4d0f382434d393e17864262c5d'

class ContractError(ValueError):pass

def require(value:bool,why:str)->None:
    if not value:raise ContractError(why)

def _regular(root:Path,relative:str)->Path:
    p=root/relative
    require(p.is_file() and not p.is_symlink() and p.resolve().is_relative_to(root.resolve()),
            'missing/unsafe reviewed file: '+relative)
    return p

def audit(root:Path)->dict:
    config=tomllib.loads(_regular(root,'.devtool.toml').read_text(encoding='utf8'))
    require(set(config['targets'])==TARGETS,'eight target identities changed')
    for t in config['targets'].values():
        require(t['affected_ownership']=='inputs-only','existing affected ownership weakened')
    require(config['wrapper']['default_command']=='ci-source' and
            set(config['wrapper']['commands'])=={'ci-source','ci-preflight'},
            'existing CLI source workflows/wrapper changed')
    native=config['targets']['studyflow_android']['workflows']['ci-source']
    require(len(native)==4 and [x['id'] for x in native]==
            ['actions-contract','source-contract','sfdt-source-audit','github-gradle-policy'],
            'existing canonical 4-stage source DAG changed')
    bridge_sha=hashlib.sha256(_regular(root,BRIDGE).read_bytes()).hexdigest()
    require(hashlib.sha256(_regular(root,'gradle/wrapper/gradle-wrapper.jar').read_bytes()).hexdigest()==WRAPPER_SHA,
            'reviewed Gradle wrapper JAR bytes changed')
    for lane,target in LANE_TARGET.items():
        t=config['targets'][target]
        jobs=t.get('jobs',{})
        name='sfdt08-'+ ('deep-'+lane.removesuffix('-contracts') if lane.endswith('-contracts') else lane)
        job=jobs.get(name)
        require(isinstance(job,dict),'missing native lane job: '+lane)
        workflow=t['workflows'].get(WORKFLOW[lane])
        require(isinstance(workflow,list) and len(workflow)==1 and
                workflow[0].get('ref')=='job:'+name and workflow[0].get('depends_on')==[],
                'unreviewed native DAG ownership: '+lane)
        require(not job.get('outputs') and not job.get('shell_command') and not job.get('action') and
                not job.get('env') and not job.get('output_scopes'),
                'native test/verification lane cannot claim unreviewed output/env: '+lane)
        meta=job.get('metadata',{})
        require(meta.get('ci_junit_report_dir','')==JUNIT.get(lane,''),
                'missing/extra native JUnit test requirement: '+lane)
        if lane=='phone-release-jvm':
            require(job['runner']=='android' and job.get('gradle_tasks')==[':app:testReleaseUnitTest'] and
                    not job.get('command') and meta.get('ci_android_unsigned_release_jvm_test') is True and
                    meta.get('ci_android_host_profile')=='studyflow-ubuntu24' and
                    meta.get('ci_toolchain')=='android-gradle' and
                    meta.get('ci_android_wrapper_jar_sha256')==WRAPPER_SHA,
                    'phone unsigned Release JVM native provider contract drift: '+lane)
        else:
            require(job['runner']=='command' and job.get('command')==
                    ['python3',BRIDGE,lane] and
                    not job.get('gradle_tasks') and meta.get('ci_toolchain')=='python-shell' and
                    meta.get('ci_python_version')=='3.12' and
                    meta.get('ci_python_script_sha256')==bridge_sha and
                    meta.get('ci_python_script_args')==[lane],
                    'reviewed SHA-pinned Python/Gradle bridge drift: '+lane)
    legacy=_regular(root,'.github/workflows/studyflow-ci.yml').read_text()
    require(all(x in legacy for x in ('  source:\n','  web:\n','  desktop:\n','  android:\n','  deep-contracts:\n')),
            'existing authoritative legacy CI lanes removed')
    sign=_regular(root,'.github/workflows/studyflow-hosted-sign.yml').read_text()
    require('environment: studyflow-signing' in sign and
            'secrets.STUDYFLOW_SIGNING_P12_BASE64' in sign,
            'protected signer boundary changed')
    wf=_regular(root,'.github/workflows/studyflow-devtool-native-coverage.yml').read_text()
    for marker in ('workflow_dispatch:','expected_sha:','devtool_sha:',
                   'scope:','SFDT_LANE','scripts/sfdt08_native_coverage.py',
                   'if: always()','if-no-files-found: error','persist-credentials: false',
                   'permissions:\n  contents: read','setup-node@249970729cb0ef3589644e2896645e5dc5ba9c38'):
        require(marker in wf,'missing exact hosted source/evidence control: '+marker)
    require('cache: false' not in wf and 'secrets.' not in wf and 'id-token: write' not in wf and
            '\n  push:' not in wf and '\n  pull_request:' not in wf and
            '\n  schedule:' not in wf and 'continue-on-error:' not in wf,
            'native shadow has invalid credentials/cache/triggers/error policy')
    require(all(('\"lane\":\"'+lane+'\"') in wf for lane in LANE_TARGET),
            'hosted fast/deep lane matrix incomplete')
    return {'schema_version':1,'kind':'studyflow.sfdt08.native-coverage-source',
            'status':'SOURCE_ONLY_PASS','lanes':sorted(LANE_TARGET),
            'bridge_sha256':bridge_sha,'source_only':True,'gradle_executed':False,
            'hosted_results':'NOT_OBSERVED','deep_equivalent':False,
            'legacy_equivalent':False,'cutover_authorized':False,
            'release_qualified':False,'device_qualified':False}

def main(argv=None)->int:
    p=argparse.ArgumentParser()
    p.add_argument('--check',action='store_true');p.add_argument('--json',action='store_true')
    options=p.parse_args(argv)
    try:record=audit(ROOT)
    except (OSError,KeyError,ValueError,TypeError) as exc:
        print('SFDT08_SOURCE_REFUSED:',str(exc)[:300],file=sys.stderr);return 2
    if options.json:print(json.dumps(record,sort_keys=True,indent=2))
    else:print('SFDT08_SOURCE_ONLY_PASS: 8 hosted migration lanes; cutover BLOCKED; Gradle NOT_RUN')
    return 0
if __name__=='__main__':raise SystemExit(main())
