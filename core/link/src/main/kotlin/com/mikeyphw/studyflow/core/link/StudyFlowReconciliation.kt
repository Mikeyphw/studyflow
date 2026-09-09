package com.mikeyphw.studyflow.core.link

import kotlinx.serialization.Serializable

@Serializable
data class StudyFlowOperationConflict(
    val entityId: StudyFlowEntityId,
    val entityRevision: StudyFlowEntityRevision,
    val operations: List<StudyFlowDomainOperation>,
) {
    init {
        require(operations.size >= 2) { "A conflict must contain at least two operations" }
        require(operations.all { it.entityId == entityId }) { "Conflict operations must target one entity" }
        require(operations.all { it.entityRevision == entityRevision }) {
            "Conflict operations must target one entity revision"
        }
    }
}

fun interface StudyFlowConflictHandler {
    fun resolve(conflict: StudyFlowOperationConflict): List<StudyFlowDomainOperation>
}

object StudyFlowKeepAllConflictHandler : StudyFlowConflictHandler {
    override fun resolve(conflict: StudyFlowOperationConflict): List<StudyFlowDomainOperation> =
        conflict.operations.sortedWith(StudyFlowDomainOperationDeterministicComparator)
}

object StudyFlowDeterministicWinnerConflictHandler : StudyFlowConflictHandler {
    override fun resolve(conflict: StudyFlowOperationConflict): List<StudyFlowDomainOperation> =
        listOf(conflict.operations.maxWith(StudyFlowDomainOperationDeterministicComparator))
}

data class StudyFlowConflictRule(
    val operationTypePrefix: String,
    val handler: StudyFlowConflictHandler,
) {
    init {
        require(operationTypePrefix.isNotBlank()) { "Conflict rule prefix must not be blank" }
    }
}

class StudyFlowConflictHandlerRegistry(
    private val defaultHandler: StudyFlowConflictHandler = StudyFlowDeterministicWinnerConflictHandler,
    rules: List<StudyFlowConflictRule> = emptyList(),
) {
    private val rules = rules.sortedByDescending { it.operationTypePrefix.length }

    fun resolve(conflict: StudyFlowOperationConflict): List<StudyFlowDomainOperation> {
        val matching = rules.firstOrNull { rule ->
            conflict.operations.all { it.operationType.startsWith(rule.operationTypePrefix) }
        }
        val resolved = (matching?.handler ?: defaultHandler).resolve(conflict)
        require(resolved.isNotEmpty()) { "Conflict handler must retain at least one operation" }
        require(resolved.all { it in conflict.operations }) {
            "Conflict handler may only return operations from the conflict"
        }
        return resolved.distinctBy(StudyFlowDomainOperation::operationId)
    }
}

@Serializable
data class StudyFlowResolvedReplay(
    val operations: List<StudyFlowDomainOperation>,
    val conflicts: List<StudyFlowOperationConflict>,
)

class StudyFlowSyncContinuationException(message: String) : IllegalStateException(message)

class StudyFlowSyncBatchIntegrityException(message: String) : IllegalStateException(message)

@Serializable
data class StudyFlowSyncApplyResult(
    val added: Int,
    val duplicates: Int,
    val resultingFrontier: StudyFlowJournalFrontier,
    val conflicts: List<StudyFlowOperationConflict>,
)

class StudyFlowReconciliationEngine(
    private val journal: StudyFlowOperationJournal,
    private val conflictHandlers: StudyFlowConflictHandlerRegistry = StudyFlowConflictHandlerRegistry(),
) {
    fun currentFrontier(): StudyFlowJournalFrontier = journal.frontier()

    fun nextBatch(
        request: StudyFlowSyncPullRequest,
        batchId: StudyFlowMessageId,
    ): StudyFlowSyncOperationsBatch {
        require(request.workspaceId == journal.workspaceId) { "Pull request belongs to another workspace" }
        val operations = journal.missingFrom(request.knownFrontier, request.maxOperations)
        val throughFrontier = advanceFrontier(request.knownFrontier, operations)
        val hasMore = journal.missingFrom(throughFrontier, 1).isNotEmpty()
        return StudyFlowSyncOperationsBatch(
            batchId = batchId,
            workspaceId = journal.workspaceId,
            baseFrontier = request.knownFrontier.normalized(),
            operations = operations,
            throughFrontier = throughFrontier,
            hasMore = hasMore,
        )
    }

    fun applyBatch(batch: StudyFlowSyncOperationsBatch): StudyFlowSyncApplyResult {
        require(batch.workspaceId == journal.workspaceId) { "Operation batch belongs to another workspace" }
        val localBefore = journal.frontier()
        if (!localBefore.dominates(batch.baseFrontier)) {
            throw StudyFlowSyncContinuationException(
                "Local frontier does not cover the batch base; restart from the receiver frontier",
            )
        }
        val declaredThrough = batch.throughFrontier.normalized()
        val expectedThrough = advanceFrontier(batch.baseFrontier, batch.operations)
        if (expectedThrough != declaredThrough) {
            throw StudyFlowSyncBatchIntegrityException(
                "Operation batch through-frontier does not match its operations",
            )
        }
        val summary = journal.appendAll(batch.operations)
        val resulting = journal.frontier()
        if (!resulting.dominates(batch.throughFrontier)) {
            throw StudyFlowSyncBatchIntegrityException(
                "Applied operation batch did not reach its declared through-frontier",
            )
        }
        val replay = resolvedReplay()
        return StudyFlowSyncApplyResult(
            added = summary.added,
            duplicates = summary.duplicates,
            resultingFrontier = resulting,
            conflicts = replay.conflicts,
        )
    }

    fun resolvedReplay(): StudyFlowResolvedReplay {
        val deterministic = journal.replay()
        val grouped = deterministic.groupBy { it.entityId to it.entityRevision }
        val conflicts = mutableListOf<StudyFlowOperationConflict>()
        val resolved = mutableListOf<StudyFlowDomainOperation>()
        grouped.values.forEach { candidates ->
            if (candidates.size == 1) {
                resolved += candidates.single()
            } else {
                val first = candidates.first()
                val conflict = StudyFlowOperationConflict(
                    entityId = first.entityId,
                    entityRevision = first.entityRevision,
                    operations = candidates.sortedWith(StudyFlowDomainOperationDeterministicComparator),
                )
                conflicts += conflict
                resolved += conflictHandlers.resolve(conflict)
            }
        }
        return StudyFlowResolvedReplay(
            operations = resolved.sortedWith(StudyFlowDomainOperationDeterministicComparator),
            conflicts = conflicts.sortedWith(
                compareBy<StudyFlowOperationConflict>(
                    { it.entityId.value },
                    { it.entityRevision.value },
                ),
            ),
        )
    }

    private fun advanceFrontier(
        base: StudyFlowJournalFrontier,
        operations: List<StudyFlowDomainOperation>,
    ): StudyFlowJournalFrontier {
        val sequencesByOrigin = mutableMapOf<StudyFlowDeviceId, MutableSet<Long>>()
        operations.forEach { operation ->
            sequencesByOrigin.getOrPut(operation.originDeviceId) { mutableSetOf() }.add(operation.originSequence)
        }
        val allOrigins = (base.origins.map(StudyFlowOriginRevision::deviceId) + sequencesByOrigin.keys).distinct()
        return StudyFlowJournalFrontier(
            allOrigins.mapNotNull { deviceId ->
                var contiguous = base.sequenceFor(deviceId)
                val available = sequencesByOrigin[deviceId].orEmpty()
                while ((contiguous + 1L) in available) contiguous += 1L
                if (contiguous == 0L) null else StudyFlowOriginRevision(deviceId, contiguous)
            },
        ).normalized()
    }
}
