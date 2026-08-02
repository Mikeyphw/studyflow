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

class StudyReminderPlannerPhase29HotfixTest {
    @Test
    fun plannedActivityStillDeliversWhenInexactAlarmFiresInsideWindow() {
        val now = 1_000_000L
        val plan = plan()
        val reminderPlan = StudyReminderPlanner.buildPlan(
            input(
                plan = plan,
                now = now,
                activities = listOf(
                    activity(
                        planId = plan.id,
                        scheduledStart = now - 5 * 60 * 1000L,
                    ),
                ),
            ),
        )

        val planned = reminderPlan.notificationCandidates.single { candidate ->
            candidate.type == ReminderNotificationType.PLANNED_ACTIVITY
        }
        assertEquals("Late but valid block", planned.title)
        assertFalse(planned.suppressedByQuietHours)
        assertFalse(
            StudyReminderPlanner.shouldRescheduleAfterDelivery(
                candidate = planned,
                deliveredAtEpochMillis = now,
            ),
        )
    }

    @Test
    fun stalePlannedActivityAlarmOutsideWindowIsSuppressed() {
        val now = 1_000_000L
        val plan = plan()
        val reminderPlan = StudyReminderPlanner.buildPlan(
            input(
                plan = plan,
                now = now,
                activities = listOf(
                    activity(
                        planId = plan.id,
                        scheduledStart = now - StudyReminderPlanner.PlannedActivityDeliveryGraceMillis - 1L,
                    ),
                ),
            ),
        )

        assertNull(reminderPlan.notificationCandidates.firstOrNull { candidate ->
            candidate.type == ReminderNotificationType.PLANNED_ACTIVITY
        })
        assertFalse(
            StudyReminderPlanner.plannedActivityWithinDeliveryWindow(
                scheduledStartEpochMillis = now - StudyReminderPlanner.PlannedActivityDeliveryGraceMillis - 1L,
                nowEpochMillis = now,
            ),
        )
    }

    @Test
    fun quietHoursStillSuppressLatePlannedActivityDelivery() {
        val now = 1_000_000L
        val plan = plan()
        val reminderPlan = StudyReminderPlanner.buildPlan(
            input(
                plan = plan,
                now = now,
                minuteOfDay = 10 * 60,
                settings = StudyPlanSettings.defaults(plan.id).copy(
                    quietHoursStartMinute = 9 * 60,
                    quietHoursEndMinute = 11 * 60,
                ),
                activities = listOf(
                    activity(
                        planId = plan.id,
                        scheduledStart = now - 5 * 60 * 1000L,
                    ),
                ),
            ),
        )

        val planned = reminderPlan.notificationCandidates.single { candidate ->
            candidate.type == ReminderNotificationType.PLANNED_ACTIVITY
        }
        assertTrue(planned.suppressedByQuietHours)
        assertEquals(0, reminderPlan.activeCandidateCount)
    }

    @Test
    fun completedActivitiesRemainSuppressedEvenInsideDeliveryWindow() {
        val now = 1_000_000L
        val plan = plan()
        val reminderPlan = StudyReminderPlanner.buildPlan(
            input(
                plan = plan,
                now = now,
                activities = listOf(
                    activity(
                        planId = plan.id,
                        scheduledStart = now - 5 * 60 * 1000L,
                        status = PlannedActivityStatus.COMPLETED,
                    ),
                ),
            ),
        )

        assertNull(reminderPlan.notificationCandidates.firstOrNull { candidate ->
            candidate.type == ReminderNotificationType.PLANNED_ACTIVITY
        })
    }

    private fun input(
        plan: StudyPlan,
        now: Long,
        activities: List<PlannedActivity>,
        minuteOfDay: Int = 12 * 60,
        settings: StudyPlanSettings = StudyPlanSettings.defaults(plan.id),
    ) = StudyReminderInput(
        plan = plan,
        settings = settings,
        schedule = StudySchedule(
            studyPlanId = plan.id,
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
        minuteOfDay = minuteOfDay,
        apiLevel = 37,
        notificationPermissionGranted = true,
    )

    private fun plan() = StudyPlan(
        id = StudyPlanId("phase29-hotfix-plan"),
        name = "Phase 29 Hotfix Plan",
        description = "Reminder delivery grace coverage",
        type = StudyType.PUBLIC_EXAMINATION,
        status = StudyPlanStatus.ACTIVE,
        examDateIso = null,
        weeklyGoalHours = 10,
        progressPercent = 55,
        studiedMinutesThisWeek = 240,
        questionsAnswered = 120,
        averageAccuracyPercent = 82,
    )

    private fun activity(
        planId: StudyPlanId,
        scheduledStart: Long,
        status: PlannedActivityStatus = PlannedActivityStatus.PLANNED,
    ) = PlannedActivity(
        id = PlannedActivityId("late-planned"),
        studyPlanId = planId,
        subjectId = null,
        topicId = null,
        materialId = null,
        videoResourceId = null,
        type = StudyActivityType.THEORY,
        title = "Late but valid block",
        scheduledStartEpochMillis = scheduledStart,
        plannedDurationMinutes = 45,
        status = status,
        locked = false,
        completedAtEpochMillis = if (status == PlannedActivityStatus.COMPLETED) scheduledStart else null,
    )
}
