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


def resources(path: str) -> dict[str, str]:
    root = ET.fromstring(text(path))
    return {
        child.attrib["name"]: child.tag
        for child in root
        if child.tag in {"string", "plurals"} and "name" in child.attrib
    }


runbook = text("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudyExamRunbook.kt")
sprint = text("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudySprintPlanner.kt")
schedule = text("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ManageStudySchedule.kt")
workspace = text(
    "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/ProgressWorkspaceScreen.kt",
)
phase62 = text("scripts/verify_phase_62.py")
build = text("build.gradle.kts")

for token in (
    "enum class StudyExamRunbookState",
    "NO_EXAM_DATE",
    "enum class StudyExamRunbookWindow",
    "enum class StudyExamRunbookAction",
    "data class StudyExamRunbookItem",
    "data class StudyExamRunbook",
    "object StudyExamRunbookPlanner",
    "plan.examDateIso == null",
    "analytics.overdueReviewCount",
    "analytics.weakAreas.take",
    "analytics.questionAccuracyPercent",
    "schedule.plannedActivities.count",
    "mockExams.count",
    "Runbook analytics belong to another Study Plan.",
    "Runbook readiness belongs to another Study Plan.",
    "Runbook schedule belongs to another Study Plan.",
    "Runbook strategy evidence belongs to another Study Plan.",
    "Runbook schedule-rule evidence belongs to another Study Plan.",
    "Runbook activity evidence belongs to another Study Plan.",
    "actionPriority(item.action)",
    "StudyExamRunbookAction.FINAL_REVIEW -> 1",
):
    require(token in runbook, f"Phase 63 runbook contract missing: {token}")

for forbidden in (
    "Repository",
    "Retrofit",
    "OkHttp",
    "http://",
    "https://",
    "suspend fun",
):
    require(forbidden not in runbook, f"Phase 63 runbook must remain local/read-only; found {forbidden}")

for token in (
    "enum class StudySprintFocus",
    "data class StudySprintPreview",
    "object StudySprintPlanner",
    "HorizonDays = 7",
    "!activity.locked",
    "activity.status == PlannedActivityStatus.PLANNED",
    "scheduledStart in start..end",
    "take(MaxCandidates)",
    "AdaptiveReschedulePlanner.scheduleVersionToken(schedule)",
    "currentStartEpochMillis = activity.scheduledStartEpochMillis",
    "currentStatus = activity.status",
    "executable = true",
    "Sprint analytics belong to another Study Plan.",
    "Sprint readiness belongs to another Study Plan.",
    "Sprint schedule belongs to another Study Plan.",
    "Sprint strategy evidence belongs to another Study Plan.",
    "Sprint schedule-rule evidence belongs to another Study Plan.",
    "Sprint activity evidence belongs to another Study Plan.",
    "internal fun requireSafeApply(",
    "Study sprint cannot move an activity to another time.",
    "Study sprint can only preserve or shorten activity duration.",
    "Study sprint cannot target an activity more than once.",
    "PreviewFreshnessMillis = 120_000L",
    "Study sprint cannot mutate an activity outside the approved seven-day candidate set.",
    "Study sprint candidate set no longer matches the approved preview.",
):
    require(token in sprint, f"Phase 64 sprint contract missing: {token}")

for forbidden in (
    "UUID.randomUUID",
    "upsert",
    "delete",
    "Repository",
    "Retrofit",
    "OkHttp",
    "http://",
    "https://",
):
    require(forbidden not in sprint, f"Phase 64 preview must not create a parallel mutable source: {forbidden}")

for token in (
    "suspend fun applyVersionedPreview(",
    "preview.studyPlanId == planId",
    "preview.scheduleVersionToken.isNotBlank()",
    "val currentSchedule = repository.observeSchedule(planId).first()",
    "StudySprintPlanner.requireSafeApply(",
    "nowEpochMillis = clock()",
    "repository.applyAdaptivePreview(planId, preview.schedulePreview)",
):
    require(token in schedule, f"Version-bound sprint apply boundary missing: {token}")

for token in (
    "StudyExamRunbookPlanner.build(",
    "StudySprintPlanner.preview(",
    "exam-runbook",
    "study-sprint",
    "scheduleManager.applyVersionedPreview(",
    "preview = approvedPreview",
    "sprintApplyInFlight",
    "catch (cancelled: CancellationException)",
    "catch (_: Exception)",
    "loadAttempt += 1",
    "rememberSaveable(plan.id.value)",
):
    require(token in workspace, f"Progress workspace Phase 63/64 integration missing: {token}")

require(
    "Future runbook behavior must not leak into Phase 62 onboarding" in phase62,
    "Phase 62 historical verifier must constrain onboarding scope instead of globally banning future Phase 63 work",
)

for test_path, tokens in {
    "core/domain/src/test/kotlin/com/mikeyphw/studyflow/core/domain/StudyExamRunbookPlannerTest.kt": (
        "noExamDateIsExplicitlyNotApplicable",
        "runbookPrioritizesBacklogWeaknessPracticeAndFinalReview",
        "crossPlanReadinessEvidenceFailsClosed",
        "nestedScheduleEvidenceFailsClosed",
        "boundedRunbookKeepsFinalReviewAheadOfLowerPriorityAdvice",
    ),
    "core/domain/src/test/kotlin/com/mikeyphw/studyflow/core/domain/StudySprintPlannerTest.kt": (
        "previewIsBoundedToExistingUnlockedSevenDayActivities",
        "previewNeverMutatesSourceAndUsesStableScheduleVersion",
        "healthyScheduleCanReturnPreviewOnlyInsteadOfInventingMutation",
        "crossPlanScheduleEvidenceFailsClosed",
        "nestedScheduleActivityEvidenceFailsClosed",
        "safeApplyEnvelopeRejectsMovedLengthenedDuplicateOrMismatchedChanges",
    ),
}.items():
    source = text(test_path)
    for token in tokens:
        require(token in source, f"missing Phase 63/64 regression test token: {test_path}: {token}")

workspace_en = resources("feature/workspace/src/main/res/values/strings.xml")
workspace_pt = resources("feature/workspace/src/main/res/values-pt-rBR/strings.xml")
phase_names = {
    name: kind
    for name, kind in workspace_en.items()
    if name.startswith("exam_runbook_") or name.startswith("study_sprint_")
}
require(bool(phase_names), "Phase 63/64 workspace resources are missing")
for name, kind in phase_names.items():
    require(name in workspace_pt, f"pt-BR Phase 63/64 resource missing: {name}")
    if name in workspace_pt:
        require(workspace_pt[name] == kind, f"pt-BR resource kind mismatch for {name}")

for token in (
    "verifyPhase63_64ExamRunbookSprintSeal",
    "scripts/verify_phase_63_64.py",
    "python3 scripts/verify_phase_62.py",
    "python3 scripts/verify_phase_63_64.py",
):
    require(token in build, f"Gradle Phase 63/64 verification wiring missing: {token}")

phase_doc = text("PHASE_63_64_EXAM_RUNBOOK_STUDY_SPRINT.md")
readme = text("README.md")
changelog = text("CHANGELOG.md")
devtool = text(".devtool.toml")
for token in (
    "Phase 63 — Exam runbook",
    "Phase 64 — Seven-day study sprint",
    "applyVersionedPreview",
    "Android/Gradle/lint/device validation is intentionally not",
):
    require(token in phase_doc, f"Phase 63/64 implementation record missing: {token}")
for token in ("Phase 63 adds", "Phase 64 adds"):
    require(token in readme, f"README Phase 63/64 summary missing: {token}")
require("# Phase 63-64 Exam Runbook and Study Sprint" in changelog, "changelog Phase 63/64 entry missing")
require(
    devtool.count('"verifyPhase63_64ExamRunbookSprintSeal"') >= 2,
    "Devtool phase metadata must retain the Phase 63/64 seal for deferred campaign validation",
)

for source_path in (
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudyExamRunbook.kt",
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudySprintPlanner.kt",
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ManageStudySchedule.kt",
    "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/ProgressWorkspaceScreen.kt",
):
    source = text(source_path)
    for line_number, line in enumerate(source.splitlines(), 1):
        require("\t" not in line, f"{source_path}:{line_number} contains a tab")
        require(line.rstrip() == line, f"{source_path}:{line_number} has trailing whitespace")
        require(len(line) <= 120, f"{source_path}:{line_number} has {len(line)} characters")

if FAILURES:
    print("Phase 63/64 exam runbook and study sprint verification failed:")
    for failure in FAILURES:
        print(f" - {failure}")
    sys.exit(1)

print("Phase 63/64 exam runbook and study sprint seal passed")
