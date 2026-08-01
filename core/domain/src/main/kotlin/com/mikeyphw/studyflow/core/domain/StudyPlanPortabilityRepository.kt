package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.PlanSnapshot
import com.mikeyphw.studyflow.core.model.PlanSnapshotId
import com.mikeyphw.studyflow.core.model.PlanSnapshotKind
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.portability.StudyPlanBackupIntegrityReport
import com.mikeyphw.studyflow.core.portability.StudyPlanExportOptions
import com.mikeyphw.studyflow.core.portability.StudyPlanImportPreview
import com.mikeyphw.studyflow.core.portability.StudyPlanImportResult
import com.mikeyphw.studyflow.core.portability.StudyPlanImportStrategy
import com.mikeyphw.studyflow.core.portability.StudyPlanRestoreDryRun
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.flow.Flow

interface StudyPlanPortabilityRepository {
    fun observeSnapshots(planId: StudyPlanId): Flow<List<PlanSnapshot>>

    suspend fun exportPlan(
        planId: StudyPlanId,
        options: StudyPlanExportOptions,
        output: OutputStream,
        password: CharArray? = null,
    )

    suspend fun previewImport(
        input: InputStream,
        password: CharArray? = null,
    ): StudyPlanImportPreview

    suspend fun inspectBackup(
        input: InputStream,
        password: CharArray? = null,
    ): StudyPlanBackupIntegrityReport

    suspend fun dryRunImport(
        input: InputStream,
        strategy: StudyPlanImportStrategy,
        targetPlanId: StudyPlanId? = null,
        password: CharArray? = null,
    ): StudyPlanRestoreDryRun

    suspend fun importPlan(
        input: InputStream,
        strategy: StudyPlanImportStrategy,
        targetPlanId: StudyPlanId? = null,
        password: CharArray? = null,
    ): StudyPlanImportResult

    suspend fun createSnapshot(
        planId: StudyPlanId,
        kind: PlanSnapshotKind,
        label: String,
    ): PlanSnapshot

    suspend fun restoreSnapshot(snapshotId: PlanSnapshotId): StudyPlanImportResult

    suspend fun deleteSnapshot(snapshotId: PlanSnapshotId)
}

class ManageStudyPlanPortability(
    private val repository: StudyPlanPortabilityRepository,
) {
    fun observeSnapshots(planId: StudyPlanId): Flow<List<PlanSnapshot>> =
        repository.observeSnapshots(planId)

    suspend fun exportPlan(
        planId: StudyPlanId,
        options: StudyPlanExportOptions,
        output: OutputStream,
        password: CharArray? = null,
    ) = repository.exportPlan(planId, options, output, password)

    suspend fun previewImport(
        input: InputStream,
        password: CharArray? = null,
    ): StudyPlanImportPreview = repository.previewImport(input, password)

    suspend fun inspectBackup(
        input: InputStream,
        password: CharArray? = null,
    ): StudyPlanBackupIntegrityReport = repository.inspectBackup(input, password)

    suspend fun dryRunImport(
        input: InputStream,
        strategy: StudyPlanImportStrategy,
        targetPlanId: StudyPlanId? = null,
        password: CharArray? = null,
    ): StudyPlanRestoreDryRun = repository.dryRunImport(input, strategy, targetPlanId, password)

    suspend fun importPlan(
        input: InputStream,
        strategy: StudyPlanImportStrategy,
        targetPlanId: StudyPlanId? = null,
        password: CharArray? = null,
    ): StudyPlanImportResult = repository.importPlan(input, strategy, targetPlanId, password)

    suspend fun createSnapshot(
        planId: StudyPlanId,
        kind: PlanSnapshotKind = PlanSnapshotKind.MANUAL,
        label: String,
    ): PlanSnapshot {
        require(label.isNotBlank()) { "Snapshot label cannot be blank." }
        return repository.createSnapshot(planId, kind, label.trim())
    }

    suspend fun restoreSnapshot(snapshotId: PlanSnapshotId) = repository.restoreSnapshot(snapshotId)

    suspend fun deleteSnapshot(snapshotId: PlanSnapshotId) = repository.deleteSnapshot(snapshotId)
}
