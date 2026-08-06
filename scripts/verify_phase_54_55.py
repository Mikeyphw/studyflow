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


reliability = text("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ReminderReliabilityCenter.kt")
timeline = text("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudyEvidenceTimeline.kt")
reminders_ui = text("feature/reminders/src/main/java/com/mikeyphw/studyflow/feature/reminders/StudyRemindersScreen.kt")
progress_ui = text("feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/ProgressWorkspaceScreen.kt")
nav = text("app/src/main/java/com/mikeyphw/studyflow/StudyFlowV2Navigation.kt")
build = text("build.gradle.kts")

for token in (
    "object ReminderReliabilityCenter",
    "ReminderReliabilityStatus.BLOCKED",
    "ReminderReliabilityIssueKind.NOTIFICATION_PERMISSION",
    "ReminderReliabilityIssueKind.EXACT_ALARM_ACCESS",
    "ReminderReliabilityIssueKind.BATTERY_OPTIMIZATION",
    "ReminderReliabilityFallback.WIDGET_AND_MANUAL_CHECK",
    "ReminderReliabilityFallback.INEXACT_ALARM_WINDOW",
):
    require(token in reliability, f"Reminder Reliability Center missing: {token}")

for token in (
    "object StudyEvidenceTimelineBuilder",
    "StudyEvidenceEventKind.SESSION_COMPLETED",
    "StudyEvidenceEventKind.REVIEW_DUE",
    "StudyEvidenceEventKind.QUESTION_ADDED",
    "StudyEvidenceEventKind.PRACTICE_TEST_SUBMITTED",
    "StudyEvidenceEventKind.MOCK_EXAM_SUBMITTED",
    "StudyEvidenceEventKind.GOAL_COMPLETED",
    "StudyEvidenceEventKind.NOTE_PINNED",
    "val studyPlanId: StudyPlanId? = null",
    "targetPlanId == null || session.studyPlanId == targetPlanId",
    "targetPlanId == null || question.studyPlanId == targetPlanId",
    ".mapNotNull(::testEvent)",
    ".mapNotNull(::mockExamEvent)",
    "test.startedAtEpochMillis ?: return null",
    "mockExam.startedAtEpochMillis ?: return null",
    ".take(input.limit)",
):
    require(token in timeline, f"Evidence timeline missing: {token}")

for token in (
    "reminder_reliability_title",
    "canScheduleStudyFlowExactAlarms",
    "isIgnoringBatteryOptimizations",
    "ReminderReliabilityIssueRow",
    "notificationPermissionGranted = notificationPermissionGranted",
    "plannedActivityCandidateCount",
):
    require(token in reminders_ui, f"Reminders UI missing Phase 54 behavior: {token}")

for token in (
    "StudyEvidenceTimelineBuilder.build",
    "StudyEvidenceTimelineInput",
    "studyPlanId = plan.id",
    "evidence_timeline",
    "eventMetadata(event)",
    "event.kind.titleRes()",
    "scheduleManager: ManageStudySchedule",
    "reviewManager: ManageStudyReview",
    "practiceManager: ManageStudyPractice",
):
    require(token in progress_ui, f"Progress workspace missing Phase 55 behavior: {token}")

for token in (
    "scheduleManager = container.manageStudySchedule",
    "reviewManager = container.manageStudyReview",
    "practiceManager = container.manageStudyPractice",
):
    require(token in nav, f"Navigation missing Phase 55 manager wiring: {token}")

require("python3 scripts/verify_phase_54_55.py" in build, "Phase 54/55 verifier is not wired")

for path in (
    "feature/reminders/src/main/res/values/strings.xml",
    "feature/reminders/src/main/res/values-pt-rBR/strings.xml",
):
    xml_root = ET.parse(ROOT / path).getroot()
    names = {element.attrib["name"] for element in xml_root if element.tag == "string"}
    for key in (
        "reminder_reliability_title",
        "reminder_reliability_ready",
        "reminder_reliability_degraded",
        "reminder_reliability_action_needed",
        "reminder_reliability_blocked",
        "reminder_fallback_widget_manual",
        "reminder_issue_exact_alarm",
        "reminder_issue_battery_message",
    ):
        require(key in names, f"reminders resource missing {key} in {path}")

for path in (
    "feature/workspace/src/main/res/values/strings.xml",
    "feature/workspace/src/main/res/values-pt-rBR/strings.xml",
):
    xml_root = ET.parse(ROOT / path).getroot()
    names = {element.attrib["name"] for element in xml_root if element.tag == "string"}
    for key in (
        "evidence_timeline",
        "evidence_session_completed",
        "evidence_review_due",
        "evidence_question_added",
        "evidence_mock_submitted",
        "evidence_note_pinned",
    ):
        require(key in names, f"workspace resource missing {key} in {path}")

workspace_strings = {
    element.attrib["name"]
    for element in ET.parse(ROOT / "feature/workspace/src/main/res/values/strings.xml").getroot()
    if element.tag == "string"
}
for match in sorted(set(re.findall(r"R\.string\.([A-Za-z0-9_]+)", progress_ui))):
    require(match in workspace_strings, f"Progress workspace references missing string: {match}")

reminder_strings = {
    element.attrib["name"]
    for element in ET.parse(ROOT / "feature/reminders/src/main/res/values/strings.xml").getroot()
    if element.tag == "string"
}
for match in sorted(set(re.findall(r"R\.string\.([A-Za-z0-9_]+)", reminders_ui))):
    require(match in reminder_strings, f"Reminders screen references missing string: {match}")

for path in (
    "core/domain/src/test/kotlin/com/mikeyphw/studyflow/core/domain/ReminderReliabilityCenterTest.kt",
    "core/domain/src/test/kotlin/com/mikeyphw/studyflow/core/domain/StudyEvidenceTimelineBuilderTest.kt",
    "docs/architecture/ADR-0033-reminder-reliability-evidence-timeline.md",
    "docs/quality/PHASE_54_55_ACCEPTANCE.md",
    "PHASE_54_55_REMINDER_RELIABILITY_EVIDENCE_TIMELINE.md",
):
    require((ROOT / path).is_file(), f"missing Phase 54/55 documentation or test: {path}")


for token in (
    "evidenceTimelineFiltersEventsToRequestedPlan",
    "evidenceTimelineSkipsUntimedPracticeAndMockEvents",
):
    require(token in text("core/domain/src/test/kotlin/com/mikeyphw/studyflow/core/domain/StudyEvidenceTimelineBuilderTest.kt"),
            f"timeline test missing promise coverage: {token}")

for source_path in [
    ROOT / "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ReminderReliabilityCenter.kt",
    ROOT / "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudyEvidenceTimeline.kt",
    ROOT / "feature/reminders/src/main/java/com/mikeyphw/studyflow/feature/reminders/StudyRemindersScreen.kt",
    ROOT / "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/ProgressWorkspaceScreen.kt",
]:
    source = source_path.read_text(encoding="utf-8")
    require("\x00" not in source, f"NUL byte in {source_path.relative_to(ROOT)}")
    for line_number, line in enumerate(source.splitlines(), 1):
        require("\t" not in line, f"{source_path.relative_to(ROOT)}:{line_number} contains a tab")
        require(len(line) <= 120, f"{source_path.relative_to(ROOT)}:{line_number} exceeds 120 characters")

if FAILURES:
    print("PHASE_54_55_VALIDATION_FAIL")
    for failure in FAILURES:
        print(f"- {failure}")
    sys.exit(1)

print("PHASE_54_55_VALIDATION_OK")
print("Reminder Reliability Center and Evidence Timeline verified")


def verify_phase_54_55_resource_lint_guards(root: Path) -> None:
    reminder_values = [
        root / "feature/reminders/src/main/res/values/strings.xml",
        root / "feature/reminders/src/main/res/values-pt-rBR/strings.xml",
    ]
    for path in reminder_values:
        text = read(path)
        forbidden = [
            "app's safe",
            "plan's quiet",
            "\\u",
        ]
        for token in forbidden:
            require(token not in text, f"{path}: resource string contains lint/AAPT-risky token {token!r}")
        require("reminder_fallback_inexact" in text, f"{path}: missing inexact fallback string")
        require("reminder_issue_quiet_hours_message" in text, f"{path}: missing quiet-hours issue string")

    widget = root / "app/src/main/res/layout/widget_today.xml"
    if widget.exists():
        widget_text = read(widget)
        require("android:tint" not in widget_text, "widget_today.xml must not use android:tint; use app:tint or text styling")
        require('android:layout_height="48dp"' in widget_text, "widget action row must retain 48dp targets")
