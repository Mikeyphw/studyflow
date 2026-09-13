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


planner = text("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/MockExamStudioPlanner.kt")
practice = text("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ManageStudyPractice.kt")
practice_ui = text(
    "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/PracticeWorkspaceScreen.kt"
)
reminders = text("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudyReminderPlanner.kt")
widget = text("app/src/main/java/com/mikeyphw/studyflow/widget/StudyFlowTodayWidgetProvider.kt")
widget_layout = text("app/src/main/res/layout/widget_today.xml")
widget_info = text("app/src/main/res/xml/studyflow_today_widget_info.xml")
manifest = text("app/src/main/AndroidManifest.xml")
build = text("build.gradle.kts")

require(".filter(Question::isExamEligible)" not in planner,
        "MockExamStudioPlanner must call extension eligibility through an explicit lambda")
require(".filter { question -> question.isExamEligible() }" in planner,
        "MockExamStudioPlanner missing compile-safe explicit eligibility lambda")

for token in (
    "object MockExamStudioPlanner",
    "data class MockExamStudioRequest",
    "data class MockExamStudioBlueprint",
    "data class MockExamSection",
    "enum class MockExamStudioWarning",
    "QuestionType.ESSAY",
    "NOT_ENOUGH_MATCHING_QUESTIONS",
    "NARROW_SUBJECT_COVERAGE",
    "selectedQuestions",
    "if (disputed && !request.includeDisputed) return 0",
    "if (source != null) score += 1",
):
    require(token in planner, f"mock exam studio planner missing: {token}")

for token in (
    "previewMockExamStudio",
    "createMockExamFromStudio",
    "MockExamStudioPlanner.preview",
    "createMockExam(",
    "minimumScorePercent = blueprint.minimumScorePercent",
):
    require(token in practice, f"ManageStudyPractice missing Phase 52 behavior: {token}")

for token in (
    "MockExamStudioDialog",
    "mock_exam_studio_title",
    "previewMockExamStudio",
    "createMockExamFromStudio",
    "MockExamStudioWarning.NO_QUESTIONS",
    "mock_exam_warning_not_enough",
    "onPreviewMockExam",
    "currentBlueprint.warnings.forEach",
    "mock_exam_board_filter",
    "mock_exam_source_filter",
    "mock_exam_tags_filter",
    "mock_exam_include_disputed",
    "includeDisputed",
):
    require(token in practice_ui, f"Practice workspace missing Phase 52 surface: {token}")

for token in (
    "val studyPlanId: StudyPlanId",
    "primaryActionType: ReminderNotificationType",
    "ReminderNotificationType.DUE_REVIEWS",
    "ReminderNotificationType.PLANNED_ACTIVITY",
    "ReminderNotificationType.MISSED_ACTIVITY",
):
    require(token in reminders, f"Today widget summary missing Phase 53 field/action: {token}")

for token in (
    "studyReminderOpenIntent",
    "Uri.Builder()",
    '.authority("widget-open")',
    '.appendPath(summary?.studyPlanId?.value ?: "app")',
    '.appendPath(type?.name ?: "root")',
    "widget_primary_action",
    "widget_reviews_action",
    "primaryActionLabel",
    "ReminderNotificationType.DUE_REVIEWS",
):
    require(token in widget, f"Today widget provider missing Phase 53 action behavior: {token}")

for token in (
    "@+id/widget_action_row",
    "@+id/widget_primary_action",
    "@+id/widget_reviews_action",
    "48dp",
):
    require(token in widget_layout, f"widget layout missing accessible action row: {token}")

require("android.appwidget.action.APPWIDGET_UPDATE" in manifest, "app widget receiver update action missing")
require("android.appwidget.provider" in manifest, "app widget provider metadata missing")
require("@xml/studyflow_today_widget_info" in manifest, "app widget info XML not linked")
require("android:description=\"@string/widget_today_description\"" in widget_info,
        "app widget info missing localized description")
require("scripts/verify_phase_52_53.py" in build, "Phase 52/53 verifier is not wired")

mock_test = text("core/domain/src/test/kotlin/com/mikeyphw/studyflow/core/domain/MockExamStudioPlannerTest.kt")
for token in (
    "studioExcludesDisputedUnlessRequestedAndFiltersSource",
    "includeDisputed = true",
    'source = "official"',
):
    require(token in mock_test, f"Mock Exam Studio test missing: {token}")

for path in (
    "core/domain/src/test/kotlin/com/mikeyphw/studyflow/core/domain/MockExamStudioPlannerTest.kt",
    "docs/architecture/ADR-0032-mock-exam-studio-and-widget-actions.md",
    "docs/quality/PHASE_52_53_ACCEPTANCE.md",
    "PHASE_52_53_MOCK_EXAM_STUDIO_WIDGET.md",
):
    require((ROOT / path).is_file(), f"missing Phase 52/53 documentation or test: {path}")

for path in (
    "feature/workspace/src/main/res/values/strings.xml",
    "feature/workspace/src/main/res/values-pt-rBR/strings.xml",
):
    xml_root = ET.parse(ROOT / path).getroot()
    string_names = {element.attrib["name"] for element in xml_root if element.tag == "string"}
    plural_names = {element.attrib["name"] for element in xml_root if element.tag == "plurals"}
    for key in (
        "mock_exam_studio_title",
        "mock_exam_warning_not_enough",
        "mock_exam_board_filter",
        "mock_exam_source_filter",
        "mock_exam_tags_filter",
        "mock_exam_include_disputed",
        "mock_exam_created",
    ):
        require(key in string_names, f"workspace resource missing {key} in {path}")
    require(
        "mock_exam_studio_message" in string_names or "mock_exam_studio_message" in plural_names,
        f"workspace resource missing mock_exam_studio_message in {path}",
    )

for path in (
    "app/src/main/res/values/strings.xml",
    "app/src/main/res/values-pt-rBR/strings.xml",
):
    xml_root = ET.parse(ROOT / path).getroot()
    names = {element.attrib["name"] for element in xml_root if element.tag == "string"}
    for key in (
        "widget_action_reviews",
        "widget_action_next_block",
        "widget_action_recover",
        "widget_action_today",
    ):
        require(key in names, f"app widget resource missing {key} in {path}")

workspace_strings = {
    element.attrib["name"]
    for element in ET.parse(ROOT / "feature/workspace/src/main/res/values/strings.xml").getroot()
    if element.tag == "string"
}
for match in sorted(set(re.findall(r"R\.string\.([A-Za-z0-9_]+)", practice_ui))):
    require(match in workspace_strings, f"Practice workspace references missing string: {match}")

for source_path in [
    ROOT / "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/MockExamStudioPlanner.kt",
    ROOT / "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ManageStudyPractice.kt",
    ROOT / "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/PracticeWorkspaceScreen.kt",
    ROOT / "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudyReminderPlanner.kt",
    ROOT / "app/src/main/java/com/mikeyphw/studyflow/widget/StudyFlowTodayWidgetProvider.kt",
]:
    source = source_path.read_text(encoding="utf-8")
    require("\x00" not in source, f"NUL byte in {source_path.relative_to(ROOT)}")
    for line_number, line in enumerate(source.splitlines(), 1):
        require("\t" not in line, f"{source_path.relative_to(ROOT)}:{line_number} contains a tab")
        require(len(line) <= 120, f"{source_path.relative_to(ROOT)}:{line_number} exceeds 120 characters")

if FAILURES:
    print("PHASE_52_53_VALIDATION_FAIL")
    for failure in FAILURES:
        print(f"- {failure}")
    sys.exit(1)

print("PHASE_52_53_VALIDATION_OK")
print("Mock Exam Studio, widget action routing, resources, docs, and source hygiene verified")
