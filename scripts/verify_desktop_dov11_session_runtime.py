#!/usr/bin/env python3
from pathlib import Path
import re
root=Path(__file__).resolve().parents[1]
failures=[]
def req(path,*tokens):
    text=(root/path).read_text()
    for token in tokens:
        if token not in text: failures.append(f"{path}: missing {token!r}")
req("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopWorkspaceStore.kt",
    "SCHEMA_VERSION = 4", "updateSessions", "writeSession", "readSession")
req("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/InMemoryStudyScheduleRepository.kt",
    "transitionFromSession", "PlannedActivityStatus.IN_PROGRESS")
req("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/InMemoryStudySessionRepository.kt",
    "linkedActivityExpectedStatus", "transitionFromSession", "purgePlan", "onChanged")
req("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopSessionRuntimeController.kt",
    "startNextActivity", "pause", "resume", "complete", "cancel", "requireRunnablePlan")
req("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopPlannerPracticeScreens.kt",
    "Study Session runtime", "Start next", "Quick session")
req("desktopApp/src/test/kotlin/com/mikeyphw/studyflow/desktop/DesktopSessionRuntimePersistenceTest.kt",
    "RecoversAcrossRestart", "PlannedActivityStatus.IN_PROGRESS", "PlannedActivityStatus.COMPLETED")
test_root = root / "desktopApp/src/test"
for milestone_test in (
    "DesktopWorkspacePersistenceTest.kt",
    "DesktopPlanContentLifecycleTest.kt",
    "DesktopPlannerAuthoringPersistenceTest.kt",
    "DesktopSessionRuntimePersistenceTest.kt",
):
    milestone_path = test_root / "kotlin/com/mikeyphw/studyflow/desktop" / milestone_test
    milestone_text = milestone_path.read_text()
    if re.search(r"@Test\s*\n\s*fun\s+\w+\([^)]*\)\s*=\s*runBlocking", milestone_text):
        failures.append(f"{milestone_path.relative_to(root)}: expression-bodied runBlocking JUnit test")
direct_service_call = re.compile(r"(?<![A-Za-z0-9_])DesktopStudyFlowServices\s*\(")
for test_file in test_root.rglob("*.kt"):
    text = test_file.read_text()
    for match in direct_service_call.finditer(text):
        depth = 0
        end = None
        for index in range(match.end() - 1, len(text)):
            if text[index] == "(":
                depth += 1
            elif text[index] == ")":
                depth -= 1
                if depth == 0:
                    end = index + 1
                    break
        block = text[match.start():end]
        if "contentRepository =" not in block:
            line = text.count("\n", 0, match.start()) + 1
            failures.append(f"{test_file.relative_to(root)}:{line}: missing contentRepository")
req(".devtool.toml", '"verifyDesktopDov11SessionRuntime"')
if failures: raise SystemExit("DOV11 session-runtime violations:\n- " + "\n- ".join(failures))
print("DESKTOP_DOV11_SESSION_RUNTIME: PASS")
