#!/usr/bin/env python3
from __future__ import annotations

import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FAILURES: list[str] = []


def require(condition: bool, message: str) -> None:
    if not condition:
        FAILURES.append(message)


def text(path: str) -> str:
    candidate = ROOT / path
    require(candidate.is_file(), f"missing required file: {path}")
    return candidate.read_text(encoding="utf-8") if candidate.is_file() else ""


sync_queue = text("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/CloudlessBackupQueue.kt")
coach = text("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudyCoachWeeklyReview.kt")
sync_screen = text("feature/settings/src/main/java/com/mikeyphw/studyflow/feature/settings/SyncFoundationScreen.kt")
progress = text("feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/ProgressWorkspaceScreen.kt")
routes = text("core/navigation/src/main/kotlin/com/mikeyphw/studyflow/core/navigation/StudyFlowRoutes.kt")
design = text("core/designsystem/src/main/java/com/mikeyphw/studyflow/core/designsystem/StudyFlowWorkspaceComponents.kt")
today = text("feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/TodayWorkspaceScreen.kt")
reminders = text("feature/reminders/src/main/java/com/mikeyphw/studyflow/feature/reminders/StudyRemindersScreen.kt")
build = text("build.gradle.kts")

for token in (
    "enum class CloudlessBackupQueueAction",
    "PREPARE_CHECKPOINT",
    "PREPARE_CHECKPOINT",
    "CHECKPOINT_CURRENT",
    "REVIEW_LOCAL_CONFLICT_POLICY",
    "WAIT_FOR_LOCAL_DATA",
    "UNBLOCK_CHECKPOINT",
    "StudyLocalCheckpointReadinessAuditor.localCheckpointReady",
    "fun plan(",
    "actual Study Plan export",
):
    require(token in sync_queue, f"Phase 58 queue missing {token}")

for token in (
    "enum class StudyCoachWeeklyReviewItemKind",
    "READINESS_STABLE",
    "READINESS_RISK",
    "REVIEW_BACKLOG",
    "PRACTICE_ACCURACY",
    "SCHEDULE_DRIFT",
    "MOCK_EXAM_SIGNAL",
    "CONSISTENCY_WIN",
    "RESCHEDULE_READY",
    "object StudyCoachWeeklyReviewBuilder",
    "coachPriority",
    ".sortedBy { candidate -> candidate.kind.coachPriority }",
    "StudyCoachWeeklyReviewItemKind.RESCHEDULE_READY -> 1",
):
    require(token in coach, f"Phase 59 coach review missing {token}")

priority_sort = coach.find(".sortedBy { candidate -> candidate.kind.coachPriority }")
visible_cap = coach.find(".take(5)")
require(
    priority_sort >= 0 and visible_cap >= 0 and priority_sort < visible_cap,
    "weekly coach review must prioritize safe actions before applying the visible item cap",
)

for token in (
    "LocalCheckpointReadinessCard",
    "CloudlessBackupQueuePlanner.plan",
    "sync_checkpoint_readiness_title",
    "sync_checkpoint_readiness_summary",
    "CloudlessBackupQueueAction.titleRes",
    "CloudlessBackupQueueStatus.labelRes",
):
    require(token in sync_screen, f"Sync Foundation queue UI missing {token}")

for token in (
    "StudyCoachWeeklyReviewBuilder.build",
    "StudyCoachWeeklyReviewInput",
    "weekly_coach_review",
    "weekly-coach-summary",
    "StudyCoachWeeklyReviewItemKind.titleRes",
    "WeeklyCoachDestination.REVIEWS",
):
    require(token in progress, f"Progress weekly coach UI missing {token}")

for enum_name in (
    "StudyFlowWorkspaceRoot",
    "PlanWorkspaceTab",
    "PracticeWorkspaceTab",
    "ProgressWorkspacePeriod",
):
    require(f"@Keep\n@Serializable\nenum class {enum_name}" in routes, f"{enum_name} lacks @Keep")

focus_primitives = text("core/designsystem/src/main/java/com/mikeyphw/studyflow/core/designsystem/StudyFlowFocusWorkspacePrimitives.kt")
require("PrimaryScrollableTabRow(" not in design, "legacy Material workspace tab row remains")
require("fun <T> StudyFlowTabStrip(" in focus_primitives, "custom StudyFlow tab strip is missing")
require("Role.Tab" in focus_primitives, "custom StudyFlow tab semantics are missing")
require("import androidx.compose.material3.ScrollableTabRow" not in design, "deprecated ScrollableTabRow import remains")
require("mutableLongStateOf(System.currentTimeMillis())" in today, "Today current time must use mutableLongStateOf")
require("Build.VERSION.SDK_INT < 23" not in reminders, "reminders still contain obsolete minSdk < 23 branch")
require("name=\"in_progress\"" not in text("feature/workspace/src/main/res/values/strings.xml"), "private in_progress key remains")
require("name=\"in_progress\"" not in text("feature/workspace/src/main/res/values-pt-rBR/strings.xml"), "pt private key remains")

for path in (
    "feature/workspace/src/main/res/values/strings.xml",
    "feature/workspace/src/main/res/values-pt-rBR/strings.xml",
    "feature/settings/src/main/res/values/strings.xml",
    "feature/settings/src/main/res/values-pt-rBR/strings.xml",
    "app/src/main/res/values/strings.xml",
    "app/src/main/res/values-pt-rBR/strings.xml",
):
    source = text(path)
    require("tools:ignore" not in source, f"{path} must not retain targeted warning suppressions")
    ET.fromstring(source)

for path in (
    "core/domain/src/test/kotlin/com/mikeyphw/studyflow/core/domain/CloudlessBackupQueuePlannerTest.kt",
    "core/domain/src/test/kotlin/com/mikeyphw/studyflow/core/domain/StudyCoachWeeklyReviewTest.kt",
):
    source = text(path)
    require("@Test" in source, f"{path} lacks tests")

require("scripts/verify_phase_58_59.py" in build, "Phase 58/59 verifier is not wired")
require("verifyPhase58_59WarningHygiene" in build, "Phase 58/59 warning hygiene gate is missing")
require(":app:lintRelease" in text(".devtool-artifact.json") if (ROOT / ".devtool-artifact.json").exists() else True,
        "overlay validation must retain release lint")

for source_path in [
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/CloudlessBackupQueue.kt",
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudyCoachWeeklyReview.kt",
    "feature/settings/src/main/java/com/mikeyphw/studyflow/feature/settings/SyncFoundationScreen.kt",
    "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/ProgressWorkspaceScreen.kt",
]:
    source = text(source_path)
    for line_number, line in enumerate(source.splitlines(), 1):
        require("\t" not in line, f"{source_path}:{line_number} contains a tab")
        require(line == line.rstrip(), f"{source_path}:{line_number} has trailing whitespace")
        require(len(line) <= 120, f"{source_path}:{line_number} exceeds 120 characters")

for resource_path in [
    "app/src/main/res/values/strings.xml",
    "app/src/main/res/values-pt-rBR/strings.xml",
    "feature/settings/src/main/res/values/strings.xml",
    "feature/settings/src/main/res/values-pt-rBR/strings.xml",
    "feature/workspace/src/main/res/values/strings.xml",
    "feature/workspace/src/main/res/values-pt-rBR/strings.xml",
]:
    resource_text = text(resource_path)
    require(
        "<resources" in resource_text,
        f"{resource_path} must remain a resource XML file",
    )
    require(
        "tools:ignore=\"PluralsCandidate,UnusedResources\"" not in resource_text.split("\n", 3)[0:3].__str__(),
        f"{resource_path} must not suppress plural and unused warnings at file scope",
    )
    require(
        "tools:ignore=\"UnusedResources\"" not in resource_text.split("\n", 3)[0:3].__str__(),
        f"{resource_path} must not suppress unused warnings at file scope",
    )

if FAILURES:
    print("PHASE_58_59_VALIDATION_FAIL")
    for failure in FAILURES:
        print(f"- {failure}")
    sys.exit(1)

print("PHASE_58_59_VALIDATION_OK")
print("Local checkpoint readiness, weekly coach review, and warning hygiene verified")
