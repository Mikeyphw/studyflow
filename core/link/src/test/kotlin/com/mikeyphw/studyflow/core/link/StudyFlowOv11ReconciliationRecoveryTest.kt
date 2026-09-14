package com.mikeyphw.studyflow.core.link

import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowOv11ReconciliationRecoveryTest {
    private val workspaceId = StudyFlowWorkspaceId("workspace-ov11")
    private val localId = StudyFlowDeviceId("device-ov11-local")
    private val peerId = StudyFlowDeviceId("device-ov11-peer")

    @Test
    fun persistedPeerBackoffDefersBeforeAnyPeerNetworkMethodRuns() {
        val store = InMemoryStudyFlowOutgoingQueueStore()
        val firstQueue = StudyFlowOutgoingOperationQueue(
            workspaceId,
            store,
            StudyFlowRetryPolicy(baseDelayMillis = 1_000L, maxDelayMillis = 8_000L),
        )
        firstQueue.recordPeerFailure(peerId, nowEpochMillis = 10_000L, failure = "offline")

        val calls = AtomicInteger()
        val peer = object : StudyFlowSyncPeerSession {
            override val deviceId = peerId
            override val workspaceId = this@StudyFlowOv11ReconciliationRecoveryTest.workspaceId
            override fun currentFrontier(): StudyFlowJournalFrontier {
                calls.incrementAndGet()
                return StudyFlowJournalFrontier()
            }
            override fun nextBatch(request: StudyFlowSyncPullRequest, batchId: StudyFlowMessageId): StudyFlowSyncOperationsBatch {
                calls.incrementAndGet()
                error("network must not be reached while peer backoff is active")
            }
            override fun applyBatch(batch: StudyFlowSyncOperationsBatch): StudyFlowSyncApplyResult {
                calls.incrementAndGet()
                error("network must not be reached while peer backoff is active")
            }
        }
        val coordinator = coordinator(
            journal = journal(localId),
            queue = StudyFlowOutgoingOperationQueue(workspaceId, store),
        )

        val result = coordinator.reconcile(peer, nowEpochMillis = 10_999L)

        assertEquals(StudyFlowReconnectStatus.DEFERRED_BY_BACKOFF, result.status)
        assertEquals(11_000L, result.retryAtEpochMillis)
        assertEquals(0, calls.get())
    }

    @Test
    fun runtimeBackoffGatesSessionCreationBeforeTrustOrSocketWork() {
        val fixedNow = 25_000L
        val runtime = runtime(StudyFlowPeerClock { fixedNow })
        val unreachableEndpoint = StudyFlowTransportEndpoint(
            scheme = StudyFlowTransportScheme.TCP,
            host = "127.0.0.1",
            port = 65_000,
        )
        runtime.workspace(workspaceId).outgoingQueue.recordPeerFailure(peerId, fixedNow, "connection refused")

        val result = runtime.reconcileWith(workspaceId, peerId, unreachableEndpoint)

        assertEquals(StudyFlowReconnectStatus.DEFERRED_BY_BACKOFF, result.status)
        assertTrue(requireNotNull(result.retryAtEpochMillis) > fixedNow)
        runtime.close()
    }

    @Test
    fun transientFailurePenalizesOnlyOperationsThatWereActuallyAttempted() {
        val local = journal(localId)
        val first = operation("op-ov11-1", localId, 1L)
        val second = operation("op-ov11-2", localId, 2L)
        local.append(first)
        local.append(second)
        val store = InMemoryStudyFlowOutgoingQueueStore()
        val queue = StudyFlowOutgoingOperationQueue(workspaceId, store)
        queue.enqueue(peerId, first, 100L)
        queue.enqueue(peerId, second, 100L)
        val coordinator = coordinator(local, queue, batchSize = 1)
        val failingPeer = object : StudyFlowSyncPeerSession {
            override val deviceId = peerId
            override val workspaceId = this@StudyFlowOv11ReconciliationRecoveryTest.workspaceId
            override fun currentFrontier() = StudyFlowJournalFrontier()
            override fun nextBatch(request: StudyFlowSyncPullRequest, batchId: StudyFlowMessageId) =
                StudyFlowSyncOperationsBatch(batchId, workspaceId, request.knownFrontier, emptyList(), request.knownFrontier, false)
            override fun applyBatch(batch: StudyFlowSyncOperationsBatch): StudyFlowSyncApplyResult =
                throw StudyFlowPeerUnavailableException("transient transport loss")
        }

        val result = coordinator.reconcile(failingPeer, nowEpochMillis = 1_000L)
        val byId = queue.pendingForPeer(peerId).associateBy { it.operation.operationId.value }

        assertEquals(StudyFlowReconnectStatus.INTERRUPTED, result.status)
        assertEquals(1, requireNotNull(byId[first.operationId.value]).attemptCount)
        assertEquals(0, requireNotNull(byId[second.operationId.value]).attemptCount)
        assertNotNull(queue.peerRetryState(peerId))
    }

    @Test
    fun malformedRemoteBatchBecomesStructuredTerminalFailureInsteadOfEscaping() {
        val local = journal(localId)
        val coordinator = coordinator(local)
        val remoteOperation = operation("op-remote-gap", peerId, 2L)
        val malformedPeer = object : StudyFlowSyncPeerSession {
            override val deviceId = peerId
            override val workspaceId = this@StudyFlowOv11ReconciliationRecoveryTest.workspaceId
            override fun currentFrontier() = StudyFlowJournalFrontier()
            override fun nextBatch(request: StudyFlowSyncPullRequest, batchId: StudyFlowMessageId) =
                StudyFlowSyncOperationsBatch(
                    batchId = batchId,
                    workspaceId = workspaceId,
                    baseFrontier = request.knownFrontier,
                    operations = listOf(remoteOperation),
                    throughFrontier = StudyFlowJournalFrontier(listOf(StudyFlowOriginRevision(peerId, 2L))),
                    hasMore = false,
                )
            override fun applyBatch(batch: StudyFlowSyncOperationsBatch): StudyFlowSyncApplyResult =
                error("local peer has no outgoing operations")
        }

        val result = coordinator.reconcile(malformedPeer, nowEpochMillis = 2_000L)

        assertEquals(StudyFlowReconnectStatus.TERMINAL_FAILURE, result.status)
        assertTrue(requireNotNull(result.failure).contains("through-frontier"))
    }

    @Test
    fun retryableAndFatalRemoteErrorsAreClassifiedDifferently() {
        fun peer(retryable: Boolean) = object : StudyFlowSyncPeerSession {
            override val deviceId = peerId
            override val workspaceId = this@StudyFlowOv11ReconciliationRecoveryTest.workspaceId
            override fun currentFrontier(): StudyFlowJournalFrontier = throw StudyFlowRemoteProtocolException(
                StudyFlowLinkError(
                    code = if (retryable) "link.unavailable" else "link.unauthorized",
                    message = if (retryable) "temporary" else "revoked",
                    retryable = retryable,
                ),
            )
            override fun nextBatch(request: StudyFlowSyncPullRequest, batchId: StudyFlowMessageId): StudyFlowSyncOperationsBatch = error("unused")
            override fun applyBatch(batch: StudyFlowSyncOperationsBatch): StudyFlowSyncApplyResult = error("unused")
        }

        val retryStore = InMemoryStudyFlowOutgoingQueueStore()
        val retryResult = coordinator(journal(localId), StudyFlowOutgoingOperationQueue(workspaceId, retryStore))
            .reconcile(peer(true), nowEpochMillis = 3_000L)
        val fatalStore = InMemoryStudyFlowOutgoingQueueStore()
        val fatalResult = coordinator(journal(localId), StudyFlowOutgoingOperationQueue(workspaceId, fatalStore))
            .reconcile(peer(false), nowEpochMillis = 3_000L)

        assertEquals(StudyFlowReconnectStatus.INTERRUPTED, retryResult.status)
        assertNotNull(StudyFlowOutgoingOperationQueue(workspaceId, retryStore).peerRetryState(peerId))
        assertEquals(StudyFlowReconnectStatus.TERMINAL_FAILURE, fatalResult.status)
        assertEquals(null, StudyFlowOutgoingOperationQueue(workspaceId, fatalStore).peerRetryState(peerId))
    }

    @Test
    fun peerRetryStateRoundTripsWithQueueSnapshot() {
        val operation = operation("op-persist", localId, 1L)
        val entry = StudyFlowOutgoingQueueEntry(peerId, operation, queuedAtEpochMillis = 10L)
        val state = StudyFlowPeerRetryState(workspaceId, peerId, 3, 100L, 5_000L, "offline")
        val encoded = StudyFlowPeerPersistenceCodec.encodeOutgoingQueue(workspaceId, listOf(entry), listOf(state))
        val decoded = StudyFlowPeerPersistenceCodec.decodeOutgoingQueueSnapshot(workspaceId, encoded)
        assertEquals(listOf(entry), decoded.entries)
        assertEquals(listOf(state), decoded.peerRetryStates)
    }

    private fun coordinator(
        journal: StudyFlowOperationJournal,
        queue: StudyFlowOutgoingOperationQueue = StudyFlowOutgoingOperationQueue(workspaceId, InMemoryStudyFlowOutgoingQueueStore()),
        batchSize: Int = 100,
    ): StudyFlowReconnectionCoordinator {
        val ids = AtomicInteger()
        return StudyFlowReconnectionCoordinator(
            localDeviceId = localId,
            localEngine = StudyFlowReconciliationEngine(journal),
            outgoingQueue = queue,
            messageIds = StudyFlowSyncMessageIdFactory { prefix -> StudyFlowMessageId("$prefix-${ids.incrementAndGet()}") },
            batchSize = batchSize,
        )
    }

    private fun journal(deviceId: StudyFlowDeviceId) = StudyFlowOperationJournal(
        workspaceId = workspaceId,
        localDeviceId = deviceId,
        store = InMemoryStudyFlowOperationJournalStore(),
    )

    private fun operation(id: String, origin: StudyFlowDeviceId, sequence: Long) = StudyFlowDomainOperation(
        operationId = StudyFlowOperationId(id),
        workspaceId = workspaceId,
        originDeviceId = origin,
        originSequence = sequence,
        entityId = StudyFlowEntityId("entity-$id"),
        entityRevision = StudyFlowEntityRevision(1L),
        logicalTimestamp = sequence,
        operationType = "study.changed",
        occurredAtEpochMillis = sequence * 100L,
    )

    private fun runtime(clock: StudyFlowPeerClock): StudyFlowPeerRuntime {
        val identity = StudyFlowLocalDeviceIdentity(
            descriptor = StudyFlowLinkPeerDescriptor(
                deviceId = localId,
                displayName = "OV11 local",
                clientKind = StudyFlowLinkClientKind.DESKTOP,
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
