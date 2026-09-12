package com.mikeyphw.studyflow.reminders

import android.content.Context
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionDeliveryRouter
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionDeliveryRoute
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionFocusPolicy
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionLedgerEntry
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionOccurrence
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionDeliverySurface
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionDeviceAvailability
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionDeviceCapability
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionRoutingContext
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionTopologyProjector
import com.mikeyphw.studyflow.core.link.StudyFlowDeviceId
import com.mikeyphw.studyflow.core.link.StudyFlowLinkClientKind
import com.mikeyphw.studyflow.core.link.StudyFlowLinkPeerDescriptor
import com.mikeyphw.studyflow.core.link.StudyFlowPresenceView
import com.mikeyphw.studyflow.core.link.StudyFlowTrustedPeerRecord

object AndroidStudyFlowAttentionRouterBridge {
    fun localCapability(
        context: Context,
        coordinator: StudyReminderDeliveryCoordinator,
        peer: StudyFlowLinkPeerDescriptor,
        nowEpochMillis: Long,
    ): StudyFlowAttentionDeviceCapability {
        val capability = coordinator.platformCapability()
        return StudyFlowAttentionDeviceCapability(
            peer = peer,
            availability = StudyFlowAttentionDeviceAvailability.ONLINE,
            deliverySurfaces = listOf(
                StudyFlowAttentionDeliverySurface.ANDROID_ALARM,
                StudyFlowAttentionDeliverySurface.SYSTEM_NOTIFICATION,
                StudyFlowAttentionDeliverySurface.WIDGET_OR_BADGE,
            ),
            notificationPermissionGranted = capability.notificationsAllowed,
            primaryUserDevice = true,
            designatedNotifier = true,
            lastSeenAtEpochMillis = nowEpochMillis,
        )
    }

    fun topologyCapabilities(
        context: Context,
        coordinator: StudyReminderDeliveryCoordinator,
        localPeer: StudyFlowLinkPeerDescriptor,
        trustedPeers: List<StudyFlowTrustedPeerRecord>,
        presenceViews: List<StudyFlowPresenceView>,
        nowEpochMillis: Long,
    ): List<StudyFlowAttentionDeviceCapability> {
        val local = localCapability(context, coordinator, localPeer, nowEpochMillis)
        return StudyFlowAttentionTopologyProjector.project(
            localPeer = localPeer,
            localSurfaces = local.deliverySurfaces,
            localAvailability = local.availability,
            localNotificationPermissionGranted = local.notificationPermissionGranted,
            trustedPeers = trustedPeers,
            presenceViews = presenceViews,
            nowEpochMillis = nowEpochMillis,
            localPrimaryUserDevice = true,
            localDesignatedNotifier = true,
        )
    }

    fun routingContext(
        localDeviceId: StudyFlowDeviceId,
        devices: List<StudyFlowAttentionDeviceCapability>,
    ): StudyFlowAttentionRoutingContext = StudyFlowAttentionTopologyProjector.routingContext(
        localDeviceId = localDeviceId,
        devices = devices,
        staleAfterMillis = 10 * 60_000L,
        allowTvForegroundOwners = true,
    )

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
        context = routingContext(localDeviceId, devices),
        ledgerEntry = ledgerEntry,
        localClientKind = StudyFlowLinkClientKind.ANDROID,
    )

    fun isLocalTarget(route: StudyFlowAttentionDeliveryRoute, localDeviceId: StudyFlowDeviceId): Boolean =
        route.deliverable && localDeviceId in route.targetDeviceIds

    fun router(): StudyFlowAttentionDeliveryRouter = StudyFlowAttentionDeliveryRouter()
}
