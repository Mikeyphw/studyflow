#!/usr/bin/env python3
from __future__ import annotations

import re
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


seal = text("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/PostV2ReleaseSeal.kt")
seal_test = text("core/domain/src/test/kotlin/com/mikeyphw/studyflow/core/domain/PostV2ReleaseSealTest.kt")
nav = text("app/src/main/java/com/mikeyphw/studyflow/StudyFlowV2Navigation.kt")
routes = text("core/navigation/src/main/kotlin/com/mikeyphw/studyflow/core/navigation/StudyFlowRoutes.kt")
progress = text("feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/ProgressWorkspaceScreen.kt")
practice = text("feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/PracticeWorkspaceScreen.kt")
reminders = text("feature/reminders/src/main/java/com/mikeyphw/studyflow/feature/reminders/StudyRemindersScreen.kt")
widget = text("app/src/main/java/com/mikeyphw/studyflow/widget/StudyFlowTodayWidgetProvider.kt")
widget_layout = text("app/src/main/res/layout/widget_today.xml")
build = text("build.gradle.kts")

for token in (
    "enum class PostV2IntegrationArea",
    "WORKSPACE_SHELL",
    "LEGACY_ROUTE_COMPATIBILITY",
    "FOCUS_RESTORATION",
    "READINESS_AND_RESCHEDULE",
    "PRACTICE_PIPELINE",
    "MOCK_EXAM_AND_WIDGET",
    "REMINDER_AND_TIMELINE",
    "BACKUP_DEBUG_AND_SEARCH",
    "LOCALIZATION_AND_SOURCE_HYGIENE",
    "object PostV2IntegrationSeal",
    "enum class DeviceReadinessPolishArea",
    "PREDICTIVE_BACK",
    "EDGE_TO_EDGE_AND_IME",
    "WIDGET_TAP_PATHS",
    "WARNING_BOUNDARY",
    "object DeviceReadinessPolishSeal",
):
    require(token in seal, f"Phase 56/57 seal missing {token}")

for token in (
    "postV2IntegrationSealRequiresEveryPromisedArea",
    "postV2IntegrationSealBlocksWhenAnyWorkspaceContractIsMissing",
    "deviceReadinessSealRequiresEveryRealDevicePolishArea",
    "deviceReadinessSealBlocksWhenWarningBoundaryRegresses",
):
    require(token in seal_test, f"Phase 56/57 test missing {token}")

for token in (
    "NavigationSuiteScaffold",
    "clearBackStack<TodayRoute>()",
    "clearBackStack<PlanWorkspaceRoute>()",
    "clearBackStack<PracticeWorkspaceRoute>()",
    "clearBackStack<ProgressWorkspaceRoute>()",
    "navigateWorkspaceRootRoute",
    "restoreState = restoreSavedStack",
    "saveState = restoreSavedStack",
    "FocusSessionRoute",
    "StudyFlowWorkspaceRoot.PROGRESS",
):
    require(token in nav, f"V2 navigation contract missing {token}")

for token in (
    "is DashboardRoute -> TodayRoute",
    "is ScheduleRoute -> PlanWorkspaceRoute",
    "is SubjectsRoute -> PlanWorkspaceRoute",
    "is LibraryRoute -> PlanWorkspaceRoute",
    "is PracticeRoute -> PracticeWorkspaceRoute",
    "is ReviewsRoute -> PracticeWorkspaceRoute",
    "is ProgressRoute -> ProgressWorkspaceRoute",
):
    require(token in routes, f"Legacy route compatibility missing {token}")

for token in (
    "ManageExamReadiness",
    "ManageAdaptiveReschedule",
    "StudyEvidenceTimelineBuilder.build",
    "studyPlanId = plan.id",
    "event.kind.titleRes()",
    "onOpenReviews",
):
    require(token in progress, f"Progress integration contract missing {token}")

for token in (
    "buildWeaknessDrill",
    "createWeaknessDrillTest",
    "QuestionImportDialog",
    "importQuestionsFromText",
    "MockExamStudioDialog",
    "createMockExamFromStudio",
    "currentBlueprint.warnings.forEach",
):
    require(token in practice, f"Practice integration contract missing {token}")

for token in (
    "ReminderReliabilityCenter.inspect",
    "notificationPermissionGranted = notificationPermissionGranted",
    "canScheduleStudyFlowExactAlarms",
    "isIgnoringBatteryOptimizations",
    "ReminderReliabilityIssueRow",
):
    require(token in reminders, f"Reminder reliability surface missing {token}")

for token in (
    "StudyReminderExtraPlanId",
    "StudyReminderExtraType",
    "widget_primary_action",
    "widget_reviews_action",
    "ReminderNotificationType.DUE_REVIEWS",
):
    require(token in widget, f"Widget route/tap contract missing {token}")

require("android:tint" not in widget_layout, "widget_today.xml must not use android:tint")
require("48dp" in widget_layout, "widget_today.xml must retain 48dp widget actions")
require("Icons.Outlined.TrendingUp" not in progress, "Progress must use AutoMirrored TrendingUp")
require(".filter(Question::isExamEligible)" not in text(
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/MockExamStudioPlanner.kt"
), "MockExamStudioPlanner must not use forbidden extension function reference")

for path in (
    "feature/reminders/src/main/res/values/strings.xml",
    "feature/reminders/src/main/res/values-pt-rBR/strings.xml",
):
    resource_text = text(path)
    for forbidden in ("app's safe", "plan's quiet", "\\u"):
        require(forbidden not in resource_text, f"{path} contains AAPT-risky token {forbidden!r}")
    ET.fromstring(resource_text)

for path in (
    "feature/workspace/src/main/res/values/strings.xml",
    "feature/workspace/src/main/res/values-pt-rBR/strings.xml",
    "feature/reminders/src/main/res/values/strings.xml",
    "feature/reminders/src/main/res/values-pt-rBR/strings.xml",
):
    names = [element.attrib["name"] for element in ET.parse(ROOT / path).getroot() if element.tag == "string"]
    duplicates = sorted(name for name in set(names) if names.count(name) > 1)
    require(not duplicates, f"{path} has duplicate string keys: {duplicates}")

for path in (
    "scripts/verify_phase_48_49.py",
    "scripts/verify_phase_50_51.py",
    "scripts/verify_phase_52_53.py",
    "scripts/verify_phase_54_55.py",
):
    require((ROOT / path).is_file(), f"missing previous verifier: {path}")
    require(f"python3 {path}" in build, f"{path} is not wired into verifyRepositoryContracts")
require("python3 scripts/verify_phase_56_57.py" in build, "Phase 56/57 verifier is not wired")
require(
    "verifyPhase56_57ReleaseReadiness" in build,
    "Phase 56/57 release-readiness Gradle gate is not defined",
)
require(
    "verifyPhase56_57ReleaseReadiness" in build and '"verifyPhase56_57ReleaseReadiness"' in build,
    "Phase 56/57 release-readiness gate is not wired into checkAll",
)
require(
    ":app:lintRelease" in text(".devtool-artifact.json") if (ROOT / ".devtool-artifact.json").is_file() else True,
    "Overlay validation must include :app:lintRelease",
)
require("Phase 57" in text("VALIDATION_REPORT.md"), "Validation report missing Phase 57 entry")
require("PHASE_56_57_POST_V2_INTEGRATION_RELEASE_SEAL.md" in text("README.md"), "README missing Phase 56/57 docs link")

for source_path in [
    ROOT / "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/PostV2ReleaseSeal.kt",
    ROOT / "core/domain/src/test/kotlin/com/mikeyphw/studyflow/core/domain/PostV2ReleaseSealTest.kt",
    ROOT / "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/ProgressWorkspaceScreen.kt",
    ROOT / "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/PracticeWorkspaceScreen.kt",
    ROOT / "feature/reminders/src/main/java/com/mikeyphw/studyflow/feature/reminders/StudyRemindersScreen.kt",
]:
    source = source_path.read_text(encoding="utf-8")
    require("\x00" not in source, f"NUL byte in {source_path.relative_to(ROOT)}")
    for line_number, line in enumerate(source.splitlines(), 1):
        require("\t" not in line, f"{source_path.relative_to(ROOT)}:{line_number} contains a tab")
        require(line == line.rstrip(), f"{source_path.relative_to(ROOT)}:{line_number} has trailing whitespace")
        require(len(line) <= 120, f"{source_path.relative_to(ROOT)}:{line_number} exceeds 120 characters")

if FAILURES:
    print("PHASE_56_57_VALIDATION_FAIL")
    for failure in FAILURES:
        print(f"- {failure}")
    sys.exit(1)

print("PHASE_56_57_VALIDATION_OK")
print("Post-V2 integration seal and real-device polish guards verified")
