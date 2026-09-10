#!/usr/bin/env python3
from pathlib import Path
R=Path(__file__).resolve().parents[1];F=[]
def t(p):return (R/p).read_text()
def q(c,m):
 if not c:F.append(m)
s=t("app-tv/src/main/java/com/mikeyphw/studyflow/tv/search/StudyFlowTvSearch.kt")
a=t("app-tv/src/main/java/com/mikeyphw/studyflow/tv/StudyFlowTvActivity.kt")
p=t("core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowClientProfiles.kt")
for x in ("StudyFlowTvControlInputReceiver","StudyFlowInputDelegationOffer","StudyFlowInputEventKind.TEXT_REPLACE","StudyFlowInputEventKind.TEXT_COMMIT"):q(x in s,"SFLP search input missing "+x)
for x in ("KEYCODE_SEARCH","KEYCODE_DPAD_UP","KEYCODE_DPAD_DOWN","renderSearchOverlay"):q(x in a,"native D-pad search missing "+x)
q("StudyFlowLinkCapabilities.KeyboardInput" in p,"Android TV keyboard input acceptance missing")
q("repositorySearchForbidden = true" in s,"TV search must remain presentation-safe")
b=t("build.gradle.kts");d=t(".devtool.toml")
q('tasks.register<Exec>("verifyStudyFlowTvDpadSflpSearch")' in b,"Gradle task missing")
q("verifyStudyFlowTvDpadSflpSearch" in d,"Devtool task missing")
if F:
 print("TVOV30_DPAD_SFLP_SEARCH: FAIL");[print("-",x) for x in F];raise SystemExit(1)
print("TVOV30_DPAD_SFLP_SEARCH: PASS")
