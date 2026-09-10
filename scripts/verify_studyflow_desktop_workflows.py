#!/usr/bin/env python3
"""Verify StudyFlow desktop DOV06/DOV07 workflow contracts."""
from __future__ import annotations

from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
DESKTOP = ROOT / "desktopApp"


def read(relative: str) -> str:
    path = ROOT / relative
    if not path.is_file():
        raise AssertionError(f"missing required file: {relative}")
    return path.read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def desktop_kotlin_files() -> list[Path]:
    return sorted((DESKTOP / "src").rglob("*.kt"))


def ensure_no_forbidden_imports(failures: list[str]) -> None:
    forbidden_import_prefixes = (
        "android.",
        "androidx.activity.",
        "androidx.lifecycle.",
        "androidx.navigation.",
        "androidx.room.",
        "androidx.datastore.",
        "androidx.work.",
        "com.mikeyphw.studyflow.app.",
        "com.mikeyphw.studyflow.core.data.",
        "com.mikeyphw.studyflow.core.database.",
        "com.mikeyphw.studyflow.core.designsystem.",
        "com.mikeyphw.studyflow.core.i18n.",
        "com.mikeyphw.studyflow.core.navigation.",
        "com.mikeyphw.studyflow.feature.",
    )
    for source_file in desktop_kotlin_files():
        for line_number, line in enumerate(source_file.read_text(encoding="utf-8").splitlines(), start=1):
            stripped = line.strip()
            if not stripped.startswith("import "):
                continue
            imported = stripped.removeprefix("import ").strip()
            if any(imported.startswith(prefix) for prefix in forbidden_import_prefixes):
                relative = source_file.relative_to(ROOT)
                failures.append(f"{relative}:{line_number}: forbidden desktop import {imported}")


def ensure_project_dependencies_are_still_sealed(build_text: str, failures: list[str]) -> None:
    dependencies = re.findall(r'project\("([^"]+)"\)', build_text)
    allowed = [":core:model", ":core:domain", ":core:link", ":core:portability"]
    if dependencies != allowed:
        failures.append(f"desktopApp project dependencies must be exactly {allowed}, got {dependencies}")


def main() -> int:
    failures: list[str] = []
    try:
        devtool = read(".devtool.toml")
        root_build = read("build.gradle.kts")
        desktop_build = read("desktopApp/build.gradle.kts")
        app = read("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/StudyFlowDesktopApp.kt")
        state = read("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopAppState.kt")
        services = read("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopStudyFlowServices.kt")
        sample = read("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopStudyFlowSampleData.kt")
        schedule_repo = read("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/InMemoryStudyScheduleRepository.kt")
        session_repo = read("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/InMemoryStudySessionRepository.kt")
        practice_repo = read("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/InMemoryStudyPracticeRepository.kt")
        analytics_search = read("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopAnalyticsDiscovery.kt")
        ui_models = read("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopWorkflowUiModels.kt")
        workflow_screens = read("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopPlannerPracticeScreens.kt")
        utility_screens = read(
            "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/"
            "DesktopAnalyticsSearchSettingsScreens.kt",
        )
        tests = read(
            "desktopApp/src/test/kotlin/com/mikeyphw/studyflow/desktop/"
            "StudyFlowDesktopWorkflowScreensTest.kt",
        )
        resources = read("desktopApp/src/main/resources/studyflow-desktop.properties")
        docs = read("DESKTOP_DOV06_DOV07_PLANNER_PRACTICE_ANALYTICS_SEARCH_SETTINGS.md")
        readme = read("desktopApp/README.md")

        ensure_project_dependencies_are_still_sealed(desktop_build, failures)
        ensure_no_forbidden_imports(failures)

        for route in ("PLANNER", "PRACTICE", "ANALYTICS", "SEARCH", "SETTINGS"):
            require(route in state, f"DesktopRoute.{route} must exist")
            require(f"DesktopRoute.{route}" in app, f"StudyFlowDesktopApp must route {route}")

        for name in (
            "InMemoryStudyScheduleRepository",
            "InMemoryStudySessionRepository",
            "InMemoryStudyPracticeRepository",
            "InMemoryStudyAnalyticsRepository",
            "DesktopStudyDiscoveryRepository",
            "DesktopPreferencesStore",
        ):
            require(name in services or name in analytics_search, f"{name} must be wired")

        for manager in (
            "ManageStudySchedule",
            "ManageStudySession",
            "ManageStudyPractice",
            "ManageStudyAnalytics",
            "ManageStudyDiscovery",
        ):
            require(manager in services, f"{manager} must be exposed from desktop services")

        require("StudyScheduleRepository" in schedule_repo, "schedule repository must implement the core domain contract")
        require("StudySessionRepository" in session_repo, "session repository must implement the core domain contract")
        require("StudyPracticeRepository" in practice_repo, "practice repository must implement the core domain contract")
        require("StudyAnalyticsRepository" in analytics_search, "analytics repository must implement the core domain contract")
        require("StudyDiscoveryRepository" in analytics_search, "search repository must implement the core domain contract")
        require("AppPreferences" in analytics_search, "settings store must use core:model AppPreferences")
        require("RecentSearchHistory" in analytics_search, "search must persist recent queries through core:model helper")

        for model in (
            "PlannedActivity(",
            "StudySession(",
            "Question(",
            "StudyTest(",
            "MockExam(",
            "StudyStrategy(",
            "ScheduleRule(",
        ):
            require(model in sample, f"sample data must include {model}")

        for state_name in (
            "DesktopPlannerScreenState",
            "DesktopPracticeScreenState",
            "DesktopAnalyticsScreenState",
            "DesktopSearchScreenState",
            "DesktopSettingsScreenState",
        ):
            require(state_name in ui_models or state_name in services, f"{state_name} must exist")

        require("PracticeAnalytics" in ui_models, "practice UI model must reuse core domain practice analytics")
        require("StudyAnalyticsCalculator" in analytics_search, "analytics repository must reuse core domain analytics calculator")
        require("GlobalSearchRanker" in analytics_search, "search repository must reuse core domain search ranker")
        require("StudyRecommendation" in analytics_search, "search screen must include recommendation support")
        require("VerticalScrollbar" in workflow_screens, "planner/practice lists must use desktop scrollbars")
        require("OutlinedTextField" in utility_screens, "search screen must include a desktop text field")
        require("Cycle theme" in utility_screens, "settings screen must provide theme control")
        require("Cycle language" in utility_screens, "settings screen must provide language control")
        require("Native file pickers" in ui_models, "settings must document deferred native adapters")

        for test_name in (
            "desktopPlannerAndPracticeStatesExposeDomainBackedData",
            "desktopAnalyticsSearchAndSettingsUseCoreContracts",
            "desktopRoutesIncludeWorkflowScreens",
            "emptyWorkflowRepositoriesRenderEmptyStates",
        ):
            require(test_name in tests, f"missing workflow test {test_name}")

        require("dov06-dov07" in resources, "desktop resources must record DOV06/DOV07 stage")
        require("verifyStudyFlowDesktopWorkflows" in root_build, "root build must register workflow verifier")
        require("verifyStudyFlowDesktopWorkflows" in devtool, ".devtool.toml must expose workflow verifier")
        require("DOV06" in docs and "DOV07" in docs, "overlay docs must cover DOV06 and DOV07")
        require("DOV06/DOV07" in readme, "desktop README must mention DOV06/DOV07 expansion")
    except AssertionError as exc:
        failures.append(str(exc))

    if failures:
        for failure in failures:
            print(f"DESKTOP_WORKFLOWS: FAIL: {failure}")
        return 1

    print("DESKTOP_WORKFLOWS: PASS")
    return 0


if __name__ == "__main__":
    sys.exit(main())
