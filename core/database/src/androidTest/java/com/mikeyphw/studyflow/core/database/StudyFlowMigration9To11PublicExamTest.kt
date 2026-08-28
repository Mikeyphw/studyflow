package com.mikeyphw.studyflow.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudyFlowMigration9To11PublicExamTest {
    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        StudyFlowDatabase::class.java,
    )

    @Test
    fun migrationAddsRevisionAndPersistentCycleTables() {
        val name = "studyflow-migration-9-11-public-exam.db"
        migrationHelper.createDatabase(name, 9).close()
        migrationHelper.runMigrationsAndValidate(
            name,
            11,
            true,
            StudyFlowMigrations.Migration9To10,
            StudyFlowMigrations.Migration10To11,
        ).use { database ->
            database.query(
                "SELECT name FROM sqlite_master WHERE type='table' " +
                    "AND name IN ('exam_notice_revisions','study_cycles','study_cycle_items')",
            ).use { cursor ->
                val names = buildSet {
                    while (cursor.moveToNext()) add(cursor.getString(0))
                }
                assertEquals(
                    setOf("exam_notice_revisions", "study_cycles", "study_cycle_items"),
                    names,
                )
            }
            database.query(
                "SELECT name FROM sqlite_master WHERE type='index' " +
                    "AND name='index_study_cycle_items_studyCycleId_position'",
            ).use { cursor -> assertTrue(cursor.moveToFirst()) }
        }
    }
}
