package com.mikeyphw.studyflow.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mikeyphw.studyflow.core.database.StudyFlowDatabase
import com.mikeyphw.studyflow.core.database.entity.StudyPlanEntity
import com.mikeyphw.studyflow.core.model.PlanSnapshotKind
import com.mikeyphw.studyflow.core.model.AnnexStorageMode
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.portability.StudyPlanExportOptions
import com.mikeyphw.studyflow.core.portability.StudyPlanImportStrategy
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudyPlanPortabilityAtomicRestoreTest {
    private lateinit var context: Context
    private lateinit var database: StudyFlowDatabase

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, StudyFlowDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun constructingPortabilityRepositoryRemovesStaleAuthenticatedPlaintextTemp() {
        val stale = java.io.File.createTempFile(
            "studyflow-authenticated-restore-",
            ".studyplan",
            context.cacheDir,
        ).apply { writeText("sensitive plaintext") }

        RoomStudyPlanPortabilityRepository(context, database, clock = { 1_000L })

        assertTrue("Stale authenticated restore plaintext must be removed at startup", !stale.exists())
    }


    @Test
    fun repeatedAnnexBearingSnapshotRestoreAtomicallyReplacesExistingDestination() = runBlocking {
        val planId = StudyPlanId("plan")
        database.studyPlanDao().insert(plan(name = "Snapshot annex state"))
        val storage = RoomAnnexFileStorageRepository(context, database, clock = { 1_000L })
        val annex = storage.importAnnexCopy(
            studyPlanId = planId,
            subjectId = null,
            topicId = null,
            materialId = null,
            displayName = "evidence.txt",
            mimeType = "text/plain",
            declaredByteSize = 8L,
            input = ByteArrayInputStream("original".encodeToByteArray()),
        )
        assertEquals(AnnexStorageMode.IMPORTED_COPY, annex.storageMode)
        val portability = RoomStudyPlanPortabilityRepository(context, database, clock = { 2_000L })
        val snapshot = portability.createSnapshot(planId, PlanSnapshotKind.MANUAL, "Annex snapshot")
        val stored = requireNotNull(database.studyLibraryDao().findAnnex(planId.value, annex.id.value))
        val destination = java.io.File(context.filesDir, stored.storageReference)

        destination.writeText("changed-one")
        portability.restoreSnapshot(snapshot.id)
        assertEquals("original", destination.readText())

        destination.writeText("changed-two")
        portability.restoreSnapshot(snapshot.id)
        assertEquals("original", destination.readText())
    }


    @Test
    fun snapshotDeletionRemovesPrivateBytesAndUsesSanitizedPlanPath() = runBlocking {
        val unsafePlanId = StudyPlanId("plan/unsafe")
        database.studyPlanDao().insert(plan(name = "Unsafe id").copy(id = unsafePlanId.value))
        val portability = RoomStudyPlanPortabilityRepository(context, database, clock = { 3_000L })
        val snapshot = portability.createSnapshot(unsafePlanId, PlanSnapshotKind.MANUAL, "Delete me")
        val stored = requireNotNull(database.studySupportDao().findSnapshot(snapshot.id.value))
        val snapshotFile = java.io.File(context.filesDir, stored.payloadReference)

        assertTrue(snapshotFile.isFile)
        assertTrue(stored.payloadReference.startsWith("study-plans/plan_unsafe/snapshots/"))

        portability.deleteSnapshot(snapshot.id)

        assertTrue(database.studySupportDao().findSnapshot(snapshot.id.value) == null)
        assertTrue(!snapshotFile.exists())
    }

    @Test
    fun failureImmediatelyAfterStagingAlwaysRemovesRestoreStagingDirectory() = runBlocking {
        val planId = StudyPlanId("plan")
        database.studyPlanDao().insert(plan(name = "Exported state"))
        val exporter = RoomStudyPlanPortabilityRepository(context, database, clock = { 1_000L })
        val packageBytes = ByteArrayOutputStream().also { output ->
            exporter.exportPlan(planId, StudyPlanExportOptions(), output, password = null)
        }.toByteArray()
        val before = restoreStageDirectories()
        val importer = RoomStudyPlanPortabilityRepository(
            context = context,
            database = database,
            clock = { 2_000L },
            restoreFailureHook = { point ->
                if (point == RoomStudyPlanPortabilityRepository.RestoreFailurePoint.AFTER_STAGING) {
                    error("Injected post-staging failure")
                }
            },
        )

        val result = runCatching {
            importer.importPlan(
                input = ByteArrayInputStream(packageBytes),
                strategy = StudyPlanImportStrategy.REPLACE_EXISTING,
                targetPlanId = planId,
                password = null,
            )
        }

        assertTrue(result.isFailure)
        assertEquals(before, restoreStageDirectories())
        assertEquals("Exported state", database.studyPlanDao().findById(planId.value)?.name)
    }

    @Test
    fun failureBeforeDatabaseCommitRollsBackReplacementAndRetainsRecoverySnapshot() = runBlocking {
        val planId = StudyPlanId("plan")
        database.studyPlanDao().insert(plan(name = "Exported state"))
        val exporter = RoomStudyPlanPortabilityRepository(context, database, clock = { 1_000L })
        val packageBytes = ByteArrayOutputStream().also { output ->
            exporter.exportPlan(planId, StudyPlanExportOptions(), output, password = null)
        }.toByteArray()

        database.studyPlanDao().upsert(plan(name = "Current state"))
        val importer = RoomStudyPlanPortabilityRepository(
            context = context,
            database = database,
            clock = { 2_000L },
            restoreFailureHook = { point ->
                if (point == RoomStudyPlanPortabilityRepository.RestoreFailurePoint.BEFORE_DATABASE_COMMIT) {
                    error("Injected restore failure")
                }
            },
        )

        runCatching {
            importer.importPlan(
                input = ByteArrayInputStream(packageBytes),
                strategy = StudyPlanImportStrategy.REPLACE_EXISTING,
                targetPlanId = planId,
                password = null,
            )
        }.onSuccess { error("Injected restore failure should abort the replacement") }

        assertEquals("Current state", database.studyPlanDao().findById(planId.value)?.name)
        val snapshots = database.studySupportDao().findSnapshots(planId.value)
        assertTrue(snapshots.any { it.kind == PlanSnapshotKind.PRE_IMPORT.name })
        snapshots.forEach { snapshot ->
            val file = java.io.File(context.filesDir, snapshot.payloadReference)
            assertTrue("Recovery snapshot package must remain durable after failure", file.isFile)
        }
    }

    private fun restoreStageDirectories(): Set<String> = context.cacheDir.listFiles()
        .orEmpty()
        .filter { it.isDirectory && it.name.startsWith("studyflow-restore-stage-") }
        .mapTo(linkedSetOf()) { it.name }

    private fun plan(name: String) = StudyPlanEntity(
        id = "plan",
        name = name,
        description = "",
        type = "FREE_STUDY",
        status = "ACTIVE",
        examDateIso = null,
        weeklyGoalHours = 10,
        progressPercent = 0,
        studiedMinutesThisWeek = 0,
        questionsAnswered = 0,
        averageAccuracyPercent = 0,
        createdAtEpochMillis = 1L,
        updatedAtEpochMillis = 1L,
        trashedAtEpochMillis = null,
    )
}
