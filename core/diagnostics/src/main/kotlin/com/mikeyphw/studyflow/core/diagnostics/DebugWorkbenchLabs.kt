package com.mikeyphw.studyflow.core.diagnostics

enum class DebugLabStatus {
    NOT_RUN,
    RUNNING,
    PASSED,
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
}

data class ReminderCandidateDiagnostic(
    val kind: ReminderCandidateKind,
    val scheduledAtEpochMillis: Long,
    val effectiveTriggerAtEpochMillis: Long,
    val deliveryWindowMillis: Long,
    val lateDeliveryGraceMillis: Long,
    val suppressedByQuietHours: Boolean,
    val pendingIntentIdentityExists: Boolean,
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
    val remindersEnabled: Boolean = false,
    val quietHoursActive: Boolean = false,
    val exactAlarmUsed: Boolean = false,
    val candidates: List<ReminderCandidateDiagnostic> = emptyList(),
) {
    init {
        require(inspectedAtEpochMillis >= 0L)
        require(apiLevel >= 0)
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
    val userTableCount: Int = 0,
    val totalEntityRows: Long = 0L,
    val metadataPresent: Boolean = false,
    val migrationPathComplete: Boolean = false,
) {
    init {
        require(schemaVersion >= 0)
        require(expectedSchemaVersion >= 0)
        require(foreignKeyViolationCount >= 0)
        require(userTableCount >= 0)
        require(totalEntityRows >= 0L)
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
}

data class PortabilityDrillDiagnostic(
    val kind: PortabilityDrillKind,
    val passed: Boolean,
    val destructive: Boolean,
)

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
