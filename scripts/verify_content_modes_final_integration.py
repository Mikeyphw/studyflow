#!/usr/bin/env python3
from __future__ import annotations

import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

CMOV_VALIDATORS = [
    "verify_content_mode_foundation.py",
    "verify_programming_course_model.py",
    "verify_code_blocks_syntax.py",
    "verify_termux_runtime_foundation.py",
    "verify_programming_course_ui.py",
    "verify_coding_exercise_termux_execution.py",
    "verify_language_deck_idiom_model.py",
    "verify_language_review_modes.py",
    "verify_import_templates.py",
    "verify_mode_aware_analytics.py",
]

PRODUCTION_FLOW_FILES = [
    "app/src/main/java/com/mikeyphw/studyflow/StudyFlowApplication.kt",
    "app/src/main/java/com/mikeyphw/studyflow/StudyFlowV2Navigation.kt",
    "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/TodayWorkspaceScreen.kt",
    "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/ProgrammingCourseWorkspacePanel.kt",
    "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/LanguageDeckWorkspacePanel.kt",
    "feature/analytics/src/main/java/com/mikeyphw/studyflow/feature/analytics/AnalyticsUiModels.kt",
    "core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/RoomProgrammingCourseRepository.kt",
    "core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/RoomLanguageLearningRepository.kt",
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ProgrammingCourseRepository.kt",
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/LanguageLearningRepository.kt",
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/SpecializedContentImportWorkflow.kt",
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ModeAwareAnalytics.kt",
    "core/model/src/main/kotlin/com/mikeyphw/studyflow/core/model/LanguageLearning.kt",
    "core/model/src/main/kotlin/com/mikeyphw/studyflow/core/model/LanguageReviewPractice.kt",
    "core/model/src/main/kotlin/com/mikeyphw/studyflow/core/model/TermuxProgrammingRuntime.kt",
    "core/model/src/main/kotlin/com/mikeyphw/studyflow/core/model/CodingExerciseTermuxExecution.kt",
]

errors: list[str] = []


def require(condition: bool, message: str) -> None:
    if not condition:
        errors.append(message)


def text(rel: str) -> str:
    path = ROOT / rel
    require(path.is_file(), f"missing {rel}")
    return path.read_text(encoding="utf-8") if path.is_file() else ""

for validator in CMOV_VALIDATORS:
    path = ROOT / "scripts" / validator
    require(path.is_file(), f"missing CMOV validator {validator}")
    if path.is_file():
        result = subprocess.run([sys.executable, str(path)], cwd=ROOT, text=True, capture_output=True)
        if result.returncode != 0:
            errors.append(f"{validator} failed: {(result.stderr or result.stdout).strip()}")

combined_flow = "\n".join(text(rel) for rel in PRODUCTION_FLOW_FILES)
for marker in [
    "RoomProgrammingCourseRepository",
    "RoomLanguageLearningRepository",
    "safeCommandTemplateFor",
    "ProgrammingCourseSnapshot",
    "LanguageLearningSnapshot",
    "ProgrammingCourseWorkspacePanel",
    "LanguageDeckWorkspacePanel",
    "applyProgrammingCourseImportPreview",
    "applyLanguageDeckImportPreview",
    "SpecializedContentImportWorkflow",
    "ModeAwareAnalyticsCalculator",
    "LanguageReviewEngine",
    "tokenizeTermuxCommandTemplate",
]:
    require(marker in combined_flow, f"production content-mode flow missing {marker}")

build = text("build.gradle.kts")
for task in [
    "verifyContentModeFoundation",
    "verifyProgrammingCourseModel",
    "verifyCodeBlocksSyntax",
    "verifyTermuxRuntimeFoundation",
    "verifyProgrammingCourseUi",
    "verifyCodingExerciseTermuxExecution",
    "verifyLanguageDeckIdiomModel",
    "verifyLanguageReviewModes",
    "verifyImportTemplates",
    "verifyModeAwareAnalytics",
]:
    require(task in build, f"Gradle graph omits {task}")

if errors:
    print("CONTENT_MODES_FINAL_INTEGRATION_SEAL_R4: FAIL")
    for error in errors:
        print("-", error)
    raise SystemExit(1)
print("CONTENT_MODES_FINAL_INTEGRATION_SEAL_R4: PASS (10 validators + production flows)")
