package com.mikeyphw.studyflow.core.model

enum class StudySyncProviderKind {
    LOCAL_ONLY,
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

data class StudySyncProviderDescriptor(
    val kind: StudySyncProviderKind,
    val displayName: String,
    val configured: Boolean,
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
) {
    val checkpointReady: Boolean = connectionStatus == StudySyncConnectionStatus.LOCAL_READY
    val localBackupAvailable: Boolean = provider.kind == StudySyncProviderKind.LOCAL_ONLY && provider.configured

    companion object {
        fun disabled(deviceName: String = "This device"): StudySyncState = StudySyncState(
            enabled = false,
            provider = LocalOnlyProvider,
            connectionStatus = StudySyncConnectionStatus.DISABLED,
            conflictPolicy = StudySyncConflictPolicy.MANUAL_REVIEW,
            deviceName = deviceName,
            lastPreparedAtEpochMillis = null,
            lastLocalChangeToken = null,
            pendingLocalChangeCount = 0,
            blockedReason = null,
        )
    }
}

val LocalOnlyProvider = StudySyncProviderDescriptor(
    kind = StudySyncProviderKind.LOCAL_ONLY,
    displayName = "This device",
    configured = true,
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
