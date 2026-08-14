package com.mikeyphw.studyflow.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mikeyphw.studyflow.core.database.StudyFlowDatabase
import com.mikeyphw.studyflow.core.database.entity.PlannedActivityEntity
import com.mikeyphw.studyflow.core.database.entity.StudyPlanEntity
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
