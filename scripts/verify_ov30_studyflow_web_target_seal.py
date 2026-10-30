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
        require("npm run ov30:target-seal && " in str(scripts.get(name,"")) or "npm run ov30r2:proper-validation-seal && npm run ov30:target-seal && " in str(scripts.get(name,"")), f"web {name} must execute OV30 target seal first" )
    # Devtool resolves declared patterns against targets.studyflow_web.root (web/).
    # A web/ prefix here creates an erroneous web/web/ path in affected
    # selection and scheduler resource reservations.
    for token in ('src/**','tests/**','scripts/**','package.json','package-lock.json','tsconfig.json','sw.js'):
        require(token in web.get('inputs',[]), f'web target missing root-relative input {token}')
    require('dist/**' in web.get('outputs',[]), 'web target missing root-relative dist output')
    for token in ('web/src/**','web/tests/**','web/package.json','web/dist/**'):
        require(token not in web.get('inputs',[]) and token not in web.get('outputs',[]),
                f'web target repeats its web/ root prefix: {token}')
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
