package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.PlannedActivity
import com.mikeyphw.studyflow.core.model.PlannedActivityId
import com.mikeyphw.studyflow.core.model.PlannedActivityStatus
import com.mikeyphw.studyflow.core.model.StudyActivityType
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyPlanSettings
import com.mikeyphw.studyflow.core.model.StudyPlanStatus
import com.mikeyphw.studyflow.core.model.StudySchedule
import com.mikeyphw.studyflow.core.model.StudySession
import com.mikeyphw.studyflow.core.model.StudySessionId
import com.mikeyphw.studyflow.core.model.StudySessionStatus
import com.mikeyphw.studyflow.core.model.StudyType
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyReminderPlannerAtt02Test {
    @Test
    fun dailySummaryDefaultsToEightAmInsteadOfMidnight() {
        val now = epoch(2026, Calendar.SEPTEMBER, 11, 7, 0)
        val result = build(now = now)
        val summary = result.notificationCandidates.single { it.type == ReminderNotificationType.DAILY_SUMMARY }
        val calendar = Calendar.getInstance().apply { timeInMillis = summary.scheduledAtEpochMillis }

        assertEquals(8, calendar.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, calendar.get(Calendar.MINUTE))
        assertTrue(summary.scheduledAtEpochMillis > now)
    }

    @Test
    fun categoryPolicyCanDisableEveryReminderFamilyIndependently() {
        val policy = StudyReminderPolicy(
            plannedStudyEnabled = false,
            dueReviewEnabled = false,
            missedActivityEnabled = false,
            dailySummaryEnabled = false,
            sessionTimerEnabled = false,
            examCountdownEnabled = false,
        )
        val result = build(
            policy = policy,
            activities = listOf(plannedActivity(epoch(2026, Calendar.SEPTEMBER, 11, 10, 0))),
        )

        assertTrue(result.notificationCandidates.isEmpty())
    }

    @Test
    fun plannedStudyLeadTimeMovesOccurrenceBeforeActivityStart() {
        val now = epoch(2026, Calendar.SEPTEMBER, 11, 9, 0)
        val start = now + 60 * 60 * 1000L
        val result = build(
            now = now,
            policy = StudyReminderPolicy(plannedStudyLeadMinutes = 20),
            activities = listOf(plannedActivity(start)),
        )
        val planned = result.notificationCandidates.single { it.type == ReminderNotificationType.PLANNED_ACTIVITY }

        assertEquals(start - 20 * 60_000L, planned.scheduledAtEpochMillis)
        assertTrue(planned.occurrenceId.endsWith(":20"))
    }

    @Test
    fun runningTimedSessionProducesEndingAndCompletionOccurrences() {
        val now = epoch(2026, Calendar.SEPTEMBER, 11, 9, 0)
        val result = build(
            now = now,
            activeSessions = listOf(session(startedAt = now, targetMinutes = 45)),
        )
        val timerTypes = result.notificationCandidates
            .filter {
                it.type in setOf(
                    ReminderNotificationType.SESSION_ENDING,
                    ReminderNotificationType.SESSION_COMPLETED,
                )
            }
            .map { it.type }
            .toSet()

        assertEquals(
            setOf(ReminderNotificationType.SESSION_ENDING, ReminderNotificationType.SESSION_COMPLETED),
            timerTypes,
        )
    }

    @Test
    fun elapsedSessionSuppressesStaleEndingWarningButKeepsCompletionRecovery() {
        val now = epoch(2026, Calendar.SEPTEMBER, 11, 10, 0)
        val result = build(
            now = now,
            activeSessions = listOf(session(startedAt = now - 50 * 60_000L, targetMinutes = 45)),
        )

        assertFalse(result.notificationCandidates.any { it.type == ReminderNotificationType.SESSION_ENDING })
        assertNotNull(
            result.notificationCandidates.firstOrNull {
                it.type == ReminderNotificationType.SESSION_COMPLETED
            },
        )
    }

    @Test
    fun examCountdownIsOptInAndUsesConfiguredHorizon() {
        val now = epoch(2026, Calendar.SEPTEMBER, 11, 7, 0)
        val examPlan = plan(examDateIso = "2026-09-12")
        val disabled = build(plan = examPlan, now = now)
        val enabled = build(
            plan = examPlan,
            now = now,
            policy = StudyReminderPolicy(examCountdownEnabled = true, examCountdownDays = 7),
        )

        assertFalse(disabled.notificationCandidates.any { it.type == ReminderNotificationType.EXAM_COUNTDOWN })
        assertTrue(enabled.notificationCandidates.any { it.type == ReminderNotificationType.EXAM_COUNTDOWN })
    }

    private fun build(
        plan: StudyPlan = plan(),
        now: Long = epoch(2026, Calendar.SEPTEMBER, 11, 7, 0),
        policy: StudyReminderPolicy = StudyReminderPolicy(),
        activities: List<PlannedActivity> = emptyList(),
        activeSessions: List<StudySession> = emptyList(),
    ): StudyReminderPlan = StudyReminderPlanner.buildPlan(
        StudyReminderInput(
            plan = plan,
            settings = StudyPlanSettings.defaults(plan.id),
            schedule = StudySchedule(plan.id, null, emptyList(), activities),
            reviewQueue = ReviewQueue(emptyList(), emptyList(), emptyList(), emptyList(), emptyList()),
            nowEpochMillis = now,
            minuteOfDay = Calendar.getInstance().apply { timeInMillis = now }
                .run { get(Calendar.HOUR_OF_DAY) * 60 + get(Calendar.MINUTE) },
            apiLevel = 37,
            notificationPermissionGranted = true,
            policy = policy,
            activeSessions = activeSessions,
        ),
    )

    private fun plan(examDateIso: String? = null) = StudyPlan(
        id = StudyPlanId("att02-plan"),
        name = "ATT02 plan",
        description = "Android attention coverage",
        type = StudyType.PUBLIC_EXAMINATION,
        status = StudyPlanStatus.ACTIVE,
        examDateIso = examDateIso,
        weeklyGoalHours = 10,
        progressPercent = 50,
        studiedMinutesThisWeek = 120,
        questionsAnswered = 20,
        averageAccuracyPercent = 75,
    )

    private fun plannedActivity(startAt: Long) = PlannedActivity(
        id = PlannedActivityId("att02-activity"),
        studyPlanId = StudyPlanId("att02-plan"),
        subjectId = null,
        topicId = null,
        materialId = null,
        videoResourceId = null,
        type = StudyActivityType.THEORY,
        title = "Planned block",
        scheduledStartEpochMillis = startAt,
        plannedDurationMinutes = 45,
        status = PlannedActivityStatus.PLANNED,
        locked = false,
        completedAtEpochMillis = null,
    )

    private fun session(startedAt: Long, targetMinutes: Int) = StudySession(
        id = StudySessionId("att02-session"),
        studyPlanId = StudyPlanId("att02-plan"),
        plannedActivityId = null,
        subjectId = null,
        topicId = null,
        materialId = null,
        videoResourceId = null,
        type = StudyActivityType.THEORY,
        status = StudySessionStatus.RUNNING,
        startedAtEpochMillis = startedAt,
        endedAtEpochMillis = null,
        grossDurationSeconds = 0,
        pauseDurationSeconds = 0,
        pagesCompleted = 0,
        questionsAnswered = 0,
        correctAnswers = 0,
        concentrationRating = null,
        difficultyRating = null,
        notes = "Timed session",
        targetDurationMinutes = targetMinutes,
    )

    private fun epoch(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long = Calendar.getInstance().apply {
        clear()
        set(year, month, day, hour, minute, 0)
    }.timeInMillis
}
