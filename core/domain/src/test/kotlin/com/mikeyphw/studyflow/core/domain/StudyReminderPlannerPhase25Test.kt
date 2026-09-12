package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.PlannedActivity
import com.mikeyphw.studyflow.core.model.PlannedActivityId
import com.mikeyphw.studyflow.core.model.PlannedActivityStatus
import com.mikeyphw.studyflow.core.model.ReviewItem
import com.mikeyphw.studyflow.core.model.ReviewItemId
import com.mikeyphw.studyflow.core.model.ReviewMethod
import com.mikeyphw.studyflow.core.model.ReviewStatus
import com.mikeyphw.studyflow.core.model.StudyActivityType
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyPlanSettings
import com.mikeyphw.studyflow.core.model.StudyPlanStatus
import com.mikeyphw.studyflow.core.model.StudySchedule
import com.mikeyphw.studyflow.core.model.StudyType
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyReminderPlannerPhase25Test {
    @Test
    fun quietHoursDelayCandidatesInsteadOfDroppingThem() {
        val planId = StudyPlanId("plan-phase25")
        val plan = plan(planId)
        val settings = StudyPlanSettings.defaults(planId).copy(
            quietHoursStartMinute = 9 * 60,
            quietHoursEndMinute = 11 * 60,
        )
        val now = epoch(hour = 10)

        val result = StudyReminderPlanner.buildPlan(
            input(
                plan = plan,
                settings = settings,
                now = now,
                minuteOfDay = 10 * 60,
                activities = listOf(
                    activity(planId, "planned", now + 30 * 60 * 1000L, PlannedActivityStatus.PLANNED),
                    activity(planId, "missed", now - 60 * 60 * 1000L, PlannedActivityStatus.MISSED),
                ),
                due = listOf(dueReview(planId)),
            ),
        )

        assertTrue(result.quietHoursActive)
        assertEquals(
            setOf(
                ReminderNotificationType.DUE_REVIEWS,
                ReminderNotificationType.PLANNED_ACTIVITY,
                ReminderNotificationType.MISSED_ACTIVITY,
                ReminderNotificationType.DAILY_SUMMARY,
            ),
            result.notificationCandidates.map { it.type }.toSet(),
        )
        assertTrue(result.notificationCandidates.any { it.delayedByQuietHours })
        assertTrue(result.notificationCandidates.all { it.fireAtEpochMillis >= it.scheduledAtEpochMillis })
        assertEquals(result.notificationCandidates.size, result.activeCandidateCount)
    }

    @Test
    fun equalQuietEndpointsDisableQuietHours() {
        val planId = StudyPlanId("equal")
        val settings = StudyPlanSettings.defaults(planId).copy(
            quietHoursStartMinute = 480,
            quietHoursEndMinute = 480,
        )

        assertFalse(StudyReminderPlanner.isQuietNow(settings, 480))
        assertFalse(StudyReminderPlanner.isQuietNow(settings, 0))
    }

    private fun input(
        plan: StudyPlan,
        settings: StudyPlanSettings,
        now: Long,
        minuteOfDay: Int,
        activities: List<PlannedActivity>,
        due: List<ReviewItem>,
    ) = StudyReminderInput(
        plan = plan,
        settings = settings,
        schedule = StudySchedule(plan.id, null, emptyList(), activities),
        reviewQueue = ReviewQueue(due, emptyList(), emptyList(), emptyList(), emptyList()),
        nowEpochMillis = now,
        minuteOfDay = minuteOfDay,
        apiLevel = 37,
        notificationPermissionGranted = true,
    )

    private fun plan(planId: StudyPlanId) = StudyPlan(
        id = planId,
        name = "Phase 25 Plan",
        description = "Quiet-hour coverage",
        type = StudyType.PUBLIC_EXAMINATION,
        status = StudyPlanStatus.ACTIVE,
        examDateIso = null,
        weeklyGoalHours = 12,
        progressPercent = 40,
        studiedMinutesThisWeek = 120,
        questionsAnswered = 80,
        averageAccuracyPercent = 75,
    )

    private fun activity(
        planId: StudyPlanId,
        id: String,
        scheduledAt: Long,
        status: PlannedActivityStatus,
    ) = PlannedActivity(
        id = PlannedActivityId(id),
        studyPlanId = planId,
        subjectId = null,
        topicId = null,
        materialId = null,
        videoResourceId = null,
        type = StudyActivityType.REVIEW,
        title = id,
        scheduledStartEpochMillis = scheduledAt,
        plannedDurationMinutes = 45,
        status = status,
        locked = false,
        completedAtEpochMillis = null,
    )

    private fun dueReview(planId: StudyPlanId) = ReviewItem(
        id = ReviewItemId("due-review"),
        studyPlanId = planId,
        subjectId = null,
        topicId = null,
        questionId = null,
        flashcardId = null,
        errorEntryId = null,
        method = ReviewMethod.SPACED_REPETITION,
        status = ReviewStatus.DUE,
        dueAtEpochMillis = 0L,
        intervalDays = 0,
        repetitionCount = 0,
        easeFactor = 2.5,
        completedAtEpochMillis = null,
    )

    private fun epoch(hour: Int): Long = Calendar.getInstance().apply {
        clear()
        set(2026, Calendar.AUGUST, 14, hour, 0, 0)
    }.timeInMillis
}
