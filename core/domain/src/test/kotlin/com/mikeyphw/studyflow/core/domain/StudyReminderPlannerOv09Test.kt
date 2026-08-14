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
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyReminderPlannerOv09Test {
    @Test
    fun futureCandidateInsideQuietHoursDelaysToQuietEnd() = withTimeZone("America/Sao_Paulo") {
        val now = epoch(2026, Calendar.AUGUST, 14, 20, 0)
        val activityAt = epoch(2026, Calendar.AUGUST, 14, 23, 0)
        val quietEnd = epoch(2026, Calendar.AUGUST, 15, 7, 0)
        val settings = StudyPlanSettings.defaults(Plan.id).copy(
            quietHoursStartMinute = 22 * 60,
            quietHoursEndMinute = 7 * 60,
        )

        val candidate = build(settings, now, listOf(activity("first", activityAt))).notificationCandidates
            .single { it.type == ReminderNotificationType.PLANNED_ACTIVITY }

        assertTrue(candidate.delayedByQuietHours)
        assertEquals(quietEnd, candidate.fireAtEpochMillis)
    }

    @Test
    fun reviewToggleOnlyRemovesDueReviewCandidate() {
        val now = 10_000_000L
        val settings = StudyPlanSettings.defaults(Plan.id).copy(reviewNotificationsEnabled = false)
        val plan = build(settings, now, listOf(activity("planned", now + 60_000L)), dueCount = 1)

        assertFalse(plan.notificationCandidates.any { it.type == ReminderNotificationType.DUE_REVIEWS })
        assertTrue(plan.notificationCandidates.any { it.type == ReminderNotificationType.PLANNED_ACTIVITY })
        assertTrue(plan.notificationCandidates.any { it.type == ReminderNotificationType.DAILY_SUMMARY })
    }

    @Test
    fun deliveredPlannedOccurrenceAdvancesToLaterOccurrence() {
        val now = 10_000_000L
        val first = activity("first", now + 60_000L)
        val second = activity("second", now + 120_000L)
        val initial = build(StudyPlanSettings.defaults(Plan.id), now, listOf(first, second))
        val firstCandidate = initial.notificationCandidates.single { it.type == ReminderNotificationType
            .PLANNED_ACTIVITY }

        val chained = build(
            StudyPlanSettings.defaults(Plan.id),
            now,
            listOf(first, second),
            delivered = setOf(firstCandidate.occurrenceId),
        ).notificationCandidates.single { it.type == ReminderNotificationType.PLANNED_ACTIVITY }

        assertNotEquals(firstCandidate.occurrenceId, chained.occurrenceId)
        assertEquals("second", chained.content.userLabel)
    }

    @Test
    fun persistedDueAndMissedOccurrenceTimesDoNotSlideAcrossReconciliation() {
        val now = 10_000_000L
        val initial = build(
            settings = StudyPlanSettings.defaults(Plan.id),
            now = now,
            activities = listOf(missedActivity("missed", now - 60_000L)),
            dueCount = 1,
        )
        val due = initial.notificationCandidates.single { it.type == ReminderNotificationType.DUE_REVIEWS }
        val missed = initial.notificationCandidates.single { it.type == ReminderNotificationType.MISSED_ACTIVITY }
        val later = now + 30 * 60_000L

        val reconciled = build(
            settings = StudyPlanSettings.defaults(Plan.id),
            now = later,
            activities = listOf(missedActivity("missed", now - 60_000L)),
            dueCount = 1,
            dueAtEpochMillis = now,
            persistedScheduledAt = mapOf(
                due.occurrenceId to due.scheduledAtEpochMillis,
                missed.occurrenceId to missed.scheduledAtEpochMillis,
            ),
        )

        assertEquals(
            due.scheduledAtEpochMillis,
            reconciled.notificationCandidates.single { it.type == ReminderNotificationType.DUE_REVIEWS }
                .scheduledAtEpochMillis,
        )
        assertEquals(
            missed.scheduledAtEpochMillis,
            reconciled.notificationCandidates.single { it.type == ReminderNotificationType.MISSED_ACTIVITY }
                .scheduledAtEpochMillis,
        )
    }

    @Test
    fun persistedOccurrenceTimeReevaluatesQuietHoursWithoutChangingOriginalSchedule() =
        withTimeZone("America/Sao_Paulo") {
            val scheduledAt = epoch(2026, Calendar.AUGUST, 14, 23, 0)
            val dueAt = epoch(2026, Calendar.AUGUST, 14, 20, 0)
            val initial = build(
                settings = StudyPlanSettings.defaults(Plan.id),
                now = dueAt,
                activities = emptyList(),
                dueCount = 1,
                dueAtEpochMillis = dueAt,
            )
            val due = initial.notificationCandidates.single { it.type == ReminderNotificationType.DUE_REVIEWS }
            val persistedAt = scheduledAt
            val quietSettings = StudyPlanSettings.defaults(Plan.id).copy(
                quietHoursStartMinute = 22 * 60,
                quietHoursEndMinute = 7 * 60,
            )
            val reconciled = build(
                settings = quietSettings,
                now = epoch(2026, Calendar.AUGUST, 14, 22, 30),
                activities = emptyList(),
                dueCount = 1,
                dueAtEpochMillis = dueAt,
                persistedScheduledAt = mapOf(due.occurrenceId to persistedAt),
            )
            val current = reconciled.notificationCandidates.single { it.type == ReminderNotificationType.DUE_REVIEWS }

            assertEquals(persistedAt, current.scheduledAtEpochMillis)
            assertEquals(epoch(2026, Calendar.AUGUST, 15, 7, 0), current.fireAtEpochMillis)
        }

    @Test
    fun occurrenceIdentityIsStableAndContainsEntityIdentityNotHashBucket() {
        val now = 10_000_000L
        val first = build(
            StudyPlanSettings.defaults(Plan.id),
            now,
            listOf(activity("activity-123", now + 60_000L)),
        ).notificationCandidates.single { it.type == ReminderNotificationType.PLANNED_ACTIVITY }
        val second = build(
            StudyPlanSettings.defaults(Plan.id),
            now,
            listOf(activity("activity-123", now + 60_000L)),
        ).notificationCandidates.single { it.type == ReminderNotificationType.PLANNED_ACTIVITY }

        assertEquals(first.occurrenceId, second.occurrenceId)
        assertTrue(first.occurrenceId.contains("activity-123"))
    }

    private fun build(
        settings: StudyPlanSettings,
        now: Long,
        activities: List<PlannedActivity>,
        dueCount: Int = 0,
        delivered: Set<String> = emptySet(),
        persistedScheduledAt: Map<String, Long> = emptyMap(),
        dueAtEpochMillis: Long = now,
    ): StudyReminderPlan = StudyReminderPlanner.buildPlan(
        StudyReminderInput(
            plan = Plan,
            settings = settings,
            schedule = StudySchedule(Plan.id, null, emptyList(), activities),
            reviewQueue = ReviewQueue(
                due = (0 until dueCount).map { index -> dueReview(index, dueAtEpochMillis) },
                upcoming = emptyList(),
                completedToday = emptyList(),
                flashcards = emptyList(),
                errors = emptyList(),
            ),
            nowEpochMillis = now,
            minuteOfDay = Calendar.getInstance().apply { timeInMillis = now }.run {
                get(Calendar.HOUR_OF_DAY) * 60 + get(Calendar.MINUTE)
            },
            apiLevel = 37,
            notificationPermissionGranted = true,
            deliveredOccurrenceIds = delivered,
            persistedScheduledAtByOccurrenceId = persistedScheduledAt,
        ),
    )

    private fun activity(id: String, scheduledAt: Long) = PlannedActivity(
        id = PlannedActivityId(id),
        studyPlanId = Plan.id,
        subjectId = null,
        topicId = null,
        materialId = null,
        videoResourceId = null,
        type = StudyActivityType.THEORY,
        title = id,
        scheduledStartEpochMillis = scheduledAt,
        plannedDurationMinutes = 45,
        status = PlannedActivityStatus.PLANNED,
        locked = false,
        completedAtEpochMillis = null,
    )

    private fun missedActivity(id: String, scheduledAt: Long) = activity(id, scheduledAt).copy(
        status = PlannedActivityStatus.MISSED,
    )

    private fun dueReview(index: Int, now: Long) = ReviewItem(
        id = ReviewItemId("due-$index"),
        studyPlanId = Plan.id,
        subjectId = null,
        topicId = null,
        questionId = null,
        flashcardId = null,
        errorEntryId = null,
        method = ReviewMethod.SPACED_REPETITION,
        status = ReviewStatus.DUE,
        dueAtEpochMillis = now - index,
        intervalDays = 0,
        repetitionCount = 0,
        easeFactor = 2.5,
        completedAtEpochMillis = null,
    )

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

    private companion object {
        val Plan = StudyPlan(
            id = StudyPlanId("ov09-plan"),
            name = "OV09",
            description = "",
            type = StudyType.PUBLIC_EXAMINATION,
            status = StudyPlanStatus.ACTIVE,
            examDateIso = null,
            weeklyGoalHours = 10,
            progressPercent = 0,
            studiedMinutesThisWeek = 0,
            questionsAnswered = 0,
            averageAccuracyPercent = 0,
        )
    }
}
