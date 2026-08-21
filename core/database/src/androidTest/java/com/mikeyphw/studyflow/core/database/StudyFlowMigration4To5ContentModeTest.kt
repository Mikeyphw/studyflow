package com.mikeyphw.studyflow.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudyFlowMigration4To5ContentModeTest {
    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        StudyFlowDatabase::class.java,
    )

    @Test
    fun migrationBackfillsExistingPlansAsExamPlans() {
        val name = "studyflow-migration-4-5-content-mode.db"
        val database = migrationHelper.createDatabase(name, 4)
        insertPlan(database, "legacy")
        database.close()

        migrationHelper.runMigrationsAndValidate(
            name,
            5,
            true,
            StudyFlowMigrations.Migration4To5,
        ).use { migrated ->
            migrated.query("SELECT contentMode FROM study_plans WHERE id = 'legacy'").use { cursor ->
                cursor.moveToFirst()
                assertEquals("EXAM_PLAN", cursor.getString(0))
            }
        }
    }

    private fun insertPlan(database: SupportSQLiteDatabase, id: String) {
        database.execSQL(
            """
            INSERT INTO study_plans (
                id, name, description, type, status, examDateIso, weeklyGoalHours, progressPercent,
                studiedMinutesThisWeek, questionsAnswered, averageAccuracyPercent, createdAtEpochMillis,
                updatedAtEpochMillis, trashedAtEpochMillis
            ) VALUES (
                '$id', 'Legacy', '', 'PUBLIC_EXAMINATION', 'ACTIVE', NULL, 10, 0, 0, 0, 0, 1, 1, NULL
            )
            """.trimIndent(),
        )
    }
}
