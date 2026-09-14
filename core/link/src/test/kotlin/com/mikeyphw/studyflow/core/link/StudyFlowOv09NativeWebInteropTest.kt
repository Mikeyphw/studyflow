package com.mikeyphw.studyflow.core.link

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowOv09NativeWebInteropTest {
    @Test
    fun websocketBridgeUsesCanonicalPairingSyncAndRoutedRpc() {
        var now = 90_000L
        val clock = StudyFlowPeerClock { now++ }
        val native = runtime("ov09-native", StudyFlowLinkClientKind.DESKTOP, clock)
        val browserProfile = runtime("ov09-web", StudyFlowLinkClientKind.WEB, clock)
        val endpoint = native.startWebSocketServer("127.0.0.1", 0, "/sflp")
        val workspace = StudyFlowWorkspaceId("workspace-ov09")
        try {
            assertEquals(StudyFlowTransportScheme.WEBSOCKET, endpoint.scheme)
            assertEquals("/sflp", endpoint.path)
            val offer = native.createPairingOffer(listOf(endpoint.canonicalUri()))
            assertTrue(offer.qrPayload.startsWith("studyflow://pair?data="))

            val trusted = browserProfile.pairingClient.pairFromQr(offer.qrPayload)
            assertEquals(native.localIdentity.descriptor.deviceId, trusted.peer.deviceId)
            assertEquals(
                browserProfile.localIdentity.descriptor.deviceId,
                native.trustManager.trustedPeers().single().peer.deviceId,
            )
            browserProfile.rememberEndpoints(native.localIdentity.descriptor.deviceId, listOf(endpoint))

            browserProfile.appendLocalMutation(
                workspaceId = workspace,
                operationId = StudyFlowOperationId("op-ov09-web-1"),
                entityId = StudyFlowEntityId("goal-ov09"),
                operationType = "goal.completed",
                payload = JsonObject(mapOf("source" to JsonPrimitive("web"))),
            )
            val sync = browserProfile.reconcileWith(
                workspaceId = workspace,
                peerDeviceId = native.localIdentity.descriptor.deviceId,
                endpoint = endpoint,
            )
            assertEquals(StudyFlowReconnectStatus.COMPLETE, sync.status)
            assertEquals(1, native.workspace(workspace).journal.operations().size)

            val accepted = browserProfile.sendRoutedRpc(
                workspaceId = workspace,
                peerDeviceId = native.localIdentity.descriptor.deviceId,
                name = StudyFlowPresentationProjectionMessageNames.Control,
                payload = JsonObject(mapOf("projectionId" to JsonPrimitive("projection-ov09"))),
                endpoint = endpoint,
            )
            assertEquals(StudyFlowPresentationProjectionMessageNames.Control, accepted.requestName)
            assertEquals(native.localIdentity.descriptor.deviceId, accepted.receivingDeviceId)
            val delivered = native.routedRpcBus.snapshot().single()
            assertEquals(browserProfile.localIdentity.descriptor.deviceId, delivered.authenticatedPeerDeviceId)
            assertEquals(StudyFlowPresentationProjectionMessageNames.Control, delivered.envelope.name)
            assertEquals("projection-ov09", (delivered.envelope.payload["projectionId"] as JsonPrimitive).content)
        } finally {
            browserProfile.close()
            native.close()
        }
    }

    @Test
    fun routedRegistryCoversEveryPromisedSflp14Through23Family() {
        val names = StudyFlowRoutedRpcContracts.RequestNames
        assertTrue(StudyFlowActivityHandoffMessageNames.Offer in names)
        assertTrue(StudyFlowLiveSessionMessageNames.Cursor in names)
        assertTrue(StudyFlowPresentationProjectionMessageNames.Frame in names)
        assertTrue(StudyFlowControlMessageNames.Command in names)
        assertTrue(StudyFlowControlMessageNames.InputEvent in names)
        assertTrue(StudyFlowNotificationMediaMessageNames.ScheduleChange in names)
        assertTrue(StudyFlowNotificationMediaMessageNames.MaterialChunk in names)
        assertTrue(StudyFlowShareImportWireNames.ShareImportOffer in names)
        assertTrue(StudyFlowShareImportWireNames.ConflictResolve in names)
        assertTrue(StudyFlowAccountBackupMessageNames.AccountHandoffOffer in names)
        assertTrue(StudyFlowAccountBackupMessageNames.BackupRestorePlan in names)
        assertTrue(StudyFlowAccountBackupMessageNames.ReleaseValidationPlan in names)
    }

    private fun runtime(
        id: String,
        kind: StudyFlowLinkClientKind,
        clock: StudyFlowPeerClock,
    ): StudyFlowPeerRuntime = StudyFlowPeerRuntime(
        localIdentity = StudyFlowLocalDeviceIdentity(
            descriptor = StudyFlowLinkPeerDescriptor(
                deviceId = StudyFlowDeviceId(id),
                displayName = id,
                clientKind = kind,
                capabilities = StudyFlowClientProfiles.capabilitiesFor(kind),
            ),
            createdAtEpochMillis = clock.nowEpochMillis(),
        ),
        trustStore = InMemoryStudyFlowTrustStore(),
        journalStore = InMemoryStudyFlowOperationJournalStore(),
        queueStore = InMemoryStudyFlowOutgoingQueueStore(),
        endpointStore = InMemoryStudyFlowPeerEndpointStore(),
        discovery = StudyFlowManualDiscovery(),
        clock = clock,
    )
}
