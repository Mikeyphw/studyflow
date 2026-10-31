#!/usr/bin/env python3
"""R4R7 source-only regression; never runs Gradle or accesses a network."""
from __future__ import annotations
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]

def require(test, reason):
    if not test: raise AssertionError(reason)

def lines(path):return (ROOT/path).read_text(encoding='utf-8').splitlines()

def validate_query(xml):
    try:
        document=ET.fromstring(xml)
        ns='{http://schemas.android.com/apk/res/android}name'
        queries=document.findall('queries')
        if len(queries)!=1:return False
        nodes=list(queries[0]);
        if [n.tag for n in nodes]!=['package','intent']:return False
        if nodes[0].get(ns)!='com.termux' or len(nodes[0].attrib)!=1:return False
        intents=list(nodes[1]);
        return len(intents)==1 and intents[0].tag=='action' and intents[0].get(ns)=='com.termux.RUN_COMMAND' and len(intents[0].attrib)==1
    except ET.ParseError:
        return False

def descriptions(text):
    out=[];a=text.splitlines()
    for i,l in enumerate(a):
        m=re.match(r'^\s*description = "([^"\\]*)"$',l)
        if m:out.append(m.group(1));continue
        if l.strip()=='description =':
            chunks=[];i+=1
            while i<len(a):
                m=re.match(r'^\s*"([^"\\]*)"( \+)?$',a[i])
                if not m:break
                chunks.append(m.group(1));i+=1
                if not m.group(2):break
            if chunks:out.append(''.join(chunks))
    return out

def run(script):
    p=subprocess.run([sys.executable,'scripts/'+script],cwd=ROOT,timeout=75,text=True,capture_output=True)
    require(p.returncode==0,script+': '+(p.stdout+p.stderr)[-1500:])

def main():
    ci='\n'.join(lines('.github/workflows/studyflow-ci.yml'))
    require('python3 scripts/test_studyflow_ov01_r4r7_contracts.py' in ci,'R4R7 not hosted in CI source gate')
    android='\n'.join(lines('app/src/main/AndroidManifest.xml'))
    require(validate_query(android),'Termux visibility is broader than the audited narrow allowlist')
    for changed in (
      android.replace('com.termux"','com.other.vendor"',1),
      android.replace('</queries>', '<package android:name="com.example.unsafe" /></queries>',1),
      android.replace('</queries>', '<intent><action android:name="android.intent.action.VIEW" /></intent></queries>',1),
      android.replace('com.termux.RUN_COMMAND','android.intent.action.VIEW',1),
      android.replace('<queries>', '<queries /><queries>',1),
    ):
        require(not validate_query(changed),'Broad queries mutation escaped rejection')
    test='\n'.join(lines('app/src/test/java/com/mikeyphw/studyflow/ManifestSecuritySourceContractTest.kt'))
    for needle in ('DocumentBuilderFactory','queryChildren.map { it.tagName }','assertEquals(listOf("package", "intent")',
                   '"com.termux.RUN_COMMAND"','queryNodes.length'):
        require(needle in test,'Manifest JVM test lost strict allowlist: '+needle)
    require('assertFalse("<queries" in manifest)' not in test,'Old blanket queries prohibition returned')
    widget='\n'.join(lines('app/src/main/java/com/mikeyphw/studyflow/widget/StudyFlowWidgetModels.kt'))
    assert 'filter { it > nowEpochMillis }' in widget,'Widget must preserve sub-minute future boundary'
    require('filter { it >= nowEpochMillis + MinimumRefreshDelayMillis }' not in widget,'Old 60s filter returned')
    require('nowEpochMillis + MinimumRefreshDelayMillis' in widget,'Minute fallback removed')
    require('MinimumImmediateWakeupDelayMillis' in '\n'.join(lines('app/src/main/java/com/mikeyphw/studyflow/widget/StudyFlowWidgetFreshnessScheduler.kt')),'Alarm floor removed')
    widget_test='\n'.join(lines('app/src/test/java/com/mikeyphw/studyflow/widget/StudyFlowWidgetSnapshotProjectorAtt03Test.kt'))
    for marker in ('keepsSubMinutePlannedBoundaryForExactFreshnessScheduler', 'imminentReviewDeadlineRemainsExactWithoutAnActiveSession','assertEquals(reviewAt, snapshot.nextRefreshEpochMillis)'):
        require(marker in widget_test,'Missing JVM widget behavior test: '+marker)
    ov15='\n'.join(lines('scripts/verify_ov15_android_product_flow_honesty.py'))
    require('runner_path = "scripts/validate_ov15_android_product_flow_honesty.sh"' in ov15,'Permanent OV15 runner missing')
    require('active_hook_path.is_file()' in ov15,'OV15 hook must be checked only if present')
    for name in ('verify_ov13_android_peer_lifecycle_lan_recovery.py','verify_ov17_attention_delivery_widget_remote_policy.py'):
        content='\n'.join(lines('scripts/'+name))
        require('is_file()' in content,'Missing optional artifact-hook check: '+name)
    require('"<queries>" in manifest' not in '\n'.join(lines('scripts/verify_phase_18.py')), 'Phase 18 blanket rejection returned')
    gradle='\n'.join(lines('build.gradle.kts'))
    require(all(len(line)<=120 for line in gradle.splitlines()),'Kotlin root Gradle exceeds 120 columns')
    got=hashlib.sha256(json.dumps(descriptions(gradle),ensure_ascii=False).encode()).hexdigest()
    require(got=="b97431b84b5ed967ebef5b5b00bb7e7186b81d30e4ffd3de56158de90546d7cf",'Root Gradle description text semantically changed')
    aggregate=re.search(r'tasks\.register\("verifyOv30R2AllTargetProperValidationSeals"\)\s*\{(.*?)\n\}',gradle,re.S)
    require(aggregate is not None,'OV30R2 aggregate task missing')
    deps=re.findall(r'"(verifyOv30R2Studyflow\w+ProperValidationSeal)"',aggregate.group(1))
    require(deps==['verifyOv30R2StudyflowAndroidProperValidationSeal', 'verifyOv30R2StudyflowDesktopProperValidationSeal', 'verifyOv30R2StudyflowWebProperValidationSeal', 'verifyOv30R2StudyflowTvProperValidationSeal', 'verifyOv30R2StudyflowReleaseProperValidationSeal', 'verifyOv30R2StudyflowTvReleaseProperValidationSeal', 'verifyOv30R2StudyflowQualityProperValidationSeal', 'verifyOv30R2StudyflowParityProperValidationSeal'],'OV30R2 ordered aggregate dependency graph changed')
    for script in ('verify_sflp02_03_journal_reconciliation.py','verify_phase_18.py',
                   'verify_ov15_android_product_flow_honesty.py','verify_ov13_android_peer_lifecycle_lan_recovery.py',
                   'verify_ov17_attention_delivery_widget_remote_policy.py',
                   'verify_rel_final_roadmap_promise_audit.py','verify_ov30_all_target_overlay_split.py'):
        run(script)
    print('STUDYFLOW_R4R7_SOURCE_NEGATIVE_CONTRACTS: PASS (no Gradle)')

if __name__=='__main__':main()
