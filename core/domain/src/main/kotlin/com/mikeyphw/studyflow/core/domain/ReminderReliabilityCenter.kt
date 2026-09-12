package com.mikeyphw.studyflow.core.domain

enum class ReminderReliabilityStatus {
    READY,
    DEGRADED,
    ACTION_NEEDED,
    BLOCKED,
}

enum class ReminderReliabilityIssueKind {
    NOTIFICATION_PERMISSION,
    SYSTEM_NOTIFICATIONS_DISABLED,
    NOTIFICATION_CHANNEL_BLOCKED,
    BATTERY_OPTIMIZATION,
    QUIET_HOURS_ACTIVE,
    REVIEW_BACKLOG,
    MISSED_ACTIVITY_BACKLOG,
    NO_SCHEDULED_CANDIDATES,
    RECONCILIATION_STALE,
}

enum class ReminderReliabilityFallback {
    NONE,
    WIDGET_AND_MANUAL_CHECK,
    QUIET_HOURS_DELAY,
    BACKLOG_RECOVERY,
}

data class ReminderReliabilityIssue(
    val kind: ReminderReliabilityIssueKind,
    val blocking: Boolean,
)

data class ReminderReliabilityInput(
    val notificationPermissionGranted: Boolean,
    val appNotificationsEnabled: Boolean = notificationPermissionGranted,
    val notificationChannelCount: Int = 0,
    val blockedNotificationChannelCount: Int = 0,
    val batteryOptimizationIgnored: Boolean,
    val exactAlarmCapabilityAvailable: Boolean = true,
    val exactAlarmRequired: Boolean = false,
    val quietHoursActive: Boolean,
    val activeCandidateCount: Int,
    val dueReviewCount: Int,
    val missedActivityCount: Int,
    val plannedActivityCandidateCount: Int,
    val nextCandidateAtEpochMillis: Long?,
    val lastReconciledAtEpochMillis: Long? = null,
    val lastDeliveredAtEpochMillis: Long? = null,
    val lastDeliveredOccurrenceId: String? = null,
    val lastRecoveryReason: String? = null,
    val nowEpochMillis: Long,
) {
    init {
        require(notificationChannelCount >= 0) { "Notification channel count cannot be negative." }
        require(blockedNotificationChannelCount in 0..notificationChannelCount) {
            "Blocked notification channels must be within the channel count."
        }
        require(activeCandidateCount >= 0) { "Active candidate count cannot be negative." }
        require(dueReviewCount >= 0) { "Due review count cannot be negative." }
        require(missedActivityCount >= 0) { "Missed activity count cannot be negative." }
        require(plannedActivityCandidateCount >= 0) { "Planned candidate count cannot be negative." }
        require(nowEpochMillis >= 0L) { "Inspection time cannot be negative." }
        require(nextCandidateAtEpochMillis == null || nextCandidateAtEpochMillis >= 0L) {
            "Next candidate time cannot be negative."
        }
        require(lastReconciledAtEpochMillis == null || lastReconciledAtEpochMillis >= 0L)
        require(lastDeliveredAtEpochMillis == null || lastDeliveredAtEpochMillis >= 0L)
    }
}

data class ReminderReliabilityReport(
    val status: ReminderReliabilityStatus,
    val issues: List<ReminderReliabilityIssue>,
    val fallback: ReminderReliabilityFallback,
    val nextCandidateAtEpochMillis: Long?,
    val exactAlarmCapabilityAvailable: Boolean,
    val exactAlarmRequired: Boolean,
    val lastReconciledAtEpochMillis: Long?,
    val lastDeliveredAtEpochMillis: Long?,
    val lastDeliveredOccurrenceId: String?,
    val lastRecoveryReason: String?,
    val inspectedAtEpochMillis: Long,
) {
    val healthy: Boolean = status == ReminderReliabilityStatus.READY
    val actionIssueCount: Int = issues.count(ReminderReliabilityIssue::blocking)
}

object ReminderReliabilityCenter {
    private const val ReconciliationStaleMillis = 36L * 60L * 60L * 1000L

    fun inspect(input: ReminderReliabilityInput): ReminderReliabilityReport {
        val issues = buildList {
            if (!input.notificationPermissionGranted) {
                add(ReminderReliabilityIssue(ReminderReliabilityIssueKind.NOTIFICATION_PERMISSION, blocking = true))
            } else if (!input.appNotificationsEnabled) {
                add(ReminderReliabilityIssue(ReminderReliabilityIssueKind.SYSTEM_NOTIFICATIONS_DISABLED, blocking = true))
            }
            if (input.blockedNotificationChannelCount > 0) {
                add(
                    ReminderReliabilityIssue(
                        ReminderReliabilityIssueKind.NOTIFICATION_CHANNEL_BLOCKED,
                        blocking = input.notificationChannelCount > 0 &&
                            input.blockedNotificationChannelCount == input.notificationChannelCount,
                    ),
                )
            }
            if (!input.batteryOptimizationIgnored) {
                add(ReminderReliabilityIssue(ReminderReliabilityIssueKind.BATTERY_OPTIMIZATION, blocking = false))
            }
            if (input.quietHoursActive) {
                add(ReminderReliabilityIssue(ReminderReliabilityIssueKind.QUIET_HOURS_ACTIVE, blocking = false))
            }
            if (input.dueReviewCount > 0) {
                add(ReminderReliabilityIssue(ReminderReliabilityIssueKind.REVIEW_BACKLOG, blocking = false))
            }
            if (input.missedActivityCount > 0) {
                add(ReminderReliabilityIssue(ReminderReliabilityIssueKind.MISSED_ACTIVITY_BACKLOG, blocking = false))
            }
            if (input.activeCandidateCount == 0) {
                add(ReminderReliabilityIssue(ReminderReliabilityIssueKind.NO_SCHEDULED_CANDIDATES, blocking = false))
            }
            val reconciledAt = input.lastReconciledAtEpochMillis
            if (reconciledAt != null && input.nowEpochMillis - reconciledAt > ReconciliationStaleMillis) {
                add(ReminderReliabilityIssue(ReminderReliabilityIssueKind.RECONCILIATION_STALE, blocking = false))
            }
        }
        val status = when {
            issues.any(ReminderReliabilityIssue::blocking) -> ReminderReliabilityStatus.BLOCKED
            issues.any { it.kind in setOf(ReminderReliabilityIssueKind.MISSED_ACTIVITY_BACKLOG, ReminderReliabilityIssueKind.REVIEW_BACKLOG) } -> {
                ReminderReliabilityStatus.ACTION_NEEDED
            }
            issues.any { it.kind !in setOf(ReminderReliabilityIssueKind.NO_SCHEDULED_CANDIDATES) } -> {
                ReminderReliabilityStatus.DEGRADED
            }
            else -> ReminderReliabilityStatus.READY
        }
        val fallback = when {
            !input.notificationPermissionGranted || !input.appNotificationsEnabled -> {
                ReminderReliabilityFallback.WIDGET_AND_MANUAL_CHECK
            }
            input.quietHoursActive -> ReminderReliabilityFallback.QUIET_HOURS_DELAY
            input.missedActivityCount > 0 || input.dueReviewCount > 0 -> ReminderReliabilityFallback.BACKLOG_RECOVERY
            else -> ReminderReliabilityFallback.NONE
        }
        return ReminderReliabilityReport(
            status = status,
            issues = issues,
            fallback = fallback,
            nextCandidateAtEpochMillis = input.nextCandidateAtEpochMillis,
            exactAlarmCapabilityAvailable = input.exactAlarmCapabilityAvailable,
            exactAlarmRequired = input.exactAlarmRequired,
            lastReconciledAtEpochMillis = input.lastReconciledAtEpochMillis,
            lastDeliveredAtEpochMillis = input.lastDeliveredAtEpochMillis,
            lastDeliveredOccurrenceId = input.lastDeliveredOccurrenceId,
            lastRecoveryReason = input.lastRecoveryReason,
            inspectedAtEpochMillis = input.nowEpochMillis,
        )
    }
}
