package com.mikeyphw.studyflow.core.model

object StudyFlowSyncProviderIds {
    const val NONE: String = "none"
    const val LOCAL_FOLDER: String = "local-folder"
    const val WEBDAV: String = "webdav"
    const val GOOGLE_DRIVE: String = "google-drive"
}

enum class StudySyncProviderKind {
    NONE,
    LOCAL_ONLY,
    LOCAL_FOLDER,
    REMOTE_PROVIDER,
}

enum class StudySyncConnectionStatus {
    DISABLED,
    LOCAL_READY,
    CHECKPOINT_NEEDED,
    CHECKPOINT_BLOCKED,
}

enum class StudySyncConflictPolicy {
    KEEP_LOCAL,
    MANUAL_REVIEW,
}

enum class StudySyncManualAction {
    CHOOSE_SYNC_FOLDER,
    PREPARE_UPLOAD_CHECKPOINT,
    CHECK_REMOTE_CHECKPOINTS,
    PREVIEW_RESTORE,
    VIEW_SYNC_HISTORY,
}

data class StudyFlowSyncSettings(
    val syncEnabled: Boolean = false,
    val providerId: String? = null,
    val automaticSyncEnabled: Boolean = false,
    val wifiOnly: Boolean = true,
    val chargingOnly: Boolean = false,
    val encryptRemoteBackups: Boolean = true,
    val providerLocation: String? = null,
    val lastManualUploadAtEpochMillis: Long? = null,
    val lastRemoteCheckAtEpochMillis: Long? = null,
    val remoteCheckpointCount: Int = 0,
) {
    init {
        require(providerId?.isNotBlank() ?: true) { "Sync provider id cannot be blank." }
        require(providerLocation?.isNotBlank() ?: true) { "Sync provider location cannot be blank." }
        require(!automaticSyncEnabled || syncEnabled) { "Automatic sync cannot run while sync is off." }
        require(remoteCheckpointCount >= 0) { "Remote checkpoint count cannot be negative." }
    }

    val cloudRequired: Boolean = false
    val configuredProviderId: String = providerId ?: StudyFlowSyncProviderIds.NONE
    val providerConfigured: Boolean = providerId != null
    val localFolderConfigured: Boolean =
        providerId == StudyFlowSyncProviderIds.LOCAL_FOLDER && providerLocation != null
    val manualOnly: Boolean = syncEnabled && !automaticSyncEnabled

    companion object {
        val LocalFirstDefault: StudyFlowSyncSettings = StudyFlowSyncSettings()
    }
}

data class StudySyncProviderDescriptor(
    val kind: StudySyncProviderKind,
    val displayName: String,
    val configured: Boolean,
    val id: String = kind.name.lowercase(),
    val requiresAccount: Boolean = false,
)

data class StudySyncState(
    val enabled: Boolean,
    val provider: StudySyncProviderDescriptor,
    val connectionStatus: StudySyncConnectionStatus,
    val conflictPolicy: StudySyncConflictPolicy,
    val deviceId: String = StudySyncDeviceIdentity.DefaultDeviceId,
    val deviceName: String,
    val lastPreparedAtEpochMillis: Long?,
    val lastLocalChangeToken: String?,
    val pendingLocalChangeCount: Int,
    val blockedReason: String?,
    val settings: StudyFlowSyncSettings = StudyFlowSyncSettings.LocalFirstDefault,
) {
    val checkpointReady: Boolean = connectionStatus == StudySyncConnectionStatus.LOCAL_READY
    val localBackupAvailable: Boolean = provider.kind == StudySyncProviderKind.LOCAL_ONLY && provider.configured
    val cloudRequired: Boolean = settings.cloudRequired
    val optionalCloudConfigured: Boolean = settings.providerConfigured
    val localFolderSyncConfigured: Boolean = settings.localFolderConfigured
    val remoteCheckpointAvailable: Boolean = settings.remoteCheckpointCount > 0

    companion object {
        fun disabled(deviceName: String = "This device"): StudySyncState = StudySyncState(
            enabled = false,
            provider = NoSyncProvider,
            connectionStatus = StudySyncConnectionStatus.DISABLED,
            conflictPolicy = StudySyncConflictPolicy.MANUAL_REVIEW,
            deviceId = StudySyncDeviceIdentity.DefaultDeviceId,
            deviceName = deviceName,
            lastPreparedAtEpochMillis = null,
            lastLocalChangeToken = null,
            pendingLocalChangeCount = 0,
            blockedReason = null,
            settings = StudyFlowSyncSettings.LocalFirstDefault,
        )
    }
}

val NoSyncProvider = StudySyncProviderDescriptor(
    kind = StudySyncProviderKind.NONE,
    displayName = "None",
    configured = false,
    id = StudyFlowSyncProviderIds.NONE,
)

val LocalOnlyProvider = StudySyncProviderDescriptor(
    kind = StudySyncProviderKind.LOCAL_ONLY,
    displayName = "This device",
    configured = true,
    id = "local-checkpoint",
)

val LocalFolderSyncProvider = StudySyncProviderDescriptor(
    kind = StudySyncProviderKind.LOCAL_FOLDER,
    displayName = "Local folder",
    configured = true,
    id = StudyFlowSyncProviderIds.LOCAL_FOLDER,
)

data class StudySyncChangeSummary(
    val planCount: Int,
    val activePlanCount: Int,
    val latestUpdatedAtEpochMillis: Long?,
    val localChangeToken: String,
    val databaseRevision: Long = 0L,
    val changesSinceCheckpoint: Int = 0,
) {
    val changeCount: Int = changesSinceCheckpoint
    val hasLocalData: Boolean = planCount > 0
}

data class StudySyncDevice(
    val id: String,
    val name: String,
    val createdAtEpochMillis: Long,
    val lastSeenAtEpochMillis: Long,
) {
    init {
        require(id.isNotBlank()) { "Device id cannot be blank." }
        require(name.isNotBlank()) { "Device name cannot be blank." }
        require(createdAtEpochMillis >= 0L) { "Device creation time cannot be negative." }
        require(lastSeenAtEpochMillis >= createdAtEpochMillis) {
            "Device last-seen time cannot be before creation."
        }
    }
}

enum class StudySyncLedgerEventType {
    DEVICE_REGISTERED,
    LOCAL_CHECKPOINT_PREPARED,
    MANUAL_UPLOAD_RECORDED,
    REMOTE_CHECK_COMPLETED,
    RESTORE_PREVIEWED,
    CONFLICT_DETECTED,
    CONFLICT_RESOLVED,
}

data class StudySyncCheckpointRecord(
    val id: String,
    val deviceId: String = StudySyncDeviceIdentity.DefaultDeviceId,
    val deviceName: String,
    val createdAtEpochMillis: Long,
    val databaseRevision: Long,
    val localChangeToken: String,
    val changeCount: Int,
    val uploadedAtEpochMillis: Long? = null,
    val remoteLocation: String? = null,
    val restorePreviewedAtEpochMillis: Long? = null,
    val conflictCount: Int = 0,
) {
    init {
        require(id.isNotBlank()) { "Checkpoint id cannot be blank." }
        require(deviceId.isNotBlank()) { "Checkpoint device id cannot be blank." }
        require(deviceName.isNotBlank()) { "Checkpoint device name cannot be blank." }
        require(createdAtEpochMillis >= 0L) { "Checkpoint creation time cannot be negative." }
        require(databaseRevision >= 0L) { "Checkpoint database revision cannot be negative." }
        require(localChangeToken.isNotBlank()) { "Checkpoint change token cannot be blank." }
        require(changeCount >= 0) { "Checkpoint change count cannot be negative." }
        require(uploadedAtEpochMillis == null || uploadedAtEpochMillis >= createdAtEpochMillis) {
            "Upload time cannot be before checkpoint creation."
        }
        require(remoteLocation?.isNotBlank() ?: true) { "Remote location cannot be blank." }
        require(
            restorePreviewedAtEpochMillis == null ||
                restorePreviewedAtEpochMillis >= createdAtEpochMillis,
        ) {
            "Restore preview time cannot be before checkpoint creation."
        }
        require(conflictCount >= 0) { "Conflict count cannot be negative." }
    }

    val uploaded: Boolean = uploadedAtEpochMillis != null
    val restorePreviewed: Boolean = restorePreviewedAtEpochMillis != null
    val hasConflicts: Boolean = conflictCount > 0
}

data class StudySyncLedgerEvent(
    val sequence: Long,
    val type: StudySyncLedgerEventType,
    val deviceId: String,
    val checkpointId: String? = null,
    val createdAtEpochMillis: Long,
    val message: String,
) {
    init {
        require(sequence >= 0L) { "Ledger event sequence cannot be negative." }
        require(deviceId.isNotBlank()) { "Ledger event device id cannot be blank." }
        require(checkpointId?.isNotBlank() ?: true) { "Ledger event checkpoint id cannot be blank." }
        require(createdAtEpochMillis >= 0L) { "Ledger event time cannot be negative." }
        require(message.isNotBlank()) { "Ledger event message cannot be blank." }
    }
}

data class StudySyncLedgerSnapshot(
    val devices: List<StudySyncDevice> = emptyList(),
    val checkpoints: List<StudySyncCheckpointRecord> = emptyList(),
    val events: List<StudySyncLedgerEvent> = emptyList(),
) {
    val currentDevice: StudySyncDevice? = devices.maxByOrNull { device -> device.lastSeenAtEpochMillis }
    val latestCheckpoint: StudySyncCheckpointRecord? = checkpoints.maxByOrNull { checkpoint ->
        checkpoint.createdAtEpochMillis
    }
    val uploadedCheckpointCount: Int = checkpoints.count { checkpoint -> checkpoint.uploaded }
    val restorePreviewedCheckpointCount: Int = checkpoints.count { checkpoint -> checkpoint.restorePreviewed }
    val conflictCount: Int = checkpoints.sumOf { checkpoint -> checkpoint.conflictCount }
    val hasConflicts: Boolean = conflictCount > 0
}

object StudySyncDeviceIdentity {
    const val DefaultDeviceId: String = "device-local-default"

    fun stableDeviceId(seed: String): String {
        val normalized = seed.trim().lowercase()
        require(normalized.isNotBlank()) { "Device identity seed cannot be blank." }
        val hash = normalized.fold(0x811C9DC5.toInt()) { acc, char ->
            (acc xor char.code) * 0x01000193
        }.toLong() and 0xffffffffL
        return "device-${hash.toString(16).padStart(8, '0')}"
    }

    fun normalizeDeviceName(name: String): String = name.trim().ifBlank { "This device" }
}

object StudySyncLedgerFactory {
    fun checkpointId(
        deviceId: String,
        databaseRevision: Long,
        createdAtEpochMillis: Long,
    ): String {
        require(deviceId.isNotBlank()) { "Checkpoint device id cannot be blank." }
        require(databaseRevision >= 0L) { "Checkpoint database revision cannot be negative." }
        require(createdAtEpochMillis >= 0L) { "Checkpoint creation time cannot be negative." }
        return "checkpoint-$deviceId-r$databaseRevision-t$createdAtEpochMillis"
    }

    fun localDevice(
        id: String,
        name: String,
        firstSeenAtEpochMillis: Long,
        lastSeenAtEpochMillis: Long = firstSeenAtEpochMillis,
    ): StudySyncDevice = StudySyncDevice(
        id = id,
        name = StudySyncDeviceIdentity.normalizeDeviceName(name),
        createdAtEpochMillis = firstSeenAtEpochMillis,
        lastSeenAtEpochMillis = lastSeenAtEpochMillis,
    )
}


data class StudySyncManualActionAvailability(
    val action: StudySyncManualAction,
    val enabled: Boolean,
    val blockedReason: String? = null,
)

object StudySyncManualActionPlanner {
    fun actions(state: StudySyncState): List<StudySyncManualActionAvailability> {
        val folderConfigured = state.localFolderSyncConfigured
        val hasRemoteCheckpoint = state.remoteCheckpointAvailable
        return listOf(
            StudySyncManualActionAvailability(
                action = StudySyncManualAction.CHOOSE_SYNC_FOLDER,
                enabled = true,
            ),
            StudySyncManualActionAvailability(
                action = StudySyncManualAction.PREPARE_UPLOAD_CHECKPOINT,
                enabled = folderConfigured,
                blockedReason = if (folderConfigured) null else "Choose a sync folder first.",
            ),
            StudySyncManualActionAvailability(
                action = StudySyncManualAction.CHECK_REMOTE_CHECKPOINTS,
                enabled = folderConfigured,
                blockedReason = if (folderConfigured) null else "Choose a sync folder first.",
            ),
            StudySyncManualActionAvailability(
                action = StudySyncManualAction.PREVIEW_RESTORE,
                enabled = folderConfigured && hasRemoteCheckpoint,
                blockedReason = if (hasRemoteCheckpoint) null else "No remote checkpoint has been found yet.",
            ),
            StudySyncManualActionAvailability(
                action = StudySyncManualAction.VIEW_SYNC_HISTORY,
                enabled = true,
            ),
        )
    }
}
