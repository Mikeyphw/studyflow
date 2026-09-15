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
    target_cfg = devtool.get('targets',{}).get('studyflow_web',{})
    build = (ROOT/'build.gradle.kts').read_text(encoding='utf-8')
    hook_path = ROOT/'.devtool-artifact/validation/validate.sh'
    hook = hook_path.read_text(encoding='utf-8') if hook_path.is_file() else ''
    require(target_cfg, 'Devtool target studyflow_web missing')
    require('tasks.register<Exec>("verifyOv30R2StudyflowWebProperValidationSeal")' in build, 'Gradle task verifyOv30R2StudyflowWebProperValidationSeal missing')
    require('commandLine("bash", "scripts/validate_ov30r2_studyflow_web_proper_validation_seal.sh")' in build, 'Gradle task must call the target validation shell script')
    require((ROOT/'scripts/validate_ov30r2_studyflow_web_proper_validation_seal.sh').is_file(), 'target validation shell script missing')
    require((ROOT/'scripts/verify_ov30r2_studyflow_web_proper_validation_seal.py').is_file(), 'target validation python contract missing')
    require((ROOT/'REMEDIATION_OV_30R2_STUDYFLOW_WEB_PROPER_VALIDATION_SEAL.md').is_file(), 'root validation seal missing')
    require((ROOT/'docs/remediation/OV30R2_STUDYFLOW_WEB_PROPER_VALIDATION_SEAL.md').is_file(), 'remediation validation seal doc missing')
    if os.environ.get('OV30R2_ACTIVE_TARGET') == 'studyflow_web':
        require(hook_path.is_file(), 'active Devtool artifact validation hook missing')
        require('npm --prefix web run ov30r2:proper-validation-seal' in hook, 'active Devtool artifact validation hook must execute the proper target validation task')

    pkg = json.loads((ROOT/'web/package.json').read_text(encoding='utf-8'))
    scripts = pkg.get('scripts', {})
    require(scripts.get('ov30r2:proper-validation-seal') == 'python3 ../scripts/verify_ov30r2_studyflow_web_proper_validation_seal.py', 'web package proper validation seal npm script missing')
    for name in ('build','test','lint','release:check'):
        require(str(scripts.get(name, '')).startswith('npm run ov30r2:proper-validation-seal && '), f'web {name} must run proper validation seal before product command')
    require('npm run ov30r2:proper-validation-seal' in values(target_cfg), 'web Devtool target tasks must reference npm proper validation seal')
    require('npm run ov30r2:proper-validation-seal' in values(target_cfg.get('validation', {})), 'web Devtool validation phases must reference npm proper validation seal')
    if FAIL:
        print('OV30R2_STUDYFLOW_WEB_PROPER_VALIDATION_SEAL: FAIL')
        for f in FAIL: print('-', f)
        raise SystemExit(1)
    print('OV30R2_STUDYFLOW_WEB_PROPER_VALIDATION_SEAL: PASS')
if __name__ == '__main__':
    main()
