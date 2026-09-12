package com.mikeyphw.studyflow.tv.attention

import com.mikeyphw.studyflow.core.link.StudyFlowAttentionDeliveryRouter
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionDeliveryRoute
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionFocusPolicy
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionFocusPolicyEngine
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionLedgerEntry
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionOccurrence
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionPolicyDecision
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionLedgerSnapshot
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionReliabilityAnalyzer
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionReliabilityEvidenceSnapshot
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionDeliverySurface
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionDeviceAvailability
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionDeviceCapability
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionRoutingContext
import com.mikeyphw.studyflow.core.link.StudyFlowLinkClientKind
import com.mikeyphw.studyflow.core.link.StudyFlowLinkPeerDescriptor

object StudyFlowTvAttentionDeliveryRouterBridge {
    fun localForegroundCapability(
        localPeer: StudyFlowLinkPeerDescriptor,
        nowEpochMillis: Long,
    ): StudyFlowAttentionDeviceCapability = StudyFlowAttentionDeviceCapability(
        peer = localPeer,
        availability = StudyFlowAttentionDeviceAvailability.FOREGROUND,
        deliverySurfaces = listOf(
            StudyFlowAttentionDeliverySurface.FOREGROUND_BANNER,
            StudyFlowAttentionDeliverySurface.LEDGER_ONLY,
        ),
        foregroundActive = true,
        notificationPermissionGranted = false,
        lastSeenAtEpochMillis = nowEpochMillis,
    )

    fun foregroundRoutingContext(localPeer: StudyFlowLinkPeerDescriptor): StudyFlowAttentionRoutingContext =
        StudyFlowAttentionRoutingContext(
            localDeviceId = localPeer.deviceId,
            foregroundDeviceId = localPeer.deviceId,
            staleAfterMillis = 60_000L,
            allowTvForegroundOwners = true,
        )

    fun reliabilityProjection(
        ledger: StudyFlowAttentionLedgerSnapshot,
        localPeer: StudyFlowLinkPeerDescriptor,
        nowEpochMillis: Long,
    ): StudyFlowAttentionReliabilityEvidenceSnapshot = StudyFlowAttentionReliabilityAnalyzer.analyze(
        ledger = ledger,
        devices = listOf(localForegroundCapability(localPeer, nowEpochMillis)),
        nowEpochMillis = nowEpochMillis,
    )

    fun focusProjection(
        occurrence: StudyFlowAttentionOccurrence,
        policy: StudyFlowAttentionFocusPolicy,
        nowEpochMillis: Long,
        ledgerEntry: StudyFlowAttentionLedgerEntry? = null,
    ): StudyFlowTvAttentionFocusProjection {
        val decision = StudyFlowAttentionFocusPolicyEngine.evaluate(
            occurrence = occurrence,
            policy = policy,
            nowEpochMillis = nowEpochMillis,
            ledgerEntry = ledgerEntry,
            localClientKind = StudyFlowLinkClientKind.ANDROID_TV,
        )
        return StudyFlowTvAttentionFocusProjection(
            profile = decision.profile.name,
            outcome = decision.outcome.name,
            quietHoursActive = decision.quietHoursActive,
            controlsReadOnly = true,
            remoteControlRequired = true,
            explanation = decision.explanation,
        )
    }

    fun routeTargetedDelivery(
        occurrence: StudyFlowAttentionOccurrence,
        localPeer: StudyFlowLinkPeerDescriptor,
        nowEpochMillis: Long,
        ledgerEntry: StudyFlowAttentionLedgerEntry? = null,
    ): StudyFlowAttentionDeliveryRoute = router().route(
        occurrence = occurrence.copy(targetDeviceIds = listOf(localPeer.deviceId)),
        devices = listOf(localForegroundCapability(localPeer, nowEpochMillis)),
        nowEpochMillis = nowEpochMillis,
        context = foregroundRoutingContext(localPeer),
        ledgerEntry = ledgerEntry,
    )

    fun routeWithPolicy(
        occurrence: StudyFlowAttentionOccurrence,
        devices: List<StudyFlowAttentionDeviceCapability>,
        localPeer: StudyFlowLinkPeerDescriptor,
        policy: StudyFlowAttentionFocusPolicy,
        nowEpochMillis: Long,
        ledgerEntry: StudyFlowAttentionLedgerEntry? = null,
    ): StudyFlowAttentionDeliveryRoute = router().routeWithPolicy(
        occurrence = occurrence,
        devices = devices,
        nowEpochMillis = nowEpochMillis,
        focusPolicy = policy,
        context = foregroundRoutingContext(localPeer),
        ledgerEntry = ledgerEntry,
        localClientKind = StudyFlowLinkClientKind.ANDROID_TV,
    )

    fun router(): StudyFlowAttentionDeliveryRouter = StudyFlowAttentionDeliveryRouter()
}

data class StudyFlowTvAttentionFocusProjection(
    val profile: String,
    val outcome: String,
    val quietHoursActive: Boolean,
    val controlsReadOnly: Boolean,
    val remoteControlRequired: Boolean,
    val explanation: String,
)
