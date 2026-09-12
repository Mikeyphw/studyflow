#!/usr/bin/env python3
from pathlib import Path
R=Path(__file__).resolve().parents[1]; F=[]
def t(p): return (R/p).read_text()
def q(c,m):
    if not c:F.append(m)
pres=t("core/tvcontrol/src/main/kotlin/com/mikeyphw/studyflow/core/tvcontrol/StudyFlowTvPresentation.kt")
domain=t("core/tvcontrol/src/main/kotlin/com/mikeyphw/studyflow/core/tvcontrol/StudyFlowTvDomainAction.kt")
proj=t("app/src/main/java/com/mikeyphw/studyflow/tvcontrol/StudyFlowTvPresentationProjector.kt")
source=t("app/src/main/java/com/mikeyphw/studyflow/tvcontrol/StudyFlowTvPresentationSource.kt")
execu=t("app/src/main/java/com/mikeyphw/studyflow/tvcontrol/StudyFlowTvDomainActionExecutor.kt")
ui=t("app-tv/src/main/java/com/mikeyphw/studyflow/tv/StudyFlowTvActivity.kt")
for x in ("StudyFlowTvGoalPresentation","val goals:"):q(x in pres,"goal schema missing "+x)
for x in ("GOAL_COMPLETE","GOAL_PAUSE","GOAL_RESUME"):q(x in domain,"domain action missing "+x);q(x in execu,"executor missing "+x)
q("StudyGoalProgressEngine.evaluate" in proj,"goal projection must evaluate canonical evidence")
q("progressRepository.observeGoals" in source,"presentation source must observe persisted goals")
q("tvGoalAction" in ui and "tv_progress_goals_title" in ui,"TV Progress UI missing goal actions")
b=t("build.gradle.kts");d=t(".devtool.toml")
q('tasks.register<Exec>("verifyStudyFlowTvGoalProjectionActions")' in b,"Gradle task missing")
q("verifyStudyFlowTvAllContracts" in d, "Devtool target missing aggregate TV contract gate")
tv_aggregate = b.split('tasks.register("verifyStudyFlowTvAllContracts")', 1)[1].split('tasks.register("verifyStudyFlowAndroidPreflightContracts")', 1)[0]
q("verifyStudyFlowTvGoalProjectionActions" in tv_aggregate, "aggregate TV contract gate missing verifyStudyFlowTvGoalProjectionActions")
if F:
 print("TVOV28_GOAL_PROJECTION_ACTIONS: FAIL");[print("-",x) for x in F];raise SystemExit(1)
print("TVOV28_GOAL_PROJECTION_ACTIONS: PASS")
