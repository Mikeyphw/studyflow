package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.StudyFlowSyncProviderIds
import com.mikeyphw.studyflow.core.portability.StudyFlowSyncBundleManifest

interface StudyFlowSyncProvider {
    val id: String
    val displayName: String
    val requiresAccount: Boolean

    suspend fun isConfigured(): Boolean
    suspend fun listCheckpoints(): List<RemoteSyncCheckpoint>
    suspend fun uploadCheckpoint(bundle: SyncBundleUpload): SyncUploadResult
    suspend fun downloadCheckpoint(id: String): SyncBundleDownload
    suspend fun deleteCheckpoint(id: String): Boolean
}

data class RemoteSyncCheckpoint(
    val id: String,
    val providerId: String,
    val displayName: String,
    val createdAtEpochMillis: Long,
    val deviceId: String,
    val deviceName: String,
    val schemaVersion: Int,
    val databaseHashSha256: String,
    val changeCount: Int,
    val byteSize: Long,
    val encrypted: Boolean,
)

data class SyncBundleUpload(
    val fileName: String,
    val manifest: StudyFlowSyncBundleManifest,
    val encryptedBytes: ByteArray,
) {
    init {
        require(fileName.endsWith(".sfbackup")) { "Sync bundle uploads must use the .sfbackup extension." }
        require(encryptedBytes.isNotEmpty()) { "Sync bundle upload bytes cannot be empty." }
        require(manifest.encryptedForRemote) { "Provider uploads require encrypted remote bundles." }
    }

    override fun equals(other: Any?): Boolean =
        other is SyncBundleUpload &&
            fileName == other.fileName &&
            manifest == other.manifest &&
            encryptedBytes.contentEquals(other.encryptedBytes)

    override fun hashCode(): Int =
        31 * (31 * fileName.hashCode() + manifest.hashCode()) + encryptedBytes.contentHashCode()
}

data class SyncBundleDownload(
    val checkpoint: RemoteSyncCheckpoint,
    val encryptedBytes: ByteArray,
) {
    init {
        require(encryptedBytes.isNotEmpty()) { "Downloaded sync bundle bytes cannot be empty." }
        require(checkpoint.encrypted) { "Downloaded provider bundles must be encrypted." }
    }

    override fun equals(other: Any?): Boolean =
        other is SyncBundleDownload &&
            checkpoint == other.checkpoint &&
            encryptedBytes.contentEquals(other.encryptedBytes)

    override fun hashCode(): Int = 31 * checkpoint.hashCode() + encryptedBytes.contentHashCode()
}

data class SyncUploadResult(
    val checkpoint: RemoteSyncCheckpoint,
    val uploadedAtEpochMillis: Long,
    val providerRevision: String? = null,
)

class SyncProviderNotConfiguredException(providerId: String) : IllegalStateException(
    "Sync provider $providerId is not configured.",
)

object DisabledStudyFlowSyncProvider : StudyFlowSyncProvider {
    override val id: String = StudyFlowSyncProviderIds.NONE
    override val displayName: String = "None"
    override val requiresAccount: Boolean = false

    override suspend fun isConfigured(): Boolean = false

    override suspend fun listCheckpoints(): List<RemoteSyncCheckpoint> = emptyList()

    override suspend fun uploadCheckpoint(bundle: SyncBundleUpload): SyncUploadResult =
        throw SyncProviderNotConfiguredException(id)

    override suspend fun downloadCheckpoint(id: String): SyncBundleDownload =
        throw SyncProviderNotConfiguredException(this.id)

    override suspend fun deleteCheckpoint(id: String): Boolean = false
}

class StudyFlowSyncProviderRegistry(
    providers: List<StudyFlowSyncProvider>,
) {
    private val providersById: Map<String, StudyFlowSyncProvider> = providers.associateBy { provider -> provider.id }

    init {
        require(providersById.size == providers.size) { "Sync provider ids must be unique." }
        require(StudyFlowSyncProviderIds.NONE in providersById) { "Sync registry must include the disabled provider." }
    }

    fun provider(id: String?): StudyFlowSyncProvider = providersById[id ?: StudyFlowSyncProviderIds.NONE]
        ?: throw IllegalArgumentException("Unknown sync provider id: $id")

    fun all(): List<StudyFlowSyncProvider> = providersById.values.sortedBy { provider -> provider.displayName }

    companion object {
        val DisabledOnly: StudyFlowSyncProviderRegistry = StudyFlowSyncProviderRegistry(
            providers = listOf(DisabledStudyFlowSyncProvider),
        )
    }
}


data class WebDavSyncProviderConfig(
    val endpointUrl: String,
    val username: String? = null,
    val password: String? = null,
    val indexFileName: String = "studyflow-sync-index.tsv",
    val requireHttps: Boolean = true,
) {
    init {
        require(endpointUrl.isNotBlank()) { "WebDAV endpoint cannot be blank." }
        require(indexFileName.isNotBlank()) { "WebDAV index file name cannot be blank." }
        require('/' !in indexFileName && '\\' !in indexFileName) {
            "WebDAV index file name must be a single path segment."
        }
        require(username?.isNotBlank() ?: true) { "WebDAV username cannot be blank." }
        require(password?.isNotBlank() ?: true) { "WebDAV password cannot be blank." }
        require((username == null) == (password == null)) {
            "WebDAV username and password must be configured together."
        }
        if (requireHttps) {
            require(endpointUrl.trim().startsWith("https://")) {
                "WebDAV sync requires HTTPS by default."
            }
        }
    }

    val normalizedEndpointUrl: String = endpointUrl.trim().trimEnd('/')
    val configured: Boolean = normalizedEndpointUrl.isNotBlank()
    val hasCredentials: Boolean = username != null && password != null

    fun bundleUrl(fileName: String): String = normalizedEndpointUrl + "/" + encodePathSegment(fileName)
    fun indexUrl(): String = normalizedEndpointUrl + "/" + encodePathSegment(indexFileName)
}

data class WebDavSyncRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray? = null,
)

data class WebDavSyncResponse(
    val code: Int,
    val headers: Map<String, List<String>> = emptyMap(),
    val body: ByteArray = ByteArray(0),
) {
    val successful: Boolean = code in 200..299
    val notFound: Boolean = code == 404

    override fun equals(other: Any?): Boolean = other is WebDavSyncResponse &&
        code == other.code &&
        headers == other.headers &&
        body.contentEquals(other.body)

    override fun hashCode(): Int = 31 * (31 * code + headers.hashCode()) + body.contentHashCode()
}

interface WebDavSyncTransport {
    suspend fun execute(request: WebDavSyncRequest): WebDavSyncResponse
}

class HttpUrlConnectionWebDavSyncTransport(
    private val connectTimeoutMillis: Int = 15_000,
    private val readTimeoutMillis: Int = 30_000,
) : WebDavSyncTransport {
    override suspend fun execute(request: WebDavSyncRequest): WebDavSyncResponse =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val connection = java.net.URI.create(request.url).toURL().openConnection() as java.net.HttpURLConnection
            connection.requestMethod = request.method
            connection.connectTimeout = connectTimeoutMillis
            connection.readTimeout = readTimeoutMillis
            request.headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            request.body?.let { body ->
                connection.doOutput = true
                connection.outputStream.use { output -> output.write(body) }
            }
            val code = connection.responseCode
            val input = if (code >= 400) connection.errorStream else connection.inputStream
            val body = input?.use { stream -> stream.readBytes() } ?: ByteArray(0)
            WebDavSyncResponse(
                code = code,
                headers = connection.headerFields.mapNotNull { (key, value) -> key?.let { it to value } }.toMap(),
                body = body,
            )
        }
}

class WebDavStudyFlowSyncProvider(
    private val config: WebDavSyncProviderConfig,
    private val transport: WebDavSyncTransport = HttpUrlConnectionWebDavSyncTransport(),
    private val clock: () -> Long = { System.currentTimeMillis() },
) : StudyFlowSyncProvider {
    override val id: String = StudyFlowSyncProviderIds.WEBDAV
    override val displayName: String = "WebDAV / Nextcloud"
    override val requiresAccount: Boolean = true

    override suspend fun isConfigured(): Boolean = config.configured

    override suspend fun listCheckpoints(): List<RemoteSyncCheckpoint> =
        readIndex().map { indexed -> indexed.checkpoint }

    override suspend fun uploadCheckpoint(bundle: SyncBundleUpload): SyncUploadResult {
        requireConfigured()
        val upload = execute(
            method = "PUT",
            url = config.bundleUrl(bundle.fileName),
            body = bundle.encryptedBytes,
            extraHeaders = mapOf("Content-Type" to "application/vnd.studyflow.sync+encrypted"),
        )
        require(upload.successful) { "WebDAV upload failed with HTTP ${upload.code}." }

        val checkpoint = RemoteSyncCheckpoint(
            id = bundle.manifest.checkpointId,
            providerId = id,
            displayName = bundle.fileName,
            createdAtEpochMillis = bundle.manifest.createdAtEpochMillis,
            deviceId = bundle.manifest.deviceId,
            deviceName = bundle.manifest.checkpoint.deviceName,
            schemaVersion = bundle.manifest.schemaVersion,
            databaseHashSha256 = bundle.manifest.databaseChecksumSha256,
            changeCount = bundle.manifest.checkpoint.changeCount,
            byteSize = bundle.encryptedBytes.size.toLong(),
            encrypted = true,
        )
        val merged = (readIndex() + IndexedRemoteSyncCheckpoint(bundle.fileName, checkpoint))
            .distinctBy { indexed -> indexed.checkpoint.id }
            .sortedByDescending { indexed -> indexed.checkpoint.createdAtEpochMillis }
        writeIndex(merged)
        return SyncUploadResult(
            checkpoint = checkpoint,
            uploadedAtEpochMillis = clock(),
            providerRevision = upload.headers["ETag"]?.firstOrNull(),
        )
    }

    override suspend fun downloadCheckpoint(id: String): SyncBundleDownload {
        require(id.isNotBlank()) { "Checkpoint id cannot be blank." }
        val indexed = readIndex().firstOrNull { item -> item.checkpoint.id == id }
            ?: throw IllegalArgumentException("Unknown WebDAV sync checkpoint: $id")
        val response = execute("GET", config.bundleUrl(indexed.fileName))
        require(response.successful) { "WebDAV download failed with HTTP ${response.code}." }
        return SyncBundleDownload(indexed.checkpoint, response.body)
    }

    override suspend fun deleteCheckpoint(id: String): Boolean {
        require(id.isNotBlank()) { "Checkpoint id cannot be blank." }
        val index = readIndex()
        val indexed = index.firstOrNull { item -> item.checkpoint.id == id } ?: return false
        val response = execute("DELETE", config.bundleUrl(indexed.fileName))
        if (!response.successful && !response.notFound) return false
        writeIndex(index.filterNot { item -> item.checkpoint.id == id })
        return true
    }

    private suspend fun readIndex(): List<IndexedRemoteSyncCheckpoint> {
        requireConfigured()
        val response = execute("GET", config.indexUrl())
        if (response.notFound) return emptyList()
        require(response.successful) { "WebDAV index read failed with HTTP ${response.code}." }
        return decodeIndex(response.body.decodeToString())
    }

    private suspend fun writeIndex(checkpoints: List<IndexedRemoteSyncCheckpoint>) {
        val bytes = encodeIndex(checkpoints).encodeToByteArray()
        val response = execute(
            method = "PUT",
            url = config.indexUrl(),
            body = bytes,
            extraHeaders = mapOf("Content-Type" to "text/tab-separated-values; charset=utf-8"),
        )
        require(response.successful) { "WebDAV index write failed with HTTP ${response.code}." }
    }

    private fun requireConfigured() {
        if (!config.configured) throw SyncProviderNotConfiguredException(id)
    }

    private suspend fun execute(
        method: String,
        url: String,
        body: ByteArray? = null,
        extraHeaders: Map<String, String> = emptyMap(),
    ): WebDavSyncResponse = transport.execute(
        WebDavSyncRequest(
            method = method,
            url = url,
            headers = authHeaders(config) + extraHeaders,
            body = body,
        ),
    )
}

object GoogleDriveStudyFlowSyncProvider : StudyFlowSyncProvider {
    override val id: String = StudyFlowSyncProviderIds.GOOGLE_DRIVE
    override val displayName: String = "Google Drive"
    override val requiresAccount: Boolean = true

    override suspend fun isConfigured(): Boolean = false
    override suspend fun listCheckpoints(): List<RemoteSyncCheckpoint> = emptyList()
    override suspend fun uploadCheckpoint(bundle: SyncBundleUpload): SyncUploadResult =
        throw SyncProviderNotConfiguredException(id)
    override suspend fun downloadCheckpoint(id: String): SyncBundleDownload =
        throw SyncProviderNotConfiguredException(this.id)
    override suspend fun deleteCheckpoint(id: String): Boolean = false
}

object StudyFlowRemoteSyncProviders {
    fun webDav(config: WebDavSyncProviderConfig, transport: WebDavSyncTransport): StudyFlowSyncProvider =
        WebDavStudyFlowSyncProvider(config = config, transport = transport)

    val DefaultCatalog: StudyFlowSyncProviderRegistry = StudyFlowSyncProviderRegistry(
        providers = listOf(
            DisabledStudyFlowSyncProvider,
            GoogleDriveStudyFlowSyncProvider,
        ),
    )
}

private data class IndexedRemoteSyncCheckpoint(
    val fileName: String,
    val checkpoint: RemoteSyncCheckpoint,
)

private fun encodeIndex(checkpoints: List<IndexedRemoteSyncCheckpoint>): String =
    checkpoints.joinToString(separator = "\n", postfix = "\n") { indexed ->
        val checkpoint = indexed.checkpoint
        listOf(
            checkpoint.id,
            indexed.fileName,
            checkpoint.createdAtEpochMillis.toString(),
            checkpoint.deviceId,
            encodeIndexText(checkpoint.deviceName),
            checkpoint.schemaVersion.toString(),
            checkpoint.databaseHashSha256,
            checkpoint.changeCount.toString(),
            checkpoint.byteSize.toString(),
            checkpoint.encrypted.toString(),
        ).joinToString("\t")
    }

private fun decodeIndex(text: String): List<IndexedRemoteSyncCheckpoint> = text
    .lineSequence()
    .map(String::trim)
    .filter(String::isNotBlank)
    .map { line ->
        val parts = line.split('\t')
        require(parts.size == 10) { "Invalid WebDAV sync index row." }
        val checkpoint = RemoteSyncCheckpoint(
            id = parts[0],
            providerId = StudyFlowSyncProviderIds.WEBDAV,
            displayName = parts[1],
            createdAtEpochMillis = parts[2].toLong(),
            deviceId = parts[3],
            deviceName = decodeIndexText(parts[4]),
            schemaVersion = parts[5].toInt(),
            databaseHashSha256 = parts[6],
            changeCount = parts[7].toInt(),
            byteSize = parts[8].toLong(),
            encrypted = parts[9].toBooleanStrict(),
        )
        IndexedRemoteSyncCheckpoint(fileName = parts[1], checkpoint = checkpoint)
    }
    .toList()

private fun authHeaders(config: WebDavSyncProviderConfig): Map<String, String> =
    if (config.username == null || config.password == null) {
        emptyMap()
    } else {
        val token = java.util.Base64.getEncoder()
            .encodeToString("${config.username}:${config.password}".encodeToByteArray())
        mapOf("Authorization" to "Basic $token")
    }

private fun encodeIndexText(value: String): String =
    java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(value.encodeToByteArray())

private fun decodeIndexText(value: String): String =
    java.util.Base64.getUrlDecoder().decode(value).decodeToString()

private fun encodePathSegment(value: String): String {
    require(value.isNotBlank()) { "Remote path segment cannot be blank." }
    require('/' !in value && '\\' !in value && value != ".." && value != ".") {
        "Remote path segment must not contain path separators."
    }
    return java.net.URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
}
