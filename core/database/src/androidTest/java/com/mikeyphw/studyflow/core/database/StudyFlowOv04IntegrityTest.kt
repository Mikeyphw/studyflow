package com.mikeyphw.studyflow.core.database

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mikeyphw.studyflow.core.database.entity.StudyPlanEntity
import com.mikeyphw.studyflow.core.database.entity.StudySessionEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudyFlowOv04IntegrityTest {
    private lateinit var database: StudyFlowDatabase
    private lateinit var sqlite: SupportSQLiteDatabase

    @Before
    fun createDatabase() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, StudyFlowDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        sqlite = database.openHelper.writableDatabase
        StudyFlowIntegrity.install(sqlite)
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun singleActiveSessionIsGlobalAcrossPlans() = runBlocking {
        database.studyPlanDao().insert(plan("a"))
        database.studyPlanDao().insert(plan("b"))
        database.studyPlanningDao().upsertStudySession(session("a", "sa", "RUNNING"))

        val rejected = runCatching {
            database.studyPlanningDao().upsertStudySession(session("b", "sb", "PAUSED"))
        }.isFailure
        assertTrue(rejected)
    }

    @Test
    fun modernTablesAdvanceGraphRevision() = runBlocking {
        database.studyPlanDao().insert(plan("a"))
        val before = revision()
        sqlite.execSQL(
            "INSERT INTO programming_course_profiles " +
                "(id,studyPlanId,primaryLanguage,level,repositoryUri,workspaceRelativePath,buildSystem," +
                "packageManager,tagsJson,createdAtEpochMillis,updatedAtEpochMillis) " +
                "VALUES ('profile','a','Kotlin','BEGINNER',NULL,'course',NULL,NULL,'[]',1,1)",
        )
        assertTrue(revision() > before)

        val revisionTables = listOf(
            "exam_notice_revisions", "study_cycles", "study_cycle_items", "programming_course_profiles",
            "code_snippets", "coding_exercises", "mini_projects", "termux_runtime_preferences",
            "termux_command_requests", "termux_command_results", "language_deck_profiles",
            "language_expressions", "idiom_cards", "vocabulary_cards", "language_review_attempts",
        )
        val triggerNames = sqlite.query(
            "SELECT name FROM sqlite_master WHERE type='trigger' AND name LIKE 'studyflow_graph_revision_%'",
        ).use { cursor -> buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) } }
        revisionTables.forEach { table ->
            assertTrue("missing revision trigger for $table", "studyflow_graph_revision_${table}_insert" in triggerNames)
            assertTrue("missing update revision trigger for $table", "studyflow_graph_revision_${table}_update" in triggerNames)
            assertTrue("missing delete revision trigger for $table", "studyflow_graph_revision_${table}_delete" in triggerNames)
        }
    }

    @Test
    fun crossPlanModernReferencesAreRejectedAndDiagnosed() = runBlocking {
        database.studyPlanDao().insert(plan("a"))
        database.studyPlanDao().insert(plan("b"))
        insertSubject("a", "subject-a")
        insertTopic("a", "subject-a", "topic-a")
        insertTarget("a", "target-a")
        insertCycle("a", "cycle-a")
        insertLanguageDeck("a", "deck-a")

        assertTrue(runCatching {
            sqlite.execSQL(
                "INSERT INTO code_snippets (id,studyPlanId,subjectId,topicId,title,language,code,description," +
                    "sourceUri,tagsJson,createdAtEpochMillis,updatedAtEpochMillis) " +
                    "VALUES ('snippet-b','b','subject-a','topic-a','x','kotlin','x','',NULL,'[]',1,1)",
            )
        }.isFailure)
        assertTrue(runCatching {
            sqlite.execSQL(
                "INSERT INTO language_expressions (id,studyPlanId,deckId,expression,meaning,literalTranslation," +
                    "translationEquivalent,type,register,region,pronunciationNote,commonMistake,examplesJson,tagsJson," +
                    "createdAtEpochMillis,updatedAtEpochMillis) VALUES " +
                    "('expr-b','b','deck-a','hello','hello',NULL,NULL,'VOCABULARY','NEUTRAL',NULL,NULL,NULL,'[]','[]',1,1)",
            )
        }.isFailure)
        assertTrue(runCatching {
            sqlite.execSQL(
                "INSERT INTO study_cycle_items (id,studyCycleId,studyPlanId,subjectId,topicId,position," +
                    "targetDurationMinutes,enabled,activityType,completionRule) VALUES " +
                    "('item-b','cycle-a','b',NULL,NULL,0,25,1,'THEORY',NULL)",
            )
        }.isFailure)

        // Prove diagnostics see the same corruption even if a managed guard is intentionally removed.
        sqlite.execSQL("DROP TRIGGER IF EXISTS guard_code_snippets_subjectId_plan_scope_insert")
        sqlite.execSQL(
            "INSERT INTO code_snippets (id,studyPlanId,subjectId,topicId,title,language,code,description," +
                "sourceUri,tagsJson,createdAtEpochMillis,updatedAtEpochMillis) " +
                "VALUES ('corrupt','b','subject-a',NULL,'x','kotlin','x','',NULL,'[]',1,1)",
        )
        assertTrue(StudyFlowIntegrity.logicalIntegrityViolationCount(sqlite) > 0)
    }

    @Test
    fun questionProvenanceAndCyclePointerAreDurable() = runBlocking {
        database.studyPlanDao().insert(plan("a"))
        database.studyPlanDao().insert(plan("b"))
        insertTarget("a", "target-a")
        insertExamStage("a", "target-a", "stage-a")
        insertExamNoticeRevision("a", "target-a", "revision-a")

        assertTrue(runCatching { insertQuestion("b", "q-stage", "stage-a", null) }.isFailure)
        assertTrue(runCatching { insertQuestion("b", "q-revision", null, "revision-a") }.isFailure)

        insertCycle("a", "cycle-a")
        assertTrue(runCatching {
            sqlite.execSQL("UPDATE study_cycles SET currentItemId='missing' WHERE id='cycle-a'")
        }.isFailure)
        insertCycleItem("a", "cycle-a", "item-a")
        sqlite.execSQL("UPDATE study_cycles SET currentItemId='item-a' WHERE id='cycle-a'")
        sqlite.execSQL("DELETE FROM study_cycle_items WHERE id='item-a'")
        val pointer = sqlite.query("SELECT currentItemId FROM study_cycles WHERE id='cycle-a'").use { cursor ->
            check(cursor.moveToFirst()); if (cursor.isNull(0)) null else cursor.getString(0)
        }
        assertEquals(null, pointer)
    }

    private suspend fun revision(): Long =
        database.databaseMetadataDao().findValue(StudyFlowIntegrity.GraphRevisionMetadataKey)?.toLong() ?: 0L

    private fun plan(id: String) = StudyPlanEntity(
        id = id, name = id, description = "", type = "FREE_STUDY", status = "ACTIVE", examDateIso = null,
        weeklyGoalHours = 10, progressPercent = 0, studiedMinutesThisWeek = 0, questionsAnswered = 0,
        averageAccuracyPercent = 0, createdAtEpochMillis = 1L, updatedAtEpochMillis = 1L, trashedAtEpochMillis = null,
    )

    private fun session(planId: String, id: String, status: String) = StudySessionEntity(
        id = id, studyPlanId = planId, plannedActivityId = null, subjectId = null, topicId = null,
        materialId = null, videoResourceId = null, type = "CUSTOM", status = status,
        startedAtEpochMillis = 1L, endedAtEpochMillis = null, grossDurationSeconds = 0L,
        pauseDurationSeconds = 0L, pagesCompleted = 0, questionsAnswered = 0, correctAnswers = 0,
        concentrationRating = null, difficultyRating = null, notes = "", targetDurationMinutes = 25,
    )

    private fun insertSubject(planId: String, id: String) = sqlite.execSQL(
        "INSERT INTO subjects (id,studyPlanId,name,description,colorArgb,priority,examWeight,estimatedMinutes," +
            "progressPercent,accuracyPercent,status,position,archived,examStageId) VALUES " +
            "('$id','$planId','Subject','',NULL,3,1.0,0,0,0,'NOT_STARTED',0,0,NULL)",
    )

    private fun insertTopic(planId: String, subjectId: String, id: String) = sqlite.execSQL(
        "INSERT INTO topics (id,studyPlanId,subjectId,parentTopicId,name,description,priority,difficulty," +
            "examIncidencePercent,estimatedMinutes,progressPercent,masteryPercent,status,position,archived,examStageId) " +
            "VALUES ('$id','$planId','$subjectId',NULL,'Topic','',3,3,NULL,0,0,0,'NOT_STARTED',0,0,NULL)",
    )

    private fun insertTarget(planId: String, id: String) = sqlite.execSQL(
        "INSERT INTO study_targets (id,studyPlanId,organization,role,examBoard,noticeUrl,confirmedExamDateIso," +
            "expectedExamDateIso,openings,estimatedCutoffPercent,createdAtEpochMillis,updatedAtEpochMillis) " +
            "VALUES ('$id','$planId','Org','Role','Board',NULL,NULL,NULL,NULL,NULL,1,1)",
    )

    private fun insertExamStage(planId: String, targetId: String, id: String) = sqlite.execSQL(
        "INSERT INTO exam_stages (id,studyPlanId,studyTargetId,name,type,scheduledDateIso,weight," +
            "minimumScorePercent,position) VALUES ('$id','$planId','$targetId','Stage','OBJECTIVE',NULL,1.0,NULL,0)",
    )

    private fun insertExamNoticeRevision(planId: String, targetId: String, id: String) = sqlite.execSQL(
        "INSERT INTO exam_notice_revisions (id,studyPlanId,studyTargetId,label,sourceReference,importedAtEpochMillis," +
            "normalizedContentSha256,parserVersion,publicationDateIso,addedCount,removedCount,changedCount,unchangedCount) " +
            "VALUES ('$id','$planId','$targetId','r',NULL,1,'sha','1',NULL,0,0,0,0)",
    )

    private fun insertQuestion(planId: String, id: String, stageId: String?, revisionId: String?) {
        val stage = stageId?.let { "'$it'" } ?: "NULL"
        val revision = revisionId?.let { "'$it'" } ?: "NULL"
        sqlite.execSQL(
            "INSERT INTO questions (id,studyPlanId,subjectId,topicId,type,statement,optionsJson,correctAnswer," +
                "explanation,examBoard,source,year,difficulty,favorite,disputed,tagsJson,createdAtEpochMillis," +
                "updatedAtEpochMillis,organization,role,examStageId,authorship,examNoticeRevisionId," +
                "externalReference,points) VALUES ('$id','$planId',NULL,NULL,'MULTIPLE_CHOICE','Q','[]','A',''," +
                "NULL,NULL,NULL,3,0,0,'[]',1,1,NULL,NULL,$stage,'UNKNOWN',$revision,NULL,NULL)",
        )
    }

    private fun insertCycle(planId: String, id: String) = sqlite.execSQL(
        "INSERT INTO study_cycles (id,studyPlanId,name,enabled,currentItemId,createdAtEpochMillis,updatedAtEpochMillis) " +
            "VALUES ('$id','$planId','Cycle',1,NULL,1,1)",
    )

    private fun insertCycleItem(planId: String, cycleId: String, id: String) = sqlite.execSQL(
        "INSERT INTO study_cycle_items (id,studyCycleId,studyPlanId,subjectId,topicId,position,targetDurationMinutes," +
            "enabled,activityType,completionRule) VALUES ('$id','$cycleId','$planId',NULL,NULL,0,25,1,'THEORY',NULL)",
    )

    private fun insertLanguageDeck(planId: String, id: String) = sqlite.execSQL(
        "INSERT INTO language_deck_profiles (id,studyPlanId,targetLanguage,nativeLanguage,regionFocus," +
            "defaultRegister,reviewDirectionsJson,tagsJson,createdAtEpochMillis,updatedAtEpochMillis) " +
            "VALUES ('$id','$planId','en',NULL,NULL,'NEUTRAL','[]','[]',1,1)",
    )
}
