#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]


def text(rel: str) -> str:
    path = ROOT / rel
    if not path.is_file():
        raise AssertionError(f"missing required file: {rel}")
    return path.read_text(encoding="utf-8")


def require(rel: str, *tokens: str) -> str:
    value = text(rel)
    for token in tokens:
        if token not in value:
            raise AssertionError(f"{rel}: missing contract token {token!r}")
    return value


def main() -> None:
    repository = require(
        "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/PublicExamRepository.kt",
        "class ManagePublicExam",
        "repository.observeLegislation(planId)",
        "repository.linkLegislationEvidence(",
    )
    interface_body = repository.split("class ManagePublicExam", 1)[0]
    if "repository." in interface_body:
        raise AssertionError("PublicExamRepository interface must not contain facade delegation code")

    workspace = require(
        "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/PlanWorkspaceScreen.kt",
        "observeStudyCycle(plan.id)",
        "cycleDistance = cycleDistance",
        "accuracyWindowStart",
        "ImportanceMasteryMatrix",
        "BoxWithConstraints",
        "maxWidth < 480.dp",
        "row.status.workspaceLabelRes()",
    )
    if "cycleDistance = null" in workspace:
        raise AssertionError("priority integration still discards study-cycle position")

    question_filters = require(
        "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/PublicExamQuestionFilters.kt",
        "object PublicExamQuestionOutcomeEvidence",
        "QuestionAttemptOutcome.UNSEEN",
        "QuestionAttemptOutcome.CORRECT",
        "QuestionAttemptOutcome.WRONG",
    )
    if "PracticeAttemptCodec.decode" not in question_filters:
        raise AssertionError("question outcome filtering is not backed by attempt evidence")

    practice = require(
        "feature/practice/src/main/java/com/mikeyphw/studyflow/feature/practice/PracticeScreen.kt",
        "outcomeFilter",
        "PublicExamQuestionOutcomeEvidence.latest",
        "WrongAnswerClassificationDialog",
        "blueprintScoringMode",
        "blueprintWrongPenalty",
        "blueprintBlankPenalty",
        "blueprintSectionCounts",
        "blueprintSectionWeights",
        "blueprintStrictMode",
        "blueprintAllowMultipleChoice",
        "blueprintAllowTrueFalse",
        "blueprintAllowShortAnswer",
    )
    if practice.count("QuestionAttemptOutcome.CORRECT") < 1 or practice.count("QuestionAttemptOutcome.UNSEEN") < 1:
        raise AssertionError("Practice UI does not expose all promised outcome filters")

    execution = require(
        "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/PracticeExecutionEngine.kt",
        "val errorCategory: ErrorCategory?",
        "ErrorCategory.OTHER",
        "parts.size !in 6..7",
    )
    if "errorCategory ?: ErrorCategory.OTHER" not in execution:
        raise AssertionError("unclassified wrong answers must remain truthful rather than fabricate a cause")

    manage_practice = require(
        "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ManageStudyPractice.kt",
        "errorCategory: ErrorCategory? = null",
    )
    if "elapsedSeconds, errorCategory" not in manage_practice:
        raise AssertionError("quick error classification is not propagated to the execution engine")

    repository_practice = require(
        "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudyPracticeRepository.kt",
        "category: ErrorCategory = ErrorCategory.OTHER",
    )
    if "CONCEPT_CONFUSION" in re.search(
        r"data class PracticeWrongAnswerCapture\([\s\S]*?\n\)", repository_practice
    ).group(0):
        raise AssertionError("wrong-answer capture still fabricates CONCEPT_CONFUSION")

    legislation = require(
        "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/PublicExamLegislationPanel.kt",
        "WEAK",
        "LegislationEvidenceLinkDialog",
        "manager.linkLegislationEvidence",
        "questionId =",
        "errorEntryId =",
        "reviewItemId =",
    )
    if "LegislationUiFilter.WEAK" not in legislation:
        raise AssertionError("Lei Seca weak filter is not implemented")

    onboarding = require(
        "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/PublicExamOnboarding.kt",
        "enum class PublicExamExamDateKind",
        "enum class PublicExamEditalImportChoice",
        "ELIMINATE_OVERDUE_REVIEWS",
        "EDITAL_COVERAGE_BY_DATE",
        "accuracySubjectId: SubjectId?",
        "endsOnIso = targetDate",
    )
    for rel in (
        "samples/import/public_exam_edital.txt",
        "samples/import/public_exam_edital.csv",
        "samples/import/public_exam_edital.tsv",
        "samples/import/public_exam_questions.csv",
        "samples/import/public_exam_questions.tsv",
        "samples/import/public_exam_legislation.txt",
        "samples/import/public_exam_legislation.md",
        "samples/import/public_exam_legislation.csv",
    ):
        if not (ROOT / rel).is_file():
            raise AssertionError(f"missing promised local import template: {rel}")

    journal = require(
        "app/src/main/java/com/mikeyphw/studyflow/SamplePlanProvisioningJournal.kt",
        "pending_edital_import_choice",
        "editalImportChoice: PublicExamEditalImportChoice",
    )
    if "PublicExamEditalImportChoice.LATER" not in journal:
        raise AssertionError("old provisioning journals must default safely to import later")

    app = require(
        "app/src/main/java/com/mikeyphw/studyflow/StudyFlowApp.kt",
        "PublicExamEditalImportChoice.NOW",
        "ContentManagerRoute",
    )
    if "localizedPublicExamGoalNames" not in app:
        raise AssertionError("new public-exam goal presets are not localized at provisioning time")

    blueprint = require(
        "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ManageStudyPractice.kt",
        "blueprint.strictExamMode",
        "TestMode.EXAM",
        "TestMode.IMMEDIATE_FEEDBACK",
    )
    if "ExamBlueprint" not in blueprint:
        raise AssertionError("mock creation does not consume the frozen blueprint")

    db = require(
        "core/database/src/main/java/com/mikeyphw/studyflow/core/database/StudyFlowDatabase.kt",
        "SCHEMA_VERSION = 13",
    )
    if "SCHEMA_VERSION = 14" in db:
        raise AssertionError("roadmap reconciliation must not introduce a schema bump")

    en = ROOT / "feature/plans/src/main/res/values/strings.xml"
    pt = ROOT / "feature/plans/src/main/res/values-pt-rBR/strings.xml"
    en_names = {node.attrib["name"] for node in ET.parse(en).getroot().findall("string")}
    pt_names = {node.attrib["name"] for node in ET.parse(pt).getroot().findall("string")}
    required_strings = {
        "onboarding_goal_title_overdue_reviews",
        "onboarding_goal_title_edital_coverage",
        "onboarding_exam_date_confirmed",
        "onboarding_exam_date_expected",
        "onboarding_edital_import_now",
        "onboarding_edital_import_later",
    }
    missing = required_strings - en_names | required_strings - pt_names
    if missing:
        raise AssertionError("bilingual onboarding resource gap: " + ", ".join(sorted(missing)))

    print(
        "CPOV_ROADMAP_RECONCILIATION: PASSED "
        "(priority evidence, question outcomes, blueprint UI, error classification, legislation links, "
        "dashboard matrix, onboarding/templates/goals, schema 13)"
    )


if __name__ == "__main__":
    main()
