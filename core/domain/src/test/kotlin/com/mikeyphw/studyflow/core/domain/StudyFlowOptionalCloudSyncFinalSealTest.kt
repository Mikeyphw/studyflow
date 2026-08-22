package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.StudyFlowSyncProviderIds
import com.mikeyphw.studyflow.core.model.StudyFlowSyncSettings
import com.mikeyphw.studyflow.core.model.StudySyncBackgroundMode
import com.mikeyphw.studyflow.core.model.StudySyncBackgroundSyncPlanner
import com.mikeyphw.studyflow.core.model.StudySyncConflictReviewPresenter
import com.mikeyphw.studyflow.core.model.StudySyncEntityState
import com.mikeyphw.studyflow.core.model.StudySyncEntityVersion
import com.mikeyphw.studyflow.core.model.StudySyncMergeAction
import com.mikeyphw.studyflow.core.model.StudySyncProviderCatalog
import com.mikeyphw.studyflow.core.portability.StudyFlowSyncBundleCodec
import com.mikeyphw.studyflow.core.portability.StudyFlowSyncCheckpoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowOptionalCloudSyncFinalSealTest {
    @Test
    fun `fresh install is local first and cloud optional`() {
        val settings = StudyFlowSyncSettings.LocalFirstDefault
        val plan = StudySyncBackgroundSyncPlanner.plan(settings)

        assertFalse(settings.cloudRequired)
        assertFalse(settings.syncEnabled)
        assertEquals(StudySyncBackgroundMode.OFF, settings.effectiveBackgroundMode)
        assertFalse(plan.canSchedule)
    }

    @Test
    fun `provider catalog exposes optional remote providers`() {
        val providers = StudySyncProviderCatalog.optionalRemoteProviders().map { provider -> provider.id }

        assertTrue(StudyFlowSyncProviderIds.LOCAL_FOLDER in providers)
        assertTrue(StudyFlowSyncProviderIds.WEBDAV in providers)
        assertTrue(StudyFlowSyncProviderIds.GOOGLE_DRIVE in providers)
        assertTrue(StudySyncProviderCatalog.entry(StudyFlowSyncProviderIds.WEBDAV).requiresAccount)
        assertFalse(StudySyncProviderCatalog.entry(StudyFlowSyncProviderIds.LOCAL_FOLDER).requiresAccount)
    }

    @Test
    fun `remote uploads must remain encrypted`() {
        val checkpoint = StudyFlowSyncCheckpoint(
            id = "checkpoint-final",
            deviceId = "device-final",
            deviceName = "Tablet",
            createdAtEpochMillis = 1_000L,
            databaseHashSha256 = "af".repeat(32),
            changeCount = 1,
        )
        val manifest = StudyFlowSyncBundleCodec().buildManifest(
            applicationVersion = "1.0",
            checkpoint = checkpoint,
            databaseSnapshot = byteArrayOf(1, 2, 3),
            encryptedForRemote = true,
        )
        val upload = SyncBundleUpload(
            fileName = "studyflow-sync-final.sfbackup",
            manifest = manifest,
            encryptedBytes = byteArrayOf(7, 8, 9),
        )

        assertTrue(upload.manifest.encryptedForRemote)
    }

    @Test
    fun `conflicting changes block automatic merge and require review`() {
        val local = StudySyncEntityVersion(
            entityType = "studyPlan",
            entityId = "plan-1",
            updatedAtEpochMillis = 200L,
            contentHash = "local",
            state = StudySyncEntityState.PRESENT,
        )
        val remote = local.copy(updatedAtEpochMillis = 300L, contentHash = "remote")
        val decision = StudySyncMergeEngine.decide(local = local, remote = remote)
        val review = StudySyncConflictReviewPresenter.fromDecision(decision)

        assertEquals(StudySyncMergeAction.MANUAL_CONFLICT, decision.action)
        assertTrue(decision.blocksAutomaticApply)
        assertTrue(review?.allowedChoices?.isNotEmpty() == true)
    }
}
