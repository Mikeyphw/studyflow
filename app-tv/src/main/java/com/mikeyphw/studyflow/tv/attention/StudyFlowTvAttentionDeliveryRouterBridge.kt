package com.mikeyphw.studyflow.tv.attention

import com.mikeyphw.studyflow.core.link.StudyFlowAttentionDeliveryRouter
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionDeliveryRoute
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionFocusPolicy
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionFocusPolicyEngine
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionLedgerEntry
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionOccurrence
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionPolicyDecision
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionDeliverySurface
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionDeviceAvailability
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionDeviceCapability
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionRoutingContext
import com.mikeyphw.studyflow.core.link.StudyFlowClientProfiles
import com.mikeyphw.studyflow.core.link.StudyFlowDeviceId
import com.mikeyphw.studyflow.core.link.StudyFlowLinkClientKind
import com.mikeyphw.studyflow.core.link.StudyFlowLinkPeerDescriptor

private const val AndroidTvAttentionDeviceId = "android-tv-local"

object StudyFlowTvAttentionDeliveryRouterBridge {
    val localDeviceId: StudyFlowDeviceId = StudyFlowDeviceId(AndroidTvAttentionDeviceId)

    fun localForegroundCapability(nowEpochMillis: Long): StudyFlowAttentionDeviceCapability =
        StudyFlowAttentionDeviceCapability(
            peer = StudyFlowLinkPeerDescriptor(
                deviceId = localDeviceId,
                displayName = "StudyFlow TV",
                clientKind = StudyFlowLinkClientKind.ANDROID_TV,
                capabilities = StudyFlowClientProfiles.capabilitiesFor(StudyFlowLinkClientKind.ANDROID_TV),
            ),
            availability = StudyFlowAttentionDeviceAvailability.FOREGROUND,
            deliverySurfaces = listOf(
                StudyFlowAttentionDeliverySurface.FOREGROUND_BANNER,
                StudyFlowAttentionDeliverySurface.LEDGER_ONLY,
            ),
            foregroundActive = true,
            notificationPermissionGranted = false,
            lastSeenAtEpochMillis = nowEpochMillis,
        )

    fun foregroundRoutingContext(nowEpochMillis: Long): StudyFlowAttentionRoutingContext =
        StudyFlowAttentionRoutingContext(
            localDeviceId = localDeviceId,
            foregroundDeviceId = localDeviceId,
            staleAfterMillis = 60_000L,
            allowTvForegroundOwners = true,
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

    fun routeWithPolicy(
        occurrence: StudyFlowAttentionOccurrence,
        devices: List<StudyFlowAttentionDeviceCapability>,
        policy: StudyFlowAttentionFocusPolicy,
        nowEpochMillis: Long,
        ledgerEntry: StudyFlowAttentionLedgerEntry? = null,
    ): StudyFlowAttentionDeliveryRoute = router().routeWithPolicy(
        occurrence = occurrence,
        devices = devices,
        nowEpochMillis = nowEpochMillis,
        focusPolicy = policy,
        context = foregroundRoutingContext(nowEpochMillis),
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

