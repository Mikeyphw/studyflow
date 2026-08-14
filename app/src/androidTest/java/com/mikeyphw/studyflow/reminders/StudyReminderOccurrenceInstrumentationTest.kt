package com.mikeyphw.studyflow.reminders

import android.app.PendingIntent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mikeyphw.studyflow.core.domain.ReminderNotificationType
import com.mikeyphw.studyflow.core.domain.ReviewQueue
import com.mikeyphw.studyflow.core.domain.StudyReminderInput
import com.mikeyphw.studyflow.core.domain.StudyReminderPlanner
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudyReminderOccurrenceInstrumentationTest {
    @Test
    fun occurrenceIdentityIsCollisionSafeAndDeliveryJournalChainsAcrossRestart() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val plan = plan()
        val store = StudyReminderOccurrenceStore(context)
        store.clearPlan(plan.id)
        val now = 10_000_000L
        val first = activity("activity-a", now + 60_000L, plan.id)
        val second = activity("activity-b", now + 120_000L, plan.id)

        val initial = reminderPlan(plan, now, listOf(first, second), delivered = emptySet())
        val firstCandidate = initial.notificationCandidates.single {
            it.type == ReminderNotificationType.PLANNED_ACTIVITY
        }
        store.replaceScheduled(plan.id, listOf(firstCandidate))

        val firstPendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            reminderIntent(context, plan.id, firstCandidate.type, firstCandidate.occurrenceId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val secondIdentity = "planned:${second.id.value}:${second.scheduledStartEpochMillis}"
        val secondPendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            reminderIntent(context, plan.id, ReminderNotificationType.PLANNED_ACTIVITY, secondIdentity),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        try {
            assertNotEquals(firstPendingIntent, secondPendingIntent)
            assertNotEquals(
                firstPendingIntent.intentSender,
                secondPendingIntent.intentSender,
            )
            assertTrue(
                studyReminderPendingIntentIdentityExists(
                    context,
                    plan.id,
                    firstCandidate.type,
                    firstCandidate.occurrenceId,
                ),
            )

            store.markDelivered(plan.id, firstCandidate.occurrenceId)
            val restartedStore = StudyReminderOccurrenceStore(context)
            assertTrue(firstCandidate.occurrenceId in restartedStore.deliveredOccurrenceIds(plan.id))
            val restartedOccurrence = restartedStore.scheduled(plan.id).single()
            assertEquals(firstCandidate.occurrenceId, restartedOccurrence.occurrenceId)
            assertEquals(firstCandidate.scheduledAtEpochMillis, restartedOccurrence.scheduledAtEpochMillis)
            assertEquals(firstCandidate.fireAtEpochMillis, restartedOccurrence.fireAtEpochMillis)

            val chained = reminderPlan(
                plan,
                now,
                listOf(first, second),
                delivered = restartedStore.deliveredOccurrenceIds(plan.id),
            ).notificationCandidates.single { it.type == ReminderNotificationType.PLANNED_ACTIVITY }
            assertEquals("activity-b", chained.content.userLabel)
            assertNotEquals(firstCandidate.occurrenceId, chained.occurrenceId)
        } finally {
            firstPendingIntent.cancel()
            secondPendingIntent.cancel()
            store.clearPlan(plan.id)
        }
    }

    private fun reminderPlan(
        plan: StudyPlan,
        now: Long,
        activities: List<PlannedActivity>,
        delivered: Set<String>,
    ) = StudyReminderPlanner.buildPlan(
        StudyReminderInput(
            plan = plan,
            settings = StudyPlanSettings.defaults(plan.id),
            schedule = StudySchedule(plan.id, null, emptyList(), activities),
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
        ),
    )

    private fun activity(id: String, scheduledAt: Long, planId: StudyPlanId) = PlannedActivity(
        id = PlannedActivityId(id),
        studyPlanId = planId,
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

    private fun plan() = StudyPlan(
        id = StudyPlanId("reminder-instrumentation-plan"),
        name = "Reminder instrumentation",
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
