package com.mikeyphw.studyflow.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mikeyphw.studyflow.core.database.entity.AnnexEntity
import com.mikeyphw.studyflow.core.database.entity.MockExamEntity
import com.mikeyphw.studyflow.core.database.entity.PlannedActivityEntity
import com.mikeyphw.studyflow.core.database.entity.QuestionEntity
import com.mikeyphw.studyflow.core.database.entity.StudyPlanEntity
import com.mikeyphw.studyflow.core.database.entity.StudyTestEntity
import com.mikeyphw.studyflow.core.portability.PracticeConfigurationReferences
import com.mikeyphw.studyflow.core.portability.StudyPlanExportOptions
import com.mikeyphw.studyflow.core.portability.StudyPlanImportStrategy
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudyPlanPortabilityOwnershipTest {
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
    fun createAsNewRemapsQuestionIdsEmbeddedInTestAndMockConfigurations() = runBlocking {
        seedPracticeGraph()
        val store = StudyPlanGraphPortabilityStore(database)
        val payload = store.exportPayload(
            planId = PLAN_ID,
            options = StudyPlanExportOptions(),
            applicationVersion = "test",
            exportedAtEpochMillis = 86_400_000L,
        )

        store.importPayload(
            payload = payload,
            strategy = StudyPlanImportStrategy.CREATE_AS_NEW,
            targetPlanId = RESTORED_PLAN_ID,
            importedAtEpochMillis = 172_800_000L,
        )

        val expectedQuestionId = "$RESTORED_PLAN_ID:$QUESTION_ID"
        val restoredTest = requireNotNull(database.studyPracticeDao().findTest(RESTORED_PLAN_ID,
            "$RESTORED_PLAN_ID:$TEST_ID"))
        val restoredMock = requireNotNull(database.studyPracticeDao().findMockExam(RESTORED_PLAN_ID,
            "$RESTORED_PLAN_ID:$MOCK_ID"))
        assertEquals(setOf(expectedQuestionId), PracticeConfigurationReferences
            .referencedQuestionIds(restoredTest.configurationJson))
        assertEquals(setOf(expectedQuestionId), PracticeConfigurationReferences
            .referencedQuestionIds(restoredMock.configurationJson))
    }

    @Test
    fun exportRejectsLiveExecutionState() = runBlocking {
        database.studyPlanDao().insert(plan())
        database.studyPlanningDao().upsertPlannedActivity(
            PlannedActivityEntity(
                id = "active-activity",
                studyPlanId = PLAN_ID,
                subjectId = null,
                topicId = null,
                materialId = null,
                videoResourceId = null,
                type = "THEORY",
                title = "Active",
                scheduledStartEpochMillis = 1_000L,
                plannedDurationMinutes = 25,
                status = "IN_PROGRESS",
                locked = true,
                completedAtEpochMillis = null,
            ),
        )
        val store = StudyPlanGraphPortabilityStore(database)

        val result = runCatching {
            store.exportPayload(
                planId = PLAN_ID,
                options = StudyPlanExportOptions(),
                applicationVersion = "test",
                exportedAtEpochMillis = 86_400_000L,
            )
        }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("in-progress activity"))
    }

    @Test
    fun metadataPrivateExportHidesAnnexFilenameAndEmbeddedQuestionIds() = runBlocking {
        seedPracticeGraph()
        database.studyLibraryDao().upsertAnnex(
            AnnexEntity(
                id = ANNEX_ID,
                studyPlanId = PLAN_ID,
                subjectId = null,
                topicId = null,
                materialId = null,
                displayName = "secret-original-name.pdf",
                mimeType = "application/pdf",
                byteSize = 10L,
                checksumSha256 = "0".repeat(64),
                storageMode = "IMPORTED_COPY",
                storageReference = "shareable-annexes/$PLAN_ID/$ANNEX_ID/secret-original-name.pdf",
                persistedPermission = false,
                createdAtEpochMillis = 1L,
                updatedAtEpochMillis = 1L,
            ),
        )
        val store = StudyPlanGraphPortabilityStore(database)
        val payload = store.exportPayload(
            planId = PLAN_ID,
            options = StudyPlanExportOptions(metadataPrivacyMode = true),
            applicationVersion = "9.9.9",
            exportedAtEpochMillis = 172_800_123L,
        )

        val privateQuestionId = payload.tables.single { it.name == "questions" }.rows.single().values
            .getValue("id").value!!
        assertFalse(privateQuestionId.contains(QUESTION_ID))
        listOf("study_tests", "mock_exams").forEach { tableName ->
            val configuration = payload.tables.single { it.name == tableName }.rows.single().values
                .getValue("configurationJson").value.orEmpty()
            assertEquals(setOf(privateQuestionId), PracticeConfigurationReferences.referencedQuestionIds(configuration))
            assertFalse(configuration.contains(QUESTION_ID))
        }
        val annex = payload.tables.single { it.name == "annexes" }.rows.single()
        assertEquals("private-annex.bin", annex.values.getValue("displayName").value)
        assertFalse(annex.values.values.any { value -> value.value?.contains("secret-original-name.pdf") == true })
        assertTrue(payload.manifest.sourceStudyPlanId.startsWith("private-"))
    }

    private suspend fun seedPracticeGraph() {
        database.studyPlanDao().insert(plan())
        database.studyPracticeDao().upsertQuestion(
            QuestionEntity(
                id = QUESTION_ID,
                studyPlanId = PLAN_ID,
                subjectId = null,
                topicId = null,
                type = "MULTIPLE_CHOICE",
                statement = "Question?",
                optionsJson = "[\"Correct\",\"Wrong\"]",
                correctAnswer = "Correct",
                explanation = "",
                examBoard = null,
                source = null,
                year = null,
                difficulty = 2,
                favorite = false,
                disputed = false,
                tagsJson = "[]",
                createdAtEpochMillis = 1L,
                updatedAtEpochMillis = 1L,
            ),
        )
        val configuration = "questionIds=$QUESTION_ID\nanswer=cTE|Q29ycmVjdA|Q29ycmVjdA|true|12|42"
        database.studyPracticeDao().upsertTest(
            StudyTestEntity(
                id = TEST_ID,
                studyPlanId = PLAN_ID,
                title = "Practice",
                mode = "PRACTICE",
                timeLimitSeconds = null,
                questionCount = 1,
                answeredCount = 1,
                correctCount = 1,
                startedAtEpochMillis = 1L,
                submittedAtEpochMillis = 2L,
                configurationJson = configuration,
            ),
        )
        database.studyPracticeDao().upsertMockExam(
            MockExamEntity(
                id = MOCK_ID,
                studyPlanId = PLAN_ID,
                title = "Mock",
                mode = "EXAM",
                timeLimitSeconds = 3600L,
                questionCount = 1,
                answeredCount = 1,
                correctCount = 1,
                scorePercent = 100.0,
                negativeMarking = 0.0,
                minimumScorePercent = 50,
                startedAtEpochMillis = 1L,
                submittedAtEpochMillis = 2L,
                configurationJson = configuration,
            ),
        )
    }

    private fun plan() = StudyPlanEntity(
        id = PLAN_ID,
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

    private companion object {
        const val PLAN_ID = "plan"
        const val RESTORED_PLAN_ID = "restored"
        const val QUESTION_ID = "q1"
        const val TEST_ID = "test1"
        const val MOCK_ID = "mock1"
        const val ANNEX_ID = "annex1"
    }
}
