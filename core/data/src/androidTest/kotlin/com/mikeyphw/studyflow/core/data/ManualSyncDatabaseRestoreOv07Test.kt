package com.mikeyphw.studyflow.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mikeyphw.studyflow.core.database.StudyFlowDatabase
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ManualSyncDatabaseRestoreOv07Test {
    private lateinit var context: Context
    private lateinit var database: StudyFlowDatabase
    private lateinit var snapshotFile: File

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, StudyFlowDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        snapshotFile = File.createTempFile("studyflow-ov07-", ".snapshot", context.cacheDir)
    }

    @After
    fun tearDown() {
        database.close()
        snapshotFile.delete()
    }

    @Test
    fun logicalSnapshotRestoreReplacesMutatedGraphAtomically() = runBlocking {
        database.openHelper.writableDatabase.execSQL(
            """
            INSERT INTO study_plans (
                id, name, description, type, status, examDateIso, weeklyGoalHours, progressPercent,
                studiedMinutesThisWeek, questionsAnswered, averageAccuracyPercent, createdAtEpochMillis,
                updatedAtEpochMillis, trashedAtEpochMillis, contentMode
            ) VALUES (?, ?, '', 'FREE_STUDY', 'ACTIVE', NULL, 10, 0, 0, 0, 0, 1, 1, NULL, 'EXAM_PLAN')
            """.trimIndent(),
            arrayOf("plan-restore", "Before restore"),
        )
        snapshotFile.writeBytes(StudyFlowDatabaseSnapshotWriter.capture(database))
        database.openHelper.writableDatabase.execSQL(
            "UPDATE study_plans SET name = 'After mutation' WHERE id = 'plan-restore'",
        )
        assertEquals("After mutation", database.studyPlanDao().findById("plan-restore")?.name)

        StudyFlowDatabaseSnapshotRestorer.restore(database, snapshotFile, emptyList())

        assertEquals("Before restore", database.studyPlanDao().findById("plan-restore")?.name)
    }
}
