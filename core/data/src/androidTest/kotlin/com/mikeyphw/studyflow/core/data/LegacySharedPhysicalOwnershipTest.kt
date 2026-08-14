package com.mikeyphw.studyflow.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mikeyphw.studyflow.core.database.StudyFlowDatabase
import com.mikeyphw.studyflow.core.database.entity.AnnexEntity
import com.mikeyphw.studyflow.core.database.entity.PlanSnapshotEntity
import com.mikeyphw.studyflow.core.database.entity.StudyPlanEntity
import com.mikeyphw.studyflow.core.model.AnnexId
import com.mikeyphw.studyflow.core.model.StudyPlanId
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LegacySharedPhysicalOwnershipTest {
    private lateinit var context: Context
    private lateinit var database: StudyFlowDatabase
    private val ownedPlanIds = mutableListOf<String>()

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
        ownedPlanIds.forEach { planId ->
            File(context.filesDir, "shareable-annexes/$planId").deleteRecursively()
            File(context.filesDir, "study-plans/$planId").deleteRecursively()
        }
    }

    @Test
    fun annexDeleteAndOrphanCleanupPreserveAnotherPlansLegacySharedReference() = runBlocking {
        val planA = newPlanId("owner")
        val planB = newPlanId("survivor")
        database.studyPlanDao().insert(plan(planA))
        database.studyPlanDao().insert(plan(planB))

        val shared = File(context.filesDir, "shareable-annexes/$planA/annex-a/shared.bin")
            .apply { parentFile?.mkdirs(); writeText("shared") }
        val reference = shared.relativeTo(context.filesDir).invariantSeparatorsPath
        database.studyLibraryDao().upsertAnnex(annex("annex-a", planA, reference, shared.length()))
        database.studyLibraryDao().upsertAnnex(annex("annex-b", planB, reference, shared.length()))

        val repository = RoomAnnexFileStorageRepository(context, database, clock = { 1_000L })
        repository.deleteStoredAnnex(StudyPlanId(planA), AnnexId("annex-a"))
        assertTrue("Another plan still references the imported copy", shared.isFile)

        repository.cleanupOrphanedAnnexFiles(StudyPlanId(planA))
        assertTrue("Orphan cleanup must use global imported-copy references", shared.isFile)

        repository.deleteStoredAnnex(StudyPlanId(planB), AnnexId("annex-b"))
        assertFalse("The final reference may delete the app-owned bytes", shared.exists())
    }

    @Test
    fun legacyMigrationCopiesEverySharedRowBeforeRemovingTheOldSource() = runBlocking {
        val planA = newPlanId("legacy-owner")
        val planB = newPlanId("legacy-copy")
        database.studyPlanDao().insert(plan(planA))
        database.studyPlanDao().insert(plan(planB))

        val source = File(context.filesDir, "study-plans/$planA/annexes/shared/source.bin")
            .apply { parentFile?.mkdirs(); writeText("legacy") }
        val legacyReference = source.relativeTo(context.filesDir).invariantSeparatorsPath
        database.studyLibraryDao().upsertAnnex(annex("annex-a", planA, legacyReference, source.length()))
        database.studyLibraryDao().upsertAnnex(annex("annex-b", planB, legacyReference, source.length()))

        val repository = RoomAnnexFileStorageRepository(context, database, clock = { 2_000L })
        assertEquals(2, repository.migrateOwnedAnnexesToShareableRoot())

        val migratedA = requireNotNull(database.studyLibraryDao().findAnnex(planA, "annex-a"))
        val migratedB = requireNotNull(database.studyLibraryDao().findAnnex(planB, "annex-b"))
        assertNotEquals(migratedA.storageReference, migratedB.storageReference)
        assertTrue(File(context.filesDir, migratedA.storageReference).isFile)
        assertTrue(File(context.filesDir, migratedB.storageReference).isFile)
        assertFalse("Legacy bytes are removed only after the last old reference migrates", source.exists())
    }

    @Test
    fun planPhysicalCleanupPreservesSurvivingCrossPlanAnnexAndSnapshotReferences() = runBlocking {
        val planA = newPlanId("physical-owner")
        val planB = newPlanId("physical-survivor")
        database.studyPlanDao().insert(plan(planA))
        database.studyPlanDao().insert(plan(planB))

        val annexFile = File(context.filesDir, "shareable-annexes/$planA/annex/shared.bin")
            .apply { parentFile?.mkdirs(); writeText("annex") }
        val snapshotFile = File(context.filesDir, "study-plans/$planA/snapshots/shared.studyplan")
            .apply { parentFile?.mkdirs(); writeText("snapshot") }
        val annexReference = annexFile.relativeTo(context.filesDir).invariantSeparatorsPath
        val snapshotReference = snapshotFile.relativeTo(context.filesDir).invariantSeparatorsPath

        database.studyLibraryDao().upsertAnnex(annex("survivor-annex", planB, annexReference, annexFile.length()))
        database.studySupportDao().upsertSnapshots(
            listOf(
                PlanSnapshotEntity(
                    id = "survivor-snapshot",
                    studyPlanId = planB,
                    kind = "MANUAL",
                    label = "Legacy shared snapshot",
                    schemaVersion = 1,
                    payloadReference = snapshotReference,
                    checksumSha256 = "unused-by-cleanup",
                    createdAtEpochMillis = 1L,
                ),
            ),
        )

        val duplicator = PlanPhysicalResourceDuplicator(context.filesDir, database)
        duplicator.deletePlanOwnedFiles(planA)
        assertTrue(annexFile.isFile)
        assertTrue(snapshotFile.isFile)

        assertEquals(1, database.studyLibraryDao().deleteAnnex(planB, "survivor-annex"))
        assertEquals(1, database.studySupportDao().deleteSnapshot("survivor-snapshot"))
        duplicator.deletePlanOwnedFiles(planA)
        assertFalse(annexFile.exists())
        assertFalse(snapshotFile.exists())
    }

    private fun newPlanId(prefix: String): String = "$prefix-${UUID.randomUUID()}".also(ownedPlanIds::add)

    private fun annex(id: String, planId: String, reference: String, byteSize: Long) = AnnexEntity(
        id = id,
        studyPlanId = planId,
        subjectId = null,
        topicId = null,
        materialId = null,
        displayName = "$id.bin",
        mimeType = "application/octet-stream",
        byteSize = byteSize,
        checksumSha256 = null,
        storageMode = "IMPORTED_COPY",
        storageReference = reference,
        persistedPermission = false,
        createdAtEpochMillis = 1L,
        updatedAtEpochMillis = 1L,
    )

    private fun plan(id: String) = StudyPlanEntity(
        id = id,
        name = id,
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
