#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]

def read(rel: str) -> str:
    path = ROOT / rel
    if not path.exists():
        raise AssertionError(f"missing required OV22 file: {rel}")
    return path.read_text(encoding="utf-8")

def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)

checks = []
def check(name: str, condition: bool, evidence: str) -> None:
    require(condition, f"{name}: {evidence}")
    checks.append(name)

app_state = read("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopAppState.kt")
services = read("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopStudyFlowServices.kt")
discovery = read("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopAnalyticsDiscovery.kt")
runner = read("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopUiActionRunner.kt")
plan_content = read("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopPlanContentController.kt")
schedule = read("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/InMemoryStudyScheduleRepository.kt")
session = read("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/InMemoryStudySessionRepository.kt")
practice = read("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/InMemoryStudyPracticeRepository.kt")
review = read("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/InMemoryStudyReviewRepository.kt")
workspace = read("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopWorkspaceStore.kt")
portability = read("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopPortabilityController.kt")
link_persistence = read("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopStudyFlowLinkPersistence.kt")
content_mode = read("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopContentModePersistence.kt")
public_exam = read("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopPublicExamPersistence.kt")
build = read("build.gradle.kts")
hook = read(".devtool-artifact/validation/validate.sh") if (ROOT / ".devtool-artifact/validation/validate.sh").exists() else ""

check("S9-01-ui-action-runner", "class DesktopUiActionRunner" in runner and "SupervisorJob() + Dispatchers.Default" in runner, "UI action runner missing")
check("S9-01-appstate-no-runblocking", "runBlocking" not in app_state and "uiActions.launch" in app_state, "DesktopAppState still blocks UI actions")
check("S9-01-search-no-runblocking", "runBlocking" not in services and "currentSearch(trimmed)" in services and "currentRecommendations(planId, wallClockMillis())" in services, "Desktop service search still blocks")
check("S9-01-discovery-sync-path", "fun currentSearch" in discovery and "fun currentRecommendations" in discovery, "nonblocking sync discovery path missing")

for token in ["deleteSubject", "deleteTopic", "scrubDeletedContentReferences", "topicDescendants"]:
    check(f"S9-02-plan-content-{token}", token in plan_content, f"{token} missing from plan-content cleanup")
for name, source in [("schedule", schedule), ("session", session), ("practice", practice), ("review", review)]:
    check(f"S9-02-{name}-scrub", "fun scrubDeletedContentReferences" in source, f"{name} repository cleanup hook missing")

check("S9-03-linked-session-create-rollback", "beforeSessions" in session and "linkedBefore" in session and "Linked Desktop Study Session did not commit atomically" in session, "linked create rollback missing")
check("DS2-38-linked-session-transition-rollback", "Linked Desktop Study Session transition did not commit atomically" in session and "replaceActivityFromSessionRollback" in schedule, "linked transition rollback missing")
check("S9-04-practice-before-attempt", practice.index("wrongAnswerSink(wrongAnswers)") < practice.index("updateTestAttemptIfOpen(expectedConfiguration, next)"), "practice attempt commits before wrong-answer evidence")
check("S9-04-mock-before-attempt", practice.rindex("wrongAnswerSink(wrongAnswers)") < practice.rindex("updateMockExamAttemptIfOpen(expectedConfiguration, next)"), "mock attempt commits before wrong-answer evidence")

check("S9-05-restore-reloads-memory", "services.reloadFromDiskAfterRestore()" in portability and "fun reloadFromDiskAfterRestore" in services, "restore does not reload in-memory repositories")
check("S9-05-restore-no-restart-required", "restartRequired = false" in portability, "restore still reports restart-required memory divergence")
check("S9-06-point-in-time-backup", "createPointInTimeWorkspaceSnapshot" in portability and "point-in-time encrypted Desktop backup" in portability, "point-in-time backup staging missing")
check("S9-07-aux-validation", "validateAuxiliaryStagedStores(staging)" in portability, "auxiliary restore validation missing")
for file_name in ["library-v1.properties", "practice-v1.properties", "reviews-v1.properties", "content-modes-v1.properties", "public-exam-v1.properties", "progress-v1.properties"]:
    check(f"S9-07-aux-{file_name}", file_name in portability, f"{file_name} is not validated during restore")

check("S9-08-content-mode-migration", "contentModeOrDefault" in workspace and "StudyContentMode.GENERAL.name" not in workspace, "legacy contentMode migration still uses a nonexistent/mandatory enum")
check("S9-09-single-instance-lock", "FileChannel.open" in workspace and "FileLock" in workspace and "tryLock()" in workspace and ".studyflow-workspace.lock" in workspace, "workspace single-instance lock missing")
check("S12-06-workspace-permissions", "PosixFilePermission.OWNER_READ" in workspace and "applyOwnerOnlyPermissions(workspaceFile" in workspace, "workspace owner-only permissions missing")
check("S12-06-link-permissions", "PosixFilePermission.OWNER_READ" in link_persistence and "applyOwnerOnlyPermissions(path" in link_persistence, "SFLP link/trust owner-only permissions missing")
check("S12-09-password-secret", "CharArray" in app_state and "portabilityPasswordSecret.fill" in app_state and "portabilityPasswordDraft = \"\"" in app_state, "backup secret clearing missing")
check("S12-09-password-mask", "\"•\".repeat(value.length)" in app_state, "backup password remains displayed as immutable raw String")
check("OV22-reload-repositories", all(token in services for token in ["planRepository.replaceFromPersistence", "contentRepository.replaceFromPersistence", "libraryRepository.replaceFromPersistence", "practiceRepository.replaceFromPersistence", "reviewRepository.replaceFromPersistence", "contentModeRepository.replaceFromPersistence", "publicExamRepository.replaceFromPersistence", "progressRepository.reloadFromDisk"]), "restore reload does not cover all Desktop stores")
check("OV22-content-mode-replace", "fun replaceFromPersistence(next: DesktopContentModeSnapshot)" in content_mode, "content mode restore replace missing")
check("OV22-public-exam-replace", "fun replaceFromPersistence(snapshot: DesktopPublicExamSnapshot)" in public_exam, "public exam restore replace missing")
check("OV22-gradle-task", "verifyOv22DesktopPersistenceAtomicRestoreFileSafety" in build, "Gradle verification task missing")
check("OV22-validation-hook", "verifyOv22DesktopPersistenceAtomicRestoreFileSafety" in hook and "--stop" not in hook and "gradle --stop" not in hook, "validation hook missing or stops daemons")

print(f"OV22 Desktop persistence/atomic restore/file safety source evidence: PASS ({len(checks)} checks)")
