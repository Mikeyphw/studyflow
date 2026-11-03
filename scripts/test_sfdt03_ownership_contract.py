#!/usr/bin/env python3
"""SFDT-03 source-only negative contract tests. No Gradle, no source writes."""
from __future__ import annotations
import copy, sys, pathlib, tomllib
ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT/'scripts'))
import sfdt03_ownership_contract as S

def expect_reject(src: dict, code: str):
    try:
        S.audit_config(src)
    except S.OwnershipError:
        return
    raise AssertionError(f'Accepted unsafe ownership mutation: {code}')

def run():
    baseline=tomllib.loads((ROOT/'.devtool.toml').read_text(encoding='utf-8'))
    assert S.audit_config(baseline)['status']=='SOURCE_ONLY_PASS'
    mutations=[]
    def modify(name,fn):
        clone=copy.deepcopy(baseline)
        fn(clone)
        mutations.append((name,clone))
    modify('root-implicit-ownership',lambda c:c['targets']['studyflow_android'].pop('affected_ownership'))
    modify('root-execution-drift',lambda c:c['targets']['studyflow_desktop'].__setitem__('root','desktopApp'))
    modify('missing-web-root-config',lambda c:c['targets']['studyflow_web']['inputs'].remove('../.devtool.toml'))
    modify('missing-web-protocol',lambda c:c['targets']['studyflow_web']['inputs'].remove('../protocol/**'))
    modify('missing-parity-build-logic',lambda c:c['targets']['studyflow_parity']['inputs'].remove('build-logic/**'))
    modify('missing-parity-core',lambda c:c['targets']['studyflow_parity']['inputs'].remove('core/**'))
    modify('missing-shared-ci-workflow',lambda c:c['targets']['studyflow_tv']['inputs'].remove('.github/workflows/*.yml'))
    modify('missing-signing-source',lambda c:c['targets']['studyflow_release']['inputs'].remove('scripts/**'))
    modify('duplicate-input-pattern',lambda c:c['targets']['studyflow_android']['inputs'].append('app/**'))
    modify('injected-broad-pattern',lambda c:c['targets']['studyflow_web']['inputs'].append('../**'))
    modify('unrecognized-target',lambda c:c['targets'].__setitem__('extra',{}))
    modify('removed-target',lambda c:c['targets'].pop('studyflow_parity'))
    modify('wrong-root-mode',lambda c:c['targets']['studyflow_parity'].__setitem__('affected_ownership','root-and-inputs'))
    for name,bad in mutations:
        expect_reject(bad,name)
    assert set(S.projected_direct(baseline['targets'],'web/src/main.ts'))=={'studyflow_web','studyflow_parity'}
    assert not S.projected_direct(baseline['targets'],'README.md')
    assert set(S.projected_direct(baseline['targets'],'protocol/schema.json'))==set(S.TARGETS)
    print(f'SFDT03_OWNERSHIP_TESTS_PASS: {len(mutations)} negative + 4 positive')

if __name__=='__main__':
    run()
