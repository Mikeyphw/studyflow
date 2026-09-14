#!/usr/bin/env python3
from pathlib import Path
root=Path(__file__).resolve().parents[1]
checks={
'S4-01': [('core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/RoomStudyFlowSflpProductStateBridge.kt','captureLocalMutations'),('core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowPeerRpc.kt','flushProductState')],
'S4-02': [('core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/RoomStudyFlowSflpProductStateBridge.kt','projectResolved'),('core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowPeerRpc.kt','projectProductState')],
'S4-09': [('app/src/main/java/com/mikeyphw/studyflow/StudyFlowApplication.kt','delay(15_000L)'),('app-tv/src/main/java/com/mikeyphw/studyflow/tv/link/StudyFlowTvSflpPeer.kt','scheduleWithFixedDelay')],
'DS2-01': [('core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowWorkspaceMembership.kt','canWrite'),('core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowPeerRpc.kt','workspace_access_denied')],
'DS2-56': [('app-tv/src/main/java/com/mikeyphw/studyflow/tv/link/StudyFlowTvSflpPeer.kt','publishDomainAction'),('app/src/main/java/com/mikeyphw/studyflow/StudyFlowApplication.kt','tvDomainActionExecutor.execute(action)')],
}
failed=[]
for finding, reqs in checks.items():
    for rel, token in reqs:
        text=(root/rel).read_text()
        if token not in text: failed.append(f'{finding}: missing {token} in {rel}')
if failed:
    raise SystemExit('\n'.join(failed))
print('OV08 structural wiring: PASS (5/5 owned findings mapped to production paths)')
