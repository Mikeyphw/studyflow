package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.link.StudyFlowAttentionDeliveryRouter
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionDeliveryRoute
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionFocusPolicy
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionLedgerEntry
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionOccurrence
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionDeliverySurface
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionDeviceAvailability
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionDeviceCapability
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionTopologyProjector
import com.mikeyphw.studyflow.core.link.StudyFlowDeviceId
import com.mikeyphw.studyflow.core.link.StudyFlowLinkClientKind
import com.mikeyphw.studyflow.core.link.StudyFlowClientProfiles
import com.mikeyphw.studyflow.core.link.StudyFlowLinkPeerDescriptor
import com.mikeyphw.studyflow.core.link.StudyFlowPresenceView
import com.mikeyphw.studyflow.core.link.StudyFlowTrustedPeerRecord

object DesktopAttentionDeliveryRouterBridge {
    fun standalonePeer(): StudyFlowLinkPeerDescriptor = StudyFlowLinkPeerDescriptor(
        deviceId = StudyFlowDeviceId("desktop-standalone"),
        displayName = "StudyFlow Desktop",
        clientKind = StudyFlowLinkClientKind.DESKTOP,
        capabilities = StudyFlowClientProfiles.capabilitiesFor(StudyFlowLinkClientKind.DESKTOP),
    )

    fun topologyCapabilities(
        localPeer: StudyFlowLinkPeerDescriptor,
        nativeNotificationsAvailable: Boolean,
        trayAvailable: Boolean,
        trustedPeers: List<StudyFlowTrustedPeerRecord>,
        presenceViews: List<StudyFlowPresenceView>,
        nowEpochMillis: Long,
    ): List<StudyFlowAttentionDeviceCapability> {
        val localSurfaces = buildList {
            if (nativeNotificationsAvailable) add(StudyFlowAttentionDeliverySurface.SYSTEM_NOTIFICATION)
            if (trayAvailable) {
                add(StudyFlowAttentionDeliverySurface.DESKTOP_TRAY)
                add(StudyFlowAttentionDeliverySurface.WIDGET_OR_BADGE)
            }
            if (isEmpty()) add(StudyFlowAttentionDeliverySurface.LEDGER_ONLY)
        }
        return StudyFlowAttentionTopologyProjector.project(
            localPeer = localPeer,
            localSurfaces = localSurfaces,
            localAvailability = StudyFlowAttentionDeviceAvailability.ONLINE,
            localNotificationPermissionGranted = nativeNotificationsAvailable,
            trustedPeers = trustedPeers,
            presenceViews = presenceViews,
            nowEpochMillis = nowEpochMillis,
            localPrimaryUserDevice = false,
            localDesignatedNotifier = nativeNotificationsAvailable || trayAvailable,
        )
    }

    fun routeWithPolicy(
        occurrence: StudyFlowAttentionOccurrence,
        devices: List<StudyFlowAttentionDeviceCapability>,
        localDeviceId: StudyFlowDeviceId,
        focusPolicy: StudyFlowAttentionFocusPolicy,
        nowEpochMillis: Long,
        ledgerEntry: StudyFlowAttentionLedgerEntry? = null,
    ): StudyFlowAttentionDeliveryRoute = router().routeWithPolicy(
        occurrence = occurrence,
        devices = devices,
        nowEpochMillis = nowEpochMillis,
        focusPolicy = focusPolicy,
        context = StudyFlowAttentionTopologyProjector.routingContext(
            localDeviceId = localDeviceId,
            devices = devices,
            staleAfterMillis = 5 * 60_000L,
        ),
        ledgerEntry = ledgerEntry,
        localClientKind = StudyFlowLinkClientKind.DESKTOP,
    )

    fun isLocalTarget(route: StudyFlowAttentionDeliveryRoute, localDeviceId: StudyFlowDeviceId): Boolean =
        route.deliverable && localDeviceId in route.targetDeviceIds

    fun router(): StudyFlowAttentionDeliveryRouter = StudyFlowAttentionDeliveryRouter()
}
