package com.mikeyphw.studyflow.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mikeyphw.studyflow.core.database.StudyFlowDatabase
import com.mikeyphw.studyflow.core.database.entity.PlannedActivityEntity
import com.mikeyphw.studyflow.core.database.entity.StudyPlanEntity
import com.mikeyphw.studyflow.core.domain.PlannedActivityRebalanceMutation
import com.mikeyphw.studyflow.core.model.PlannedActivity
import com.mikeyphw.studyflow.core.model.PlannedActivityId
import com.mikeyphw.studyflow.core.model.PlannedActivityStatus
import com.mikeyphw.studyflow.core.model.StudyActivityType
import com.mikeyphw.studyflow.core.model.StudyPlanId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudyScheduleMissedRebalanceAtomicTest {
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
    fun scheduleCrudCannotCreateInProgressActivityWithoutStudySession() = runBlocking {
        database.studyPlanDao().insert(plan())
        val repository = RoomStudyScheduleRepository(database)

        val result = runCatching {
            repository.upsertPlannedActivity(model("detached", PlannedActivityStatus.IN_PROGRESS))
        }

        check(result.isFailure)
        assertEquals(null, database.studyPlanningDao().findPlannedActivity("plan", "detached"))
    }

    @Test
    fun scheduleStatusCrudCannotEnterInProgressOutsideStudySessionStateMachine() = runBlocking {
        database.studyPlanDao().insert(plan())
        database.studyPlanningDao().upsertPlannedActivity(entity("planned", PlannedActivityStatus.PLANNED))
        val repository = RoomStudyScheduleRepository(database)

        val result = runCatching {
            repository.updatePlannedActivityStatus(
                planId = StudyPlanId("plan"),
                activityId = PlannedActivityId("planned"),
                status = PlannedActivityStatus.IN_PROGRESS,
                completedAtEpochMillis = null,
            )
        }

        check(result.isFailure)
        assertEquals(
            PlannedActivityStatus.PLANNED.name,
            database.studyPlanningDao().findPlannedActivity("plan", "planned")?.status,
        )
    }

    @Test
    fun staleSecondMutationRollsBackEarlierRebalanceMutation() = runBlocking {
        database.studyPlanDao().insert(plan())
        database.studyPlanningDao().upsertPlannedActivity(entity("first", PlannedActivityStatus.MISSED))
        database.studyPlanningDao().upsertPlannedActivity(entity("second", PlannedActivityStatus.COMPLETED))
        val repository = RoomStudyScheduleRepository(database)

        val firstExpected = model("first", PlannedActivityStatus.MISSED)
        val secondStaleExpected = model("second", PlannedActivityStatus.MISSED)
        val result = runCatching {
            repository.applyMissedRebalance(
                StudyPlanId("plan"),
                listOf(
                    PlannedActivityRebalanceMutation(
                        expected = firstExpected,
                        proposed = firstExpected.copy(status = PlannedActivityStatus.SKIPPED),
                    ),
                    PlannedActivityRebalanceMutation(
                        expected = secondStaleExpected,
                        proposed = secondStaleExpected.copy(status = PlannedActivityStatus.SKIPPED),
                    ),
                ),
            )
        }

        check(result.isFailure)
        assertEquals(
            PlannedActivityStatus.MISSED.name,
            database.studyPlanningDao().findPlannedActivity("plan", "first")?.status,
        )
        assertEquals(
            PlannedActivityStatus.COMPLETED.name,
            database.studyPlanningDao().findPlannedActivity("plan", "second")?.status,
        )
    }

    private fun model(id: String, status: PlannedActivityStatus) = PlannedActivity(
        id = PlannedActivityId(id),
        studyPlanId = StudyPlanId("plan"),
        subjectId = null,
        topicId = null,
        materialId = null,
        videoResourceId = null,
        type = StudyActivityType.THEORY,
        title = id,
        scheduledStartEpochMillis = 1_000L,
        plannedDurationMinutes = 25,
        status = status,
        locked = false,
        completedAtEpochMillis = null,
    )

    private fun entity(id: String, status: PlannedActivityStatus) = PlannedActivityEntity(
        id = id,
        studyPlanId = "plan",
        subjectId = null,
        topicId = null,
        materialId = null,
        videoResourceId = null,
        type = StudyActivityType.THEORY.name,
        title = id,
        scheduledStartEpochMillis = 1_000L,
        plannedDurationMinutes = 25,
        status = status.name,
        locked = false,
        completedAtEpochMillis = null,
    )

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
