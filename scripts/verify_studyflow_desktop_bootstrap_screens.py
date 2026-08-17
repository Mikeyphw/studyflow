#!/usr/bin/env python3
"""Verify StudyFlow desktop DOV04/DOV05 bootstrap and screen contracts."""
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
    source_root = DESKTOP / "src"
    if not source_root.is_dir():
        return []
    return sorted(source_root.rglob("*.kt"))


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
    allowed = [":core:model", ":core:domain", ":core:portability"]
    if dependencies != allowed:
        failures.append(f"desktopApp project dependencies must be exactly {allowed}, got {dependencies}")
    forbidden_prefixes = (
        ":app",
        ":feature:",
        ":core:data",
        ":core:database",
        ":core:designsystem",
        ":core:diagnostics",
        ":core:i18n",
        ":core:navigation",
        ":core:testing",
    )
    for dependency in dependencies:
        if any(dependency == prefix or dependency.startswith(prefix) for prefix in forbidden_prefixes):
            failures.append(f"desktopApp must not depend on Android-only module {dependency}")


def main() -> int:
    failures: list[str] = []
    try:
        devtool = read(".devtool.toml")
        root_build = read("build.gradle.kts")
        desktop_build = read("desktopApp/build.gradle.kts")
        app = read("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/StudyFlowDesktopApp.kt")
        bootstrap = read("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopAppBootstrap.kt")
        state = read("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopAppState.kt")
        services = read("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopStudyFlowServices.kt")
        plan_repo = read("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/InMemoryStudyPlanRepository.kt")
        library_repo = read("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/InMemoryStudyLibraryRepository.kt")
        sample = read("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopStudyFlowSampleData.kt")
        ui_models = read("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopUiModels.kt")
        layout = read("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopLayout.kt")
        dashboard = read("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopDashboardScreen.kt")
        library = read("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopLibraryScreen.kt")
        tests = read(
            "desktopApp/src/test/kotlin/com/mikeyphw/studyflow/desktop/"
            "StudyFlowDesktopBootstrapScreensTest.kt",
        )
        resources = read("desktopApp/src/main/resources/studyflow-desktop.properties")
        docs = read("DESKTOP_DOV04_DOV05_BOOTSTRAP_DASHBOARD_LIBRARY.md")
        readme = read("desktopApp/README.md")

        ensure_project_dependencies_are_still_sealed(desktop_build, failures)
        require("implementation(libs.kotlinx.coroutines.core)" in desktop_build, "desktopApp must depend on coroutines")
        require("StudyFlowDesktopBootstrap" in bootstrap, "desktop composition root must exist")
        require("DesktopAppState" in state, "desktop app state holder must exist")
        require("DesktopRoute" in state, "desktop navigation route state must exist")
        require("DesktopScreenStatus" in state, "desktop status modeling must exist")
        for status in ("LOADING", "CONTENT", "EMPTY", "ERROR"):
            require(status in state or status in library, f"desktop state must model {status}")

        require("ManageStudyPlan" in services, "desktop services must wire ManageStudyPlan")
        require("ManageStudyLibrary" in services, "desktop services must wire ManageStudyLibrary")
        require("AppShellReadinessAuditor" in services, "desktop services must reuse core domain readiness")
        require("firstOrNull()?.id" in services, "desktop services must not crash when no Study Plan exists")
        require("emptyLibrarySnapshot" in services, "desktop services must provide an empty-state snapshot")
        require("StudyPlanRepository" in plan_repo, "desktop plan repository must implement core domain contract")
        require("StudyLibraryRepository" in library_repo, "desktop library repository must implement core domain contract")
        require("MutableStateFlow" in plan_repo, "plan repository must expose JVM observable state")
        require("MutableStateFlow" in library_repo, "library repository must expose JVM observable state")
        require("DesktopStudyFlowSampleData" in sample, "desktop sample state must be explicit")
        require("StudyPlan(" in sample, "sample state must use core:model StudyPlan")
        require("StudyMaterial(" in sample, "sample state must use core:model StudyMaterial")
        require("VideoResource(" in sample, "sample state must use core:model VideoResource")

        require("DesktopDashboardScreen" in app, "app must route to the dashboard screen")
        require("DesktopLibraryScreen" in app, "app must route to the library screen")
        require("DesktopSidebar" in app, "app must render desktop navigation chrome")
        require("DesktopDashboardState" in ui_models, "dashboard state model must exist")
        require("DesktopLibraryScreenState" in ui_models, "library state model must exist")
        require("DesktopLibraryMaterialRow" in ui_models, "library material row model must exist")
        require("LibraryProgressCalculator" in ui_models, "library UI model must reuse core domain progress logic")
        require("DesktopMetricCard" in layout, "reusable metric card must exist")
        require("DesktopSectionCard" in layout, "reusable section card must exist")
        require("DesktopStateMessage" in layout, "reusable state message must exist")
        require("DesktopTagRow" in layout, "reusable tag row must exist")
        require("verticalScroll" in dashboard, "dashboard must support mouse/keyboard scrolling")
        require("LazyColumn" in library, "library resource list must use a scrollable list")
        require("Resource list" in library, "library screen must contain resource list")
        require("Resource detail" in library, "library screen must contain material detail shell")
        require("Video list" in library or "Videos" in library, "library screen must include videos shell")
        require("No resources yet" in library, "library screen must provide empty state")
        require("Library unavailable" in library, "library screen must provide error state")
        require("Loading library" in library, "library screen must provide loading state")

        require("StudyFlowDesktopBootstrapScreensTest" in tests, "desktop bootstrap screen tests must exist")
        require("desktopBootstrapWiresDomainManagersAndSampleState" in tests, "bootstrap wiring test missing")
        require("desktopLibraryCreatesListAndDetailStateFromCoreModels" in tests, "library state test missing")
        require("navigationStateMovesBetweenDashboardAndLibrary" in tests, "navigation state test missing")
        require("emptyRepositoriesRenderEmptyStatesInsteadOfCrashing" in tests, "empty-state robustness test missing")
        require("dov04-dov05" in resources, "desktop resources must record DOV04/DOV05 stage")
        require("verifyStudyFlowDesktopBootstrapScreens" in root_build, "root build must register verifier")
        require("verifyStudyFlowDesktopBootstrapScreens" in devtool, ".devtool.toml must expose verifier")
        require("DOV04" in docs and "DOV05" in docs, "overlay docs must cover DOV04 and DOV05")
        require("in-memory JVM repositories" in readme, "desktop README must document in-memory bootstrap")
        require("Persistence is intentionally deferred" in readme, "desktop README must document deferred persistence")
        ensure_no_forbidden_imports(failures)
    except AssertionError as exc:
        failures.append(str(exc))

    if failures:
        for failure in failures:
            print(f"DESKTOP_BOOTSTRAP_SCREENS: FAIL: {failure}")
        return 1

    print("DESKTOP_BOOTSTRAP_SCREENS: PASS")
    return 0


if __name__ == "__main__":
    sys.exit(main())
