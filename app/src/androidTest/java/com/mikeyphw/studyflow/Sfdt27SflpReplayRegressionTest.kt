package com.mikeyphw.studyflow

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mikeyphw.studyflow.core.database.StudyFlowIntegrity
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mikeyphw.studyflow.core.data.createSfdt27DebugSflpReplayBridge
import com.mikeyphw.studyflow.core.database.StudyFlowDatabase
import com.mikeyphw.studyflow.core.database.entity.StudyPlanEntity
import com.mikeyphw.studyflow.core.database.entity.StudyPlanSettingsEntity
import com.mikeyphw.studyflow.core.database.entity.SubjectEntity
import com.mikeyphw.studyflow.core.database.entity.TopicEntity
import com.mikeyphw.studyflow.core.link.StudyFlowDeviceId
import com.mikeyphw.studyflow.core.link.StudyFlowDomainOperation
import com.mikeyphw.studyflow.core.link.StudyFlowEntityRevision
import com.mikeyphw.studyflow.core.link.StudyFlowOperationId
import com.mikeyphw.studyflow.core.link.StudyFlowProductMutation
import com.mikeyphw.studyflow.core.link.StudyFlowProductStateBridge
import com.mikeyphw.studyflow.core.link.StudyFlowProductStateContract
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Rule
import org.junit.rules.TestName
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real, disposable hosted Android SFLP replay against the SAME production bridge
 * used by background reconciliation. No mock DAO, SQLite in-memory substitute,
 * synthetic pass, temporary defaults, or conditional skip is permitted.
 */
@RunWith(AndroidJUnit4::class)
class Sfdt27SflpReplayRegressionTest {
    private lateinit var context: Context
    private lateinit var database: StudyFlowDatabase
    private lateinit var bridge: StudyFlowProductStateBridge
    private val existingId = "sfdt27-sflp-existing"
    private val newId = "sfdt27-sflp-new"
    private val workspace = StudyFlowProductStateContract.WorkspaceId
    private val isolatedDatabaseName = "sfdt27-sflp-regression.db"
    @get:Rule val testName = TestName()

    private val allowedTests = setOf(
        "existingPlanReplayPreservesSettingsAndOwnedContentAfterColdReopen",
        "noncanonicalPeerKeysAndValuesFailWithoutMutation",
        "newPlanReplayInsertsAndExplicitDeleteStillWorks",
    )
    private val allowedStages = setOf(
        "SFLP_SETUP", "SFLP_FIXTURE", "SFLP_BRIDGE", "SFLP_EXISTING",
        "SFLP_CAPTURE", "SFLP_REPLAY", "SFLP_VERIFY", "SFLP_REOPEN",
        "SFLP_TAMPER", "SFLP_NEW", "SFLP_INSERT", "SFLP_DELETE",
        "SFLP_SAVE", "SFLP_CLEANUP", "SFLP_CLOSE", "SFLP_DELETE_DB",
        "SFLP_COMPLETE",
    )

    // An instrumented test writes ONLY fixed categories to app-private storage.
    // The host reads and validates this separately from JUnit's stack formatting.
    private fun recordStage(stage: String, status: String = "ENTER") {
        require(stage in allowedStages && status in setOf("ENTER", "FAIL", "PASS"))
        val method = requireNotNull(testName.methodName)
        require(method in allowedTests)
        val reset = stage == "SFLP_SETUP" && status == "ENTER"
        val mode = if (reset) Context.MODE_PRIVATE else Context.MODE_APPEND
        context.openFileOutput("sfdt27-sflp-stage-v1.txt", mode).use { output ->
            if (reset) output.write("SFLP_V1\n".toByteArray(Charsets.US_ASCII))
            output.write("$method|$stage|$status\n".toByteArray(Charsets.US_ASCII))
        }
    }

    private fun openIsolatedDatabase(): StudyFlowDatabase = Room.databaseBuilder(
        context, StudyFlowDatabase::class.java, isolatedDatabaseName,
    ).addCallback(object : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) { StudyFlowIntegrity.install(db) }
        override fun onOpen(db: SupportSQLiteDatabase) { StudyFlowIntegrity.install(db) }
    }).build()

    @Before fun prepare() {
        var stage = "SFLP_SETUP"
        try {
            context = InstrumentationRegistry.getInstrumentation().targetContext
            recordStage(stage)
            stage = "SFLP_FIXTURE"
            recordStage(stage)
            check(context.deleteDatabase(isolatedDatabaseName) ||
                !context.getDatabasePath(isolatedDatabaseName).exists())
            database = openIsolatedDatabase()
            cleanFixture()
            stage = "SFLP_BRIDGE"
            recordStage(stage)
            bridge = createSfdt27DebugSflpReplayBridge(database)
        } catch (failure: Throwable) {
            if (::context.isInitialized) recordStage(stage, "FAIL")
            throw AssertionError("SFDT27_ROOM_STAGE_$stage", failure)
        }
    }

    @After fun cleanup() {
        if (!::context.isInitialized) return
        var stage = "SFLP_CLEANUP"
        try {
            recordStage(stage)
            if (::database.isInitialized && database.isOpen) cleanFixture()
            stage = "SFLP_CLOSE"
            recordStage(stage)
            if (::database.isInitialized) database.close()
            stage = "SFLP_DELETE_DB"
            recordStage(stage)
            check(context.deleteDatabase(isolatedDatabaseName) ||
                !context.getDatabasePath(isolatedDatabaseName).exists())
            recordStage("SFLP_COMPLETE", "PASS")
        } catch (failure: Throwable) {
            recordStage(stage, "FAIL")
            throw AssertionError("SFDT27_ROOM_STAGE_$stage", failure)
        }
    }

    private fun cleanFixture() {
        database.openHelper.writableDatabase.execSQL(
            "DELETE FROM study_plans WHERE id IN (?, ?)", arrayOf(existingId, newId),
        )
    }

    private fun plan(id: String) = StudyPlanEntity(
        id = id, name = "SFLP Replay Regression", description = "", type = "FREE_STUDY",
        status = "ACTIVE", examDateIso = null, weeklyGoalHours = 11,
        progressPercent = 0, studiedMinutesThisWeek = 0, questionsAnswered = 0,
        averageAccuracyPercent = 0, createdAtEpochMillis = 1L,
        updatedAtEpochMillis = 1L, trashedAtEpochMillis = null,
    )

    private fun settings(id: String) = StudyPlanSettingsEntity(
        studyPlanId = id, adaptivePlanningEnabled = false,
        reviewNotificationsEnabled = false, strictExamModeEnabled = true,
        preferredSessionMinutes = 83, quietHoursStartMinute = 121,
        quietHoursEndMinute = 743,
        configurationJson = "{\"local-customized\":\"must-survive\"}",
    )

    private fun subject(id: String) = SubjectEntity(
        id = "hf27-sflp-subject", studyPlanId = id, name = "Ecology",
        description = "", colorArgb = null, priority = 1, examWeight = 1.0,
        estimatedMinutes = 120, progressPercent = 0, accuracyPercent = 0,
        status = "NOT_STARTED", position = 0, archived = false,
    )

    private fun topic(id: String) = TopicEntity(
        id = "hf27-sflp-topic", studyPlanId = id, subjectId = "hf27-sflp-subject",
        parentTopicId = null, name = "Ecosystems", description = "",
        priority = 1, difficulty = 2, examIncidencePercent = null,
        estimatedMinutes = 35, progressPercent = 0, masteryPercent = 0,
        status = "NOT_STARTED", position = 0, archived = false,
    )

    private fun mutationFor(id: String): StudyFlowProductMutation =
        bridge.captureLocalMutations(workspace).single { mutation ->
            mutation.operationType == StudyFlowProductStateContract.RowUpsert &&
                mutation.payload["table"]?.jsonPrimitive?.content == "study_plans" &&
                mutation.payload["primaryKey"]?.jsonObject?.get("id")?.jsonObject
                    ?.get("value")?.jsonPrimitive?.content == id
        }

    private fun replay(
        mutation: StudyFlowProductMutation,
        sequence: Long,
        payload: JsonObject = mutation.payload,
    ): StudyFlowDomainOperation = StudyFlowDomainOperation(
        operationId = StudyFlowOperationId("sfdt27-sflp-test-$sequence"),
        workspaceId = workspace,
        originDeviceId = StudyFlowDeviceId("sfdt27-test-device"),
        originSequence = sequence,
        entityId = mutation.entityId,
        entityRevision = StudyFlowEntityRevision(sequence),
        logicalTimestamp = sequence,
        operationType = mutation.operationType,
        occurredAtEpochMillis = sequence,
        payload = payload,
    )

    @Test fun existingPlanReplayPreservesSettingsAndOwnedContentAfterColdReopen() = runBlocking {
        var stage = "SFLP_EXISTING"
        recordStage(stage)
        try {
            bridge.captureLocalMutations(workspace)
            stage = "SFLP_FIXTURE"
            recordStage(stage)
            database.studyPlanDao().insert(plan(existingId))
            val originalSettings = settings(existingId)
            database.studySupportDao().upsertPlanSettings(originalSettings)
            database.studyStructureDao().upsertSubject(subject(existingId))
            database.studyStructureDao().upsertTopic(topic(existingId))
            stage = "SFLP_CAPTURE"
            recordStage(stage)
            val mutation = mutationFor(existingId)
            stage = "SFLP_REPLAY"
            recordStage(stage)
            val remote = replay(mutation, 1L)
            assertEquals(1, bridge.projectResolved(workspace, listOf(remote)))
            assertEquals(0, bridge.projectResolved(workspace, listOf(remote)))
            stage = "SFLP_VERIFY"
            recordStage(stage)
            assertEquals(originalSettings, database.studySupportDao().findPlanSettings(existingId))
            assertNotNull(database.studyStructureDao().findSubject(existingId, "hf27-sflp-subject"))
            assertNotNull(database.studyStructureDao().findTopic(existingId, "hf27-sflp-topic"))
            stage = "SFLP_REOPEN"
            recordStage(stage)
            database.close()
            database = openIsolatedDatabase()
            assertEquals(originalSettings, database.studySupportDao().findPlanSettings(existingId))
            assertNotNull(database.studyStructureDao().findSubject(existingId, "hf27-sflp-subject"))
            assertNotNull(database.studyStructureDao().findTopic(existingId, "hf27-sflp-topic"))
        } catch (failure: Throwable) {
            recordStage(stage, "FAIL")
            throw AssertionError("SFDT27_ROOM_STAGE_$stage", failure)
        }
    }

    @Test fun noncanonicalPeerKeysAndValuesFailWithoutMutation() = runBlocking {
        var stage = "SFLP_TAMPER"
        recordStage(stage)
        try {
            bridge.captureLocalMutations(workspace)
            database.studyPlanDao().insert(plan(existingId))
            val originalSettings = settings(existingId)
            database.studySupportDao().upsertPlanSettings(originalSettings)
            stage = "SFLP_CAPTURE"
            recordStage(stage)
            val mutation = mutationFor(existingId)
            stage = "SFLP_TAMPER"
            recordStage(stage)
            val wrongId = buildJsonObject {
                put("type", "string")
                put("value", "sfdt27-not-the-original-id")
            }
            val values = JsonObject(mutation.payload.getValue("values").jsonObject.toMutableMap().apply {
                put("id", wrongId)
            })
            val tampered = JsonObject(mutation.payload.toMutableMap().apply {
                put("values", values)
            })
            assertTrue(runCatching { bridge.projectResolved(workspace, listOf(replay(mutation, 2L, tampered))) }.isFailure)
            val forgedKeys = JsonObject(mutation.payload.getValue("primaryKey").jsonObject.toMutableMap().apply {
                put("extra_column", JsonPrimitive("not-allowed"))
            })
            val forged = JsonObject(mutation.payload.toMutableMap().apply {
                put("primaryKey", forgedKeys)
            })
            assertTrue(runCatching { bridge.projectResolved(workspace, listOf(replay(mutation, 3L, forged))) }.isFailure)
            assertEquals(originalSettings, database.studySupportDao().findPlanSettings(existingId))
            assertEquals(1, database.studyPlanDao().countById(existingId))
        } catch (failure: Throwable) {
            recordStage(stage, "FAIL")
            throw AssertionError("SFDT27_ROOM_STAGE_$stage", failure)
        }
    }

    @Test fun newPlanReplayInsertsAndExplicitDeleteStillWorks() = runBlocking {
        var stage = "SFLP_NEW"
        recordStage(stage)
        try {
            bridge.captureLocalMutations(workspace)
            database.studyPlanDao().insert(plan(newId))
            stage = "SFLP_CAPTURE"
            recordStage(stage)
            val mutation = mutationFor(newId)
            database.studyPlanDao().deleteById(newId)
            stage = "SFLP_INSERT"
            recordStage(stage)
            val insert = replay(mutation, 4L)
            assertEquals(1, bridge.projectResolved(workspace, listOf(insert)))
            assertEquals(1, database.studyPlanDao().countById(newId))
            stage = "SFLP_DELETE"
            recordStage(stage)
            // SFLP type is authoritative, not a payload-supplied operationType.
            val deleted = StudyFlowDomainOperation(
                operationId = StudyFlowOperationId("sfdt27-sflp-test-5"),
                workspaceId = workspace,
                originDeviceId = StudyFlowDeviceId("sfdt27-test-device"),
                originSequence = 5L,
                entityId = mutation.entityId,
                entityRevision = StudyFlowEntityRevision(5L),
                logicalTimestamp = 5L,
                operationType = StudyFlowProductStateContract.RowDelete,
                occurredAtEpochMillis = 5L,
                kind = com.mikeyphw.studyflow.core.link.StudyFlowOperationKind.TOMBSTONE,
                payload = mutation.payload,
            )
            assertEquals(1, bridge.projectResolved(workspace, listOf(deleted)))
            assertEquals(0, database.studyPlanDao().countById(newId))
        } catch (failure: Throwable) {
            recordStage(stage, "FAIL")
            throw AssertionError("SFDT27_ROOM_STAGE_$stage", failure)
        }
    }
}
