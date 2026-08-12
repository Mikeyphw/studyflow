package com.mikeyphw.studyflow.core.database

import android.database.sqlite.SQLiteConstraintException
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudyFlowMigration3To4IntegrityTest {
    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        StudyFlowDatabase::class.java,
    )

    @Test
    fun migrationInstallsPlanScopedAndSingleActiveSessionGuards() {
        val name = "studyflow-migration-3-4-integrity.db"
        val database = migrationHelper.createDatabase(name, 3)
        insertPlan(database, "plan-a")
        insertPlan(database, "plan-b")
        database.execSQL(
            """
            INSERT INTO subjects (
                id, studyPlanId, name, description, colorArgb, priority, examWeight,
                estimatedMinutes, progressPercent, accuracyPercent, status, position, archived, examStageId
            ) VALUES (
                'subject-a', 'plan-a', 'A', '', NULL, 3, 1.0, 60, 0, 0, 'NOT_STARTED', 0, 0, NULL
            )
            """.trimIndent(),
        )
        database.close()

        migrationHelper.runMigrationsAndValidate(
            name,
            4,
            true,
            StudyFlowMigrations.Migration3To4,
        ).use { migrated ->
            assertThrows(SQLiteConstraintException::class.java) {
                migrated.execSQL(
                    """
                    INSERT INTO topics (
                        id, studyPlanId, subjectId, parentTopicId, name, description, priority, difficulty,
                        examIncidencePercent, estimatedMinutes, progressPercent, masteryPercent, status,
                        position, archived, examStageId
                    ) VALUES (
                        'cross-plan-topic', 'plan-b', 'subject-a', NULL, 'Bad', '', 3, 3,
                        NULL, 30, 0, 0, 'NOT_STARTED', 0, 0, NULL
                    )
                    """.trimIndent(),
                )
            }
            insertSession(migrated, "session-a", "plan-a", "RUNNING")
            assertThrows(SQLiteConstraintException::class.java) {
                insertSession(migrated, "session-b", "plan-a", "PAUSED")
            }

            migrated.execSQL(
                """
                INSERT INTO study_targets (
                    id, studyPlanId, organization, role, examBoard, noticeUrl, confirmedExamDateIso,
                    expectedExamDateIso, openings, estimatedCutoffPercent, createdAtEpochMillis,
                    updatedAtEpochMillis
                ) VALUES (
                    'target-a', 'plan-a', 'Org', 'Role', 'Board', NULL, NULL, NULL, NULL, NULL, 1, 1
                )
                """.trimIndent(),
            )
            migrated.execSQL(
                """
                INSERT INTO exam_stages (
                    id, studyPlanId, studyTargetId, name, type, weight, minimumScorePercent, position
                ) VALUES ('stage-a', 'plan-a', 'target-a', 'Stage', 'OBJECTIVE', 1.0, NULL, 0)
                """.trimIndent(),
            )
            migrated.execSQL("UPDATE subjects SET examStageId = 'stage-a' WHERE id = 'subject-a'")
            migrated.execSQL(
                """
                INSERT INTO topics (
                    id, studyPlanId, subjectId, parentTopicId, name, description, priority, difficulty,
                    examIncidencePercent, estimatedMinutes, progressPercent, masteryPercent, status,
                    position, archived, examStageId
                ) VALUES (
                    'topic-a', 'plan-a', 'subject-a', NULL, 'Topic', '', 3, 3, NULL, 30, 0, 0,
                    'NOT_STARTED', 0, 0, 'stage-a'
                )
                """.trimIndent(),
            )
            migrated.execSQL(
                """
                INSERT INTO subjects (
                    id, studyPlanId, name, description, colorArgb, priority, examWeight,
                    estimatedMinutes, progressPercent, accuracyPercent, status, position, archived, examStageId
                ) VALUES (
                    'subject-a2', 'plan-a', 'A2', '', NULL, 3, 1.0, 60, 0, 0, 'NOT_STARTED', 1, 0, NULL
                )
                """.trimIndent(),
            )
            migrated.execSQL(
                """
                INSERT INTO topics (
                    id, studyPlanId, subjectId, parentTopicId, name, description, priority, difficulty,
                    examIncidencePercent, estimatedMinutes, progressPercent, masteryPercent, status,
                    position, archived, examStageId
                ) VALUES (
                    'topic-child', 'plan-a', 'subject-a', 'topic-a', 'Child', '', 3, 3, NULL, 30, 0, 0,
                    'NOT_STARTED', 1, 0, NULL
                )
                """.trimIndent(),
            )
            assertThrows(SQLiteConstraintException::class.java) {
                migrated.execSQL("UPDATE topics SET subjectId = 'subject-a2' WHERE id = 'topic-a'")
            }

            migrated.execSQL("DELETE FROM exam_stages WHERE id = 'stage-a'")
            migrated.query("SELECT examStageId FROM subjects WHERE id = 'subject-a'").use { cursor ->
                check(cursor.moveToFirst())
                assertNull(cursor.getString(0))
            }
            migrated.query("SELECT examStageId FROM topics WHERE id = 'topic-a'").use { cursor ->
                check(cursor.moveToFirst())
                assertNull(cursor.getString(0))
            }
        }
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(name)
    }


    @Test
    fun migrationRejectsPreexistingInvalidPersistedValues() {
        val name = "studyflow-migration-3-4-invalid-values.db"
        val database = migrationHelper.createDatabase(name, 3)
        insertPlan(database, "plan-a")
        database.execSQL(
            "UPDATE study_plans SET weeklyGoalHours = 0 WHERE id = 'plan-a'",
        )
        database.close()

        assertThrows(IllegalStateException::class.java) {
            migrationHelper.runMigrationsAndValidate(
                name,
                4,
                true,
                StudyFlowMigrations.Migration3To4,
            )
        }
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(name)
    }

    @Test
    fun migrationRejectsPreexistingDuplicateActiveSessions() {
        val name = "studyflow-migration-3-4-duplicate-active.db"
        val database = migrationHelper.createDatabase(name, 3)
        insertPlan(database, "plan-a")
        insertSession(database, "session-a", "plan-a", "RUNNING")
        insertSession(database, "session-b", "plan-a", "PAUSED")
        database.close()

        assertThrows(IllegalStateException::class.java) {
            migrationHelper.runMigrationsAndValidate(
                name,
                4,
                true,
                StudyFlowMigrations.Migration3To4,
            )
        }
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(name)
    }

    private fun insertPlan(db: androidx.sqlite.db.SupportSQLiteDatabase, id: String) {
        db.execSQL(
            """
            INSERT INTO study_plans (
                id, name, description, type, status, examDateIso, weeklyGoalHours, progressPercent,
                studiedMinutesThisWeek, questionsAnswered, averageAccuracyPercent,
                createdAtEpochMillis, updatedAtEpochMillis, trashedAtEpochMillis
            ) VALUES (
                '$id', '$id', '', 'FREE_STUDY', 'ACTIVE', NULL, 10, 0, 0, 0, 0, 1, 1, NULL
            )
            """.trimIndent(),
        )
    }

    private fun insertSession(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        id: String,
        planId: String,
        status: String,
    ) {
        db.execSQL(
            """
            INSERT INTO study_sessions (
                id, studyPlanId, plannedActivityId, subjectId, topicId, materialId, videoResourceId,
                type, status, startedAtEpochMillis, endedAtEpochMillis, grossDurationSeconds,
                pauseDurationSeconds, pagesCompleted, questionsAnswered, correctAnswers,
                concentrationRating, difficultyRating, notes, targetDurationMinutes
            ) VALUES (
                '$id', '$planId', NULL, NULL, NULL, NULL, NULL, 'CUSTOM', '$status',
                1, NULL, 0, 0, 0, 0, 0, NULL, NULL, '', 25
            )
            """.trimIndent(),
        )
    }
}
