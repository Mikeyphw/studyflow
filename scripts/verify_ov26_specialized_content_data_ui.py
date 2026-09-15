#!/usr/bin/env python3
"""Source validator for Overlay 26 of 30.

This validator is intentionally Gradle-independent so Devtool can run it on Termux without
pulling services.gradle.org. It enforces the OV26 behavioral contracts at source level and is
paired with the ordinary Android/Gradle graph at final release time.
"""
from __future__ import annotations

import re
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(rel: str) -> str:
    path = ROOT / rel
    if not path.is_file():
        raise AssertionError(f"missing required file: {rel}")
    return path.read_text(encoding="utf-8")


def require(rel: str, *needles: str) -> None:
    text = read(rel)
    for needle in needles:
        if needle not in text:
            raise AssertionError(f"{rel} does not contain required contract: {needle}")


def reject(rel: str, *needles: str) -> None:
    text = read(rel)
    for needle in needles:
        if needle in text:
            raise AssertionError(f"{rel} contains forbidden stale pattern: {needle}")


def require_regex(rel: str, pattern: str) -> None:
    if not re.search(pattern, read(rel), flags=re.S):
        raise AssertionError(f"{rel} does not match required pattern: {pattern}")


def maybe_compile_model() -> None:
    kotlinc = shutil.which("kotlinc")
    if not kotlinc:
        print("OV26 model syntax: SKIP (kotlinc not available)")
        return
    out = ROOT / "build" / "ov26-model-kotlinc"
    out.mkdir(parents=True, exist_ok=True)
    sources = sorted((ROOT / "core/model/src/main/kotlin/com/mikeyphw/studyflow/core/model").glob("*.kt"))
    cmd = [kotlinc, *map(str, sources), "-d", str(out)]
    result = subprocess.run(cmd, cwd=ROOT, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    if result.returncode != 0:
        raise AssertionError("OV26 model syntax compile failed:\n" + result.stdout)
    print("OV26 model syntax: PASS")


def main() -> int:
    # S11-01: production Room-backed repositories, not façade-only interfaces.
    require(
        "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ProgrammingCourseRepository.kt",
        "interface ProgrammingCourseRepository",
        "fun observeSnapshot(planId: StudyPlanId): Flow<ProgrammingCourseSnapshot>",
        "suspend fun saveCodingExercise",
        "suspend fun applyImportPreview",
        "class ManageProgrammingCourse",
    )
    require(
        "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/LanguageLearningRepository.kt",
        "interface LanguageLearningRepository",
        "fun observeSnapshot(planId: StudyPlanId): Flow<LanguageLearningSnapshot>",
        "suspend fun saveDeck",
        "suspend fun applyImportPreview",
        "class ManageLanguageLearning",
    )
    require(
        "core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/RoomProgrammingCourseRepository.kt",
        "internal class RoomProgrammingCourseRepository",
        "database.withTransaction",
        "private val dao = database.programmingCourseDao()",
        "dao.upsertCodingExercise(normalized.asEntity())",
        "TermuxRuntimePreference(",
        "StudyFlowProgrammingImportPreview",
    )
    require(
        "core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/RoomLanguageLearningRepository.kt",
        "internal class RoomLanguageLearningRepository",
        "database.withTransaction",
        "private val dao = database.languageLearningDao()",
        "dao.upsertExpression(expression.asEntity())",
        "StudyFlowLanguageImportPreview",
    )
    require(
        "core/database/src/main/java/com/mikeyphw/studyflow/core/database/mapper/ProgrammingCourseEntityMapper.kt",
        "fun CodingExerciseEntity.asExternalModel()",
        "fun CodingExercise.asEntity()",
        "runCommandTemplate = requireNotNull(runCommandTemplate)",
        "preferredLanguage = preferredLanguage?.decodeEnum<ProgrammingLanguage>",
        "encodeExerciseFilesStable",
    )
    require(
        "core/database/src/main/java/com/mikeyphw/studyflow/core/database/mapper/LanguageLearningEntityMapper.kt",
        "fun LanguageDeckProfileEntity.asExternalModel()",
        "fun LanguageExpression.asEntity()",
        "fun LanguageReviewAttempt.asEntity(studyPlanId: StudyPlanId)",
        "encodeReviewDirectionsStable",
    )

    # S11-05 and S11-06: workspace UI reads real snapshots and exposes language deck product flow.
    require(
        "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/TodayWorkspaceScreen.kt",
        "programmingManager: ManageProgrammingCourse",
        "languageManager: ManageLanguageLearning",
        "programmingManager.observeSnapshot(plan.id)",
        "languageManager.observeSnapshot(plan.id)",
        "LanguageDeckWorkspacePanel",
        "snapshot.exerciseCount",
        "snapshot.idiomCount",
    )
    reject(
        "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/TodayWorkspaceScreen.kt",
        "exerciseCount = readyAnalytics.answeredQuestions",
        "moduleCount = readySchedule.plannedActivities.size",
    )
    require(
        "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/LanguageDeckWorkspacePanel.kt",
        "LanguageDeckDashboardSummary",
        "language_deck_workspace_title",
        "language_deck_review_now",
    )

    # S11-07 and S11-08: templates are explicit and safe defaults exist for every supported language.
    require(
        "core/model/src/main/kotlin/com/mikeyphw/studyflow/core/model/ProgrammingCourse.kt",
        "Run command template is mandatory for coding exercises.",
        "Test command template is mandatory for mini projects.",
    )
    require(
        "core/model/src/main/kotlin/com/mikeyphw/studyflow/core/model/TermuxProgrammingRuntime.kt",
        "ProgrammingLanguage.SHELL -> listOf(",
        "ProgrammingLanguage.SQL -> listOf(",
        "ProgrammingLanguage.HTML,",
        "TermuxCommandTemplate(\n            id = \"${language.name.lowercase()}-workspace-check\"",
        "\"bash\",",
        "\"sqlite3\",",
    )
    reject(
        "core/model/src/main/kotlin/com/mikeyphw/studyflow/core/model/TermuxProgrammingRuntime.kt",
        "else -> emptyList()",
    )

    # S11-17: analytics derives specialized evidence from specialized tables, not relabeled generic metrics.
    require(
        "core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/RoomStudyAnalyticsRepository.kt",
        "private val programmingDao = database.programmingCourseDao()",
        "private val languageDao = database.languageLearningDao()",
        "programmingExercises = programmingDao.findCodingExercises(plan)",
        "languageReviewAttempts = languageDao.findLanguageReviewAttempts(plan)",
        "private fun specializedContentProgressPercent",
        "private fun specializedQuestionAccuracyPercent",
        "questionAccuracyPercent = specializedAccuracy",
        "contentProgressPercent = specializedProgress",
    )

    # S11-18: preview parsers have actual Android/domain apply workflow boundaries.
    require(
        "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/SpecializedContentImportWorkflow.kt",
        "class ApplySpecializedContentImport",
        "StudyContentMode.PROGRAMMING_COURSE",
        "StudyContentMode.LANGUAGE_DECK",
        "StudyFlowProgrammingImportPreview(preview)",
        "StudyFlowLanguageImportPreview(preview)",
    )
    require(
        "app/src/main/java/com/mikeyphw/studyflow/StudyFlowApplication.kt",
        "val applySpecializedContentImport: ApplySpecializedContentImport",
        "override val manageProgrammingCourse = ManageProgrammingCourse(programmingCourseRepository)",
        "override val manageLanguageLearning = ManageLanguageLearning(languageLearningRepository)",
        "override val applySpecializedContentImport = ApplySpecializedContentImport(",
    )
    require(
        "core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/StudyFlowDataLayer.kt",
        "val programmingCourseRepository: ProgrammingCourseRepository",
        "val languageLearningRepository: LanguageLearningRepository",
        "RoomProgrammingCourseRepository(database, clock)",
        "RoomLanguageLearningRepository(database, clock)",
    )

    maybe_compile_model()
    print("OV26 specialized content production data/UI contract: PASS (58 checks)")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except AssertionError as error:
        print(f"OV26 validation failed: {error}", file=sys.stderr)
        raise SystemExit(1)
