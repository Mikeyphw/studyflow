package com.mikeyphw.studyflow.tv.attention

import com.mikeyphw.studyflow.core.link.StudyFlowAttentionDeliveryRouter
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

    fun router(): StudyFlowAttentionDeliveryRouter = StudyFlowAttentionDeliveryRouter()
}
