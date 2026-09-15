#!/usr/bin/env python3
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
checks: list[str] = []
errors: list[str] = []

def read(rel: str) -> str:
    p = ROOT / rel
    if not p.is_file():
        errors.append(f"missing file: {rel}")
        return ""
    return p.read_text(encoding="utf-8")

def require(rel: str, needle: str, label: str) -> None:
    text = read(rel)
    if needle not in text:
        errors.append(f"{rel}: missing {label}: {needle!r}")
    else:
        checks.append(label)

def reject(rel: str, needle: str, label: str) -> None:
    text = read(rel)
    if needle in text:
        errors.append(f"{rel}: forbidden {label}: {needle!r}")
    else:
        checks.append(label)

language = "core/model/src/main/kotlin/com/mikeyphw/studyflow/core/model/LanguageReviewPractice.kt"
require(language, "spotTheMistakeReviewOutcome", "S11-15 dedicated spot-the-mistake grader")
require(language, "do not repeat the mistake", "S11-15 prompt tells user not to repeat mistake")
require(language, "normalizedAnswer == promptText", "S11-15 rejects direct prompt echo")
require(language, "promptText in normalizedAnswer", "S11-15 rejects answer containing mistake")
require(language, "literal translation", "S11-15 detects literal-only answer")
require(language, "hasMistakeReason", "S11-15 requires an explanation marker")
require(language, "referencesAcceptedMeaning", "S11-15 accepts meaning-based explanation")
require(language, "looksLikeLanguageGibberish", "S11-14 gibberish detector")
require(language, "tokens.distinct().size == 1", "S11-14 repeated-token rejection")
require(language, "asdf", "S11-14 placeholder nonsense rejection")
require(language, "vowelCount == 0", "S11-14 vowel-less nonsense rejection")
require(language, "LanguageReviewOutcome.INCORRECT", "S11-14 incorrect outcome path")
require(language, "targetUsage && wordCount >= 4", "S11-14 valid sentence outranks target echo")
require(language, "deterministicLanguageReviewChoiceOrder", "S11-16 deterministic choice ordering helper")
require(language, "stableLanguageChoiceHash", "S11-16 stable hash for choice ordering")
require(language, "correctIndex", "S11-16 correct answer position computed")
require(language, "orderedDistractors", "S11-16 distractor ordering is deterministic")
require(language, "multipleChoiceReviewOutcome", "S11-16 exact multiple-choice outcome")
require(language, "normalizeLanguageAnswer(expected.label) == normalizedAnswer", "S11-16 grades expected choice label")
reject(language, "return (listOf(LanguageReviewChoice(idiomCard.naturalExample, expected = true)) +", "S11-16 old correct-first implementation")

language_test = "core/model/src/test/kotlin/com/mikeyphw/studyflow/core/model/LanguageReviewPracticeTest.kt"
require(language_test, "free-form sentence scoring rejects gibberish below valid target usage", "S11-14 unit test")
require(language_test, "spot mistake rejects repeated mistake and literal-only answers", "S11-15 unit test")
require(language_test, "natural usage choices use deterministic non-fixed answer positions", "S11-16 unit test")
require(language_test, "assertTrue(answerIndexes.any { it > 0 })", "S11-16 non-fixed index assertion")

persistence = "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopContentModePersistence.kt"
require(persistence, "private const val ContentModeSchemaVersion = 2", "S11-19 Desktop schema v2")
require(persistence, "val codeSnippets: List<CodeSnippet>", "S11-19 snapshot persists code snippets")
require(persistence, "val miniProjects: List<MiniProject>", "S11-19 snapshot persists mini projects")
require(persistence, "val termuxRuntimePreferences: List<TermuxRuntimePreference>", "S11-19 snapshot persists runtime preferences")
require(persistence, "val idiomCards: List<IdiomCard>", "S11-19 snapshot persists idiom cards")
require(persistence, "val vocabularyCards: List<VocabularyCard>", "S11-19 snapshot persists vocabulary cards")
require(persistence, "writeList(props, \"snippet\"", "S11-19 snippets encoded")
require(persistence, "writeList(props, \"project\"", "S11-19 mini-projects encoded")
require(persistence, "writeList(props, \"runtime\"", "S11-19 runtime preferences encoded")
require(persistence, "writeList(props, \"idiom\"", "S11-19 idioms encoded")
require(persistence, "writeList(props, \"vocab\"", "S11-19 vocabulary encoded")
require(persistence, "decodeCodeSnippet", "S11-19 snippets decoded")
require(persistence, "decodeMiniProject", "S11-19 mini-projects decoded")
require(persistence, "decodeRuntimePreference", "S11-19 runtime preferences decoded")
require(persistence, "decodeVocabulary", "S11-19 vocabulary decoded")
require(persistence, "readList(props, \"snippet\"", "S11-19 snippets read from persistence")
require(persistence, "readList(props, \"runtime\"", "S11-19 runtime read from persistence")
require(persistence, "props.getProperty(\"$prefix.count\")?.toInt() ?: 0", "S11-19 v1 back-compat for missing v2 lists")

controller = "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopContentModeController.kt"
require(controller, "val expectedOutputMatched: Boolean = true", "S11-20 execution result carries expected-output match")
require(controller, "codeSnippets = planId?.let(repository::codeSnippets).orEmpty()", "S11-19 state exposes snippets")
require(controller, "miniProjects = planId?.let(repository::miniProjects).orEmpty()", "S11-19 state exposes mini-projects")
require(controller, "termuxRuntimePreference = planId?.let(repository::termuxRuntimePreference)", "S11-19 state exposes runtime preference")
require(controller, "vocabularyCards = planId?.let(repository::vocabularyCards).orEmpty()", "S11-19 state exposes vocabulary cards")
require(controller, "CodeSnippet(", "S11-19 Desktop sample creates snippet")
require(controller, "MiniProject(", "S11-19 Desktop sample creates mini-project")
require(controller, "TermuxRuntimePreference(", "S11-19 Desktop sample creates runtime preference")
require(controller, "VocabularyCard(", "S11-19 Desktop sample creates vocabulary card")
require(controller, "repository.upsertProgramming(profile, exercise, snippet, project, runtimePreference)", "S11-19 graph saved in one programming upsert")
require(controller, "tokenizeTermuxCommandTemplate", "S11-20 Desktop uses shared command tokenizer")
require(controller, "exercise.testCommandTemplate?.takeIf", "S11-20 Desktop prefers exercise test template")
require(controller, "resolveTemplateToken", "S11-20 Desktop resolves command placeholders")
require(controller, ".replace(\"{mainFile}\"", "S11-20 resolves mainFile placeholder")
require(controller, ".replace(\"{expectedOutput}\"", "S11-20 resolves expectedOutput placeholder")
require(controller, "exercise.files.isNotEmpty()", "S11-20 writes exercise file bundle")
require(controller, "expectedOutputMatches", "S11-20 compares stdout to expected output")
require(controller, "!result.expectedOutputMatched -> CodingExerciseStatus.FAILED_TESTS", "S11-20 mismatch fails exercise")
require(controller, "expectedOutputMatched=$expectedOutputMatched", "S11-20 persists expected-output evidence")
require(controller, "expectedOutputMatched = false", "S11-20 unsupported runs cannot masquerade as matched")

desktop_test = "desktopApp/src/test/kotlin/com/mikeyphw/studyflow/desktop/DesktopContentModePersistenceExecutionTest.kt"
require(desktop_test, "assertEquals(1, programmingState.codeSnippets.size)", "S11-19 Desktop test asserts snippet persistence")
require(desktop_test, "assertEquals(1, programmingState.miniProjects.size)", "S11-19 Desktop test asserts mini-project persistence")
require(desktop_test, "assertTrue(programmingState.termuxRuntimePreference != null)", "S11-19 Desktop test asserts runtime persistence")
require(desktop_test, "assertEquals(1, languageState.vocabularyCards.size)", "S11-19 Desktop test asserts vocabulary persistence")
require(desktop_test, "desktopProgrammingExecutionUsesExerciseTemplatesAndExpectedOutput", "S11-20 Desktop execution test")
require(desktop_test, "assertFalse(run.expectedOutputMatched)", "S11-20 Desktop mismatch assertion")
require(desktop_test, "expectedOutputMatched=false", "S11-20 Desktop result persistence assertion")

docs = "docs/remediation/OV28_LANGUAGE_REVIEW_DESKTOP_SPECIALIZED_PARITY.md"
require(docs, "S11-14", "OV28 docs mention S11-14")
require(docs, "S11-20", "OV28 docs mention S11-20")

build = "build.gradle.kts"
require(build, "verifyOv28LanguageReviewDesktopSpecializedParity", "OV28 Gradle verification task")
require(build, "scripts/validate_ov28_language_review_desktop_specialized_parity.sh", "OV28 Gradle task entrypoint")

if errors:
    print("OV28 verification FAILED", file=sys.stderr)
    for error in errors:
        print(f" - {error}", file=sys.stderr)
    sys.exit(1)
print(f"OV28 language review/Desktop specialized parity contract: PASS ({len(checks)} checks)")
