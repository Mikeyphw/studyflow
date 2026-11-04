#!/usr/bin/env python3
"""Source-reviewed, GitHub-only StudyFlow Devtool native DAG job argv bridge.

One native Devtool target-owned job is the execution owner. This bridge only
performs a *fixed* reviewed argument plan; it does not generate a workflow,
schedule jobs, or report remote/release qualification.
"""
from __future__ import annotations

import os
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
DEEP = ('android-contracts','tv-contracts','desktop-contracts',
        'quality-contracts','parity-contracts')
MODES = ('web-full','desktop-fast',*DEEP)


def verify_host(mode: str) -> None:
    if mode not in MODES:
        raise ValueError('SFDT08 unknown reviewed bridge operation')
    if (os.environ.get('GITHUB_ACTIONS') != 'true' or
            os.environ.get('CI') != 'true' or
            os.environ.get('RUNNER_OS') != 'Linux' or
            os.environ.get('RUNNER_ARCH') != 'X64'):
        raise ValueError('SFDT08 Gradle/npm bridge requires genuine hosted Linux GitHub Actions')
    sha = os.environ.get('GITHUB_SHA','')
    if not re.fullmatch('[a-f0-9]{40}',sha) or not os.environ.get('GITHUB_RUN_ID','').isdecimal():
        raise ValueError('SFDT08 source/run identity missing')
    p=subprocess.run(['git','rev-parse','HEAD'],cwd=ROOT,stdout=subprocess.PIPE,
                     stderr=subprocess.PIPE,text=True,check=False,timeout=12)
    if p.returncode or p.stdout.strip()!=sha:
        raise ValueError('SFDT08 actual checkout differs from GitHub SHA')
    if any(os.environ.get(x) for x in ('STUDYFLOW_RELEASE_STORE_PASSWORD',
               'STUDYFLOW_RELEASE_KEY_PASSWORD','STUDYFLOW_RELEASE_STORE_FILE')):
        raise ValueError('SFDT08 unsigned shadow refuses release signing credentials')


def run(argv: list[str], *, cwd: Path = ROOT) -> None:
    if cwd.is_symlink() or not cwd.is_dir() or not cwd.resolve().is_relative_to(ROOT):
        raise ValueError('SFDT08 unsafe command directory')
    print('SFDT08_REVIEWED_COMMAND:',repr(argv),'cwd=',cwd.relative_to(ROOT),flush=True)
    proc=subprocess.run(argv,cwd=cwd,check=False,timeout=5400)
    if proc.returncode:
        raise RuntimeError('SFDT08 reviewed command exited nonzero: '+str(proc.returncode))


def perform(mode: str) -> None:
    verify_host(mode)
    if mode=='desktop-fast':
        run(['./gradlew','--no-daemon','--max-workers=2',
             ':desktopApp:classes',':desktopApp:test'])
    elif mode=='web-full':
        web=ROOT/'web'
        node=subprocess.run(['node','--version'],capture_output=True,text=True,timeout=10)
        if node.returncode or not re.fullmatch(r'v22\.\d+\.\d+',node.stdout.strip()):
            raise ValueError('SFDT08 hosted Web requires Node 22')
        run(['npm','ci','--ignore-scripts','--no-audit','--no-fund'],cwd=web)
        for op in ('test','lint','build','release:check'):
            run(['npm','run',op],cwd=web)
        # Native Devtool must parse the *actual* cases from a fresh generated
        # report, not from process exit or a prior legacy artifact.
        report=ROOT/'web/build/test-results/sfdt08-pytest'
        report.mkdir(parents=True,exist_ok=False)
        run(['python3','-m','pytest','web/tests','-q',
             '--junitxml='+str(report/'web-pytest-junit.xml')])
    else:
        # Existing canonical, source-reviewed StudyFlow Gradle task inventory
        # remains authoritative, including its unsigned Release JVM test guard.
        receipt=Path(os.environ['RUNNER_TEMP'])/'sfdt08'/('profile-'+mode+'.json')
        receipt.parent.mkdir(parents=True,exist_ok=True)
        if receipt.exists() or receipt.is_symlink():
            raise ValueError('SFDT08 stale profile receipt')
        run([sys.executable,'scripts/studyflow_ci_gradle_profile.py',
             '--run',mode,'--receipt',str(receipt)])


def main() -> int:
    try:
        if len(sys.argv)!=2:
            raise ValueError('SFDT08 bridge requires exactly one reviewed mode')
        perform(sys.argv[1])
    except (RuntimeError,ValueError,KeyError,OSError,subprocess.TimeoutExpired) as exc:
        print('SFDT08_BRIDGE_REFUSED:',str(exc)[:300],file=sys.stderr)
        return 2
    print('SFDT08_BRIDGE_HOSTED_PASS:',sys.argv[1],'release/device/cutover=UNQUALIFIED')
    return 0


if __name__=='__main__':
    raise SystemExit(main())
