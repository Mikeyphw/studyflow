package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.ReviewItem
import com.mikeyphw.studyflow.core.model.ReviewItemId
import com.mikeyphw.studyflow.core.model.ReviewMethod
import com.mikeyphw.studyflow.core.model.ReviewStatus
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyPlanSettings
import com.mikeyphw.studyflow.core.model.StudyPlanStatus
import com.mikeyphw.studyflow.core.model.StudySchedule
import com.mikeyphw.studyflow.core.model.StudyType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StudyReminderPlannerOv16Test {
    @Test
    fun futureDueReviewsReceiveDurableWakeupCandidate() {
        val plan = plan()
        val futureDueAt = 2_400_000L
        val reminderPlan = StudyReminderPlanner.buildPlan(
            StudyReminderInput(
                plan = plan,
                settings = StudyPlanSettings.defaults(plan.id),
                schedule = StudySchedule(
                    studyPlanId = plan.id,
                    strategy = null,
                    scheduleRules = emptyList(),
                    plannedActivities = emptyList(),
                ),
                reviewQueue = ReviewQueue(
                    due = emptyList(),
                    upcoming = listOf(review(plan.id, "future", futureDueAt)),
                    completedToday = emptyList(),
                    flashcards = emptyList(),
                    errors = emptyList(),
                ),
                nowEpochMillis = 60_000L,
                minuteOfDay = 10 * 60,
                apiLevel = 35,
                notificationPermissionGranted = true,
            ),
        )

        val reviewCandidate = reminderPlan.notificationCandidates.single {
            it.type == ReminderNotificationType.DUE_REVIEWS
        }
        assertTrue(reviewCandidate.occurrenceId.startsWith("due-future:$futureDueAt:"))
        assertEquals(futureDueAt, reviewCandidate.scheduledAtEpochMillis)
        assertEquals(futureDueAt, reviewCandidate.fireAtEpochMillis)
    }

    private fun plan() = StudyPlan(
        id = StudyPlanId("plan"),
        name = "OV16 plan",
        description = "",
        type = StudyType.FREE_STUDY,
        status = StudyPlanStatus.ACTIVE,
        examDateIso = null,
        weeklyGoalHours = 10,
        progressPercent = 10,
        studiedMinutesThisWeek = 0,
        questionsAnswered = 0,
        averageAccuracyPercent = 0,
    )

    private fun review(planId: StudyPlanId, id: String, dueAt: Long) = ReviewItem(
        id = ReviewItemId(id),
        studyPlanId = planId,
        subjectId = null,
        topicId = null,
        questionId = null,
        flashcardId = null,
        errorEntryId = null,
        method = ReviewMethod.SPACED_REPETITION,
        status = ReviewStatus.SCHEDULED,
        dueAtEpochMillis = dueAt,
        intervalDays = 1,
        repetitionCount = 1,
        easeFactor = 2.5,
        completedAtEpochMillis = null,
    )
}
