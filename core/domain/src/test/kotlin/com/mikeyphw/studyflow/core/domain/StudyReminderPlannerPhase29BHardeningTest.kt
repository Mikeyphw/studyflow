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
import com.mikeyphw.studyflow.core.model.StudyType
import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyReminderPlannerPhase29BHardeningTest {
    @Test
    fun dailySummaryUsesNextLocalMidnightInsteadOfUtcBucket() = withTimeZone("America/Sao_Paulo") {
        val now = epoch(2026, Calendar.AUGUST, 1, 23, 30)
        val expectedLocalMidnight = epoch(2026, Calendar.AUGUST, 2, 0, 0)
        val reminderPlan = StudyReminderPlanner.buildPlan(
            input(
                now = now,
                activities = listOf(
                    activity(scheduledStart = epoch(2026, Calendar.AUGUST, 2, 0, 15)),
                ),
            ),
        )

        val daily = reminderPlan.notificationCandidates.single { candidate ->
            candidate.type == ReminderNotificationType.DAILY_SUMMARY
        }
        assertEquals(expectedLocalMidnight, daily.scheduledAtEpochMillis)
        assertEquals(0, reminderPlan.widgetSummary.plannedActivityCount)
        assertFalse(StudyReminderPlanner.sameLocalDay(now, epoch(2026, Calendar.AUGUST, 2, 0, 15)))
    }

    @Test
    fun latePlannedActivitySurvivesDozeDelayInsideLateDeliveryGrace() {
        val now = 10_000_000L
        val reminderPlan = StudyReminderPlanner.buildPlan(
            input(
                now = now,
                activities = listOf(
                    activity(scheduledStart = now - 90 * 60 * 1000L),
                ),
            ),
        )

        val planned = reminderPlan.notificationCandidates.single { candidate ->
            candidate.type == ReminderNotificationType.PLANNED_ACTIVITY
        }
        assertEquals("Phase 29B local-time block", planned.content.userLabel)
        assertFalse(planned.delayedByQuietHours)
        assertFalse(StudyReminderPlanner.shouldRescheduleAfterDelivery(planned, now))
    }

    @Test
    fun equalQuietHourBoundsMeanQuietHoursDisabled() {
        val settings = StudyPlanSettings.defaults(PlanId).copy(
            quietHoursStartMinute = 480,
            quietHoursEndMinute = 480,
        )
        assertFalse(StudyReminderPlanner.isQuietNow(settings, 480))
        assertFalse(StudyReminderPlanner.isQuietNow(settings, 0))
    }

    @Test
    fun veryOldPlannedActivityStillSuppressedOutsideLateDeliveryGrace() {
        val now = 10_000_000L
        val reminderPlan = StudyReminderPlanner.buildPlan(
            input(
                now = now,
                activities = listOf(
                    activity(scheduledStart = now - StudyReminderPlanner.PlannedActivityLateDeliveryGraceMillis - 1L),
                ),
            ),
        )

        assertNull(reminderPlan.notificationCandidates.firstOrNull { candidate ->
            candidate.type == ReminderNotificationType.PLANNED_ACTIVITY
        })
        assertFalse(
            StudyReminderPlanner.plannedActivityWithinDeliveryWindow(
                scheduledStartEpochMillis = now - StudyReminderPlanner.PlannedActivityLateDeliveryGraceMillis - 1L,
                nowEpochMillis = now,
            ),
        )
    }

    private fun withTimeZone(id: String, block: () -> Unit) {
        val previous = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(id))
            block()
        } finally {
            TimeZone.setDefault(previous)
        }
    }

    private fun epoch(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month, day, hour, minute, 0)
        }.timeInMillis

    private fun input(
        now: Long,
        activities: List<PlannedActivity>,
        settings: StudyPlanSettings = StudyPlanSettings.defaults(PlanId),
    ) = StudyReminderInput(
        plan = Plan,
        settings = settings,
        schedule = StudySchedule(
            studyPlanId = PlanId,
            strategy = null,
            scheduleRules = emptyList(),
            plannedActivities = activities,
        ),
        reviewQueue = ReviewQueue(
            due = emptyList(),
            upcoming = emptyList(),
            completedToday = emptyList(),
            flashcards = emptyList(),
            errors = emptyList(),
        ),
        nowEpochMillis = now,
        minuteOfDay = 12 * 60,
        apiLevel = 37,
        notificationPermissionGranted = true,
    )

    private fun activity(
        scheduledStart: Long,
        status: PlannedActivityStatus = PlannedActivityStatus.PLANNED,
    ) = PlannedActivity(
        id = PlannedActivityId("phase29b-activity"),
        studyPlanId = PlanId,
        subjectId = null,
        topicId = null,
        materialId = null,
        videoResourceId = null,
        type = StudyActivityType.THEORY,
        title = "Phase 29B local-time block",
        scheduledStartEpochMillis = scheduledStart,
        plannedDurationMinutes = 45,
        status = status,
        locked = false,
        completedAtEpochMillis = null,
    )

    private companion object {
        val PlanId = StudyPlanId("phase29b-plan")
        val Plan = StudyPlan(
            id = PlanId,
            name = "Phase 29B Plan",
            description = "Reminder/time hardening coverage",
            type = StudyType.PUBLIC_EXAMINATION,
            status = StudyPlanStatus.ACTIVE,
            examDateIso = null,
            weeklyGoalHours = 10,
            progressPercent = 55,
            studiedMinutesThisWeek = 240,
            questionsAnswered = 120,
            averageAccuracyPercent = 82,
        )
    }
}
