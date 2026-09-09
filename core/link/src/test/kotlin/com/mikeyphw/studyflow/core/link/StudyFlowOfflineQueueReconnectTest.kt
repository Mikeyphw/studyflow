package com.mikeyphw.studyflow.core.link

import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowOfflineQueueReconnectTest {
    private val workspaceId = StudyFlowWorkspaceId("workspace-main")
    private val androidId = StudyFlowDeviceId("android-main")
    private val desktopId = StudyFlowDeviceId("desktop-main")

    @Test
    fun outgoingQueueIsPersistentIdempotentAndUsesBoundedExponentialBackoff() {
        val store = InMemoryStudyFlowOutgoingQueueStore()
        val queue = StudyFlowOutgoingOperationQueue(
            workspaceId,
            store,
            StudyFlowRetryPolicy(baseDelayMillis = 1_000L, maxDelayMillis = 4_000L),
        )
        val operation = operation("op-1", androidId, 1L)
        queue.enqueue(desktopId, operation, nowEpochMillis = 100L)
        queue.enqueue(desktopId, operation, nowEpochMillis = 101L)
        assertEquals(1, queue.entries().size)

        queue.markFailed(desktopId, setOf(operation.operationId), 200L, "offline")
        assertTrue(queue.dueForPeer(desktopId, 1_199L).isEmpty())
        assertEquals(1, queue.dueForPeer(desktopId, 1_200L).size)
        queue.markFailed(desktopId, setOf(operation.operationId), 1_200L, "offline again")
        assertEquals(3_200L, queue.nextAttemptAt(desktopId))

        val reloaded = StudyFlowOutgoingOperationQueue(workspaceId, store)
        assertEquals(1, reloaded.pendingForPeer(desktopId).size)
        assertEquals(2, reloaded.pendingForPeer(desktopId).single().attemptCount)
    }

    @Test
    fun acknowledgingPeerFrontierRemovesOnlyDeliveriesPeerActuallyHas() {
        val queue = StudyFlowOutgoingOperationQueue(workspaceId, InMemoryStudyFlowOutgoingQueueStore())
        val first = operation("op-1", androidId, 1L)
        val second = operation("op-2", androidId, 2L)
        queue.enqueue(desktopId, first, 100L)
        queue.enqueue(desktopId, second, 100L)

        val removed = queue.acknowledgeCovered(
            desktopId,
            StudyFlowJournalFrontier(listOf(StudyFlowOriginRevision(androidId, 1L))),
        )
        assertEquals(1, removed)
        assertEquals("op-2", queue.pendingForPeer(desktopId).single().operation.operationId.value)
    }

    @Test
    fun reconnectCatchesUpStalePeerEvenWhenOutgoingQueueWasEmpty() {
        val local = journal(androidId)
        (1L..5L).forEach { sequence -> local.append(operation("android-$sequence", androidId, sequence)) }
        val remote = journal(desktopId)
        remote.append(operation("android-1", androidId, 1L))
        val coordinator = coordinator(local)
        val peer = LocalStudyFlowSyncPeerSession(
            desktopId,
            StudyFlowReconciliationEngine(remote),
            workspaceId,
        )

        val result = coordinator.reconcile(peer, nowEpochMillis = 1_000L)
        assertEquals(StudyFlowReconnectStatus.COMPLETE, result.status)
        assertEquals(4, result.pushedOperations)
        assertEquals(local.frontier(), remote.frontier())
    }

    @Test
    fun reconnectIsBidirectionalAndConvergesOfflineMutationsFromBothPeers() {
        val local = journal(androidId)
        val remote = journal(desktopId)
        local.append(operation("android-1", androidId, 1L))
        local.append(operation("android-2", androidId, 2L))
        remote.append(operation("desktop-1", desktopId, 1L))
        remote.append(operation("desktop-2", desktopId, 2L))
        val coordinator = coordinator(local, batchSize = 1)
        val peer = LocalStudyFlowSyncPeerSession(
            desktopId,
            StudyFlowReconciliationEngine(remote),
            workspaceId,
        )

        val result = coordinator.reconcile(peer, nowEpochMillis = 2_000L)
        assertEquals(StudyFlowReconnectStatus.COMPLETE, result.status)
        assertEquals(2, result.pushedOperations)
        assertEquals(2, result.pulledOperations)
        assertEquals(local.operations().toSet(), remote.operations().toSet())
        assertEquals(local.frontier(), remote.frontier())
        assertTrue(result.pushBatches >= 2)
        assertTrue(result.pullBatches >= 2)
    }

    @Test
    fun responseLossKeepsQueueAndNextReconnectRecoversFromReceiverTruthWithoutDuplicates() {
        val local = journal(androidId)
        val first = operation("android-1", androidId, 1L)
        val second = operation("android-2", androidId, 2L)
        local.append(first)
        local.append(second)
        val remote = journal(desktopId)
        val queueStore = InMemoryStudyFlowOutgoingQueueStore()
        val queue = StudyFlowOutgoingOperationQueue(workspaceId, queueStore)
        queue.enqueue(desktopId, first, 100L)
        queue.enqueue(desktopId, second, 100L)
        val coordinator = coordinator(local, queue = queue, batchSize = 2)
        val delegate = LocalStudyFlowSyncPeerSession(
            desktopId,
            StudyFlowReconciliationEngine(remote),
            workspaceId,
        )
        val lossy = ApplyThenLoseResponseSession(delegate)

        val interrupted = coordinator.reconcile(lossy, nowEpochMillis = 1_000L)
        assertEquals(StudyFlowReconnectStatus.INTERRUPTED, interrupted.status)
        assertEquals(2, remote.operations().size)
        assertEquals(2, queue.pendingForPeer(desktopId).size)
        assertTrue(queue.pendingForPeer(desktopId).all { it.attemptCount == 1 })

        val recovered = coordinator.reconcile(delegate, nowEpochMillis = 3_000L)
        assertEquals(StudyFlowReconnectStatus.COMPLETE, recovered.status)
        assertEquals(0, recovered.pushedOperations)
        assertEquals(2, recovered.acknowledgedQueueEntries)
        assertTrue(queue.pendingForPeer(desktopId).isEmpty())
        assertEquals(2, remote.operations().size)
    }

    @Test
    fun localOperationCanBeQueuedForSeveralPeersWithoutSelfDelivery() {
        val local = journal(androidId)
        val operation = operation("android-1", androidId, 1L)
        local.append(operation)
        val queue = StudyFlowOutgoingOperationQueue(workspaceId, InMemoryStudyFlowOutgoingQueueStore())
        val coordinator = coordinator(local, queue = queue)
        val web = StudyFlowDeviceId("web-main")
        val entries = coordinator.enqueueLocalOperation(
            operation,
            listOf(androidId, desktopId, web, desktopId),
            nowEpochMillis = 100L,
        )
        assertEquals(setOf(desktopId, web), entries.map { it.peerDeviceId }.toSet())
        assertEquals(2, queue.entries().size)
    }

    @Test
    fun retryPolicyNeverDropsPendingOperationAfterManyFailures() {
        val queue = StudyFlowOutgoingOperationQueue(
            workspaceId,
            InMemoryStudyFlowOutgoingQueueStore(),
            StudyFlowRetryPolicy(baseDelayMillis = 10L, maxDelayMillis = 40L),
        )
        val operation = operation("android-1", androidId, 1L)
        queue.enqueue(desktopId, operation, 0L)
        var now = 0L
        repeat(20) {
            queue.markFailed(desktopId, setOf(operation.operationId), now, "offline")
            now = requireNotNull(queue.nextAttemptAt(desktopId))
        }
        assertEquals(1, queue.pendingForPeer(desktopId).size)
        assertEquals(20, queue.pendingForPeer(desktopId).single().attemptCount)
        assertFalse(queue.entries().isEmpty())
    }

    private fun coordinator(
        localJournal: StudyFlowOperationJournal,
        queue: StudyFlowOutgoingOperationQueue = StudyFlowOutgoingOperationQueue(
            workspaceId,
            InMemoryStudyFlowOutgoingQueueStore(),
        ),
        batchSize: Int = 100,
    ): StudyFlowReconnectionCoordinator {
        val counter = AtomicInteger()
        return StudyFlowReconnectionCoordinator(
            localDeviceId = androidId,
            localEngine = StudyFlowReconciliationEngine(localJournal),
            outgoingQueue = queue,
            messageIds = StudyFlowSyncMessageIdFactory { prefix ->
                StudyFlowMessageId("$prefix-${counter.incrementAndGet()}")
            },
            batchSize = batchSize,
        )
    }

    private fun journal(deviceId: StudyFlowDeviceId) = StudyFlowOperationJournal(
        workspaceId = workspaceId,
        localDeviceId = deviceId,
        store = InMemoryStudyFlowOperationJournalStore(),
    )

    private fun operation(
        id: String,
        origin: StudyFlowDeviceId,
        originSequence: Long,
    ) = StudyFlowDomainOperation(
        operationId = StudyFlowOperationId(id),
        workspaceId = workspaceId,
        originDeviceId = origin,
        originSequence = originSequence,
        entityId = StudyFlowEntityId("entity-$id"),
        entityRevision = StudyFlowEntityRevision(1L),
        logicalTimestamp = originSequence,
        operationType = "study.changed",
        occurredAtEpochMillis = originSequence * 100L,
    )

    private class ApplyThenLoseResponseSession(
        private val delegate: StudyFlowSyncPeerSession,
    ) : StudyFlowSyncPeerSession by delegate {
        private var loseNext = true

        override fun applyBatch(batch: StudyFlowSyncOperationsBatch): StudyFlowSyncApplyResult {
            val result = delegate.applyBatch(batch)
            if (loseNext) {
                loseNext = false
                throw StudyFlowPeerUnavailableException("response lost after receiver commit")
            }
            return result
        }
    }
}
