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
import com.mikeyphw.studyflow.core.model.StudyType
import com.mikeyphw.studyflow.core.model.StudySchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyReminderPlannerTest {
    @Test
    fun buildsDueReviewAndActivityReminderCandidates() {
        val plan = plan()
        val queue = ReviewQueue(
            due = listOf(review(plan.id)),
            upcoming = emptyList(),
            completedToday = emptyList(),
            flashcards = emptyList(),
            errors = emptyList(),
        )
        val reminderPlan = StudyReminderPlanner.buildPlan(
            StudyReminderInput(
                plan = plan,
                settings = StudyPlanSettings.defaults(plan.id),
                schedule = StudySchedule(
                    studyPlanId = plan.id,
                    strategy = null,
                    scheduleRules = emptyList(),
                    plannedActivities = listOf(activity(plan.id)),
                ),
                reviewQueue = queue,
                nowEpochMillis = 1_000L,
                minuteOfDay = 9 * 60,
                apiLevel = 33,
                notificationPermissionGranted = false,
            ),
        )

        assertTrue(reminderPlan.permissionRequired)
        assertEquals(3, reminderPlan.notificationCandidates.size)
        assertEquals(1, reminderPlan.widgetSummary.dueReviewCount)
        assertEquals("Theory block", reminderPlan.widgetSummary.nextActivityTitle)
    }

    @Test
    fun quietHoursSuppressImmediateCandidates() {
        val settings = StudyPlanSettings.defaults(plan().id).copy(
            quietHoursStartMinute = 22 * 60,
            quietHoursEndMinute = 7 * 60,
        )

        assertTrue(StudyReminderPlanner.isQuietNow(settings, 23 * 60))
        assertTrue(StudyReminderPlanner.isQuietNow(settings, 6 * 60))
        assertFalse(StudyReminderPlanner.isQuietNow(settings, 12 * 60))
    }

    private fun plan() = StudyPlan(
        id = StudyPlanId("plan"),
        name = "Public Service Exam",
        description = "",
        type = StudyType.PUBLIC_EXAMINATION,
        status = StudyPlanStatus.ACTIVE,
        examDateIso = null,
        weeklyGoalHours = 10,
        progressPercent = 42,
        studiedMinutesThisWeek = 120,
        questionsAnswered = 30,
        averageAccuracyPercent = 70,
    )

    private fun activity(planId: StudyPlanId) = PlannedActivity(
        id = PlannedActivityId("activity"),
        studyPlanId = planId,
        subjectId = null,
        topicId = null,
        materialId = null,
        videoResourceId = null,
        type = StudyActivityType.THEORY,
        title = "Theory block",
        scheduledStartEpochMillis = 2_000L,
        plannedDurationMinutes = 45,
        status = PlannedActivityStatus.PLANNED,
        locked = false,
        completedAtEpochMillis = null,
    )

    private fun review(planId: StudyPlanId) = ReviewItem(
        id = ReviewItemId("review"),
        studyPlanId = planId,
        subjectId = null,
        topicId = null,
        questionId = null,
        flashcardId = null,
        errorEntryId = null,
        method = ReviewMethod.SPACED_REPETITION,
        status = ReviewStatus.DUE,
        dueAtEpochMillis = 500L,
        intervalDays = 0,
        repetitionCount = 0,
        easeFactor = 2.5,
        completedAtEpochMillis = null,
    )
}
