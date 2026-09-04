package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.StudySyncBackgroundMode
import com.mikeyphw.studyflow.core.model.StudySyncChangeSummary
import com.mikeyphw.studyflow.core.model.StudySyncConflictPolicy
import com.mikeyphw.studyflow.core.model.StudySyncLedgerSnapshot
import com.mikeyphw.studyflow.core.portability.StudyPlanEncryptedPackageCodec
import com.mikeyphw.studyflow.core.model.StudySyncState
import kotlinx.coroutines.flow.Flow

interface StudyCloudSyncRepository {
    val syncState: Flow<StudySyncState>

    suspend fun inspectLocalChanges(): StudySyncChangeSummary

    suspend fun inspectSyncLedger(): StudySyncLedgerSnapshot

    suspend fun prepareLocalFoundation(deviceName: String): StudySyncState

    suspend fun setSyncEnabled(enabled: Boolean): StudySyncState

    suspend fun setConflictPolicy(policy: StudySyncConflictPolicy): StudySyncState

    suspend fun setBackgroundSyncMode(mode: StudySyncBackgroundMode): StudySyncState

    suspend fun configureLocalFolderProvider(folderUri: String): StudySyncState

    suspend fun uploadManualFolderCheckpoint(password: CharArray): StudyManualFolderCheckpointDetails

    suspend fun scanManualFolderCheckpoints(): StudyManualFolderScan

    suspend fun previewLatestManualFolderRestore(password: CharArray): StudyManualFolderCheckpointDetails


    suspend fun clearLocalFoundation(): StudySyncState
}

class ManageStudyCloudSync(
    private val repository: StudyCloudSyncRepository,
) {
    val syncState: Flow<StudySyncState> = repository.syncState

    suspend fun inspectLocalChanges(): StudySyncChangeSummary = repository.inspectLocalChanges()

    suspend fun inspectSyncLedger(): StudySyncLedgerSnapshot = repository.inspectSyncLedger()

    suspend fun prepareLocalFoundation(deviceName: String): StudySyncState {
        require(deviceName.isNotBlank()) { "Device name cannot be blank." }
        return repository.prepareLocalFoundation(deviceName.trim())
    }

    suspend fun setSyncEnabled(enabled: Boolean): StudySyncState = repository.setSyncEnabled(enabled)

    suspend fun setConflictPolicy(policy: StudySyncConflictPolicy): StudySyncState =
        repository.setConflictPolicy(policy)

    suspend fun setBackgroundSyncMode(mode: StudySyncBackgroundMode): StudySyncState =
        repository.setBackgroundSyncMode(mode)

    suspend fun configureLocalFolderProvider(folderUri: String): StudySyncState {
        require(folderUri.isNotBlank()) { "Folder URI cannot be blank." }
        return repository.configureLocalFolderProvider(folderUri.trim())
    }

    suspend fun uploadManualFolderCheckpoint(password: CharArray): StudyManualFolderCheckpointDetails {
        require(password.size >= StudyPlanEncryptedPackageCodec.MinimumPasswordLength) {
            "Encrypted sync checkpoints require at least " +
                "${StudyPlanEncryptedPackageCodec.MinimumPasswordLength} characters."
        }
        return repository.uploadManualFolderCheckpoint(password)
    }

    suspend fun scanManualFolderCheckpoints(): StudyManualFolderScan =
        repository.scanManualFolderCheckpoints()

    suspend fun previewLatestManualFolderRestore(password: CharArray): StudyManualFolderCheckpointDetails {
        require(password.size >= StudyPlanEncryptedPackageCodec.MinimumPasswordLength) {
            "Encrypted sync checkpoints require at least " +
                "${StudyPlanEncryptedPackageCodec.MinimumPasswordLength} characters."
        }
        return repository.previewLatestManualFolderRestore(password)
    }


    suspend fun clearLocalFoundation(): StudySyncState = repository.clearLocalFoundation()
}


data class StudyManualFolderScan(
    val checkpointCount: Int,
    val latestFileName: String?,
    val latestModifiedAtEpochMillis: Long?,
) {
    init {
        require(checkpointCount >= 0) { "Manual folder checkpoint count cannot be negative." }
        require((checkpointCount == 0) == (latestFileName == null)) {
            "Manual folder scan latest file must match checkpoint availability."
        }
        require(latestFileName?.isNotBlank() ?: true) { "Manual folder checkpoint file name cannot be blank." }
        require(latestModifiedAtEpochMillis == null || latestModifiedAtEpochMillis >= 0L) {
            "Manual folder checkpoint modification time cannot be negative."
        }
    }
}

data class StudyManualFolderCheckpointDetails(
    val fileName: String,
    val checkpointId: String,
    val deviceId: String,
    val deviceName: String,
    val createdAtEpochMillis: Long,
    val graphRevision: Long,
    val changeCount: Int,
    val databaseByteSize: Long,
    val attachmentCount: Int,
    val applicationVersion: String,
) {
    init {
        require(fileName.endsWith(".sfbackup", ignoreCase = true)) {
            "Manual folder checkpoints must use the .sfbackup extension."
        }
        require(checkpointId.isNotBlank()) { "Manual folder checkpoint id cannot be blank." }
        require(deviceId.isNotBlank()) { "Manual folder checkpoint device id cannot be blank." }
        require(deviceName.isNotBlank()) { "Manual folder checkpoint device name cannot be blank." }
        require(createdAtEpochMillis >= 0L) { "Manual folder checkpoint creation time cannot be negative." }
        require(graphRevision >= 0L) { "Manual folder checkpoint graph revision cannot be negative." }
        require(changeCount >= 0) { "Manual folder checkpoint change count cannot be negative." }
        require(databaseByteSize >= 0L) { "Manual folder database size cannot be negative." }
        require(attachmentCount >= 0) { "Manual folder attachment count cannot be negative." }
        require(applicationVersion.isNotBlank()) { "Manual folder application version cannot be blank." }
    }
}

object StudyLocalCheckpointReadinessAuditor {
    fun localCheckpointReady(
        state: StudySyncState,
        summary: StudySyncChangeSummary,
    ): Boolean = state.checkpointReady &&
        summary.hasLocalData &&
        state.lastLocalChangeToken == summary.localChangeToken
}
