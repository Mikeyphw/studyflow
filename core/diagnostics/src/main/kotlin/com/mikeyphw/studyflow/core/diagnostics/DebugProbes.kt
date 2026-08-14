package com.mikeyphw.studyflow.core.diagnostics

enum class DebugProbeId {
    RECORDER_HEALTH,
    NOTIFICATION_HEALTH,
    WIDGET_HEALTH,
    PROCESS_EXIT_HISTORY,
    BACKUP_CONFIGURATION,
    FILE_PROVIDER_CONFIGURATION,
    REMINDER_RECONCILIATION,
    WIDGET_REFRESH,
    BACKUP_DRY_RUN,
    SEARCH_QUERY,
    RECOMMENDATION_QUERY,
    SYNC_FOUNDATION,
}

enum class DebugProbeSafety {
    READ_ONLY,
    VISIBLE_SIDE_EFFECT,
}

enum class DebugProbeMessage {
    NONE,
    MISSING_DEFAULT_PLAN,
    NOTIFICATION_PERMISSION_DENIED,
    NOTIFICATIONS_BLOCKED,
    CHANNEL_MISSING,
    CHANNEL_BLOCKED,
    WIDGETS_UNSUPPORTED,
    WIDGET_PROVIDER_MISSING,
    WIDGET_PROVIDER_DISABLED,
    NO_WIDGET_INSTANCES,
    PROCESS_HISTORY_UNAVAILABLE,
    NO_PROCESS_EXIT_HISTORY,
    RECENT_CRASH_OR_ANR,
    RECENT_ABNORMAL_EXIT,
    RECENT_LOW_MEMORY_EXIT,
    BACKUP_POLICY_INVALID,
    BACKUP_RULES_INVALID,
    FILE_PROVIDER_INVALID,
    WIDGET_REFRESH_NOT_OBSERVED,
    BACKUP_DRY_RUN_INTEGRITY_FAILED,
    SEARCH_CANARY_FAILED,
    NO_SEARCH_EVIDENCE,
    RECOMMENDATION_CANARY_FAILED,
    NO_RECOMMENDATION_EVIDENCE,
    SYNC_CANARY_FAILED,
    PROBE_TIMED_OUT,
}

enum class DebugProbeStatus {
    NOT_RUN,
    RUNNING,
    PASSED,
    WARNING,
    BLOCKED,
    FAILED,
    CANCELLED,
}

data class DebugProbeDescriptor(
    val id: DebugProbeId,
    val area: DiagnosticArea,
    val safety: DebugProbeSafety,
    val requiresDefaultPlan: Boolean,
    val timeoutMillis: Long,
) {
    init {
        require(timeoutMillis in 10L..60_000L) { "Probe timeout must be between ten milliseconds and sixty seconds." }
    }
}

data class DebugProbeResult(
    val id: DebugProbeId,
    val status: DebugProbeStatus,
    val message: DebugProbeMessage = DebugProbeMessage.NONE,
    val safeFacts: Map<String, String> = emptyMap(),
    val durationMillis: Long = 0L,
    val completedAtEpochMillis: Long = 0L,
    val exception: DiagnosticExceptionSummary? = null,
) {
    init {
        require(durationMillis >= 0L) { "Probe duration must not be negative." }
        require(completedAtEpochMillis >= 0L) { "Probe completion time must not be negative." }
        require(status != DebugProbeStatus.RUNNING || completedAtEpochMillis == 0L) {
            "Running probes cannot have a completion timestamp."
        }
    }

    fun sanitized(): DebugProbeResult = copy(
        safeFacts = DiagnosticRedactor.redactDetails(safeFacts),
        exception = exception?.let(DiagnosticRedactor::sanitizeExceptionSummary),
    )
}

data class DebugProbeReport(
    val results: Map<DebugProbeId, DebugProbeResult>,
    val running: Set<DebugProbeId> = emptySet(),
) {
    val passedCount: Int get() = results.values.count { it.status == DebugProbeStatus.PASSED }
    val warningCount: Int get() = results.values.count { it.status == DebugProbeStatus.WARNING }
    val blockedCount: Int get() = results.values.count { it.status == DebugProbeStatus.BLOCKED }
    val failedCount: Int get() = results.values.count { it.status == DebugProbeStatus.FAILED }

    companion object {
        fun empty(): DebugProbeReport = DebugProbeReport(emptyMap())
    }
}

data class NotificationHealthSnapshot(
    val permissionGranted: Boolean,
    val appNotificationsEnabled: Boolean,
    val channelRequired: Boolean,
    val channelPresent: Boolean,
    val channelBlocked: Boolean,
    val channelImportance: Int?,
    val apiLevel: Int,
)

data class WidgetHealthSnapshot(
    val deviceSupportsWidgets: Boolean,
    val providerRegistered: Boolean,
    val providerEnabled: Boolean,
    val providerMetadataPresent: Boolean,
    val boundInstanceCount: Int,
)

data class ProcessExitHealthSnapshot(
    val apiSupported: Boolean,
    val recentExitCount: Int,
    val crashOrAnrCount: Int,
    val abnormalExitCount: Int,
    val lowMemoryCount: Int,
    val apiLevel: Int,
)

data class BackupConfigurationSnapshot(
    val backupAllowed: Boolean,
    val expectedBackupAllowed: Boolean,
    val legacyRuleLinked: Boolean,
    val modernRuleLinked: Boolean,
    val legacyRulePresent: Boolean,
    val modernRulePresent: Boolean,
)

data class FileProviderHealthSnapshot(
    val providerPresent: Boolean,
    val providerExported: Boolean,
    val grantsUriPermissions: Boolean,
    val pathMetadataPresent: Boolean,
    val allowedRootGranted: Boolean,
    val privateRootRejected: Boolean,
)

object DebugProbePlatformPolicy {
    fun notification(id: DebugProbeId, snapshot: NotificationHealthSnapshot): DebugProbeResult {
        val message = when {
            !snapshot.permissionGranted -> DebugProbeMessage.NOTIFICATION_PERMISSION_DENIED
            !snapshot.appNotificationsEnabled -> DebugProbeMessage.NOTIFICATIONS_BLOCKED
            snapshot.channelRequired && !snapshot.channelPresent -> DebugProbeMessage.CHANNEL_MISSING
            snapshot.channelRequired && snapshot.channelBlocked -> DebugProbeMessage.CHANNEL_BLOCKED
            else -> DebugProbeMessage.NONE
        }
        return DebugProbeResult(
            id = id,
            status = if (message == DebugProbeMessage.NONE) DebugProbeStatus.PASSED else DebugProbeStatus.WARNING,
            message = message,
            safeFacts = buildMap {
                put("enabled", snapshot.appNotificationsEnabled.toString())
                put("available", snapshot.permissionGranted.toString())
                put("api_level", snapshot.apiLevel.toString())
                snapshot.channelImportance?.let { importance -> put("importance", importance.toString()) }
            },
        )
    }

    fun widget(id: DebugProbeId, snapshot: WidgetHealthSnapshot): DebugProbeResult {
        val (status, message) = when {
            !snapshot.deviceSupportsWidgets -> DebugProbeStatus.BLOCKED to DebugProbeMessage.WIDGETS_UNSUPPORTED
            !snapshot.providerRegistered || !snapshot.providerMetadataPresent -> {
                DebugProbeStatus.FAILED to DebugProbeMessage.WIDGET_PROVIDER_MISSING
            }
            !snapshot.providerEnabled -> DebugProbeStatus.WARNING to DebugProbeMessage.WIDGET_PROVIDER_DISABLED
            snapshot.boundInstanceCount == 0 -> DebugProbeStatus.BLOCKED to DebugProbeMessage.NO_WIDGET_INSTANCES
            else -> DebugProbeStatus.PASSED to DebugProbeMessage.NONE
        }
        return DebugProbeResult(
            id = id,
            status = status,
            message = message,
            safeFacts = mapOf(
                "available" to snapshot.deviceSupportsWidgets.toString(),
                "enabled" to snapshot.providerEnabled.toString(),
                "count" to snapshot.boundInstanceCount.toString(),
                "provider_registered" to snapshot.providerRegistered.toString(),
                "provider_metadata" to snapshot.providerMetadataPresent.toString(),
            ),
        )
    }

    fun processExit(id: DebugProbeId, snapshot: ProcessExitHealthSnapshot): DebugProbeResult {
        if (!snapshot.apiSupported) {
            return DebugProbeResult(
                id = id,
                status = DebugProbeStatus.BLOCKED,
                message = DebugProbeMessage.PROCESS_HISTORY_UNAVAILABLE,
                safeFacts = mapOf("api_level" to snapshot.apiLevel.toString()),
            )
        }
        val (status, message) = when {
            snapshot.recentExitCount == 0 -> DebugProbeStatus.BLOCKED to DebugProbeMessage.NO_PROCESS_EXIT_HISTORY
            snapshot.crashOrAnrCount > 0 -> DebugProbeStatus.WARNING to DebugProbeMessage.RECENT_CRASH_OR_ANR
            snapshot.abnormalExitCount > 0 -> DebugProbeStatus.WARNING to DebugProbeMessage.RECENT_ABNORMAL_EXIT
            snapshot.lowMemoryCount > 0 -> DebugProbeStatus.WARNING to DebugProbeMessage.RECENT_LOW_MEMORY_EXIT
            else -> DebugProbeStatus.PASSED to DebugProbeMessage.NONE
        }
        return DebugProbeResult(
            id = id,
            status = status,
            message = message,
            safeFacts = mapOf(
                "count" to snapshot.recentExitCount.toString(),
                "crash_or_anr_count" to snapshot.crashOrAnrCount.toString(),
                "abnormal_count" to snapshot.abnormalExitCount.toString(),
                "low_memory_count" to snapshot.lowMemoryCount.toString(),
            ),
        )
    }

    fun backup(id: DebugProbeId, snapshot: BackupConfigurationSnapshot): DebugProbeResult {
        val rulesValid = snapshot.legacyRuleLinked &&
            snapshot.modernRuleLinked &&
            snapshot.legacyRulePresent &&
            snapshot.modernRulePresent
        val (status, message) = when {
            snapshot.backupAllowed != snapshot.expectedBackupAllowed -> {
                DebugProbeStatus.FAILED to DebugProbeMessage.BACKUP_POLICY_INVALID
            }
            !rulesValid -> DebugProbeStatus.FAILED to DebugProbeMessage.BACKUP_RULES_INVALID
            else -> DebugProbeStatus.PASSED to DebugProbeMessage.NONE
        }
        return DebugProbeResult(
            id = id,
            status = status,
            message = message,
            safeFacts = mapOf(
                "enabled" to snapshot.backupAllowed.toString(),
                "expected_enabled" to snapshot.expectedBackupAllowed.toString(),
                "legacy_rule_linked" to snapshot.legacyRuleLinked.toString(),
                "modern_rule_linked" to snapshot.modernRuleLinked.toString(),
                "legacy_rule_present" to snapshot.legacyRulePresent.toString(),
                "modern_rule_present" to snapshot.modernRulePresent.toString(),
            ),
        )
    }

    fun fileProvider(id: DebugProbeId, snapshot: FileProviderHealthSnapshot): DebugProbeResult {
        val valid = snapshot.providerPresent &&
            !snapshot.providerExported &&
            snapshot.grantsUriPermissions &&
            snapshot.pathMetadataPresent &&
            snapshot.allowedRootGranted &&
            snapshot.privateRootRejected
        return DebugProbeResult(
            id = id,
            status = if (valid) DebugProbeStatus.PASSED else DebugProbeStatus.FAILED,
            message = if (valid) DebugProbeMessage.NONE else DebugProbeMessage.FILE_PROVIDER_INVALID,
            safeFacts = mapOf(
                "available" to snapshot.providerPresent.toString(),
                "enabled" to valid.toString(),
                "path_metadata" to snapshot.pathMetadataPresent.toString(),
                "allowed_root" to snapshot.allowedRootGranted.toString(),
                "private_root_rejected" to snapshot.privateRootRejected.toString(),
            ),
        )
    }
}

object DebugProbeCatalog {
    val descriptors: List<DebugProbeDescriptor> = listOf(
        descriptor(DebugProbeId.RECORDER_HEALTH, DiagnosticArea.VALIDATION),
        descriptor(DebugProbeId.NOTIFICATION_HEALTH, DiagnosticArea.NOTIFICATIONS),
        descriptor(DebugProbeId.WIDGET_HEALTH, DiagnosticArea.WIDGET),
        descriptor(DebugProbeId.PROCESS_EXIT_HISTORY, DiagnosticArea.APP_LIFECYCLE),
        descriptor(DebugProbeId.BACKUP_CONFIGURATION, DiagnosticArea.BACKUP_RESTORE),
        descriptor(DebugProbeId.FILE_PROVIDER_CONFIGURATION, DiagnosticArea.ANNEX_STORAGE),
        descriptor(
            id = DebugProbeId.REMINDER_RECONCILIATION,
            area = DiagnosticArea.REMINDERS,
            safety = DebugProbeSafety.VISIBLE_SIDE_EFFECT,
            requiresDefaultPlan = true,
            timeoutMillis = 30_000L,
        ),
        descriptor(
            id = DebugProbeId.WIDGET_REFRESH,
            area = DiagnosticArea.WIDGET,
            safety = DebugProbeSafety.VISIBLE_SIDE_EFFECT,
            timeoutMillis = 30_000L,
        ),
        descriptor(
            id = DebugProbeId.BACKUP_DRY_RUN,
            area = DiagnosticArea.BACKUP_RESTORE,
            requiresDefaultPlan = true,
            timeoutMillis = 60_000L,
        ),
        descriptor(DebugProbeId.SEARCH_QUERY, DiagnosticArea.SEARCH_RECOMMENDATIONS),
        descriptor(
            id = DebugProbeId.RECOMMENDATION_QUERY,
            area = DiagnosticArea.SEARCH_RECOMMENDATIONS,
            requiresDefaultPlan = true,
        ),
        descriptor(DebugProbeId.SYNC_FOUNDATION, DiagnosticArea.SYNC_FOUNDATION),
    )

    private val byId: Map<DebugProbeId, DebugProbeDescriptor> = descriptors.associateBy { it.id }

    val safeSuiteIds: List<DebugProbeId> = descriptors
        .filter { descriptor -> descriptor.safety == DebugProbeSafety.READ_ONLY }
        .map(DebugProbeDescriptor::id)

    fun descriptor(id: DebugProbeId): DebugProbeDescriptor = requireNotNull(byId[id]) {
        "Unknown debug probe: $id"
    }

    private fun descriptor(
        id: DebugProbeId,
        area: DiagnosticArea,
        safety: DebugProbeSafety = DebugProbeSafety.READ_ONLY,
        requiresDefaultPlan: Boolean = false,
        timeoutMillis: Long = 15_000L,
    ): DebugProbeDescriptor = DebugProbeDescriptor(
        id = id,
        area = area,
        safety = safety,
        requiresDefaultPlan = requiresDefaultPlan,
        timeoutMillis = timeoutMillis,
    )
}

object DebugProbeReportPolicy {
    fun markRunning(report: DebugProbeReport, id: DebugProbeId): DebugProbeReport = report.copy(
        running = report.running + id,
        results = report.results + (
            id to DebugProbeResult(
                id = id,
                status = DebugProbeStatus.RUNNING,
            )
        ),
    )

    fun complete(report: DebugProbeReport, result: DebugProbeResult): DebugProbeReport {
        val sanitized = result.sanitized()
        require(sanitized.status !in setOf(DebugProbeStatus.NOT_RUN, DebugProbeStatus.RUNNING)) {
            "Completed probe must have a terminal status."
        }
        return report.copy(
            running = report.running - result.id,
            results = report.results + (result.id to sanitized),
        )
    }

    fun blockedForMissingPlan(id: DebugProbeId, nowEpochMillis: Long): DebugProbeResult {
        val descriptor = DebugProbeCatalog.descriptor(id)
        require(descriptor.requiresDefaultPlan) { "Probe does not require a default plan." }
        return DebugProbeResult(
            id = id,
            status = DebugProbeStatus.BLOCKED,
            message = DebugProbeMessage.MISSING_DEFAULT_PLAN,
            completedAtEpochMillis = nowEpochMillis,
        )
    }
}
