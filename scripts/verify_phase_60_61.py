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


def resource_names(path: str) -> dict[str, str]:
    source = text(path)
    root = ET.fromstring(source)
    return {
        child.attrib["name"]: child.tag
        for child in root
        if child.tag in {"string", "plurals"} and "name" in child.attrib
    }


cleanup = text("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudyContentCleanup.kt")
report = text("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudyExamReport.kt")
plan = text("feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/PlanWorkspaceScreen.kt")
progress = text("feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/ProgressWorkspaceScreen.kt")
document = text("feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/StudyExamReportDocument.kt")
exporter = text("app/src/main/java/com/mikeyphw/studyflow/StudyFlowExamReportExporter.kt")
navigation = text("app/src/main/java/com/mikeyphw/studyflow/StudyFlowV2Navigation.kt")
provider_paths = text("app/src/main/res/xml/file_provider_paths.xml")
build = text("build.gradle.kts")
versions = text("gradle/libs.versions.toml")

for token in (
    "DUPLICATE_QUESTION",
    "ORPHAN_TOPIC",
    "STALE_RESOURCE",
    "STALE_PLAN",
    "INCONSISTENT_TAG",
    "STALE_REVIEW",
    "BROKEN_SCHEDULE_ALIGNMENT",
):
    require(token in cleanup, f"Phase 60 cleanup issue kind missing: {token}")
    require(token in plan, f"Plan workspace does not localize cleanup issue kind: {token}")

for token in (
    "val destructive: Boolean = false",
    "val requiresConfirmation: Boolean = true",
    "previewActions.none(StudyContentCleanupPreviewAction::destructive)",
    "groupBy(::duplicateQuestionKey)",
    "normalizedText(question.statement)",
    "question.tags.map(::normalizedTag)",
    "STALE_PLAN_MILLIS",
    "STALE_RESOURCE_MILLIS",
):
    require(token in cleanup, f"Phase 60 cleanup safety/detection contract missing: {token}")

require("repository." not in cleanup, "Phase 60 engine must not mutate repositories")
require("suspend fun" not in cleanup, "Phase 60 cleanup engine must remain preview-only and synchronous")
require("StudyContentCleanupEngine.inspect" in plan, "Plan workspace must compute the real cleanup preview")
require("cleanup_preview_notice" in plan, "Plan workspace must disclose preview-only cleanup behavior")
require("cleanupReport?.issues.orEmpty().take(5)" in plan, "Plan workspace must expose concrete cleanup issues")

for token in (
    "StudyExamReportSnapshot",
    "StudyExamReportSignal",
    "ReadinessSignalKind",
    "cleanupHealthScorePercent",
    "nextActions",
    "freeformNotesIncluded: Boolean = false",
    "require(!freeformNotesIncluded)",
):
    require(token in report, f"Phase 61 structured report contract missing: {token}")

for forbidden in ("signal.label", "signal.message", "actionSummary", ".reason", ".title"):
    require(forbidden not in report, f"Phase 61 report must not copy domain prose: {forbidden}")

for token in (
    "REVIEW_BACKLOG",
    "RECOVER_SCHEDULE",
    "PRACTICE_WEAK_AREAS",
    "RUN_MOCK_EXAM",
    "REVIEW_CONTENT_CLEANUP",
    "APPLY_SAFE_RESCHEDULE",
    "KEEP_PLAN",
):
    require(token in report, f"Phase 61 next-action kind missing: {token}")
    require(token in progress, f"Progress workspace does not localize report action: {token}")

require("StudyExamReportBuilder.build" in progress, "Progress workspace must build the structured exam report")
require("localizedExamReportDocument" in progress, "Progress workspace must localize structured report data")
require("StudyExamReportFormat.HTML" in progress, "HTML export action is missing")
require("StudyExamReportFormat.PDF" in progress, "PDF export action is missing")
require("onExportExamReport" in progress, "Progress workspace must require explicit export callback")
require("onExportExamReport =" in navigation, "Navigation must wire report export to Android")

for token in (
    'REPORT_DIRECTORY = "exam-reports"',
    "context.cacheDir",
    "FileProvider.getUriForFile",
    "Intent.ACTION_SEND",
    "FLAG_GRANT_READ_URI_PERMISSION",
    "StudyExamReportFormat.HTML",
    "StudyExamReportFormat.PDF",
    "PdfDocument",
    "escapeHtml",
):
    require(token in exporter, f"Local report exporter contract missing: {token}")

for forbidden in ("http://", "https://", "Retrofit", "OkHttp", "openConnection", "URLConnection"):
    require(forbidden not in exporter, f"Report exporter must stay cloudless; found {forbidden}")

require('path="exam-reports/"' in provider_paths, "FileProvider must expose only the exam-report cache path")
require("freeform" not in document.lower(), "Localized report document must not carry freeform note fields")

primary_resources = [
    "app/src/main/res/values/strings.xml",
    "app/src/main/res/values-pt-rBR/strings.xml",
    "feature/settings/src/main/res/values/strings.xml",
    "feature/settings/src/main/res/values-pt-rBR/strings.xml",
    "feature/workspace/src/main/res/values/strings.xml",
    "feature/workspace/src/main/res/values-pt-rBR/strings.xml",
]
for path in primary_resources:
    source = text(path)
    require("tools:ignore" not in source, f"warning cleanup must leave no resource suppressions in {path}")
    ET.fromstring(source)

for retired in (
    "navigation_plans",
    "navigation_settings",
    "navigation_search",
):
    require(
        f'name="{retired}"' not in text("app/src/main/res/values/strings.xml"),
        f"unused app resource still present: {retired}",
    )

for retired in ("today_eyebrow", "streak", "quick_add_subtitle"):
    require(
        f'name="{retired}"' not in text("feature/workspace/src/main/res/values/strings.xml"),
        f"unused workspace resource still present: {retired}",
    )

for retired in ("sync_connection_checkpoint_prepared", "sync_conflict_last_write_wins"):
    require(
        f'name="{retired}"' not in text("feature/settings/src/main/res/values/strings.xml"),
        f"unused settings resource still present: {retired}",
    )

workspace_en = resource_names("feature/workspace/src/main/res/values/strings.xml")
workspace_pt = resource_names("feature/workspace/src/main/res/values-pt-rBR/strings.xml")
settings_en = resource_names("feature/settings/src/main/res/values/strings.xml")
settings_pt = resource_names("feature/settings/src/main/res/values-pt-rBR/strings.xml")
require(workspace_en == workspace_pt, "workspace English and pt-BR resource names/types must match")
require(settings_en == settings_pt, "settings English and pt-BR resource names/types must match")

expected_plurals = {
    "days_value",
    "reviews_value",
    "questions_value",
    "subjects_count",
    "topics_count",
    "materials_count",
    "videos_count",
    "annexes_count",
    "practice_due_message",
    "tests_count",
    "mocks_count",
    "recurrences_value",
    "sessions_value",
    "answered_value",
    "active_goals_value",
    "notes_value",
    "flashcards_value",
    "view_all_topics",
    "open_remaining_reviews",
    "review_method_and_interval",
    "test_progress",
    "reschedule_apply_success",
    "weakness_drill_message",
    "mock_exam_studio_message",
    "mock_exam_studio_sections",
    "evidence_minutes_and_count",
    "evidence_count",
    "items_count",
    "weekly_coach_summary",
    "content_health_summary",
}
for name in expected_plurals:
    require(workspace_en.get(name) == "plurals", f"workspace plural migration missing: {name}")
require(settings_en.get("debug_timeline_summary") == "plurals", "settings debug summary must use plurals")

for source_path in (
    "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/PlanWorkspaceScreen.kt",
    "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/TodayWorkspaceScreen.kt",
    "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/PracticeWorkspaceScreen.kt",
    "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/ProgressWorkspaceScreen.kt",
):
    source = text(source_path)
    for name in expected_plurals:
        require(f"R.string.{name}" not in source, f"{source_path} still references plural {name} as R.string")

settings_screen = text("feature/settings/src/main/java/com/mikeyphw/studyflow/feature/settings/DebugWorkbenchScreen.kt")
require("R.plurals.debug_timeline_summary" in settings_screen, "Debug timeline must consume the plural resource")
require("R.string.debug_timeline_summary" not in settings_screen, "Debug timeline still uses the old string resource")

for module, source_paths, resources in (
    (
        "workspace",
        [
            "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/PlanWorkspaceScreen.kt",
            "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/TodayWorkspaceScreen.kt",
            "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/PracticeWorkspaceScreen.kt",
            "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/ProgressWorkspaceScreen.kt",
        ],
        workspace_en,
    ),
    (
        "settings",
        ["feature/settings/src/main/java/com/mikeyphw/studyflow/feature/settings/DebugWorkbenchScreen.kt"],
        settings_en,
    ),
):
    for source_path in source_paths:
        source = text(source_path)
        for kind, name in re.findall(r"R\.(string|plurals)\.([A-Za-z0-9_]+)", source):
            require(name in resources, f"{module} missing resource {kind}.{name} used by {source_path}")
            if name in resources:
                require(resources[name] == kind, f"{module} resource kind mismatch for {name}: expected {kind}")

for test_path in (
    "core/domain/src/test/kotlin/com/mikeyphw/studyflow/core/domain/StudyContentCleanupEngineTest.kt",
    "core/domain/src/test/kotlin/com/mikeyphw/studyflow/core/domain/StudyExamReportBuilderTest.kt",
):
    source = text(test_path)
    require(source.count("@Test") >= 2, f"{test_path} must contain behavioral unit tests")

for source_path in (
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudyContentCleanup.kt",
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudyExamReport.kt",
    "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/PlanWorkspaceScreen.kt",
    "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/ProgressWorkspaceScreen.kt",
    "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/StudyExamReportDocument.kt",
    "app/src/main/java/com/mikeyphw/studyflow/StudyFlowExamReportExporter.kt",
):
    source = text(source_path)
    for line_number, line in enumerate(source.splitlines(), 1):
        require("\t" not in line, f"{source_path}:{line_number} contains a tab")
        require(line == line.rstrip(), f"{source_path}:{line_number} has trailing whitespace")
        require(len(line) <= 120, f"{source_path}:{line_number} exceeds 120 characters")

require('agp = "9.3.1"' in versions, "AGP version changed unexpectedly")
require('kotlin = "2.4.10"' in versions, "Kotlin version changed unexpectedly")
require('navigation = "2.9.8"' in versions, "Navigation must remain on 2.9.8")
require('ksp = "2.3.11"' in versions, "KSP version changed unexpectedly")
require("docs/roadmap/ACTIVE_ROADMAP.md" in build or (ROOT / "docs/roadmap/ACTIVE_ROADMAP.md").is_file(), "active roadmap boundary is present")
require('"verifyPhase60_61ContentReportSeal"' in build, "checkAll must depend on the Phase 60/61 gate")

if FAILURES:
    print("PHASE_60_61_VALIDATION_FAIL")
    for failure in FAILURES:
        print(f"- {failure}")
    sys.exit(1)

print("PHASE_60_61_VALIDATION_OK")
print("Smart cleanup, local HTML/PDF export, bilingual UI mapping, and warning cleanup verified")
