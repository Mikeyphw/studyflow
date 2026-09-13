package com.mikeyphw.studyflow.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mikeyphw.studyflow.core.database.StudyFlowDatabase
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudyFlowDatabaseSnapshotWriterOv05Test {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun logicalSnapshotIgnoresFtsShadowTablesAndUsesStablePrimaryKeyRowOrder() {
        val first = databaseWithPlans(listOf("b", "a"))
        val second = databaseWithPlans(listOf("a", "b"))
        try {
            val firstBytes = StudyFlowDatabaseSnapshotWriter.capture(first)
            val secondBytes = StudyFlowDatabaseSnapshotWriter.capture(second)
            assertArrayEquals(secondBytes, firstBytes)
            val printable = firstBytes.toString(Charsets.ISO_8859_1)
            assertFalse(printable.contains("studyflow_search_fts"))
            assertFalse(printable.contains("room_master_table"))
        } finally {
            first.close()
            second.close()
        }
    }

    private fun databaseWithPlans(ids: List<String>): StudyFlowDatabase =
        Room.inMemoryDatabaseBuilder(context, StudyFlowDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            .also { database ->
                ids.forEach { id ->
                    database.openHelper.writableDatabase.execSQL(
                        """
                        INSERT INTO study_plans (
                            id, name, description, type, status, examDateIso, weeklyGoalHours, progressPercent,
                            studiedMinutesThisWeek, questionsAnswered, averageAccuracyPercent, createdAtEpochMillis,
                            updatedAtEpochMillis, trashedAtEpochMillis, contentMode
                        ) VALUES (?, ?, '', 'FREE_STUDY', 'ACTIVE', NULL, 10, 0, 0, 0, 0, 1, 1, NULL, 'EXAM_PLAN')
                        """.trimIndent(), arrayOf(id, id),
                    )
                }
            }
}
