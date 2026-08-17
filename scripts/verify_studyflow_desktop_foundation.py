#!/usr/bin/env python3
"""Verify StudyFlow desktop foundation wiring.

This verifier is intentionally static. DOV01 should not require the desktop app
shell to compile yet; it only confirms that Gradle, Devtool, and version-catalog
hooks are ready for the next overlays.
"""
from __future__ import annotations

from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]


def read(relative: str) -> str:
    path = ROOT / relative
    if not path.is_file():
        raise AssertionError(f"missing required file: {relative}")
    return path.read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def main() -> int:
    failures: list[str] = []

    try:
        devtool = read(".devtool.toml")
        settings = read("settings.gradle.kts")
        versions = read("gradle/libs.versions.toml")
        build = read("build.gradle.kts")
        desktop_readme = read("desktopApp/README.md")
        gitignore = read(".gitignore")

        require('default_target = "studyflow_desktop"' in devtool,
                ".devtool.toml must select studyflow_desktop as the default target")
        require('[targets.studyflow_android]' in devtool,
                ".devtool.toml must preserve the Android target")
        require('[targets.studyflow_desktop]' in devtool,
                ".devtool.toml must declare the desktop target")
        require(re.search(r'\[targets\.studyflow_desktop\]\s*\ntype = "gradle"', devtool),
                "studyflow_desktop must use the JVM/Gradle Devtool runner")
        require('android_safe = false' in devtool,
                "desktop validation must not be marked Android-safe")
        require('verifyStudyFlowDesktopFoundation' in devtool,
                "desktop target must expose the foundation verifier")

        require('":desktopApp"' in settings,
                "settings.gradle.kts must include :desktopApp")
        require('composeMultiplatform = "1.9.3"' in versions,
                "version catalog must pin the Compose Multiplatform Gradle plugin")
        require('compose-multiplatform = { id = "org.jetbrains.compose"' in versions,
                "version catalog must expose the Compose Multiplatform plugin alias")
        require('compose-compiler = { id = "org.jetbrains.kotlin.plugin.compose"' in versions,
                "version catalog must expose the Compose compiler plugin alias")
        require('kotlin-jvm = { id = "org.jetbrains.kotlin.jvm"' in versions,
                "version catalog must expose the Kotlin JVM plugin alias")

        require('"desktopApp"' in build,
                "desktopApp must be part of quality source roots")
        require('tasks.register<Exec>("verifyStudyFlowDesktopFoundation")' in build,
                "root build must register verifyStudyFlowDesktopFoundation")
        require("Do not place Android-only APIs" in desktop_readme,
                "desktopApp README must document the no Android-only API boundary")
        require("*.msi" in gitignore and "*.deb" in gitignore,
                ".gitignore must cover desktop package artifacts")
    except AssertionError as exc:
        failures.append(str(exc))

    if failures:
        for failure in failures:
            print(f"DESKTOP_FOUNDATION: FAIL: {failure}")
        return 1

    print("DESKTOP_FOUNDATION: PASS")
    return 0


if __name__ == "__main__":
    sys.exit(main())
