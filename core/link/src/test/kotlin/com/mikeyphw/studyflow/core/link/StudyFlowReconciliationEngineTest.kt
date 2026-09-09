package com.mikeyphw.studyflow.core.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowReconciliationEngineTest {
    private val workspaceId = StudyFlowWorkspaceId("workspace-main")

    @Test
    fun peerFrontierDrivesBatchedMissingOperationTransfer() {
        val source = journal("android")
        (1..5).forEach { number ->
            source.appendLocalMutation(
                operationId = StudyFlowOperationId("op-$number"),
                entityId = StudyFlowEntityId("entity-$number"),
                operationType = "study.changed",
                occurredAtEpochMillis = number * 100L,
            )
        }
        val receiver = journal("desktop")
        val sourceEngine = StudyFlowReconciliationEngine(source)
        val receiverEngine = StudyFlowReconciliationEngine(receiver)

        var batchNumber = 1
        while (!receiver.frontier().dominates(source.frontier())) {
            val request = StudyFlowSyncPullRequest(
                workspaceId = workspaceId,
                knownFrontier = receiver.frontier(),
                maxOperations = 2,
            )
            val batch = sourceEngine.nextBatch(request, StudyFlowMessageId("batch-$batchNumber"))
            assertTrue(batch.operations.size <= 2)
            receiverEngine.applyBatch(batch)
            batchNumber += 1
        }

        assertEquals(source.frontier(), receiver.frontier())
        assertEquals(source.operations().toSet(), receiver.operations().toSet())
        assertEquals(3, batchNumber - 1)
    }

    @Test
    fun interruptedTransferResumesFromReceiverFrontierAndDuplicateRetryIsSafe() {
        val source = journal("web")
        (1..4).forEach { number ->
            source.appendLocalMutation(
                operationId = StudyFlowOperationId("web-op-$number"),
                entityId = StudyFlowEntityId("topic-$number"),
                operationType = "topic.changed",
                occurredAtEpochMillis = number * 100L,
            )
        }
        val receiver = journal("android")
        val sourceEngine = StudyFlowReconciliationEngine(source)
        val receiverEngine = StudyFlowReconciliationEngine(receiver)
        val first = sourceEngine.nextBatch(
            StudyFlowSyncPullRequest(workspaceId, receiver.frontier(), maxOperations = 2),
            StudyFlowMessageId("batch-1"),
        )
        val firstApply = receiverEngine.applyBatch(first)
        val retry = receiverEngine.applyBatch(first)
        assertEquals(2, firstApply.added)
        assertEquals(0, retry.added)
        assertEquals(2, retry.duplicates)

        val resumed = sourceEngine.nextBatch(
            StudyFlowSyncPullRequest(workspaceId, receiver.frontier(), maxOperations = 2),
            StudyFlowMessageId("batch-2"),
        )
        assertFalse(resumed.hasMore)
        receiverEngine.applyBatch(resumed)
        assertEquals(source.frontier(), receiver.frontier())
    }

    @Test
    fun skippedBatchIsRejectedSoCallerCanRestartFromReceiverFrontier() {
        val source = journal("web")
        (1..4).forEach { number ->
            source.appendLocalMutation(
                operationId = StudyFlowOperationId("web-op-$number"),
                entityId = StudyFlowEntityId("topic-$number"),
                operationType = "topic.changed",
                occurredAtEpochMillis = number * 100L,
            )
        }
        val sourceEngine = StudyFlowReconciliationEngine(source)
        val first = sourceEngine.nextBatch(
            StudyFlowSyncPullRequest(workspaceId, StudyFlowJournalFrontier(), maxOperations = 2),
            StudyFlowMessageId("batch-1"),
        )
        val second = sourceEngine.nextBatch(
            StudyFlowSyncPullRequest(workspaceId, first.throughFrontier, maxOperations = 2),
            StudyFlowMessageId("batch-2"),
        )
        val freshReceiver = StudyFlowReconciliationEngine(journal("android"))

        assertThrows(StudyFlowSyncContinuationException::class.java) {
            freshReceiver.applyBatch(second)
        }
    }

    @Test
    fun defaultConflictHandlerPicksSameWinnerRegardlessOfArrivalOrder() {
        val leftFirst = journal("desktop")
        val rightFirst = journal("desktop")
        val left = concurrentOperation("left", "android", 10L)
        val right = concurrentOperation("right", "web", 10L)
        leftFirst.append(left)
        leftFirst.append(right)
        rightFirst.append(right)
        rightFirst.append(left)

        val leftReplay = StudyFlowReconciliationEngine(leftFirst).resolvedReplay()
        val rightReplay = StudyFlowReconciliationEngine(rightFirst).resolvedReplay()
        assertEquals(1, leftReplay.conflicts.size)
        assertEquals(1, rightReplay.conflicts.size)
        assertEquals(leftReplay.operations, rightReplay.operations)
        assertEquals("right", leftReplay.operations.single().operationId.value)
    }

    @Test
    fun appendOnlyConflictRuleCanKeepConcurrentEvents() {
        val journal = journal("desktop")
        journal.append(concurrentOperation("attempt-a", "android", 20L, "attempt.recorded"))
        journal.append(concurrentOperation("attempt-b", "web", 20L, "attempt.recorded"))
        val handlers = StudyFlowConflictHandlerRegistry(
            rules = listOf(
                StudyFlowConflictRule("attempt.", StudyFlowKeepAllConflictHandler),
            ),
        )
        val replay = StudyFlowReconciliationEngine(journal, handlers).resolvedReplay()

        assertEquals(1, replay.conflicts.size)
        assertEquals(2, replay.operations.size)
        assertEquals(setOf("attempt-a", "attempt-b"), replay.operations.map { it.operationId.value }.toSet())
    }

    @Test
    fun batchIntegrityRejectsDeclaredFrontierThatOperationsCannotReach() {
        val receiver = journal("android")
        val engine = StudyFlowReconciliationEngine(receiver)
        val impossible = StudyFlowSyncOperationsBatch(
            batchId = StudyFlowMessageId("batch-bad"),
            workspaceId = workspaceId,
            baseFrontier = StudyFlowJournalFrontier(),
            operations = listOf(
                operation("remote-1", "web", 1L, "entity-1", 1L, 1L),
            ),
            throughFrontier = StudyFlowJournalFrontier(
                listOf(StudyFlowOriginRevision(StudyFlowDeviceId("web"), 2L)),
            ),
            hasMore = false,
        )

        assertThrows(StudyFlowSyncBatchIntegrityException::class.java) {
            engine.applyBatch(impossible)
        }
    }

    private fun journal(device: String) = StudyFlowOperationJournal(
        workspaceId = workspaceId,
        localDeviceId = StudyFlowDeviceId(device),
        store = InMemoryStudyFlowOperationJournalStore(),
    )

    private fun concurrentOperation(
        id: String,
        origin: String,
        logicalTimestamp: Long,
        type: String = "goal.changed",
    ) = operation(id, origin, 1L, "shared-entity", 1L, logicalTimestamp, type)

    private fun operation(
        id: String,
        origin: String,
        originSequence: Long,
        entity: String,
        entityRevision: Long,
        logicalTimestamp: Long,
        type: String = "test.changed",
    ) = StudyFlowDomainOperation(
        operationId = StudyFlowOperationId(id),
        workspaceId = workspaceId,
        originDeviceId = StudyFlowDeviceId(origin),
        originSequence = originSequence,
        entityId = StudyFlowEntityId(entity),
        entityRevision = StudyFlowEntityRevision(entityRevision),
        logicalTimestamp = logicalTimestamp,
        operationType = type,
        occurredAtEpochMillis = logicalTimestamp * 100L,
    )
}
