package com.mikeyphw.studyflow.core.link

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StudyFlowAttentionReliabilityEvidenceTest {
    private val workspace = StudyFlowWorkspaceId("workspace-att11")
    private val phone = peer("phone", StudyFlowLinkClientKind.ANDROID)
    private val web = peer("web", StudyFlowLinkClientKind.WEB)
    private val tv = peer("tv", StudyFlowLinkClientKind.ANDROID_TV)

    @Test
    fun analyticsSeparateDeferredMissedSnoozedDismissedAndDoneEvidence() {
        val ledger = StudyFlowAttentionLedger()
        val deferred = occurrence("deferred")
        ledger.upsertScheduled(deferred, 1_000L)
        ledger.recordPolicyDecision(
            StudyFlowAttentionPolicyDecision(
                occurrenceId = deferred.occurrenceId,
                profile = StudyFlowAttentionFocusProfile.SLEEP_MODE,
                outcome = StudyFlowAttentionPolicyOutcome.DEFER_UNTIL_QUIET_END,
                evaluatedAtEpochMillis = 1_100L,
                deferredUntilEpochMillis = 5_000L,
                quietHoursActive = true,
                explanation = "Deferred for quiet hours.",
            ),
        )

        val missed = occurrence("missed")
        ledger.upsertScheduled(missed, 1_000L)
        ledger.transition(missed.occurrenceId, StudyFlowAttentionDeliveryState.MISSED, 1_200L)

        val snoozed = occurrence("snoozed")
        ledger.upsertScheduled(snoozed, 1_000L)
        ledger.transition(snoozed.occurrenceId, StudyFlowAttentionDeliveryState.DELIVERY_PENDING, 1_100L)
        ledger.transition(snoozed.occurrenceId, StudyFlowAttentionDeliveryState.SNOOZED, 1_300L)

        val dismissed = occurrence("dismissed")
        ledger.upsertScheduled(dismissed, 1_000L)
        ledger.transition(dismissed.occurrenceId, StudyFlowAttentionDeliveryState.DELIVERY_PENDING, 1_100L)
        ledger.transition(dismissed.occurrenceId, StudyFlowAttentionDeliveryState.DELIVERED, 1_200L)
        ledger.transition(dismissed.occurrenceId, StudyFlowAttentionDeliveryState.DISMISSED, 1_400L)

        val done = occurrence("done")
        ledger.upsertScheduled(done, 1_000L)
        ledger.transition(done.occurrenceId, StudyFlowAttentionDeliveryState.DELIVERY_PENDING, 1_100L)
        ledger.transition(done.occurrenceId, StudyFlowAttentionDeliveryState.DELIVERED, 1_200L)
        ledger.transition(done.occurrenceId, StudyFlowAttentionDeliveryState.COMPLETED, 1_500L)

        val snapshot = StudyFlowAttentionReliabilityAnalyzer.analyze(
            ledger = ledger.snapshot(),
            devices = listOf(phone.asAttentionDeviceCapability(primaryUserDevice = true)),
            nowEpochMillis = 2_000L,
            workspaceId = workspace,
        )

        assertEquals(5, snapshot.analytics.totalCount)
        assertEquals(1, snapshot.analytics.deferredCount)
        assertEquals(1, snapshot.analytics.missedCount)
        assertEquals(1, snapshot.analytics.snoozedCount)
        assertEquals(1, snapshot.analytics.dismissedCount)
        assertEquals(1, snapshot.analytics.doneCount)
    }

    @Test
    fun routeAuditIsPersistedOnCanonicalLedgerEntryAndExported() {
        val ledger = StudyFlowAttentionLedger()
        val occurrence = occurrence("route")
        ledger.upsertScheduled(occurrence, 1_000L)
        val route = StudyFlowAttentionDeliveryRouter().routeWithPolicy(
            occurrence = occurrence,
            devices = listOf(
                phone.asAttentionDeviceCapability(primaryUserDevice = true),
                peer("desktop", StudyFlowLinkClientKind.DESKTOP).asAttentionDeviceCapability(),
            ),
            nowEpochMillis = 40 * 60_000L,
            focusPolicy = StudyFlowAttentionFocusPolicy(quietWindow = null, maxEscalationMirrors = 1),
            context = StudyFlowAttentionRoutingContext(primaryDeviceId = phone.deviceId, maxMirrors = 1),
        )
        ledger.recordRouteDecision(route)

        val entry = requireNotNull(ledger.entry(occurrence.occurrenceId))
        assertEquals(phone.deviceId, entry.routeAudit?.owner?.deviceId)
        assertTrue(entry.routeAudit?.mirrors?.size == 1)

        val snapshot = StudyFlowAttentionReliabilityAnalyzer.analyze(
            ledger.snapshot(),
            listOf(
                phone.asAttentionDeviceCapability(primaryUserDevice = true),
                peer("desktop", StudyFlowLinkClientKind.DESKTOP).asAttentionDeviceCapability(),
            ),
            40 * 60_000L,
            workspace,
        )
        assertEquals(1, snapshot.analytics.auditedRouteCount)
        val json = StudyFlowAttentionReliabilityExport.toJson(snapshot)
        val csv = StudyFlowAttentionReliabilityExport.toCsv(snapshot)
        assertContains(json, occurrence.occurrenceId.value)
        assertContains(json, "routeAudit")
        assertContains(csv, "owner_device")
        assertContains(csv, "phone")
    }

    @Test
    fun platformDiagnosticsStayTruthfulAboutWebPushAndTvForegroundOnlyDelivery() {
        val snapshot = StudyFlowAttentionReliabilityAnalyzer.analyze(
            StudyFlowAttentionLedgerSnapshot(),
            listOf(
                phone.asAttentionDeviceCapability(primaryUserDevice = true),
                web.asAttentionDeviceCapability(
                    deliverySurfaces = listOf(
                        StudyFlowAttentionDeliverySurface.SYSTEM_NOTIFICATION,
                        StudyFlowAttentionDeliverySurface.WIDGET_OR_BADGE,
                    ),
                ),
                tv.asAttentionDeviceCapability(
                    availability = StudyFlowAttentionDeviceAvailability.FOREGROUND,
                    foregroundActive = true,
                ),
            ),
            10_000L,
        )

        val phoneDiagnostic = snapshot.platforms.single { it.deviceId == phone.deviceId }
        val webDiagnostic = snapshot.platforms.single { it.deviceId == web.deviceId }
        val tvDiagnostic = snapshot.platforms.single { it.deviceId == tv.deviceId }
        assertTrue(phoneDiagnostic.truthfulBackgroundDelivery)
        assertFalse(webDiagnostic.truthfulBackgroundDelivery)
        assertEquals(StudyFlowAttentionPlatformReliabilityStatus.FOREGROUND_ONLY, webDiagnostic.status)
        assertFalse(tvDiagnostic.truthfulBackgroundDelivery)
        assertEquals(StudyFlowAttentionPlatformReliabilityStatus.FOREGROUND_ONLY, tvDiagnostic.status)
        assertContains(tvDiagnostic.explanation, "foreground-only")
    }

    private fun occurrence(id: String) = StudyFlowAttentionOccurrence(
        occurrenceId = StudyFlowAttentionOccurrenceId.deterministic("att11", id),
        workspaceId = workspace,
        category = StudyFlowAttentionCategory.DUE_REVIEW,
        priority = StudyFlowAttentionPriority.NORMAL,
        scheduledAtEpochMillis = 1_000L,
        originalScheduledAtEpochMillis = 1_000L,
        expiresAtEpochMillis = 9_999_999L,
        deliveryPolicy = StudyFlowAttentionDeliveryPolicy.PHONE_AND_DESKTOP,
    )

    private fun peer(id: String, kind: StudyFlowLinkClientKind) = StudyFlowLinkPeerDescriptor(
        deviceId = StudyFlowDeviceId(id),
        displayName = id,
        clientKind = kind,
        capabilities = StudyFlowClientProfiles.capabilitiesFor(kind),
    )
}
