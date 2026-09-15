#!/usr/bin/env python3
from __future__ import annotations
import json
import os
import tomllib
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
FAIL=[]
def require(cond, msg):
    if not cond:
        FAIL.append(msg)
def values(obj):
    if isinstance(obj, dict):
        out=[]
        for v in obj.values(): out.extend(values(v))
        return out
    if isinstance(obj, list):
        out=[]
        for v in obj: out.extend(values(v))
        return out
    return [str(obj)]

def main():
    devtool = tomllib.loads((ROOT/'.devtool.toml').read_text(encoding='utf-8'))
    target_cfg = devtool.get('targets',{}).get('studyflow_parity',{})
    build = (ROOT/'build.gradle.kts').read_text(encoding='utf-8')
    hook = (ROOT/'.devtool-artifact/validation/validate.sh').read_text(encoding='utf-8')
    require(target_cfg, 'Devtool target studyflow_parity missing')
    require('tasks.register<Exec>("verifyOv30R2StudyflowParityProperValidationSeal")' in build, 'Gradle task verifyOv30R2StudyflowParityProperValidationSeal missing')
    require('commandLine("bash", "scripts/validate_ov30r2_studyflow_parity_proper_validation_seal.sh")' in build, 'Gradle task must call the target validation shell script')
    require((ROOT/'scripts/validate_ov30r2_studyflow_parity_proper_validation_seal.sh').is_file(), 'target validation shell script missing')
    require((ROOT/'scripts/verify_ov30r2_studyflow_parity_proper_validation_seal.py').is_file(), 'target validation python contract missing')
    require((ROOT/'REMEDIATION_OV_30R2_STUDYFLOW_PARITY_PROPER_VALIDATION_SEAL.md').is_file(), 'root validation seal missing')
    require((ROOT/'docs/remediation/OV30R2_STUDYFLOW_PARITY_PROPER_VALIDATION_SEAL.md').is_file(), 'remediation validation seal doc missing')
    if os.environ.get('OV30R2_ACTIVE_TARGET') == 'studyflow_parity':
        require('./gradlew --no-daemon verifyOv30R2StudyflowParityProperValidationSeal' in hook, 'active Devtool artifact validation hook must execute the proper target validation task')

    require('verifyOv30R2StudyflowParityProperValidationSeal' in values(target_cfg), 'Devtool target studyflow_parity must include verifyOv30R2StudyflowParityProperValidationSeal in tasks/validation phases')

    required_tasks = ['verifyOv30R2StudyflowAndroidProperValidationSeal', 'verifyOv30R2StudyflowDesktopProperValidationSeal', 'verifyOv30R2StudyflowWebProperValidationSeal', 'verifyOv30R2StudyflowTvProperValidationSeal', 'verifyOv30R2StudyflowReleaseProperValidationSeal', 'verifyOv30R2StudyflowTvReleaseProperValidationSeal', 'verifyOv30R2StudyflowQualityProperValidationSeal', 'verifyOv30R2StudyflowParityProperValidationSeal', 'verifyOv30R2AllTargetProperValidationSeals']
    for required in required_tasks:
        require((f'tasks.register<Exec>("{required}")' in build) or (f'tasks.register("{required}")' in build), f'aggregate requires Gradle task {required}')
    require('verifyOv30R2AllTargetProperValidationSeals' in values(target_cfg), 'parity target must include all-target R2 aggregate task')
    if FAIL:
        print('OV30R2_STUDYFLOW_PARITY_PROPER_VALIDATION_SEAL: FAIL')
        for f in FAIL: print('-', f)
        raise SystemExit(1)
    print('OV30R2_STUDYFLOW_PARITY_PROPER_VALIDATION_SEAL: PASS')
if __name__ == '__main__':
    main()
