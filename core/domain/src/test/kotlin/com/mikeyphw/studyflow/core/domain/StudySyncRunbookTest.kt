package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.LocalFolderSyncProvider
import com.mikeyphw.studyflow.core.model.StudyFlowSyncProviderIds
import com.mikeyphw.studyflow.core.model.StudyFlowSyncSettings
import com.mikeyphw.studyflow.core.model.StudySyncBackgroundMode
import com.mikeyphw.studyflow.core.model.StudySyncChangeSummary
import com.mikeyphw.studyflow.core.model.StudySyncEntityVersion
import com.mikeyphw.studyflow.core.model.StudySyncState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudySyncRunbookTest {
    @Test
    fun `manual upload prepares encrypted bundle before provider upload`() {
        val plan = StudySyncRunbookPlanner.plan(
            intent = StudySyncRunIntent.MANUAL_UPLOAD,
            state = folderState(remoteCount = 0),
            summary = summary(hasLocalData = true),
        )

        assertTrue(plan.canStart)
        assertTrue(StudySyncRunStep.BUILD_ENCRYPTED_BUNDLE in plan.steps)
        assertTrue(StudySyncRunStep.UPLOAD_ENCRYPTED_BUNDLE in plan.steps)
        assertTrue(plan.requiresNetwork)
        assertFalse(plan.appliesLocalData)
    }

    @Test
    fun `restore preview never applies local data and blocks on conflict review`() {
        val mergePlan = StudySyncMergeEngine.plan(
            local = listOf(entity("local-hash", 20L)),
            remote = listOf(entity("remote-hash", 30L)),
            base = listOf(entity("base-hash", 10L)),
        )
        val plan = StudySyncRunbookPlanner.plan(
            intent = StudySyncRunIntent.RESTORE_PREVIEW,
            state = folderState(remoteCount = 1),
            restoreMergePlan = mergePlan,
        )

        assertFalse(plan.canStart)
        assertTrue(StudySyncRunBlocker.CONFLICT_REVIEW_REQUIRED in plan.blockers)
        assertTrue(StudySyncRunStep.DRY_RUN_RESTORE in plan.steps)
        assertTrue(StudySyncRunStep.REQUIRE_CONFLICT_REVIEW in plan.steps)
        assertFalse(plan.appliesLocalData)
    }

    @Test
    fun `background upload requires matching enabled background mode`() {
        val manual = StudySyncRunbookPlanner.plan(
            intent = StudySyncRunIntent.BACKGROUND_UPLOAD_AFTER_CHANGES,
            state = folderState(remoteCount = 0),
            summary = summary(hasLocalData = true),
        )

        assertFalse(manual.canStart)
        assertTrue(StudySyncRunBlocker.BACKGROUND_MODE_DISABLED in manual.blockers)
        assertTrue(StudySyncRunBlocker.BACKGROUND_MODE_MISMATCH in manual.blockers)

        val automatic = StudySyncRunbookPlanner.plan(
            intent = StudySyncRunIntent.BACKGROUND_UPLOAD_AFTER_CHANGES,
            state = folderState(
                remoteCount = 0,
                backgroundMode = StudySyncBackgroundMode.UPLOAD_AFTER_CHANGES,
            ),
            summary = summary(hasLocalData = true),
        )

        assertTrue(automatic.canStart)
        assertTrue(StudySyncRunStep.UPLOAD_ENCRYPTED_BUNDLE in automatic.steps)
    }

    @Test
    fun `disabled sync blocks every remote run`() {
        val plan = StudySyncRunbookPlanner.plan(
            intent = StudySyncRunIntent.MANUAL_REMOTE_CHECK,
            state = StudySyncState.disabled(),
        )

        assertFalse(plan.canStart)
        assertTrue(StudySyncRunBlocker.SYNC_DISABLED in plan.blockers)
        assertTrue(StudySyncRunBlocker.PROVIDER_NOT_CONFIGURED in plan.blockers)
    }

    private fun folderState(
        remoteCount: Int,
        backgroundMode: StudySyncBackgroundMode = StudySyncBackgroundMode.MANUAL_ONLY,
    ): StudySyncState = StudySyncState.disabled().copy(
        enabled = true,
        provider = LocalFolderSyncProvider,
        blockedReason = null,
        settings = StudyFlowSyncSettings(
            syncEnabled = true,
            providerId = StudyFlowSyncProviderIds.LOCAL_FOLDER,
            providerLocation = "content://sync-folder",
            remoteCheckpointCount = remoteCount,
            backgroundSyncMode = backgroundMode,
        ),
    )

    private fun summary(hasLocalData: Boolean): StudySyncChangeSummary = StudySyncChangeSummary(
        planCount = if (hasLocalData) 1 else 0,
        activePlanCount = if (hasLocalData) 1 else 0,
        latestUpdatedAtEpochMillis = 100L,
        localChangeToken = "token",
        databaseRevision = 1L,
        changesSinceCheckpoint = if (hasLocalData) 1 else 0,
    )

    private fun entity(hash: String, updatedAt: Long): StudySyncEntityVersion = StudySyncEntityVersion(
        entityType = "studyPlanId",
        entityId = "plan-1",
        updatedAtEpochMillis = updatedAt,
        contentHash = hash,
    )
}
