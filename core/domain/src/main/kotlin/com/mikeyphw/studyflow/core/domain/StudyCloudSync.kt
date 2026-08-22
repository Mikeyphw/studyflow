package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.StudySyncChangeSummary
import com.mikeyphw.studyflow.core.model.StudySyncConflictPolicy
import com.mikeyphw.studyflow.core.model.StudySyncState
import kotlinx.coroutines.flow.Flow

interface StudyCloudSyncRepository {
    val syncState: Flow<StudySyncState>

    suspend fun inspectLocalChanges(): StudySyncChangeSummary

    suspend fun prepareLocalFoundation(deviceName: String): StudySyncState

    suspend fun setSyncEnabled(enabled: Boolean): StudySyncState

    suspend fun setConflictPolicy(policy: StudySyncConflictPolicy): StudySyncState

    suspend fun configureLocalFolderProvider(folderUri: String): StudySyncState

    suspend fun recordManualUploadCheckpoint(): StudySyncState

    suspend fun recordManualRemoteCheck(remoteCheckpointCount: Int): StudySyncState

    suspend fun clearLocalFoundation(): StudySyncState
}

class ManageStudyCloudSync(
    private val repository: StudyCloudSyncRepository,
) {
    val syncState: Flow<StudySyncState> = repository.syncState

    suspend fun inspectLocalChanges(): StudySyncChangeSummary = repository.inspectLocalChanges()

    suspend fun prepareLocalFoundation(deviceName: String): StudySyncState {
        require(deviceName.isNotBlank()) { "Device name cannot be blank." }
        return repository.prepareLocalFoundation(deviceName.trim())
    }

    suspend fun setSyncEnabled(enabled: Boolean): StudySyncState = repository.setSyncEnabled(enabled)

    suspend fun setConflictPolicy(policy: StudySyncConflictPolicy): StudySyncState =
        repository.setConflictPolicy(policy)

    suspend fun configureLocalFolderProvider(folderUri: String): StudySyncState {
        require(folderUri.isNotBlank()) { "Folder URI cannot be blank." }
        return repository.configureLocalFolderProvider(folderUri.trim())
    }

    suspend fun recordManualUploadCheckpoint(): StudySyncState = repository.recordManualUploadCheckpoint()

    suspend fun recordManualRemoteCheck(remoteCheckpointCount: Int): StudySyncState {
        require(remoteCheckpointCount >= 0) { "Remote checkpoint count cannot be negative." }
        return repository.recordManualRemoteCheck(remoteCheckpointCount)
    }

    suspend fun clearLocalFoundation(): StudySyncState = repository.clearLocalFoundation()
}

object StudyLocalCheckpointReadinessAuditor {
    fun localCheckpointReady(
        state: StudySyncState,
        summary: StudySyncChangeSummary,
    ): Boolean = state.checkpointReady &&
        summary.hasLocalData &&
        state.lastLocalChangeToken == summary.localChangeToken
}
