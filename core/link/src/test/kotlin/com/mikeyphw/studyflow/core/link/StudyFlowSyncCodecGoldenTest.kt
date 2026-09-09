package com.mikeyphw.studyflow.core.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowSyncCodecGoldenTest {
    @Test
    fun syncGoldenMessagesDecodeAndPayloadsRemainTyped() {
        val frontierRequest = golden("sync-frontier-request.json")
        val frontierEnvelope = StudyFlowLinkCodec.decode(frontierRequest)
        assertEquals(StudyFlowSyncMessageNames.Frontier, frontierEnvelope.name)
        val frontierPayload = StudyFlowLinkCodec.decodePayload(
            StudyFlowSyncFrontierRequest.serializer(),
            frontierEnvelope.payload,
        )
        assertEquals(2L, frontierPayload.knownFrontier.sequenceFor(StudyFlowDeviceId("android-tablet")))

        val frontierResponse = StudyFlowLinkCodec.decode(golden("sync-frontier-response.json"))
        val responsePayload = StudyFlowLinkCodec.decodePayload(
            StudyFlowSyncFrontierResponse.serializer(),
            frontierResponse.payload,
        )
        assertEquals(4L, responsePayload.currentFrontier.sequenceFor(StudyFlowDeviceId("android-tablet")))

        val pullEnvelope = StudyFlowLinkCodec.decode(golden("sync-pull-request.json"))
        val pull = StudyFlowLinkCodec.decodePayload(StudyFlowSyncPullRequest.serializer(), pullEnvelope.payload)
        assertEquals(2, pull.maxOperations)

        val operationsEnvelope = StudyFlowLinkCodec.decode(golden("sync-operations-response.json"))
        val batch = StudyFlowLinkCodec.decodePayload(
            StudyFlowSyncOperationsBatch.serializer(),
            operationsEnvelope.payload,
        )
        assertEquals(2, batch.operations.size)
        assertTrue(batch.hasMore)
        assertEquals(4L, batch.throughFrontier.sequenceFor(StudyFlowDeviceId("android-tablet")))
    }

    private fun golden(name: String): String {
        val resource = javaClass.classLoader.getResource("sflp/v1/$name")
        requireNotNull(resource) { "Missing golden resource $name" }
        return resource.readText()
    }
}
