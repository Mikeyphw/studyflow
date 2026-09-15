#!/usr/bin/env python3
from __future__ import annotations
import json, tomllib
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
FAIL=[]
def require(c,m):
    if not c: FAIL.append(m)

def main():
    devtool=tomllib.loads((ROOT/'.devtool.toml').read_text(encoding='utf-8'))
    web=devtool.get('targets',{}).get('studyflow_web',{})
    pkg=json.loads((ROOT/'web/package.json').read_text(encoding='utf-8'))
    scripts=pkg.get('scripts',{})
    require(web.get('runner')=='node','studyflow_web must keep node runner')
    require(web.get('platform')=='web','studyflow_web must keep web platform')
    require(web.get('root')=='web','studyflow_web must stay rooted at web/')
    require('ov30:target-seal' in scripts,'web package must expose ov30:target-seal')
    for name in ('build','test','lint','release:check'):
        require(str(scripts.get(name,'')).startswith('npm run ov30:target-seal && '), f'web {name} must execute OV30 target seal first')
    for token in ('src/**','tests/**','package.json'):
        require(token in web.get('inputs',[]), f'web target missing input {token}')
    require((ROOT/'web/scripts/validate_web.py').is_file(), 'web validator missing')
    require((ROOT/'web/scripts/check_release.mjs').is_file(), 'web release checker missing')
    require('assert_runtime_dist_fresh.mjs' in (ROOT/'scripts/verify_web_stale_dist_release_integrity.py').read_text(encoding='utf-8'), 'web stale dist gate must execute runtime/dist freshness check')
    require((ROOT/'docs/remediation/OV30_STUDYFLOW_WEB_TARGET_SEAL.md').is_file(), 'web OV30 target doc missing')
    if FAIL:
        print('OV30_STUDYFLOW_WEB_TARGET_SEAL: FAIL')
        for f in FAIL: print('-', f)
        raise SystemExit(1)
    print('OV30_STUDYFLOW_WEB_TARGET_SEAL: PASS')
if __name__ == '__main__': main()
