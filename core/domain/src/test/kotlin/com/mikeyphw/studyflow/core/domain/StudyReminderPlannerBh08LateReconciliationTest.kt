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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyReminderPlannerBh08LateReconciliationTest {
    @Test
    fun reconciliationWindowHasFixedBoundaryBeyondNormalLateGrace() {
        val fireAt = 1_000_000L
        val slack = StudyReminderPlanner.PlannedActivityDeliveryGraceMillis + 60_000L
        val lastAccepted = fireAt + StudyReminderPlanner.PlannedActivityLateDeliveryGraceMillis + slack

        assertTrue(
            StudyReminderPlanner.plannedActivityWithinReconciliationDeliveryWindow(
                fireAtEpochMillis = fireAt,
                nowEpochMillis = lastAccepted,
                reconciliationSlackMillis = slack,
            ),
        )
        assertFalse(
            StudyReminderPlanner.plannedActivityWithinReconciliationDeliveryWindow(
                fireAtEpochMillis = fireAt,
                nowEpochMillis = lastAccepted + 1L,
                reconciliationSlackMillis = slack,
            ),
        )
    }

    @Test
    fun scheduledPlannedOccurrenceCanSurviveBoundedReconciliationSlack() {
        val scheduledAt = 1_000_000L
        val occurrenceId = plannedOccurrenceId("late", scheduledAt)
        val now = scheduledAt + StudyReminderPlanner.PlannedActivityLateDeliveryGraceMillis + 1L

        val withoutDurableEligibility = build(now, activity("late", scheduledAt))
        assertNull(withoutDurableEligibility.plannedCandidate())

        val reconciled = build(
            now = now,
            activity = activity("late", scheduledAt),
            reconciliationEligible = setOf(occurrenceId),
        )

        assertEquals(occurrenceId, reconciled.plannedCandidate()?.occurrenceId)
    }

    @Test
    fun reconciliationEligibilityNeverResurrectsCompletedOrDeliveredOccurrence() {
        val scheduledAt = 1_000_000L
        val occurrenceId = plannedOccurrenceId("late", scheduledAt)
        val now = scheduledAt + StudyReminderPlanner.PlannedActivityLateDeliveryGraceMillis + 1L

        val completed = build(
            now = now,
            activity = activity("late", scheduledAt, PlannedActivityStatus.COMPLETED),
            reconciliationEligible = setOf(occurrenceId),
        )
        assertNull(completed.plannedCandidate())

        val delivered = build(
            now = now,
            activity = activity("late", scheduledAt),
            reconciliationEligible = setOf(occurrenceId),
            delivered = setOf(occurrenceId),
        )
        assertNull(delivered.plannedCandidate())
    }

    @Test
    fun reconciliationEligibilityIsBoundToExactScheduledOccurrenceAfterReschedule() {
        val oldScheduledAt = 1_000_000L
        val newScheduledAt = oldScheduledAt + 60_000L
        val oldOccurrenceId = plannedOccurrenceId("late", oldScheduledAt)
        val now = newScheduledAt + StudyReminderPlanner.PlannedActivityLateDeliveryGraceMillis + 1L

        val plan = build(
            now = now,
            activity = activity("late", newScheduledAt),
            reconciliationEligible = setOf(oldOccurrenceId),
        )

        assertNull(plan.plannedCandidate())
        assertFalse(plan.notificationCandidates.any { candidate -> candidate.occurrenceId == oldOccurrenceId })
    }

    private fun build(
        now: Long,
        activity: PlannedActivity,
        reconciliationEligible: Set<String> = emptySet(),
        delivered: Set<String> = emptySet(),
    ): StudyReminderPlan = StudyReminderPlanner.buildPlan(
        StudyReminderInput(
            plan = Plan,
            settings = StudyPlanSettings.defaults(PlanId),
            schedule = StudySchedule(PlanId, null, emptyList(), listOf(activity)),
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
            deliveredOccurrenceIds = delivered,
            reconciliationEligiblePlannedOccurrenceIds = reconciliationEligible,
        ),
    )

    private fun StudyReminderPlan.plannedCandidate(): ReminderNotificationCandidate? =
        notificationCandidates.firstOrNull { candidate -> candidate.type == ReminderNotificationType.PLANNED_ACTIVITY }

    private fun plannedOccurrenceId(activityId: String, scheduledAt: Long): String =
        "planned:$activityId:$scheduledAt:${StudyReminderPolicy().plannedStudyLeadMinutes}"

    private fun activity(
        id: String,
        scheduledAt: Long,
        status: PlannedActivityStatus = PlannedActivityStatus.PLANNED,
    ) = PlannedActivity(
        id = PlannedActivityId(id),
        studyPlanId = PlanId,
        subjectId = null,
        topicId = null,
        materialId = null,
        videoResourceId = null,
        type = StudyActivityType.THEORY,
        title = "BH08 late reminder",
        scheduledStartEpochMillis = scheduledAt,
        plannedDurationMinutes = 45,
        status = status,
        locked = false,
        completedAtEpochMillis = if (status == PlannedActivityStatus.COMPLETED) scheduledAt else null,
    )

    private companion object {
        val PlanId = StudyPlanId("bh08-plan")
        val Plan = StudyPlan(
            id = PlanId,
            name = "BH08",
            description = "Late reminder reconciliation regression",
            type = StudyType.PUBLIC_EXAMINATION,
            status = StudyPlanStatus.ACTIVE,
            examDateIso = null,
            weeklyGoalHours = 10,
            progressPercent = 50,
            studiedMinutesThisWeek = 120,
            questionsAnswered = 40,
            averageAccuracyPercent = 75,
        )
    }
}
