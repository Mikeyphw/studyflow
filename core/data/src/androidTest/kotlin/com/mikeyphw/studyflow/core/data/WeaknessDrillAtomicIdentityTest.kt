package com.mikeyphw.studyflow.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mikeyphw.studyflow.core.database.StudyFlowDatabase
import com.mikeyphw.studyflow.core.database.entity.StudyPlanEntity
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyTest
import com.mikeyphw.studyflow.core.model.StudyTestId
import com.mikeyphw.studyflow.core.model.TestMode
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WeaknessDrillAtomicIdentityTest {
    private lateinit var context: Context
    private lateinit var database: StudyFlowDatabase

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, StudyFlowDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        runBlocking { database.studyPlanDao().insert(plan()) }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun submittedDrillHistoryIsNeverReusedForNewDrill() = runBlocking {
        val repository = RoomStudyPracticeRepository(database)
        val key = "q1,q2,q3"
        repository.upsertTest(test("historic", key, submittedAt = 100L))

        val created = repository.createWeaknessDrillIfAbsent(test("fresh", key), key)

        assertEquals(StudyTestId("fresh"), created.id)
        assertNotNull(repository.findTest(StudyPlanId("plan"), StudyTestId("historic")))
        assertNotNull(repository.findTest(StudyPlanId("plan"), StudyTestId("fresh")))
    }

    @Test
    fun secondOpenDrillWithSameKeyJoinsExistingAttempt() = runBlocking {
        val repository = RoomStudyPracticeRepository(database)
        val key = "q1,q2,q3"
        val first = repository.createWeaknessDrillIfAbsent(test("first", key), key)
        val second = repository.createWeaknessDrillIfAbsent(test("second", key), key)

        assertEquals(first.id, second.id)
        assertEquals(StudyTestId("first"), second.id)
        assertEquals(null, repository.findTest(StudyPlanId("plan"), StudyTestId("second")))
    }

    private fun test(id: String, key: String, submittedAt: Long? = null) = StudyTest(
        id = StudyTestId(id),
        studyPlanId = StudyPlanId("plan"),
        title = "Weakness drill",
        mode = TestMode.IMMEDIATE_FEEDBACK,
        timeLimitSeconds = null,
        questionCount = 3,
        answeredCount = if (submittedAt == null) 0 else 3,
        correctCount = if (submittedAt == null) 0 else 2,
        startedAtEpochMillis = if (submittedAt == null) null else 10L,
        submittedAtEpochMillis = submittedAt,
        configurationJson = "questionIds=q1,q2,q3\nweaknessDrillKey=$key",
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
