package com.mikeyphw.studyflow.core.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowPresenceRpcIntegrationTest {
    @Test
    fun authenticatedReconnectExchangesPresenceAfterDurableReconciliation() {
        var now = 50_000L
        val clock = StudyFlowPeerClock { now++ }
        val desktop = runtime("device-presence-desktop", StudyFlowLinkClientKind.DESKTOP, clock)
        val phone = runtime("device-presence-phone", StudyFlowLinkClientKind.ANDROID, clock)
        val desktopEndpoint = desktop.startServer("127.0.0.1", 0)
        val phoneEndpoint = phone.startServer("127.0.0.1", 0)
        val workspace = StudyFlowWorkspaceId("workspace-presence-rpc")

        try {
            pair(phone, desktop, desktopEndpoint)
            desktop.rememberEndpoints(phone.localIdentity.descriptor.deviceId, listOf(phoneEndpoint))
            phone.rememberEndpoints(desktop.localIdentity.descriptor.deviceId, listOf(desktopEndpoint))

            val hint = StudyFlowPresenceActivityHint(
                activityId = StudyFlowActivityId("activity-review-1"),
                kind = StudyFlowActivityKind.REVIEW_ITEM,
                entityId = StudyFlowEntityId("review-1"),
                positionToken = "card-3",
                continuityMode = StudyFlowActivityContinuityMode.SNAPSHOT,
                requiredCapabilities = listOf(StudyFlowLinkCapabilities.ReviewExecute),
            )
            phone.updatePresence(workspace, StudyFlowPresenceAvailability.ONLINE, hint)
            desktop.updatePresence(workspace, StudyFlowPresenceAvailability.AWAY)
            phone.appendLocalMutation(
                workspaceId = workspace,
                operationId = StudyFlowOperationId("op-presence-sync"),
                entityId = StudyFlowEntityId("goal-presence"),
                operationType = "goal.completed",
            )

            val result = phone.reconcileWith(workspace, desktop.localIdentity.descriptor.deviceId, desktopEndpoint)
            assertEquals(StudyFlowReconnectStatus.COMPLETE, result.status)
            assertEquals(1, desktop.workspace(workspace).journal.operations().size)

            val phoneViews = phone.presenceViews(workspace)
            assertTrue(
                phoneViews.any {
                    it.sourceDevice.deviceId == desktop.localIdentity.descriptor.deviceId &&
                        it.status == StudyFlowPresenceStatus.AWAY
                },
            )
            val desktopViews = desktop.presenceViews(workspace)
            val phoneView = desktopViews.firstOrNull {
                it.sourceDevice.deviceId == phone.localIdentity.descriptor.deviceId
            }
            assertNotNull(phoneView)
            assertEquals(StudyFlowActivityId("activity-review-1"), requireNotNull(phoneView).activityHint?.activityId)
        } finally {
            phone.close()
            desktop.close()
        }
    }

    @Test
    fun threePeerGossipRelaysTrustedPresenceWithoutEchoLoop() {
        var now = 80_000L
        val clock = StudyFlowPeerClock { now++ }
        val android = runtime("device-gossip-android", StudyFlowLinkClientKind.ANDROID, clock)
        val desktop = runtime("device-gossip-desktop", StudyFlowLinkClientKind.DESKTOP, clock)
        val web = runtime("device-gossip-web", StudyFlowLinkClientKind.WEB, clock)
        val androidEndpoint = android.startServer("127.0.0.1", 0)
        val desktopEndpoint = desktop.startServer("127.0.0.1", 0)
        val webEndpoint = web.startServer("127.0.0.1", 0)
        val workspace = StudyFlowWorkspaceId("workspace-presence-gossip")

        try {
            pair(android, desktop, desktopEndpoint)
            pair(desktop, web, webEndpoint)
            pair(android, web, webEndpoint)
            android.rememberEndpoints(desktop.localIdentity.descriptor.deviceId, listOf(desktopEndpoint))
            desktop.rememberEndpoints(android.localIdentity.descriptor.deviceId, listOf(androidEndpoint))
            desktop.rememberEndpoints(web.localIdentity.descriptor.deviceId, listOf(webEndpoint))
            web.rememberEndpoints(desktop.localIdentity.descriptor.deviceId, listOf(desktopEndpoint))

            web.updatePresence(workspace, StudyFlowPresenceAvailability.ONLINE)
            desktop.reconcileWith(workspace, web.localIdentity.descriptor.deviceId, webEndpoint)
            assertTrue(
                desktop.presenceViews(workspace).any {
                    it.sourceDevice.deviceId == web.localIdentity.descriptor.deviceId
                },
            )

            android.reconcileWith(workspace, desktop.localIdentity.descriptor.deviceId, desktopEndpoint)
            val webAtAndroid = android.presenceViews(workspace).filter {
                it.sourceDevice.deviceId == web.localIdentity.descriptor.deviceId
            }
            assertEquals(1, webAtAndroid.size)

            desktop.reconcileWith(workspace, android.localIdentity.descriptor.deviceId, androidEndpoint)
            assertEquals(
                1,
                desktop.presenceViews(workspace).count {
                    it.sourceDevice.deviceId == web.localIdentity.descriptor.deviceId
                },
            )
        } finally {
            android.close()
            desktop.close()
            web.close()
        }
    }

    private fun pair(
        client: StudyFlowPeerRuntime,
        host: StudyFlowPeerRuntime,
        hostEndpoint: StudyFlowTransportEndpoint,
    ) {
        val offer = host.createPairingOffer(listOf(hostEndpoint.canonicalUri()))
        client.pairingClient.pairFromQr(offer.qrPayload)
    }

    private fun runtime(
        id: String,
        kind: StudyFlowLinkClientKind,
        clock: StudyFlowPeerClock,
    ): StudyFlowPeerRuntime {
        val identity = StudyFlowLocalDeviceIdentity(
            descriptor = StudyFlowLinkPeerDescriptor(
                deviceId = StudyFlowDeviceId(id),
                displayName = id,
                clientKind = kind,
                capabilities = StudyFlowClientProfiles.capabilitiesFor(kind),
            ),
            createdAtEpochMillis = clock.nowEpochMillis(),
        )
        return StudyFlowPeerRuntime(
            localIdentity = identity,
            trustStore = InMemoryStudyFlowTrustStore(),
            journalStore = InMemoryStudyFlowOperationJournalStore(),
            queueStore = InMemoryStudyFlowOutgoingQueueStore(),
            endpointStore = InMemoryStudyFlowPeerEndpointStore(),
            discovery = StudyFlowManualDiscovery(),
            clock = clock,
        )
    }
}
