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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyReminderPlannerPhase25Test {
    @Test
    fun quietHoursSuppressEveryReminderCandidateIncludingDailySummary() {
        val planId = StudyPlanId("plan-phase25")
        val plan = StudyPlan(
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
        val settings = StudyPlanSettings.defaults(planId).copy(
            quietHoursStartMinute = 9 * 60,
            quietHoursEndMinute = 11 * 60,
        )
        val now = 10 * 60 * 60 * 1000L

        val result = StudyReminderPlanner.buildPlan(
            StudyReminderInput(
                plan = plan,
                settings = settings,
                schedule = StudySchedule(
                    studyPlanId = planId,
                    strategy = null,
                    scheduleRules = emptyList(),
                    plannedActivities = listOf(
                        PlannedActivity(
                            id = PlannedActivityId("planned"),
                            studyPlanId = planId,
                            subjectId = null,
                            topicId = null,
                            materialId = null,
                            videoResourceId = null,
                            type = StudyActivityType.REVIEW,
                            title = "Planned review",
                            scheduledStartEpochMillis = now + 30 * 60 * 1000L,
                            plannedDurationMinutes = 45,
                            status = PlannedActivityStatus.PLANNED,
                            locked = false,
                            completedAtEpochMillis = null,
                        ),
                        PlannedActivity(
                            id = PlannedActivityId("missed"),
                            studyPlanId = planId,
                            subjectId = null,
                            topicId = null,
                            materialId = null,
                            videoResourceId = null,
                            type = StudyActivityType.QUESTIONS,
                            title = "Missed questions",
                            scheduledStartEpochMillis = now - 60 * 60 * 1000L,
                            plannedDurationMinutes = 45,
                            status = PlannedActivityStatus.MISSED,
                            locked = false,
                            completedAtEpochMillis = null,
                        ),
                    ),
                ),
                reviewQueue = ReviewQueue(
                    due = listOf(dueReview(planId)),
                    upcoming = emptyList(),
                    completedToday = emptyList(),
                    flashcards = emptyList(),
                    errors = emptyList(),
                ),
                nowEpochMillis = now,
                minuteOfDay = 10 * 60,
                apiLevel = 37,
                notificationPermissionGranted = true,
            ),
        )

        assertTrue(result.quietHoursActive)
        assertEquals(
            ReminderNotificationType.values().toSet(),
            result.notificationCandidates.map { it.type }.toSet(),
        )
        assertTrue(result.notificationCandidates.all { it.suppressedByQuietHours })
        assertEquals(0, result.activeCandidateCount)
    }

    @Test
    fun dueReviewsAreNotImmediatelyRescheduledAfterDelivery() {
        val now = 50_000L
        assertFalse(
            StudyReminderPlanner.shouldRescheduleAfterDelivery(
                ReminderNotificationCandidate(
                    type = ReminderNotificationType.DUE_REVIEWS,
                    title = "Due reviews",
                    body = "1 review is due.",
                    scheduledAtEpochMillis = now,
                    suppressedByQuietHours = false,
                ),
                deliveredAtEpochMillis = now,
            ),
        )
    }

    @Test
    fun futureCadenceRemindersRemainEligibleForRescheduleAfterDelivery() {
        val now = 50_000L
        assertTrue(
            StudyReminderPlanner.shouldRescheduleAfterDelivery(
                ReminderNotificationCandidate(
                    type = ReminderNotificationType.DAILY_SUMMARY,
                    title = "Daily summary",
                    body = "Summary ready.",
                    scheduledAtEpochMillis = now + 86_400_000L,
                    suppressedByQuietHours = false,
                ),
                deliveredAtEpochMillis = now,
            ),
        )
    }

    private fun dueReview(planId: StudyPlanId): ReviewItem = ReviewItem(
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
}
