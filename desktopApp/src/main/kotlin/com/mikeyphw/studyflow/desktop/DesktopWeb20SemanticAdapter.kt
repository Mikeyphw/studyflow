package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.domain.*
import com.mikeyphw.studyflow.core.link.*
import com.mikeyphw.studyflow.core.model.*
import com.mikeyphw.studyflow.core.portability.CanonicalPortableJson
import java.time.ZoneId
import java.util.TimeZone

data class DesktopWeb20ScheduleResult(val count: Int, val firstStartEpochMillis: Long?)
data class DesktopWeb20RebalanceResult(val changed: Int, val status: String?, val scheduledStartEpochMillis: Long?)
data class DesktopWeb20ScoreResult(val scorePercent: Int, val passed: Boolean?)
data class DesktopWeb20SrsResult(
    val intervalDays: Int,
    val repetitionCount: Int,
    val easeFactor: Double,
    val dueAtEpochMillis: Long,
)
data class DesktopWeb20ErrorResult(val priority: String, val suggestion: String)
data class DesktopWeb20ConflictInput(
    val id: String,
    val logicalTimestamp: Long,
    val deviceId: String,
    val originSequence: Long,
)

/** Desktop-facing semantic adapter used by WEB20 and reused by PARITY04. */
object DesktopWeb20SemanticAdapter {
    fun readinessBand(score: Int): String = ExamReadinessEngine.bandFor(score).name.lowercase().replace('_', '-')

    fun generateSchedule(
        startEpochMillis: Long,
        days: Int,
        dayOfWeek: Int,
        startMinuteOfDay: Int,
        durationMinutes: Int,
        dayOff: Boolean,
    ): DesktopWeb20ScheduleResult {
        val planner = StudySchedulePlanner(TimeZone.getTimeZone("UTC")) { "desktop-web20" }
        val plan = StudyPlanId("desktop-plan-web20")
        val rules = buildList {
            add(
                ScheduleRule(
                    ScheduleRuleId("desktop-rule-main"), plan, null, ScheduleRuleType.RECURRING_BLOCK,
                    dayOfWeek, startMinuteOfDay, durationMinutes, null, false, true, 0,
                ),
            )
            if (dayOff) {
                add(
                    ScheduleRule(
                        ScheduleRuleId("desktop-rule-off"), plan, null, ScheduleRuleType.DAY_OFF,
                        dayOfWeek, null, 5, null, false, true, 1,
                    ),
                )
            }
        }
        val result = planner.generateActivities(ScheduleGenerationRequest(plan, startEpochMillis, days, true), rules)
        return DesktopWeb20ScheduleResult(result.size, result.firstOrNull()?.scheduledStartEpochMillis)
    }

    fun rebalanceSchedule(
        handling: String,
        nowEpochMillis: Long,
        startEpochMillis: Long,
        durationMinutes: Int,
    ): DesktopWeb20RebalanceResult {
        val plan = StudyPlanId("desktop-plan-web20")
        val activity = PlannedActivity(
            PlannedActivityId("desktop-activity-web20"), plan, null, null, null, null,
            StudyActivityType.THEORY, "Golden", startEpochMillis, durationMinutes,
            PlannedActivityStatus.MISSED, false, null,
        )
        val result = StudySchedulePlanner(TimeZone.getTimeZone("UTC")).rebalanceMissedActivities(
            activities = listOf(activity),
            rules = emptyList(),
            handling = MissedActivityHandling.valueOf(handling),
            nowEpochMillis = nowEpochMillis,
        )
        val changed = result.firstOrNull()
        return DesktopWeb20RebalanceResult(result.size, changed?.status?.name, changed?.scheduledStartEpochMillis)
    }

    fun practiceScore(
        total: Int,
        correct: Int,
        incorrect: Int,
        negativeMarking: Double,
        minimumScorePercent: Int?,
    ): DesktopWeb20ScoreResult {
        val ids = (0 until total).map { QuestionId("desktop-q-$it") }
        val answers = buildList {
            repeat(correct) { i -> add(PracticeAnswerRecord(ids[i], "A", "A", true, 1, 1)) }
            repeat(incorrect) { i ->
                add(PracticeAnswerRecord(ids[correct + i], "B", "A", false, 1, 1, ErrorCategory.OTHER))
            }
        }
        val summary = PracticeExecutionEngine { 1 }.finish(
            PracticeExecutionState(
                "desktop-golden", "Golden", TestMode.PRACTICE, ids, emptyList(), answers,
                0, null, null, negativeMarking, minimumScorePercent,
            ),
        )
        return DesktopWeb20ScoreResult(summary.scorePercent, summary.passed)
    }

    fun scheduleReview(
        intervalDays: Int,
        repetitionCount: Int,
        easeFactor: Double,
        grade: Int,
        reviewedAtEpochMillis: Long,
    ): DesktopWeb20SrsResult {
        val item = ReviewItem(
            ReviewItemId("desktop-review-web20"), StudyPlanId("desktop-plan-web20"), null, null, null,
            null, null, ReviewMethod.SPACED_REPETITION, ReviewStatus.DUE, 0, intervalDays,
            repetitionCount, easeFactor, null,
        )
        val next = SpacedRepetitionScheduler.schedule(item, grade, reviewedAtEpochMillis)
        return DesktopWeb20SrsResult(next.intervalDays, next.repetitionCount, next.easeFactor, next.dueAtEpochMillis)
    }

    fun studyMinutes(grossDurationSeconds: Long, pauseDurationSeconds: Long): Double {
        val plan = StudyPlanId("desktop-plan-web20")
        val started = 1_788_739_200_000L
        val goal = StudyGoal(
            StudyGoalId("desktop-time-goal"), plan, null, null, GoalType.STUDY_TIME, GoalPeriod.WEEKLY,
            "Golden study time", 10_000.0, 0.0, "2026-09-07", null, null, false,
        )
        val session = StudySession(
            StudySessionId("desktop-session-web20"), plan, null, null, null, null, null,
            StudyActivityType.THEORY, StudySessionStatus.COMPLETED, started, started + 1_000L,
            grossDurationSeconds, pauseDurationSeconds, 0, 0, 0, null, null, "",
        )
        return StudyGoalProgressEngine.evaluate(
            goal,
            GoalEvidence(listOf(session), emptyList(), emptyList(), emptyList()),
            started,
            ZoneId.of("UTC"),
        ).currentValue
    }

    fun goalPercent(currentValue: Double, targetValue: Double): Int = GoalProgressCalculator.goalPercent(
        StudyGoal(
            StudyGoalId("desktop-goal-web20"), StudyPlanId("desktop-plan-web20"), null, null,
            GoalType.CUSTOM, GoalPeriod.WEEKLY, "Golden", targetValue, currentValue,
            "2026-09-07", null, null, false,
        ),
    )

    fun errorState(category: String, recurrenceCount: Int): DesktopWeb20ErrorResult {
        val error = ErrorEntry(
            ErrorEntryId("desktop-error-web20"), StudyPlanId("desktop-plan-web20"), null, null, null,
            ErrorCategory.valueOf(category), null, null, "Golden", recurrenceCount, 0, 0,
        )
        return DesktopWeb20ErrorResult(
            WrongAnswerRemediator.priorityFor(error, 0).name,
            WrongAnswerRemediator.suggestionFor(error).name,
        )
    }

    fun canonicalJson(rawJson: String): String = CanonicalPortableJson.canonicalize(rawJson)

    fun conflictWinner(operations: List<DesktopWeb20ConflictInput>): String {
        val workspace = StudyFlowWorkspaceId("desktop-workspace-web20")
        val entity = StudyFlowEntityId("desktop-entity-web20")
        val revision = StudyFlowEntityRevision(1)
        val domainOperations = operations.map { operation ->
            StudyFlowDomainOperation(
                StudyFlowOperationId(operation.id), workspace, StudyFlowDeviceId(operation.deviceId),
                operation.originSequence, entity, revision, operation.logicalTimestamp, "study.updated",
                occurredAtEpochMillis = 0,
            )
        }
        return StudyFlowDeterministicWinnerConflictHandler.resolve(
            StudyFlowOperationConflict(entity, revision, domainOperations),
        ).single().operationId.value
    }
}
