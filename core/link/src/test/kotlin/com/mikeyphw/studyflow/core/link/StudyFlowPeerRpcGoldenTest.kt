package com.mikeyphw.studyflow.core.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class StudyFlowPeerRpcGoldenTest {
    @Test
    fun reconnectRequestGoldenUsesGenericDeviceIdentityWithoutClientRoleAssumptions() {
        val envelope = StudyFlowLinkCodec.decode(golden("peer-reconnect-request.json"))
        assertEquals(StudyFlowPairingMessageNames.ReconnectChallenge, envelope.name)
        val payload = StudyFlowLinkCodec.decodePayload(
            StudyFlowReconnectChallengeRequest.serializer(),
            envelope.payload,
        )
        assertEquals(StudyFlowDeviceId("android-main"), payload.peerDeviceId)
        assertFalse(envelope.name.contains("phone"))
        assertFalse(envelope.name.contains("tv"))
    }

    @Test
    fun syncApplyGoldenCarriesSflp03BatchAcrossTheGenericPeerRpcBoundary() {
        val envelope = StudyFlowLinkCodec.decode(golden("peer-sync-apply-request.json"))
        assertEquals(StudyFlowPeerRpcMessageNames.SyncApply, envelope.name)
        val batch = StudyFlowLinkCodec.decodePayload(
            StudyFlowSyncOperationsBatch.serializer(),
            envelope.payload,
        )
        assertEquals(StudyFlowWorkspaceId("workspace-main"), batch.workspaceId)
        assertEquals(StudyFlowMessageId("batch-peer-1"), batch.batchId)
        assertEquals(0, batch.operations.size)
    }

    private fun golden(name: String): String {
        val resource = javaClass.classLoader.getResource("sflp/v1/$name")
        requireNotNull(resource) { "Missing golden resource $name" }
        return resource.readText()
    }
}
