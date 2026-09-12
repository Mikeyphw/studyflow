package com.mikeyphw.studyflow.core.link

import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StudyFlowAttentionFocusPolicyTest {
    private val workspace = StudyFlowWorkspaceId("workspace-att10")

    @Test
    fun defaultPolicyDoesNotInventQuietHours() {
        assertEquals(null, StudyFlowAttentionFocusPolicy().quietWindow)
    }

    @Test
    fun quietEndUsesConfiguredWallClockAcrossDstTransition() {
        val zone = ZoneId.of("America/New_York")
        val now = LocalDateTime.of(2026, 3, 8, 1, 30).atZone(zone).toInstant().toEpochMilli()
        val quiet = StudyFlowAttentionQuietWindow(
            enabled = true,
            startMinute = 22 * 60,
            endMinute = 7 * 60,
            timezoneId = zone.id,
        )
        val expected = LocalDateTime.of(2026, 3, 8, 7, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals(expected, quiet.nextEndEpochMillis(now))
    }

    @Test
    fun overnightQuietHoursDeferToLocalQuietEnd() {
        val zone = ZoneId.of("America/Sao_Paulo")
        val now = LocalDateTime.of(2026, 9, 12, 1, 30).atZone(zone).toInstant().toEpochMilli()
        val decision = StudyFlowAttentionFocusPolicyEngine.evaluate(
            occurrence = occurrence("overnight", StudyFlowAttentionCategory.PLANNED_STUDY, now, zone.id),
            policy = StudyFlowAttentionFocusPolicy(
                profile = StudyFlowAttentionFocusProfile.SLEEP_MODE,
                quietWindow = StudyFlowAttentionQuietWindow(true, 22 * 60, 7 * 60, zone.id),
                allowHighPriorityQuietBypass = false,
                allowTimeSensitiveQuietBypass = false,
            ),
            nowEpochMillis = now,
        )
        assertEquals(StudyFlowAttentionPolicyOutcome.DEFER_UNTIL_QUIET_END, decision.outcome)
        val expected = LocalDateTime.of(2026, 9, 12, 7, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals(expected, decision.deferredUntilEpochMillis)
        assertTrue(decision.quietHoursActive)
    }

    @Test
    fun examCountdownRequiresActionAndCanBypassQuietHours() {
        val now = 10_000L
        val decision = StudyFlowAttentionFocusPolicyEngine.evaluate(
            occurrence = occurrence(
                "exam",
                StudyFlowAttentionCategory.EXAM_COUNTDOWN,
                now,
                "UTC",
                StudyFlowAttentionPriority.HIGH,
            ),
            policy = StudyFlowAttentionFocusPolicy(
                profile = StudyFlowAttentionFocusProfile.EXAM_MODE,
                quietWindow = StudyFlowAttentionQuietWindow(true, 0, 60, "UTC"),
            ),
            nowEpochMillis = now,
        )
        assertEquals(StudyFlowAttentionPolicyOutcome.REQUIRE_USER_ACTION, decision.outcome)
        assertTrue(decision.requiresUserAction)
    }

    @Test
    fun handledLedgerOccurrenceSuppressesDuplicateBeforeProfileRules() {
        val occurrence = occurrence("done", StudyFlowAttentionCategory.DUE_REVIEW, 10_000L, "UTC")
        val decision = StudyFlowAttentionFocusPolicyEngine.evaluate(
            occurrence,
            StudyFlowAttentionFocusPolicy(profile = StudyFlowAttentionFocusProfile.EXAM_MODE),
            10_000L,
            StudyFlowAttentionLedgerEntry(occurrence, StudyFlowAttentionDeliveryState.ACKNOWLEDGED, 9_000L),
        )
        assertEquals(StudyFlowAttentionPolicyOutcome.SUPPRESS_DUPLICATE, decision.outcome)
    }

    @Test
    fun overdueReviewEscalatesOneBoundedMirrorInBalancedProfile() {
        val now = 2_000_000L
        val occurrence = occurrence("mirror", StudyFlowAttentionCategory.DUE_REVIEW, now - 31 * 60_000L, "UTC")
        val decision = StudyFlowAttentionFocusPolicyEngine.evaluate(
            occurrence,
            StudyFlowAttentionFocusPolicy(profile = StudyFlowAttentionFocusProfile.BALANCED, quietWindow = null),
            now,
        )
        assertEquals(StudyFlowAttentionPolicyOutcome.ESCALATE_MIRROR, decision.outcome)
        assertEquals(1, decision.requestedMirrorCount)
    }

    @Test
    fun minimalTvRoutesToPhoneRatherThanPretendingTvBackgroundDelivery() {
        val now = 20_000L
        val decision = StudyFlowAttentionFocusPolicyEngine.evaluate(
            occurrence("tv", StudyFlowAttentionCategory.PLANNED_STUDY, now, "UTC"),
            StudyFlowAttentionFocusPolicy(profile = StudyFlowAttentionFocusProfile.MINIMAL_TV, quietWindow = null),
            now,
            localClientKind = StudyFlowLinkClientKind.ANDROID_TV,
        )
        assertEquals(StudyFlowAttentionPolicyOutcome.ROUTE_TO_PHONE, decision.outcome)
        assertTrue(decision.preferPhone)
    }

    private fun occurrence(
        id: String,
        category: StudyFlowAttentionCategory,
        scheduledAt: Long,
        timezoneId: String,
        priority: StudyFlowAttentionPriority = StudyFlowAttentionPriority.NORMAL,
    ) = StudyFlowAttentionOccurrence(
        occurrenceId = StudyFlowAttentionOccurrenceId.deterministic("att10", id),
        workspaceId = workspace,
        category = category,
        priority = priority,
        scheduledAtEpochMillis = scheduledAt,
        originalScheduledAtEpochMillis = scheduledAt,
        expiresAtEpochMillis = scheduledAt + 12 * 60 * 60_000L,
        timezoneId = timezoneId,
    )
}
