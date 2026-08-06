package com.mikeyphw.studyflow.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderReliabilityCenterTest {
    @Test
    fun missingNotificationPermissionBlocksReminderDeliveryAndFallsBackToWidget() {
        val report = ReminderReliabilityCenter.inspect(
            input = baseInput(notificationPermissionGranted = false),
        )

        assertEquals(ReminderReliabilityStatus.BLOCKED, report.status)
        assertEquals(ReminderReliabilityFallback.WIDGET_AND_MANUAL_CHECK, report.fallback)
        assertTrue(report.issues.any { issue -> issue.kind == ReminderReliabilityIssueKind.NOTIFICATION_PERMISSION })
        assertFalse(report.healthy)
    }

    @Test
    fun plannedActivitiesWithoutExactAlarmAccessDegradeButDoNotBlock() {
        val report = ReminderReliabilityCenter.inspect(
            input = baseInput(
                exactAlarmAvailable = false,
                plannedActivityCandidateCount = 2,
            ),
        )

        assertEquals(ReminderReliabilityStatus.DEGRADED, report.status)
        assertEquals(ReminderReliabilityFallback.INEXACT_ALARM_WINDOW, report.fallback)
        assertTrue(report.issues.none(ReminderReliabilityIssue::blocking))
    }

    @Test
    fun reviewOrMissedBacklogRequiresLearnerAction() {
        val report = ReminderReliabilityCenter.inspect(
            input = baseInput(dueReviewCount = 3, missedActivityCount = 1),
        )

        assertEquals(ReminderReliabilityStatus.ACTION_NEEDED, report.status)
        assertEquals(ReminderReliabilityFallback.BACKLOG_RECOVERY, report.fallback)
        assertEquals(2, report.issues.count { issue ->
            issue.kind in setOf(
                ReminderReliabilityIssueKind.REVIEW_BACKLOG,
                ReminderReliabilityIssueKind.MISSED_ACTIVITY_BACKLOG,
            )
        })
    }

    private fun baseInput(
        notificationPermissionGranted: Boolean = true,
        exactAlarmAvailable: Boolean = true,
        batteryOptimizationIgnored: Boolean = true,
        quietHoursActive: Boolean = false,
        activeCandidateCount: Int = 2,
        dueReviewCount: Int = 0,
        missedActivityCount: Int = 0,
        plannedActivityCandidateCount: Int = 1,
    ) = ReminderReliabilityInput(
        notificationPermissionGranted = notificationPermissionGranted,
        exactAlarmAvailable = exactAlarmAvailable,
        batteryOptimizationIgnored = batteryOptimizationIgnored,
        quietHoursActive = quietHoursActive,
        activeCandidateCount = activeCandidateCount,
        dueReviewCount = dueReviewCount,
        missedActivityCount = missedActivityCount,
        plannedActivityCandidateCount = plannedActivityCandidateCount,
        nextCandidateAtEpochMillis = 2_000L,
        nowEpochMillis = 1_000L,
    )
}
