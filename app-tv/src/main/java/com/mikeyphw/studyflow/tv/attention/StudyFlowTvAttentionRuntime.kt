package com.mikeyphw.studyflow.tv.attention

import android.content.Context
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionDeliveryState
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionDiagnosticsSnapshot
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionLedger
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionOccurrence
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionFocusPolicy
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionFocusProfile
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionReliabilityEvidenceSnapshot
import com.mikeyphw.studyflow.core.link.StudyFlowCompactAttentionProjection
import com.mikeyphw.studyflow.core.link.StudyFlowLinkPeerDescriptor
import com.mikeyphw.studyflow.core.link.StudyFlowNotificationDelivery
import com.mikeyphw.studyflow.core.link.StudyFlowNotificationDeliveryAck
import com.mikeyphw.studyflow.core.link.StudyFlowNotificationDeliveryStatus
import com.mikeyphw.studyflow.core.link.StudyFlowPeerClock
import com.mikeyphw.studyflow.tv.link.StudyFlowTvAttentionLedgerStorage
import com.mikeyphw.studyflow.tv.link.StudyFlowTvNotificationMediaReceiver

/** Production ATT06 Android TV foreground attention runtime. */
class StudyFlowTvAttentionRuntime(
    private val receiver: StudyFlowTvNotificationMediaReceiver,
    private val attentionLedger: StudyFlowAttentionLedger,
    private val localPeer: StudyFlowLinkPeerDescriptor,
    private val clock: StudyFlowPeerClock,
) {
    private var state: StudyFlowTvAttentionState = StudyFlowTvAttentionState()
    private var compactProjection: StudyFlowCompactAttentionProjection? = null
    private var lastOccurrence: StudyFlowAttentionOccurrence? = null

    fun snapshot(): StudyFlowTvAttentionState = synchronized(this) { state }
    fun compactProjection(): StudyFlowCompactAttentionProjection? = synchronized(this) { compactProjection }

    fun ingest(delivery: StudyFlowNotificationDelivery): StudyFlowTvAttentionIngestResult = synchronized(this) {
        val ack = receiver.applyNotification(delivery)
        val banner = StudyFlowTvAttentionSurface.bannerFor(delivery, ack)
        state = if (banner != null && ack.status == StudyFlowNotificationDeliveryStatus.ACCEPTED) {
            StudyFlowTvAttentionSurface.present(state, banner)
        } else {
            state.copy(updatedAtEpochMillis = ack.acknowledgedAtEpochMillis)
        }
        if (ack.status == StudyFlowNotificationDeliveryStatus.ACCEPTED) {
            val occurrence = delivery.notification.canonicalAttentionOccurrence(delivery.sourceDeviceId)
            lastOccurrence = occurrence
            val entry = attentionLedger.entry(occurrence.occurrenceId)
            val route = StudyFlowTvAttentionDeliveryRouterBridge.routeTargetedDelivery(
                occurrence = occurrence,
                localPeer = localPeer,
                nowEpochMillis = ack.acknowledgedAtEpochMillis,
                ledgerEntry = entry,
            )
            compactProjection = StudyFlowTvCompactCompanionBridge.projectForegroundOverlay(
                occurrence = occurrence,
                route = route,
                parentDeviceId = localPeer.deviceId,
                nowEpochMillis = ack.acknowledgedAtEpochMillis,
                ledgerEntry = entry,
            )
        }
        StudyFlowTvAttentionIngestResult(
            ack = ack,
            state = state,
            foregroundBannerShown = banner != null && ack.status == StudyFlowNotificationDeliveryStatus.ACCEPTED,
        )
    }

    fun applyAction(
        action: StudyFlowTvAttentionAction,
        commandId: String,
    ): StudyFlowTvAttentionActionResult = synchronized(this) {
        val now = clock.nowEpochMillis()
        val result = StudyFlowTvAttentionSurface.reduce(state, action, commandId, now)
        val occurrenceId = state.activeBanner?.occurrenceId
        if (occurrenceId != null) {
            attentionLedger.transitionByOccurrenceId(
                occurrenceId = occurrenceId,
                state = result.acknowledgementState,
                nowEpochMillis = now,
            )
        }
        state = result.state
        result
    }

    fun focusProjection(): StudyFlowTvAttentionFocusProjection? = synchronized(this) {
        val occurrence = lastOccurrence ?: return@synchronized null
        StudyFlowTvAttentionDeliveryRouterBridge.focusProjection(
            occurrence = occurrence,
            policy = StudyFlowAttentionFocusPolicy(
            profile = StudyFlowAttentionFocusProfile.MINIMAL_TV,
            quietWindow = null,
        ),
            nowEpochMillis = clock.nowEpochMillis(),
            ledgerEntry = attentionLedger.entry(occurrence.occurrenceId),
        )
    }

    fun reliabilityProjection(): StudyFlowAttentionReliabilityEvidenceSnapshot = synchronized(this) {
        StudyFlowTvAttentionDeliveryRouterBridge.reliabilityProjection(
            ledger = attentionLedger.snapshot(),
            localPeer = localPeer,
            nowEpochMillis = clock.nowEpochMillis(),
        )
    }

    fun diagnostics(): StudyFlowTvAttentionDiagnostics = synchronized(this) {
        val ledger = attentionLedger.diagnostics()
        StudyFlowTvAttentionDiagnostics(
            foregroundBannerVisible = state.hasForegroundBanner,
            capabilitySummary = StudyFlowTvAttentionSurface.capabilitySummary(state.capability),
            attentionLedger = ledger,
            lastAcknowledgedOccurrenceId = state.lastAcknowledgedOccurrenceId,
            lastAction = state.lastAction,
        )
    }

    companion object {
        fun persistent(
            context: Context,
            tvIdentity: StudyFlowLinkPeerDescriptor,
            clock: StudyFlowPeerClock,
        ): StudyFlowTvAttentionRuntime {
            val ledger = StudyFlowAttentionLedger(StudyFlowTvAttentionLedgerStorage(context.applicationContext))
            return StudyFlowTvAttentionRuntime(
                receiver = StudyFlowTvNotificationMediaReceiver(tvIdentity, clock, ledger),
                attentionLedger = ledger,
                localPeer = tvIdentity,
                clock = clock,
            )
        }
    }
}

data class StudyFlowTvAttentionIngestResult(
    val ack: StudyFlowNotificationDeliveryAck,
    val state: StudyFlowTvAttentionState,
    val foregroundBannerShown: Boolean,
)

data class StudyFlowTvAttentionDiagnostics(
    val foregroundBannerVisible: Boolean,
    val capabilitySummary: String,
    val attentionLedger: StudyFlowAttentionDiagnosticsSnapshot,
    val lastAcknowledgedOccurrenceId: String?,
    val lastAction: StudyFlowTvAttentionAction?,
) {
    val closedAppDeliveryTruth: String = "foreground-only; fallback requires designated notifier"
    val unifiedTvPlaneTruth: String = "OV21 attention ingress uses canonical TV plane before surface delivery"
}
