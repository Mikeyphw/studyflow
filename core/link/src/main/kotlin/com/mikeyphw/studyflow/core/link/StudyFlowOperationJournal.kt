package com.mikeyphw.studyflow.core.link

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
@JvmInline
value class StudyFlowCheckpointId(val value: String) {
    init {
        require(value.isNotBlank()) { "Checkpoint ID must not be blank" }
        require(value.length <= 128) { "Checkpoint ID is too long" }
    }
}

@Serializable
data class StudyFlowOriginRevision(
    val deviceId: StudyFlowDeviceId,
    val contiguousSequence: Long,
) {
    init {
        require(contiguousSequence >= 0L) { "Contiguous sequence must not be negative" }
    }
}

@Serializable
data class StudyFlowJournalFrontier(
    val origins: List<StudyFlowOriginRevision> = emptyList(),
) {
    init {
        require(origins.map { it.deviceId }.distinct().size == origins.size) {
            "Frontier must contain each origin device at most once"
        }
    }

    fun sequenceFor(deviceId: StudyFlowDeviceId): Long =
        origins.firstOrNull { it.deviceId == deviceId }?.contiguousSequence ?: 0L

    fun covers(operation: StudyFlowDomainOperation): Boolean =
        sequenceFor(operation.originDeviceId) >= operation.originSequence

    fun dominates(other: StudyFlowJournalFrontier): Boolean =
        other.origins.all { sequenceFor(it.deviceId) >= it.contiguousSequence }

    fun normalized(): StudyFlowJournalFrontier =
        copy(origins = origins.sortedBy { it.deviceId.value })
}

@Serializable
data class StudyFlowJournalEntry(
    val localSequence: Long,
    val operation: StudyFlowDomainOperation,
) {
    init {
        require(localSequence >= 1L) { "Local journal sequence must be at least 1" }
    }
}

@Serializable
data class StudyFlowJournalCheckpoint(
    val checkpointId: StudyFlowCheckpointId,
    val workspaceId: StudyFlowWorkspaceId,
    val frontier: StudyFlowJournalFrontier,
    val operationCount: Long,
    val createdAtEpochMillis: Long,
    val state: JsonObject,
) {
    init {
        require(operationCount >= 0L) { "Checkpoint operation count must not be negative" }
        require(createdAtEpochMillis >= 0L) { "Checkpoint timestamp must not be negative" }
    }
}

interface StudyFlowOperationJournalStore {
    fun entries(workspaceId: StudyFlowWorkspaceId): List<StudyFlowJournalEntry>

    fun append(entry: StudyFlowJournalEntry)

    fun checkpoints(workspaceId: StudyFlowWorkspaceId): List<StudyFlowJournalCheckpoint>

    fun appendCheckpoint(checkpoint: StudyFlowJournalCheckpoint)
}

class InMemoryStudyFlowOperationJournalStore : StudyFlowOperationJournalStore {
    private val entriesByWorkspace = linkedMapOf<StudyFlowWorkspaceId, MutableList<StudyFlowJournalEntry>>()
    private val checkpointsByWorkspace =
        linkedMapOf<StudyFlowWorkspaceId, MutableList<StudyFlowJournalCheckpoint>>()

    override fun entries(workspaceId: StudyFlowWorkspaceId): List<StudyFlowJournalEntry> =
        entriesByWorkspace[workspaceId]?.toList().orEmpty()

    override fun append(entry: StudyFlowJournalEntry) {
        val workspaceId = entry.operation.workspaceId
        entriesByWorkspace.getOrPut(workspaceId) { mutableListOf() }.add(entry)
    }

    override fun checkpoints(workspaceId: StudyFlowWorkspaceId): List<StudyFlowJournalCheckpoint> =
        checkpointsByWorkspace[workspaceId]?.toList().orEmpty()

    override fun appendCheckpoint(checkpoint: StudyFlowJournalCheckpoint) {
        checkpointsByWorkspace.getOrPut(checkpoint.workspaceId) { mutableListOf() }.add(checkpoint)
    }
}

enum class StudyFlowJournalAppendStatus {
    ADDED,
    DUPLICATE,
}

data class StudyFlowJournalAppendResult(
    val status: StudyFlowJournalAppendStatus,
    val entry: StudyFlowJournalEntry,
)

data class StudyFlowJournalAppendSummary(
    val added: Int,
    val duplicates: Int,
)

class StudyFlowJournalIntegrityException(message: String) : IllegalStateException(message)

class StudyFlowOperationJournal(
    val workspaceId: StudyFlowWorkspaceId,
    val localDeviceId: StudyFlowDeviceId,
    private val store: StudyFlowOperationJournalStore,
) {
    fun entries(): List<StudyFlowJournalEntry> =
        store.entries(workspaceId).sortedBy(StudyFlowJournalEntry::localSequence)

    fun operations(): List<StudyFlowDomainOperation> = entries().map(StudyFlowJournalEntry::operation)

    fun replay(): List<StudyFlowDomainOperation> =
        operations().sortedWith(StudyFlowDomainOperationDeterministicComparator)

    fun frontier(): StudyFlowJournalFrontier = frontierFor(operations())

    fun latestEntityRevision(entityId: StudyFlowEntityId): StudyFlowEntityRevision =
        StudyFlowEntityRevision(
            operations()
                .asSequence()
                .filter { it.entityId == entityId }
                .maxOfOrNull { it.entityRevision.value }
                ?: 0L,
        )

    fun append(operation: StudyFlowDomainOperation): StudyFlowJournalAppendResult {
        require(operation.workspaceId == workspaceId) { "Operation belongs to a different workspace" }
        val existing = operations().firstOrNull { it.operationId == operation.operationId }
        if (existing != null) {
            if (existing != operation) {
                throw StudyFlowJournalIntegrityException(
                    "Operation ID ${operation.operationId.value} was reused with different content",
                )
            }
            val entry = entries().first { it.operation.operationId == operation.operationId }
            return StudyFlowJournalAppendResult(StudyFlowJournalAppendStatus.DUPLICATE, entry)
        }
        rejectConflictingOriginSequence(operation)
        val entry = StudyFlowJournalEntry(
            localSequence = (entries().lastOrNull()?.localSequence ?: 0L) + 1L,
            operation = operation,
        )
        store.append(entry)
        return StudyFlowJournalAppendResult(StudyFlowJournalAppendStatus.ADDED, entry)
    }

    fun appendAll(operations: Iterable<StudyFlowDomainOperation>): StudyFlowJournalAppendSummary {
        val pending = operations.toList()
        validateBatchBeforeAppend(pending)
        var added = 0
        var duplicates = 0
        pending.forEach { operation ->
            when (append(operation).status) {
                StudyFlowJournalAppendStatus.ADDED -> added += 1
                StudyFlowJournalAppendStatus.DUPLICATE -> duplicates += 1
            }
        }
        return StudyFlowJournalAppendSummary(added = added, duplicates = duplicates)
    }

    fun appendLocalMutation(
        operationId: StudyFlowOperationId,
        entityId: StudyFlowEntityId,
        operationType: String,
        occurredAtEpochMillis: Long,
        payload: JsonObject = JsonObject(emptyMap()),
    ): StudyFlowDomainOperation = appendLocal(
        operationId = operationId,
        entityId = entityId,
        operationType = operationType,
        kind = StudyFlowOperationKind.MUTATION,
        occurredAtEpochMillis = occurredAtEpochMillis,
        payload = payload,
    )

    fun appendLocalTombstone(
        operationId: StudyFlowOperationId,
        entityId: StudyFlowEntityId,
        operationType: String,
        occurredAtEpochMillis: Long,
        payload: JsonObject = JsonObject(emptyMap()),
    ): StudyFlowDomainOperation = appendLocal(
        operationId = operationId,
        entityId = entityId,
        operationType = operationType,
        kind = StudyFlowOperationKind.TOMBSTONE,
        occurredAtEpochMillis = occurredAtEpochMillis,
        payload = payload,
    )

    fun missingFrom(
        peerFrontier: StudyFlowJournalFrontier,
        limit: Int,
    ): List<StudyFlowDomainOperation> {
        require(limit >= 1) { "Missing-operation limit must be at least 1" }
        val localFrontier = frontier()
        return operations()
            .asSequence()
            .filter { it.originSequence > peerFrontier.sequenceFor(it.originDeviceId) }
            .filter { it.originSequence <= localFrontier.sequenceFor(it.originDeviceId) }
            .sortedWith(
                compareBy<StudyFlowDomainOperation>(
                    { it.originDeviceId.value },
                    StudyFlowDomainOperation::originSequence,
                    { it.operationId.value },
                ),
            )
            .take(limit)
            .toList()
    }

    fun createCheckpoint(
        checkpointId: StudyFlowCheckpointId,
        createdAtEpochMillis: Long,
        state: JsonObject,
    ): StudyFlowJournalCheckpoint {
        require(store.checkpoints(workspaceId).none { it.checkpointId == checkpointId }) {
            "Checkpoint ID ${checkpointId.value} already exists"
        }
        val checkpoint = StudyFlowJournalCheckpoint(
            checkpointId = checkpointId,
            workspaceId = workspaceId,
            frontier = frontier(),
            operationCount = operations().size.toLong(),
            createdAtEpochMillis = createdAtEpochMillis,
            state = state,
        )
        store.appendCheckpoint(checkpoint)
        return checkpoint
    }

    fun checkpoints(): List<StudyFlowJournalCheckpoint> =
        store.checkpoints(workspaceId).sortedBy(StudyFlowJournalCheckpoint::createdAtEpochMillis)

    fun latestCheckpoint(): StudyFlowJournalCheckpoint? = checkpoints().lastOrNull()

    fun replayAfter(checkpoint: StudyFlowJournalCheckpoint): List<StudyFlowDomainOperation> {
        require(checkpoint.workspaceId == workspaceId) { "Checkpoint belongs to a different workspace" }
        return entries()
            .asSequence()
            .filter { it.localSequence > checkpoint.operationCount }
            .map(StudyFlowJournalEntry::operation)
            .sortedWith(StudyFlowDomainOperationDeterministicComparator)
            .toList()
    }

    private fun appendLocal(
        operationId: StudyFlowOperationId,
        entityId: StudyFlowEntityId,
        operationType: String,
        kind: StudyFlowOperationKind,
        occurredAtEpochMillis: Long,
        payload: JsonObject,
    ): StudyFlowDomainOperation {
        val existingOperations = operations()
        val operation = StudyFlowDomainOperation(
            operationId = operationId,
            workspaceId = workspaceId,
            originDeviceId = localDeviceId,
            originSequence = existingOperations
                .asSequence()
                .filter { it.originDeviceId == localDeviceId }
                .maxOfOrNull(StudyFlowDomainOperation::originSequence)
                ?.plus(1L)
                ?: 1L,
            entityId = entityId,
            entityRevision = StudyFlowEntityRevision(latestEntityRevision(entityId).value + 1L),
            logicalTimestamp = (existingOperations.maxOfOrNull(StudyFlowDomainOperation::logicalTimestamp) ?: 0L) + 1L,
            operationType = operationType,
            kind = kind,
            occurredAtEpochMillis = occurredAtEpochMillis,
            payload = payload,
        )
        append(operation)
        return operation
    }

    private fun validateBatchBeforeAppend(pending: List<StudyFlowDomainOperation>) {
        val byId = operations().associateBy(StudyFlowDomainOperation::operationId).toMutableMap()
        val byOriginSequence = operations().associateBy { it.originDeviceId to it.originSequence }.toMutableMap()
        pending.forEach { operation ->
            require(operation.workspaceId == workspaceId) { "Operation belongs to a different workspace" }
            val existingId = byId[operation.operationId]
            if (existingId != null && existingId != operation) {
                throw StudyFlowJournalIntegrityException(
                    "Operation ID ${operation.operationId.value} was reused with different content",
                )
            }
            val originKey = operation.originDeviceId to operation.originSequence
            val existingSequence = byOriginSequence[originKey]
            if (existingSequence != null && existingSequence.operationId != operation.operationId) {
                throw StudyFlowJournalIntegrityException(
                    "Origin ${operation.originDeviceId.value} reused sequence ${operation.originSequence}",
                )
            }
            byId.putIfAbsent(operation.operationId, operation)
            byOriginSequence.putIfAbsent(originKey, operation)
        }
    }

    private fun rejectConflictingOriginSequence(operation: StudyFlowDomainOperation) {
        val conflict = operations().firstOrNull {
            it.originDeviceId == operation.originDeviceId &&
                it.originSequence == operation.originSequence &&
                it.operationId != operation.operationId
        }
        if (conflict != null) {
            throw StudyFlowJournalIntegrityException(
                "Origin ${operation.originDeviceId.value} reused sequence ${operation.originSequence}",
            )
        }
    }

    companion object {
        fun frontierFor(operations: Collection<StudyFlowDomainOperation>): StudyFlowJournalFrontier {
            val origins = operations
                .groupBy(StudyFlowDomainOperation::originDeviceId)
                .map { (deviceId, originOperations) ->
                    val available = originOperations.map(StudyFlowDomainOperation::originSequence).toHashSet()
                    var contiguous = 0L
                    while ((contiguous + 1L) in available) contiguous += 1L
                    StudyFlowOriginRevision(deviceId, contiguous)
                }
                .filter { it.contiguousSequence > 0L }
                .sortedBy { it.deviceId.value }
            return StudyFlowJournalFrontier(origins)
        }
    }
}
