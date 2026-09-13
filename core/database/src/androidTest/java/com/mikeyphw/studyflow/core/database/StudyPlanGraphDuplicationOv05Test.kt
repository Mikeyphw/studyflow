package com.mikeyphw.studyflow.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudyPlanGraphDuplicationOv05Test {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val database = Room.inMemoryDatabaseBuilder(context, StudyFlowDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    @After fun close() = database.close()

    @Test
    fun duplicatedGraphRemapsPracticeLanguageAndTermuxReferences() {
        val db = database.openHelper.writableDatabase
        insertPlan(db, "source")
        insertPlan(db, "copy")
        db.execSQL(
            """
            INSERT INTO questions (
                id, studyPlanId, subjectId, topicId, type, statement, optionsJson, correctAnswer, explanation,
                examBoard, source, year, difficulty, favorite, disputed, tagsJson, createdAtEpochMillis,
                updatedAtEpochMillis, organization, role, examStageId, authorship, examNoticeRevisionId,
                externalReference, points
            ) VALUES ('q1', 'source', NULL, NULL, 'MULTIPLE_CHOICE', 'Q?', '["A","B"]', 'A', 'why',
                NULL, NULL, NULL, 1, 0, 0, '[]', 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 1.0)
            """.trimIndent(),
        )
        val snapshotId = b64("q1")
        val config = "questionIds=q1\nquestionSnapshotV1=$snapshotId|MULTIPLE_CHOICE|UT8|QQ|d2h5|1|false|false|0"
        db.execSQL(
            """
            INSERT INTO study_tests (
                id, studyPlanId, title, mode, timeLimitSeconds, questionCount, answeredCount, correctCount,
                startedAtEpochMillis, submittedAtEpochMillis, configurationJson
            ) VALUES ('test', 'source', 'Test', 'PRACTICE', NULL, 1, 0, 0, NULL, NULL, ?)
            """.trimIndent(), arrayOf(config),
        )
        db.execSQL(
            """
            INSERT INTO language_deck_profiles (
                id, studyPlanId, targetLanguage, nativeLanguage, regionFocus, defaultRegister,
                reviewDirectionsJson, tagsJson, createdAtEpochMillis, updatedAtEpochMillis
            ) VALUES ('deck', 'source', 'en', 'pt-BR', NULL, 'NEUTRAL', '[]', '[]', 1, 1)
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO language_expressions (
                id, studyPlanId, deckId, expression, meaning, literalTranslation, translationEquivalent,
                type, register, region, pronunciationNote, commonMistake, examplesJson, tagsJson,
                createdAtEpochMillis, updatedAtEpochMillis
            ) VALUES ('expr', 'source', 'deck', 'hello', 'oi', NULL, NULL, 'VOCABULARY', 'NEUTRAL', NULL,
                NULL, NULL, '[]', '[]', 1, 1)
            """.trimIndent(),
        )
        db.execSQL(
            "INSERT INTO vocabulary_cards (id, expressionId, partOfSpeech, synonymsJson, antonymsJson, reviewDirectionsJson, difficulty) VALUES ('vocab', 'expr', NULL, '[]', '[]', '[]', 1)",
        )
        db.execSQL(
            """
            INSERT INTO termux_command_requests (
                id, studyPlanId, exerciseId, workspaceRoot, courseSlug, workspaceRelativePath, action,
                commandTemplateId, commandTemplateLabel, commandTemplateLanguage, commandLine,
                workingDirectory, state, createdAtEpochMillis
            ) VALUES ('req', 'source', NULL, '/tmp', 'course', '.', 'RUN', NULL, NULL, NULL, 'echo ok', '.',
                'COMPLETED', 1)
            """.trimIndent(),
        )
        db.execSQL(
            "INSERT INTO termux_command_results (id, requestId, state, exitCode, stdoutText, stderrText, resultJson, durationMillis, completedAtEpochMillis) VALUES ('res', 'req', 'COMPLETED', 0, 'ok', '', NULL, 5, 2)",
        )

        StudyPlanGraphDuplicator(db).duplicate("source", "copy")

        db.query("SELECT configurationJson FROM study_tests WHERE studyPlanId='copy'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            val copied = cursor.getString(0)
            assertTrue(copied.contains("questionIds=copy:q1"))
            assertTrue(copied.contains("questionSnapshotV1=${b64("copy:q1")}|"))
        }
        assertEquals("copy:deck", scalar(db, "SELECT deckId FROM language_expressions WHERE studyPlanId='copy'"))
        assertEquals("copy:expr", scalar(db, "SELECT expressionId FROM vocabulary_cards WHERE id='copy:vocab'"))
        assertEquals("copy:req", scalar(db, "SELECT requestId FROM termux_command_results WHERE id='copy:res'"))
    }

    private fun insertPlan(db: androidx.sqlite.db.SupportSQLiteDatabase, id: String) {
        db.execSQL(
            """
            INSERT INTO study_plans (
                id, name, description, type, status, examDateIso, weeklyGoalHours, progressPercent,
                studiedMinutesThisWeek, questionsAnswered, averageAccuracyPercent, createdAtEpochMillis,
                updatedAtEpochMillis, trashedAtEpochMillis, contentMode
            ) VALUES (?, ?, '', 'FREE_STUDY', 'ACTIVE', NULL, 10, 0, 0, 0, 0, 1, 1, NULL, 'EXAM_PLAN')
            """.trimIndent(), arrayOf(id, id),
        )
    }

    private fun scalar(db: androidx.sqlite.db.SupportSQLiteDatabase, sql: String): String =
        db.query(sql).use { cursor -> assertTrue(cursor.moveToFirst()); cursor.getString(0) }

    private fun b64(value: String): String = java.util.Base64.getUrlEncoder().withoutPadding()
        .encodeToString(value.toByteArray(Charsets.UTF_8))
}
