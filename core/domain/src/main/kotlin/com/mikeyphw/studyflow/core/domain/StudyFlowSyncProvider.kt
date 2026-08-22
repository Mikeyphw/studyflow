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
