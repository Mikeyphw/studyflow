package com.mikeyphw.studyflow.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
class StudyPlanCpovPortabilitySealTest {
    private lateinit var context: Context
    private lateinit var database: StudyFlowDatabase

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, StudyFlowDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        seedPublicExamGraph()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun fullPackageExportsAndRestoresEveryPersistedCpovConceptWithOwnershipRemapped() = runBlocking {
        val store = StudyPlanGraphPortabilityStore(database)
        val payload = store.exportPayload(
            planId = SOURCE_PLAN,
            options = StudyPlanExportOptions(),
            applicationVersion = "cpov13-test",
            exportedAtEpochMillis = 10_000L,
        )

        val expectedTables = setOf(
            "study_targets",
            "exam_notice_revisions",
            "study_cycles",
            "study_cycle_items",
            "questions",
            "legislation_documents",
            "legislation_provisions",
            "legislation_evidence_links",
        )
        val exportedByName = payload.tables.associateBy { it.name }
        expectedTables.forEach { tableName ->
            assertEquals(1, exportedByName.getValue(tableName).rows.size)
        }
        store.validatePayload(payload)

        store.importPayload(
            payload = payload,
            strategy = StudyPlanImportStrategy.CREATE_AS_NEW,
            targetPlanId = RESTORED_PLAN,
            importedAtEpochMillis = 20_000L,
        )

        expectedTables.forEach { tableName ->
            database.openHelper.writableDatabase.query(
                "SELECT COUNT(*) FROM `$tableName` WHERE studyPlanId = ?",
                arrayOf(RESTORED_PLAN),
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
        }

        database.openHelper.writableDatabase.query(
            "SELECT id, studyTargetId FROM exam_notice_revisions WHERE studyPlanId = ?",
            arrayOf(RESTORED_PLAN),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("$RESTORED_PLAN:revision", cursor.getString(0))
            assertEquals("$RESTORED_PLAN:target", cursor.getString(1))
        }
        database.openHelper.writableDatabase.query(
            "SELECT id, currentItemId FROM study_cycles WHERE studyPlanId = ?",
            arrayOf(RESTORED_PLAN),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("$RESTORED_PLAN:cycle", cursor.getString(0))
            assertEquals("$RESTORED_PLAN:cycle-item", cursor.getString(1))
        }
        database.openHelper.writableDatabase.query(
            "SELECT examNoticeRevisionId, organization, role, authorship FROM questions WHERE studyPlanId = ?",
            arrayOf(RESTORED_PLAN),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("$RESTORED_PLAN:revision", cursor.getString(0))
            assertEquals("Agency", cursor.getString(1))
            assertEquals("Analyst", cursor.getString(2))
            assertEquals("OFFICIAL", cursor.getString(3))
        }
        database.openHelper.writableDatabase.query(
            "SELECT provisionId, questionId FROM legislation_evidence_links WHERE studyPlanId = ?",
            arrayOf(RESTORED_PLAN),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("$RESTORED_PLAN:provision", cursor.getString(0))
            assertEquals("$RESTORED_PLAN:question", cursor.getString(1))
        }

        database.openHelper.writableDatabase.query(
            "SELECT id FROM exam_notice_revisions WHERE studyPlanId = ?",
            arrayOf(RESTORED_PLAN),
        ).use { cursor ->
            while (cursor.moveToNext()) assertFalse(cursor.getString(0).startsWith("$SOURCE_PLAN:"))
        }
    }

    private fun seedPublicExamGraph() {
        val db = database.openHelper.writableDatabase
        db.execSQL(
            """
            INSERT INTO study_plans (
                id, name, description, type, contentMode, status, examDateIso, weeklyGoalHours,
                progressPercent, studiedMinutesThisWeek, questionsAnswered, averageAccuracyPercent,
                createdAtEpochMillis, updatedAtEpochMillis, trashedAtEpochMillis
            ) VALUES (
                '$SOURCE_PLAN', 'Concurso', '', 'PUBLIC_EXAMINATION', 'EXAM_PLAN', 'ACTIVE',
                '2027-01-10', 18, 20, 120, 40, 75, 1, 1, NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO study_targets (
                id, studyPlanId, organization, role, examBoard, noticeUrl, confirmedExamDateIso,
                expectedExamDateIso, openings, estimatedCutoffPercent, createdAtEpochMillis,
                updatedAtEpochMillis
            ) VALUES (
                'target', '$SOURCE_PLAN', 'Agency', 'Analyst', 'Board', 'edital.pdf', '2027-01-10',
                NULL, 10, 70, 1, 1
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO exam_notice_revisions (
                id, studyPlanId, studyTargetId, label, sourceReference, importedAtEpochMillis,
                normalizedContentSha256, parserVersion, publicationDateIso, addedCount,
                removedCount, changedCount, unchangedCount
            ) VALUES (
                'revision', '$SOURCE_PLAN', 'target', 'Edital 1', 'edital.pdf', 2,
                '${"a".repeat(64)}', 'cpov02-v1', '2026-08-01', 1, 0, 0, 0
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO subjects (
                id, studyPlanId, name, description, colorArgb, priority, examWeight, estimatedMinutes,
                progressPercent, accuracyPercent, status, position, archived, examStageId
            ) VALUES (
                'subject', '$SOURCE_PLAN', 'Direito', '', NULL, 5, 3.0, 600,
                20, 75, 'STUDYING', 0, 0, NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO topics (
                id, studyPlanId, subjectId, parentTopicId, name, description, priority, difficulty,
                examIncidencePercent, estimatedMinutes, progressPercent, masteryPercent, status,
                position, archived, examStageId
            ) VALUES (
                'topic', '$SOURCE_PLAN', 'subject', NULL, 'Constitucional', '', 5, 4,
                80, 240, 10, 30, 'STUDYING', 0, 0, NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO study_cycles (
                id, studyPlanId, name, enabled, currentItemId, createdAtEpochMillis, updatedAtEpochMillis
            ) VALUES ('cycle', '$SOURCE_PLAN', 'Cycle', 1, NULL, 2, 2)
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO study_cycle_items (
                id, studyCycleId, studyPlanId, subjectId, topicId, position, targetDurationMinutes,
                enabled, activityType, completionRule
            ) VALUES (
                'cycle-item', 'cycle', '$SOURCE_PLAN', 'subject', 'topic', 0, 45, 1, 'THEORY', NULL
            )
            """.trimIndent(),
        )
        db.execSQL("UPDATE study_cycles SET currentItemId='cycle-item' WHERE id='cycle'")
        db.execSQL(
            """
            INSERT INTO questions (
                id, studyPlanId, subjectId, topicId, type, statement, optionsJson, correctAnswer,
                explanation, examBoard, source, year, difficulty, favorite, disputed, tagsJson,
                createdAtEpochMillis, updatedAtEpochMillis, organization, role, examStageId,
                authorship, examNoticeRevisionId, externalReference, points
            ) VALUES (
                'question', '$SOURCE_PLAN', 'subject', 'topic', 'MULTIPLE_CHOICE', 'Question?',
                '["A","B"]', 'A', '', 'Board', 'official', 2026, 3, 0, 0, '[]', 3, 3,
                'Agency', 'Analyst', NULL, 'OFFICIAL', 'revision', 'Q-1', 1.0
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO legislation_documents (
                id, studyPlanId, title, sourceReference, createdAtEpochMillis, updatedAtEpochMillis
            ) VALUES ('law', '$SOURCE_PLAN', 'Constitution', 'local.md', 4, 4)
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO legislation_provisions (
                id, legislationDocumentId, studyPlanId, reference, parentReference, kind, text,
                position, read, important, createdAtEpochMillis, updatedAtEpochMillis
            ) VALUES (
                'provision', 'law', '$SOURCE_PLAN', 'Art. 1', NULL, 'ARTICLE', 'Text', 0, 1, 1, 4, 4
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO legislation_evidence_links (
                id, studyPlanId, provisionId, questionId, errorEntryId, reviewItemId
            ) VALUES ('link', '$SOURCE_PLAN', 'provision', 'question', NULL, NULL)
            """.trimIndent(),
        )
    }

    private companion object {
        const val SOURCE_PLAN = "source-plan"
        const val RESTORED_PLAN = "restored-plan"
    }
}
