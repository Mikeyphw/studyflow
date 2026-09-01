package com.mikeyphw.studyflow.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
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
class StudyFlowMigration9To13CpovSealTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseName = "studyflow-cpov9-13-seal.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        StudyFlowDatabase::class.java,
    )

    @After
    fun cleanup() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun representativePhase64FixtureMigratesThroughEveryCpovSchema() {
        helper.createDatabase(databaseName, 9).apply {
            execSQL(
                """
                INSERT INTO study_plans (
                    id, name, description, type, status, examDateIso, weeklyGoalHours,
                    progressPercent, studiedMinutesThisWeek, questionsAnswered,
                    averageAccuracyPercent, createdAtEpochMillis, updatedAtEpochMillis,
                    trashedAtEpochMillis, contentMode
                ) VALUES (
                    'plan', 'Concurso', '', 'PUBLIC_EXAMINATION', 'ACTIVE', '2027-01-10', 18,
                    25, 120, 40, 75, 1, 1, NULL, 'EXAM_PLAN'
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO study_targets (
                    id, studyPlanId, organization, role, examBoard, noticeUrl,
                    confirmedExamDateIso, expectedExamDateIso, openings,
                    estimatedCutoffPercent, createdAtEpochMillis, updatedAtEpochMillis
                ) VALUES (
                    'target', 'plan', 'Agency', 'Analyst', 'Board', 'edital.pdf',
                    '2027-01-10', NULL, 10, 70, 1, 1
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO subjects (
                    id, studyPlanId, name, description, colorArgb, priority, examWeight,
                    estimatedMinutes, progressPercent, accuracyPercent, status, position,
                    archived, examStageId
                ) VALUES (
                    'subject', 'plan', 'Direito', '', NULL, 5, 3.0,
                    600, 20, 75, 'STUDYING', 0, 0, NULL
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO topics (
                    id, studyPlanId, subjectId, parentTopicId, name, description, priority,
                    difficulty, examIncidencePercent, estimatedMinutes, progressPercent,
                    masteryPercent, status, position, archived, examStageId
                ) VALUES (
                    'topic', 'plan', 'subject', NULL, 'Constitucional', '', 5,
                    4, 80, 240, 10, 30, 'STUDYING', 0, 0, NULL
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO questions (
                    id, studyPlanId, subjectId, topicId, type, statement, optionsJson,
                    correctAnswer, explanation, examBoard, source, year, difficulty,
                    favorite, disputed, tagsJson, createdAtEpochMillis, updatedAtEpochMillis
                ) VALUES (
                    'q1', 'plan', 'subject', 'topic', 'MULTIPLE_CHOICE', 'Question?', '["A","B"]',
                    'A', '', 'Board', 'fixture', 2026, 3, 0, 0, '[]', 1, 1
                )
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(
            databaseName,
            13,
            true,
            StudyFlowMigrations.Migration9To10,
            StudyFlowMigrations.Migration10To11,
            StudyFlowMigrations.Migration11To12,
            StudyFlowMigrations.Migration12To13,
        ).use { db ->
            db.query("SELECT organization, role, examBoard FROM study_targets WHERE id='target'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Agency", cursor.getString(0))
                assertEquals("Analyst", cursor.getString(1))
                assertEquals("Board", cursor.getString(2))
            }
            db.query(
                "SELECT authorship, organization, role, examNoticeRevisionId, points FROM questions WHERE id='q1'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("UNKNOWN", cursor.getString(0))
                assertTrue(cursor.isNull(1))
                assertTrue(cursor.isNull(2))
                assertTrue(cursor.isNull(3))
                assertTrue(cursor.isNull(4))
            }
            val expectedTables = setOf(
                "exam_notice_revisions",
                "study_cycles",
                "study_cycle_items",
                "legislation_documents",
                "legislation_provisions",
                "legislation_evidence_links",
            )
            db.query(
                "SELECT name FROM sqlite_master WHERE type='table' " +
                    "AND name IN ('exam_notice_revisions','study_cycles','study_cycle_items'," +
                    "'legislation_documents','legislation_provisions','legislation_evidence_links')",
            ).use { cursor ->
                val actual = buildSet {
                    while (cursor.moveToNext()) add(cursor.getString(0))
                }
                assertEquals(expectedTables, actual)
            }
        }
    }

    @Test
    fun freshSchemaCreatesTheSameCpovEndpoint() {
        val database = Room.inMemoryDatabaseBuilder(context, StudyFlowDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val db = database.openHelper.writableDatabase
            assertEquals(13, db.version)
            db.query(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' " +
                    "AND name IN ('exam_notice_revisions','study_cycles','study_cycle_items'," +
                    "'legislation_documents','legislation_provisions','legislation_evidence_links')",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(6, cursor.getInt(0))
            }
        } finally {
            database.close()
        }
    }
}
