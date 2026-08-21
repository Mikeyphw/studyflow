#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(text: str, needle: str, label: str, failures: list[str]) -> None:
    if needle not in text:
        failures.append(f"{label}: missing {needle!r}")


def main() -> None:
    failures: list[str] = []
    domain = read("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ModeAwareAnalytics.kt")
    ui = read("feature/analytics/src/main/java/com/mikeyphw/studyflow/feature/analytics/AnalyticsUiModels.kt")
    screen = read("feature/analytics/src/main/java/com/mikeyphw/studyflow/feature/analytics/StudyAnalyticsScreen.kt")
    strings = read("feature/analytics/src/main/res/values/strings.xml")
    pt_strings = read("feature/analytics/src/main/res/values-pt-rBR/strings.xml")
    domain_test = read("core/domain/src/test/kotlin/com/mikeyphw/studyflow/core/domain/ModeAwareAnalyticsTest.kt")
    ui_test = read("feature/analytics/src/test/java/com/mikeyphw/studyflow/feature/analytics/AnalyticsUiModelsTest.kt")
    build = read("build.gradle.kts")

    for token in [
        "PROGRAMMING_COURSE",
        "LANGUAGE_DECK",
        "READING_STUDY",
        "GENERAL_SKILL",
        "MODULES_COMPLETED",
        "EXERCISES_COMPLETED",
        "DEBUGGING_ACCURACY",
        "PROJECT_PROGRESS",
        "IDIOMS_LEARNED",
        "IDIOMS_DUE",
        "USAGE_ACCURACY",
        "MISTAKE_PATTERNS",
        "REGISTER_COVERAGE",
        "ModeAwareWeakAreaLabel.WEAK_CONCEPTS",
        "ModeAwareWeakAreaLabel.LANGUAGE_MISTAKE_PATTERNS",
    ]:
        require(domain, token, "ModeAwareAnalytics.kt", failures)

    require(ui, "ModeAwareAnalyticsCalculator", "AnalyticsUiModels.kt", failures)
    require(ui, "StudyContentMode = StudyContentMode.EXAM_PLAN", "AnalyticsUiModels.kt", failures)
    require(ui, "weakAreasTitleRes", "AnalyticsUiModels.kt", failures)
    require(screen, "metricRows(plan.contentMode)", "StudyAnalyticsScreen.kt", failures)
    require(screen, "plan.contentMode.weakAreasTitleRes()", "StudyAnalyticsScreen.kt", failures)

    for token in [
        "modules_completed",
        "exercises_completed",
        "debugging_accuracy",
        "project_progress",
        "idioms_learned",
        "idioms_due",
        "usage_accuracy",
        "mistake_patterns",
        "register_coverage",
        "reading_progress",
        "skill_progress",
    ]:
        require(strings, token, "analytics English strings", failures)
        require(pt_strings, token, "analytics Portuguese strings", failures)

    require(domain_test, "programming analytics promote modules exercises", "ModeAwareAnalyticsTest.kt", failures)
    require(domain_test, "language analytics promote idioms usage", "ModeAwareAnalyticsTest.kt", failures)
    require(ui_test, "programmingModeCreatesProgrammingMetricRows", "AnalyticsUiModelsTest.kt", failures)
    require(ui_test, "languageModeCreatesLanguageMetricRows", "AnalyticsUiModelsTest.kt", failures)
    require(build, "verifyModeAwareAnalytics", "build.gradle.kts", failures)

    if failures:
        raise SystemExit("MODE_AWARE_ANALYTICS_VALIDATION_FAILED\n" + "\n".join(failures))
    print("MODE_AWARE_ANALYTICS_VALIDATION_OK")


if __name__ == "__main__":
    main()
