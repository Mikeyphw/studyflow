#!/usr/bin/env python3
from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FAILURES: list[str] = []


def require(condition: bool, message: str) -> None:
    if not condition:
        FAILURES.append(message)


def text(path: str) -> str:
    candidate = ROOT / path
    require(candidate.is_file(), f"missing required file: {path}")
    return candidate.read_text(encoding="utf-8") if candidate.is_file() else ""


weakness = text("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/WeaknessDrillGenerator.kt")
importer = text("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/QuestionImportPipeline.kt")
practice = text("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ManageStudyPractice.kt")
workspace = text("feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/PracticeWorkspaceScreen.kt")
progress = text("feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/ProgressWorkspaceScreen.kt")
build = text("build.gradle.kts")
devtool = text(".devtool.toml")
versions = text("gradle/libs.versions.toml")

for token in (
    "object WeaknessDrillGenerator",
    "data class WeaknessDrillPlan",
    "data class WeaknessDrillRequest",
    "WeaknessDrillEmptyReason.NO_WEAK_SIGNALS",
    "DirectErrorWeight",
    "OverdueReviewWeight",
    "TopicMatchWeight",
    "SubjectMatchWeight",
    "matchedWeakSignal",
    "if (!matchedWeakSignal) return null",
):
    require(token in weakness, f"weakness drill contract missing: {token}")

for token in (
    "object QuestionImportPipeline",
    "enum class QuestionImportFormat",
    "QuestionImportFormat.CSV",
    "QuestionImportFormat.TSV",
    "QuestionImportFormat.MARKDOWN",
    "duplicateOfExisting",
    "duplicateWithinImport",
    "val importable: Boolean = valid",
    "QuestionType.SHORT_ANSWER",
    "Regex(\"\\\\s+\")",
    "Regex(\"\\\\n\\\\s*\\\\n\")",
):
    require(token in importer, f"question import contract missing: {token}")

for token in (
    "previewQuestionImport",
    "importQuestionsFromText",
    "buildWeaknessDrill",
    "createWeaknessDrillTest",
    "QuestionImportDraft::importable",
    "TestMode.IMMEDIATE_FEEDBACK",
):
    require(token in practice, f"ManageStudyPractice missing Phase 50/51 behavior: {token}")

for token in (
    "QuestionImportDialog",
    "question_importer_title",
    "importQuestionsFromText",
    "WeaknessDrillPlan",
    "createWeaknessDrillTest",
    "weakness_drill_title",
    "CancellationException",
):
    require(token in workspace, f"Practice workspace missing Phase 50/51 surface: {token}")


for token in (
    "val flashcardId = item.flashcardId",
    "val questionId = item.questionId",
    "val errorEntryId = item.errorEntryId",
    "val topicId = item.topicId",
    "flashcardId != null -> stringResource(R.string.review_flashcard, flashcardId.value.take(8))",
    "questionId != null -> stringResource(R.string.review_question, questionId.value.take(8))",
    "errorEntryId != null -> stringResource(R.string.review_error, errorEntryId.value.take(8))",
    "topicId != null -> stringResource(R.string.review_topic, topicId.value.take(8))",
):
    require(token in workspace, f"Practice workspace missing stable ReviewItem smart-cast snapshot: {token}")

for forbidden in (
    "item.flashcardId != null -> stringResource(R.string.review_flashcard, item.flashcardId.value.take(8))",
    "item.questionId != null -> stringResource(R.string.review_question, item.questionId.value.take(8))",
    "item.errorEntryId != null -> stringResource(R.string.review_error, item.errorEntryId.value.take(8))",
    "item.topicId != null -> stringResource(R.string.review_topic, item.topicId.value.take(8))",
):
    require(forbidden not in workspace, f"Practice workspace retains cross-module smart-cast defect: {forbidden}")

require("Icons.AutoMirrored.Outlined.TrendingUp" in progress, "Progress does not use AutoMirrored TrendingUp")
require("Icons.Outlined.TrendingUp" not in progress, "Progress still uses deprecated TrendingUp")
require("import androidx.compose.material.icons.automirrored.outlined.TrendingUp" in progress,
        "Progress missing AutoMirrored TrendingUp import")

require(r'Regex("\\s+")' in importer, "Question importer must escape whitespace regexes")
require(r'Regex("\\n\\s*\\n")' in importer, "Markdown splitter must escape whitespace regexes")
require(r'Regex("\s+")' not in importer, "Question importer has illegal Kotlin regex escape")
require(r'Regex("\n\s*\n")' not in importer, "Markdown splitter has illegal Kotlin regex escape")
require("score += driver.recurrenceCount" not in weakness, "Weakness drill must not score unrelated questions")
require("QuestionType.SHORT_ANSWER" in importer, "Anki/TSV import must default no-option rows to short answer")
require("val rejectedCount: Int = drafts.count { !it.importable }" in importer,
        "Question import rejected count must count rejected drafts, not individual issues")

for path, tokens in {
    "core/domain/src/test/kotlin/com/mikeyphw/studyflow/core/domain/WeaknessDrillGeneratorTest.kt": (
        "drillPrioritizesRepeatedErrorsOverGenericQuestions",
        "dueQuestionReviewsCanDriveADrillWithoutErrorNotebook",
        "emptySignalsAreReportedInsteadOfInventingADrill",
        "unrelatedQuestionsAreNotPulledInByRecurrenceWeight",
    ),
    "core/domain/src/test/kotlin/com/mikeyphw/studyflow/core/domain/QuestionImportPipelineTest.kt": (
        "csvImportDetectsHeaderRowsAndExistingDuplicates",
        "markdownImportBuildsValidatedDrafts",
        "ankiStyleTsvIsAcceptedAndRepeatedRowsAreSkipped",
        "csvImportHandlesQuotedCommasAndEscapedQuotes",
        "invalidRowsAreReported",
    ),
}.items():
    source = text(path)
    for token in tokens:
        require(token in source, f"missing Phase 50/51 test token: {token}")

for doc_path in (
    "PHASE_50_51_WEAKNESS_DRILLS_IMPORT.md",
    "docs/architecture/ADR-0031-weakness-drills-and-question-import.md",
    "docs/quality/PHASE_50_51_ACCEPTANCE.md",
):
    require((ROOT / doc_path).is_file(), f"missing Phase 50/51 documentation: {doc_path}")

for required_task in (
    ":core:domain:test",
    ":feature:workspace:testDebugUnitTest",
    ":app:assembleDebug",
):
    require(required_task in build or required_task in devtool, f"validation task missing: {required_task}")

require("scripts/verify_phase_50_51.py" in build, "Phase 50/51 verifier is not wired")

expected_versions = {
    "agp": "9.3.1",
    "kotlin": "2.4.10",
    "composeBom": "2026.06.01",
    "navigation": "2.9.8",
    "room": "2.8.0",
    "ksp": "2.3.11",
}
for name, expected in expected_versions.items():
    require(f'{name} = "{expected}"' in versions, f"toolchain drifted: {name} must remain {expected}")

string_sets = []
for path in (
    ROOT / "feature/workspace/src/main/res/values/strings.xml",
    ROOT / "feature/workspace/src/main/res/values-pt-rBR/strings.xml",
):
    xml_root = ET.parse(path).getroot()
    names = [element.attrib["name"] for element in xml_root if element.tag == "string"]
    require(len(names) == len(set(names)), f"duplicate string resources in {path.relative_to(ROOT)}")
    string_sets.append(set(names))

if len(string_sets) == 2:
    require(string_sets[0] == string_sets[1], "workspace resource key parity broke")

if string_sets:
    workspace_strings = string_sets[0]
    for source_name, source in (("Practice", workspace), ("Progress", progress)):
        for match in sorted(set(re.findall(r"R\.string\.([A-Za-z0-9_]+)", source))):
            require(match in workspace_strings, f"{source_name} workspace references missing string: {match}")

for source_path in [
    ROOT / "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/WeaknessDrillGenerator.kt",
    ROOT / "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/QuestionImportPipeline.kt",
    ROOT / "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ManageStudyPractice.kt",
    ROOT / "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/PracticeWorkspaceScreen.kt",
    ROOT / "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/ProgressWorkspaceScreen.kt",
]:
    source = source_path.read_text(encoding="utf-8")
    require("\x00" not in source, f"NUL byte in {source_path.relative_to(ROOT)}")
    for line_number, line in enumerate(source.splitlines(), 1):
        require("\t" not in line, f"{source_path.relative_to(ROOT)}:{line_number} contains a tab character")
        require(len(line) <= 120, f"{source_path.relative_to(ROOT)}:{line_number} exceeds 120 characters")

if FAILURES:
    print("PHASE_50_51_VALIDATION_FAIL")
    for failure in FAILURES:
        print(f"- {failure}")
    sys.exit(1)

print("PHASE_50_51_VALIDATION_OK")
print("Weakness drills, question import pipeline, workspace UI, resources, and warning fix verified")
