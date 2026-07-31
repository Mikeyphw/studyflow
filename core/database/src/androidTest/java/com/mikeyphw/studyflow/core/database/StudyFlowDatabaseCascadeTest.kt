package com.mikeyphw.studyflow.core.database

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mikeyphw.studyflow.core.database.entity.StudyPlanEntity
import com.mikeyphw.studyflow.core.database.entity.SubjectEntity
import com.mikeyphw.studyflow.core.database.entity.TopicEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudyFlowDatabaseCascadeTest {
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
    fun deletingPlanCascadesToOwnedSubjectsAndTopics() = runBlocking {
        database.studyPlanDao().insert(samplePlan("plan-a"))
        database.studyStructureDao().upsertSubjects(listOf(sampleSubject("subject-a", "plan-a")))
        database.studyStructureDao().upsertTopics(listOf(sampleTopic("topic-a", "plan-a", "subject-a")))

        database.studyPlanDao().deleteById("plan-a")

        assertEquals(0, database.planOwnershipDao().countSubjects("plan-a"))
        assertEquals(0, database.planOwnershipDao().countTopics("plan-a"))
    }

    private fun samplePlan(id: String) = StudyPlanEntity(
        id = id,
        name = "Plan",
        description = "Description",
        type = "FREE_STUDY",
        status = "ACTIVE",
        examDateIso = null,
        weeklyGoalHours = 5,
        progressPercent = 0,
        studiedMinutesThisWeek = 0,
        questionsAnswered = 0,
        averageAccuracyPercent = 0,
        createdAtEpochMillis = 1L,
        updatedAtEpochMillis = 1L,
        trashedAtEpochMillis = null,
    )

    private fun sampleSubject(id: String, planId: String) = SubjectEntity(
        id = id,
        studyPlanId = planId,
        name = "Subject",
        description = "",
        colorArgb = null,
        priority = 1,
        examWeight = 1.0,
        estimatedMinutes = 60,
        progressPercent = 0,
        accuracyPercent = 0,
        status = "NOT_STARTED",
        position = 0,
        archived = false,
    )

    private fun sampleTopic(id: String, planId: String, subjectId: String) = TopicEntity(
        id = id,
        studyPlanId = planId,
        subjectId = subjectId,
        parentTopicId = null,
        name = "Topic",
        description = "",
        priority = 1,
        difficulty = 1,
        examIncidencePercent = null,
        estimatedMinutes = 30,
        progressPercent = 0,
        masteryPercent = 0,
        status = "NOT_STARTED",
        position = 0,
        archived = false,
    )
}
