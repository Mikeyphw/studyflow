package com.mikeyphw.studyflow.core.link

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowOperationJournalTest {
    private val workspaceId = StudyFlowWorkspaceId("workspace-main")
    private val localDeviceId = StudyFlowDeviceId("android-tablet")

    @Test
    fun localOperationsAdvanceOriginEntityAndLogicalRevisions() {
        val journal = journal()
        val entityId = StudyFlowEntityId("goal-1")
        val first = journal.appendLocalMutation(
            operationId = StudyFlowOperationId("op-1"),
            entityId = entityId,
            operationType = "goal.created",
            occurredAtEpochMillis = 100L,
        )
        val second = journal.appendLocalMutation(
            operationId = StudyFlowOperationId("op-2"),
            entityId = entityId,
            operationType = "goal.progressed",
            occurredAtEpochMillis = 200L,
        )

        assertEquals(1L, first.originSequence)
        assertEquals(2L, second.originSequence)
        assertEquals(1L, first.entityRevision.value)
        assertEquals(2L, second.entityRevision.value)
        assertEquals(1L, first.logicalTimestamp)
        assertEquals(2L, second.logicalTimestamp)
        assertEquals(2L, journal.frontier().sequenceFor(localDeviceId))
    }

    @Test
    fun duplicateOperationIsIdempotentButConflictingReuseIsRejected() {
        val journal = journal()
        val operation = operation(
            id = "remote-op-1",
            origin = "desktop",
            originSequence = 1L,
            entity = "topic-1",
            entityRevision = 1L,
            logicalTimestamp = 4L,
        )
        assertEquals(StudyFlowJournalAppendStatus.ADDED, journal.append(operation).status)
        assertEquals(StudyFlowJournalAppendStatus.DUPLICATE, journal.append(operation).status)

        assertThrows(StudyFlowJournalIntegrityException::class.java) {
            journal.append(operation.copy(logicalTimestamp = 5L))
        }
        assertEquals(1, journal.entries().size)
    }

    @Test
    fun oneOriginCannotReuseASequenceForAnotherOperation() {
        val journal = journal()
        journal.append(
            operation(
                id = "remote-op-1",
                origin = "web",
                originSequence = 1L,
                entity = "plan-1",
                entityRevision = 1L,
                logicalTimestamp = 1L,
            ),
        )
        assertThrows(StudyFlowJournalIntegrityException::class.java) {
            journal.append(
                operation(
                    id = "remote-op-2",
                    origin = "web",
                    originSequence = 1L,
                    entity = "plan-2",
                    entityRevision = 1L,
                    logicalTimestamp = 2L,
                ),
            )
        }
    }

    @Test
    fun frontierOnlyAdvancesAcrossContiguousOriginSequences() {
        val journal = journal()
        journal.append(
            operation(
                id = "op-1",
                origin = "web",
                originSequence = 1L,
                entity = "q-1",
                entityRevision = 1L,
                logicalTimestamp = 1L,
            ),
        )
        journal.append(
            operation(
                id = "op-3",
                origin = "web",
                originSequence = 3L,
                entity = "q-3",
                entityRevision = 1L,
                logicalTimestamp = 3L,
            ),
        )
        assertEquals(1L, journal.frontier().sequenceFor(StudyFlowDeviceId("web")))

        journal.append(
            operation(
                id = "op-2",
                origin = "web",
                originSequence = 2L,
                entity = "q-2",
                entityRevision = 1L,
                logicalTimestamp = 2L,
            ),
        )
        assertEquals(3L, journal.frontier().sequenceFor(StudyFlowDeviceId("web")))
    }

    @Test
    fun deterministicReplayDoesNotDependOnAppendArrivalOrder() {
        val first = journal()
        val second = journal()
        val operations = listOf(
            operation("op-a", "web", 1L, "a", 1L, 8L),
            operation("op-b", "android-phone", 1L, "b", 1L, 7L),
            operation("op-c", "desktop", 1L, "c", 1L, 8L),
        )
        operations.forEach(first::append)
        operations.reversed().forEach(second::append)

        assertEquals(first.replay(), second.replay())
        assertEquals(listOf("op-b", "op-c", "op-a"), first.replay().map { it.operationId.value })
    }

    @Test
    fun missingDiscoveryDoesNotAdvertiseOperationsBeyondAnOriginGap() {
        val journal = journal()
        journal.append(operation("op-1", "web", 1L, "a", 1L, 1L))
        journal.append(operation("op-3", "web", 3L, "c", 1L, 3L))

        assertTrue(journal.missingFrom(StudyFlowJournalFrontier(), 10).all { it.originSequence == 1L })

        journal.append(operation("op-2", "web", 2L, "b", 1L, 2L))
        assertEquals(
            listOf(1L, 2L, 3L),
            journal.missingFrom(StudyFlowJournalFrontier(), 10).map { it.originSequence },
        )
    }

    @Test
    fun missingOperationDiscoveryUsesPeerFrontierAndBatchLimit() {
        val journal = journal()
        (1L..4L).forEach { sequence ->
            journal.append(
                operation(
                    id = "web-op-$sequence",
                    origin = "web",
                    originSequence = sequence,
                    entity = "entity-$sequence",
                    entityRevision = 1L,
                    logicalTimestamp = sequence,
                ),
            )
        }
        val peer = StudyFlowJournalFrontier(
            listOf(StudyFlowOriginRevision(StudyFlowDeviceId("web"), 1L)),
        )
        assertEquals(
            listOf("web-op-2", "web-op-3"),
            journal.missingFrom(peer, limit = 2).map { it.operationId.value },
        )
    }

    @Test
    fun appendAllPreflightsIntegritySoAFailedBatchDoesNotPartiallyMutateHistory() {
        val journal = journal()
        val valid = operation("op-1", "web", 1L, "a", 1L, 1L)
        val conflicting = operation("op-2", "web", 1L, "b", 1L, 2L)

        assertThrows(StudyFlowJournalIntegrityException::class.java) {
            journal.appendAll(listOf(valid, conflicting))
        }
        assertTrue(journal.entries().isEmpty())
    }

    @Test
    fun checkpointCapturesFrontierWithoutCompactingAppendOnlyHistory() {
        val journal = journal()
        journal.appendLocalMutation(
            operationId = StudyFlowOperationId("op-1"),
            entityId = StudyFlowEntityId("plan-1"),
            operationType = "plan.created",
            occurredAtEpochMillis = 100L,
        )
        val checkpoint = journal.createCheckpoint(
            checkpointId = StudyFlowCheckpointId("cp-1"),
            createdAtEpochMillis = 200L,
            state = buildJsonObject { put("planCount", 1) },
        )
        journal.appendLocalMutation(
            operationId = StudyFlowOperationId("op-2"),
            entityId = StudyFlowEntityId("plan-1"),
            operationType = "plan.renamed",
            occurredAtEpochMillis = 300L,
        )

        assertEquals(1L, checkpoint.operationCount)
        assertEquals(1L, checkpoint.frontier.sequenceFor(localDeviceId))
        assertEquals(checkpoint, journal.latestCheckpoint())
        assertEquals(2, journal.entries().size)
        assertEquals(listOf("op-2"), journal.replayAfter(checkpoint).map { it.operationId.value })
    }

    @Test
    fun tombstonesRemainFirstClassReplayableOperations() {
        val journal = journal()
        val entityId = StudyFlowEntityId("material-1")
        journal.appendLocalMutation(
            operationId = StudyFlowOperationId("op-create"),
            entityId = entityId,
            operationType = "material.created",
            occurredAtEpochMillis = 100L,
        )
        val tombstone = journal.appendLocalTombstone(
            operationId = StudyFlowOperationId("op-delete"),
            entityId = entityId,
            operationType = "material.deleted",
            occurredAtEpochMillis = 200L,
            payload = buildJsonObject { put("reason", "user") },
        )

        assertEquals(StudyFlowOperationKind.TOMBSTONE, tombstone.kind)
        assertEquals(2, journal.replay().size)
        assertTrue(journal.replay().contains(tombstone))
    }

    private fun journal() = StudyFlowOperationJournal(
        workspaceId = workspaceId,
        localDeviceId = localDeviceId,
        store = InMemoryStudyFlowOperationJournalStore(),
    )

    private fun operation(
        id: String,
        origin: String,
        originSequence: Long,
        entity: String,
        entityRevision: Long,
        logicalTimestamp: Long,
    ) = StudyFlowDomainOperation(
        operationId = StudyFlowOperationId(id),
        workspaceId = workspaceId,
        originDeviceId = StudyFlowDeviceId(origin),
        originSequence = originSequence,
        entityId = StudyFlowEntityId(entity),
        entityRevision = StudyFlowEntityRevision(entityRevision),
        logicalTimestamp = logicalTimestamp,
        operationType = "test.changed",
        occurredAtEpochMillis = logicalTimestamp * 100L,
    )
}
