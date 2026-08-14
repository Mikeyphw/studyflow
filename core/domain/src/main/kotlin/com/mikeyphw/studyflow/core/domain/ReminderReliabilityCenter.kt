package com.mikeyphw.studyflow.core.domain

enum class ReminderReliabilityStatus {
    READY,
    DEGRADED,
    ACTION_NEEDED,
    BLOCKED,
}

enum class ReminderReliabilityIssueKind {
    NOTIFICATION_PERMISSION,
    BATTERY_OPTIMIZATION,
    QUIET_HOURS_ACTIVE,
    REVIEW_BACKLOG,
    MISSED_ACTIVITY_BACKLOG,
    NO_SCHEDULED_CANDIDATES,
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
    val batteryOptimizationIgnored: Boolean,
    val quietHoursActive: Boolean,
    val activeCandidateCount: Int,
    val dueReviewCount: Int,
    val missedActivityCount: Int,
    val plannedActivityCandidateCount: Int,
    val nextCandidateAtEpochMillis: Long?,
    val nowEpochMillis: Long,
) {
    init {
        require(activeCandidateCount >= 0) { "Active candidate count cannot be negative." }
        require(dueReviewCount >= 0) { "Due review count cannot be negative." }
        require(missedActivityCount >= 0) { "Missed activity count cannot be negative." }
        require(plannedActivityCandidateCount >= 0) { "Planned candidate count cannot be negative." }
        require(nowEpochMillis >= 0L) { "Inspection time cannot be negative." }
        require(nextCandidateAtEpochMillis == null || nextCandidateAtEpochMillis >= 0L) {
            "Next candidate time cannot be negative."
        }
    }
}

data class ReminderReliabilityReport(
    val status: ReminderReliabilityStatus,
    val issues: List<ReminderReliabilityIssue>,
    val fallback: ReminderReliabilityFallback,
    val nextCandidateAtEpochMillis: Long?,
    val inspectedAtEpochMillis: Long,
) {
    val healthy: Boolean = status == ReminderReliabilityStatus.READY
    val actionIssueCount: Int = issues.count(ReminderReliabilityIssue::blocking)
}

object ReminderReliabilityCenter {
    fun inspect(input: ReminderReliabilityInput): ReminderReliabilityReport {
        val issues = buildList {
            if (!input.notificationPermissionGranted) {
                add(ReminderReliabilityIssue(ReminderReliabilityIssueKind.NOTIFICATION_PERMISSION, blocking = true))
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
        }
        val status = when {
            issues.any(ReminderReliabilityIssue::blocking) -> ReminderReliabilityStatus.BLOCKED
            issues.any { it.kind == ReminderReliabilityIssueKind.MISSED_ACTIVITY_BACKLOG } -> {
                ReminderReliabilityStatus.ACTION_NEEDED
            }
            issues.any { it.kind == ReminderReliabilityIssueKind.REVIEW_BACKLOG } -> {
                ReminderReliabilityStatus.ACTION_NEEDED
            }
            issues.any { it.kind != ReminderReliabilityIssueKind.NO_SCHEDULED_CANDIDATES } -> {
                ReminderReliabilityStatus.DEGRADED
            }
            else -> ReminderReliabilityStatus.READY
        }
        val fallback = when {
            !input.notificationPermissionGranted -> ReminderReliabilityFallback.WIDGET_AND_MANUAL_CHECK
            input.quietHoursActive -> ReminderReliabilityFallback.QUIET_HOURS_DELAY
            input.missedActivityCount > 0 || input.dueReviewCount > 0 -> ReminderReliabilityFallback.BACKLOG_RECOVERY
            else -> ReminderReliabilityFallback.NONE
        }
        return ReminderReliabilityReport(
            status = status,
            issues = issues,
            fallback = fallback,
            nextCandidateAtEpochMillis = input.nextCandidateAtEpochMillis,
            inspectedAtEpochMillis = input.nowEpochMillis,
        )
    }
}
