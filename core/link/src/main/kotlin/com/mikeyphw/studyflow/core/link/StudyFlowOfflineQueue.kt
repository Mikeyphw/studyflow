package com.mikeyphw.studyflow.core.link

import kotlinx.serialization.Serializable

private const val DefaultSyncBatchSize = 100
private const val DefaultMaxSyncRounds = 8

@Serializable
data class StudyFlowOutgoingQueueEntry(
    val peerDeviceId: StudyFlowDeviceId,
    val operation: StudyFlowDomainOperation,
    val queuedAtEpochMillis: Long,
    val attemptCount: Int = 0,
    val lastAttemptAtEpochMillis: Long? = null,
    val nextAttemptAtEpochMillis: Long = queuedAtEpochMillis,
    val lastFailure: String? = null,
) {
    init {
        require(queuedAtEpochMillis >= 0L) { "Queue timestamp must not be negative" }
        require(attemptCount >= 0) { "Queue attempt count must not be negative" }
        require(lastAttemptAtEpochMillis == null || lastAttemptAtEpochMillis >= 0L) {
            "Queue attempt timestamp must not be negative"
        }
        require(nextAttemptAtEpochMillis >= queuedAtEpochMillis) {
            "Next queue attempt must not precede queue creation"
        }
    }

    val deliveryKey: String get() = "${peerDeviceId.value}|${operation.operationId.value}"
}

interface StudyFlowOutgoingQueueStore {
    fun load(workspaceId: StudyFlowWorkspaceId): List<StudyFlowOutgoingQueueEntry>
    fun save(workspaceId: StudyFlowWorkspaceId, entries: List<StudyFlowOutgoingQueueEntry>)
}

class InMemoryStudyFlowOutgoingQueueStore : StudyFlowOutgoingQueueStore {
    private val entriesByWorkspace = linkedMapOf<StudyFlowWorkspaceId, List<StudyFlowOutgoingQueueEntry>>()

    override fun load(workspaceId: StudyFlowWorkspaceId): List<StudyFlowOutgoingQueueEntry> =
        synchronized(this) { entriesByWorkspace[workspaceId].orEmpty().toList() }

    override fun save(workspaceId: StudyFlowWorkspaceId, entries: List<StudyFlowOutgoingQueueEntry>) {
        synchronized(this) { entriesByWorkspace[workspaceId] = entries.toList() }
    }
}

data class StudyFlowRetryPolicy(
    val baseDelayMillis: Long = 1_000L,
    val maxDelayMillis: Long = 5 * 60_000L,
) {
    init {
        require(baseDelayMillis >= 1L) { "Retry base delay must be positive" }
        require(maxDelayMillis >= baseDelayMillis) { "Retry max delay must cover the base delay" }
    }

    fun delayAfterFailure(attemptCount: Int): Long {
        require(attemptCount >= 1) { "Retry attempt count must be at least one" }
        var delay = baseDelayMillis
        repeat((attemptCount - 1).coerceAtMost(30)) {
            delay = (delay * 2L).coerceAtMost(maxDelayMillis)
        }
        return delay
    }
}

class StudyFlowOutgoingOperationQueue(
    val workspaceId: StudyFlowWorkspaceId,
    private val store: StudyFlowOutgoingQueueStore,
    private val retryPolicy: StudyFlowRetryPolicy = StudyFlowRetryPolicy(),
) {
    fun entries(): List<StudyFlowOutgoingQueueEntry> = store.load(workspaceId)
        .sortedWith(
            compareBy<StudyFlowOutgoingQueueEntry>(
                StudyFlowOutgoingQueueEntry::nextAttemptAtEpochMillis,
                { it.peerDeviceId.value },
                { it.operation.operationId.value },
            ),
        )

    fun enqueue(
        peerDeviceId: StudyFlowDeviceId,
        operation: StudyFlowDomainOperation,
        nowEpochMillis: Long,
    ): StudyFlowOutgoingQueueEntry {
        require(operation.workspaceId == workspaceId) { "Queued operation belongs to another workspace" }
        require(nowEpochMillis >= 0L) { "Queue timestamp must not be negative" }
        val current = entries().toMutableList()
        val key = "${peerDeviceId.value}|${operation.operationId.value}"
        val existing = current.firstOrNull { it.deliveryKey == key }
        if (existing != null) {
            require(existing.operation == operation) { "Delivery key was reused with different operation content" }
            return existing
        }
        val entry = StudyFlowOutgoingQueueEntry(
            peerDeviceId = peerDeviceId,
            operation = operation,
            queuedAtEpochMillis = nowEpochMillis,
            nextAttemptAtEpochMillis = nowEpochMillis,
        )
        current += entry
        save(current)
        return entry
    }

    fun enqueueForPeers(
        peerDeviceIds: Iterable<StudyFlowDeviceId>,
        operation: StudyFlowDomainOperation,
        nowEpochMillis: Long,
    ): List<StudyFlowOutgoingQueueEntry> = peerDeviceIds
        .distinct()
        .sortedBy(StudyFlowDeviceId::value)
        .map { enqueue(it, operation, nowEpochMillis) }

    fun dueForPeer(
        peerDeviceId: StudyFlowDeviceId,
        nowEpochMillis: Long,
        limit: Int = DefaultSyncBatchSize,
    ): List<StudyFlowOutgoingQueueEntry> {
        require(limit >= 1) { "Queue due limit must be at least one" }
        return entries()
            .asSequence()
            .filter { it.peerDeviceId == peerDeviceId }
            .filter { it.nextAttemptAtEpochMillis <= nowEpochMillis }
            .take(limit)
            .toList()
    }

    fun markFailed(
        peerDeviceId: StudyFlowDeviceId,
        operationIds: Set<StudyFlowOperationId>,
        nowEpochMillis: Long,
        failure: String,
    ) {
        require(nowEpochMillis >= 0L) { "Queue failure timestamp must not be negative" }
        val updated = entries().map { entry ->
            if (entry.peerDeviceId != peerDeviceId || entry.operation.operationId !in operationIds) {
                entry
            } else {
                val attempts = entry.attemptCount + 1
                entry.copy(
                    attemptCount = attempts,
                    lastAttemptAtEpochMillis = nowEpochMillis,
                    nextAttemptAtEpochMillis = nowEpochMillis + retryPolicy.delayAfterFailure(attempts),
                    lastFailure = failure.take(512),
                )
            }
        }
        save(updated)
    }

    fun acknowledgeCovered(
        peerDeviceId: StudyFlowDeviceId,
        peerFrontier: StudyFlowJournalFrontier,
    ): Int {
        val current = entries()
        val retained = current.filterNot { entry ->
            entry.peerDeviceId == peerDeviceId && peerFrontier.covers(entry.operation)
        }
        val removed = current.size - retained.size
        if (removed > 0) save(retained)
        return removed
    }

    fun pendingForPeer(peerDeviceId: StudyFlowDeviceId): List<StudyFlowOutgoingQueueEntry> =
        entries().filter { it.peerDeviceId == peerDeviceId }

    fun nextAttemptAt(peerDeviceId: StudyFlowDeviceId): Long? =
        pendingForPeer(peerDeviceId).minOfOrNull(StudyFlowOutgoingQueueEntry::nextAttemptAtEpochMillis)

    fun clearPeer(peerDeviceId: StudyFlowDeviceId): Int {
        val current = entries()
        val retained = current.filterNot { it.peerDeviceId == peerDeviceId }
        val removed = current.size - retained.size
        if (removed > 0) save(retained)
        return removed
    }

    private fun save(entries: List<StudyFlowOutgoingQueueEntry>) {
        val unique = entries.associateBy(StudyFlowOutgoingQueueEntry::deliveryKey)
        require(unique.size == entries.size) { "Outgoing queue contains duplicate delivery keys" }
        store.save(workspaceId, entries.sortedBy(StudyFlowOutgoingQueueEntry::deliveryKey))
    }
}

class StudyFlowPeerUnavailableException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

class StudyFlowRemoteProtocolException(
    val remoteError: StudyFlowLinkError,
) : RuntimeException("${remoteError.code}: ${remoteError.message}")

interface StudyFlowSyncPeerSession {
    val deviceId: StudyFlowDeviceId
    val workspaceId: StudyFlowWorkspaceId
    fun currentFrontier(): StudyFlowJournalFrontier
    fun nextBatch(request: StudyFlowSyncPullRequest, batchId: StudyFlowMessageId): StudyFlowSyncOperationsBatch
    fun applyBatch(batch: StudyFlowSyncOperationsBatch): StudyFlowSyncApplyResult
}

class LocalStudyFlowSyncPeerSession(
    override val deviceId: StudyFlowDeviceId,
    private val engine: StudyFlowReconciliationEngine,
    override val workspaceId: StudyFlowWorkspaceId,
) : StudyFlowSyncPeerSession {
    override fun currentFrontier(): StudyFlowJournalFrontier = engine.currentFrontier()

    override fun nextBatch(
        request: StudyFlowSyncPullRequest,
        batchId: StudyFlowMessageId,
    ): StudyFlowSyncOperationsBatch = engine.nextBatch(request, batchId)

    override fun applyBatch(batch: StudyFlowSyncOperationsBatch): StudyFlowSyncApplyResult = engine.applyBatch(batch)
}

fun interface StudyFlowSyncMessageIdFactory {
    fun next(prefix: String): StudyFlowMessageId
}

enum class StudyFlowReconnectStatus {
    COMPLETE,
    INTERRUPTED,
    TERMINAL_FAILURE,
    ROUND_LIMIT_REACHED,
}

data class StudyFlowReconnectResult(
    val status: StudyFlowReconnectStatus,
    val pushedOperations: Int,
    val pulledOperations: Int,
    val duplicateOperations: Int,
    val pushBatches: Int,
    val pullBatches: Int,
    val acknowledgedQueueEntries: Int,
    val localFrontier: StudyFlowJournalFrontier,
    val remoteFrontier: StudyFlowJournalFrontier?,
    val failure: String? = null,
)

class StudyFlowReconnectionCoordinator(
    private val localDeviceId: StudyFlowDeviceId,
    private val localEngine: StudyFlowReconciliationEngine,
    private val outgoingQueue: StudyFlowOutgoingOperationQueue,
    private val messageIds: StudyFlowSyncMessageIdFactory,
    private val batchSize: Int = DefaultSyncBatchSize,
    private val maxRounds: Int = DefaultMaxSyncRounds,
) {
    init {
        require(batchSize in 1..1000) { "Reconnect batch size must be between 1 and 1000" }
        require(maxRounds >= 1) { "Reconnect round limit must be at least one" }
    }

    fun enqueueLocalOperation(
        operation: StudyFlowDomainOperation,
        peerDeviceIds: Iterable<StudyFlowDeviceId>,
        nowEpochMillis: Long,
    ): List<StudyFlowOutgoingQueueEntry> = outgoingQueue.enqueueForPeers(
        peerDeviceIds.filter { it != localDeviceId },
        operation,
        nowEpochMillis,
    )

    fun reconcile(
        peer: StudyFlowSyncPeerSession,
        nowEpochMillis: Long,
    ): StudyFlowReconnectResult {
        require(peer.deviceId != localDeviceId) { "Cannot reconcile a device with itself" }
        require(peer.workspaceId == outgoingQueue.workspaceId) { "Peer belongs to another workspace" }

        var pushed = 0
        var pulled = 0
        var duplicates = 0
        var pushBatches = 0
        var pullBatches = 0
        var acknowledged = 0
        var remoteFrontier: StudyFlowJournalFrontier? = null

        try {
            repeat(maxRounds) { round ->
                remoteFrontier = peer.currentFrontier().normalized()
                acknowledged += outgoingQueue.acknowledgeCovered(peer.deviceId, requireNotNull(remoteFrontier))

                while (true) {
                    val localBatch = localEngine.nextBatch(
                        StudyFlowSyncPullRequest(
                            workspaceId = peer.workspaceId,
                            knownFrontier = requireNotNull(remoteFrontier),
                            maxOperations = batchSize,
                        ),
                        messageIds.next("push-$round"),
                    )
                    if (localBatch.operations.isEmpty()) break
                    localBatch.operations.forEach { operation ->
                        outgoingQueue.enqueue(peer.deviceId, operation, nowEpochMillis)
                    }
                    val applied = peer.applyBatch(localBatch)
                    pushBatches += 1
                    pushed += applied.added
                    duplicates += applied.duplicates
                    remoteFrontier = applied.resultingFrontier.normalized()
                    acknowledged += outgoingQueue.acknowledgeCovered(
                        peer.deviceId,
                        requireNotNull(remoteFrontier),
                    )
                    if (!localBatch.hasMore) break
                }

                while (true) {
                    val localFrontier = localEngine.currentFrontier().normalized()
                    val remoteBatch = peer.nextBatch(
                        StudyFlowSyncPullRequest(
                            workspaceId = peer.workspaceId,
                            knownFrontier = localFrontier,
                            maxOperations = batchSize,
                        ),
                        messageIds.next("pull-$round"),
                    )
                    if (remoteBatch.operations.isEmpty()) break
                    val applied = localEngine.applyBatch(remoteBatch)
                    pullBatches += 1
                    pulled += applied.added
                    duplicates += applied.duplicates
                    if (!remoteBatch.hasMore) break
                }

                val localAfter = localEngine.currentFrontier().normalized()
                remoteFrontier = peer.currentFrontier().normalized()
                acknowledged += outgoingQueue.acknowledgeCovered(peer.deviceId, requireNotNull(remoteFrontier))
                if (localAfter == remoteFrontier) {
                    return StudyFlowReconnectResult(
                        status = StudyFlowReconnectStatus.COMPLETE,
                        pushedOperations = pushed,
                        pulledOperations = pulled,
                        duplicateOperations = duplicates,
                        pushBatches = pushBatches,
                        pullBatches = pullBatches,
                        acknowledgedQueueEntries = acknowledged,
                        localFrontier = localAfter,
                        remoteFrontier = remoteFrontier,
                    )
                }
            }
        } catch (failure: StudyFlowRemoteProtocolException) {
            val retryable = failure.remoteError.retryable
            if (retryable) {
                val dueIds = outgoingQueue.pendingForPeer(peer.deviceId)
                    .map { it.operation.operationId }
                    .toSet()
                if (dueIds.isNotEmpty()) {
                    outgoingQueue.markFailed(peer.deviceId, dueIds, nowEpochMillis, failure.message.orEmpty())
                }
            }
            return StudyFlowReconnectResult(
                status = if (retryable) StudyFlowReconnectStatus.INTERRUPTED else StudyFlowReconnectStatus.TERMINAL_FAILURE,
                pushedOperations = pushed,
                pulledOperations = pulled,
                duplicateOperations = duplicates,
                pushBatches = pushBatches,
                pullBatches = pullBatches,
                acknowledgedQueueEntries = acknowledged,
                localFrontier = localEngine.currentFrontier().normalized(),
                remoteFrontier = remoteFrontier,
                failure = failure.message,
            )
        } catch (failure: StudyFlowPeerUnavailableException) {
            val dueIds = outgoingQueue.pendingForPeer(peer.deviceId)
                .map { it.operation.operationId }
                .toSet()
            if (dueIds.isNotEmpty()) {
                outgoingQueue.markFailed(peer.deviceId, dueIds, nowEpochMillis, failure.message.orEmpty())
            }
            return StudyFlowReconnectResult(
                status = StudyFlowReconnectStatus.INTERRUPTED,
                pushedOperations = pushed,
                pulledOperations = pulled,
                duplicateOperations = duplicates,
                pushBatches = pushBatches,
                pullBatches = pullBatches,
                acknowledgedQueueEntries = acknowledged,
                localFrontier = localEngine.currentFrontier().normalized(),
                remoteFrontier = remoteFrontier,
                failure = failure.message,
            )
        }

        return StudyFlowReconnectResult(
            status = StudyFlowReconnectStatus.ROUND_LIMIT_REACHED,
            pushedOperations = pushed,
            pulledOperations = pulled,
            duplicateOperations = duplicates,
            pushBatches = pushBatches,
            pullBatches = pullBatches,
            acknowledgedQueueEntries = acknowledged,
            localFrontier = localEngine.currentFrontier().normalized(),
            remoteFrontier = remoteFrontier,
        )
    }
}
