#!/usr/bin/env python3
"""R4R5 regression tests, entirely source-only. No Gradle or package installs."""
from pathlib import Path
import ast
import json
import re
import tomllib

ROOT=Path(__file__).resolve().parents[1]

def must(condition,reason):
    if not condition: raise AssertionError(reason)

def main():
    must('python3 scripts/test_studyflow_ov01_r4r5_contracts.py' in (ROOT/'.github/workflows/studyflow-ci.yml').read_text(),'hosted source job forgot R4R5 regression gate')
    cfg=tomllib.loads((ROOT/'.devtool.toml').read_text())
    d=cfg['targets']['studyflow_desktop']
    for plan in (d['tasks']['test'],d['validation']['phases']['unit-test']):
        must(plan.count('verifyStudyFlowDesktopBootstrapScreens')==1,'desktop bootstrap owner missing or duplicate')
    q=cfg['targets']['studyflow_quality']['inputs']
    must('desktopApp/**' in q,'desktop quality target missing source input')
    src=(ROOT/'scripts/verify_formatting.py').read_text()
    roots=next((ast.literal_eval(n.value) for n in ast.parse(src).body if isinstance(n,ast.Assign) and any(isinstance(t,ast.Name) and t.id=='ROOTS' for t in n.targets)),())
    must('desktopApp' in roots,'desktop formatter root omitted')
    build=(ROOT/'app/build.gradle.kts').read_text()
    must('!name.contains("UnitTest")' in build,'JVM test packaging still requests signing')
    must('name != "packageReleaseResources"' in build,'JVM resource preparation still requests signing')
    for marker in ('name == "assembleRelease"','name == "bundleRelease"','name.startsWith("packageRelease")','name.startsWith("signRelease")','dependsOn(verifyPhoneReleaseSigningPreflight)'):
        must(marker in build,'signed packaging gate lost: '+marker)
    must('verifyPhoneReleaseSigningPreflight' in (ROOT/'scripts/ov01_release_gate.py').read_text() or 'signing' in (ROOT/'scripts/ov01_release_gate.py').read_text(),'original release gate missing')
    # Classify task names according to the exact source predicate: unsigned JVM tasks excluded,
    # while signed APK/AAB names still require a preflight.
    def requires_signing(name):
        return name in ('assembleRelease','bundleRelease') or (name.startswith('packageRelease') and name != 'packageReleaseResources' and 'UnitTest' not in name) or name.startswith('signRelease')
    for name in ('packageReleaseUnitTestForUnitTest','packageReleaseResources','testReleaseUnitTest','lintRelease','assembleDebug'):
        must(not requires_signing(name),'non-output test task would trigger signing: '+name)
    for name in ('assembleRelease','bundleRelease','packageRelease','packageReleaseUniversalApk','signReleaseBundle'):
        must(requires_signing(name),'release output unguarded: '+name)
    audit=json.loads((ROOT/'docs/roadmap/REL_FINAL_PROMISE_AUDIT.json').read_text())
    inv=audit['verifier_inventory'];legacy=set(inv['legacy_scripts']);original=set(inv['post_rel_final_verifiers']);extra=inv['additional_verifiers_since_rel_final_boundary']
    must(len(legacy)==213 and inv['legacy_total']==213,'legacy inventory rewritten')
    must(len(original)==16 and len(extra)==54 and len(set(extra))==54 and inv['additional_verifier_count']==54,'additional inventory drift')
    must(extra==sorted(extra),'additional verifier inventory must be sorted')
    must(not (legacy&original or legacy&set(extra) or original&set(extra)),'verifier cohorts overlap')
    scripts={x.name for x in (ROOT/'scripts').glob('verify_*.py')}-{'verify_rel_final_roadmap_promise_audit.py'}
    must(scripts==legacy|original|set(extra),'unclassified or missing verifier(s)')
    must(audit['claims']['release_ready'] is False and audit['claims']['release_evidence_complete'] is False,'fake release readiness')
    must('verifyRelFinalTerminalSeal' in cfg['targets']['studyflow_parity']['tasks']['build'],'release gate removed')
    print('PASS_R4R5_SIGNING_DESKTOP_INVENTORY_SOURCE_ONLY')

if __name__=='__main__': main()
