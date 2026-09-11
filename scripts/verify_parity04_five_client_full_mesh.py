#!/usr/bin/env python3
from __future__ import annotations
import json, subprocess, sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
errors=[]
def need(c,m):
    if not c: errors.append(m)
def txt(rel):
    p=ROOT/rel; need(p.is_file(),f"missing {rel}"); return p.read_text(encoding='utf-8') if p.is_file() else ''

fixture_path=ROOT/'protocol/parity/v1/five-client-scenarios.json'
need(fixture_path.is_file(),'PARITY04 fixture missing')
if fixture_path.is_file():
    data=json.loads(fixture_path.read_text())
    need(data.get('schemaVersion')==1,'PARITY04 schemaVersion must be 1')
    need(data.get('milestone')=='PARITY04','PARITY04 milestone mismatch')
    clients=data.get('clients',[]); pairs=data.get('directPairs',[]); scenarios=data.get('scenarios',[])
    need([c.get('clientKind') for c in clients]==['android','desktop','web','android_tv','web_tv'],'five canonical clients/order drifted')
    need(len(pairs)==10 and len({tuple(sorted(p)) for p in pairs})==10,'all 10 direct pairs must be unique')
    kinds={s.get('kind') for s in scenarios}
    required={'full_mesh_convergence','offline_conflict','activity_continuation','tv_presentation','exactly_once_completion','direct_pair','capability_rejection','loop_free','presence','portable','semantic_equivalence'}
    need(required <= kinds,f'PARITY04 scenario kinds missing: {sorted(required-kinds)}')
    need(data.get('web20Fixture')=='protocol/semantic/v1/golden-vectors.json','PARITY04 must reuse WEB20 fixture path')

checks={
 'core/link/src/test/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowFiveClientParity04AcceptanceTest.kt':[
  'fiveClientsPairDirectlyAndConvergeOverRealTcpWithoutAndroidHub','pairEveryClientDirectly','reconcileEveryDirection',
  'offlineConcurrentWritersResolveDeterministicallyOnEveryClient','parity-web','parity-session-complete',
  'StudyFlowActivityHandoffCoordinator','StudyFlowPresentationProjectionReceiver','StudyFlowRouteTrace.begin',
  'StudyFlowLinkClientKind.ANDROID_TV','StudyFlowLinkClientKind.WEB_TV','assertFalse(StudyFlowLinkCapabilities.WorkspaceWrite in capabilities)',
 ],
 'desktopApp/src/test/kotlin/com/mikeyphw/studyflow/desktop/DesktopParity04FiveClientAcceptanceTest.kt':[
  'protocol/parity/v1/five-client-scenarios.json','protocol/semantic/v1/golden-vectors.json','CanonicalPortableJson','assertEquals(40',
 ],
 'web/tests/parity04_runner.mjs':['directPairs.length,10','runSemanticGolden','stableStringify','StudyFlowWebActivityHandoffController','StudyFlowWebPresentationProjectionController','StudyFlowWebPresenceRuntime'],
 'web/tests/test_parity04.py':['PARITY04_TYPESCRIPT_ACCEPTANCE: PASS','test_parity04_reuses_web20_and_production_runtimes'],
 'PARITY04_FIVE_CLIENT_FULL_MESH_ACCEPTANCE_SEAL.md':['10 direct pair combinations','exactly-once','REL-FINAL evidence execution'],
}
for rel,tokens in checks.items():
    s=txt(rel)
    for token in tokens: need(token in s,f'{rel} missing {token}')

web20=json.loads(txt('protocol/semantic/v1/golden-vectors.json'))
need(len(web20.get('vectors',[]))==40,'WEB20 vector count must remain 40')
remaining=json.loads(txt('docs/roadmap/CROSS_PLATFORM_REMAINING_WORK.json'))
tracks={x.get('id'):x for x in remaining.get('remaining_tracks',[])}
need(tracks.get('PARITY04',{}).get('status')=='complete','PARITY04 must be complete')
need(tracks.get('PARITY04',{}).get('completed_through')=='PARITY04','PARITY04 completed_through missing')
need(tracks.get('REL-FINAL',{}).get('status')=='pending_execution','REL-FINAL must remain pending execution')
need(tracks.get('REL-FINAL',{}).get('prerequisites')==['PARITY04','REL01-REL08'],'REL-FINAL prerequisite drift')
roadmap=txt('docs/roadmap/CROSS_PLATFORM_PARITY_CONNECTIVITY_ROADMAP.md')
need('**PARITY04 is implemented.**' in roadmap,'canonical roadmap must mark PARITY04 implemented')
need('REL-FINAL evidence execution is the only remaining canonical track' in roadmap,'roadmap must advance to REL-FINAL')
matrix=txt('docs/product/CROSS_PLATFORM_CAPABILITY_MATRIX.md')
need('PARITY04 is **SUPPORTED / SEALED**' in matrix,'capability matrix must mark PARITY04 sealed')
index=txt('docs/roadmap/FINAL_ROADMAP_INDEX.md')
need('**PARITY04 — COMPLETE**' in index,'final roadmap index must mark PARITY04 complete')
need('**REL-FINAL evidence execution — NEXT**' in index,'final roadmap index must advance REL-FINAL')

if not errors:
    p=subprocess.run(['node','tests/parity04_runner.mjs'],cwd=ROOT/'web',text=True,capture_output=True)
    if p.returncode!=0: errors.append('Web PARITY04 runner failed: '+(p.stderr or p.stdout).strip())
    elif 'PASS (5 clients, 10 direct pairs, 40 WEB20 vectors)' not in p.stdout: errors.append('Web PARITY04 runner summary missing')
if errors:
    print('PARITY04_FIVE_CLIENT_FULL_MESH: FAIL')
    for e in errors: print('-',e)
    sys.exit(1)
print('PARITY04_FIVE_CLIENT_FULL_MESH: PASS (5 clients, 10 direct pairs, 40 WEB20 vectors)')
