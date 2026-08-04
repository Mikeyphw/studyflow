package com.mikeyphw.studyflow.core.model

enum class StudySyncProviderKind {
    LOCAL_ONLY,
    CLOUD_PLACEHOLDER,
}

enum class StudySyncConnectionStatus {
    DISABLED,
    LOCAL_READY,
    PROVIDER_REQUIRED,
    BLOCKED,
}

enum class StudySyncConflictPolicy {
    KEEP_LOCAL,
    MANUAL_REVIEW,
}

data class StudySyncProviderDescriptor(
    val kind: StudySyncProviderKind,
    val displayName: String,
    val configured: Boolean,
    val identityRequired: Boolean,
    val conflictPolicyRequired: Boolean,
)

data class StudySyncState(
    val enabled: Boolean,
    val provider: StudySyncProviderDescriptor,
    val connectionStatus: StudySyncConnectionStatus,
    val conflictPolicy: StudySyncConflictPolicy,
    val deviceName: String,
    val lastPreparedAtEpochMillis: Long?,
    val lastLocalChangeToken: String?,
    val pendingUploadCount: Int,
    val pendingDownloadCount: Int,
    val blockedReason: String?,
) {
    val readyForProviderSelection: Boolean = connectionStatus == StudySyncConnectionStatus.LOCAL_READY
    val cloudProviderSelected: Boolean = provider.kind != StudySyncProviderKind.LOCAL_ONLY && provider.configured

    companion object {
        fun disabled(deviceName: String = "This device"): StudySyncState = StudySyncState(
            enabled = false,
            provider = LocalOnlyProvider,
            connectionStatus = StudySyncConnectionStatus.DISABLED,
            conflictPolicy = StudySyncConflictPolicy.MANUAL_REVIEW,
            deviceName = deviceName,
            lastPreparedAtEpochMillis = null,
            lastLocalChangeToken = null,
            pendingUploadCount = 0,
            pendingDownloadCount = 0,
            blockedReason = null,
        )
    }
}

val LocalOnlyProvider = StudySyncProviderDescriptor(
    kind = StudySyncProviderKind.LOCAL_ONLY,
    displayName = "This device",
    configured = false,
    identityRequired = true,
    conflictPolicyRequired = true,
)

data class StudySyncChangeSummary(
    val planCount: Int,
    val activePlanCount: Int,
    val latestUpdatedAtEpochMillis: Long?,
    val localChangeToken: String,
) {
    val changeCount: Int = planCount
    val hasLocalData: Boolean = planCount > 0
}
