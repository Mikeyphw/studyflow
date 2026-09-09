package com.mikeyphw.studyflow.core.link

import kotlinx.serialization.Serializable

@Serializable
data class StudyFlowSyncFrontierRequest(
    val knownFrontier: StudyFlowJournalFrontier,
)

@Serializable
data class StudyFlowSyncFrontierResponse(
    val currentFrontier: StudyFlowJournalFrontier,
)

@Serializable
data class StudyFlowSyncPullRequest(
    val workspaceId: StudyFlowWorkspaceId,
    val knownFrontier: StudyFlowJournalFrontier,
    val maxOperations: Int = 100,
) {
    init {
        require(maxOperations in 1..1000) { "maxOperations must be between 1 and 1000" }
    }
}

@Serializable
data class StudyFlowSyncOperationsBatch(
    val batchId: StudyFlowMessageId,
    val workspaceId: StudyFlowWorkspaceId,
    val baseFrontier: StudyFlowJournalFrontier,
    val operations: List<StudyFlowDomainOperation>,
    val throughFrontier: StudyFlowJournalFrontier,
    val hasMore: Boolean,
) {
    init {
        require(operations.map { it.operationId }.distinct().size == operations.size) {
            "Operation batch must not contain duplicate operation IDs"
        }
        require(operations.all { it.workspaceId == workspaceId }) {
            "Every operation in a batch must belong to the batch workspace"
        }
        require(throughFrontier.dominates(baseFrontier)) {
            "Batch through-frontier must dominate its base frontier"
        }
    }
}

object StudyFlowSyncMessageNames {
    const val Frontier = "sync.frontier"
    const val Pull = "sync.pull"
    const val Operations = "sync.operations"
}

object StudyFlowSyncWire {
    fun frontierRequest(
        messageId: StudyFlowMessageId,
        workspaceId: StudyFlowWorkspaceId,
        senderDeviceId: StudyFlowDeviceId,
        sentAtEpochMillis: Long,
        knownFrontier: StudyFlowJournalFrontier,
    ): StudyFlowLinkEnvelope = StudyFlowLinkEnvelope(
        messageId = messageId,
        workspaceId = workspaceId,
        senderDeviceId = senderDeviceId,
        messageType = StudyFlowLinkMessageType.REQUEST,
        name = StudyFlowSyncMessageNames.Frontier,
        sentAtEpochMillis = sentAtEpochMillis,
        payload = StudyFlowLinkCodec.encodePayload(
            StudyFlowSyncFrontierRequest.serializer(),
            StudyFlowSyncFrontierRequest(knownFrontier),
        ),
    )

    fun frontierResponse(
        messageId: StudyFlowMessageId,
        requestMessageId: StudyFlowMessageId,
        workspaceId: StudyFlowWorkspaceId,
        senderDeviceId: StudyFlowDeviceId,
        sentAtEpochMillis: Long,
        currentFrontier: StudyFlowJournalFrontier,
    ): StudyFlowLinkEnvelope = StudyFlowLinkEnvelope(
        messageId = messageId,
        workspaceId = workspaceId,
        senderDeviceId = senderDeviceId,
        messageType = StudyFlowLinkMessageType.RESPONSE,
        name = StudyFlowSyncMessageNames.Frontier,
        sentAtEpochMillis = sentAtEpochMillis,
        correlationId = requestMessageId,
        payload = StudyFlowLinkCodec.encodePayload(
            StudyFlowSyncFrontierResponse.serializer(),
            StudyFlowSyncFrontierResponse(currentFrontier),
        ),
    )

    fun pullRequest(
        messageId: StudyFlowMessageId,
        workspaceId: StudyFlowWorkspaceId,
        senderDeviceId: StudyFlowDeviceId,
        sentAtEpochMillis: Long,
        request: StudyFlowSyncPullRequest,
    ): StudyFlowLinkEnvelope {
        require(request.workspaceId == workspaceId) { "Pull payload belongs to another workspace" }
        return StudyFlowLinkEnvelope(
            messageId = messageId,
            workspaceId = workspaceId,
            senderDeviceId = senderDeviceId,
            messageType = StudyFlowLinkMessageType.REQUEST,
            name = StudyFlowSyncMessageNames.Pull,
            sentAtEpochMillis = sentAtEpochMillis,
            payload = StudyFlowLinkCodec.encodePayload(StudyFlowSyncPullRequest.serializer(), request),
        )
    }

    fun operationsResponse(
        messageId: StudyFlowMessageId,
        requestMessageId: StudyFlowMessageId,
        workspaceId: StudyFlowWorkspaceId,
        senderDeviceId: StudyFlowDeviceId,
        sentAtEpochMillis: Long,
        batch: StudyFlowSyncOperationsBatch,
    ): StudyFlowLinkEnvelope {
        require(batch.workspaceId == workspaceId) { "Operation batch belongs to another workspace" }
        return StudyFlowLinkEnvelope(
            messageId = messageId,
            workspaceId = workspaceId,
            senderDeviceId = senderDeviceId,
            messageType = StudyFlowLinkMessageType.RESPONSE,
            name = StudyFlowSyncMessageNames.Operations,
            sentAtEpochMillis = sentAtEpochMillis,
            correlationId = requestMessageId,
            payload = StudyFlowLinkCodec.encodePayload(StudyFlowSyncOperationsBatch.serializer(), batch),
        )
    }
}
