package com.mikeyphw.studyflow.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowSyncSettingsTest {
    @Test
    fun `fresh installs are local first with optional cloud sync off`() {
        val settings = StudyFlowSyncSettings.LocalFirstDefault
        val state = StudySyncState.disabled()

        assertFalse(settings.syncEnabled)
        assertEquals(null, settings.providerId)
        assertEquals(StudyFlowSyncProviderIds.NONE, settings.configuredProviderId)
        assertFalse(settings.automaticSyncEnabled)
        assertTrue(settings.wifiOnly)
        assertFalse(settings.chargingOnly)
        assertTrue(settings.encryptRemoteBackups)
        assertFalse(settings.cloudRequired)
        assertEquals(NoSyncProvider, state.provider)
        assertFalse(state.cloudRequired)
    }

    @Test
    fun `automatic sync cannot run while optional sync is off`() {
        assertThrows(IllegalArgumentException::class.java) {
            StudyFlowSyncSettings(automaticSyncEnabled = true)
        }
    }

    @Test
    fun `manual optional sync can select a bring your own folder provider`() {
        val settings = StudyFlowSyncSettings(
            syncEnabled = true,
            providerId = StudyFlowSyncProviderIds.LOCAL_FOLDER,
        )

        assertTrue(settings.manualOnly)
        assertTrue(settings.providerConfigured)
        assertEquals(StudyFlowSyncProviderIds.LOCAL_FOLDER, settings.configuredProviderId)
        assertFalse(settings.cloudRequired)
    }

    @Test
    fun `local folder sync stores manual checkpoint state without requiring an account`() {
        val settings = StudyFlowSyncSettings(
            syncEnabled = true,
            providerId = StudyFlowSyncProviderIds.LOCAL_FOLDER,
            providerLocation = "content://sync-folder",
            lastManualUploadAtEpochMillis = 42L,
            lastRemoteCheckAtEpochMillis = 64L,
            remoteCheckpointCount = 1,
        )
        val state = StudySyncState.disabled().copy(
            enabled = true,
            provider = LocalFolderSyncProvider,
            settings = settings,
        )

        assertTrue(settings.localFolderConfigured)
        assertTrue(state.localFolderSyncConfigured)
        assertTrue(state.remoteCheckpointAvailable)
        assertFalse(settings.automaticSyncEnabled)
        assertFalse(settings.cloudRequired)
    }

    @Test
    fun `manual sync actions require a folder before upload or check`() {
        val disabledActions = StudySyncManualActionPlanner.actions(StudySyncState.disabled())
        val folderState = StudySyncState.disabled().copy(
            enabled = true,
            provider = LocalFolderSyncProvider,
            settings = StudyFlowSyncSettings(
                syncEnabled = true,
                providerId = StudyFlowSyncProviderIds.LOCAL_FOLDER,
                providerLocation = "content://sync-folder",
            ),
        )
        val folderActions = StudySyncManualActionPlanner.actions(folderState)

        assertFalse(disabledActions.first {
            it.action == StudySyncManualAction.PREPARE_UPLOAD_CHECKPOINT
        }.enabled)
        assertTrue(folderActions.first {
            it.action == StudySyncManualAction.PREPARE_UPLOAD_CHECKPOINT
        }.enabled)
        assertTrue(folderActions.first {
            it.action == StudySyncManualAction.CHECK_REMOTE_CHECKPOINTS
        }.enabled)
    }
}
