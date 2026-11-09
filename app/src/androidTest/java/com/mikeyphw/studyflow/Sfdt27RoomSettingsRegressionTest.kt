package com.mikeyphw.studyflow

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mikeyphw.studyflow.core.database.StudyFlowDatabase
import com.mikeyphw.studyflow.core.database.StudyFlowIntegrity
import com.mikeyphw.studyflow.core.database.entity.StudyPlanEntity
import com.mikeyphw.studyflow.core.database.entity.StudyPlanSettingsEntity
import com.mikeyphw.studyflow.core.database.entity.SubjectEntity
import com.mikeyphw.studyflow.core.database.entity.TopicEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** A real hosted Android/Room test, not mocked SQLite or a UI journey substitute.
 * Isolated database name; no changes to the user's production studyflow.db.
 * The same settings row must survive DAO saves, progress and a cold DB reopen.
 */
@RunWith(AndroidJUnit4::class)
class Sfdt27RoomSettingsRegressionTest {
    private val name = "sfdt27-room-regression.db"
    private lateinit var context: Context
    private lateinit var database: StudyFlowDatabase

    private fun open(): StudyFlowDatabase = Room.databaseBuilder(context, StudyFlowDatabase::class.java, name)
        .addCallback(object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) { StudyFlowIntegrity.install(db) }
            override fun onOpen(db: SupportSQLiteDatabase) { StudyFlowIntegrity.install(db) }
        }).build()

    @Before fun prepare() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.deleteDatabase(name) || !context.getDatabasePath(name).exists())
        database = open()
    }

    @After fun cleanup() {
        database.close()
        context.deleteDatabase(name)
    }

    private fun plan(id: String) = StudyPlanEntity(
        id = id, name = "HF27 Room plan", description = "", type = "FREE_STUDY", status = "ACTIVE",
        examDateIso = null, weeklyGoalHours = 11, progressPercent = 0,
        studiedMinutesThisWeek = 0, questionsAnswered = 0, averageAccuracyPercent = 0,
        createdAtEpochMillis = 1L, updatedAtEpochMillis = 1L, trashedAtEpochMillis = null,
    )

    private fun settings(planId: String) = StudyPlanSettingsEntity(
        studyPlanId = planId, adaptivePlanningEnabled = false,
        reviewNotificationsEnabled = false, strictExamModeEnabled = true,
        preferredSessionMinutes = 83, quietHoursStartMinute = 121,
        quietHoursEndMinute = 743, configurationJson = "{\"private-choice\":\"preserve-exactly\"}",
    )

    private fun subject(planId: String) = SubjectEntity(
        id = "hf27-subject", studyPlanId = planId, name = "Ecology", description = "",
        colorArgb = null, priority = 1, examWeight = 1.0, estimatedMinutes = 120,
        progressPercent = 0, accuracyPercent = 0, status = "NOT_STARTED",
        position = 0, archived = false,
    )

    private fun topic(planId: String) = TopicEntity(
        id = "hf27-topic", studyPlanId = planId, subjectId = "hf27-subject",
        parentTopicId = null, name = "Ecosystems", description = "", priority = 1,
        difficulty = 2, examIncidencePercent = null, estimatedMinutes = 35,
        progressPercent = 0, masteryPercent = 0, status = "NOT_STARTED",
        position = 0, archived = false,
    )

    @Test fun customizedSettingsAndOwnershipSurviveSubjectTopicAndColdRoomReopen() = runBlocking {
        var hf27RoomStage = "PLAN_SETUP"
        try {
        val id = "hf27-plan-a"
        val prefs = settings(id)
        database.studyPlanDao().insert(plan(id))
        database.studySupportDao().upsertPlanSettings(prefs)
        assertEquals(prefs, database.studySupportDao().findPlanSettings(id))

        hf27RoomStage = "SUBJECT_TOPIC"
        database.withTransaction {
            database.studyStructureDao().upsertSubject(subject(id))
            database.studyPlanDao().updateProgress(id, 17, 2L)
            assertEquals(prefs, database.studySupportDao().findPlanSettings(id))
            database.studyStructureDao().upsertTopic(topic(id))
            database.studyPlanDao().updateProgress(id, 29, 3L)
            assertEquals(prefs, database.studySupportDao().findPlanSettings(id))
        }
        assertEquals(id, database.studyStructureDao().findSubject(id, "hf27-subject")?.studyPlanId)
        val persisted = database.studyStructureDao().findTopic(id, "hf27-topic")
        assertNotNull(persisted)
        assertEquals(id, persisted?.studyPlanId)
        assertEquals("hf27-subject", persisted?.subjectId)
        // Exercise the real Room @Update on the parent with a child settings
        // row and real FK triggers enabled. This must never cascade the child.
        hf27RoomStage = "PLAN_UPDATE"
        assertEquals(1, database.studyPlanDao().updateExisting(
            plan(id).copy(progressPercent = 31, updatedAtEpochMillis = 5L),
        ))
        assertEquals(prefs, database.studySupportDao().findPlanSettings(id))
        assertEquals(id, database.studyStructureDao().findSubject(id, "hf27-subject")?.studyPlanId)
        assertEquals(id, database.studyStructureDao().findTopic(id, "hf27-topic")?.studyPlanId)
        hf27RoomStage = "COLD_REOPEN"
        database.close()
        database = open()
        assertEquals(prefs, database.studySupportDao().findPlanSettings(id))
        assertEquals(id, database.studyStructureDao().findSubject(id, "hf27-subject")?.studyPlanId)
        assertEquals(id, database.studyStructureDao().findTopic(id, "hf27-topic")?.studyPlanId)
        assertEquals("hf27-subject", database.studyStructureDao().findTopic(id, "hf27-topic")?.subjectId)
        } catch (failure: Throwable) {
            throw AssertionError("SFDT27_ROOM_STAGE_$hf27RoomStage", failure)
        }
    }

    @Test fun failedTopicTransactionRollsBackWithoutChangingPersonalizedSettings() = runBlocking {
        var hf27RoomStage = "ROLLBACK_SETUP"
        try {
        val id = "hf27-plan-a"
        val prefs = settings(id)
        database.studyPlanDao().insert(plan(id))
        database.studySupportDao().upsertPlanSettings(prefs)
        database.studyStructureDao().upsertSubject(subject(id))
        hf27RoomStage = "ROLLBACK_WRITE"
        val outcome = runCatching {
            database.withTransaction {
                database.studyStructureDao().upsertTopic(topic(id))
                database.studyPlanDao().updateProgress(id, 99, 4L)
                error("HF27_INJECTED_ROLLBACK")
            }
        }
        hf27RoomStage = "ROLLBACK_VERIFY"
        assertTrue(outcome.isFailure)
        assertEquals(prefs, database.studySupportDao().findPlanSettings(id))
        assertNull(database.studyStructureDao().findTopic(id, "hf27-topic"))
        assertEquals(0, database.studyPlanDao().findById(id)?.progressPercent)
        assertEquals(id, database.studyStructureDao().findSubject(id, "hf27-subject")?.studyPlanId)
        } catch (failure: Throwable) {
            throw AssertionError("SFDT27_ROOM_STAGE_$hf27RoomStage", failure)
        }
    }
}
