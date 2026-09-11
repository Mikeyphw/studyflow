#!/usr/bin/env python3
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"CMOV-02 programming course model failed: {message}")


def require_contains(path: str, *needles: str) -> str:
    text = read(path)
    missing = [needle for needle in needles if needle not in text]
    require(not missing, f"{path} missing {missing}")
    return text


def main() -> None:
    mode = require_contains(
        "core/model/src/main/kotlin/com/mikeyphw/studyflow/core/model/StudyContentMode.kt",
        "StudyContentMode.PROGRAMMING_COURSE",
        "supportsCodeBlocks = true",
        "supportsTermuxRuntime = true",
        "supportsCodingExercises = true",
    )
    require(mode.count("supportsCodingExercises = true") == 1, "only programming mode should enable coding exercises")

    require_contains(
        "core/model/src/main/kotlin/com/mikeyphw/studyflow/core/model/Identifiers.kt",
        "ProgrammingCourseProfileId",
        "CodeSnippetId",
        "CodingExerciseId",
        "MiniProjectId",
    )
    require_contains(
        "core/model/src/main/kotlin/com/mikeyphw/studyflow/core/model/ProgrammingCourse.kt",
        "enum class ProgrammingLanguage",
        "data class ProgrammingCourseProfile",
        "data class CodeSnippet",
        "data class CodingExercise",
        "data class MiniProject",
        "data class TermuxRuntimePreference",
        "fun isSafeTermuxCommandTemplate",
    )
    require_contains(
        "core/model/src/test/kotlin/com/mikeyphw/studyflow/core/model/ProgrammingCourseModelTest.kt",
        "programming course mode enables code exercise and termux runtime capabilities",
        "termux command templates reject shell chaining",
    )
    require_contains(
        "core/database/src/main/java/com/mikeyphw/studyflow/core/database/entity/ProgrammingCourseEntities.kt",
        "ProgrammingCourseProfileEntity",
        "CodeSnippetEntity",
        "CodingExerciseEntity",
        "MiniProjectEntity",
        "TermuxRuntimePreferenceEntity",
        "tableName = \"coding_exercises\"",
    )
    require_contains(
        "core/database/src/main/java/com/mikeyphw/studyflow/core/database/dao/ProgrammingCourseDao.kt",
        "observeCodeSnippets",
        "observeCodingExercises",
        "observeMiniProjects",
        "observeTermuxRuntimePreference",
    )
    require_contains(
        "core/database/src/main/java/com/mikeyphw/studyflow/core/database/StudyFlowDatabase.kt",
        "ProgrammingCourseProfileEntity::class",
        "CodeSnippetEntity::class",
        "CodingExerciseEntity::class",
        "MiniProjectEntity::class",
        "TermuxRuntimePreferenceEntity::class",
        "abstract fun programmingCourseDao()",
    )
    require_contains(
        "core/database/src/main/java/com/mikeyphw/studyflow/core/database/StudyFlowMigrations.kt",
        "Migration5To6",
        "CREATE TABLE IF NOT EXISTS programming_course_profiles",
        "CREATE TABLE IF NOT EXISTS code_snippets",
        "CREATE TABLE IF NOT EXISTS coding_exercises",
        "CREATE TABLE IF NOT EXISTS mini_projects",
        "CREATE TABLE IF NOT EXISTS termux_runtime_preferences",
    )
    require_contains(
        "core/database/src/main/java/com/mikeyphw/studyflow/core/database/StudyPlanGraphDuplicator.kt",
        "programming_course_profiles",
        "code_snippets",
        "coding_exercises",
        "mini_projects",
        "termux_runtime_preferences",
    )
    require_contains(
        "core/database/src/main/java/com/mikeyphw/studyflow/core/database/StudyPlanGraphPortabilityStore.kt",
        "requireReference(\"coding_exercises\", row, \"snippetId\", \"code_snippets\")",
        "\"programming_course_profiles\",",
        "\"termux_runtime_preferences\",",
    )

    schema_path = ROOT / "core/database/schemas/com.mikeyphw.studyflow.core.database.StudyFlowDatabase/6.json"
    require(schema_path.is_file(), "schema 6 must remain checked in for migration history")
    schema = json.loads(schema_path.read_text(encoding="utf-8"))
    tables = {entity["tableName"] for entity in schema["database"]["entities"]}
    for table in (
        "programming_course_profiles",
        "code_snippets",
        "coding_exercises",
        "mini_projects",
        "termux_runtime_preferences",
    ):
        require(table in tables, f"schema 6 missing {table}")

    print("PROGRAMMING_COURSE_MODEL: PASS")


if __name__ == "__main__":
    main()
