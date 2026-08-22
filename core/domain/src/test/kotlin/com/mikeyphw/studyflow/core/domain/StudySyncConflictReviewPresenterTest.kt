package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.StudySyncConflictResolutionChoice
import com.mikeyphw.studyflow.core.model.StudySyncConflictReviewPresenter
import com.mikeyphw.studyflow.core.model.StudySyncEntityVersion
import com.mikeyphw.studyflow.core.model.StudySyncMergeConflictReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudySyncConflictReviewPresenterTest {
    @Test
    fun `manual conflicts become review items with all user choices`() {
        val base = version(hash = "base", updatedAt = 1L)
        val local = base.copy(contentHash = "local", updatedAtEpochMillis = 2L)
        val remote = base.copy(contentHash = "remote", updatedAtEpochMillis = 3L)
        val plan = StudySyncMergeEngine.plan(local = listOf(local), remote = listOf(remote), base = listOf(base))

        val item = StudySyncConflictReviewPresenter.fromPlan(plan).single()

        assertEquals("studyPlanId:plan-1", item.stableKey)
        assertEquals(StudySyncMergeConflictReason.BOTH_CHANGED, item.reason)
        assertEquals(2L, item.localUpdatedAtEpochMillis)
        assertEquals(3L, item.remoteUpdatedAtEpochMillis)
        assertTrue(StudySyncConflictResolutionChoice.KEEP_LOCAL in item.allowedChoices)
        assertTrue(StudySyncConflictResolutionChoice.USE_REMOTE in item.allowedChoices)
        assertTrue(StudySyncConflictResolutionChoice.KEEP_BOTH in item.allowedChoices)
    }

    private fun version(
        hash: String,
        updatedAt: Long,
    ) = StudySyncEntityVersion(
        entityType = "studyPlanId",
        entityId = "plan-1",
        updatedAtEpochMillis = updatedAt,
        contentHash = hash,
    )
}
