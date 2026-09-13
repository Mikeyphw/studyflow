package com.mikeyphw.studyflow.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudyFlowMigration13To14IntegrityTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        StudyFlowDatabase::class.java,
    )

    @Test
    fun migrationReplacesLegacyTriggersWithOv04Semantics() {
        val name = "ov04-integrity-upgrade"
        helper.createDatabase(name, 13).use { db ->
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS guard_study_sessions_single_active_insert " +
                    "BEFORE INSERT ON study_sessions BEGIN SELECT 1; END",
            )
        }
        helper.runMigrationsAndValidate(name, 14, true, StudyFlowMigrations.Migration13To14).use { db ->
            val activeSql = db.query(
                "SELECT sql FROM sqlite_master WHERE type='trigger' " +
                    "AND name='guard_study_sessions_single_active_insert'",
            ).use { cursor -> check(cursor.moveToFirst()); cursor.getString(0) }
            assertTrue(activeSql.contains("only one active Study Session is allowed globally"))
            assertFalse(activeSql.contains("active.studyPlanId = NEW.studyPlanId"))

            val modernRevision = db.query(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='trigger' " +
                    "AND name='studyflow_graph_revision_language_review_attempts_insert'",
            ).use { cursor -> check(cursor.moveToFirst()); cursor.getInt(0) }
            assertTrue(modernRevision == 1)
        }
    }
}
