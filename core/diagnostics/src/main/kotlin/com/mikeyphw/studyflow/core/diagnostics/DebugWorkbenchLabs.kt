package com.mikeyphw.studyflow.core.diagnostics

enum class DebugLabStatus {
    NOT_RUN,
    RUNNING,
    PASSED,
    EMPTY,
    NOT_APPLICABLE,
    WARNING,
    BLOCKED,
    FAILED,
    CANCELLED,
}

enum class ReminderLabMessage {
    NONE,
    MISSING_DEFAULT_PLAN,
    PLAN_UNAVAILABLE,
    PLAN_NOT_ACTIVE,
    REMINDERS_DISABLED,
    NOTIFICATION_PERMISSION_DENIED,
    NOTIFICATIONS_BLOCKED,
    CHANNEL_MISSING,
    CHANNEL_BLOCKED,
    NO_CANDIDATES,
    INSPECTION_FAILED,
}


enum class ReminderLabActionResult {
    INSPECTED,
    RECONCILED,
    DIAGNOSTIC_NOTIFICATION_POSTED,
    DIAGNOSTIC_NOTIFICATION_BLOCKED,
    MISSING_DEFAULT_PLAN,
    FAILED,
}

enum class ReminderCandidateKind {
    DUE_REVIEWS,
    PLANNED_ACTIVITY,
    MISSED_ACTIVITY,
    DAILY_SUMMARY,
    SESSION_ENDING,
    SESSION_COMPLETED,
    EXAM_COUNTDOWN,
}

data class ReminderCandidateDiagnostic(
    val kind: ReminderCandidateKind,
    val scheduledAtEpochMillis: Long,
    val effectiveTriggerAtEpochMillis: Long,
    val deliveryWindowMillis: Long,
    val lateDeliveryGraceMillis: Long,
    val suppressedByQuietHours: Boolean,
    val delayedByQuietHours: Boolean = false,
    /** A matching immutable delivery PendingIntent identity exists; this does not prove AlarmManager installation. */
    val pendingIntentIdentityExists: Boolean,
    /** Durable occurrence bookkeeping matches the planner candidate. */
    val scheduledBookkeepingPresent: Boolean = false,
    val requestCode: Int,
) {
    init {
        require(scheduledAtEpochMillis >= 0L)
        require(effectiveTriggerAtEpochMillis >= 0L)
        require(deliveryWindowMillis > 0L)
        require(lateDeliveryGraceMillis >= deliveryWindowMillis)
        require(requestCode >= 0)
    }
}

data class ReminderNotificationLabReport(
    val status: DebugLabStatus,
    val message: ReminderLabMessage = ReminderLabMessage.NONE,
    val inspectedAtEpochMillis: Long = 0L,
    val apiLevel: Int = 0,
    val notificationsEnabled: Boolean = false,
    val notificationPermissionGranted: Boolean = false,
    val channelPresent: Boolean = false,
    val channelBlocked: Boolean = false,
    val channelImportance: Int? = null,
    val channelCount: Int = 0,
    val blockedChannelCount: Int = 0,
    val exactAlarmCapabilityAvailable: Boolean = false,
    val lastReconciledAtEpochMillis: Long? = null,
    val lastDeliveredAtEpochMillis: Long? = null,
    val lastDeliveredOccurrenceId: String? = null,
    val lastRecoveryReason: String? = null,
    val remindersEnabled: Boolean = false,
    val quietHoursActive: Boolean = false,
    val exactAlarmUsed: Boolean = false,
    val plannerStatus: DebugLabStatus = DebugLabStatus.NOT_RUN,
    val platformStatus: DebugLabStatus = DebugLabStatus.NOT_RUN,
    val schedulingStatus: DebugLabStatus = DebugLabStatus.NOT_RUN,
    val productionPipelineVerified: Boolean = false,
    val attentionLedgerEntryCount: Int = 0,
    val attentionLedgerActiveCount: Int = 0,
    val attentionLedgerTerminalCount: Int = 0,
    val attentionLedgerSourceSequenceCount: Int = 0,
    val attentionLedgerLegacyAliasCount: Int = 0,
    val attentionLedgerRecoveredFromInvalidState: Boolean = false,
    val candidates: List<ReminderCandidateDiagnostic> = emptyList(),
) {
    init {
        require(inspectedAtEpochMillis >= 0L)
        require(apiLevel >= 0)
        require(attentionLedgerEntryCount >= 0)
        require(attentionLedgerActiveCount >= 0)
        require(attentionLedgerTerminalCount >= 0)
        require(attentionLedgerSourceSequenceCount >= 0)
        require(attentionLedgerLegacyAliasCount >= 0)
        require(channelCount >= 0)
        require(blockedChannelCount in 0..channelCount)
        require(lastReconciledAtEpochMillis == null || lastReconciledAtEpochMillis >= 0L)
        require(lastDeliveredAtEpochMillis == null || lastDeliveredAtEpochMillis >= 0L)
        require(candidates.distinctBy(ReminderCandidateDiagnostic::kind).size == candidates.size)
    }

    val activeCandidateCount: Int = candidates.count { candidate -> !candidate.suppressedByQuietHours }
}

enum class DatabaseHealthMessage {
    NONE,
    DATABASE_UNAVAILABLE,
    SCHEMA_MISMATCH,
    QUICK_CHECK_FAILED,
    FOREIGN_KEY_VIOLATIONS,
    LOGICAL_INTEGRITY_VIOLATIONS,
    METADATA_MISSING,
    MIGRATION_PATH_INCOMPLETE,
}

data class DatabaseHealthLabReport(
    val status: DebugLabStatus,
    val message: DatabaseHealthMessage = DatabaseHealthMessage.NONE,
    val schemaVersion: Int = 0,
    val expectedSchemaVersion: Int = 0,
    val quickCheckPassed: Boolean = false,
    val foreignKeyViolationCount: Int = 0,
    val logicalIntegrityViolationCount: Int = 0,
    val userTableCount: Int = 0,
    val totalEntityRows: Long = 0L,
    val metadataPresent: Boolean = false,
    val migrationPathComplete: Boolean = false,
    val currentSchemaIntegrityVerified: Boolean = false,
    val currentSchemaCorruptionDetected: Boolean = false,
) {
    init {
        require(schemaVersion >= 0)
        require(expectedSchemaVersion >= 0)
        require(foreignKeyViolationCount >= 0)
        require(logicalIntegrityViolationCount >= 0)
        require(userTableCount >= 0)
        require(totalEntityRows >= 0L)
        require(!currentSchemaIntegrityVerified || status == DebugLabStatus.PASSED) {
            "Current-schema integrity can only be verified on a passing Database Health Lab report."
        }
        require(!currentSchemaIntegrityVerified || !currentSchemaCorruptionDetected) {
            "Current-schema corruption cannot be reported as verified."
        }
    }
}

enum class DatabasePortabilityLabActionResult {
    DATABASE_INSPECTED,
    PORTABILITY_DRILLS_COMPLETED,
    MISSING_DEFAULT_PLAN,
    FAILED,
}

enum class PortabilityLabMessage {
    NONE,
    MISSING_DEFAULT_PLAN,
    DRILL_FAILED,
    DRILL_BLOCKED,
}

enum class PortabilityDrillKind {
    ENCRYPTED_EXPORT,
    WRONG_PASSWORD,
    TAMPER_DETECTION,
    METADATA_PRIVATE_ANNEX,
    RESTORE_DRY_RUN,
    RETENTION_CLEANUP,
    RECOVERY_REPORT,
    REAL_RESTORE,
    RESTORE_FAILURE_ISOLATION,
    LOGICAL_INTEGRITY,
}

data class PortabilityDrillDiagnostic(
    val kind: PortabilityDrillKind,
    val status: DebugLabStatus,
    val destructive: Boolean,
) {
    val passed: Boolean get() = status == DebugLabStatus.PASSED
    val applicable: Boolean get() = status != DebugLabStatus.NOT_APPLICABLE
}

data class DatabasePortabilityLabReport(
    val database: DatabaseHealthLabReport,
    val portabilityStatus: DebugLabStatus = DebugLabStatus.NOT_RUN,
    val portabilityMessage: PortabilityLabMessage = PortabilityLabMessage.NONE,
    val portabilityGeneratedAtEpochMillis: Long = 0L,
    val drills: List<PortabilityDrillDiagnostic> = emptyList(),
) {
    init {
        require(portabilityGeneratedAtEpochMillis >= 0L)
        require(drills.distinctBy(PortabilityDrillDiagnostic::kind).size == drills.size)
        require(drills.none(PortabilityDrillDiagnostic::destructive)) {
            "Debug portability drills must never be destructive."
        }
    }
}

enum class StudyEngineLabMessage {
    NONE,
    MISSING_DEFAULT_PLAN,
    INSPECTION_FAILED,
}

enum class StudyEngineLabActionResult {
    INSPECTED,
    MISSING_DEFAULT_PLAN,
    FAILED,
}

enum class StudyEngineDiagnosticKind {
    SCHEDULE_EXPANSION,
    SCHEDULE_PERSISTENCE,
    REVIEW_QUEUE,
    SRS_SCHEDULER_CANARY,
    PRACTICE_DECK,
    PRACTICE_CANARY,
    MOCK_EXAM,
    MOCK_PLANNER_CANARY,
    RECOMMENDATIONS,
    SEARCH_QUERY,
    PROGRESS_SNAPSHOT,
    ANALYTICS_SNAPSHOT,
    SYNC_FOUNDATION,
}

data class StudyEngineDiagnostic(
    val kind: StudyEngineDiagnosticKind,
    val status: DebugLabStatus,
    val primaryCount: Int = 0,
    val secondaryCount: Int = 0,
    val percent: Int? = null,
    val score: Int? = null,
) {
    init {
        require(status != DebugLabStatus.RUNNING) { "Completed study-engine diagnostics cannot remain running." }
        require(primaryCount >= 0)
        require(secondaryCount >= 0)
        percent?.let { require(it in 0..100) }
        score?.let { require(it >= 0) }
    }
}

data class StudyEngineLabReport(
    val status: DebugLabStatus,
    val message: StudyEngineLabMessage = StudyEngineLabMessage.NONE,
    val inspectedAtEpochMillis: Long = 0L,
    val diagnostics: List<StudyEngineDiagnostic> = emptyList(),
) {
    init {
        require(inspectedAtEpochMillis >= 0L)
        require(diagnostics.distinctBy(StudyEngineDiagnostic::kind).size == diagnostics.size)
        require(diagnostics.none { it.kind.name.contains("TITLE") || it.kind.name.contains("NAME") })
    }

    val passedCount: Int = diagnostics.count { it.status == DebugLabStatus.PASSED }
    val warningCount: Int = diagnostics.count { it.status == DebugLabStatus.WARNING }
    val blockedCount: Int = diagnostics.count { it.status == DebugLabStatus.BLOCKED }
    val emptyCount: Int = diagnostics.count { it.status == DebugLabStatus.EMPTY }
    val notApplicableCount: Int = diagnostics.count { it.status == DebugLabStatus.NOT_APPLICABLE }
}
