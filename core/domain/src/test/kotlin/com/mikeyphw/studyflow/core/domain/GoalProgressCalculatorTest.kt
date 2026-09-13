package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.GoalPeriod
import com.mikeyphw.studyflow.core.model.GoalType
import com.mikeyphw.studyflow.core.model.StudyGoal
import com.mikeyphw.studyflow.core.model.StudyGoalId
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyActivityType
import com.mikeyphw.studyflow.core.model.StudySession
import com.mikeyphw.studyflow.core.model.StudySessionId
import com.mikeyphw.studyflow.core.model.StudySessionStatus
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class GoalProgressCalculatorTest {
    @Test
    fun goalPercentClampsToOneHundred() {
        assertEquals(100, GoalProgressCalculator.goalPercent(goal(current = 12.0, target = 10.0)))
    }

    @Test
    fun overallGoalPercentIgnoresArchivedGoals() {
        val progress = GoalProgressCalculator.overallGoalPercent(
            listOf(goal(current = 5.0, target = 10.0), goal(current = 10.0, target = 10.0, archived = true)),
        )
        assertEquals(50, progress)
    }

    @Test
    fun weeklyEvidenceIgnoresPriorWeekSessions() {
        val now = Instant.parse("2026-08-13T12:00:00Z").toEpochMilli()
        val thisWeek = session("current", Instant.parse("2026-08-12T12:00:00Z").toEpochMilli(), 3_600L)
        val priorWeek = session("old", Instant.parse("2026-08-02T12:00:00Z").toEpochMilli(), 7_200L)
        val evaluated = StudyGoalProgressEngine.evaluate(
            goal = goal(current = 999.0, target = 120.0),
            evidence = GoalEvidence(
                sessions = listOf(priorWeek, thisWeek),
                tests = emptyList(),
                mockExams = emptyList(),
                topics = emptyList(),
            ),
            nowEpochMillis = now,
            zoneId = ZoneId.of("UTC"),
        )

        assertEquals(60.0, evaluated.currentValue, 0.0)
        assertEquals("2026-08-10", evaluated.startsOnIso)
        assertEquals(null, evaluated.completedAtEpochMillis)
    }

    @Test
    fun completedSessionDoesNotSubtractRecordedPauseFromPersistedActiveTimeAgain() {
        val now = Instant.parse("2026-08-13T12:00:00Z").toEpochMilli()
        val evaluated = StudyGoalProgressEngine.evaluate(
            goal = goal(current = 0.0, target = 30.0),
            evidence = GoalEvidence(
                sessions = listOf(session("paused", now - 60_000L, 600L, pauseSeconds = 600L)),
                tests = emptyList(),
                mockExams = emptyList(),
                topics = emptyList(),
            ),
            nowEpochMillis = now,
            zoneId = ZoneId.of("UTC"),
        )

        assertEquals(10.0, evaluated.currentValue, 0.0)
    }

    private fun session(id: String, startedAt: Long, grossSeconds: Long, pauseSeconds: Long = 0L) = StudySession(
        id = StudySessionId(id),
        studyPlanId = StudyPlanId("plan-1"),
        plannedActivityId = null,
        subjectId = null,
        topicId = null,
        materialId = null,
        videoResourceId = null,
        type = StudyActivityType.THEORY,
        status = StudySessionStatus.COMPLETED,
        startedAtEpochMillis = startedAt,
        endedAtEpochMillis = startedAt + grossSeconds * 1_000L,
        grossDurationSeconds = grossSeconds,
        pauseDurationSeconds = pauseSeconds,
        pagesCompleted = 0,
        questionsAnswered = 0,
        correctAnswers = 0,
        concentrationRating = null,
        difficultyRating = null,
        notes = "",
        targetDurationMinutes = 60,
    )

    private fun goal(current: Double, target: Double, archived: Boolean = false): StudyGoal = StudyGoal(
        id = StudyGoalId("goal-$current"),
        studyPlanId = StudyPlanId("plan-1"),
        subjectId = null,
        topicId = null,
        type = GoalType.STUDY_TIME,
        period = GoalPeriod.WEEKLY,
        title = "Goal",
        targetValue = target,
        currentValue = current,
        startsOnIso = "2026-08-01",
        endsOnIso = null,
        completedAtEpochMillis = null,
        archived = archived,
    )
}
