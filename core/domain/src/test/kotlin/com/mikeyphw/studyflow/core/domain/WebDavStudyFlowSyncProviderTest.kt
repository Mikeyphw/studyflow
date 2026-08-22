package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.portability.StudyFlowSyncBundleCodec
import com.mikeyphw.studyflow.core.portability.StudyFlowSyncCheckpoint
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDavStudyFlowSyncProviderTest {
    @Test
    fun `webdav provider uploads encrypted checkpoint and records remote index`() = runTest {
        val transport = RecordingWebDavTransport()
        val provider = WebDavStudyFlowSyncProvider(
            config = WebDavSyncProviderConfig(
                endpointUrl = "https://cloud.example.com/remote.php/dav/files/mikey/StudyFlow",
                username = "mikey",
                password = "secret",
            ),
            transport = transport,
            clock = { 2_000L },
        )
        val upload = sampleUpload()

        val result = provider.uploadCheckpoint(upload)
        val checkpoints = provider.listCheckpoints()

        assertTrue(provider.isConfigured())
        assertEquals("checkpoint-1", result.checkpoint.id)
        assertEquals(1, checkpoints.size)
        assertEquals("checkpoint-1", checkpoints.single().id)
        assertTrue(checkpoints.single().encrypted)
        assertTrue(transport.requests.any { request -> request.method == "PUT" && request.url.endsWith(".sfbackup") })
        assertTrue(transport.requests.any { request -> request.headers["Authorization"]?.startsWith("Basic ") == true })
    }

    @Test
    fun `webdav provider downloads and deletes indexed checkpoints`() = runTest {
        val transport = RecordingWebDavTransport()
        val provider = WebDavStudyFlowSyncProvider(
            config = WebDavSyncProviderConfig(endpointUrl = "https://cloud.example.com/studyflow"),
            transport = transport,
        )
        val upload = sampleUpload()
        provider.uploadCheckpoint(upload)

        val download = provider.downloadCheckpoint("checkpoint-1")
        val deleted = provider.deleteCheckpoint("checkpoint-1")

        assertArrayEquals(upload.encryptedBytes, download.encryptedBytes)
        assertTrue(deleted)
        assertTrue(provider.listCheckpoints().isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `webdav provider requires https by default`() {
        WebDavSyncProviderConfig(endpointUrl = "http://cloud.example.com/studyflow")
    }

    @Test
    fun `google drive provider is optional shell and not configured by default`() = runTest {
        assertEquals("google-drive", GoogleDriveStudyFlowSyncProvider.id)
        assertTrue(GoogleDriveStudyFlowSyncProvider.requiresAccount)
        assertFalse(GoogleDriveStudyFlowSyncProvider.isConfigured())
    }

    private fun sampleUpload(): SyncBundleUpload {
        val database = byteArrayOf(9, 8, 7)
        val checkpoint = StudyFlowSyncCheckpoint(
            id = "checkpoint-1",
            deviceId = "device-1",
            deviceName = "Tablet",
            createdAtEpochMillis = 1_000L,
            databaseHashSha256 = "89".repeat(32),
            changeCount = 3,
        )
        val manifest = StudyFlowSyncBundleCodec().buildManifest(
            applicationVersion = "1.0",
            checkpoint = checkpoint,
            databaseSnapshot = database,
            encryptedForRemote = true,
        )
        return SyncBundleUpload(
            fileName = "studyflow-sync-1000.sfbackup",
            manifest = manifest,
            encryptedBytes = byteArrayOf(1, 2, 3, 4),
        )
    }

    private class RecordingWebDavTransport : WebDavSyncTransport {
        val requests = mutableListOf<WebDavSyncRequest>()
        private val stored = linkedMapOf<String, ByteArray>()

        override suspend fun execute(request: WebDavSyncRequest): WebDavSyncResponse {
            requests += request
            return when (request.method) {
                "GET" -> stored[request.url]?.let { bytes -> WebDavSyncResponse(200, body = bytes) }
                    ?: WebDavSyncResponse(404)
                "PUT" -> {
                    stored[request.url] = requireNotNull(request.body)
                    WebDavSyncResponse(201, headers = mapOf("ETag" to listOf("revision-1")))
                }
                "DELETE" -> {
                    stored.remove(request.url)
                    WebDavSyncResponse(204)
                }
                else -> error("Unexpected method ${request.method}")
            }
        }
    }
}
