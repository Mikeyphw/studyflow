package com.mikeyphw.studyflow.reminders

import com.mikeyphw.studyflow.core.domain.ReminderNotificationCandidate
import com.mikeyphw.studyflow.core.domain.ReminderNotificationContentKind
import com.mikeyphw.studyflow.core.domain.ReminderNotificationType
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionCategory
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionEscalationMode
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionFocusPolicy
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionPriority
import java.util.TimeZone

/**
 * OV16 owns the Android attention wakeup boundary. The domain planner still emits
 * reminder candidates, but every Android alarm is normalized here through ATT10
 * focus/quiet-hours policy before a PendingIntent can be scheduled or consumed.
 */
object StudyReminderWakeupContracts {
    const val MinimumImmediateWakeupDelayMillis: Long = 1_000L
    const val PermissionDeniedRetryMillis: Long = 15 * 60_000L
    const val NearTermWidgetExactThresholdMillis: Long = 60_000L

    fun applyAuthoritativeQuietHours(
        candidate: ReminderNotificationCandidate,
        focusPolicy: StudyFlowAttentionFocusPolicy,
        nowEpochMillis: Long,
        timezoneId: String = TimeZone.getDefault().id,
    ): ReminderNotificationCandidate = quietDeferredCandidate(
        candidate = candidate,
        focusPolicy = focusPolicy,
        evaluationEpochMillis = candidate.scheduledAtEpochMillis,
        nowEpochMillis = nowEpochMillis,
        timezoneId = timezoneId,
    ) ?: candidate

    fun rearmFiredQuietCandidate(
        candidate: ReminderNotificationCandidate,
        focusPolicy: StudyFlowAttentionFocusPolicy,
        nowEpochMillis: Long,
        timezoneId: String = TimeZone.getDefault().id,
    ): ReminderNotificationCandidate? = quietDeferredCandidate(
        candidate = candidate,
        focusPolicy = focusPolicy,
        evaluationEpochMillis = nowEpochMillis,
        nowEpochMillis = nowEpochMillis,
        timezoneId = timezoneId,
    )

    fun permissionDeniedRetryCandidate(
        candidate: ReminderNotificationCandidate,
        nowEpochMillis: Long,
    ): ReminderNotificationCandidate = candidate.copy(
        fireAtEpochMillis = nowEpochMillis + PermissionDeniedRetryMillis,
        delayedByQuietHours = candidate.delayedByQuietHours,
    )

    fun mirrorEscalationEpochMillis(
        candidate: ReminderNotificationCandidate,
        focusPolicy: StudyFlowAttentionFocusPolicy,
    ): Long? {
        val policy = focusPolicy.categoryPolicy(candidate.attentionCategory())
        if (policy.mode != StudyFlowAttentionEscalationMode.MIRROR_IF_UNHANDLED) return null
        if (policy.mirrorAfterMinutes <= 0) return candidate.fireAtEpochMillis
        val scheduledAt = candidate.scheduledAtEpochMillis.coerceAtLeast(0L)
        return scheduledAt + policy.mirrorAfterMinutes * 60_000L
    }

    fun requiresExactWakeup(candidate: ReminderNotificationCandidate): Boolean =
        candidate.type == ReminderNotificationType.SESSION_ENDING ||
            candidate.type == ReminderNotificationType.SESSION_COMPLETED ||
            candidate.content.kind == ReminderNotificationContentKind.BREAK_COMPLETED

    fun requiresExactWidgetRefresh(triggerEpochMillis: Long, nowEpochMillis: Long): Boolean =
        triggerEpochMillis - nowEpochMillis <= NearTermWidgetExactThresholdMillis

    private fun quietDeferredCandidate(
        candidate: ReminderNotificationCandidate,
        focusPolicy: StudyFlowAttentionFocusPolicy,
        evaluationEpochMillis: Long,
        nowEpochMillis: Long,
        timezoneId: String,
    ): ReminderNotificationCandidate? {
        if (!focusPolicy.enabled) return null
        val quiet = focusPolicy.quietWindow?.takeIf { it.enabled } ?: return null
        if (!quiet.contains(evaluationEpochMillis, timezoneId)) return null
        if (quietBypassed(candidate, focusPolicy)) return null
        val deferredUntil = quiet.nextEndEpochMillis(evaluationEpochMillis, timezoneId)
            .coerceAtLeast(nowEpochMillis + MinimumImmediateWakeupDelayMillis)
        if (deferredUntil == candidate.fireAtEpochMillis && candidate.delayedByQuietHours) return null
        return candidate.copy(
            fireAtEpochMillis = deferredUntil,
            delayedByQuietHours = true,
        )
    }

    private fun quietBypassed(
        candidate: ReminderNotificationCandidate,
        focusPolicy: StudyFlowAttentionFocusPolicy,
    ): Boolean {
        if (candidate.occurrenceId.contains(":snooze:") ||
            candidate.content.kind == ReminderNotificationContentKind.BREAK_COMPLETED
        ) return false
        val categoryPolicy = focusPolicy.categoryPolicy(candidate.attentionCategory())
        val priority = candidate.attentionPriority()
        return categoryPolicy.bypassQuietHours ||
            (focusPolicy.allowTimeSensitiveQuietBypass && priority == StudyFlowAttentionPriority.TIME_SENSITIVE) ||
            (focusPolicy.allowHighPriorityQuietBypass && priority == StudyFlowAttentionPriority.HIGH)
    }
}

internal fun ReminderNotificationCandidate.attentionCategory(): StudyFlowAttentionCategory = when (type) {
    ReminderNotificationType.DUE_REVIEWS -> StudyFlowAttentionCategory.DUE_REVIEW
    ReminderNotificationType.PLANNED_ACTIVITY -> StudyFlowAttentionCategory.PLANNED_STUDY
    ReminderNotificationType.MISSED_ACTIVITY -> StudyFlowAttentionCategory.MISSED_ACTIVITY
    ReminderNotificationType.DAILY_SUMMARY -> StudyFlowAttentionCategory.DAILY_SUMMARY
    ReminderNotificationType.SESSION_ENDING -> StudyFlowAttentionCategory.SESSION_ENDING
    ReminderNotificationType.SESSION_COMPLETED -> StudyFlowAttentionCategory.SESSION_COMPLETED
    ReminderNotificationType.EXAM_COUNTDOWN -> StudyFlowAttentionCategory.EXAM_COUNTDOWN
}

internal fun ReminderNotificationCandidate.attentionPriority(): StudyFlowAttentionPriority = when {
    content.kind == ReminderNotificationContentKind.BREAK_COMPLETED -> StudyFlowAttentionPriority.NORMAL
    type == ReminderNotificationType.SESSION_ENDING || type == ReminderNotificationType.SESSION_COMPLETED ->
        StudyFlowAttentionPriority.TIME_SENSITIVE
    else -> StudyFlowAttentionPriority.NORMAL
}
