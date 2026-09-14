package com.mikeyphw.studyflow.core.link

import java.io.Closeable
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.serialization.Serializable

private val StudyFlowLinkSystemWorkspaceId = StudyFlowWorkspaceId("link-system-v1")

@Serializable
data class StudyFlowReconnectChallengeRequest(
    val peerDeviceId: StudyFlowDeviceId,
)

@Serializable
data class StudyFlowReconnectAccepted(
    val peerDeviceId: StudyFlowDeviceId,
)

@Serializable
data class StudyFlowPairingRejectedPayload(
    val reason: StudyFlowPairingRejection,
)

object StudyFlowPeerRpcMessageNames {
    const val SyncApply = "sync.apply"
    const val ReconnectAccepted = "link.reconnect_accepted"
    const val RoutedAccepted = "link.rpc_accepted"
}

fun interface StudyFlowPeerClock {
    fun nowEpochMillis(): Long
}

class StudyFlowUuidMessageIdFactory : StudyFlowSyncMessageIdFactory {
    override fun next(prefix: String): StudyFlowMessageId =
        StudyFlowMessageId("$prefix-${UUID.randomUUID()}")
}

interface StudyFlowWorkspaceRuntimeProvider {
    fun workspace(workspaceId: StudyFlowWorkspaceId): StudyFlowWorkspaceRuntime
}

class StudyFlowWorkspaceRuntime(
    val workspaceId: StudyFlowWorkspaceId,
    val localDeviceId: StudyFlowDeviceId,
    journalStore: StudyFlowOperationJournalStore,
    queueStore: StudyFlowOutgoingQueueStore,
    messageIds: StudyFlowSyncMessageIdFactory,
) {
    val journal = StudyFlowOperationJournal(workspaceId, localDeviceId, journalStore)
    val reconciliation = StudyFlowReconciliationEngine(journal)
    val outgoingQueue = StudyFlowOutgoingOperationQueue(workspaceId, queueStore)
    val reconnect = StudyFlowReconnectionCoordinator(
        localDeviceId = localDeviceId,
        localEngine = reconciliation,
        outgoingQueue = outgoingQueue,
        messageIds = messageIds,
    )

    fun localSession(): StudyFlowSyncPeerSession = LocalStudyFlowSyncPeerSession(
        deviceId = localDeviceId,
        engine = reconciliation,
        workspaceId = workspaceId,
    )
}

class StudyFlowPeerServer(
    private val localIdentity: StudyFlowLocalDeviceIdentity,
    private val trustManager: StudyFlowTrustManager,
    private val pairingAuthority: StudyFlowPairingAuthority,
    private val workspaces: StudyFlowWorkspaceRuntimeProvider,
    private val presenceRegistry: StudyFlowPresenceRegistry,
    private val presencePublisher: StudyFlowLocalPresencePublisher,
    private val clock: StudyFlowPeerClock,
    private val messageIds: StudyFlowSyncMessageIdFactory,
    private val workspaceMembership: StudyFlowWorkspaceMembershipManager? = null,
    private val routedRpcBus: StudyFlowRoutedRpcBus = StudyFlowRoutedRpcBus(),
    private val notificationDeliveryHandler:
        (StudyFlowNotificationDelivery) -> StudyFlowNotificationDeliveryAck? = { null },
    private val listeningTransport: StudyFlowListeningTransport = StudyFlowTcpTransport(),
    private val executor: ExecutorService = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "studyflow-sflp-peer").apply { isDaemon = true }
    },
) : Closeable {
    private val running = AtomicBoolean(false)
    private var listener: StudyFlowConnectionListener? = null

    val boundEndpoint: StudyFlowTransportEndpoint?
        get() = listener?.boundEndpoint

    fun start(endpoint: StudyFlowTransportEndpoint): StudyFlowTransportEndpoint {
        check(running.compareAndSet(false, true)) { "SFLP peer server is already running" }
        val created = listeningTransport.listen(endpoint)
        listener = created
        executor.execute {
            while (running.get() && created.isOpen) {
                val connection = created.accept() ?: continue
                executor.execute { handleConnection(connection) }
            }
        }
        return created.boundEndpoint
    }

    private fun handleConnection(raw: StudyFlowConnection) {
        StudyFlowLinkConnection(raw).use { link ->
            var authenticatedPeer: StudyFlowDeviceId? = null
            while (running.get() && link.isOpen) {
                val request = runCatching { link.receive() }.getOrNull() ?: return
                if (!StudyFlowLinkProtocol.CurrentSupport.contains(request.protocol)) {
                    sendError(
                        link,
                        request,
                        StudyFlowLinkErrorCodes.IncompatibleProtocol,
                        "Unsupported SFLP protocol ${request.protocol.major}.${request.protocol.minor}",
                        false,
                    )
                    continue
                }
                if (request.messageType != StudyFlowLinkMessageType.REQUEST) {
                    sendError(link, request, StudyFlowLinkErrorCodes.InvalidMessage, "Only SFLP requests are accepted", false)
                    continue
                }
                when (request.name) {
                    StudyFlowPairingMessageNames.Pair -> handlePairing(link, request)
                    StudyFlowPairingMessageNames.ReconnectChallenge -> handleChallenge(link, request)
                    StudyFlowPairingMessageNames.ReconnectProof -> {
                        authenticatedPeer = handleProof(link, request)
                    }
                    StudyFlowSyncMessageNames.Frontier,
                    StudyFlowSyncMessageNames.Pull,
                    StudyFlowPeerRpcMessageNames.SyncApply,
                    -> handleSync(link, request, authenticatedPeer)
                    StudyFlowPresenceMessageNames.Exchange -> handlePresence(link, request, authenticatedPeer)
                    StudyFlowNotificationMediaMessageNames.NotificationDeliver ->
                        handleNotificationDelivery(link, request, authenticatedPeer)
                    else -> if (StudyFlowRoutedRpcContracts.supports(request.name)) {
                        handleRoutedRpc(link, request, authenticatedPeer)
                    } else {
                        sendError(link, request, "link.unsupported_request", "Unsupported SFLP request", false)
                    }
                }
            }
        }
    }

    private fun handlePairing(link: StudyFlowLinkConnection, request: StudyFlowLinkEnvelope) {
        if (request.workspaceId != StudyFlowLinkSystemWorkspaceId) {
            sendError(link, request, "link.workspace_mismatch", "Pairing must use the link-system workspace", false)
            return
        }
        val pairing = runCatching {
            StudyFlowLinkCodec.decodePayload(StudyFlowPairingRequest.serializer(), request.payload)
        }.getOrElse {
            sendError(link, request, "link.invalid_pairing", "Invalid pairing request", false)
            return
        }
        if (pairing.peer.deviceId != request.senderDeviceId) {
            sendError(link, request, "link.identity_mismatch", "Pairing peer does not match envelope sender", false)
            return
        }
        if (StudyFlowLinkProtocol.negotiate(localIdentity.descriptor.protocolSupport, pairing.peer.protocolSupport) == null) {
            sendError(link, request, StudyFlowLinkErrorCodes.IncompatibleProtocol, "Pairing peer has no compatible protocol", false)
            return
        }
        when (val result = pairingAuthority.accept(pairing, clock.nowEpochMillis())) {
            is StudyFlowPairingResult.Accepted -> link.send(
                response(
                    request = request,
                    name = StudyFlowPairingMessageNames.Grant,
                    payload = StudyFlowLinkCodec.encodePayload(StudyFlowPairingGrant.serializer(), result.grant),
                ),
            )
            is StudyFlowPairingResult.Rejected -> link.send(
                response(
                    request = request,
                    name = StudyFlowPairingMessageNames.Grant,
                    payload = StudyFlowLinkCodec.encodePayload(
                        StudyFlowPairingRejectedPayload.serializer(),
                        StudyFlowPairingRejectedPayload(result.reason),
                    ),
                ),
            )
        }
    }

    private fun handleChallenge(link: StudyFlowLinkConnection, request: StudyFlowLinkEnvelope) {
        val challengeRequest = runCatching {
            StudyFlowLinkCodec.decodePayload(StudyFlowReconnectChallengeRequest.serializer(), request.payload)
        }.getOrElse {
            sendError(link, request, "link.invalid_reconnect", "Invalid reconnect request", false)
            return
        }
        if (challengeRequest.peerDeviceId != request.senderDeviceId) {
            sendError(link, request, "link.identity_mismatch", "Reconnect identity mismatch", false)
            return
        }
        val challenge = runCatching {
            trustManager.issueReconnectChallenge(request.senderDeviceId, clock.nowEpochMillis())
        }.getOrElse {
            sendError(link, request, "link.peer_not_trusted", "Peer is not trusted", false)
            return
        }
        link.send(
            response(
                request = request,
                name = StudyFlowPairingMessageNames.ReconnectChallenge,
                payload = StudyFlowLinkCodec.encodePayload(StudyFlowReconnectChallenge.serializer(), challenge),
            ),
        )
    }

    private fun handleProof(
        link: StudyFlowLinkConnection,
        request: StudyFlowLinkEnvelope,
    ): StudyFlowDeviceId? {
        val proof = runCatching {
            StudyFlowLinkCodec.decodePayload(StudyFlowReconnectProof.serializer(), request.payload)
        }.getOrElse {
            sendError(link, request, "link.invalid_reconnect_proof", "Invalid reconnect proof", false)
            return null
        }
        if (proof.proverDeviceId != request.senderDeviceId ||
            !trustManager.verifyReconnectProof(proof, clock.nowEpochMillis())
        ) {
            sendError(link, request, "link.authentication_failed", "Reconnect authentication failed", false)
            return null
        }
        link.send(
            response(
                request = request,
                name = StudyFlowPeerRpcMessageNames.ReconnectAccepted,
                payload = StudyFlowLinkCodec.encodePayload(
                    StudyFlowReconnectAccepted.serializer(),
                    StudyFlowReconnectAccepted(proof.proverDeviceId),
                ),
            ),
        )
        return proof.proverDeviceId
    }

    private fun handleSync(
        link: StudyFlowLinkConnection,
        request: StudyFlowLinkEnvelope,
        authenticatedPeer: StudyFlowDeviceId?,
    ) {
        if (authenticatedPeer == null || authenticatedPeer != request.senderDeviceId) {
            sendError(link, request, "link.authentication_required", "Trusted reconnect is required", false)
            return
        }
        val authenticatedRecord = trustManager.trustedPeers().firstOrNull { it.peer.deviceId == authenticatedPeer }
        if (authenticatedRecord == null) {
            sendError(link, request, "link.peer_revoked", "Peer trust was revoked", false)
            return
        }
        val required = when (request.name) {
            StudyFlowPeerRpcMessageNames.SyncApply -> setOf(StudyFlowLinkCapabilities.WorkspaceSync, StudyFlowLinkCapabilities.WorkspaceWrite)
            StudyFlowSyncMessageNames.Frontier, StudyFlowSyncMessageNames.Pull -> setOf(StudyFlowLinkCapabilities.WorkspaceSync, StudyFlowLinkCapabilities.WorkspaceRead)
            else -> emptySet()
        }
        if (!authenticatedRecord.peer.capabilities.containsAll(required)) {
            sendError(link, request, StudyFlowLinkErrorCodes.UnsupportedCapability, "Peer did not advertise required sync capability", false)
            return
        }
        val membershipAllowed = when (request.name) {
            StudyFlowPeerRpcMessageNames.SyncApply -> workspaceMembership?.canWrite(request.workspaceId, authenticatedPeer) != false
            else -> workspaceMembership?.canRead(request.workspaceId, authenticatedPeer) != false
        }
        if (!membershipAllowed) {
            sendError(link, request, "link.workspace_access_denied", "Peer lacks workspace membership for this sync operation", false)
            return
        }
        val workspace = workspaces.workspace(request.workspaceId)
        when (request.name) {
            StudyFlowSyncMessageNames.Frontier -> link.send(
                StudyFlowSyncWire.frontierResponse(
                    messageId = messageIds.next("frontier-response"),
                    requestMessageId = request.messageId,
                    workspaceId = request.workspaceId,
                    senderDeviceId = localIdentity.descriptor.deviceId,
                    sentAtEpochMillis = clock.nowEpochMillis(),
                    currentFrontier = workspace.reconciliation.currentFrontier(),
                ),
            )
            StudyFlowSyncMessageNames.Pull -> {
                val pull = runCatching {
                    StudyFlowLinkCodec.decodePayload(StudyFlowSyncPullRequest.serializer(), request.payload)
                }.getOrElse {
                    sendError(link, request, "link.invalid_sync_pull", "Invalid sync pull request", false)
                    return
                }
                val batch = workspace.reconciliation.nextBatch(pull, messageIds.next("operations-batch"))
                link.send(
                    StudyFlowSyncWire.operationsResponse(
                        messageId = messageIds.next("operations-response"),
                        requestMessageId = request.messageId,
                        workspaceId = request.workspaceId,
                        senderDeviceId = localIdentity.descriptor.deviceId,
                        sentAtEpochMillis = clock.nowEpochMillis(),
                        batch = batch,
                    ),
                )
            }
            StudyFlowPeerRpcMessageNames.SyncApply -> {
                val batch = runCatching {
                    StudyFlowLinkCodec.decodePayload(StudyFlowSyncOperationsBatch.serializer(), request.payload)
                }.getOrElse {
                    sendError(link, request, "link.invalid_sync_batch", "Invalid sync operation batch", false)
                    return
                }
                val result = runCatching { workspace.reconciliation.applyBatch(batch) }.getOrElse {
                    sendError(link, request, "link.sync_apply_failed", it.message ?: "Sync apply failed", true)
                    return
                }
                link.send(
                    response(
                        request = request,
                        name = StudyFlowPeerRpcMessageNames.SyncApply,
                        payload = StudyFlowLinkCodec.encodePayload(StudyFlowSyncApplyResult.serializer(), result),
                    ),
                )
            }
        }
    }

    private fun handlePresence(
        link: StudyFlowLinkConnection,
        request: StudyFlowLinkEnvelope,
        authenticatedPeer: StudyFlowDeviceId?,
    ) {
        if (authenticatedPeer == null || authenticatedPeer != request.senderDeviceId) {
            sendError(link, request, "link.authentication_required", "Trusted reconnect is required", false)
            return
        }
        val trusted = trustManager.trustedPeers()
        val authenticatedRecord = trusted.firstOrNull { it.peer.deviceId == authenticatedPeer }
        if (authenticatedRecord == null) {
            sendError(link, request, "link.peer_revoked", "Peer trust was revoked", false)
            return
        }
        val exchange = runCatching {
            StudyFlowLinkCodec.decodePayload(StudyFlowPresenceExchangeRequest.serializer(), request.payload)
        }.getOrElse {
            sendError(link, request, "link.invalid_presence_exchange", "Invalid presence exchange", false)
            return
        }
        val trustedSourceIds = trusted.mapTo(linkedSetOf()) { it.peer.deviceId }
        exchange.records.asSequence()
            .filter { it.workspaceId == request.workspaceId }
            .forEach { record ->
                presenceRegistry.ingest(
                    record = record,
                    authenticatedPeerDeviceId = authenticatedPeer,
                    trustedSourceDeviceIds = trustedSourceIds,
                    nowEpochMillis = clock.nowEpochMillis(),
                )
            }
        presencePublisher.ensureOnline(request.workspaceId)
        val response = StudyFlowPresenceExchangeResponse(
            presenceRegistry.recordsForPeer(
                workspaceId = request.workspaceId,
                peer = authenticatedRecord.peer,
                nowEpochMillis = clock.nowEpochMillis(),
            ),
        )
        link.send(
            response(
                request = request,
                name = StudyFlowPresenceMessageNames.Exchange,
                payload = StudyFlowLinkCodec.encodePayload(StudyFlowPresenceExchangeResponse.serializer(), response),
            ),
        )
    }

    private fun handleNotificationDelivery(
        link: StudyFlowLinkConnection,
        request: StudyFlowLinkEnvelope,
        authenticatedPeer: StudyFlowDeviceId?,
    ) {
        if (authenticatedPeer == null || authenticatedPeer != request.senderDeviceId) {
            sendError(link, request, "link.authentication_required", "Trusted reconnect is required", false)
            return
        }
        if (trustManager.trustedPeers().none { it.peer.deviceId == authenticatedPeer }) {
            sendError(link, request, "link.peer_revoked", "Peer trust was revoked", false)
            return
        }
        val delivery = runCatching {
            StudyFlowLinkCodec.decodePayload(StudyFlowNotificationDelivery.serializer(), request.payload)
        }.getOrElse {
            sendError(link, request, "link.invalid_notification_delivery", "Invalid notification delivery", false)
            return
        }
        if (delivery.sourceDeviceId != authenticatedPeer || delivery.sourceDeviceId != request.senderDeviceId) {
            sendError(
                link,
                request,
                "link.identity_mismatch",
                "Notification source does not match authenticated peer",
                false,
            )
            return
        }
        if (delivery.targetDeviceId != localIdentity.descriptor.deviceId) {
            sendError(link, request, "link.wrong_target", "Notification targets another device", false)
            return
        }
        if (delivery.notification.route.workspaceId != request.workspaceId) {
            sendError(link, request, "link.workspace_mismatch", "Notification route workspace mismatch", false)
            return
        }
        if (!delivery.notification.route.accepts(localIdentity.descriptor)) {
            sendError(link, request, "link.route_rejected", "Notification route does not accept this target", false)
            return
        }
        val ack = notificationDeliveryHandler(delivery) ?: StudyFlowNotificationDeliveryAck(
            messageId = StudyFlowMessageId("ack-${delivery.messageId.value}"),
            notificationId = delivery.notification.notificationId,
            targetDeviceId = localIdentity.descriptor.deviceId,
            acknowledgedSequence = delivery.sequence,
            status = StudyFlowNotificationDeliveryStatus.REJECTED,
            reason = "notification_handler_unavailable",
            acknowledgedAtEpochMillis = clock.nowEpochMillis(),
        )
        link.send(
            response(
                request = request,
                name = StudyFlowNotificationMediaMessageNames.NotificationAck,
                payload = StudyFlowLinkCodec.encodePayload(StudyFlowNotificationDeliveryAck.serializer(), ack),
            ),
        )
    }

    private fun handleRoutedRpc(
        link: StudyFlowLinkConnection,
        request: StudyFlowLinkEnvelope,
        authenticatedPeer: StudyFlowDeviceId?,
    ) {
        if (authenticatedPeer == null || authenticatedPeer != request.senderDeviceId) {
            sendError(link, request, "link.authentication_required", "Trusted reconnect is required", false)
            return
        }
        val authenticatedRecord = trustManager.trustedPeers().firstOrNull { it.peer.deviceId == authenticatedPeer }
        if (authenticatedRecord == null) {
            sendError(link, request, "link.peer_revoked", "Peer trust was revoked", false)
            return
        }
        val requiredCapabilities = StudyFlowRoutedRpcContracts.requiredSenderCapabilities(request.name)
        if (!authenticatedRecord.peer.capabilities.containsAll(requiredCapabilities)) {
            sendError(
                link,
                request,
                StudyFlowLinkErrorCodes.UnsupportedCapability,
                "Peer did not advertise required routed-RPC capability",
                false,
            )
            return
        }
        if (workspaceMembership?.canRead(request.workspaceId, authenticatedPeer) == false) {
            sendError(link, request, "link.workspace_access_denied", "Peer is not a member of this workspace", false)
            return
        }
        routedRpcBus.publish(
            StudyFlowRoutedRpcMessage(
                authenticatedPeerDeviceId = authenticatedPeer,
                envelope = request,
                receivedAtEpochMillis = clock.nowEpochMillis(),
            ),
        )
        link.send(
            response(
                request = request,
                name = StudyFlowPeerRpcMessageNames.RoutedAccepted,
                payload = StudyFlowRoutedRpcAccepted(
                    requestMessageId = request.messageId,
                    requestName = request.name,
                    receivingDeviceId = localIdentity.descriptor.deviceId,
                    acceptedAtEpochMillis = clock.nowEpochMillis(),
                ).toPayload(),
            ),
        )
    }

    private fun response(
        request: StudyFlowLinkEnvelope,
        name: String,
        payload: kotlinx.serialization.json.JsonObject,
    ): StudyFlowLinkEnvelope = StudyFlowLinkEnvelope(
        protocol = request.protocol,
        messageId = messageIds.next("response"),
        workspaceId = request.workspaceId,
        senderDeviceId = localIdentity.descriptor.deviceId,
        messageType = StudyFlowLinkMessageType.RESPONSE,
        name = name,
        sentAtEpochMillis = clock.nowEpochMillis(),
        correlationId = request.messageId,
        payload = payload,
    )

    private fun sendError(
        link: StudyFlowLinkConnection,
        request: StudyFlowLinkEnvelope,
        code: String,
        message: String,
        retryable: Boolean,
    ) {
        link.send(
            StudyFlowLinkEnvelope(
                protocol = if (StudyFlowLinkProtocol.CurrentSupport.contains(request.protocol)) request.protocol else StudyFlowLinkProtocol.Current,
                messageId = messageIds.next("error"),
                workspaceId = request.workspaceId,
                senderDeviceId = localIdentity.descriptor.deviceId,
                messageType = StudyFlowLinkMessageType.ERROR,
                name = "link.error",
                sentAtEpochMillis = clock.nowEpochMillis(),
                correlationId = request.messageId,
                payload = StudyFlowLinkCodec.encodePayload(
                    StudyFlowLinkError.serializer(),
                    StudyFlowLinkError(code = code, message = message, retryable = retryable),
                ),
            ),
        )
    }

    override fun close() {
        running.set(false)
        runCatching { listener?.close() }
        executor.shutdownNow()
    }
}

class StudyFlowRemoteSyncPeerSession(
    override val deviceId: StudyFlowDeviceId,
    override val workspaceId: StudyFlowWorkspaceId,
    private val localIdentity: StudyFlowLocalDeviceIdentity,
    private val trustManager: StudyFlowTrustManager,
    endpoint: StudyFlowTransportEndpoint,
    transportRegistry: StudyFlowTransportRegistry,
    private val clock: StudyFlowPeerClock,
    private val messageIds: StudyFlowSyncMessageIdFactory,
) : StudyFlowSyncPeerSession, Closeable {
    private val link = StudyFlowLinkConnection(transportRegistry.connect(endpoint))

    init {
        authenticate()
    }

    private fun authenticate() {
        val challengeRequest = StudyFlowLinkEnvelope(
            messageId = messageIds.next("reconnect-challenge"),
            workspaceId = StudyFlowLinkSystemWorkspaceId,
            senderDeviceId = localIdentity.descriptor.deviceId,
            messageType = StudyFlowLinkMessageType.REQUEST,
            name = StudyFlowPairingMessageNames.ReconnectChallenge,
            sentAtEpochMillis = clock.nowEpochMillis(),
            payload = StudyFlowLinkCodec.encodePayload(
                StudyFlowReconnectChallengeRequest.serializer(),
                StudyFlowReconnectChallengeRequest(localIdentity.descriptor.deviceId),
            ),
        )
        val challengeResponse = exchange(challengeRequest, StudyFlowPairingMessageNames.ReconnectChallenge)
        val challenge = StudyFlowLinkCodec.decodePayload(
            StudyFlowReconnectChallenge.serializer(),
            challengeResponse.payload,
        )
        require(challenge.challengerDeviceId == deviceId) { "Reconnect challenge came from an unexpected peer" }
        val proof = trustManager.createReconnectProof(challenge, clock.nowEpochMillis())
        val proofRequest = StudyFlowLinkEnvelope(
            messageId = messageIds.next("reconnect-proof"),
            workspaceId = StudyFlowLinkSystemWorkspaceId,
            senderDeviceId = localIdentity.descriptor.deviceId,
            messageType = StudyFlowLinkMessageType.REQUEST,
            name = StudyFlowPairingMessageNames.ReconnectProof,
            sentAtEpochMillis = clock.nowEpochMillis(),
            payload = StudyFlowLinkCodec.encodePayload(StudyFlowReconnectProof.serializer(), proof),
        )
        exchange(proofRequest, StudyFlowPeerRpcMessageNames.ReconnectAccepted)
    }

    override fun currentFrontier(): StudyFlowJournalFrontier {
        val request = StudyFlowSyncWire.frontierRequest(
            messageId = messageIds.next("frontier"),
            workspaceId = workspaceId,
            senderDeviceId = localIdentity.descriptor.deviceId,
            sentAtEpochMillis = clock.nowEpochMillis(),
            knownFrontier = StudyFlowJournalFrontier(),
        )
        val response = exchange(request, StudyFlowSyncMessageNames.Frontier)
        return StudyFlowLinkCodec.decodePayload(
            StudyFlowSyncFrontierResponse.serializer(),
            response.payload,
        ).currentFrontier
    }

    override fun nextBatch(
        request: StudyFlowSyncPullRequest,
        batchId: StudyFlowMessageId,
    ): StudyFlowSyncOperationsBatch {
        val envelope = StudyFlowSyncWire.pullRequest(
            messageId = messageIds.next("pull"),
            workspaceId = workspaceId,
            senderDeviceId = localIdentity.descriptor.deviceId,
            sentAtEpochMillis = clock.nowEpochMillis(),
            request = request,
        )
        val response = exchange(envelope, StudyFlowSyncMessageNames.Operations)
        return StudyFlowLinkCodec.decodePayload(StudyFlowSyncOperationsBatch.serializer(), response.payload)
    }

    override fun applyBatch(batch: StudyFlowSyncOperationsBatch): StudyFlowSyncApplyResult {
        val request = StudyFlowLinkEnvelope(
            messageId = messageIds.next("apply"),
            workspaceId = workspaceId,
            senderDeviceId = localIdentity.descriptor.deviceId,
            messageType = StudyFlowLinkMessageType.REQUEST,
            name = StudyFlowPeerRpcMessageNames.SyncApply,
            sentAtEpochMillis = clock.nowEpochMillis(),
            payload = StudyFlowLinkCodec.encodePayload(StudyFlowSyncOperationsBatch.serializer(), batch),
        )
        val response = exchange(request, StudyFlowPeerRpcMessageNames.SyncApply)
        return StudyFlowLinkCodec.decodePayload(StudyFlowSyncApplyResult.serializer(), response.payload)
    }

    fun exchangePresence(requestPayload: StudyFlowPresenceExchangeRequest): StudyFlowPresenceExchangeResponse {
        val request = StudyFlowLinkEnvelope(
            messageId = messageIds.next("presence-exchange"),
            workspaceId = workspaceId,
            senderDeviceId = localIdentity.descriptor.deviceId,
            messageType = StudyFlowLinkMessageType.REQUEST,
            name = StudyFlowPresenceMessageNames.Exchange,
            sentAtEpochMillis = clock.nowEpochMillis(),
            payload = StudyFlowLinkCodec.encodePayload(StudyFlowPresenceExchangeRequest.serializer(), requestPayload),
        )
        val response = exchange(request, StudyFlowPresenceMessageNames.Exchange)
        return StudyFlowLinkCodec.decodePayload(StudyFlowPresenceExchangeResponse.serializer(), response.payload)
    }

    fun deliverNotification(delivery: StudyFlowNotificationDelivery): StudyFlowNotificationDeliveryAck {
        require(delivery.sourceDeviceId == localIdentity.descriptor.deviceId) {
            "Notification delivery source must be the local peer"
        }
        require(delivery.targetDeviceId == deviceId) { "Notification delivery targets another peer" }
        require(delivery.notification.route.workspaceId == workspaceId) {
            "Notification delivery belongs to another workspace"
        }
        val request = StudyFlowLinkEnvelope(
            messageId = messageIds.next("notification-deliver"),
            workspaceId = workspaceId,
            senderDeviceId = localIdentity.descriptor.deviceId,
            messageType = StudyFlowLinkMessageType.REQUEST,
            name = StudyFlowNotificationMediaMessageNames.NotificationDeliver,
            sentAtEpochMillis = clock.nowEpochMillis(),
            payload = StudyFlowLinkCodec.encodePayload(StudyFlowNotificationDelivery.serializer(), delivery),
        )
        val response = exchange(request, StudyFlowNotificationMediaMessageNames.NotificationAck)
        return StudyFlowLinkCodec.decodePayload(StudyFlowNotificationDeliveryAck.serializer(), response.payload)
    }

    fun exchangeRoutedRpc(
        name: String,
        payload: kotlinx.serialization.json.JsonObject,
    ): StudyFlowRoutedRpcAccepted {
        require(StudyFlowRoutedRpcContracts.supports(name)) { "Unsupported routed RPC $name" }
        val request = StudyFlowLinkEnvelope(
            messageId = messageIds.next("routed-rpc"),
            workspaceId = workspaceId,
            senderDeviceId = localIdentity.descriptor.deviceId,
            messageType = StudyFlowLinkMessageType.REQUEST,
            name = name,
            sentAtEpochMillis = clock.nowEpochMillis(),
            payload = payload,
        )
        val response = exchange(request, StudyFlowPeerRpcMessageNames.RoutedAccepted)
        return StudyFlowLinkCodec.decodePayload(StudyFlowRoutedRpcAccepted.serializer(), response.payload)
    }

    private fun exchange(
        request: StudyFlowLinkEnvelope,
        expectedName: String,
    ): StudyFlowLinkEnvelope {
        link.send(request)
        val response = link.receive() ?: error("Peer closed the SFLP connection")
        require(response.correlationId == request.messageId) { "SFLP response correlation mismatch" }
        require(StudyFlowLinkProtocol.CurrentSupport.contains(response.protocol)) { "Peer returned an unsupported SFLP protocol" }
        require(response.protocol == request.protocol) { "SFLP response protocol does not match the request" }
        if (response.messageType == StudyFlowLinkMessageType.ERROR) {
            val error = StudyFlowLinkCodec.decodePayload(StudyFlowLinkError.serializer(), response.payload)
            throw StudyFlowRemoteProtocolException(error)
        }
        require(response.messageType == StudyFlowLinkMessageType.RESPONSE) { "Expected an SFLP response" }
        require(response.name == expectedName) { "Unexpected SFLP response ${response.name}" }
        require(response.senderDeviceId == deviceId) { "SFLP response came from an unexpected peer" }
        return response
    }

    override fun close() = link.close()
}

class StudyFlowNetworkPairingClient(
    private val localIdentity: StudyFlowLocalDeviceIdentity,
    private val trustManager: StudyFlowTrustManager,
    private val transportRegistry: StudyFlowTransportRegistry,
    private val clock: StudyFlowPeerClock,
    private val messageIds: StudyFlowSyncMessageIdFactory,
) {
    private val pairingClient = StudyFlowPairingClient(trustManager)

    fun pairFromQr(
        qrPayload: String,
        endpointOverride: StudyFlowTransportEndpoint? = null,
    ): StudyFlowTrustedPeerRecord {
        val payload = StudyFlowPairingQrCodec.decode(qrPayload)
        val endpoint = endpointOverride ?: payload.endpointHints
            .asSequence()
            .mapNotNull { hint -> runCatching { StudyFlowManualEndpointParser.parse(hint) }.getOrNull() }
            .firstOrNull()
            ?: error("Pairing QR does not contain a usable endpoint")
        require(payload.isActive(clock.nowEpochMillis())) { "Pairing QR is expired" }
        val requestPayload = pairingClient.requestFromQr(qrPayload, clock.nowEpochMillis())
        return sendPairingRequest(endpoint, requestPayload, payload.host.deviceId)
    }

    fun pairFromCode(
        offer: StudyFlowPairingPublicOffer,
        pairingCode: String,
        endpoint: StudyFlowTransportEndpoint,
    ): StudyFlowTrustedPeerRecord = sendPairingRequest(
        endpoint,
        pairingClient.requestFromCode(offer, pairingCode, clock.nowEpochMillis()),
        offer.host.deviceId,
    )

    private fun sendPairingRequest(
        endpoint: StudyFlowTransportEndpoint,
        pairingRequest: StudyFlowPairingRequest,
        expectedHostDeviceId: StudyFlowDeviceId,
    ): StudyFlowTrustedPeerRecord {
        StudyFlowLinkConnection(transportRegistry.connect(endpoint)).use { link ->
            val request = StudyFlowLinkEnvelope(
                messageId = messageIds.next("pair"),
                workspaceId = StudyFlowLinkSystemWorkspaceId,
                senderDeviceId = localIdentity.descriptor.deviceId,
                messageType = StudyFlowLinkMessageType.REQUEST,
                name = StudyFlowPairingMessageNames.Pair,
                sentAtEpochMillis = clock.nowEpochMillis(),
                payload = StudyFlowLinkCodec.encodePayload(StudyFlowPairingRequest.serializer(), pairingRequest),
            )
            link.send(request)
            val response = link.receive() ?: error("Pairing host closed the connection")
            require(response.correlationId == request.messageId) { "Pairing response correlation mismatch" }
            require(StudyFlowLinkProtocol.CurrentSupport.contains(response.protocol)) { "Pairing response uses an unsupported protocol" }
            require(response.protocol == request.protocol) { "Pairing response protocol mismatch" }
            require(response.senderDeviceId == expectedHostDeviceId) { "Pairing response came from an unexpected host" }
            require(response.workspaceId == StudyFlowLinkSystemWorkspaceId) { "Pairing response workspace mismatch" }
            if (response.messageType == StudyFlowLinkMessageType.ERROR) {
                val error = StudyFlowLinkCodec.decodePayload(StudyFlowLinkError.serializer(), response.payload)
                throw StudyFlowRemoteProtocolException(error)
            }
            require(response.messageType == StudyFlowLinkMessageType.RESPONSE) { "Expected a pairing RESPONSE envelope" }
            require(response.name == StudyFlowPairingMessageNames.Grant) { "Unexpected pairing response ${response.name}" }
            val grant = runCatching {
                StudyFlowLinkCodec.decodePayload(StudyFlowPairingGrant.serializer(), response.payload)
            }.getOrElse {
                val rejected = StudyFlowLinkCodec.decodePayload(
                    StudyFlowPairingRejectedPayload.serializer(),
                    response.payload,
                )
                error("Pairing rejected: ${rejected.reason}")
            }
            require(grant.host.deviceId == expectedHostDeviceId) { "Pairing grant host does not match response sender" }
            return pairingClient.acceptGrant(grant, clock.nowEpochMillis())
        }
    }
}

class StudyFlowPeerRuntime(
    val localIdentity: StudyFlowLocalDeviceIdentity,
    trustStore: StudyFlowTrustStore,
    private val journalStore: StudyFlowOperationJournalStore,
    private val queueStore: StudyFlowOutgoingQueueStore,
    val endpointStore: StudyFlowPeerEndpointStore,
    val discovery: StudyFlowDiscovery,
    val transportRegistry: StudyFlowTransportRegistry = StudyFlowTransportRegistry(
        listOf(StudyFlowTcpTransport(), StudyFlowWebSocketTransport()),
    ),
    val clock: StudyFlowPeerClock = StudyFlowPeerClock(System::currentTimeMillis),
    val messageIds: StudyFlowSyncMessageIdFactory = StudyFlowUuidMessageIdFactory(),
    membershipStore: StudyFlowWorkspaceMembershipStore? = null,
) : StudyFlowWorkspaceRuntimeProvider, Closeable {
    val trustManager = StudyFlowTrustManager(localIdentity, trustStore)
    val workspaceMembership: StudyFlowWorkspaceMembershipManager? = membershipStore?.let {
        StudyFlowWorkspaceMembershipManager(localIdentity.descriptor.deviceId, it)
    }
    val pairingAuthority = StudyFlowPairingAuthority(trustManager)
    val pairingClient = StudyFlowNetworkPairingClient(
        localIdentity = localIdentity,
        trustManager = trustManager,
        transportRegistry = transportRegistry,
        clock = clock,
        messageIds = messageIds,
    )
    val routedRpcBus = StudyFlowRoutedRpcBus()
    val presenceRegistry = StudyFlowPresenceRegistry(localIdentity.descriptor.deviceId)
    val presencePublisher = StudyFlowLocalPresencePublisher(
        localDevice = localIdentity.descriptor,
        registry = presenceRegistry,
        clock = clock,
    )
    private val workspaceRuntimes = ConcurrentHashMap<StudyFlowWorkspaceId, StudyFlowWorkspaceRuntime>()
    private val workspaceTopologies = ConcurrentHashMap<StudyFlowWorkspaceId, StudyFlowWorkspaceTopology>()
    @Volatile
    private var notificationDeliveryHandler:
        ((StudyFlowNotificationDelivery) -> StudyFlowNotificationDeliveryAck)? = null
    private var server: StudyFlowPeerServer? = null
    private var webSocketServer: StudyFlowPeerServer? = null

    override fun workspace(workspaceId: StudyFlowWorkspaceId): StudyFlowWorkspaceRuntime {
        topology(workspaceId)
        return workspaceRuntimes.computeIfAbsent(workspaceId) {
            StudyFlowWorkspaceRuntime(
                workspaceId = workspaceId,
                localDeviceId = localIdentity.descriptor.deviceId,
                journalStore = journalStore,
                queueStore = queueStore,
                messageIds = messageIds,
            )
        }
    }

    fun topology(workspaceId: StudyFlowWorkspaceId): StudyFlowWorkspaceTopology =
        workspaceTopologies.computeIfAbsent(workspaceId) {
            StudyFlowWorkspaceTopology(workspaceId, localIdentity.descriptor.deviceId)
        }

    fun startServer(bindHost: String = "0.0.0.0", port: Int = 0): StudyFlowTransportEndpoint {
        check(server == null) { "SFLP peer server is already started" }
        val created = StudyFlowPeerServer(
            localIdentity = localIdentity,
            trustManager = trustManager,
            pairingAuthority = pairingAuthority,
            workspaces = this,
            presenceRegistry = presenceRegistry,
            presencePublisher = presencePublisher,
            clock = clock,
            messageIds = messageIds,
            workspaceMembership = workspaceMembership,
            routedRpcBus = routedRpcBus,
            notificationDeliveryHandler = { delivery -> notificationDeliveryHandler?.invoke(delivery) },
        )
        val bound = created.start(StudyFlowTransportEndpoint(StudyFlowTransportScheme.TCP, bindHost, port))
        server = created
        return bound
    }

    fun startWebSocketServer(
        bindHost: String = "0.0.0.0",
        port: Int = 0,
        path: String = "/sflp",
    ): StudyFlowTransportEndpoint {
        check(webSocketServer == null) { "SFLP WebSocket server is already started" }
        val transport = StudyFlowWebSocketTransport(path)
        val created = StudyFlowPeerServer(
            localIdentity = localIdentity,
            trustManager = trustManager,
            pairingAuthority = pairingAuthority,
            workspaces = this,
            presenceRegistry = presenceRegistry,
            presencePublisher = presencePublisher,
            clock = clock,
            messageIds = messageIds,
            workspaceMembership = workspaceMembership,
            routedRpcBus = routedRpcBus,
            notificationDeliveryHandler = { delivery -> notificationDeliveryHandler?.invoke(delivery) },
            listeningTransport = transport,
        )
        val bound = created.start(
            StudyFlowTransportEndpoint(StudyFlowTransportScheme.WEBSOCKET, bindHost, port, path),
        )
        webSocketServer = created
        return bound
    }

    fun setNotificationDeliveryHandler(
        handler: ((StudyFlowNotificationDelivery) -> StudyFlowNotificationDeliveryAck)?,
    ) {
        notificationDeliveryHandler = handler
    }

    fun deliverNotification(
        workspaceId: StudyFlowWorkspaceId,
        peerDeviceId: StudyFlowDeviceId,
        delivery: StudyFlowNotificationDelivery,
        endpoint: StudyFlowTransportEndpoint? = null,
    ): StudyFlowNotificationDeliveryAck {
        require(delivery.sourceDeviceId == localIdentity.descriptor.deviceId) {
            "Notification delivery source must be local"
        }
        require(delivery.targetDeviceId == peerDeviceId) { "Notification delivery target mismatch" }
        val selectedEndpoint = endpoint
            ?: topology(workspaceId).bestRouteTo(peerDeviceId)?.endpoint
            ?: requireNotNull(bestEndpoint(peerDeviceId)) { "No remembered endpoint for ${peerDeviceId.value}" }
        return StudyFlowRemoteSyncPeerSession(
            deviceId = peerDeviceId,
            workspaceId = workspaceId,
            localIdentity = localIdentity,
            trustManager = trustManager,
            endpoint = selectedEndpoint,
            transportRegistry = transportRegistry,
            clock = clock,
            messageIds = messageIds,
        ).use { it.deliverNotification(delivery) }
    }

    fun sendRoutedRpc(
        workspaceId: StudyFlowWorkspaceId,
        peerDeviceId: StudyFlowDeviceId,
        name: String,
        payload: kotlinx.serialization.json.JsonObject,
        endpoint: StudyFlowTransportEndpoint? = null,
    ): StudyFlowRoutedRpcAccepted {
        require(StudyFlowRoutedRpcContracts.supports(name)) { "Unsupported routed RPC $name" }
        val selectedEndpoint = endpoint
            ?: topology(workspaceId).bestRouteTo(peerDeviceId)?.endpoint
            ?: requireNotNull(bestEndpoint(peerDeviceId)) { "No remembered endpoint for ${peerDeviceId.value}" }
        return StudyFlowRemoteSyncPeerSession(
            deviceId = peerDeviceId,
            workspaceId = workspaceId,
            localIdentity = localIdentity,
            trustManager = trustManager,
            endpoint = selectedEndpoint,
            transportRegistry = transportRegistry,
            clock = clock,
            messageIds = messageIds,
        ).use { it.exchangeRoutedRpc(name, payload) }
    }

    fun createPairingOffer(endpointHints: List<String>): StudyFlowPairingPublicOffer =
        pairingAuthority.createOffer(clock.nowEpochMillis(), endpointHints)

    fun rememberEndpoints(
        deviceId: StudyFlowDeviceId,
        endpoints: List<StudyFlowTransportEndpoint>,
    ): StudyFlowPeerEndpointRecord {
        val record = StudyFlowPeerEndpointRecord(
            deviceId = deviceId,
            endpoints = endpoints.distinct().sortedBy(StudyFlowTransportEndpoint::canonicalUri),
            updatedAtEpochMillis = clock.nowEpochMillis(),
        )
        endpointStore.put(record)
        refreshTopologyRoutes(deviceId, record.endpoints)
        return record
    }

    fun ingestDiscovery(): List<StudyFlowDiscoveredPeer> {
        val now = clock.nowEpochMillis()
        return discovery.snapshot(now).also { peers ->
            peers.filter { it.deviceId != null && it.deviceId != localIdentity.descriptor.deviceId }
                .forEach { peer -> rememberEndpoints(requireNotNull(peer.deviceId), peer.endpoints) }
        }
    }

    fun bestEndpoint(deviceId: StudyFlowDeviceId): StudyFlowTransportEndpoint? =
        endpointStore.get(deviceId)?.endpoints?.firstOrNull()

    fun reconcileWith(
        workspaceId: StudyFlowWorkspaceId,
        peerDeviceId: StudyFlowDeviceId,
        endpoint: StudyFlowTransportEndpoint? = null,
    ): StudyFlowReconnectResult {
        val workspaceRuntime = workspace(workspaceId)
        val now = clock.nowEpochMillis()
        workspaceRuntime.reconnect.deferredResultIfBackoff(peerDeviceId, now)?.let { return it }

        val topology = topology(workspaceId)
        val selectedEndpoint = endpoint
            ?: topology.bestRouteTo(peerDeviceId)?.endpoint
            ?: requireNotNull(bestEndpoint(peerDeviceId)) {
                "No remembered endpoint for ${peerDeviceId.value}"
            }
        ensureTopologyPeer(workspaceId, peerDeviceId)
        upsertDirectRoute(
            workspaceId = workspaceId,
            peerDeviceId = peerDeviceId,
            endpoint = selectedEndpoint,
            health = StudyFlowPeerHealth(StudyFlowPeerConnectionState.CONNECTING),
        )
        val startedAt = System.nanoTime()
        return try {
            val result = StudyFlowRemoteSyncPeerSession(
                deviceId = peerDeviceId,
                workspaceId = workspaceId,
                localIdentity = localIdentity,
                trustManager = trustManager,
                endpoint = selectedEndpoint,
                transportRegistry = transportRegistry,
                clock = clock,
                messageIds = messageIds,
            ).use { remote ->
                workspaceRuntime.reconnect.reconcile(remote, clock.nowEpochMillis()).also { reconnectResult ->
                    if (reconnectResult.status == StudyFlowReconnectStatus.COMPLETE) {
                        runCatching { exchangePresenceWith(remote, peerDeviceId, workspaceId) }
                    }
                }
            }
            val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L
            val connected = result.status == StudyFlowReconnectStatus.COMPLETE
            topology.updateNextHopHealth(
                peerDeviceId,
                StudyFlowPeerHealth(
                    state = if (connected) StudyFlowPeerConnectionState.CONNECTED else StudyFlowPeerConnectionState.DEGRADED,
                    lastSuccessAtEpochMillis = if (connected) clock.nowEpochMillis() else null,
                    lastFailureAtEpochMillis = if (connected) null else clock.nowEpochMillis(),
                    consecutiveFailures = if (connected) 0 else 1,
                    roundTripMillis = elapsedMillis,
                ),
                clock.nowEpochMillis(),
            )
            result
        } catch (error: StudyFlowPeerUnavailableException) {
            val failure = workspaceRuntime.reconnect.recordConnectionFailure(
                peerDeviceId = peerDeviceId,
                nowEpochMillis = clock.nowEpochMillis(),
                failure = error.message ?: "Peer unavailable",
            )
            topology.updateNextHopHealth(
                peerDeviceId,
                StudyFlowPeerHealth(
                    state = StudyFlowPeerConnectionState.DEGRADED,
                    lastFailureAtEpochMillis = clock.nowEpochMillis(),
                    consecutiveFailures = 1,
                ),
                clock.nowEpochMillis(),
            )
            failure
        } catch (error: Throwable) {
            topology.updateNextHopHealth(
                peerDeviceId,
                StudyFlowPeerHealth(
                    state = StudyFlowPeerConnectionState.DEGRADED,
                    lastFailureAtEpochMillis = clock.nowEpochMillis(),
                    consecutiveFailures = 1,
                ),
                clock.nowEpochMillis(),
            )
            throw error
        }
    }

    fun nextReconnectAt(
        workspaceId: StudyFlowWorkspaceId,
        peerDeviceId: StudyFlowDeviceId,
    ): Long? = workspace(workspaceId).outgoingQueue.nextPeerAttemptAt(peerDeviceId)

    fun updatePresence(
        workspaceId: StudyFlowWorkspaceId,
        availability: StudyFlowPresenceAvailability,
        activityHint: StudyFlowPresenceActivityHint? = null,
        visibilityCapabilities: List<StudyFlowLinkCapability> = emptyList(),
    ): StudyFlowPresenceRecord = presencePublisher.announce(
        workspaceId = workspaceId,
        availability = availability,
        activityHint = activityHint,
        visibilityCapabilities = visibilityCapabilities,
    )

    fun presenceViews(workspaceId: StudyFlowWorkspaceId): List<StudyFlowPresenceView> =
        presenceRegistry.viewsFor(
            workspaceId = workspaceId,
            viewer = localIdentity.descriptor,
            nowEpochMillis = clock.nowEpochMillis(),
        )

    private fun exchangePresenceWith(
        remote: StudyFlowRemoteSyncPeerSession,
        peerDeviceId: StudyFlowDeviceId,
        workspaceId: StudyFlowWorkspaceId,
    ) {
        presencePublisher.ensureOnline(workspaceId)
        val peer = trustManager.trustedPeers().firstOrNull { it.peer.deviceId == peerDeviceId } ?: return
        val request = StudyFlowPresenceExchangeRequest(
            presenceRegistry.recordsForPeer(workspaceId, peer.peer, clock.nowEpochMillis()),
        )
        val response = remote.exchangePresence(request)
        val trustedSourceIds = trustManager.trustedPeers().mapTo(linkedSetOf()) { it.peer.deviceId }
        response.records.asSequence()
            .filter { it.workspaceId == workspaceId }
            .forEach { record ->
                presenceRegistry.ingest(
                    record = record,
                    authenticatedPeerDeviceId = peerDeviceId,
                    trustedSourceDeviceIds = trustedSourceIds,
                nowEpochMillis = clock.nowEpochMillis(),
                )
            }
    }

    fun appendLocalMutation(
        workspaceId: StudyFlowWorkspaceId,
        operationId: StudyFlowOperationId,
        entityId: StudyFlowEntityId,
        operationType: String,
        payload: kotlinx.serialization.json.JsonObject = kotlinx.serialization.json.JsonObject(emptyMap()),
    ): StudyFlowDomainOperation {
        val runtime = workspace(workspaceId)
        val operation = runtime.journal.appendLocalMutation(
            operationId = operationId,
            entityId = entityId,
            operationType = operationType,
            occurredAtEpochMillis = clock.nowEpochMillis(),
            payload = payload,
        )
        runtime.reconnect.enqueueLocalOperation(
            operation = operation,
            peerDeviceIds = trustManager.trustedPeers().map { it.peer.deviceId }.filter { peerId ->
                workspaceMembership?.canWrite(workspaceId, peerId) != false
            },
            nowEpochMillis = clock.nowEpochMillis(),
        )
        return operation
    }

    fun grantWorkspaceAccess(
        workspaceId: StudyFlowWorkspaceId,
        peerDeviceId: StudyFlowDeviceId,
        access: StudyFlowWorkspaceAccess = StudyFlowWorkspaceAccess.WRITE,
    ): StudyFlowWorkspaceMember {
        require(trustManager.trustedPeers().any { it.peer.deviceId == peerDeviceId }) { "Peer must be trusted before workspace access is granted" }
        return requireNotNull(workspaceMembership) { "Workspace membership persistence is not configured" }
            .grant(workspaceId, peerDeviceId, access, clock.nowEpochMillis())
    }

    fun workspacePeers(workspaceId: StudyFlowWorkspaceId, requireWrite: Boolean = true): List<StudyFlowTrustedPeerRecord> =
        trustManager.trustedPeers().filter { record ->
            if (requireWrite) workspaceMembership?.canWrite(workspaceId, record.peer.deviceId) != false
            else workspaceMembership?.canRead(workspaceId, record.peer.deviceId) != false
        }

    fun flushProductState(workspaceId: StudyFlowWorkspaceId, bridge: StudyFlowProductStateBridge): Int {
        var appended = 0
        bridge.captureLocalMutations(workspaceId).forEach { mutation ->
            val runtime = workspace(workspaceId)
            val op = if (mutation.tombstone) runtime.journal.appendLocalTombstone(
                mutation.operationId, mutation.entityId, mutation.operationType, clock.nowEpochMillis(), mutation.payload
            ) else runtime.journal.appendLocalMutation(
                mutation.operationId, mutation.entityId, mutation.operationType, clock.nowEpochMillis(), mutation.payload
            )
            runtime.reconnect.enqueueLocalOperation(op, workspacePeers(workspaceId).map { it.peer.deviceId }, clock.nowEpochMillis())
            appended++
        }
        return appended
    }

    fun projectProductState(workspaceId: StudyFlowWorkspaceId, bridge: StudyFlowProductStateBridge): Int =
        bridge.projectResolved(workspaceId, workspace(workspaceId).reconciliation.resolvedReplay().operations)

    private fun refreshTopologyRoutes(
        peerDeviceId: StudyFlowDeviceId,
        endpoints: List<StudyFlowTransportEndpoint>,
    ) {
        workspaceTopologies.keys.forEach { workspaceId ->
            ensureTopologyPeer(workspaceId, peerDeviceId)
            endpoints.forEach { endpoint ->
                upsertDirectRoute(
                    workspaceId = workspaceId,
                    peerDeviceId = peerDeviceId,
                    endpoint = endpoint,
                    health = StudyFlowPeerHealth(StudyFlowPeerConnectionState.CONNECTING),
                )
            }
        }
    }

    private fun ensureTopologyPeer(
        workspaceId: StudyFlowWorkspaceId,
        peerDeviceId: StudyFlowDeviceId,
    ) {
        val record = trustManager.rememberedPeers().firstOrNull { it.peer.deviceId == peerDeviceId } ?: return
        topology(workspaceId).upsertPeer(
            StudyFlowWorkspacePeer(
                descriptor = record.peer,
                lastSeenAtEpochMillis = record.lastSeenAtEpochMillis ?: record.updatedAtEpochMillis,
            ),
        )
    }

    private fun upsertDirectRoute(
        workspaceId: StudyFlowWorkspaceId,
        peerDeviceId: StudyFlowDeviceId,
        endpoint: StudyFlowTransportEndpoint,
        health: StudyFlowPeerHealth,
    ) {
        topology(workspaceId).upsertRoute(
            StudyFlowPeerRoute(
                targetDeviceId = peerDeviceId,
                nextHopDeviceId = peerDeviceId,
                endpoint = endpoint,
                hopCount = 1,
                kind = StudyFlowRouteKind.DIRECT,
                health = health,
                updatedAtEpochMillis = clock.nowEpochMillis(),
            ),
        )
    }

    fun revoke(peerDeviceId: StudyFlowDeviceId): StudyFlowTrustedPeerRecord? {
        val revoked = trustManager.revoke(peerDeviceId, clock.nowEpochMillis())
        workspaceMembership?.revokeDevice(peerDeviceId)
        endpointStore.remove(peerDeviceId)
        workspaceRuntimes.values.forEach { it.outgoingQueue.clearPeer(peerDeviceId) }
        workspaceTopologies.values.forEach { it.removePeer(peerDeviceId) }
        presenceRegistry.removePeer(peerDeviceId)
        return revoked
    }

    fun forget(peerDeviceId: StudyFlowDeviceId): Boolean {
        workspaceMembership?.revokeDevice(peerDeviceId)
        endpointStore.remove(peerDeviceId)
        workspaceRuntimes.values.forEach { it.outgoingQueue.clearPeer(peerDeviceId) }
        workspaceTopologies.values.forEach { it.removePeer(peerDeviceId) }
        presenceRegistry.removePeer(peerDeviceId)
        return trustManager.forget(peerDeviceId)
    }

    override fun close() {
        server?.close()
        server = null
        webSocketServer?.close()
        webSocketServer = null
    }
}
