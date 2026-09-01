package com.mikeyphw.studyflow.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudyFlowMigration12To13LegislationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        StudyFlowDatabase::class.java,
    )

    @Test
    fun migrationCreatesStructuredLegislationTables() {
        helper.createDatabase("cpov09-migration", 12).close()
        helper.runMigrationsAndValidate("cpov09-migration", 13, true, StudyFlowMigrations.Migration12To13).use { db ->
            db.query(
                "SELECT name FROM sqlite_master WHERE type='table' AND name LIKE 'legislation_%' ORDER BY name",
            ).use { cursor ->
                val names = mutableListOf<String>()
                while (cursor.moveToNext()) names += cursor.getString(0)
                assertEquals(
                    listOf("legislation_documents", "legislation_evidence_links", "legislation_provisions"),
                    names,
                )
            }
        }
    }
}
