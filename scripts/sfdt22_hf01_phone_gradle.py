#!/usr/bin/env python3
"""Hosted-only StudyFlow phone Gradle settings; no task, result, or policy bypass.

SFDT22 HF01: the phone lane timed out on Gradle silence after lintDebug,
while SFDT21's hosted no-configuration-cache, <=2 worker build had passed.
Constrain the native Devtool Gradle execution without changing its DAG.
"""
from __future__ import annotations

import argparse
import os
from pathlib import Path
import re
import subprocess
import sys

REPO = 'Mikeyphw/studyflow'
VALUES = {
    'org.gradle.parallel': 'false',
    'org.gradle.workers.max': '2',
    'org.gradle.configuration-cache': 'false',
}

class Refused(RuntimeError):
    pass

def need(condition: bool, reason: str) -> None:
    if not condition:
        raise Refused(reason)

def check_identity(env: dict, root: Path, head: str) -> Path:
    for key, value in (
        ('GITHUB_ACTIONS','true'), ('CI','true'),
        ('RUNNER_OS','Linux'), ('RUNNER_ARCH','X64'),
        ('GITHUB_REPOSITORY',REPO), ('STUDYFLOW_LANE','phone'),
    ):
        need(env.get(key) == value, 'Unreviewed hosted phone identity: '+key)
    need(env.get('GITHUB_EVENT_NAME') in ('push','pull_request','workflow_dispatch'),
         'Unreviewed hosted event')
    sha = env.get('GITHUB_SHA','')
    need(re.fullmatch(r'[0-9a-f]{40}',sha) is not None and sha == head,
         'Checkout and Actions source identity mismatch')
    need(root.is_dir() and (root/'.git').exists(), 'Not a Git checkout')
    for key in ('STUDYFLOW_RELEASE_STORE_PASSWORD', 'STUDYFLOW_RELEASE_KEY_PASSWORD',
                'STUDYFLOW_RELEASE_STORE_FILE'):
        need(not env.get(key), 'Release signing inputs are forbidden: '+key)
    user_home = Path(env.get('GRADLE_USER_HOME','/not-present'))
    need(user_home.is_absolute() and user_home.is_dir() and not user_home.is_symlink(),
         'Missing or symlinked hosted Gradle user home')
    home = user_home.resolve()
    need(home != root.resolve() and not home.is_relative_to(root.resolve()),
         'Gradle home must be outside the repository')
    need(home == Path(env.get('GRADLE_USER_HOME')).resolve(),
         'Noncanonical Gradle user home')
    need((home/'gradle.properties').parent == home,
         'Unsafe Gradle properties destination')
    return home/'gradle.properties'

def configure(env: dict, root: Path, head: str) -> dict:
    path = check_identity(env,root,head)
    need(not path.is_symlink(), 'Symlinked Gradle properties forbidden')
    previous = path.read_text(encoding='utf-8') if path.exists() else ''
    need(len(previous.encode('utf-8')) <= 128*1024,
         'Unbounded hosted Gradle properties')
    found = {}
    for line in previous.splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith(('#','!')):
            continue
        match = re.match(r'^([A-Za-z0-9_.-]+)\s*[=:]\s*(.*?)\s*$',stripped)
        if match and match[1] in VALUES:
            need(match[1] not in found,'Duplicate Gradle override property')
            found[match[1]] = match[2]
    for key, value in found.items():
        need(value == VALUES[key], 'Conflicting Gradle user property: '+key)
    missing = {key:value for key,value in VALUES.items() if key not in found}
    if missing:
        addition = ('\n' if previous and not previous.endswith('\n') else '')
        addition += '# SFDT22 HF01: hosted phone only, guarded native Devtool tasks unchanged\n'
        addition += ''.join(f'{key}={value}\n' for key,value in missing.items())
        # No file in checkout is modified; this is the runner's ephemeral HOME.
        with path.open('a',encoding='utf-8') as out:
            out.write(addition)
    actual = path.read_text(encoding='utf-8')
    for key,value in VALUES.items():
        need(re.search('(?m)^'+re.escape(key)+r'\s*=\s*'+re.escape(value)+r'\s*$',actual) is not None,
             'Hosted Gradle properties not persisted')
    return {'status':'SFDT22_HF01_HOSTED_PHONE_GRADLE_PASS',
            'parallel':False,'max_workers':2,'configuration_cache':False}

def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apply',action='store_true',required=True)
    parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    try:
        proc=subprocess.run(['git','-C',str(root),'rev-parse','HEAD'],
                            capture_output=True,text=True,timeout=15,check=False)
        need(proc.returncode==0, 'Cannot verify exact checkout SHA')
        result=configure(os.environ,root,proc.stdout.strip())
        print(result['status']+'; max_workers=2; parallel=false; configuration_cache=false')
        return 0
    except (Refused,OSError,UnicodeError) as exc:
        print('SFDT22_HF01_PHONE_REFUSED: '+str(exc),file=sys.stderr)
        return 2

if __name__=='__main__':
    raise SystemExit(main())
