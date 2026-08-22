package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.StudySyncEntityState
import com.mikeyphw.studyflow.core.model.StudySyncEntityVersion
import com.mikeyphw.studyflow.core.model.StudySyncMergeAction
import com.mikeyphw.studyflow.core.model.StudySyncMergeConflictReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudySyncMergeEngineTest {
    @Test
    fun `local only entity stays local and remote only entity imports`() {
        val local = version(id = "plan-local", hash = "local")
        val remote = version(id = "plan-remote", hash = "remote")
        val plan = StudySyncMergeEngine.plan(local = listOf(local), remote = listOf(remote))

        assertEquals(StudySyncMergeAction.KEEP_LOCAL, plan.decisions.single { it.entityId == "plan-local" }.action)
        assertEquals(StudySyncMergeAction.IMPORT_REMOTE, plan.decisions.single { it.entityId == "plan-remote" }.action)
        assertTrue(plan.canApplyAutomatically)
    }

    @Test
    fun `one sided remote change can be applied without conflict`() {
        val base = version(hash = "base", updatedAt = 1L)
        val local = base.copy(updatedAtEpochMillis = 2L)
        val remote = base.copy(contentHash = "remote", updatedAtEpochMillis = 3L)
        val decision = StudySyncMergeEngine.decide(local = local, remote = remote, base = base)

        assertEquals(StudySyncMergeAction.USE_REMOTE, decision.action)
        assertFalse(decision.blocksAutomaticApply)
    }

    @Test
    fun `both changed uses manual conflict instead of silent overwrite`() {
        val base = version(hash = "base", updatedAt = 1L)
        val local = base.copy(contentHash = "local", updatedAtEpochMillis = 2L)
        val remote = base.copy(contentHash = "remote", updatedAtEpochMillis = 3L)
        val decision = StudySyncMergeEngine.decide(local = local, remote = remote, base = base)

        assertEquals(StudySyncMergeAction.MANUAL_CONFLICT, decision.action)
        assertEquals(StudySyncMergeConflictReason.BOTH_CHANGED, decision.conflict!!.reason)
        assertTrue(decision.blocksAutomaticApply)
    }

    @Test
    fun `delete versus change becomes explicit conflict`() {
        val base = version(hash = "base", updatedAt = 1L)
        val local = base.copy(
            contentHash = "deleted-local",
            state = StudySyncEntityState.DELETED,
            updatedAtEpochMillis = 2L,
        )
        val remote = base.copy(contentHash = "remote-change", updatedAtEpochMillis = 3L)
        val decision = StudySyncMergeEngine.decide(local = local, remote = remote, base = base)

        assertEquals(StudySyncMergeAction.MANUAL_CONFLICT, decision.action)
        assertEquals(StudySyncMergeConflictReason.DELETED_LOCALLY_CHANGED_REMOTELY, decision.conflict!!.reason)
    }

    private fun version(
        type: String = "studyPlanId",
        id: String = "plan-1",
        hash: String,
        updatedAt: Long = 1L,
        state: StudySyncEntityState = StudySyncEntityState.PRESENT,
    ) = StudySyncEntityVersion(
        entityType = type,
        entityId = id,
        updatedAtEpochMillis = updatedAt,
        contentHash = hash,
        state = state,
    )
}
