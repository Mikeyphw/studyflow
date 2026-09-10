#!/usr/bin/env python3
from pathlib import Path
root=Path(__file__).resolve().parents[1]
failures=[]
def req(path,*tokens):
    text=(root/path).read_text()
    for token in tokens:
        if token not in text: failures.append(f"{path}: missing {token!r}")
req("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopWorkspaceStore.kt",
    "SCHEMA_VERSION = 3", "updateSchedule", "writeActivity", "readStrategy")
req("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/InMemoryStudyScheduleRepository.kt",
    "private val onChanged", "applyAdaptivePreview", "applyMissedRebalance", "purgePlan", "persist()")
req("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopPlannerAuthoringController.kt",
    "AdaptiveReschedulePlanner.preview", "generateSevenDays", "applyAdaptivePreview", "rebalanceMissed",
    "requireEditablePlan")
req("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopPlannerPracticeScreens.kt",
    "Author schedule", "Generate 7 days", "Apply adaptive preview")
req("desktopApp/src/test/kotlin/com/mikeyphw/studyflow/desktop/DesktopPlannerAuthoringPersistenceTest.kt",
    "SurviveRestart", "Persistent planner activity")
req(".devtool.toml", '"verifyDesktopDov10PlannerAdaptiveWorkflows"')
if failures: raise SystemExit("DOV10 planner violations:\n- " + "\n- ".join(failures))
print("DESKTOP_DOV10_PLANNER_ADAPTIVE_WORKFLOWS: PASS")
