package com.mikeyphw.studyflow.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mikeyphw.studyflow.core.portability.StudyPlanExportOptions
import com.mikeyphw.studyflow.core.portability.StudyPlanImportStrategy
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudyPlanPortabilityMigrationCompatibilityTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseName = "studyflow-portability-migration.db"

    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        StudyFlowDatabase::class.java,
    )

    @After
    fun cleanup() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun migratedDatabaseExportsDurationAndImportsSchemaTwoBackupWithoutIt() = runBlocking {
        migrationHelper.createDatabase(databaseName, 2).apply {
            insertLegacyGraph(this)
            close()
        }
        migrationHelper.runMigrationsAndValidate(
            databaseName,
            4,
            true,
            *StudyFlowMigrations.All,
        ).close()

        Room.databaseBuilder(context, StudyFlowDatabase::class.java, databaseName)
            .addMigrations(*StudyFlowMigrations.All)
            .allowMainThreadQueries()
            .build()
            .use { database ->
                val store = StudyPlanGraphPortabilityStore(database)
                val exported = store.exportPayload(
                    planId = "plan",
                    options = StudyPlanExportOptions(),
                    applicationVersion = "test",
                    exportedAtEpochMillis = 10_000L,
                )
                val exportedSession = exported.tables
                    .single { table -> table.name == "study_sessions" }
                    .rows
                    .single()
                assertTrue("targetDurationMinutes" in exportedSession.values)
                assertNull(exportedSession.values.getValue("targetDurationMinutes").value)

                val legacyTables = exported.tables.map { table ->
                    if (table.name != "study_sessions") {
                        table
                    } else {
                        table.copy(
                            rows = table.rows.map { row ->
                                row.copy(values = row.values - "targetDurationMinutes")
                            },
                        )
                    }
                }
                val legacyPayload = exported.copy(tables = legacyTables)
                store.validatePayload(legacyPayload)
                store.importPayload(
                    payload = legacyPayload,
                    strategy = StudyPlanImportStrategy.CREATE_AS_NEW,
                    targetPlanId = "restored",
                    importedAtEpochMillis = 20_000L,
                )

                database.openHelper.writableDatabase.query(
                    """
                    SELECT notes, targetDurationMinutes
                    FROM study_sessions
                    WHERE studyPlanId = 'restored'
                    """.trimIndent(),
                ).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("legacy", cursor.getString(0))
                    assertNull(if (cursor.isNull(1)) null else cursor.getInt(1))
                }
            }
    }

    private fun insertLegacyGraph(database: SupportSQLiteDatabase) {
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
}
