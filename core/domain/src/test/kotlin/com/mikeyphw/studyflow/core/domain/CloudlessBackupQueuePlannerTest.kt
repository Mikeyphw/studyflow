package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.LocalOnlyProvider
import com.mikeyphw.studyflow.core.model.StudySyncChangeSummary
import com.mikeyphw.studyflow.core.model.StudySyncConflictPolicy
import com.mikeyphw.studyflow.core.model.StudySyncConnectionStatus
import com.mikeyphw.studyflow.core.model.StudySyncState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudlessBackupQueuePlannerTest {
    @Test
    fun queuePreparesCheckpointAndBackupWhenTokenChanged() {
        val plan = CloudlessBackupQueuePlanner.plan(
            state = syncState(lastToken = "old", pending = 3),
            summary = summary(token = "new", plans = 2),
            nowEpochMillis = NOW,
        )

        assertTrue(plan.actionable)
        assertEquals(0, plan.blockedCount)
        assertTrue(plan.items.any { it.action == CloudlessBackupQueueAction.PREPARE_CHECKPOINT })
        assertTrue(plan.items.any { it.action == CloudlessBackupQueueAction.EXPORT_LOCAL_BACKUP })
        assertTrue(plan.items.any { it.action == CloudlessBackupQueueAction.REVIEW_LOCAL_CONFLICT_POLICY })
    }

    @Test
    fun queueWaitsWhenThereIsNoLocalData() {
        val plan = CloudlessBackupQueuePlanner.plan(
            state = syncState(lastToken = null, pending = 0),
            summary = summary(token = "empty", plans = 0),
            nowEpochMillis = NOW,
        )

        assertFalse(plan.actionable)
        assertEquals(1, plan.waitingCount)
        assertEquals(CloudlessBackupQueueAction.WAIT_FOR_LOCAL_DATA, plan.items.single().action)
    }

    @Test
    fun queueBlocksWhenCheckpointIsBlocked() {
        val plan = CloudlessBackupQueuePlanner.plan(
            state = syncState(
                lastToken = "same",
                pending = 0,
                status = StudySyncConnectionStatus.CHECKPOINT_BLOCKED,
            ),
            summary = summary(token = "same", plans = 1),
            nowEpochMillis = NOW,
        )

        assertFalse(plan.actionable)
        assertEquals(1, plan.blockedCount)
        assertEquals(CloudlessBackupQueueAction.UNBLOCK_CHECKPOINT, plan.items.single().action)
    }

    private fun syncState(
        lastToken: String?,
        pending: Int,
        status: StudySyncConnectionStatus = StudySyncConnectionStatus.LOCAL_READY,
    ) = StudySyncState(
        enabled = true,
        provider = LocalOnlyProvider,
        connectionStatus = status,
        conflictPolicy = StudySyncConflictPolicy.MANUAL_REVIEW,
        deviceName = "device",
        lastPreparedAtEpochMillis = NOW - 1_000L,
        lastLocalChangeToken = lastToken,
        pendingLocalChangeCount = pending,
        blockedReason = null,
    )

    private fun summary(token: String, plans: Int) = StudySyncChangeSummary(
        planCount = plans,
        activePlanCount = plans,
        latestUpdatedAtEpochMillis = NOW,
        localChangeToken = token,
    )

    private companion object {
        const val NOW = 1_000_000L
    }
}
