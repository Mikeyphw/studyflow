#!/usr/bin/env python3
from pathlib import Path
import re
root=Path(__file__).resolve().parents[1]
failures=[]
workspace_text=(root / "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopWorkspaceStore.kt").read_text()
schema_match=re.search(r"SCHEMA_VERSION\s*=\s*(\d+)", workspace_text)
if not schema_match or int(schema_match.group(1)) < 3:
    failures.append("DesktopWorkspaceStore schema must be >= 3")
def req(path,*tokens):
    text=(root/path).read_text()
    for token in tokens:
        if token not in text: failures.append(f"{path}: missing {token!r}")
req("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopWorkspaceStore.kt",
"updateSchedule", "writeActivity", "readStrategy")
req("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/InMemoryStudyScheduleRepository.kt",
    "private val onChanged", "applyAdaptivePreview", "applyMissedRebalance", "purgePlan", "persist()")
req("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopPlannerAuthoringController.kt",
    "AdaptiveReschedulePlanner.preview", "generateSevenDays", "applyAdaptivePreview", "rebalanceMissed",
    "requireEditablePlan")
req("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopPlannerPracticeScreens.kt",
    "Author schedule", "Generate 7 days", "Apply adaptive preview")
req("desktopApp/src/test/kotlin/com/mikeyphw/studyflow/desktop/DesktopPlannerAuthoringPersistenceTest.kt",
    "SurviveRestart", "Persistent planner activity")

planner_test = (
    root / "desktopApp/src/test/kotlin/com/mikeyphw/studyflow/desktop/DesktopPlannerAuthoringPersistenceTest.kt"
).read_text()
if re.search(r"@Test\s*\n\s*fun\s+\w+\([^)]*\)\s*=\s*runBlocking", planner_test):
    failures.append("DesktopPlannerAuthoringPersistenceTest must use a block-bodied JUnit test around runBlocking")
if "occupiedDays" not in planner_test or "first { it !in occupiedDays }" not in planner_test:
    failures.append("Desktop planner persistence test must select an unoccupied seeded weekday")
if "addEveningRule(dayOfWeek = 2, startMinuteOfDay = 19 * 60)" in planner_test:
    failures.append("Desktop planner persistence test must not reuse the seeded Tuesday 19:00 rule")
req(".devtool.toml", '"verifyDesktopDov10PlannerAdaptiveWorkflows"')
if failures: raise SystemExit("DOV10 planner violations:\n- " + "\n- ".join(failures))
print("DESKTOP_DOV10_PLANNER_ADAPTIVE_WORKFLOWS: PASS")
