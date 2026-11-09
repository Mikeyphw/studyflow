#!/usr/bin/env python3
"""StudyFlow: exact-SHA, fail-closed targeted source checks (hosted only for builds).

Mirrors the principle behind AuroraStore-Standalone's targeted CI while
preserving StudyFlow's independent SFDT22 PR and Devtool qualification gates.
"""
from __future__ import annotations
import argparse
import json
from pathlib import Path
import re
import subprocess
import sys

REPO = Path(__file__).resolve().parents[2]
SCRIPT = 'scripts/ci/studyflow_ci_policy.py'
SELF_TEST = 'scripts/ci/test_studyflow_ci_policy.py'
WF = '.github/workflows/studyflow-targeted.yml'
DOC = 'docs/actions/STUDYFLOW_TARGETED_CI.md'
SHA40 = re.compile(r'[0-9a-f]{40}\Z')
# These are mandatory historical source contracts. An absent one is a FAIL, never a skip.
BASELINE = ('scripts/test_sfdt22_hf22_contract.py',
            'scripts/test_sfdt22_hf23_contract.py',
            'scripts/test_sfdt22_hf25_contract.py',
            'scripts/test_sfdt22_hf26_fast_contract.py',
            'scripts/test_sfdt22_hf27_contract.py', SELF_TEST)
GROUPS = {
    'database': ('scripts/test_sfdt22_hf21_contract.py', 'scripts/test_sfdt22_hf23_contract.py',
                 'scripts/test_sfdt22_hf24_contract.py', 'scripts/test_sfdt22_hf27_contract.py'),
    'content': ('scripts/test_sfdt22_hf13_contract.py','scripts/test_sfdt22_hf20_contract.py',
                'scripts/test_sfdt22_hf24_contract.py', 'scripts/test_sfdt22_hf25_contract.py',
                'scripts/test_sfdt22_hf27_contract.py'),
    'sessions': ('scripts/test_sfdt22_hf16_contract.py','scripts/test_sfdt22_hf17_contract.py',
                 'scripts/test_sfdt22_hf18_contract.py','scripts/test_sfdt22_hf19_contract.py'),
    'ui': ('scripts/test_sfdt22_hf20_contract.py','scripts/test_sfdt22_hf25_contract.py'),
    'hosted': ('scripts/test_sfdt22_emulator_contract.py',
               'scripts/test_sfdt22_hf22_contract.py','scripts/test_sfdt22_hf26_fast_contract.py'),
}
# HF26 deliberately uses the `_fast_contract.py` filename. This list is
# source-bound to the actually reviewed historical scripts: a phantom filename
# would fail every CI-sensitive targeted push before Android even starts.
CORE_SOURCE = tuple(sorted({f'scripts/test_sfdt22_hf{n:02d}_contract.py'
                            for n in (1, *range(2, 14), *range(15, 26), 27)}
                           | {'scripts/test_sfdt22_hf26_fast_contract.py',
                              'scripts/test_sfdt22_emulator_contract.py'}))
ANDROID_PREFIX = ('app/', 'app-tv/', 'core/', 'feature/', 'build-logic/', 'gradle/')
CI_PREFIX = ('.github/', 'scripts/ci/', 'scripts/studyflow_ci_',
             'scripts/sfdt22_', 'scripts/test_sfdt22_', 'devtool',
             'build.gradle', 'settings.gradle', 'gradle.properties')
DB_PREFIX = ('core/database/', 'core/data/', 'core/model/', 'core/domain/')
CONTENT_MATCH = ('subject','topic','content','syllabus','studyplan','study_plan','studyflowentities')


def git(*args: str, cwd: Path = REPO) -> str:
    p = subprocess.run(['git', *args], cwd=cwd, capture_output=True, text=True)
    if p.returncode:
        raise RuntimeError('STUDYFLOW_TARGETED_GIT_REFUSED')
    return p.stdout.strip()


def diff(base: str, head: str, cwd: Path = REPO) -> list[str]:
    if not SHA40.fullmatch(base) or not SHA40.fullmatch(head) or base == head:
        raise ValueError('STUDYFLOW_TARGETED_INVALID_RANGE')
    # Even a deleted file remains in the diff; no synthetic success for missing tests.
    return sorted(set(filter(None, git('diff','--name-only','--no-renames',
                                      '--diff-filter=ACDMRTUXB',base,head,cwd=cwd).splitlines())))


def classify(paths: list[str]) -> dict:
    if not paths or any(not p or p.startswith('/') or '\\' in p or '..' in p.split('/') for p in paths):
        raise ValueError('STUDYFLOW_TARGETED_UNSAFE_PATHS')
    groups: set[str] = set()
    needs_android = needs_tv = needs_web = needs_desktop = ci_sensitive = False
    full_source = False
    for path in paths:
        is_ci = path.startswith(CI_PREFIX) or path in (SCRIPT, SELF_TEST, WF)
        is_build = (path.startswith(('build-logic/', 'gradle/')) or
                    path.endswith('/build.gradle.kts') or
                    path in ('build.gradle.kts','settings.gradle.kts','gradle.properties'))
        if is_ci or is_build:
            ci_sensitive = True
            full_source = True
            groups.add('hosted')
        if is_build:
            if path.startswith('app/'): needs_android = True
            elif path.startswith('app-tv/'): needs_tv = True
            elif path.startswith('desktopApp/'): needs_desktop = True
            else: needs_android = needs_tv = needs_desktop = True
        if path.startswith('app/') or path.startswith(('feature/', 'core/', 'build-logic/')):
            needs_android = True
        if path.startswith('app-tv/'):
            needs_tv = True
        if path.startswith('web/') or path in ('package.json','package-lock.json'):
            needs_web = True
        if path.startswith('desktopApp/'):
            needs_desktop = True
        if path.startswith(DB_PREFIX) or '/database/' in path or '/room/' in path.lower():
            groups.add('database')
        if path.startswith(('feature/subjects/', 'feature/plans/', 'core/data/', 'core/domain/')) or any(x in path.lower() for x in CONTENT_MATCH):
            groups.add('content')
        if path.startswith('feature/sessions/') or 'session' in path.lower():
            groups.add('sessions')
        if path.startswith(('feature/','app/src/')) or 'navigation' in path.lower():
            groups.add('ui')
        if path.startswith(('app/src/androidTest/', 'scripts/sfdt22_', '.github/workflows/studyflow-sfdt22')):
            groups.add('hosted')
        # Unknown executable/code surface must fail closed to all source tests.
        if path.endswith(('.kt','.kts','.java','.gradle','.py','.sh','.zsh','.yml','.yaml')) and not (
                path.startswith(('app/','app-tv/','core/','feature/','build-logic/',
                                 'scripts/','tests/','.github/','web/','desktopApp/'))
                or path in ('build.gradle.kts','settings.gradle.kts')):
            full_source = True
            groups.add('hosted')
            needs_android = True
    tests = set(BASELINE)
    if full_source:
        tests.update(CORE_SOURCE)
    else:
        for group in groups:
            tests.update(GROUPS.get(group,()))
    return {
        'groups': sorted(groups) or ['baseline'],
        'tests': sorted(tests),
        'changed_files': len(paths),
        'needs_android': needs_android,
        'needs_tv': needs_tv,
        'needs_web': needs_web,
        'needs_desktop': needs_desktop,
        'ci_sensitive': ci_sensitive,
        'full_source': full_source,
    }


def source_check(repo: Path = REPO) -> None:
    wf = (repo/WF).read_text()
    oldfull = (repo/'.github/workflows/studyflow-sfdt22-hosted-journeys.yml').read_text()
    assert 'on:\n  pull_request:\n  push:' in oldfull, 'mandatory full PR gate missing'
    for lit in ('needs: [source, emulator]', 'api-level: 36', 'seven unique screenshots'):
        assert lit in oldfull, 'full gate weakened: '+lit
    for lit in ('name: StudyFlow — Targeted CI','pull_request:','workflow_dispatch:',
                'Source-change classification', 'Targeted source contracts',
                'Targeted Android APK and instrumentation compilation',
                'Targeted TV build','Targeted desktop tests','Targeted web tests',
                'Targeted required-result gate','fetch-depth: 0',
                'test "$EXPECTED_SHA" = "$(git rev-parse HEAD)"',
                '--github-output "$GITHUB_OUTPUT"',
                'scripts/ci/studyflow_ci_policy.py', 'needs.scope.outputs.needs_android',
                'needs.source.result', 'needs.android.result',
                'needs.tv.result', 'needs.desktop.result','needs.web.result'):
        assert lit in wf, 'target workflow guard absent: '+lit
    for bad in ('continue-on-error:', 'pull_request_target:', 'secrets: inherit',
                'contents: write', 'id-token: write', 'allow-failure'):
        assert bad not in wf, 'target workflow weakens trust: '+bad
    print('STUDYFLOW_TARGETED_SOURCE_CHECK_PASS')


def run_selected(plan: dict, repo: Path = REPO) -> None:
    for path in plan['tests']:
        target=repo/path
        if not target.is_file():
            raise RuntimeError('STUDYFLOW_TARGETED_MISSING_MANDATORY_SOURCE_TEST: '+path)
        print('STUDYFLOW_TARGETED_TEST: '+path,flush=True)
        subprocess.run([sys.executable,'-B',str(target)],cwd=repo,check=True)
    subprocess.run([sys.executable,'-B',str(repo/'scripts/sfdt22_emulator_verify.py'),
                    '--source-check'],cwd=repo,check=True)
    source_check(repo)
    print('STUDYFLOW_TARGETED_SOURCE_PASS: '+str(len(plan['tests']))+' contracts',flush=True)


def main() -> None:
    cli=argparse.ArgumentParser()
    sub=cli.add_subparsers(dest='action',required=True)
    for name in ('classify','run-targeted'):
        p=sub.add_parser(name)
        p.add_argument('--base',required=True)
        p.add_argument('--head',required=True)
        if name=='classify':p.add_argument('--github-output',type=Path,required=True)
    sub.add_parser('source-check')
    args=cli.parse_args()
    if args.action=='source-check':
        source_check();return
    changed=diff(args.base,args.head)
    plan=classify(changed)
    print('STUDYFLOW_TARGETED_PLAN: '+json.dumps(plan,sort_keys=True),flush=True)
    if args.action=='classify':
        with args.github_output.open('a') as out:
            out.write('base_sha='+args.base+'\nhead_sha='+args.head+'\n')
            for name in ('needs_android','needs_tv','needs_web','needs_desktop','ci_sensitive'):
                out.write(name+'='+str(plan[name]).lower()+'\n')
            out.write('groups='+','.join(plan['groups'])+'\n')
    else:
        run_selected(plan)

if __name__=='__main__':
    try:main()
    except (RuntimeError,ValueError,AssertionError,subprocess.CalledProcessError) as e:
        print('STUDYFLOW_TARGETED_REFUSED: '+str(e),file=sys.stderr)
        sys.exit(2)
