package com.mikeyphw.studyflow.core.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.room.withTransaction
import com.mikeyphw.studyflow.core.diagnostics.Sfdt27DiagnosticTrace
import com.mikeyphw.studyflow.core.database.StudyFlowDatabase
import com.mikeyphw.studyflow.core.database.StudyPlanGraphDuplicator
import com.mikeyphw.studyflow.core.database.entity.DatabaseMetadataEntity
import com.mikeyphw.studyflow.core.database.entity.StudyPlanEntity
import com.mikeyphw.studyflow.core.database.mapper.asEntity
import com.mikeyphw.studyflow.core.database.mapper.asExternalModel
import com.mikeyphw.studyflow.core.domain.StudyPlanRepository
import com.mikeyphw.studyflow.core.model.AnnexStorageMode
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyPlanSettings
import com.mikeyphw.studyflow.core.model.StudyPlanStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.withLock

internal class RoomStudyPlanRepository(
    context: Context,
    private val database: StudyFlowDatabase,
    private val clock: () -> Long,
    private val diagnosticRecorder: com.mikeyphw.studyflow.core.diagnostics.DiagnosticRecorder = com
        .mikeyphw.studyflow.core.diagnostics.NoOpDiagnosticRecorder,
) : StudyPlanRepository {
    private val appContext = context.applicationContext
    private val studyPlanDao = database.studyPlanDao()
    private val studySupportDao = database.studySupportDao()
    private val libraryDao = database.studyLibraryDao()
    private val physicalResources = PlanPhysicalResourceDuplicator(appContext.filesDir, database)

    /** Only existing-plan UPDATE is permitted here: a new INSERT OR REPLACE of
     * study_plans would cascade-delete personalized study_plan_settings.
     * Never manufacture defaults; rollback on any unexpected settings change.
     * This guard is inside the caller's existing Room transaction. */
    private suspend fun updateExistingPlanPreservingSettings(entity: StudyPlanEntity) {
        val original = requireNotNull(studySupportDao.findPlanSettings(entity.id)) {
            "PLAN_SETTINGS_MISSING_BEFORE_EXISTING_PLAN_UPDATE"
        }
        check(studyPlanDao.updateExisting(entity) == 1) {
            "STUDY_PLAN_EXISTING_UPDATE_MISSING"
        }
        check(studySupportDao.findPlanSettings(entity.id) == original) {
            "PLAN_SETTINGS_CHANGED_DURING_EXISTING_PLAN_UPDATE"
        }
        Sfdt27DiagnosticTrace.record("plan_repository", "PLAN_UPDATED",
            operation = "update_plan", transaction = "write_seen", observer = "production_room",
            plan = "present", settings = "present")
    }

    override val plans: Flow<List<StudyPlan>> =
        studyPlanDao.observeAll().map { entities -> entities.map { it.asExternalModel() } }

    override fun observePlan(planId: StudyPlanId): Flow<StudyPlan?> =
        studyPlanDao.observeById(planId.value).map { entity -> entity?.asExternalModel() }

    override fun observePlanSettings(planId: StudyPlanId): Flow<StudyPlanSettings> =
        studySupportDao.observePlanSettings(planId.value).map { entity ->
            entity?.asExternalModel() ?: StudyPlanSettings.defaults(planId)
        }

    override fun observeStoredPlanSettings(planId: StudyPlanId): Flow<StudyPlanSettings?> =
        studySupportDao.observePlanSettings(planId.value).map { entity -> entity?.asExternalModel() }

    override suspend fun create(
        plan: StudyPlan,
        settings: StudyPlanSettings,
    ) {
        require(settings.studyPlanId == plan.id) { "Plan settings must belong to the created Study Plan." }
        database.withTransaction {
            require(studyPlanDao.countById(plan.id.value) == 0) {
                "A Study Plan with id ${plan.id.value} already exists."
            }
            studyPlanDao.insert(plan.asEntity(nowEpochMillis = clock()))
            studySupportDao.upsertPlanSettings(settings.asEntity())
            Sfdt27DiagnosticTrace.record("plan_repository", "SETTINGS_WRITTEN",
                operation = "insert_settings", transaction = "write_seen", observer = "production_room",
                plan = "present", settings = "present")
        }
        Sfdt27DiagnosticTrace.record("plan_repository", "PLAN_CREATED",
            operation = "create_plan", transaction = "committed", observer = "production_room",
            plan = "present", settings = "present")
    }

    override suspend fun replace(plan: StudyPlan) {
        database.withTransaction {
            val existing = requireNotNull(studyPlanDao.findById(plan.id.value)) {
                "Study Plan ${plan.id.value} does not exist."
            }
            require(existing.status !in setOf(StudyPlanStatus.ARCHIVED.name, StudyPlanStatus.TRASHED.name)) {
                "Archived or trashed Study Plans must be restored before editing."
            }
            val now = clock()
            updateExistingPlanPreservingSettings(
                plan.copy(
                    createdAtEpochMillis = existing.createdAtEpochMillis,
                    updatedAtEpochMillis = now,
                ).asEntity(nowEpochMillis = now),
            )
        }
    }

    override suspend fun duplicate(
        sourcePlanId: StudyPlanId,
        newPlanId: StudyPlanId,
        newName: String,
    ): StudyPlan = PhysicalStorageMutationCoordinator.mutex.withLock {
        val source = requireNotNull(studyPlanDao.findById(sourcePlanId.value)) {
            "Study Plan ${sourcePlanId.value} does not exist."
        }.asExternalModel()
        require(source.status != StudyPlanStatus.TRASHED) {
            "Restore a trashed Study Plan before duplicating it."
        }
        require(studyPlanDao.countById(newPlanId.value) == 0) {
            "A Study Plan with id ${newPlanId.value} already exists."
        }

        val preparedResources = physicalResources.prepare(sourcePlanId.value, newPlanId.value)
        try {
            database.withTransaction {
                // Recheck after physical preparation so a concurrent writer cannot claim the id.
                require(studyPlanDao.countById(newPlanId.value) == 0) {
                    "A Study Plan with id ${newPlanId.value} already exists."
                }
                val now = clock()
                val duplicate = source.copy(
                    id = newPlanId,
                    name = newName,
                    status = StudyPlanStatus.DRAFT,
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now,
                    trashedAtEpochMillis = null,
                )
                studyPlanDao.insert(duplicate.asEntity(nowEpochMillis = now))
                StudyPlanGraphDuplicator(database.openHelper.writableDatabase).duplicate(
                    sourcePlanId = sourcePlanId.value,
                    newPlanId = newPlanId.value,
                )
                if (preparedResources.annexes.isNotEmpty()) {
                    database.studyLibraryDao().upsertAnnexes(preparedResources.annexes)
                }
                if (preparedResources.snapshots.isNotEmpty()) {
                    studySupportDao.upsertSnapshots(preparedResources.snapshots)
                }
                if (studySupportDao.findPlanSettings(newPlanId.value) == null) {
                    studySupportDao.upsertPlanSettings(StudyPlanSettings.defaults(newPlanId).asEntity())
                }
                duplicate
            }
        } catch (error: Throwable) {
            preparedResources.cleanup()
            throw error
        }
    }

    override suspend fun updateStatus(
        planId: StudyPlanId,
        status: StudyPlanStatus,
    ) {
        require(status != StudyPlanStatus.TRASHED) { "Use moveToTrash for trashed Study Plans." }
        database.withTransaction {
            val existing = requireNotNull(studyPlanDao.findById(planId.value)) {
                "Study Plan ${planId.value} does not exist."
            }.asExternalModel()
            require(existing.status != StudyPlanStatus.TRASHED) {
                "Restore a trashed Study Plan before changing its status."
            }
            if (status == StudyPlanStatus.ARCHIVED) requireNoActiveSession(planId)
            val now = clock()
            updateExistingPlanPreservingSettings(
                existing.copy(
                    status = status,
                    updatedAtEpochMillis = now,
                    trashedAtEpochMillis = null,
                ).asEntity(now),
            )
        }
    }

    override suspend fun moveToTrash(planId: StudyPlanId) {
        database.withTransaction {
            val existing = requireNotNull(studyPlanDao.findById(planId.value)) {
                "Study Plan ${planId.value} does not exist."
            }.asExternalModel()
            requireNoActiveSession(planId)
            val now = clock()
            updateExistingPlanPreservingSettings(
                existing.copy(
                    status = StudyPlanStatus.TRASHED,
                    updatedAtEpochMillis = now,
                    trashedAtEpochMillis = now,
                ).asEntity(now),
            )
        }
    }

    override suspend fun restoreFromTrash(planId: StudyPlanId) {
        database.withTransaction {
            val existing = requireNotNull(studyPlanDao.findById(planId.value)) {
                "Study Plan ${planId.value} does not exist."
            }.asExternalModel()
            require(existing.status == StudyPlanStatus.TRASHED) { "Study Plan is not in trash." }
            val now = clock()
            updateExistingPlanPreservingSettings(
                existing.copy(
                    status = StudyPlanStatus.PAUSED,
                    updatedAtEpochMillis = now,
                    trashedAtEpochMillis = null,
                ).asEntity(now),
            )
        }
    }

    override suspend fun updatePlanSettings(settings: StudyPlanSettings) = diagnosticRecorder.recordRepositoryMutation(
        area = com.mikeyphw.studyflow.core.diagnostics.DiagnosticArea.PREFERENCES,
        action = "plan-settings-write",
    ) {
        database.withTransaction {
            val plan = requireNotNull(studyPlanDao.findById(settings.studyPlanId.value)) {
                "Study Plan ${settings.studyPlanId.value} does not exist."
            }
            require(plan.status !in setOf(StudyPlanStatus.ARCHIVED.name, StudyPlanStatus.TRASHED.name)) {
                "Archived or trashed Study Plans must be restored before editing settings."
            }
            studySupportDao.upsertPlanSettings(settings.asEntity())
            Sfdt27DiagnosticTrace.record("plan_repository", "SETTINGS_OVERWRITE",
                operation = "replace_settings", transaction = "write_seen", observer = "production_room",
                plan = "present", settings = "present")
        }

    }

    override suspend fun deletePermanently(planId: StudyPlanId) =
        PhysicalStorageMutationCoordinator.mutex.withLock {
        val linkedPermissions = libraryDao.findAnnexes(planId.value)
            .filter { annex ->
                annex.storageMode == AnnexStorageMode.LINKED_DOCUMENT.name && annex.persistedPermission
            }
            .map { annex -> annex.storageReference }
            .filter(String::isNotBlank)
            .distinct()
        database.withTransaction {
            val existing = requireNotNull(studyPlanDao.findById(planId.value)) {
                "Study Plan ${planId.value} does not exist."
            }.asExternalModel()
            require(existing.status == StudyPlanStatus.TRASHED) {
                "Only trashed Study Plans can be permanently deleted."
            }
            requireNoActiveSession(planId)
            require(studyPlanDao.deleteById(planId.value) == 1) {
                "Study Plan ${planId.value} could not be deleted."
            }
            Sfdt27DiagnosticTrace.record("plan_repository", "PLAN_DELETE",
                operation = "delete_plan", transaction = "write_seen", observer = "production_room")
        }
        // Database ownership is committed first. Only after the cascade is durable may StudyFlow
        // release an app-wide SAF grant or remove app-owned bytes. Shared linked-document grants are
        // retained while any other annex row still references the same URI.
        linkedPermissions.forEach { reference ->
            if (libraryDao.countAnnexesByStorageReference(reference) == 0) {
                val uri = Uri.parse(reference)
                if (uri.scheme.equals("content", ignoreCase = true)) {
                    runCatching {
                        appContext.contentResolver.releasePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION,
                        )
                    }
                }
            }
        }
        physicalResources.deletePlanOwnedFiles(planId.value)
    }

    override suspend fun clear() = PhysicalStorageMutationCoordinator.mutex.withLock {
        val ownership = database.withTransaction {
            capturePhysicalOwnership().also {
                studyPlanDao.deleteAll()
                Sfdt27DiagnosticTrace.record("plan_repository", "PLAN_DELETE",
                    operation = "delete_plan", transaction = "write_seen", observer = "production_room")
            }
        }
        cleanupPhysicalOwnership(ownership)
    }

    override suspend fun restoreFoundationSamples() = PhysicalStorageMutationCoordinator.mutex.withLock {
        val ownership = database.withTransaction {
            val captured = capturePhysicalOwnership()
            studyPlanDao.deleteAll()
            Sfdt27DiagnosticTrace.record("plan_repository", "PLAN_DELETE",
                operation = "delete_plan", transaction = "write_seen", observer = "production_room")
            val now = clock()
            val samples = FoundationStudyPlans.create()
            studyPlanDao.insertAll(samples.map { plan -> plan.asEntity(nowEpochMillis = now) })
            samples.forEach { plan ->
                studySupportDao.upsertPlanSettings(StudyPlanSettings.defaults(plan.id).asEntity())
            }
            captured
        }
        cleanupPhysicalOwnership(ownership)
    }

    suspend fun seedFoundationSamplesOnce() {
        database.withTransaction {
            val metadataDao = database.databaseMetadataDao()
            if (metadataDao.findValue(FOUNDATION_SEED_KEY) == null) {
                if (studyPlanDao.countAll() == 0) {
                    val now = clock()
                    val samples = FoundationStudyPlans.create()
                    studyPlanDao.insertAll(samples.map { plan -> plan.asEntity(nowEpochMillis = now) })
                    samples.forEach { plan ->
                        studySupportDao.upsertPlanSettings(StudyPlanSettings.defaults(plan.id).asEntity())
                    }
                }
                metadataDao.upsert(DatabaseMetadataEntity(FOUNDATION_SEED_KEY, "1"))
            }
            // A one-time foundation seed marker cannot prove that older or
            // restored plans still have their required settings row. Repair
            // only missing rows, on EVERY initialization, inside the SAME
            // Room transaction. Never overwrite personalized settings.
            // The owning Study Plan is present because these ids come from
            // study_plans itself; session creation still validates ownership.
            Sfdt27DiagnosticTrace.record("seed", "SEED_PASS",
                operation = "seed", transaction = "begun", observer = "production_room")
            studyPlanDao.findPlanIdsMissingSettings().forEach { missingId ->
                studySupportDao.insertMissingPlanSettings(
                    StudyPlanSettings.defaults(StudyPlanId(missingId)).asEntity(),
                )
            }
        }
    }


    private suspend fun capturePhysicalOwnership(): PhysicalOwnership {
        val planIds = studyPlanDao.snapshotAll().map { plan -> plan.id }
        val linkedPermissions = planIds.flatMap { planId ->
            libraryDao.findAnnexes(planId)
                .filter { annex ->
                    annex.storageMode == AnnexStorageMode.LINKED_DOCUMENT.name && annex.persistedPermission
                }
                .map { annex -> annex.storageReference }
                .filter(String::isNotBlank)
        }.distinct()
        return PhysicalOwnership(planIds = planIds, linkedPermissions = linkedPermissions)
    }

    private suspend fun cleanupPhysicalOwnership(ownership: PhysicalOwnership) {
        ownership.linkedPermissions.forEach { reference ->
            if (libraryDao.countAnnexesByStorageReference(reference) == 0) {
                val uri = Uri.parse(reference)
                if (uri.scheme.equals("content", ignoreCase = true)) {
                    runCatching {
                        appContext.contentResolver.releasePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION,
                        )
                    }
                }
            }
        }
        for (planId in ownership.planIds) {
            physicalResources.deletePlanOwnedFiles(planId)
        }
    }

    private data class PhysicalOwnership(
        val planIds: List<String>,
        val linkedPermissions: List<String>,
    )

    private suspend fun requireNoActiveSession(planId: StudyPlanId) {
        require(database.studyPlanningDao().findActiveStudySessions(planId.value).isEmpty()) {
            "Finish or cancel the active Study Session before archiving, trashing, or deleting this Study Plan."
        }
    }

    private companion object {
        const val FOUNDATION_SEED_KEY = "foundation_samples_seeded"
    }
}
