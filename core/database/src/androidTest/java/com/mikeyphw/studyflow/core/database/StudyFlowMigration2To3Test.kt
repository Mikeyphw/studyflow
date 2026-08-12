package com.mikeyphw.studyflow.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudyFlowMigration2To3Test {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseNames = mutableSetOf<String>()

    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        StudyFlowDatabase::class.java,
    )

    @After
    fun cleanup() {
        databaseNames.forEach(context::deleteDatabase)
    }

    @Test
    fun migrationOneToFourPreservesLegacySessionAndAddsNullableDuration() {
        val databaseName = trackedDatabase("studyflow-migration-1-4.db")
        migrationHelper.createDatabase(databaseName, 1).apply {
            insertPlanAndLegacySession(this)
            close()
        }

        migrationHelper.runMigrationsAndValidate(
            databaseName,
            4,
            true,
            *StudyFlowMigrations.All,
        ).use { database ->
            assertMigratedSession(database)
        }
    }

    @Test
    fun migrationTwoToThreePreservesLegacySessionAndAddsNullableDuration() {
        val databaseName = trackedDatabase("studyflow-migration-2-3.db")
        migrationHelper.createDatabase(databaseName, 2).apply {
            insertPlanAndLegacySession(this)
            close()
        }

        migrationHelper.runMigrationsAndValidate(
            databaseName,
            3,
            true,
            StudyFlowMigrations.Migration2To3,
        ).use { database ->
            assertMigratedSession(database)
        }
    }

    @Test
    fun migrationTwoToThreeBackfillsLinkedFocusTargetDuration() {
        val databaseName = trackedDatabase("studyflow-migration-2-3-linked.db")
        migrationHelper.createDatabase(databaseName, 2).apply {
            insertPlan(this)
            execSQL(
                """
                INSERT INTO planned_activities (
                    id, studyPlanId, subjectId, topicId, materialId, videoResourceId, type, title,
                    scheduledStartEpochMillis, plannedDurationMinutes, status, locked, completedAtEpochMillis
                ) VALUES (
                    'activity', 'plan', NULL, NULL, NULL, NULL, 'THEORY', 'Focus block',
                    1000, 55, 'IN_PROGRESS', 0, NULL
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO study_sessions (
                    id, studyPlanId, plannedActivityId, subjectId, topicId, materialId, videoResourceId,
                    type, status, startedAtEpochMillis, endedAtEpochMillis, grossDurationSeconds,
                    pauseDurationSeconds, pagesCompleted, questionsAnswered, correctAnswers,
                    concentrationRating, difficultyRating, notes
                ) VALUES (
                    'linked', 'plan', 'activity', NULL, NULL, NULL, NULL, 'THEORY', 'RUNNING',
                    1000, NULL, 0, 0, 0, 0, 0, NULL, NULL, 'legacy focus'
                )
                """.trimIndent(),
            )
            close()
        }

        migrationHelper.runMigrationsAndValidate(
            databaseName,
            3,
            true,
            StudyFlowMigrations.Migration2To3,
        ).use { database ->
            database.query(
                "SELECT targetDurationMinutes FROM study_sessions WHERE id = 'linked'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(55, cursor.getInt(0))
            }
        }
    }

    @Test
    fun freshVersionFourDatabaseContainsNullableTargetDuration() {
        val databaseName = trackedDatabase("studyflow-fresh-4.db")
        Room.databaseBuilder(context, StudyFlowDatabase::class.java, databaseName)
            .addMigrations(*StudyFlowMigrations.All)
            .allowMainThreadQueries()
            .build()
            .use { roomDatabase ->
                val database = roomDatabase.openHelper.writableDatabase
                assertTrue("targetDurationMinutes" in database.tableColumns("study_sessions"))
            }
    }

    @Test
    fun activeFocusSessionRestoresAfterDatabaseReopen() = runBlocking {
        val databaseName = trackedDatabase("studyflow-focus-restore.db")
        Room.databaseBuilder(context, StudyFlowDatabase::class.java, databaseName)
            .addMigrations(*StudyFlowMigrations.All)
            .allowMainThreadQueries()
            .build()
            .use { database ->
                insertPlanAndFocusSession(database.openHelper.writableDatabase)
            }

        Room.databaseBuilder(context, StudyFlowDatabase::class.java, databaseName)
            .addMigrations(*StudyFlowMigrations.All)
            .allowMainThreadQueries()
            .build()
            .use { reopened ->
                val restored = reopened.studyPlanningDao().findActiveStudySessions("plan").single()
                assertEquals("RUNNING", restored.status)
                assertEquals(45, restored.targetDurationMinutes)
                assertEquals(1_000L, restored.startedAtEpochMillis)
            }
    }

    private fun trackedDatabase(name: String): String = name.also(databaseNames::add)

    private fun assertMigratedSession(database: SupportSQLiteDatabase) {
        assertTrue("targetDurationMinutes" in database.tableColumns("study_sessions"))
        assertEquals(1, database.countRows("study_sessions"))
        database.query(
            "SELECT notes, targetDurationMinutes FROM study_sessions WHERE id = 'session'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("legacy", cursor.getString(0))
            assertNull(if (cursor.isNull(1)) null else cursor.getInt(1))
        }
    }

    private fun insertPlanAndLegacySession(database: SupportSQLiteDatabase) {
        insertPlan(database)
        database.execSQL(
            """
            INSERT INTO study_sessions (
                id, studyPlanId, plannedActivityId, subjectId, topicId, materialId, videoResourceId,
                type, status, startedAtEpochMillis, endedAtEpochMillis, grossDurationSeconds,
                pauseDurationSeconds, pagesCompleted, questionsAnswered, correctAnswers,
                concentrationRating, difficultyRating, notes
            ) VALUES (
                'session', 'plan', NULL, NULL, NULL, NULL, NULL, 'CUSTOM', 'COMPLETED',
                1, 2, 1, 0, 0, 0, 0, NULL, NULL, 'legacy'
            )
            """.trimIndent(),
        )
    }

    private fun insertPlanAndFocusSession(database: SupportSQLiteDatabase) {
        insertPlan(database)
        database.execSQL(
            """
            INSERT INTO study_sessions (
                id, studyPlanId, plannedActivityId, subjectId, topicId, materialId, videoResourceId,
                type, status, startedAtEpochMillis, endedAtEpochMillis, grossDurationSeconds,
                pauseDurationSeconds, pagesCompleted, questionsAnswered, correctAnswers,
                concentrationRating, difficultyRating, notes, targetDurationMinutes
            ) VALUES (
                'focus', 'plan', NULL, NULL, NULL, NULL, NULL, 'THEORY', 'RUNNING',
                1000, NULL, 0, 0, 0, 0, 0, NULL, NULL, 'Constitutional law', 45
            )
            """.trimIndent(),
        )
    }

    private fun insertPlan(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            INSERT INTO study_plans (
                id, name, description, type, status, examDateIso, weeklyGoalHours,
                progressPercent, studiedMinutesThisWeek, questionsAnswered,
                averageAccuracyPercent, createdAtEpochMillis, updatedAtEpochMillis,
                trashedAtEpochMillis
            ) VALUES (
                'plan', 'Plan', '', 'FREE_STUDY', 'ACTIVE', NULL, 10,
                0, 0, 0, 0, 1, 1, NULL
            )
            """.trimIndent(),
        )
    }
}
