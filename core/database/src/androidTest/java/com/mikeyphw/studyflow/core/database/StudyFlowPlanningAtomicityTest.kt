package com.mikeyphw.studyflow.core.database

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mikeyphw.studyflow.core.database.entity.PlannedActivityEntity
import com.mikeyphw.studyflow.core.database.entity.StudyPlanEntity
import com.mikeyphw.studyflow.core.database.entity.StudySessionEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudyFlowPlanningAtomicityTest {
    private lateinit var database: StudyFlowDatabase

    @Before
    fun createDatabase() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, StudyFlowDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun staleSessionStatusCannotOverwriteNewerTransition() = runBlocking {
        database.studyPlanDao().insert(samplePlan())
        database.studyPlanningDao().upsertStudySession(sampleSession(status = "RUNNING"))

        assertEquals(
            1,
            database.studyPlanningDao().compareAndSetStudySessionStatus(
                planId = "plan",
                sessionId = "session",
                expectedStatus = "RUNNING",
                newStatus = "PAUSED",
                endedAtEpochMillis = 2L,
                grossDurationSeconds = 1L,
                pauseDurationSeconds = 0L,
            ),
        )
        assertEquals(
            0,
            database.studyPlanningDao().compareAndSetStudySessionStatus(
                planId = "plan",
                sessionId = "session",
                expectedStatus = "RUNNING",
                newStatus = "CANCELLED",
                endedAtEpochMillis = 3L,
                grossDurationSeconds = 2L,
                pauseDurationSeconds = 0L,
            ),
        )
        assertEquals("PAUSED", database.studyPlanningDao().findStudySession("plan", "session")?.status)
    }

    @Test
    fun noteUpdateCannotReopenOrMutateTerminalSession() = runBlocking {
        database.studyPlanDao().insert(samplePlan())
        database.studyPlanningDao().upsertStudySession(sampleSession(status = "PAUSED"))

        assertEquals(
            1,
            database.studyPlanningDao().updateActiveStudySessionNotes("plan", "session", "Draft note"),
        )
        assertEquals(
            1,
            database.studyPlanningDao().completeStudySessionIfStatus(
                planId = "plan",
                sessionId = "session",
                expectedStatus = "PAUSED",
                endedAtEpochMillis = 3L,
                grossDurationSeconds = 2L,
                pauseDurationSeconds = 1L,
                pagesCompleted = 1,
                questionsAnswered = 1,
                correctAnswers = 1,
                concentrationRating = 4,
                difficultyRating = 3,
                notes = "Final note",
            ),
        )
        assertEquals(
            0,
            database.studyPlanningDao().updateActiveStudySessionNotes("plan", "session", "Stale autosave"),
        )
        val terminal = database.studyPlanningDao().findStudySession("plan", "session")
        assertEquals("COMPLETED", terminal?.status)
        assertEquals("Final note", terminal?.notes)
    }

    @Test
    fun stalePlannedActivitySnapshotCannotOverwriteNewerState() = runBlocking {
        database.studyPlanDao().insert(samplePlan())
        database.studyPlanningDao().upsertPlannedActivity(sampleActivity())

        assertEquals(
            1,
            database.studyPlanningDao().compareAndSetPlannedActivity(
                planId = "plan",
                activityId = "activity",
                expectedStatus = "PLANNED",
                expectedStartEpochMillis = 10L,
                expectedDurationMinutes = 25,
                newStatus = "IN_PROGRESS",
                newStartEpochMillis = 10L,
                newDurationMinutes = 25,
                newCompletedAtEpochMillis = null,
            ),
        )
        assertEquals(
            0,
            database.studyPlanningDao().compareAndSetPlannedActivity(
                planId = "plan",
                activityId = "activity",
                expectedStatus = "PLANNED",
                expectedStartEpochMillis = 10L,
                expectedDurationMinutes = 25,
                newStatus = "MISSED",
                newStartEpochMillis = 10L,
                newDurationMinutes = 25,
                newCompletedAtEpochMillis = null,
            ),
        )
        assertEquals(
            "IN_PROGRESS",
            database.studyPlanningDao().findPlannedActivity("plan", "activity")?.status,
        )
    }

    private fun samplePlan() = StudyPlanEntity(
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

    private fun sampleSession(status: String) = StudySessionEntity(
        id = "session",
        studyPlanId = "plan",
        plannedActivityId = null,
        subjectId = null,
        topicId = null,
        materialId = null,
        videoResourceId = null,
        type = "CUSTOM",
        status = status,
        startedAtEpochMillis = 1L,
        endedAtEpochMillis = if (status == "PAUSED") 2L else null,
        grossDurationSeconds = 1L,
        pauseDurationSeconds = 0L,
        pagesCompleted = 0,
        questionsAnswered = 0,
        correctAnswers = 0,
        concentrationRating = null,
        difficultyRating = null,
        notes = "",
        targetDurationMinutes = 25,
    )

    private fun sampleActivity() = PlannedActivityEntity(
        id = "activity",
        studyPlanId = "plan",
        subjectId = null,
        topicId = null,
        materialId = null,
        videoResourceId = null,
        type = "CUSTOM",
        title = "Block",
        scheduledStartEpochMillis = 10L,
        plannedDurationMinutes = 25,
        status = "PLANNED",
        locked = false,
        completedAtEpochMillis = null,
    )
}
