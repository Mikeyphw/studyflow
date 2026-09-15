#!/usr/bin/env python3
from __future__ import annotations
import json, tomllib
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
TARGET = 'studyflow_desktop'
TASK = 'verifyOv30StudyflowDesktopTargetSeal'
MUST_TASKS = ["verifyStudyFlowDesktopAllContracts", "verifyDesktopDov23NativeDesktopIntegration", "verifyOv28LanguageReviewDesktopSpecializedParity", "verifyOv30StudyflowDesktopTargetSeal"]
FORBID_TASKS = [":app:assembleDebug", ":app:assembleRelease", ":app-tv:assembleDebug"]
REQUIRED_FILES = ["desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopLocalization.kt"]
FAIL=[]
def require(c,m):
    if not c: FAIL.append(m)

def task_values(block):
    tasks = block.get('tasks', {}) if isinstance(block, dict) else {}
    out=[]
    for key in ('build','test','lint'):
        value = tasks.get(key, [])
        if isinstance(value, list): out.extend(str(x) for x in value)
    return out

def phase_values(block):
    validation = block.get('validation', {}) if isinstance(block, dict) else {}
    phases = validation.get('phases', {}) if isinstance(validation, dict) else {}
    out=[]
    for value in phases.values():
        if isinstance(value, list): out.extend(str(x) for x in value)
    return out

def main():
    devtool=tomllib.loads((ROOT/'.devtool.toml').read_text(encoding='utf-8'))
    target=devtool.get('targets',{}).get(TARGET,{})
    require(bool(target), f'{TARGET} target must exist')
    require(target.get('runner') == 'gradle', f'{TARGET} runner drifted')
    require(target.get('platform') == 'desktop', f'{TARGET} platform drifted')
    if 'None' != 'None':
        require(target.get('android',{}).get('variant') == None, f'{TARGET} Android variant drifted')
    values = task_values(target) + phase_values(target)
    for token in MUST_TASKS:
        require(token in values, f'{TARGET} missing required task/phase {token}')
    task_block = '\n'.join(task_values(target))
    for token in FORBID_TASKS:
        require(token not in task_block, f'{TARGET} must not include {token} in normal task lists')
    build = (ROOT/'build.gradle.kts').read_text(encoding='utf-8')
    require(f'tasks.register<Exec>("{TASK}")' in build, f'Gradle task {TASK} missing')
    require(f'scripts/verify_ov30_{TARGET}_target_seal.py' in build, f'Gradle task {TASK} must call its target verifier')
    for path in REQUIRED_FILES:
        require((ROOT/path).is_file(), f'required target source/doc missing: {path}')
    doc = ROOT / ('docs/remediation/OV30_' + TARGET.upper() + '_TARGET_SEAL.md')
    require(doc.is_file(), f'OV30 target remediation doc missing: {doc.relative_to(ROOT)}')
    if FAIL:
        print('OV30_' + TARGET.upper() + '_TARGET_SEAL: FAIL')
        for f in FAIL: print('-', f)
        raise SystemExit(1)
    print('OV30_' + TARGET.upper() + '_TARGET_SEAL: PASS')
if __name__ == '__main__': main()
