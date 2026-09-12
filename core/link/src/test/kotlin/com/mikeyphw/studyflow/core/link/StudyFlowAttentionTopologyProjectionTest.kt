package com.mikeyphw.studyflow.core.link

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StudyFlowAttentionTopologyProjectionTest {
    private val now = 10_000L
    private val local = StudyFlowLinkPeerDescriptor(
        StudyFlowDeviceId("phone-real-id"), "Phone", StudyFlowLinkClientKind.ANDROID,
        capabilities = StudyFlowClientProfiles.capabilitiesFor(StudyFlowLinkClientKind.ANDROID),
    )

    @Test
    fun `projection preserves canonical ids and includes trusted peer topology`() {
        val desktop = peer("desktop-real-id", StudyFlowLinkClientKind.DESKTOP)
        val tv = peer("tv-real-id", StudyFlowLinkClientKind.ANDROID_TV)
        val projected = StudyFlowAttentionTopologyProjector.project(
            localPeer = local,
            localSurfaces = listOf(StudyFlowAttentionDeliverySurface.ANDROID_ALARM),
            localAvailability = StudyFlowAttentionDeviceAvailability.ONLINE,
            localNotificationPermissionGranted = true,
            trustedPeers = listOf(trusted(desktop), trusted(tv)),
            presenceViews = listOf(presence(tv, StudyFlowPresenceStatus.ONLINE)),
            nowEpochMillis = now,
        )
        assertEquals(listOf("phone-real-id", "desktop-real-id", "tv-real-id"), projected.map { it.deviceId.value })
        assertTrue(projected.single { it.deviceId.value == "tv-real-id" }.foregroundActive)
        assertEquals(
            StudyFlowAttentionDeliverySurface.FOREGROUND_BANNER,
            projected.single { it.deviceId.value == "tv-real-id" }.deliverySurfaces.first(),
        )
    }

    @Test
    fun `tv without fresh presence cannot own attention`() {
        val tv = peer("tv-real-id", StudyFlowLinkClientKind.ANDROID_TV)
        val projected = StudyFlowAttentionTopologyProjector.project(
            localPeer = local,
            localSurfaces = listOf(StudyFlowAttentionDeliverySurface.ANDROID_ALARM),
            localAvailability = StudyFlowAttentionDeviceAvailability.ONLINE,
            localNotificationPermissionGranted = true,
            trustedPeers = listOf(trusted(tv)),
            presenceViews = emptyList(),
            nowEpochMillis = now,
        )
        val candidate = projected.single { it.deviceId == tv.deviceId }
        assertEquals(StudyFlowAttentionDeviceAvailability.OFFLINE, candidate.availability)
        assertFalse(candidate.foregroundActive)
    }

    @Test
    fun `routing context derives phone primary and fresh foreground owner`() {
        val web = peer("web-real-id", StudyFlowLinkClientKind.WEB)
        val devices = StudyFlowAttentionTopologyProjector.project(
            localPeer = local,
            localSurfaces = listOf(StudyFlowAttentionDeliverySurface.ANDROID_ALARM),
            localAvailability = StudyFlowAttentionDeviceAvailability.ONLINE,
            localNotificationPermissionGranted = true,
            trustedPeers = listOf(trusted(web)),
            presenceViews = listOf(presence(web, StudyFlowPresenceStatus.ONLINE)),
            nowEpochMillis = now,
        )
        val context = StudyFlowAttentionTopologyProjector.routingContext(local.deviceId, devices, 180_000L)
        assertEquals(local.deviceId, context.primaryDeviceId)
        assertEquals(web.deviceId, context.foregroundDeviceId)
    }


    @Test
    fun `desktop topology advertises compact surface for ATT08 mini dashboard`() {
        val desktop = peer("desktop-real-id", StudyFlowLinkClientKind.DESKTOP)
        val projected = StudyFlowAttentionTopologyProjector.project(
            localPeer = local,
            localSurfaces = listOf(StudyFlowAttentionDeliverySurface.ANDROID_ALARM),
            localAvailability = StudyFlowAttentionDeviceAvailability.ONLINE,
            localNotificationPermissionGranted = true,
            trustedPeers = listOf(trusted(desktop)),
            presenceViews = emptyList(),
            nowEpochMillis = now,
        )
        val candidate = projected.single { it.deviceId == desktop.deviceId }
        assertTrue(StudyFlowAttentionDeliverySurface.WIDGET_OR_BADGE in candidate.deliverySurfaces)
    }

    @Test
    fun `android without notification permission falls back to compact surface`() {
        val candidate = StudyFlowAttentionDeviceCapability(
            peer = local,
            deliverySurfaces = listOf(
                StudyFlowAttentionDeliverySurface.ANDROID_ALARM,
                StudyFlowAttentionDeliverySurface.SYSTEM_NOTIFICATION,
                StudyFlowAttentionDeliverySurface.WIDGET_OR_BADGE,
            ),
            notificationPermissionGranted = false,
            lastSeenAtEpochMillis = now,
        )
        assertEquals(
            StudyFlowAttentionDeliverySurface.WIDGET_OR_BADGE,
            candidate.bestSurface(now, StudyFlowAttentionRoutingContext(localDeviceId = local.deviceId)),
        )
    }

    @Test
    fun `remote delivery carries ATT07 compact route semantics`() {
        val desktop = peer("desktop-real-id", StudyFlowLinkClientKind.DESKTOP)
        val occurrence = StudyFlowAttentionOccurrence(
            occurrenceId = StudyFlowAttentionOccurrenceId("compact-transport"),
            workspaceId = StudyFlowWorkspaceId("plan-main"),
            category = StudyFlowAttentionCategory.PLANNED_STUDY,
            scheduledAtEpochMillis = now,
        )
        val desktopCapability = StudyFlowAttentionDeviceCapability(
            peer = desktop,
            deliverySurfaces = listOf(StudyFlowAttentionDeliverySurface.WIDGET_OR_BADGE),
            notificationPermissionGranted = false,
            lastSeenAtEpochMillis = now,
        )
        val route = StudyFlowAttentionDeliveryRouter().routeWithPolicy(
            occurrence = occurrence,
            devices = listOf(desktopCapability),
            nowEpochMillis = now,
            focusPolicy = StudyFlowAttentionFocusPolicy(profile = StudyFlowAttentionFocusProfile.TRAVEL_OFFLINE),
            context = StudyFlowAttentionRoutingContext(localDeviceId = local.deviceId),
            localClientKind = StudyFlowLinkClientKind.ANDROID,
        )
        val delivery = StudyFlowAttentionNotificationDeliveryFactory.create(
            occurrence = occurrence,
            sourceDeviceId = local.deviceId,
            targetDeviceId = desktop.deviceId,
            title = "Compact",
            body = "Compact route",
            nowEpochMillis = now,
            deliveryRoute = route,
        )
        assertTrue(route.compactOnly)
        assertEquals(StudyFlowAttentionDeliverySurface.WIDGET_OR_BADGE, route.owner?.surface)
        assertEquals(route, delivery.notification.attentionRoute)
        assertEquals(
            StudyFlowAttentionDeliverySurface.WIDGET_OR_BADGE,
            delivery.notification.attentionRoute?.targetFor(desktop.deviceId)?.surface,
        )
    }

    @Test
    fun `rejected remote owner can reroute to local compact fallback`() {
        val desktop = peer("desktop-real-id", StudyFlowLinkClientKind.DESKTOP)
        val occurrence = StudyFlowAttentionOccurrence(
            occurrenceId = StudyFlowAttentionOccurrenceId("stale-remote-capability"),
            workspaceId = StudyFlowWorkspaceId("plan-main"),
            category = StudyFlowAttentionCategory.PLANNED_STUDY,
            scheduledAtEpochMillis = now,
            deliveryPolicy = StudyFlowAttentionDeliveryPolicy.PRIMARY_DEVICE,
        )
        val localCompact = StudyFlowAttentionDeviceCapability(
            peer = local,
            deliverySurfaces = listOf(
                StudyFlowAttentionDeliverySurface.ANDROID_ALARM,
                StudyFlowAttentionDeliverySurface.SYSTEM_NOTIFICATION,
                StudyFlowAttentionDeliverySurface.WIDGET_OR_BADGE,
            ),
            notificationPermissionGranted = false,
            lastSeenAtEpochMillis = now,
        )
        val staleRemote = StudyFlowAttentionDeviceCapability(
            peer = desktop,
            deliverySurfaces = listOf(StudyFlowAttentionDeliverySurface.SYSTEM_NOTIFICATION),
            notificationPermissionGranted = true,
            lastSeenAtEpochMillis = now,
        )
        val context = StudyFlowAttentionRoutingContext(
            localDeviceId = local.deviceId,
            primaryDeviceId = desktop.deviceId,
        )
        val router = StudyFlowAttentionDeliveryRouter()
        val initial = router.route(occurrence, listOf(localCompact, staleRemote), now, context)
        assertEquals(desktop.deviceId, initial.owner?.deviceId)

        val fallback = router.route(
            occurrence = occurrence,
            devices = listOf(localCompact),
            nowEpochMillis = now,
            context = context,
        )
        assertEquals(local.deviceId, fallback.owner?.deviceId)
        assertEquals(StudyFlowAttentionDeliverySurface.WIDGET_OR_BADGE, fallback.owner?.surface)
    }

    @Test
    fun `notification delivery sequence stays monotonic within the same millisecond`() {
        val occurrence = StudyFlowAttentionOccurrence(
            occurrenceId = StudyFlowAttentionOccurrenceId("same-millisecond"),
            workspaceId = StudyFlowWorkspaceId("plan-main"),
            category = StudyFlowAttentionCategory.PLANNED_STUDY,
            scheduledAtEpochMillis = now,
        )
        val target = StudyFlowDeviceId("desktop-real-id")
        val first = StudyFlowAttentionNotificationDeliveryFactory.create(
            occurrence = occurrence,
            sourceDeviceId = local.deviceId,
            targetDeviceId = target,
            title = "First",
            body = "First reminder",
            nowEpochMillis = now,
        )
        val second = StudyFlowAttentionNotificationDeliveryFactory.create(
            occurrence = occurrence.copy(occurrenceId = StudyFlowAttentionOccurrenceId("same-millisecond-2")),
            sourceDeviceId = local.deviceId,
            targetDeviceId = target,
            title = "Second",
            body = "Second reminder",
            nowEpochMillis = now,
        )
        assertTrue(second.sequence > first.sequence)
    }

    @Test
    fun `canonical attention workspace matches SFLP plan workspace`() {
        assertEquals("plan-abc", StudyFlowAttentionWorkspace.forPlanId("abc").value)
    }

    private fun peer(id: String, kind: StudyFlowLinkClientKind) = StudyFlowLinkPeerDescriptor(
        StudyFlowDeviceId(id), id, kind, capabilities = StudyFlowClientProfiles.capabilitiesFor(kind),
    )

    private fun trusted(peer: StudyFlowLinkPeerDescriptor) = StudyFlowTrustedPeerRecord(
        peer = peer,
        state = StudyFlowTrustState.TRUSTED,
        pairedAtEpochMillis = 1L,
        updatedAtEpochMillis = 2L,
        lastSeenAtEpochMillis = now,
        trustSecret = StudyFlowTrustSecret("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"),
    )

    private fun presence(peer: StudyFlowLinkPeerDescriptor, status: StudyFlowPresenceStatus) = StudyFlowPresenceView(
        workspaceId = StudyFlowWorkspaceId("plan-main"),
        sourceDevice = peer,
        instanceId = StudyFlowPresenceInstanceId("inst-${peer.deviceId.value}"),
        status = status,
        observedAtEpochMillis = now,
    )
}
