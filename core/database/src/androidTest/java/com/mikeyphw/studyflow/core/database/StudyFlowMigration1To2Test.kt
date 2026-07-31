package com.mikeyphw.studyflow.core.database

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowMigration1To2Test {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseName = "studyflow-migration-1-2-test.db"

    @After
    fun cleanup() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun migrationAddsNullableExamStageColumnsWithoutLosingRows() {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(VersionOneSchemaCallback)
                .build(),
        )
        val database = helper.writableDatabase

        StudyFlowMigrations.Migration1To2.migrate(database)

        assertTrue("examStageId" in database.tableColumns("subjects"))
        assertTrue("examStageId" in database.tableColumns("topics"))
        assertEquals(1, database.countRows("subjects"))
        assertEquals(1, database.countRows("topics"))
        helper.close()
    }

    private object VersionOneSchemaCallback : SupportSQLiteOpenHelper.Callback(1) {
        override fun onCreate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE subjects (
                    id TEXT NOT NULL PRIMARY KEY,
                    studyPlanId TEXT NOT NULL,
                    name TEXT NOT NULL,
                    description TEXT NOT NULL,
                    colorArgb INTEGER,
                    priority INTEGER NOT NULL,
                    examWeight REAL NOT NULL,
                    estimatedMinutes INTEGER NOT NULL,
                    progressPercent INTEGER NOT NULL,
                    accuracyPercent INTEGER NOT NULL,
                    status TEXT NOT NULL,
                    position INTEGER NOT NULL,
                    archived INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE topics (
                    id TEXT NOT NULL PRIMARY KEY,
                    studyPlanId TEXT NOT NULL,
                    subjectId TEXT NOT NULL,
                    parentTopicId TEXT,
                    name TEXT NOT NULL,
                    description TEXT NOT NULL,
                    priority INTEGER NOT NULL,
                    difficulty INTEGER NOT NULL,
                    examIncidencePercent INTEGER,
                    estimatedMinutes INTEGER NOT NULL,
                    progressPercent INTEGER NOT NULL,
                    masteryPercent INTEGER NOT NULL,
                    status TEXT NOT NULL,
                    position INTEGER NOT NULL,
                    archived INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO subjects VALUES (
                    's1', 'p1', 'Law', '', NULL, 3, 1.0, 60, 20, 0, 'STUDYING', 0, 0
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO topics VALUES (
                    't1', 'p1', 's1', NULL, 'Rights', '', 3, 3, NULL, 30, 20, 20,
                    'STUDYING', 0, 0
                )
                """.trimIndent(),
            )
        }

        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }
}

private fun SupportSQLiteDatabase.tableColumns(tableName: String): Set<String> =
    query("PRAGMA table_info($tableName)").use { cursor ->
        buildSet {
            while (cursor.moveToNext()) {
                add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
            }
        }
    }

private fun SupportSQLiteDatabase.countRows(tableName: String): Int =
    query("SELECT COUNT(*) FROM $tableName").use { cursor ->
        cursor.moveToFirst()
        cursor.getInt(0)
    }
