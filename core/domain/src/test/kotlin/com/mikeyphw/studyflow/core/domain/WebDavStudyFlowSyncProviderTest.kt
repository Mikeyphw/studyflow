package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.portability.StudyFlowSyncBundleCodec
import com.mikeyphw.studyflow.core.portability.StudyFlowSyncCheckpoint
import java.util.Base64
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
        val config = config()
        val provider = WebDavStudyFlowSyncProvider(
            config = config,
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
        assertTrue(
            transport.requests.any { request ->
                request.method == "PUT" &&
                    request.url.endsWith(".sfbackup") &&
                    request.headers["If-None-Match"] == "*"
            },
        )
        assertTrue(
            transport.requests.any { request ->
                request.method == "PUT" &&
                    request.url == config.indexUrl() &&
                    request.headers["If-None-Match"] == "*"
            },
        )
        assertTrue(transport.requests.any { request -> request.headers["Authorization"]?.startsWith("Basic ") == true })
    }

    @Test
    fun `webdav provider downloads and deletes indexed checkpoints`() = runTest {
        val transport = RecordingWebDavTransport()
        val provider = WebDavStudyFlowSyncProvider(
            config = config(endpoint = "https://cloud.example.com/studyflow"),
            transport = transport,
        )
        val upload = sampleUpload()
        provider.uploadCheckpoint(upload)

        val download = provider.downloadCheckpoint("checkpoint-1")
        val deleted = provider.deleteCheckpoint("checkpoint-1")

        assertArrayEquals(upload.encryptedBytes, download.encryptedBytes)
        assertTrue(deleted)
        assertTrue(provider.listCheckpoints().isEmpty())
        assertTrue(transport.requests.any { request ->
            request.method == "PUT" && request.url.endsWith("studyflow-sync-index.tsv") &&
                request.headers.containsKey("If-Match")
        })
    }

    @Test
    fun `concurrent index change is retried without losing peer checkpoint`() = runTest {
        val transport = RecordingWebDavTransport()
        val config = config(endpoint = "https://cloud.example.com/concurrent")
        val provider = WebDavStudyFlowSyncProvider(config = config, transport = transport)
        provider.uploadCheckpoint(sampleUpload(id = "checkpoint-1", createdAt = 1_000L, fileName = "one.sfbackup"))
        transport.injectIndexRowBeforeNextConditionalWrite(
            config.indexUrl(),
            indexRow(id = "checkpoint-peer", fileName = "peer.sfbackup", createdAt = 1_500L),
        )

        provider.uploadCheckpoint(sampleUpload(id = "checkpoint-2", createdAt = 2_000L, fileName = "two.sfbackup"))

        assertEquals(
            setOf("checkpoint-1", "checkpoint-peer", "checkpoint-2"),
            provider.listCheckpoints().map { checkpoint -> checkpoint.id }.toSet(),
        )
        assertTrue(
            transport.requests.count { request ->
                request.method == "PUT" &&
                    request.url == config.indexUrl() &&
                    request.headers.containsKey("If-Match")
            } >= 2,
        )
    }

    @Test
    fun `delete retries index compare and swap without erasing concurrent peer row`() = runTest {
        val transport = RecordingWebDavTransport()
        val config = config(endpoint = "https://cloud.example.com/delete-race")
        val provider = WebDavStudyFlowSyncProvider(config = config, transport = transport)
        provider.uploadCheckpoint(sampleUpload(id = "checkpoint-1", createdAt = 1_000L, fileName = "one.sfbackup"))
        provider.uploadCheckpoint(sampleUpload(id = "checkpoint-2", createdAt = 2_000L, fileName = "two.sfbackup"))
        transport.injectIndexRowBeforeNextConditionalWrite(
            config.indexUrl(),
            indexRow(id = "checkpoint-peer", fileName = "peer.sfbackup", createdAt = 3_000L),
        )

        assertTrue(provider.deleteCheckpoint("checkpoint-1"))

        assertEquals(
            setOf("checkpoint-2", "checkpoint-peer"),
            provider.listCheckpoints().map { checkpoint -> checkpoint.id }.toSet(),
        )
    }

    @Test
    fun `retry after partial index failure reuses identical remote bundle without overwriting it`() = runTest {
        val transport = RecordingWebDavTransport()
        val config = config(endpoint = "https://cloud.example.com/retry")
        val provider = WebDavStudyFlowSyncProvider(config = config, transport = transport)
        val upload = sampleUpload()
        provider.uploadCheckpoint(upload)
        transport.remove(config.indexUrl())

        provider.uploadCheckpoint(upload)

        assertEquals(listOf("checkpoint-1"), provider.listCheckpoints().map { it.id })
        assertTrue(transport.requests.any { request -> request.method == "GET" && request.url == config.bundleUrl(upload.fileName) })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `webdav index rejects duplicate checkpoint ids`() = runTest {
        val transport = RecordingWebDavTransport()
        val config = config(endpoint = "https://cloud.example.com/duplicates")
        transport.seed(
            config.indexUrl(),
            (indexRow("duplicate", "one.sfbackup", 1L) + indexRow("duplicate", "two.sfbackup", 2L)).encodeToByteArray(),
        )
        val provider = WebDavStudyFlowSyncProvider(config = config, transport = transport)

        provider.listCheckpoints()
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

    private fun config(
        endpoint: String = "https://cloud.example.com/remote.php/dav/files/mikey/StudyFlow",
    ): WebDavSyncProviderConfig = WebDavSyncProviderConfig(
        endpointUrl = endpoint,
        username = "mikey",
        password = "secret",
    )

    private fun sampleUpload(
        id: String = "checkpoint-1",
        createdAt: Long = 1_000L,
        fileName: String = "studyflow-sync-1000.sfbackup",
    ): SyncBundleUpload {
        val database = byteArrayOf(9, 8, 7)
        val checkpoint = StudyFlowSyncCheckpoint(
            id = id,
            deviceId = "device-1",
            deviceName = "Tablet",
            createdAtEpochMillis = createdAt,
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
            fileName = fileName,
            manifest = manifest,
            encryptedBytes = byteArrayOf(1, 2, 3, 4),
        )
    }

    private fun indexRow(
        id: String,
        fileName: String,
        createdAt: Long,
    ): String = listOf(
        id,
        fileName,
        createdAt.toString(),
        "device-peer",
        Base64.getUrlEncoder().withoutPadding().encodeToString("Peer device".encodeToByteArray()),
        "1",
        "89".repeat(32),
        "1",
        "4",
        "true",
    ).joinToString("\t", postfix = "\n")

    private class RecordingWebDavTransport : WebDavSyncTransport {
        val requests = mutableListOf<WebDavSyncRequest>()
        private val stored = linkedMapOf<String, ByteArray>()
        private val revisions = linkedMapOf<String, String>()
        private var nextRevision = 1L
        private var pendingIndexMutation: Pair<String, String>? = null

        fun seed(url: String, bytes: ByteArray) {
            stored[url] = bytes
            revisions[url] = newRevision()
        }

        fun remove(url: String) {
            stored.remove(url)
            revisions.remove(url)
        }

        fun injectIndexRowBeforeNextConditionalWrite(url: String, row: String) {
            pendingIndexMutation = url to row
        }

        override suspend fun execute(request: WebDavSyncRequest): WebDavSyncResponse {
            requests += request
            return when (request.method) {
                "GET" -> stored[request.url]?.let { bytes ->
                    WebDavSyncResponse(
                        200,
                        headers = mapOf("ETag" to listOf(requireNotNull(revisions[request.url]))),
                        body = bytes,
                    )
                } ?: WebDavSyncResponse(404)
                "PUT" -> put(request)
                "DELETE" -> {
                    stored.remove(request.url)
                    revisions.remove(request.url)
                    WebDavSyncResponse(204)
                }
                else -> error("Unexpected method ${request.method}")
            }
        }

        private fun put(request: WebDavSyncRequest): WebDavSyncResponse {
            val mutation = pendingIndexMutation
            if (mutation != null && mutation.first == request.url && request.headers.containsKey("If-Match")) {
                val previous = stored[request.url]?.decodeToString().orEmpty()
                stored[request.url] = (previous + mutation.second).encodeToByteArray()
                revisions[request.url] = newRevision()
                pendingIndexMutation = null
            }

            val exists = request.url in stored
            if (request.headers["If-None-Match"] == "*" && exists) {
                return WebDavSyncResponse(412)
            }
            request.headers["If-Match"]?.let { expected ->
                if (!exists || revisions[request.url] != expected) {
                    return WebDavSyncResponse(412)
                }
            }

            stored[request.url] = requireNotNull(request.body)
            val revision = newRevision()
            revisions[request.url] = revision
            return WebDavSyncResponse(201, headers = mapOf("ETag" to listOf(revision)))
        }

        private fun newRevision(): String = "\"revision-${nextRevision++}\""
    }
}
