package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.StudySyncChangeSummary
import com.mikeyphw.studyflow.core.model.StudySyncConnectionStatus
import com.mikeyphw.studyflow.core.model.StudySyncConflictPolicy
import com.mikeyphw.studyflow.core.model.StudySyncState

/**
 * Compatibility-named model for a local checkpoint *readiness list*. It is not a durable work queue,
 * does not claim that an export was scheduled, and never substitutes checkpoint preparation for an
 * actual Study Plan export.
 */
enum class CloudlessBackupQueueAction {
    PREPARE_CHECKPOINT,
    CHECKPOINT_CURRENT,
    REVIEW_LOCAL_CONFLICT_POLICY,
    WAIT_FOR_LOCAL_DATA,
    UNBLOCK_CHECKPOINT,
}

enum class CloudlessBackupQueueStatus { READY, WAITING, BLOCKED }

data class CloudlessBackupQueueItem(
    val id: String,
    val action: CloudlessBackupQueueAction,
    val status: CloudlessBackupQueueStatus,
    val priority: Int,
    val pendingChangeCount: Int,
    val localChangeToken: String?,
    val dueAtEpochMillis: Long,
) {
    init {
        require(id.isNotBlank()) { "Readiness item id cannot be blank." }
        require(priority in 0..100) { "Readiness priority must be between 0 and 100." }
        require(pendingChangeCount >= 0) { "Pending change count cannot be negative." }
        require(dueAtEpochMillis >= 0L) { "Readiness inspection time cannot be negative." }
    }
}

data class CloudlessBackupQueuePlan(
    val generatedAtEpochMillis: Long,
    val items: List<CloudlessBackupQueueItem>,
) {
    val readyCount = items.count { it.status == CloudlessBackupQueueStatus.READY }
    val blockedCount = items.count { it.status == CloudlessBackupQueueStatus.BLOCKED }
    val waitingCount = items.count { it.status == CloudlessBackupQueueStatus.WAITING }
    val highestPriority = items.maxOfOrNull(CloudlessBackupQueueItem::priority) ?: 0
    val actionable = readyCount > 0 && blockedCount == 0
}

object CloudlessBackupQueuePlanner {
    fun plan(
        state: StudySyncState,
        summary: StudySyncChangeSummary,
        nowEpochMillis: Long,
        @Suppress("UNUSED_PARAMETER") lastExportedAtEpochMillis: Long? = null,
    ): CloudlessBackupQueuePlan {
        require(nowEpochMillis >= 0L) { "Readiness inspection time cannot be negative." }
        val items = buildList {
            if (!summary.hasLocalData) {
                add(item(CloudlessBackupQueueAction.WAIT_FOR_LOCAL_DATA, CloudlessBackupQueueStatus.WAITING, 10, state, summary, nowEpochMillis))
                return@buildList
            }
            if (state.connectionStatus == StudySyncConnectionStatus.CHECKPOINT_BLOCKED) {
                add(item(CloudlessBackupQueueAction.UNBLOCK_CHECKPOINT, CloudlessBackupQueueStatus.BLOCKED, 100, state, summary, nowEpochMillis))
                return@buildList
            }
            if (!StudyLocalCheckpointReadinessAuditor.localCheckpointReady(state, summary)) {
                add(item(CloudlessBackupQueueAction.PREPARE_CHECKPOINT, CloudlessBackupQueueStatus.READY, 90, state, summary, nowEpochMillis))
            } else {
                add(item(CloudlessBackupQueueAction.CHECKPOINT_CURRENT, CloudlessBackupQueueStatus.WAITING, 20, state, summary, nowEpochMillis))
            }
            if (state.conflictPolicy == StudySyncConflictPolicy.MANUAL_REVIEW && summary.activePlanCount > 1) {
                add(item(CloudlessBackupQueueAction.REVIEW_LOCAL_CONFLICT_POLICY, CloudlessBackupQueueStatus.READY, 55, state, summary, nowEpochMillis))
            }
        }
        return CloudlessBackupQueuePlan(
            generatedAtEpochMillis = nowEpochMillis,
            items = items.sortedWith(compareByDescending<CloudlessBackupQueueItem> { it.priority }.thenBy { it.id }),
        )
    }

    private fun item(
        action: CloudlessBackupQueueAction,
        status: CloudlessBackupQueueStatus,
        priority: Int,
        state: StudySyncState,
        summary: StudySyncChangeSummary,
        nowEpochMillis: Long,
    ) = CloudlessBackupQueueItem(
        id = "checkpoint:${action.name.lowercase()}:${summary.localChangeToken}",
        action = action,
        status = status,
        priority = priority,
        pendingChangeCount = state.pendingLocalChangeCount,
        localChangeToken = summary.localChangeToken,
        dueAtEpochMillis = nowEpochMillis,
    )
}
