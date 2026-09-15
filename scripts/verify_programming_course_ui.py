#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"CMOV-05 programming course UI failed: {message}")


def require_contains(path: str, *needles: str) -> str:
    text = read(path)
    missing = [needle for needle in needles if needle not in text]
    require(not missing, f"{path} missing {missing}")
    return text


def main() -> None:
    require_contains(
        "core/model/src/main/kotlin/com/mikeyphw/studyflow/core/model/ProgrammingCoursePresentation.kt",
        "data class ProgrammingCourseUiLabels",
        "ProgrammingCourseDashboardSummary",
        "Modules",
        "Exercises",
        "Projects",
        "Skill progress",
        "labelsForContentMode",
    )
    panel = require_contains(
        "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/ProgrammingCourseWorkspacePanel.kt",
        "ProgrammingCourseWorkspacePanel",
        "StudyFlowCodeBlock",
        "StudyFlowFocusActionSurface",
        "StudyFlowMetricStrip",
        "ProgrammingLanguage.KOTLIN",
        "programming_termux_ready",
    )
    require("Scaffold(" not in panel and "TopAppBar(" not in panel, "programming panel must use the custom shell")

    require_contains(
        "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/TodayWorkspaceScreen.kt",
        "StudyContentMode.PROGRAMMING_COURSE",
        "ProgrammingCourseWorkspacePanel",
        "ProgrammingCourseDashboardSummary",
        "programmingManager.observeSnapshot(plan.id)",
        "termuxRuntimeReady = snapshot.termuxRuntimeReady",
    )
    today = read("feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/TodayWorkspaceScreen.kt")
    require(
        "termuxRuntimeReady = plan.contentMode.capabilities.supportsTermuxRuntime" not in today,
        "programming Today panel must use persisted snapshot readiness, not content-mode capability flags",
    )

    require_contains(
        "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/PlanWorkspaceScreen.kt",
        "planPrimaryDateLabel(plan)",
        "planContentLabel(plan)",
        "planLibraryLabel(plan)",
        "programming_modules",
        "programming_resources",
    )
    strings = read("feature/workspace/src/main/res/values/strings.xml")
    for name in (
        "programming_mode_label",
        "programming_workspace_title",
        "programming_modules",
        "programming_exercises",
        "programming_termux_ready",
        "programming_resources",
    ):
        require(f'name="{name}"' in strings, f"missing string {name}")
    require_contains(
        "core/model/src/test/kotlin/com/mikeyphw/studyflow/core/model/ProgrammingCoursePresentationTest.kt",
        "programming courses expose programming labels",
        "dashboard counts cannot be negative",
    )
    require_contains(
        "feature/workspace/src/test/java/com/mikeyphw/studyflow/feature/workspace/ProgrammingCourseUiContractTest.kt",
        "programming Today panel uses shell primitives and syntax code blocks",
        "Today and Plan routes expose programming mode labels",
    )
    require_contains(
        "build.gradle.kts",
        "verifyProgrammingCourseUi",
        "scripts/verify_programming_course_ui.py",
    )

    print("PROGRAMMING_COURSE_UI: PASS")


if __name__ == "__main__":
    main()
