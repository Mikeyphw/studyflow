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

data class StudyFlowSyncSettings(
    val syncEnabled: Boolean = false,
    val providerId: String? = null,
    val automaticSyncEnabled: Boolean = false,
    val wifiOnly: Boolean = true,
    val chargingOnly: Boolean = false,
    val encryptRemoteBackups: Boolean = true,
) {
    init {
        require(providerId?.isNotBlank() ?: true) { "Sync provider id cannot be blank." }
        require(!automaticSyncEnabled || syncEnabled) { "Automatic sync cannot run while sync is off." }
    }

    val cloudRequired: Boolean = false
    val configuredProviderId: String = providerId ?: StudyFlowSyncProviderIds.NONE
    val providerConfigured: Boolean = providerId != null
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

    companion object {
        fun disabled(deviceName: String = "This device"): StudySyncState = StudySyncState(
            enabled = false,
            provider = NoSyncProvider,
            connectionStatus = StudySyncConnectionStatus.DISABLED,
            conflictPolicy = StudySyncConflictPolicy.MANUAL_REVIEW,
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
