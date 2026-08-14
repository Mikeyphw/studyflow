package com.mikeyphw.studyflow.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderReliabilityCenterTest {
    @Test
    fun notificationPermissionBlocksDelivery() {
        val report = ReminderReliabilityCenter.inspect(input(notificationPermissionGranted = false))

        assertEquals(ReminderReliabilityStatus.BLOCKED, report.status)
        assertEquals(ReminderReliabilityFallback.WIDGET_AND_MANUAL_CHECK, report.fallback)
        assertTrue(report.issues.any { it.kind == ReminderReliabilityIssueKind.NOTIFICATION_PERMISSION })
    }

    @Test
    fun inexactAlarmDeliveryDoesNotClaimExactAlarmAccessIsRequired() {
        val report = ReminderReliabilityCenter.inspect(
            input(plannedActivityCandidateCount = 1, nextCandidateAtEpochMillis = 2_000L),
        )

        assertFalse(report.issues.any { it.kind.name.contains("EXACT_ALARM") })
        assertEquals(ReminderReliabilityFallback.NONE, report.fallback)
    }

    @Test
    fun quietHoursReportDelayInsteadOfSuppression() {
        val report = ReminderReliabilityCenter.inspect(input(quietHoursActive = true))

        assertEquals(ReminderReliabilityFallback.QUIET_HOURS_DELAY, report.fallback)
        assertTrue(report.issues.any { it.kind == ReminderReliabilityIssueKind.QUIET_HOURS_ACTIVE })
    }

    private fun input(
        notificationPermissionGranted: Boolean = true,
        batteryOptimizationIgnored: Boolean = true,
        quietHoursActive: Boolean = false,
        activeCandidateCount: Int = 1,
        dueReviewCount: Int = 0,
        missedActivityCount: Int = 0,
        plannedActivityCandidateCount: Int = 0,
        nextCandidateAtEpochMillis: Long? = null,
    ) = ReminderReliabilityInput(
        notificationPermissionGranted = notificationPermissionGranted,
        batteryOptimizationIgnored = batteryOptimizationIgnored,
        quietHoursActive = quietHoursActive,
        activeCandidateCount = activeCandidateCount,
        dueReviewCount = dueReviewCount,
        missedActivityCount = missedActivityCount,
        plannedActivityCandidateCount = plannedActivityCandidateCount,
        nextCandidateAtEpochMillis = nextCandidateAtEpochMillis,
        nowEpochMillis = 1_000L,
    )
}
