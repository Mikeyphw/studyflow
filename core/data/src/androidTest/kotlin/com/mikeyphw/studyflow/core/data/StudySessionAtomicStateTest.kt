package com.mikeyphw.studyflow.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mikeyphw.studyflow.core.database.StudyFlowDatabase
import com.mikeyphw.studyflow.core.database.entity.PlannedActivityEntity
import com.mikeyphw.studyflow.core.database.entity.StudyPlanEntity
import com.mikeyphw.studyflow.core.domain.ManageStudySession
import com.mikeyphw.studyflow.core.model.PlannedActivity
import com.mikeyphw.studyflow.core.model.PlannedActivityId
import com.mikeyphw.studyflow.core.model.PlannedActivityStatus
import com.mikeyphw.studyflow.core.model.StudyActivityType
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudySession
import com.mikeyphw.studyflow.core.model.StudySessionId
import com.mikeyphw.studyflow.core.model.StudySessionStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudySessionAtomicStateTest {
    private lateinit var context: Context
    private lateinit var database: StudyFlowDatabase

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, StudyFlowDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun linkedSessionCannotClaimDifferentActivityOwnershipMetadata() = runBlocking {
        val planId = StudyPlanId("plan")
        val activityId = PlannedActivityId("activity")
        database.studyPlanDao().insert(plan())
        database.studyPlanningDao().upsertPlannedActivity(
            PlannedActivityEntity(
                id = activityId.value,
                studyPlanId = planId.value,
                subjectId = null,
                topicId = null,
                materialId = null,
                videoResourceId = null,
                type = StudyActivityType.THEORY.name,
                title = "Owned activity",
                scheduledStartEpochMillis = 1_000L,
                plannedDurationMinutes = 25,
                status = PlannedActivityStatus.PLANNED.name,
                locked = false,
                completedAtEpochMillis = null,
            ),
        )
        val repository = RoomStudySessionRepository(database)

        val result = runCatching {
            repository.createSession(
                session = StudySession(
                    id = StudySessionId("mismatched-session"),
                    studyPlanId = planId,
                    plannedActivityId = activityId,
                    subjectId = null,
                    topicId = null,
                    materialId = null,
                    videoResourceId = null,
                    type = StudyActivityType.QUESTIONS,
                    status = StudySessionStatus.RUNNING,
                    startedAtEpochMillis = 2_000L,
                    endedAtEpochMillis = null,
                    grossDurationSeconds = 0L,
                    pauseDurationSeconds = 0L,
                    pagesCompleted = 0,
                    questionsAnswered = 0,
                    correctAnswers = 0,
                    concentrationRating = null,
                    difficultyRating = null,
                    notes = "Mismatch",
                    targetDurationMinutes = 25,
                ),
                linkedActivityExpectedStatus = PlannedActivityStatus.PLANNED,
            )
        }

        check(result.isFailure)
        assertEquals(
            PlannedActivityStatus.PLANNED.name,
            database.studyPlanningDao().findPlannedActivity(planId.value, activityId.value)?.status,
        )
        assertNull(database.studyPlanningDao().findStudySession(planId.value, "mismatched-session"))
    }

    @Test
    fun cancellingSessionRestoresLinkedActivityToItsOriginalMissedState() = runBlocking {
        val planId = StudyPlanId("plan")
        val activityId = PlannedActivityId("activity")
        val sessionId = StudySessionId("session")
        database.studyPlanDao().insert(plan())
        database.studyPlanningDao().upsertPlannedActivity(
            PlannedActivityEntity(
                id = activityId.value,
                studyPlanId = planId.value,
                subjectId = null,
                topicId = null,
                materialId = null,
                videoResourceId = null,
                type = StudyActivityType.THEORY.name,
                title = "Missed activity",
                scheduledStartEpochMillis = 1_000L,
                plannedDurationMinutes = 25,
                status = PlannedActivityStatus.MISSED.name,
                locked = false,
                completedAtEpochMillis = null,
            ),
        )
        val repository = RoomStudySessionRepository(database)
        val running = repository.createSession(
            session = StudySession(
                id = sessionId,
                studyPlanId = planId,
                plannedActivityId = activityId,
                subjectId = null,
                topicId = null,
                materialId = null,
                videoResourceId = null,
                type = StudyActivityType.THEORY,
                status = StudySessionStatus.RUNNING,
                startedAtEpochMillis = 2_000L,
                endedAtEpochMillis = null,
                grossDurationSeconds = 0L,
                pauseDurationSeconds = 0L,
                pagesCompleted = 0,
                questionsAnswered = 0,
                correctAnswers = 0,
                concentrationRating = null,
                difficultyRating = null,
                notes = "Missed activity",
                targetDurationMinutes = 25,
            ),
            linkedActivityExpectedStatus = PlannedActivityStatus.MISSED,
        )

        repository.transitionSession(
            expectedStatus = StudySessionStatus.RUNNING,
            session = running.copy(
                status = StudySessionStatus.CANCELLED,
                endedAtEpochMillis = 3_000L,
                grossDurationSeconds = 1L,
            ),
            linkedActivityExpectedStatus = PlannedActivityStatus.IN_PROGRESS,
            linkedActivityStatus = PlannedActivityStatus.PLANNED,
            linkedActivityCompletedAtEpochMillis = null,
        )

        assertEquals(
            PlannedActivityStatus.MISSED.name,
            database.studyPlanningDao().findPlannedActivity(planId.value, activityId.value)?.status,
        )
        assertNull(database.databaseMetadataDao().findValue("session_source_activity_status:${sessionId.value}"))
    }

    @Test
    fun quickSessionCompletesAndPersistsWithoutPlannedActivity() = runBlocking {
        val planId = StudyPlanId("plan")
        database.studyPlanDao().insert(plan())
        val repository = RoomStudySessionRepository(database)
        var now = 2_000L
        val manager = ManageStudySession(
            repository, clock = { now }, monotonicClockMillis = { now },
        )
        val sessionId = StudySessionId("quick-complete")
        val started = manager.startQuickSession(
            sessionId = sessionId,
            studyPlanId = planId,
            title = "Quick theory",
            type = StudyActivityType.THEORY,
            targetDurationMinutes = 25,
        )
        assertNull(started.plannedActivityId)
        now = 62_000L
        val completed = manager.complete(
            planId = planId,
            sessionId = sessionId,
            pagesCompleted = 3,
            questionsAnswered = 5,
            correctAnswers = 4,
            concentrationRating = 5,
            difficultyRating = 2,
            notes = "Completed quick session",
        )
        assertEquals(StudySessionStatus.COMPLETED, completed.status)
        val persisted = requireNotNull(
            database.studyPlanningDao().findStudySession(planId.value, sessionId.value),
        )
        assertEquals(StudySessionStatus.COMPLETED.name, persisted.status)
        assertEquals("Completed quick session", persisted.notes)
        assertEquals(3, persisted.pagesCompleted)
        assertEquals(5, persisted.questionsAnswered)
        assertEquals(4, persisted.correctAnswers)
        assertEquals(62_000L, persisted.endedAtEpochMillis)
        assertNull(repository.findActiveSession(planId))
        // A committed terminal quick session must release active-session ownership.
        val next = manager.startQuickSession(
            sessionId = StudySessionId("quick-next"),
            studyPlanId = planId,
            title = "Next quick session",
            type = StudyActivityType.QUESTIONS,
            targetDurationMinutes = 25,
        )
        assertEquals(StudySessionStatus.RUNNING, next.status)
    }

    @Test
    fun quickSessionCancelsAndPersistsWithoutPlannedActivity() = runBlocking {
        val planId = StudyPlanId("plan")
        database.studyPlanDao().insert(plan())
        val repository = RoomStudySessionRepository(database)
        var now = 2_000L
        val manager = ManageStudySession(
            repository, clock = { now }, monotonicClockMillis = { now },
        )
        val sessionId = StudySessionId("quick-cancel")
        val started = manager.startQuickSession(
            sessionId = sessionId,
            studyPlanId = planId,
            title = "Quick cancellation",
            type = StudyActivityType.THEORY,
            targetDurationMinutes = 25,
        )
        assertNull(started.plannedActivityId)
        now = 32_000L
        val cancelled = manager.cancel(planId, sessionId)
        assertEquals(StudySessionStatus.CANCELLED, cancelled.status)
        val persisted = requireNotNull(
            database.studyPlanningDao().findStudySession(planId.value, sessionId.value),
        )
        assertEquals(StudySessionStatus.CANCELLED.name, persisted.status)
        assertEquals(32_000L, persisted.endedAtEpochMillis)
        assertNull(repository.findActiveSession(planId))
    }

    @Test
    fun linkedSessionCompletionStillUpdatesPlannedActivity() = runBlocking {
        val planId = StudyPlanId("plan")
        val activityId = PlannedActivityId("linked-complete-activity")
        database.studyPlanDao().insert(plan())
        database.studyPlanningDao().upsertPlannedActivity(
            PlannedActivityEntity(
                id = activityId.value,
                studyPlanId = planId.value,
                subjectId = null,
                topicId = null,
                materialId = null,
                videoResourceId = null,
                type = StudyActivityType.THEORY.name,
                title = "Scheduled theory",
                scheduledStartEpochMillis = 1_000L,
                plannedDurationMinutes = 25,
                status = PlannedActivityStatus.PLANNED.name,
                locked = false,
                completedAtEpochMillis = null,
            ),
        )
        var now = 2_000L
        val repository = RoomStudySessionRepository(database)
        val manager = ManageStudySession(
            repository, clock = { now }, monotonicClockMillis = { now },
        )
        val sessionId = StudySessionId("linked-complete")
        manager.startFromActivity(
            sessionId,
            PlannedActivity(
                id = activityId,
                studyPlanId = planId,
                subjectId = null,
                topicId = null,
                materialId = null,
                videoResourceId = null,
                type = StudyActivityType.THEORY,
                title = "Scheduled theory",
                scheduledStartEpochMillis = 1_000L,
                plannedDurationMinutes = 25,
                status = PlannedActivityStatus.PLANNED,
                locked = false,
                completedAtEpochMillis = null,
            ),
        )
        assertEquals(
            PlannedActivityStatus.IN_PROGRESS.name,
            database.studyPlanningDao().findPlannedActivity(planId.value, activityId.value)?.status,
        )
        now = 62_000L
        val completed = manager.complete(
            planId = planId,
            sessionId = sessionId,
            pagesCompleted = 1,
            questionsAnswered = 0,
            correctAnswers = 0,
            concentrationRating = null,
            difficultyRating = null,
            notes = "Scheduled session completed",
        )
        assertEquals(StudySessionStatus.COMPLETED, completed.status)
        assertEquals(
            StudySessionStatus.COMPLETED.name,
            database.studyPlanningDao().findStudySession(planId.value, sessionId.value)?.status,
        )
        val updatedActivity = requireNotNull(
            database.studyPlanningDao().findPlannedActivity(planId.value, activityId.value),
        )
        assertEquals(PlannedActivityStatus.COMPLETED.name, updatedActivity.status)
        assertEquals(completed.endedAtEpochMillis, updatedActivity.completedAtEpochMillis)
        assertNull(repository.findActiveSession(planId))
    }

    private fun plan() = StudyPlanEntity(
        id = "plan",
        name = "Plan",
        description = "",
        type = "FREE_STUDY",
        status = "ACTIVE",
        examDateIso = null,
        weeklyGoalHours = 10,
        progressPercent = 0,
        studiedMinutesThisWeek = 0,
        questionsAnswered = 0,
        averageAccuracyPercent = 0,
        createdAtEpochMillis = 1L,
        updatedAtEpochMillis = 1L,
        trashedAtEpochMillis = null,
    )
}
