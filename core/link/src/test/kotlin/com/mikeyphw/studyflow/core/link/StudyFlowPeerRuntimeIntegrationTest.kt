package com.mikeyphw.studyflow.core.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowPeerRuntimeIntegrationTest {
    @Test
    fun twoGenericPeersPairAuthenticateAndConvergeOverRealTcp() {
        var now = 10_000L
        val clock = StudyFlowPeerClock { now++ }
        val android = runtime("device-android", "Android", StudyFlowLinkClientKind.ANDROID, clock)
        val desktop = runtime("device-desktop", "Desktop", StudyFlowLinkClientKind.DESKTOP, clock)
        val androidEndpoint = android.startServer("127.0.0.1", 0)
        val desktopEndpoint = desktop.startServer("127.0.0.1", 0)

        try {
            val offer = desktop.createPairingOffer(listOf(desktopEndpoint.canonicalUri()))
            val trustedDesktop = android.pairingClient.pairFromQr(offer.qrPayload)
            assertEquals(desktop.localIdentity.descriptor.deviceId, trustedDesktop.peer.deviceId)
            assertEquals(
                android.localIdentity.descriptor.deviceId,
                desktop.trustManager.trustedPeers().single().peer.deviceId,
            )

            android.rememberEndpoints(trustedDesktop.peer.deviceId, listOf(desktopEndpoint))
            desktop.rememberEndpoints(android.localIdentity.descriptor.deviceId, listOf(androidEndpoint))
            val workspaceId = StudyFlowWorkspaceId("workspace-direct")
            android.appendLocalMutation(
                workspaceId = workspaceId,
                operationId = StudyFlowOperationId("op-android-1"),
                entityId = StudyFlowEntityId("goal-1"),
                operationType = "goal.completed",
            )
            desktop.appendLocalMutation(
                workspaceId = workspaceId,
                operationId = StudyFlowOperationId("op-desktop-1"),
                entityId = StudyFlowEntityId("note-1"),
                operationType = "writing.updated",
            )

            val result = android.reconcileWith(
                workspaceId = workspaceId,
                peerDeviceId = desktop.localIdentity.descriptor.deviceId,
                endpoint = desktopEndpoint,
            )

            assertEquals(StudyFlowReconnectStatus.COMPLETE, result.status)
            assertEquals(2, android.workspace(workspaceId).journal.operations().size)
            assertEquals(2, desktop.workspace(workspaceId).journal.operations().size)
            assertTrue(android.workspace(workspaceId).outgoingQueue.entries().isEmpty())
        } finally {
            android.close()
            desktop.close()
        }
    }

    @Test
    fun revokedPeerCannotOpenAuthenticatedSyncSession() {
        var now = 20_000L
        val clock = StudyFlowPeerClock { now++ }
        val first = runtime("device-first", "First", StudyFlowLinkClientKind.ANDROID, clock)
        val second = runtime("device-second", "Second", StudyFlowLinkClientKind.DESKTOP, clock)
        val endpoint = second.startServer("127.0.0.1", 0)

        try {
            val offer = second.createPairingOffer(listOf(endpoint.canonicalUri()))
            first.pairingClient.pairFromQr(offer.qrPayload)
            second.revoke(first.localIdentity.descriptor.deviceId)

            val failed = runCatching {
                StudyFlowRemoteSyncPeerSession(
                    deviceId = second.localIdentity.descriptor.deviceId,
                    workspaceId = StudyFlowWorkspaceId("workspace-revoked"),
                    localIdentity = first.localIdentity,
                    trustManager = first.trustManager,
                    endpoint = endpoint,
                    transportRegistry = first.transportRegistry,
                    clock = clock,
                    messageIds = first.messageIds,
                ).close()
            }.exceptionOrNull()

            assertNotNull(failed)
        } finally {
            first.close()
            second.close()
        }
    }

    @Test
    fun persistenceCodecRoundTripsEveryPeerStateFamily() {
        val identity = StudyFlowLocalDeviceIdentity(
            descriptor = StudyFlowLinkPeerDescriptor(
                deviceId = StudyFlowDeviceId("device-persist"),
                displayName = "Persistent peer",
                clientKind = StudyFlowLinkClientKind.DESKTOP,
                capabilities = listOf(StudyFlowLinkCapabilities.WorkspaceSync),
            ),
            createdAtEpochMillis = 1L,
        )
        assertEquals(identity, StudyFlowPeerPersistenceCodec.decodeIdentity(
            StudyFlowPeerPersistenceCodec.encodeIdentity(identity),
        ))

        val endpoint = StudyFlowPeerEndpointRecord(
            deviceId = StudyFlowDeviceId("device-remote"),
            endpoints = listOf(
                StudyFlowTransportEndpoint(StudyFlowTransportScheme.TCP, "192.0.2.20", 4040),
            ),
            updatedAtEpochMillis = 2L,
        )
        val decoded = StudyFlowPeerPersistenceCodec.decodePeerEndpoints(
            StudyFlowPeerPersistenceCodec.encodePeerEndpoints(listOf(endpoint)),
        )
        assertEquals(listOf(endpoint), decoded)
    }

    @Test
    fun lanBeaconCodecCarriesGenericPeerIdentityAndTcpPort() {
        val beacon = StudyFlowLanBeacon(
            peer = StudyFlowLinkPeerDescriptor(
                deviceId = StudyFlowDeviceId("device-lan"),
                displayName = "LAN peer",
                clientKind = StudyFlowLinkClientKind.DESKTOP,
                capabilities = listOf(StudyFlowLinkCapabilities.WorkspaceSync),
            ),
            tcpPort = 45_000,
            emittedAtEpochMillis = 100L,
        )
        assertEquals(beacon, StudyFlowLanBeaconCodec.decode(StudyFlowLanBeaconCodec.encode(beacon)))
    }

    private fun runtime(
        id: String,
        name: String,
        kind: StudyFlowLinkClientKind,
        clock: StudyFlowPeerClock,
    ): StudyFlowPeerRuntime {
        val identity = StudyFlowLocalDeviceIdentity(
            descriptor = StudyFlowLinkPeerDescriptor(
                deviceId = StudyFlowDeviceId(id),
                displayName = name,
                clientKind = kind,
                capabilities = listOf(
                    StudyFlowLinkCapabilities.WorkspaceRead,
                    StudyFlowLinkCapabilities.WorkspaceWrite,
                    StudyFlowLinkCapabilities.WorkspaceSync,
                ),
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
