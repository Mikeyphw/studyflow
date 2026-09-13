package com.mikeyphw.studyflow.core.database

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudyFlowOv05MigrationCoverageTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseNames = mutableSetOf<String>()

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        StudyFlowDatabase::class.java,
    )

    @After fun cleanup() = databaseNames.forEach(context::deleteDatabase)

    @Test
    fun twoToFifteenAcceptsLegacyLongFocusDurationAndPreservesCanonicalTarget() {
        val name = tracked("ov05-long-focus.db")
        helper.createDatabase(name, 2).apply {
            insertPlan(this)
            execSQL(
                """
                INSERT INTO planned_activities (
                    id, studyPlanId, subjectId, topicId, materialId, videoResourceId, type, title,
                    scheduledStartEpochMillis, plannedDurationMinutes, status, locked, completedAtEpochMillis
                ) VALUES ('activity', 'plan', NULL, NULL, NULL, NULL, 'THEORY', 'Long block', 1000, 300,
                    'PLANNED', 0, NULL)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO study_sessions (
                    id, studyPlanId, plannedActivityId, subjectId, topicId, materialId, videoResourceId,
                    type, status, startedAtEpochMillis, endedAtEpochMillis, grossDurationSeconds,
                    pauseDurationSeconds, pagesCompleted, questionsAnswered, correctAnswers,
                    concentrationRating, difficultyRating, notes
                ) VALUES ('session', 'plan', 'activity', NULL, NULL, NULL, NULL, 'THEORY', 'COMPLETED',
                    1000, 2000, 60, 0, 0, 0, 0, NULL, NULL, 'legacy')
                """.trimIndent(),
            )
            close()
        }
        helper.runMigrationsAndValidate(name, 15, true, *StudyFlowMigrations.All).use { db ->
            db.query("SELECT targetDurationMinutes FROM study_sessions WHERE id='session'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(300, cursor.getInt(0))
            }
        }
    }

    @Test
    fun sixToNineHasCheckedIntermediateSchemasAndPreservesTermuxAndLanguageRows() {
        val name = tracked("ov05-six-nine.db")
        helper.createDatabase(name, 6).apply {
            insertPlan(this)
            close()
        }
        helper.runMigrationsAndValidate(name, 7, true, StudyFlowMigrations.Migration6To7).use { db ->
            db.execSQL(
                """
                INSERT INTO termux_command_requests (
                    id, studyPlanId, exerciseId, workspaceRoot, courseSlug, workspaceRelativePath, action,
                    commandTemplateId, commandTemplateLabel, commandTemplateLanguage, commandLine,
                    workingDirectory, state, createdAtEpochMillis
                ) VALUES ('req', 'plan', NULL, '/tmp', 'course', '.', 'RUN', NULL, NULL, NULL, 'echo ok', '.',
                    'COMPLETED', 1)
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO termux_command_results (
                    id, requestId, state, exitCode, stdoutText, stderrText, resultJson, durationMillis,
                    completedAtEpochMillis
                ) VALUES ('res', 'req', 'COMPLETED', 0, 'ok', '', NULL, 5, 2)
                """.trimIndent(),
            )
        }
        helper.runMigrationsAndValidate(name, 8, true, StudyFlowMigrations.Migration7To8).use { db ->
            db.execSQL(
                """
                INSERT INTO language_deck_profiles (
                    id, studyPlanId, targetLanguage, nativeLanguage, regionFocus, defaultRegister,
                    reviewDirectionsJson, tagsJson, createdAtEpochMillis, updatedAtEpochMillis
                ) VALUES ('deck', 'plan', 'en', 'pt-BR', NULL, 'NEUTRAL', '[]', '[]', 1, 1)
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO language_expressions (
                    id, studyPlanId, deckId, expression, meaning, literalTranslation, translationEquivalent,
                    type, register, region, pronunciationNote, commonMistake, examplesJson, tagsJson,
                    createdAtEpochMillis, updatedAtEpochMillis
                ) VALUES ('expr', 'plan', 'deck', 'hello', 'oi', NULL, NULL, 'VOCABULARY', 'NEUTRAL', NULL,
                    NULL, NULL, '[]', '[]', 1, 1)
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO vocabulary_cards (
                    id, expressionId, partOfSpeech, synonymsJson, antonymsJson, reviewDirectionsJson, difficulty
                ) VALUES ('vocab', 'expr', 'noun', '[]', '[]', '[]', 1)
                """.trimIndent(),
            )
        }
        helper.runMigrationsAndValidate(name, 9, true, StudyFlowMigrations.Migration8To9).use { db ->
            db.execSQL(
                """
                INSERT INTO language_review_attempts (
                    id, studyPlanId, expressionId, idiomCardId, direction, promptText, answerText,
                    outcome, scorePercent, reviewItemId, createdAtEpochMillis
                ) VALUES ('attempt', 'plan', 'expr', NULL, 'TARGET_TO_NATIVE', 'hello', 'oi', 'CORRECT', 100,
                    NULL, 2)
                """.trimIndent(),
            )
            assertEquals(1, db.count("termux_command_requests"))
            assertEquals(1, db.count("termux_command_results"))
            assertEquals(1, db.count("language_deck_profiles"))
            assertEquals(1, db.count("language_expressions"))
            assertEquals(1, db.count("vocabulary_cards"))
            assertEquals(1, db.count("language_review_attempts"))
        }
    }

    private fun tracked(name: String) = name.also(databaseNames::add)

    private fun insertPlan(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            INSERT INTO study_plans (
                id, name, description, type, status, examDateIso, weeklyGoalHours, progressPercent,
                studiedMinutesThisWeek, questionsAnswered, averageAccuracyPercent, createdAtEpochMillis,
                updatedAtEpochMillis, trashedAtEpochMillis
            ) VALUES ('plan', 'Plan', '', 'FREE_STUDY', 'ACTIVE', NULL, 10, 0, 0, 0, 0, 1, 1, NULL)
            """.trimIndent(),
        )
    }

    private fun SupportSQLiteDatabase.count(table: String): Int =
        query("SELECT COUNT(*) FROM $table").use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }
}
