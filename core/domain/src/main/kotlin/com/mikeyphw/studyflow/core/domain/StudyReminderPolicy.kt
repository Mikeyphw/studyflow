package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.StudyPlanSettings

/**
 * Typed reminder preferences consumed by the shared planner. Android owns the persistent policy in
 * ATT02; later clients can project the same contract without changing StudyPlanSettings storage.
 */
data class StudyReminderPolicy(
    val plannedStudyEnabled: Boolean = true,
    val dueReviewEnabled: Boolean = true,
    val missedActivityEnabled: Boolean = true,
    val dailySummaryEnabled: Boolean = true,
    val sessionTimerEnabled: Boolean = true,
    val examCountdownEnabled: Boolean = false,
    val plannedStudyLeadMinutes: Int = 10,
    val dailySummaryMinuteOfDay: Int = 8 * 60,
    val missedActivityDelayMinutes: Int = 30,
    val defaultSnoozeMinutes: Int = 10,
    val sessionEndingLeadMinutes: Int = 5,
    val examCountdownDays: Int = 7,
) {
    init {
        require(plannedStudyLeadMinutes in 0..120) { "Planned-study lead time must be 0..120 minutes." }
        require(dailySummaryMinuteOfDay in 0..1439) { "Daily summary time must be within the day." }
        require(missedActivityDelayMinutes in 5..720) { "Missed-activity delay must be 5..720 minutes." }
        require(defaultSnoozeMinutes in 5..240) { "Default snooze must be 5..240 minutes." }
        require(sessionEndingLeadMinutes in 1..30) { "Session-ending lead time must be 1..30 minutes." }
        require(examCountdownDays in 1..60) { "Exam countdown horizon must be 1..60 days." }
    }

    companion object {
        fun fromLegacy(settings: StudyPlanSettings): StudyReminderPolicy = StudyReminderPolicy(
            dueReviewEnabled = settings.reviewNotificationsEnabled,
        )
    }
}

/** Platform evidence shown by reminder settings without leaking Android implementation types. */
data class ReminderPlatformRuntimeEvidence(
    val notificationChannelCount: Int = 0,
    val blockedNotificationChannelCount: Int = 0,
    val exactAlarmCapabilityAvailable: Boolean = true,
    val exactAlarmRequired: Boolean = false,
    val lastReconciledAtEpochMillis: Long? = null,
    val lastDeliveredAtEpochMillis: Long? = null,
    val lastDeliveredOccurrenceId: String? = null,
    val lastRecoveryReason: String? = null,
)
