package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.StudySyncChangeSummary
import com.mikeyphw.studyflow.core.model.StudySyncConnectionStatus
import com.mikeyphw.studyflow.core.model.StudySyncConflictPolicy
import com.mikeyphw.studyflow.core.model.StudySyncState

/**
 * Phase 58 keeps sync deliberately cloudless. This planner describes local backup/checkpoint work
 * that can be shown to the learner or scheduled by app-owned background code without accounts.
 */
enum class CloudlessBackupQueueAction {
    PREPARE_CHECKPOINT,
    EXPORT_LOCAL_BACKUP,
    VERIFY_BACKUP_FRESHNESS,
    REVIEW_LOCAL_CONFLICT_POLICY,
    WAIT_FOR_LOCAL_DATA,
    UNBLOCK_CHECKPOINT,
}

enum class CloudlessBackupQueueStatus {
    READY,
    WAITING,
    BLOCKED,
}

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
        require(id.isNotBlank()) { "Queue item id cannot be blank." }
        require(priority in 0..100) { "Queue priority must be between 0 and 100." }
        require(pendingChangeCount >= 0) { "Pending change count cannot be negative." }
        require(dueAtEpochMillis >= 0L) { "Queue due time cannot be negative." }
    }
}

data class CloudlessBackupQueuePlan(
    val generatedAtEpochMillis: Long,
    val items: List<CloudlessBackupQueueItem>,
) {
    init {
        require(generatedAtEpochMillis >= 0L) { "Queue generation time cannot be negative." }
    }

    val readyCount: Int = items.count { it.status == CloudlessBackupQueueStatus.READY }
    val blockedCount: Int = items.count { it.status == CloudlessBackupQueueStatus.BLOCKED }
    val waitingCount: Int = items.count { it.status == CloudlessBackupQueueStatus.WAITING }
    val highestPriority: Int = items.maxOfOrNull(CloudlessBackupQueueItem::priority) ?: 0
    val actionable: Boolean = readyCount > 0 && blockedCount == 0
}

object CloudlessBackupQueuePlanner {
    fun plan(
        state: StudySyncState,
        summary: StudySyncChangeSummary,
        nowEpochMillis: Long,
        lastExportedAtEpochMillis: Long? = state.lastPreparedAtEpochMillis,
    ): CloudlessBackupQueuePlan {
        require(nowEpochMillis >= 0L) { "Queue inspection time cannot be negative." }
        val items = buildList {
            if (!summary.hasLocalData) {
                add(
                    item(
                        action = CloudlessBackupQueueAction.WAIT_FOR_LOCAL_DATA,
                        status = CloudlessBackupQueueStatus.WAITING,
                        priority = 10,
                        summary = summary,
                        dueAtEpochMillis = nowEpochMillis,
                    ),
                )
                return@buildList
            }
            if (state.connectionStatus == StudySyncConnectionStatus.CHECKPOINT_BLOCKED) {
                add(
                    item(
                        action = CloudlessBackupQueueAction.UNBLOCK_CHECKPOINT,
                        status = CloudlessBackupQueueStatus.BLOCKED,
                        priority = 100,
                        summary = summary,
                        dueAtEpochMillis = nowEpochMillis,
                    ),
                )
                return@buildList
            }
            if (!StudyLocalCheckpointReadinessAuditor.localCheckpointReady(state, summary)) {
                add(
                    item(
                        action = CloudlessBackupQueueAction.PREPARE_CHECKPOINT,
                        status = CloudlessBackupQueueStatus.READY,
                        priority = 90,
                        summary = summary,
                        dueAtEpochMillis = nowEpochMillis,
                    ),
                )
            }
            if (summary.localChangeToken != state.lastLocalChangeToken || state.pendingLocalChangeCount > 0) {
                add(
                    item(
                        action = CloudlessBackupQueueAction.EXPORT_LOCAL_BACKUP,
                        status = CloudlessBackupQueueStatus.READY,
                        priority = 80,
                        summary = summary,
                        dueAtEpochMillis = nowEpochMillis + QUICK_BACKUP_DELAY_MILLIS,
                    ),
                )
            }
            if (state.conflictPolicy == StudySyncConflictPolicy.MANUAL_REVIEW && summary.activePlanCount > 1) {
                add(
                    item(
                        action = CloudlessBackupQueueAction.REVIEW_LOCAL_CONFLICT_POLICY,
                        status = CloudlessBackupQueueStatus.READY,
                        priority = 55,
                        summary = summary,
                        dueAtEpochMillis = nowEpochMillis + REVIEW_POLICY_DELAY_MILLIS,
                    ),
                )
            }
            val backupStale = lastExportedAtEpochMillis == null ||
                nowEpochMillis - lastExportedAtEpochMillis >= BACKUP_STALE_MILLIS
            if (backupStale) {
                add(
                    item(
                        action = CloudlessBackupQueueAction.VERIFY_BACKUP_FRESHNESS,
                        status = CloudlessBackupQueueStatus.READY,
                        priority = 45,
                        summary = summary,
                        dueAtEpochMillis = nowEpochMillis + FRESHNESS_CHECK_DELAY_MILLIS,
                    ),
                )
            }
            if (isEmpty()) {
                add(
                    item(
                        action = CloudlessBackupQueueAction.VERIFY_BACKUP_FRESHNESS,
                        status = CloudlessBackupQueueStatus.WAITING,
                        priority = 20,
                        summary = summary,
                        dueAtEpochMillis = nowEpochMillis + BACKUP_STALE_MILLIS,
                    ),
                )
            }
        }
        return CloudlessBackupQueuePlan(
            generatedAtEpochMillis = nowEpochMillis,
            items = items.sortedWith(
                compareByDescending<CloudlessBackupQueueItem> { it.priority }
                    .thenBy { it.dueAtEpochMillis }
                    .thenBy { it.id },
            ),
        )
    }

    private fun item(
        action: CloudlessBackupQueueAction,
        status: CloudlessBackupQueueStatus,
        priority: Int,
        summary: StudySyncChangeSummary,
        dueAtEpochMillis: Long,
    ): CloudlessBackupQueueItem = CloudlessBackupQueueItem(
        id = "cloudless:${action.name.lowercase()}:${summary.localChangeToken}",
        action = action,
        status = status,
        priority = priority,
        pendingChangeCount = summary.changeCount,
        localChangeToken = summary.localChangeToken,
        dueAtEpochMillis = dueAtEpochMillis,
    )

    private const val QUICK_BACKUP_DELAY_MILLIS = 15 * 60 * 1000L
    private const val REVIEW_POLICY_DELAY_MILLIS = 60 * 60 * 1000L
    private const val FRESHNESS_CHECK_DELAY_MILLIS = 2 * 60 * 60 * 1000L
    private const val BACKUP_STALE_MILLIS = 7 * 24 * 60 * 60 * 1000L
}
