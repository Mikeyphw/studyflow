#!/usr/bin/env python3
from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[1]
checks = {
    "controller": ROOT / "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopProgressReadinessController.kt",
    "screen": ROOT / "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopAnalyticsSearchSettingsScreens.kt",
    "services": ROOT / "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopStudyFlowServices.kt",
    "app_state": ROOT / "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopAppState.kt",
    "app": ROOT / "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/StudyFlowDesktopApp.kt",
    "test": ROOT / "desktopApp/src/test/kotlin/com/mikeyphw/studyflow/desktop/DesktopProgressReadinessIntegrationTest.kt",
    "matrix": ROOT / "docs/product/CROSS_PLATFORM_CAPABILITY_MATRIX.md",
    "remaining": ROOT / "docs/roadmap/CROSS_PLATFORM_REMAINING_WORK.json",
    "root_gradle": ROOT / "build.gradle.kts",
    "desktop_gradle": ROOT / "desktopApp/build.gradle.kts",
    "devtool": ROOT / ".devtool.toml",
}
for label, path in checks.items():
    assert path.is_file(), f"missing {label}: {path}"
text = {key: value.read_text() for key, value in checks.items()}
controller = text["controller"]
for token in (
    "ExamReadinessEngine.calculate",
    "StudyEvidenceTimelineBuilder.buildAll",
    "StudyCoachWeeklyReviewBuilder.build",
    "StudyContentCleanupEngine.inspect",
    "StudyExamReportBuilder.build",
    "StudyExamRunbookPlanner.build",
    "StudySprintPlanner.preview",
    "services.manageStudySchedule.applyVersionedPreview",
    'root.resolve("exam-reports")',
    "StandardCopyOption.ATOMIC_MOVE",
):
    assert token in controller, f"missing DOV19 shared-domain contract: {token}"
assert controller.count("notes = emptyList()") >= 2

# Kotlin compile guards for report rendering. Nested escaped quote literals inside string-template
# expressions caused DOV19 v1 to fail compileKotlin; keep formatting in a typed helper instead.
assert "private fun formatPercent(value: Int?, unavailable: String): String" in controller
assert "formatPercent(report.readinessScorePercent" in controller
assert "formatPercent(report.practiceTestAccuracyPercent" in controller
assert '\\"$it%\\"' not in controller
assert "DesktopProgressReadinessController(this, workspacePersistence?.root)" in text["services"]
assert "progressReadinessState" in text["app_state"]
assert "applyStudySprint" in text["app_state"]
assert "exportExamReport" in text["app_state"]
for token in ("Exam readiness", "Weekly coach", "Content health", "Exam runbook", "Seven-day study sprint", "Exam report"):
    assert token in text["screen"], f"missing DOV19 analytics surface: {token}"
assert "onApplySprint = appState::applyStudySprint" in text["app"]
assert "onExportReport = appState::exportExamReport" in text["app"]
assert "Progress / analytics | FULL | FULL | FULL" in text["matrix"]
remaining = json.loads(text["remaining"])
desktop = next(item for item in remaining["remaining_tracks"] if item["id"] == "DOV08-DOV24")
assert desktop["status"] == "in_progress"
assert desktop["completed_through"] == "DOV19"
for gradle_key in ("root_gradle", "desktop_gradle"):
    assert "verifyDesktopDov19ReadinessCoachingReports" in text[gradle_key]
    assert "verifyDesktopDov16Dov19Batch" in text[gradle_key]
assert '"verifyDesktopDov19ReadinessCoachingReports"' in text["devtool"]
test = text["test"]
for token in (
    "exportCurrentExamReport",
    "applyCurrentSprint",
    "Metric provenance",
    "freeformNotesIncluded",
    "DesktopWorkspacePersistence.open",
    "LocalDate.now().plusDays(7).toString()",
):
    assert token in test, f"missing DOV19 regression coverage: {token}"
assert "state.examReport?.evidenceCount" not in test
assert "state.examReport?.freeformNotesIncluded" not in test
print("DESKTOP_DOV19_READINESS_COACHING_REPORTS: PASS")
