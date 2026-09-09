package com.mikeyphw.studyflow.core.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowTopologyTest {
    private val workspaceId = StudyFlowWorkspaceId("workspace-main")
    private val localId = StudyFlowDeviceId("android-main")

    @Test
    fun topologyTracksMultiplePeersAndSelectsDeterministicHealthyRoute() {
        val topology = StudyFlowWorkspaceTopology(workspaceId, localId)
        val desktop = StudyFlowDeviceId("desktop-main")
        val web = StudyFlowDeviceId("web-main")
        topology.upsertPeer(peer(desktop, StudyFlowLinkClientKind.DESKTOP, 100L))
        topology.upsertPeer(peer(web, StudyFlowLinkClientKind.WEB, 110L))

        topology.upsertRoute(
            route(
                target = desktop,
                nextHop = desktop,
                host = "desktop.local",
                kind = StudyFlowRouteKind.DIRECT,
                hopCount = 1,
                health = connected(40L),
            ),
        )
        topology.upsertRoute(
            route(
                target = desktop,
                nextHop = web,
                host = "web.local",
                kind = StudyFlowRouteKind.LEARNED,
                hopCount = 2,
                health = connected(5L),
            ),
        )
        topology.upsertRoute(
            route(
                target = web,
                nextHop = web,
                host = "web.local",
                kind = StudyFlowRouteKind.DIRECT,
                hopCount = 1,
                health = connected(15L),
            ),
        )

        assertEquals(2, topology.peers().size)
        assertEquals(desktop, topology.bestRouteTo(desktop)?.nextHopDeviceId)
        assertEquals(2, topology.forwardingRoutes(emptySet()).size)

        topology.updateNextHopHealth(
            desktop,
            StudyFlowPeerHealth(StudyFlowPeerConnectionState.DISCONNECTED, consecutiveFailures = 3),
            nowEpochMillis = 200L,
        )
        assertEquals(web, topology.bestRouteTo(desktop)?.nextHopDeviceId)
    }

    @Test
    fun routeSelectionExcludesVisitedNextHopsAndDisconnectedRoutes() {
        val topology = StudyFlowWorkspaceTopology(workspaceId, localId)
        val target = StudyFlowDeviceId("tv-main")
        val web = StudyFlowDeviceId("web-main")
        val desktop = StudyFlowDeviceId("desktop-main")
        topology.upsertRoute(route(target, web, "web.local", StudyFlowRouteKind.LEARNED, 2, connected(10L)))
        topology.upsertRoute(route(target, desktop, "desktop.local", StudyFlowRouteKind.LEARNED, 2, connected(20L)))

        val chosen = topology.bestRouteTo(target, excludedNextHops = setOf(web))
        assertEquals(desktop, chosen?.nextHopDeviceId)

        topology.updateNextHopHealth(
            desktop,
            StudyFlowPeerHealth(StudyFlowPeerConnectionState.DISCONNECTED, consecutiveFailures = 3),
            nowEpochMillis = 300L,
        )
        assertNull(topology.bestRouteTo(target, excludedNextHops = setOf(web)))
    }

    @Test
    fun routedEnvelopePreservesOriginalSourceAndRejectsLoopsAndDuplicateMessages() {
        val sourceTopology = StudyFlowWorkspaceTopology(workspaceId, localId)
        val sourceRouter = StudyFlowTopologyRouter(sourceTopology)
        val envelope = event("route-message-1", localId)
        val originated = sourceRouter.originate(envelope)

        val desktopId = StudyFlowDeviceId("desktop-main")
        val desktopRouter = StudyFlowTopologyRouter(StudyFlowWorkspaceTopology(workspaceId, desktopId))
        val accepted = requireNotNull(desktopRouter.acceptIncoming(originated, localId))
        assertEquals(localId, accepted.trace.sourceDeviceId)
        assertEquals(listOf(localId, desktopId), accepted.trace.visitedDeviceIds)
        assertNull(desktopRouter.acceptIncoming(originated, localId))

        val bounced = accepted.copy(trace = accepted.trace.forwardedBy(StudyFlowDeviceId("web-main")))
        assertNull(sourceRouter.acceptIncoming(bounced, StudyFlowDeviceId("web-main")))
    }

    @Test
    fun routerDoesNotForwardBroadcastBackToVisitedPeers() {
        val desktop = StudyFlowDeviceId("desktop-main")
        val web = StudyFlowDeviceId("web-main")
        val topology = StudyFlowWorkspaceTopology(workspaceId, localId)
        topology.upsertRoute(route(desktop, desktop, "desktop.local", StudyFlowRouteKind.DIRECT, 1, connected(10L)))
        topology.upsertRoute(route(web, web, "web.local", StudyFlowRouteKind.DIRECT, 1, connected(20L)))
        val router = StudyFlowTopologyRouter(topology)
        val originated = router.originate(event("route-message-2", localId))
        assertEquals(setOf(desktop, web), router.broadcastRoutes(originated).map { it.nextHopDeviceId }.toSet())

        val fromDesktop = StudyFlowRoutedEnvelope(
            trace = StudyFlowRouteTrace.begin(desktop).forwardedBy(localId),
            envelope = event("route-message-3", desktop),
        )
        val routes = router.broadcastRoutes(fromDesktop)
        assertEquals(listOf(web), routes.map { it.nextHopDeviceId })
    }

    @Test
    fun messageDedupeCacheIsBoundedAndEvictsOldestIds() {
        val dedupe = StudyFlowMessageDeduplicator(capacity = 2)
        val first = StudyFlowMessageId("message-1")
        val second = StudyFlowMessageId("message-2")
        val third = StudyFlowMessageId("message-3")
        assertTrue(dedupe.accept(first))
        assertFalse(dedupe.accept(first))
        assertTrue(dedupe.accept(second))
        assertTrue(dedupe.accept(third))
        assertEquals(2, dedupe.size())
        assertFalse(dedupe.contains(first))
        assertTrue(dedupe.accept(first))
    }

    @Test
    fun healthTrackerDegradesThenDisconnectsAndRecovers() {
        val tracker = StudyFlowPeerHealthTracker()
        tracker.recordSuccess(100L, 12L)
        assertEquals(StudyFlowPeerConnectionState.CONNECTED, tracker.snapshot().state)
        tracker.recordFailure(200L)
        tracker.recordFailure(300L)
        assertEquals(StudyFlowPeerConnectionState.DEGRADED, tracker.snapshot().state)
        tracker.recordFailure(400L)
        assertEquals(StudyFlowPeerConnectionState.DISCONNECTED, tracker.snapshot().state)
        tracker.recordSuccess(500L, 9L)
        assertEquals(0, tracker.snapshot().consecutiveFailures)
        assertEquals(StudyFlowPeerConnectionState.CONNECTED, tracker.snapshot().state)
    }

    private fun peer(
        deviceId: StudyFlowDeviceId,
        kind: StudyFlowLinkClientKind,
        seenAt: Long,
    ) = StudyFlowWorkspacePeer(
        descriptor = StudyFlowLinkPeerDescriptor(
            deviceId = deviceId,
            displayName = deviceId.value,
            clientKind = kind,
            capabilities = listOf(StudyFlowLinkCapabilities.WorkspaceSync),
        ),
        lastSeenAtEpochMillis = seenAt,
    )

    private fun route(
        target: StudyFlowDeviceId,
        nextHop: StudyFlowDeviceId,
        host: String,
        kind: StudyFlowRouteKind,
        hopCount: Int,
        health: StudyFlowPeerHealth,
    ) = StudyFlowPeerRoute(
        targetDeviceId = target,
        nextHopDeviceId = nextHop,
        endpoint = StudyFlowTransportEndpoint(StudyFlowTransportScheme.TCP, host, 4711),
        hopCount = hopCount,
        kind = kind,
        health = health,
        updatedAtEpochMillis = 100L,
    )

    private fun connected(rtt: Long) = StudyFlowPeerHealth(
        state = StudyFlowPeerConnectionState.CONNECTED,
        lastSuccessAtEpochMillis = 100L,
        roundTripMillis = rtt,
    )

    private fun event(messageId: String, sender: StudyFlowDeviceId) = StudyFlowLinkEnvelope(
        messageId = StudyFlowMessageId(messageId),
        workspaceId = workspaceId,
        senderDeviceId = sender,
        messageType = StudyFlowLinkMessageType.EVENT,
        name = "workspace.changed",
        sentAtEpochMillis = 100L,
    )
}
