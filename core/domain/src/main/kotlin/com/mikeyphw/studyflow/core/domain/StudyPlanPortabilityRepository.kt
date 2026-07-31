package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.PlanSnapshot
import com.mikeyphw.studyflow.core.model.PlanSnapshotId
import com.mikeyphw.studyflow.core.model.PlanSnapshotKind
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.portability.StudyPlanExportOptions
import com.mikeyphw.studyflow.core.portability.StudyPlanImportPreview
import com.mikeyphw.studyflow.core.portability.StudyPlanImportResult
import com.mikeyphw.studyflow.core.portability.StudyPlanImportStrategy
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.flow.Flow

interface StudyPlanPortabilityRepository {
    fun observeSnapshots(planId: StudyPlanId): Flow<List<PlanSnapshot>>

    suspend fun exportPlan(
        planId: StudyPlanId,
        options: StudyPlanExportOptions,
        output: OutputStream,
    )

    suspend fun previewImport(input: InputStream): StudyPlanImportPreview

    suspend fun importPlan(
        input: InputStream,
        strategy: StudyPlanImportStrategy,
        targetPlanId: StudyPlanId? = null,
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
    ) = repository.exportPlan(planId, options, output)

    suspend fun previewImport(input: InputStream): StudyPlanImportPreview =
        repository.previewImport(input)

    suspend fun importPlan(
        input: InputStream,
        strategy: StudyPlanImportStrategy,
        targetPlanId: StudyPlanId? = null,
    ): StudyPlanImportResult = repository.importPlan(input, strategy, targetPlanId)

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
