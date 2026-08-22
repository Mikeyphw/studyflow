package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.StudyFlowSyncProviderIds
import com.mikeyphw.studyflow.core.portability.StudyFlowSyncBundleCodec
import com.mikeyphw.studyflow.core.portability.StudyFlowSyncCheckpoint
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowSyncProviderTest {
    @Test
    fun disabledProviderIsAvailableButNeverConfigured() = runTest {
        assertEquals(StudyFlowSyncProviderIds.NONE, DisabledStudyFlowSyncProvider.id)
        assertFalse(DisabledStudyFlowSyncProvider.requiresAccount)
        assertFalse(DisabledStudyFlowSyncProvider.isConfigured())
        assertTrue(DisabledStudyFlowSyncProvider.listCheckpoints().isEmpty())
        assertFalse(DisabledStudyFlowSyncProvider.deleteCheckpoint("checkpoint"))
    }

    @Test(expected = SyncProviderNotConfiguredException::class)
    fun disabledProviderRejectsUpload() = runTest {
        DisabledStudyFlowSyncProvider.uploadCheckpoint(sampleUpload())
    }

    @Test
    fun registryDefaultsToDisabledProviderForNullProviderId() {
        val provider = StudyFlowSyncProviderRegistry.DisabledOnly.provider(null)

        assertEquals(DisabledStudyFlowSyncProvider, provider)
    }

    @Test(expected = IllegalArgumentException::class)
    fun registryRejectsUnknownProvider() {
        StudyFlowSyncProviderRegistry.DisabledOnly.provider("missing")
    }

    private fun sampleUpload(): SyncBundleUpload {
        val bytes = byteArrayOf(1, 2, 3)
        val checkpoint = StudyFlowSyncCheckpoint(
            id = "checkpoint-1",
            deviceId = "device-1",
            deviceName = "Tablet",
            createdAtEpochMillis = 1_000L,
            databaseHashSha256 = "0".repeat(64),
            changeCount = 1,
        )
        val manifest = StudyFlowSyncBundleCodec().buildManifest(
            applicationVersion = "1.0",
            checkpoint = checkpoint,
            databaseSnapshot = byteArrayOf(0),
            encryptedForRemote = true,
        )
        return SyncBundleUpload(
            fileName = "studyflow-sync-1000.sfbackup",
            manifest = manifest,
            encryptedBytes = bytes,
        )
    }
}
