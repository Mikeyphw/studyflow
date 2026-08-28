package com.mikeyphw.studyflow.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudyFlowMigration11To12QuestionProvenanceTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        StudyFlowDatabase::class.java,
    )

    @Test
    fun migrate11To12AddsQuestionProvenanceAndIndexes() {
        val name = "cpov06-migration"
        helper.createDatabase(name, 11).close()
        helper.runMigrationsAndValidate(name, 12, true, StudyFlowMigrations.Migration11To12).use { db ->
            db.query("PRAGMA table_info(questions)").use { cursor ->
                val names = buildSet {
                    while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
                }
                val provenanceColumns = setOf(
                    "organization",
                    "role",
                    "examStageId",
                    "authorship",
                    "examNoticeRevisionId",
                    "externalReference",
                    "points",
                )
                check(provenanceColumns.all { it in names })
            }
            db.query("SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='questions'").use { cursor ->
                val indexes = buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
                check("index_questions_studyPlanId_examBoard_year" in indexes)
                check("index_questions_studyPlanId_subjectId_topicId" in indexes)
            }
        }
    }
}
