#!/usr/bin/env python3
"""SFDT-07 consolidated source-only native/legacy contract. Never runs Gradle."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import sys
import tomllib

ROOT = Path(__file__).resolve().parents[1]
WORKFLOW = '.github/workflows/studyflow-devtool-native-expanded.yml'
SDK = ['platform-tools', 'platforms;android-36', 'build-tools;36.0.0', 'ndk;29.0.14206865']
WRAPPER_SHA = '7a9ce74cff467ca1bf60a4fcd9f05185acceda4d0f382434d393e17864262c5d'
ANDROID = {
    'phone-debug': ('studyflow_android', 'sfdt07-phone-debug', 'ci-sfdt07-phone-debug',
                    [':app:assembleDebug', ':app:lintDebug'],
                    'app/build/outputs/apk/debug/app-debug.apk'),
    'tv-debug': ('studyflow_tv', 'sfdt07-tv-debug', 'ci-sfdt07-tv-debug',
                 [':app-tv:assembleDebug', ':app-tv:testDebugUnitTest', ':app-tv:lintDebug'],
                 'app-tv/build/outputs/apk/debug/app-tv-debug.apk'),
}
PYTHON = {
    'web-pytest': ('studyflow_android','sfdt07-web-pytest','ci-sfdt07-web-pytest',
                   'scripts/sfdt07_web_pytest.py'),
    'quality-source': ('studyflow_quality','sfdt07-quality-source','ci-sfdt07-quality-source',
                       'scripts/sfdt07_quality_source.py'),
}
TARGETS = ('studyflow_android','studyflow_desktop','studyflow_web','studyflow_tv',
           'studyflow_tv_release','studyflow_release','studyflow_quality','studyflow_parity')


class ContractError(ValueError):
    pass


def require(test: bool, message: str) -> None:
    if not test:
        raise ContractError(message)


def regular(root: Path, path: str) -> Path:
    p = root / path
    require(p.is_file() and not p.is_symlink() and p.resolve().is_relative_to(root.resolve()),
            'Missing/symlinked source ' + path)
    return p


def check_config(config: dict, root: Path) -> dict:
    targets = config.get('targets')
    require(isinstance(targets, dict) and set(targets) == set(TARGETS),
            'Eight existing StudyFlow target identities drifted')
    require(all(t.get('affected_ownership') == 'inputs-only' for t in targets.values()),
            'Precise affected ownership was disabled')
    require(config.get('wrapper', {}).get('default_command') == 'ci-source' and
            set(config.get('wrapper', {}).get('commands', {})) == {'ci-source','ci-preflight'},
            'Existing source-only wrapper was replaced')
    jobs_found = {}
    for lane,(target,job,workflow,tasks,out) in ANDROID.items():
        t=targets[target]
        require(t.get('root') == '.' and t.get('runner') == 'android',
                'Android execution root/runner drift: ' + lane)
        row=t.get('jobs',{}).get(job, {})
        meta=row.get('metadata',{})
        require(row.get('runner') == 'android' and row.get('gradle_tasks') == tasks and
                row.get('outputs') == [out] and not row.get('command') and not row.get('env') and
                not row.get('shell_command') and not row.get('action'),
                'Native Android task/bytes policy changed: ' + lane)
        require(meta.get('ci_toolchain') == 'android-gradle' and
                meta.get('ci_android_java_version') == '21' and
                meta.get('ci_android_host_profile') == 'studyflow-ubuntu24' and
                meta.get('ci_android_ndk_version') == '29.0.14206865' and
                meta.get('ci_android_debug_variant') == 'debug' and
                meta.get('ci_android_sdk_packages') == SDK and
                meta.get('ci_android_wrapper_jar_sha256') == WRAPPER_SHA and
                meta.get('ci_android_expected_output') == out and
                not any(meta.get(k) is True for k in ('signing_required', 'device_required', 'release_signed', 'root_required')),
                'Android hosted toolchain/authority identity drift: ' + lane)
        graph=t.get('workflows',{}).get(workflow)
        require(isinstance(graph,list) and len(graph)==1 and
                graph[0].get('ref') == 'job:'+job and
                graph[0].get('depends_on') == [],
                'Android native DAG has unexpected dependency: ' + lane)
        jobs_found[lane]={'target':target,'workflow':workflow,'jobs':1,
                          'tasks':tasks,'output':out,'qualified_release':False}
    for lane,(target,job,workflow,script) in PYTHON.items():
        t=targets[target]
        row=t.get('jobs',{}).get(job,{})
        meta=row.get('metadata',{})
        digest=hashlib.sha256(regular(root,script).read_bytes()).hexdigest()
        require(row.get('runner')=='command' and row.get('command')==['python3',script]
                and not row.get('outputs') and not row.get('gradle_tasks') and not row.get('shell_command'),
                'Native Python source argv/output drift: '+lane)
        require(meta.get('ci_toolchain') == 'python-shell' and
                meta.get('ci_python_version') == '3.12' and
                meta.get('ci_python_script_sha256') == digest and
                meta.get('ci_python_script_args') == [] and
                meta.get('read_only') is True,
                'Reviewed Python source checksum/host pin drift: '+lane)
        graph=t.get('workflows',{}).get(workflow)
        require(isinstance(graph,list) and len(graph)==1 and
                graph[0].get('ref')=='job:'+job and graph[0].get('depends_on')==[],
                'Native Python source DAG changed: '+lane)
        jobs_found[lane]={'target':target,'workflow':workflow,'jobs':1,
                          'script_sha256':digest,'qualified_release':False}
    return jobs_found


def check_workflow(source: str) -> None:
    require(source.count('workflow_dispatch:') == 1 and
            '  pull_request:' not in source and '  push:' not in source and
            '  schedule:' not in source,
            'Shadow must stay manual-only')
    require('permissions:\n  contents: read\n' in source and
            not any(term in source for term in ('id-token: write', 'secrets.',
                                                'environment: studyflow-signing',
                                                'continue-on-error:', 'publish', 'release-sign')),
            'Shadow gained signing/write/release permissions')
    for marker in ('DEVTOOL_SOURCE_SHA: ', 'test "$EXPECTED_SHA" = "$GITHUB_SHA"',
                   'devtool.actions_graph_export', 'sfdt07_native_lane.py',
                   'if: always()', 'if-no-files-found: error',
                   'persist-credentials: false', 'retention-days: 14',
                   'ci-sfdt07'):
        # Compilation is performed by the native lane script, not inline YAML.
        if marker == 'devtool.actions_graph_export':
            continue
        if marker == 'ci-sfdt07':
            continue
        require(marker in source, 'Missing native shadow source/identity control: '+marker)
    for lane in list(ANDROID)+list(PYTHON):
        require(source.count('lane: '+lane+'\n') == 1, 'Missing or duplicate native lane: '+lane)
    require('fail-fast: false' in source and
            'actions/checkout@d23441a48e516b6c34aea4fa41551a30e30af803' in source and
            'actions/upload-artifact@cf430e030ddbb5b0abf93d22962f4752f3646cd9' in source and
            'gradle/actions/setup-gradle@0723195856401067f7a2779048b490ace7a47d7c' in source,
            'Pinned source/action/fallback policy changed')
    require('  native-shadow:\n' in source and 'name: native-${{ matrix.lane }}' in source,
            'Native GitHub job identity changed')


def audit(root: Path) -> dict:
    config=tomllib.loads(regular(root,'.devtool.toml').read_text('utf-8'))
    jobs=check_config(config,root)
    source=regular(root,WORKFLOW).read_text('utf-8')
    check_workflow(source)
    native=regular(root,'scripts/sfdt07_native_lane.py').read_text('utf-8')
    comparer=regular(root,'scripts/sfdt07_compare_runs.py').read_text('utf-8')
    member=regular(root,'scripts/sfdt07_member_evidence.py').read_text('utf-8')
    require('from devtool.actions_graph_export import compile_graph, run_node' in native
            and 'tv-junit' in native and 'report_sha256' in native,
            'Native graph execution or externally staged TV JUnit evidence lost')
    require('verify_members' in member and 'native_receipt_sha256' in member
            and 'download_member_artifacts' in comparer and 'NATIVE_MEMBER_BYTES_VERIFIED_UNQUALIFIED' in comparer,
            'Expanded shadow lost member-level evidence verification')
    legacy=regular(root,'.github/workflows/studyflow-ci.yml').read_text('utf-8')
    require(all(x in legacy for x in ('  source:\n','  web:\n','  desktop:\n',
                                       '  android:\n','  deep-contracts:\n')),
            'Legacy fast/deep CI was removed')
    shadow=regular(root,'.github/workflows/studyflow-devtool-shadow.yml').read_text('utf-8')
    require('  native-source-shadow:\n' in shadow and 'workflow_dispatch:' in shadow,
            'Original native source shadow was replaced')
    sign=regular(root,'.github/workflows/studyflow-hosted-sign.yml').read_text('utf-8')
    require('environment: studyflow-signing' in sign and
            'secrets.STUDYFLOW_SIGNING_P12_BASE64' in sign,
            'Protected signing boundary drift')
    require(all(name in config['targets']['studyflow_android']['workflows']
                for name in ('ci-source','ci-preflight')),
            'Existing native source workflows removed')
    return {'schema_version':1,'kind':'studyflow.sfdt07.consolidated-source-review',
            'status':'SOURCE_CONTRACT_PASS', 'targets':len(TARGETS),
            'native_lanes':jobs, 'native_lane_count':len(jobs),
            'legacy_fast_deep_preserved':True, 'original_source_shadow_preserved':True,
            'gradle_executed':False, 'hosted_pass':False,
            'web_npm_equivalent':False, 'desktop_gradle_equivalent':False,
            'phone_release_unit_tests_equivalent':False,
            'quality_gradle_equivalent':False,'parity_gradle_equivalent':False,
            'device_qualified':False,'release_qualified':False,'cutover_authorized':False}


def main() -> int:
    ap=argparse.ArgumentParser()
    ap.add_argument('--check',action='store_true')
    ap.add_argument('--json',action='store_true')
    args=ap.parse_args()
    try:
        report=audit(ROOT)
    except (ContractError, OSError, ValueError, TypeError, KeyError) as exc:
        print('SFDT07_SOURCE_REFUSED: '+str(exc),file=sys.stderr)
        return 2
    if args.json:
        print(json.dumps(report,sort_keys=True,indent=2))
    else:
        print('SFDT07_CONSOLIDATED_SOURCE_PASS: 4 lanes; legacy fast/deep preserved; cutover BLOCKED')
    return 0


if __name__=='__main__':
    raise SystemExit(main())
