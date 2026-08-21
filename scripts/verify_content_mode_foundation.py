#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"CMOV-01 content mode foundation failed: {message}")


def require_contains(path: str, *needles: str) -> str:
    text = read(path)
    missing = [needle for needle in needles if needle not in text]
    require(not missing, f"{path} missing {missing}")
    return text


def main() -> None:
    mode = require_contains(
        "core/model/src/main/kotlin/com/mikeyphw/studyflow/core/model/StudyContentMode.kt",
        "enum class StudyContentMode",
        "EXAM_PLAN",
        "PROGRAMMING_COURSE",
        "LANGUAGE_DECK",
        "READING_STUDY",
        "GENERAL_SKILL",
        "supportsCodeBlocks = true",
        "supportsTermuxRuntime",
        "supportsCodingExercises",
    )
    require("contentModeOrDefault" in mode, "unknown content modes must have an exam-plan fallback")

    require_contains(
        "core/model/src/main/kotlin/com/mikeyphw/studyflow/core/model/StudyPlan.kt",
        "val contentMode: StudyContentMode = StudyContentMode.EXAM_PLAN",
    )
    require_contains(
        "core/database/src/main/java/com/mikeyphw/studyflow/core/database/entity/StudyFlowEntities.kt",
        "val contentMode: String = \"EXAM_PLAN\"",
    )
    require_contains(
        "core/database/src/main/java/com/mikeyphw/studyflow/core/database/mapper/StudyPlanEntityMapper.kt",
        "contentMode = contentModeOrDefault(contentMode)",
        "contentMode = contentMode.name",
    )
    require_contains(
        "core/database/src/main/java/com/mikeyphw/studyflow/core/database/StudyFlowDatabase.kt",
        "const val SCHEMA_VERSION = ",
    )
    require_contains(
        "core/database/src/main/java/com/mikeyphw/studyflow/core/database/StudyFlowMigrations.kt",
        "Migration4To5",
        "ALTER TABLE study_plans ADD COLUMN contentMode TEXT NOT NULL DEFAULT 'EXAM_PLAN'",
    )
    require_contains(
        "core/database/src/main/java/com/mikeyphw/studyflow/core/database/StudyPlanGraphPortabilityStore.kt",
        '"study_plans" -> expected - "contentMode"',
        'put("contentMode", text("EXAM_PLAN"))',
        '"contentMode"',
    )
    require_contains(
        "feature/plans/src/main/java/com/mikeyphw/studyflow/feature/plans/CreatePlanScreen.kt",
        "val contentMode: StudyContentMode",
        "StudyContentMode.entries.forEach",
        "contentMode = selectedContentMode",
        "content_mode_programming_status",
    )
    require_contains(
        "app/src/main/java/com/mikeyphw/studyflow/StudyFlowApp.kt",
        "contentMode = contentMode",
        "contentMode = form.contentMode",
    )
    require_contains(
        "feature/plans/src/main/java/com/mikeyphw/studyflow/feature/plans/PlanListScreen.kt",
        "contentModeCardLabel(plan.contentMode)",
    )
    strings = read("feature/plans/src/main/res/values/strings.xml")
    for name in (
        "content_mode_exam_plan",
        "content_mode_programming_course",
        "content_mode_language_deck",
        "content_mode_reading_study",
        "content_mode_general_skill",
    ):
        require(f'name="{name}"' in strings, f"missing string {name}")

    print("CONTENT_MODE_FOUNDATION: PASS")


if __name__ == "__main__":
    main()
