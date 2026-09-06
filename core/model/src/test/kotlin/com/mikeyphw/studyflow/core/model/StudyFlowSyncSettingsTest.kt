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
    @Test
    fun `fresh installation device ids are opaque and unique even when display names match`() {
        val ids = List(64) { StudySyncDeviceIdentity.newInstallationDeviceId() }

        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.all { id ->
            id.matches(Regex("device-install-[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"))
        })
    }

    @Test
    fun `existing persisted device ids are preserved exactly`() {
        val legacyId = StudySyncDeviceIdentity.stableDeviceId("This device")
        val installationId = StudySyncDeviceIdentity.newInstallationDeviceId()

        assertEquals(legacyId, StudySyncDeviceIdentity.resolveInstallationDeviceId(legacyId))
        assertEquals(installationId, StudySyncDeviceIdentity.resolveInstallationDeviceId(installationId))
    }

    @Test
    fun `legacy deterministic device ids remain backward compatible`() {
        assertEquals("device-c214f319", StudySyncDeviceIdentity.stableDeviceId("This device"))
    }

    @Test
    fun `missing persisted device id receives a new installation identity`() {
        val first = StudySyncDeviceIdentity.resolveInstallationDeviceId(null)
        val second = StudySyncDeviceIdentity.resolveInstallationDeviceId(null)

        assertTrue(first.startsWith("device-install-"))
        assertTrue(second.startsWith("device-install-"))
        assertFalse(first == second)
    }

    @Test
    fun `sync ledger records device checkpoint upload and restore preview facts`() {
        val device = StudySyncLedgerFactory.localDevice(
            id = StudySyncDeviceIdentity.stableDeviceId("Tablet"),
            name = "Tablet",
            firstSeenAtEpochMillis = 100L,
            lastSeenAtEpochMillis = 200L,
        )
        val checkpoint = StudySyncCheckpointRecord(
            id = StudySyncLedgerFactory.checkpointId(device.id, databaseRevision = 7L, createdAtEpochMillis = 250L),
            deviceId = device.id,
            deviceName = device.name,
            createdAtEpochMillis = 250L,
            databaseRevision = 7L,
            localChangeToken = "revision=7",
            changeCount = 3,
            uploadedAtEpochMillis = 300L,
            remoteLocation = "content://sync-folder",
            restorePreviewedAtEpochMillis = 400L,
        )
        val snapshot = StudySyncLedgerSnapshot(
            devices = listOf(device),
            checkpoints = listOf(checkpoint),
            events = listOf(
                StudySyncLedgerEvent(
                    sequence = 1L,
                    type = StudySyncLedgerEventType.MANUAL_UPLOAD_RECORDED,
                    deviceId = device.id,
                    checkpointId = checkpoint.id,
                    createdAtEpochMillis = 300L,
                    message = "Recorded manual encrypted checkpoint upload.",
                ),
            ),
        )

        assertEquals(device, snapshot.currentDevice)
        assertEquals(checkpoint, snapshot.latestCheckpoint)
        val latestCheckpoint = snapshot.latestCheckpoint
            ?: throw AssertionError("Expected latest sync checkpoint")
        assertTrue(latestCheckpoint.uploaded)
        assertTrue(latestCheckpoint.restorePreviewed)
        assertEquals(1, snapshot.uploadedCheckpointCount)
        assertFalse(snapshot.hasConflicts)
    }
}
