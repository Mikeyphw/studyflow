#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SUBJECTS = ROOT / "feature/subjects/src/main/java/com/mikeyphw/studyflow/feature/subjects/StudyContentScreen.kt"
SCHEDULE = ROOT / "feature/schedule/src/main/java/com/mikeyphw/studyflow/feature/schedule/StudyScheduleScreen.kt"
SUBJECT_STRINGS = [
    ROOT / "feature/subjects/src/main/res/values/strings.xml",
    ROOT / "feature/subjects/src/main/res/values-pt-rBR/strings.xml",
]
SCHEDULE_STRINGS = [
    ROOT / "feature/schedule/src/main/res/values/strings.xml",
    ROOT / "feature/schedule/src/main/res/values-pt-rBR/strings.xml",
]


def require(text: str, needle: str, label: str) -> None:
    if needle not in text:
        raise SystemExit(f"OV03 contract failed: missing {label}: {needle}")


def reject(text: str, needle: str, label: str) -> None:
    if needle in text:
        raise SystemExit(f"OV03 contract failed: found forbidden {label}: {needle}")


def main() -> None:
    subjects = SUBJECTS.read_text(encoding="utf-8")
    schedule = SCHEDULE.read_text(encoding="utf-8")

    require(subjects, "FlowRow(", "responsive syllabus FlowRow")
    reject(subjects, ".horizontalScroll(rememberScrollState())", "single-line syllabus scrolling")
    require(subjects, "Icons.Outlined.MoreVert", "syllabus overflow trigger")
    require(subjects, "DropdownMenuItem(", "syllabus overflow actions")
    require(subjects, "subject = visible.subject", "stable SubjectCard subject input")
    require(subjects, "visibleTopicCount = visible.visibleTopicCount", "stable SubjectCard topic count")
    require(subjects, "topicTree = visible.topicTree", "stable SubjectCard topic tree")
    require(subjects, "if (visible.subject.archived)", "qualified archive toggle")

    require(schedule, "FlowRow(", "responsive schedule FlowRow")
    require(schedule, "Icons.Outlined.MoreVert", "schedule overflow trigger")
    require(schedule, "DeleteOverflowMenu(", "schedule delete overflow")
    require(schedule, "DropdownMenuItem(", "schedule overflow item")
    require(schedule, "confirming = true", "destructive confirmation")
    reject(schedule, "ConfirmingDeleteButton(", "inline schedule delete button")

    for path in SUBJECT_STRINGS + SCHEDULE_STRINGS:
        require(path.read_text(encoding="utf-8"), 'name="more_actions"', f"more-actions a11y string in {path}")

    print("OV03 responsive syllabus/schedule source contracts passed.")


if __name__ == "__main__":
    main()
