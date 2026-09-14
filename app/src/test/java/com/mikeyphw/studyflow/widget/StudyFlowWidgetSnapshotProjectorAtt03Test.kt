package com.mikeyphw.studyflow.widget

import com.mikeyphw.studyflow.core.domain.ReviewQueue
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
import com.mikeyphw.studyflow.core.model.StudyPlanStatus
import com.mikeyphw.studyflow.core.model.StudySchedule
import com.mikeyphw.studyflow.core.model.StudySession
import com.mikeyphw.studyflow.core.model.StudySessionId
import com.mikeyphw.studyflow.core.model.StudySessionStatus
import com.mikeyphw.studyflow.core.model.StudyType
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowWidgetSnapshotProjectorAtt03Test {
    private val zone = ZoneId.of("UTC")
    private val now = Instant.parse("2026-09-11T12:00:00Z").toEpochMilli()

    @Test
    fun projectsNextReviewsTodayAndLiveSessionWithMinuteFreshness() {
        val snapshot = StudyFlowWidgetSnapshotProjector.project(
            plan = plan(),
            schedule = StudySchedule(
                studyPlanId = StudyPlanId("plan"),
                strategy = null,
                scheduleRules = emptyList(),
                plannedActivities = listOf(activity("next", now + 2 * 60 * 60 * 1_000L)),
            ),
            reviewQueue = ReviewQueue(
                due = listOf(review("due", now)),
                upcoming = listOf(review("upcoming", now + 4 * 60 * 60 * 1_000L)),
                completedToday = emptyList(),
                flashcards = emptyList(),
                errors = emptyList(),
            ),
            activeSession = session(),
            liveActiveSeconds = 30 * 60L,
            nowEpochMillis = now,
            zoneId = zone,
        )

        assertEquals("next", snapshot.nextActivityId)
        assertEquals(1, snapshot.plannedTodayCount)
        assertEquals(1, snapshot.dueReviewCount)
        assertEquals(1, snapshot.upcomingReviewCount)
        assertEquals("session", snapshot.activeSessionId)
        assertEquals(now + 60_000L, snapshot.nextRefreshEpochMillis)
    }

    @Test
    fun refreshBoundaryFallsBackToFutureDomainBoundaryWithoutActiveSession() {
        val nextStart = now + 90 * 60 * 1_000L
        val snapshot = StudyFlowWidgetSnapshotProjector.project(
            plan = plan(),
            schedule = StudySchedule(
                studyPlanId = StudyPlanId("plan"),
                strategy = null,
                scheduleRules = emptyList(),
                plannedActivities = listOf(activity("next", nextStart)),
            ),
            reviewQueue = ReviewQueue(emptyList(), emptyList(), emptyList(), emptyList(), emptyList()),
            activeSession = null,
            liveActiveSeconds = null,
            nowEpochMillis = now,
            zoneId = zone,
        )

        assertEquals(nextStart, snapshot.nextRefreshEpochMillis)
        assertTrue(snapshot.nextRefreshEpochMillis > now)
    }


    @Test
    fun keepsRecentlyStartedPlannedBlockActionableAndRechecksIt() {
        val startedThirtyMinutesAgo = now - 30 * 60 * 1_000L
        val snapshot = StudyFlowWidgetSnapshotProjector.project(
            plan = plan(),
            schedule = StudySchedule(
                studyPlanId = StudyPlanId("plan"),
                strategy = null,
                scheduleRules = emptyList(),
                plannedActivities = listOf(activity("current", startedThirtyMinutesAgo)),
            ),
            reviewQueue = ReviewQueue(emptyList(), emptyList(), emptyList(), emptyList(), emptyList()),
            activeSession = null,
            liveActiveSeconds = null,
            nowEpochMillis = now,
            zoneId = zone,
        )

        assertEquals("current", snapshot.nextActivityId)
        assertEquals(now + 15 * 60 * 1_000L, snapshot.nextRefreshEpochMillis)
    }


    @Test
    fun keepsSubMinutePlannedBoundaryForExactFreshnessScheduler() {
        val nextStart = now + 30_000L
        val snapshot = StudyFlowWidgetSnapshotProjector.project(
            plan = plan(),
            schedule = StudySchedule(
                studyPlanId = StudyPlanId("plan"),
                strategy = null,
                scheduleRules = emptyList(),
                plannedActivities = listOf(activity("soon", nextStart)),
            ),
            reviewQueue = ReviewQueue(emptyList(), emptyList(), emptyList(), emptyList(), emptyList()),
            activeSession = null,
            liveActiveSeconds = null,
            nowEpochMillis = now,
            zoneId = zone,
        )

        assertEquals(nextStart, snapshot.nextRefreshEpochMillis)
    }

    private fun plan() = StudyPlan(
        id = StudyPlanId("plan"),
        name = "ATT03 plan",
        description = "",
        type = StudyType.FREE_STUDY,
        status = StudyPlanStatus.ACTIVE,
        examDateIso = null,
        weeklyGoalHours = 10,
        progressPercent = 42,
        studiedMinutesThisWeek = 120,
        questionsAnswered = 20,
        averageAccuracyPercent = 80,
    )

    private fun activity(id: String, startsAt: Long) = PlannedActivity(
        id = PlannedActivityId(id),
        studyPlanId = StudyPlanId("plan"),
        subjectId = null,
        topicId = null,
        materialId = null,
        videoResourceId = null,
        type = StudyActivityType.THEORY,
        title = "Mathematics",
        scheduledStartEpochMillis = startsAt,
        plannedDurationMinutes = 60,
        status = PlannedActivityStatus.PLANNED,
        locked = false,
        completedAtEpochMillis = null,
    )

    private fun review(id: String, dueAt: Long) = ReviewItem(
        id = ReviewItemId(id),
        studyPlanId = StudyPlanId("plan"),
        subjectId = null,
        topicId = null,
        questionId = null,
        flashcardId = null,
        errorEntryId = null,
        method = ReviewMethod.SPACED_REPETITION,
        status = ReviewStatus.DUE,
        dueAtEpochMillis = dueAt,
        intervalDays = 1,
        repetitionCount = 1,
        easeFactor = 2.5,
        completedAtEpochMillis = null,
    )

    private fun session() = StudySession(
        id = StudySessionId("session"),
        studyPlanId = StudyPlanId("plan"),
        plannedActivityId = PlannedActivityId("next"),
        subjectId = null,
        topicId = null,
        materialId = null,
        videoResourceId = null,
        type = StudyActivityType.THEORY,
        status = StudySessionStatus.RUNNING,
        startedAtEpochMillis = now - 30 * 60 * 1_000L,
        endedAtEpochMillis = null,
        grossDurationSeconds = 30 * 60L,
        pauseDurationSeconds = 0,
        pagesCompleted = 0,
        questionsAnswered = 0,
        correctAnswers = 0,
        concentrationRating = null,
        difficultyRating = null,
        notes = "Mathematics",
        targetDurationMinutes = 60,
    )
}
