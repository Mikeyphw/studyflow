#!/usr/bin/env python3
"""Verify StudyFlow desktop shell and shared-module boundary contracts."""
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


def project_dependencies(build_text: str) -> list[str]:
    return re.findall(r'project\("([^"]+)"\)', build_text)


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


def main() -> int:
    failures: list[str] = []

    try:
        devtool = read(".devtool.toml")
        settings = read("settings.gradle.kts")
        root_build = read("build.gradle.kts")
        desktop_build = read("desktopApp/build.gradle.kts")
        main_source = read("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/Main.kt")
        boundary_source = read(
            "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/StudyFlowDesktopBoundary.kt",
        )
        app_source = read("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/StudyFlowDesktopApp.kt")
        ports_source = read("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopPlatformPorts.kt")
        resources = read("desktopApp/src/main/resources/studyflow-desktop.properties")
        icon_placeholder = read("desktopApp/src/main/resources/icons/studyflow-icon-placeholder.txt")
        test_source = read(
            "desktopApp/src/test/kotlin/com/mikeyphw/studyflow/desktop/StudyFlowDesktopBoundaryTest.kt",
        )
        docs = read("DESKTOP_DOV02_DOV03_SHELL_CONTRACTS.md")
        readme = read("desktopApp/README.md")

        require('":desktopApp"' in settings, "settings.gradle.kts must keep :desktopApp included")
        require("alias(libs.plugins.kotlin.jvm)" in desktop_build, "desktopApp must use the Kotlin JVM plugin")
        require(
            "alias(libs.plugins.compose.multiplatform)" in desktop_build,
            "desktopApp must use the Compose Multiplatform plugin",
        )
        require(
            "alias(libs.plugins.compose.compiler)" in desktop_build,
            "desktopApp must use the Compose compiler plugin",
        )
        require("compose.desktop" in desktop_build, "desktopApp must configure compose.desktop")
        require(
            'mainClass = "com.mikeyphw.studyflow.desktop.MainKt"' in desktop_build,
            "desktopApp must expose the Compose Desktop application main class",
        )
        require(
            "\n    application\n" not in desktop_build and "\napplication {" not in desktop_build,
            "desktopApp must not apply/configure Gradle's application plugin alongside Compose Desktop",
        )
        require("compose.desktop.currentOs" in desktop_build, "desktopApp must depend on Compose Desktop")

        dependencies = project_dependencies(desktop_build)
        allowed = [":core:model", ":core:domain", ":core:link", ":core:portability"]
        require(dependencies == allowed, f"desktopApp project dependencies must be exactly {allowed}, got {dependencies}")
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

        require("Window(" in main_source, "Main.kt must create a Compose Desktop Window")
        require("exitApplication" in main_source, "Main.kt must use a desktop close handler")
        require("@Composable" in app_source, "StudyFlowDesktopApp must provide a composable shell")
        require("MaterialTheme" in app_source, "StudyFlowDesktopApp must render a themed desktop shell")
        require("StudyFlowDesktopBoundary" in boundary_source, "desktop boundary object must exist")
        require("DesktopPlatformPorts" in ports_source, "desktop-safe platform ports must exist")
        require("NoOpDesktopPlatformPorts" in ports_source, "safe no-op desktop adapters must exist")
        require("Android intents" in ports_source, "desktop ports must document the Android boundary")
        require("app.name=StudyFlow" in resources, "desktop resources must include app metadata")
        require("icon placeholder" in icon_placeholder.lower(), "desktop icon placeholder must exist")
        require(":core:model" in boundary_source, "desktop boundary must list :core:model")
        require(":core:domain" in boundary_source, "desktop boundary must list :core:domain")
        require(":core:link" in boundary_source, "desktop boundary must list :core:link")
        require(":core:portability" in boundary_source, "desktop boundary must list :core:portability")
        require(":feature:" in boundary_source, "desktop boundary must forbid feature modules")
        require(":core:database" in boundary_source, "desktop boundary must forbid Android persistence")
        require("StudyFlowDesktopBoundaryTest" in test_source, "desktop boundary JUnit smoke test must exist")
        require("assertFalse" in test_source, "desktop boundary test must check forbidden dependencies")
        require("NoOpDesktopPlatformPorts" in test_source, "desktop port smoke test must exist")

        require(
            'tasks.register<Exec>("verifyStudyFlowDesktopShellContracts")' in root_build,
            "root build must register verifyStudyFlowDesktopShellContracts",
        )
        require(
            "verifyStudyFlowDesktopShellContracts" in devtool,
            ".devtool.toml must expose the shell contract verifier",
        )
        require(":desktopApp:compileKotlin" in devtool, ".devtool.toml must compile the desktop module")
        require(":desktopApp:test" in devtool, ".devtool.toml must test the desktop module")
        require("DOV02" in docs and "DOV03" in docs, "overlay documentation must cover both DOV02 and DOV03")
        require("Shared boundary" in readme, "desktop README must document the shared boundary")

        ensure_no_forbidden_imports(failures)
    except AssertionError as exc:
        failures.append(str(exc))

    if failures:
        for failure in failures:
            print(f"DESKTOP_SHELL_CONTRACTS: FAIL: {failure}")
        return 1

    print("DESKTOP_SHELL_CONTRACTS: PASS")
    return 0


if __name__ == "__main__":
    sys.exit(main())
