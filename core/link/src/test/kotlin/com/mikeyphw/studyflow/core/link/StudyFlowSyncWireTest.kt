package com.mikeyphw.studyflow.core.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StudyFlowSyncWireTest {
    private val workspaceId = StudyFlowWorkspaceId("workspace-main")
    private val deviceId = StudyFlowDeviceId("desktop-main")

    @Test
    fun frontierExchangeUsesTypedRequestAndCorrelatedResponse() {
        val frontier = StudyFlowJournalFrontier(
            listOf(StudyFlowOriginRevision(StudyFlowDeviceId("android-tablet"), 3L)),
        )
        val requestId = StudyFlowMessageId("frontier-request")
        val request = StudyFlowSyncWire.frontierRequest(
            messageId = requestId,
            workspaceId = workspaceId,
            senderDeviceId = deviceId,
            sentAtEpochMillis = 100L,
            knownFrontier = frontier,
        )
        assertEquals(StudyFlowLinkMessageType.REQUEST, request.messageType)
        assertEquals(StudyFlowSyncMessageNames.Frontier, request.name)
        assertNull(request.correlationId)
        assertEquals(
            frontier,
            StudyFlowLinkCodec.decodePayload(StudyFlowSyncFrontierRequest.serializer(), request.payload)
                .knownFrontier,
        )

        val response = StudyFlowSyncWire.frontierResponse(
            messageId = StudyFlowMessageId("frontier-response"),
            requestMessageId = requestId,
            workspaceId = workspaceId,
            senderDeviceId = StudyFlowDeviceId("android-tablet"),
            sentAtEpochMillis = 120L,
            currentFrontier = frontier,
        )
        assertEquals(requestId, response.correlationId)
        assertEquals(StudyFlowLinkMessageType.RESPONSE, response.messageType)
    }

    @Test
    fun pullAndOperationsResponseKeepWorkspaceAndCorrelationIdentity() {
        val requestId = StudyFlowMessageId("pull-request")
        val pull = StudyFlowSyncPullRequest(workspaceId, StudyFlowJournalFrontier(), maxOperations = 25)
        val envelope = StudyFlowSyncWire.pullRequest(
            messageId = requestId,
            workspaceId = workspaceId,
            senderDeviceId = deviceId,
            sentAtEpochMillis = 200L,
            request = pull,
        )
        val decoded = StudyFlowLinkCodec.decodePayload(
            StudyFlowSyncPullRequest.serializer(),
            envelope.payload,
        )
        assertEquals(25, decoded.maxOperations)

        val batch = StudyFlowSyncOperationsBatch(
            batchId = StudyFlowMessageId("batch-1"),
            workspaceId = workspaceId,
            baseFrontier = StudyFlowJournalFrontier(),
            operations = emptyList(),
            throughFrontier = StudyFlowJournalFrontier(),
            hasMore = false,
        )
        val response = StudyFlowSyncWire.operationsResponse(
            messageId = StudyFlowMessageId("ops-response"),
            requestMessageId = requestId,
            workspaceId = workspaceId,
            senderDeviceId = StudyFlowDeviceId("android-tablet"),
            sentAtEpochMillis = 220L,
            batch = batch,
        )
        assertEquals(requestId, response.correlationId)
        assertEquals(StudyFlowSyncMessageNames.Operations, response.name)
    }
}
