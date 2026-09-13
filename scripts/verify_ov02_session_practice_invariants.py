#!/usr/bin/env python3
"""Source-contract verifier for Overlay 2 of 30 (OV02)."""
from __future__ import annotations

from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
errors: list[str] = []


def text(path: str) -> str:
    p = ROOT / path
    if not p.is_file():
        errors.append(f"missing required file: {path}")
        return ""
    return p.read_text(encoding="utf-8")


def require(path: str, *needles: str) -> None:
    body = text(path)
    for needle in needles:
        if needle not in body:
            errors.append(f"{path}: missing contract token {needle!r}")


def forbid(path: str, *needles: str) -> None:
    body = text(path)
    for needle in needles:
        if needle in body:
            errors.append(f"{path}: forbidden stale contract token {needle!r}")

# S1-01 — canonical active seconds; pause telemetry is never subtracted twice.
require(
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudySessionTimer.kt",
    "fun persistedActiveSeconds(session: StudySession)",
    "grossDurationSeconds.coerceAtLeast(0L)",
)
require(
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/GoalProgressCalculator.kt",
    "sessions.sumOf(StudySessionTimer::persistedActiveSeconds)",
)
require(
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/PublicExamDashboard.kt",
    "scoped.sumOf(StudySessionTimer::persistedActiveSeconds)",
)
forbid(
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/GoalProgressCalculator.kt",
    "grossDurationSeconds - session.pauseDurationSeconds",
)
require(
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ManageStudySession.kt",
    "val activeMillis: Long",
    "val pausedMillis: Long",
    "monotonicClockMillis",
)

# S1-02 / S1-12 — one canonical duration/material contract, including relevant Android UI.
require(
    "core/model/src/main/kotlin/com/mikeyphw/studyflow/core/model/StudyDomainInvariants.kt",
    "MAX_SESSION_MINUTES = 720",
    "MAX_PREFERRED_SESSION_MINUTES = 240",
    "MAX_STRATEGY_MINIMUM_MINUTES = 480",
    "fun requireStrategyDurations",
    "fun requireMaterialTotalUnits",
)
require(
    "core/model/src/main/kotlin/com/mikeyphw/studyflow/core/model/StudyPlanning.kt",
    "StudyDomainInvariants.requireSessionMinutes(plannedDurationMinutes",
    "StudyDomainInvariants.requireOptionalSessionMinutes(targetDurationMinutes",
    "StudyDomainInvariants.requireStrategyDurations",
)
require(
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ManageStudySchedule.kt",
    "StudyDomainInvariants.requireStrategyDurations",
    "StudyDomainInvariants.requireSessionMinutes(rule.durationMinutes",
    "StudyDomainInvariants.requireSessionMinutes(activity.plannedDurationMinutes",
)
require(
    "core/model/src/main/kotlin/com/mikeyphw/studyflow/core/model/StudyResources.kt",
    "StudyDomainInvariants.requireMaterialTotalUnits(totalUnits)",
)
require(
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ManageStudyLibrary.kt",
    "StudyDomainInvariants.requireMaterialTotalUnits(totalUnits)",
)
require(
    "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/WorkspaceUtilityScreens.kt",
    "StudyDomainInvariants.sessionMinutes",
)

# S1-04 / S1-11 — immutable attempt snapshot + strict identity.
practice = "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/PracticeExecutionEngine.kt"
require(
    practice,
    'QuestionSnapshotPrefix = "questionSnapshotV1="',
    "fun configurationForQuestions(",
    "fun freezeConfiguration(",
    "Practice attempt must contain exactly one question identity record.",
    "Practice attempt question identity cannot be empty.",
    "Started practice attempts must contain immutable question snapshots.",
    "val frozenQuestion = requireNotNull(state.questions.firstOrNull",
    "correctAnswer = frozenQuestion.correctAnswer",
)
forbid(practice, "questions.take(questionCount)")
require(
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ManageStudyPractice.kt",
    "PracticeAttemptCodec.configurationForQuestions(questions)",
    "PracticeAttemptCodec.freezeConfiguration(",
    "All Mock Exam questions must belong to the Study Plan.",
)
require(
    "core/portability/src/main/kotlin/com/mikeyphw/studyflow/core/portability/PracticeConfigurationReferences.kt",
    'QuestionSnapshotPrefix = "questionSnapshotV1="',
    "remapSnapshotLine",
    "decodeSnapshotQuestionId",
)

# S1-05 — explicit Mock Exam Studio owner.
planner = "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/MockExamStudioPlanner.kt"
require(
    planner,
    "planId: StudyPlanId",
    "questions.all { it.studyPlanId == planId }",
    "question bank must belong entirely to the requested Study Plan",
)
forbid(planner, "planQuestionOwner")

# Regression tests must exist for each high-risk behavior.
require(
    "core/domain/src/test/kotlin/com/mikeyphw/studyflow/core/domain/ManageStudySessionFocusTest.kt",
    "validLongPlannedActivityStartsWithItsFullTargetDuration",
    "pauseResumePersistsActiveAndPausedTimeWithoutDoubleCounting",
)
require(
    "core/domain/src/test/kotlin/com/mikeyphw/studyflow/core/domain/GoalProgressCalculatorTest.kt",
    "completedSessionDoesNotSubtractRecordedPauseFromPersistedActiveTimeAgain",
)
require(
    "core/domain/src/test/kotlin/com/mikeyphw/studyflow/core/domain/PracticeExecutionEngineTest.kt",
    "frozenAttemptGradesAgainstCreationSnapshotAfterQuestionMutation",
    "frozenAttemptRemainsExecutableWhenBankQuestionIsDeleted",
    "malformedLegacyAttemptCannotBorrowCurrentQuestionIdentity",
    "startedLegacyAttemptWithoutSnapshotFailsClosed",
)
require(
    "core/domain/src/test/kotlin/com/mikeyphw/studyflow/core/domain/MockExamStudioPlannerTest.kt",
    "studioRejectsMixedPlanQuestionBanksInsteadOfInferringFirstOwner",
)
require(
    "core/portability/src/test/kotlin/com/mikeyphw/studyflow/core/portability/PracticeConfigurationReferencesTest.kt",
    "questionSnapshotV1=",
    "malformedFrozenSnapshotFailsClosed",
)

# Carry-forward OV01 wrapper regression: Gradle wrapper must launch GradleWrapperMain, never `java -jar`.
require("gradlew", "org.gradle.wrapper.GradleWrapperMain", '-classpath "$CLASSPATH"')
forbid("gradlew", '-jar "$APP_HOME/gradle/wrapper/gradle-wrapper.jar"')
require("gradlew.bat", "org.gradle.wrapper.GradleWrapperMain", "-classpath \"%CLASSPATH%\"")

ledger = text("docs/roadmap/FINAL_AUDIT_REMEDIATION_CLOSURE_LEDGER.md")
for finding in ("S1-01", "S1-02", "S1-04", "S1-05", "S1-11", "S1-12"):
    if ledger.count(f"| {finding} |") != 1:
        errors.append(f"closure ledger must own {finding} exactly once")

if errors:
    print("OV02_SESSION_PRACTICE_CANONICAL_INVARIANTS: FAIL")
    for error in errors:
        print(f"- {error}")
    raise SystemExit(1)

print("OV02_SESSION_PRACTICE_CANONICAL_INVARIANTS: PASS")
print("findings: S1-01 S1-02 S1-04 S1-05 S1-11 S1-12")
