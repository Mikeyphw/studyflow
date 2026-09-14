package com.mikeyphw.studyflow.reminders

import com.mikeyphw.studyflow.core.domain.ReminderNotificationCandidate
import com.mikeyphw.studyflow.core.domain.ReminderNotificationContent
import com.mikeyphw.studyflow.core.domain.ReminderNotificationContentKind
import com.mikeyphw.studyflow.core.domain.ReminderNotificationType
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionCategory
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionCategoryEscalationPolicy
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionEscalationMode
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionFocusPolicy
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionQuietWindow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StudyReminderWakeupContractsOv16Test {
    @Test
    fun att10QuietHoursRearmSnoozeInsteadOfConsumingIt() {
        val now = 1_800_000L
        val quietPolicy = StudyFlowAttentionFocusPolicy(
            quietWindow = StudyFlowAttentionQuietWindow(
                enabled = true,
                startMinute = 0,
                endMinute = 60,
                timezoneId = "UTC",
            ),
            allowHighPriorityQuietBypass = false,
            allowTimeSensitiveQuietBypass = false,
        )
        val original = candidate(
            type = ReminderNotificationType.PLANNED_ACTIVITY,
            scheduledAt = now,
            fireAt = now,
        )

        val rearmed = StudyReminderWakeupContracts.rearmFiredQuietCandidate(original, quietPolicy, now, "UTC")

        val actual = assertNotNull(rearmed)
        assertTrue(actual.fireAtEpochMillis > now)
        assertTrue(actual.delayedByQuietHours)
    }

    @Test
    fun mirrorIfUnhandledProducesSecondWakeupBoundary() {
        val policy = StudyFlowAttentionFocusPolicy(
            categoryPolicies = listOf(
                StudyFlowAttentionCategoryEscalationPolicy(
                    category = StudyFlowAttentionCategory.PLANNED_STUDY,
                    mode = StudyFlowAttentionEscalationMode.MIRROR_IF_UNHANDLED,
                    mirrorAfterMinutes = 10,
                ),
            ),
        )
        val original = candidate(
            type = ReminderNotificationType.PLANNED_ACTIVITY,
            scheduledAt = 5_000L,
            fireAt = 5_000L,
        )

        assertEquals(605_000L, StudyReminderWakeupContracts.mirrorEscalationEpochMillis(original, policy))
    }

    @Test
    fun sessionAndBreakWakeupsRequireExactScheduling() {
        assertTrue(
            StudyReminderWakeupContracts.requiresExactWakeup(
                candidate(ReminderNotificationType.SESSION_ENDING, 10_000L, 10_000L),
            ),
        )
        assertTrue(
            StudyReminderWakeupContracts.requiresExactWakeup(
                candidate(
                    type = ReminderNotificationType.SESSION_COMPLETED,
                    scheduledAt = 10_000L,
                    fireAt = 10_000L,
                    kind = ReminderNotificationContentKind.BREAK_COMPLETED,
                ),
            ),
        )
    }

    private fun candidate(
        type: ReminderNotificationType,
        scheduledAt: Long,
        fireAt: Long,
        kind: ReminderNotificationContentKind = when (type) {
            ReminderNotificationType.DUE_REVIEWS -> ReminderNotificationContentKind.DUE_REVIEWS
            ReminderNotificationType.PLANNED_ACTIVITY -> ReminderNotificationContentKind.PLANNED_ACTIVITY
            ReminderNotificationType.MISSED_ACTIVITY -> ReminderNotificationContentKind.MISSED_ACTIVITY
            ReminderNotificationType.DAILY_SUMMARY -> ReminderNotificationContentKind.DAILY_SUMMARY
            ReminderNotificationType.SESSION_ENDING -> ReminderNotificationContentKind.SESSION_ENDING
            ReminderNotificationType.SESSION_COMPLETED -> ReminderNotificationContentKind.SESSION_COMPLETED
            ReminderNotificationType.EXAM_COUNTDOWN -> ReminderNotificationContentKind.EXAM_COUNTDOWN
        },
    ) = ReminderNotificationCandidate(
        type = type,
        occurrenceId = "ov16-${type.name.lowercase()}",
        content = ReminderNotificationContent(kind = kind),
        scheduledAtEpochMillis = scheduledAt,
        fireAtEpochMillis = fireAt,
        delayedByQuietHours = false,
    )
}
