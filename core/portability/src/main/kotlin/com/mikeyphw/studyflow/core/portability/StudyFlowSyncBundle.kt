package com.mikeyphw.studyflow.core.portability

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

const val StudyFlowSyncBundleFormat = "com.mikeyphw.studyflow.sync"
const val StudyFlowSyncBundleSchemaVersion = 1
const val StudyFlowSyncBundleMimeType = "application/vnd.studyflow.sync+zip"
const val StudyFlowSyncBundleExtension = ".sfbackup"

@Serializable
data class StudyFlowSyncCheckpoint(
    val id: String,
    val deviceId: String,
    val deviceName: String,
    val createdAtEpochMillis: Long,
    val schemaVersion: Int = StudyFlowSyncBundleSchemaVersion,
    val databaseHashSha256: String,
    val changeCount: Int,
    val graphRevision: Long = 0L,
)

@Serializable
data class StudyFlowSyncAttachment(
    val entryName: String,
    val byteSize: Long,
    val checksumSha256: String,
)

@Serializable
data class StudyFlowSyncBundleManifest(
    val format: String = StudyFlowSyncBundleFormat,
    val schemaVersion: Int = StudyFlowSyncBundleSchemaVersion,
    val applicationId: String = "com.mikeyphw.studyflow",
    val applicationVersion: String,
    val checkpoint: StudyFlowSyncCheckpoint,
    val encryptedForRemote: Boolean = true,
    val databaseEntryName: String = StudyFlowSyncBundleCodec.DatabaseSnapshotEntry,
    val databaseByteSize: Long,
    val databaseChecksumSha256: String,
    val attachments: List<StudyFlowSyncAttachment> = emptyList(),
) {
    val checkpointId: String get() = checkpoint.id
    val deviceId: String get() = checkpoint.deviceId
    val createdAtEpochMillis: Long get() = checkpoint.createdAtEpochMillis
}

@Serializable
data class StudyFlowSyncIntegrity(
    val algorithm: String = "SHA-256",
    val entries: Map<String, String>,
)

data class StudyFlowSyncBundleArchive(
    val manifest: StudyFlowSyncBundleManifest,
    val databaseSnapshot: ByteArray,
    val attachmentBytes: Map<String, ByteArray> = emptyMap(),
    val attachmentStreams: Map<String, () -> InputStream> = emptyMap(),
) {
    init {
        val declared = manifest.attachments.map { it.entryName }.toSet()
        require(attachmentBytes.keys.intersect(attachmentStreams.keys).isEmpty()) {
            "Attachment entries must have exactly one byte or stream source."
        }
        require(attachmentBytes.keys + attachmentStreams.keys == declared) {
            "Attachment sources must match the sync manifest."
        }
    }

    fun openAttachment(entryName: String): InputStream =
        attachmentBytes[entryName]?.inputStream()
            ?: attachmentStreams[entryName]?.invoke()
            ?: throw StudyPlanPackageException("Missing sync attachment source for $entryName.")
}

class StudyFlowSyncBundleCodec(
    private val json: Json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = false
        explicitNulls = true
        prettyPrint = true
    },
    private val encryptedCodec: StudyPlanEncryptedPackageCodec = StudyPlanEncryptedPackageCodec(),
) {
    fun defaultFileName(createdAtEpochMillis: Long): String =
        "studyflow-sync-$createdAtEpochMillis$StudyFlowSyncBundleExtension"

    fun isEncrypted(bytes: ByteArray): Boolean = encryptedCodec.isEncrypted(bytes)

    fun writeArchive(archive: StudyFlowSyncBundleArchive, output: OutputStream) {
        validateManifest(archive.manifest, archive.databaseSnapshot.size.toLong())
        val manifestBytes = json.encodeToString(archive.manifest).encodeToByteArray()
        val checkpointBytes = json.encodeToString(archive.manifest.checkpoint).encodeToByteArray()
        val checksumEntries = linkedMapOf(
            ManifestEntry to sha256(manifestBytes),
            CheckpointEntry to sha256(checkpointBytes),
            archive.manifest.databaseEntryName to sha256(archive.databaseSnapshot),
        )
        archive.manifest.attachments.sortedBy { it.entryName }.forEach { attachment ->
            checksumEntries[attachment.entryName] = attachment.checksumSha256.lowercase()
        }
        val integrityBytes = json.encodeToString(StudyFlowSyncIntegrity(entries = checksumEntries)).encodeToByteArray()
        ZipOutputStream(output.buffered()).use { zip ->
            writeEntry(zip, ManifestEntry, manifestBytes)
            writeEntry(zip, CheckpointEntry, checkpointBytes)
            writeEntry(zip, archive.manifest.databaseEntryName, archive.databaseSnapshot)
            archive.manifest.attachments.sortedBy { it.entryName }.forEach { attachment ->
                archive.openAttachment(attachment.entryName).buffered().use { input ->
                    val bytes = readBounded(input, MaxEntryBytes)
                    if (bytes.size.toLong() != attachment.byteSize) {
                        throw StudyPlanPackageException("Sync attachment size does not match ${attachment.entryName}.")
                    }
                    if (!sha256(bytes).equals(attachment.checksumSha256, ignoreCase = true)) {
                        throw StudyPlanPackageException("Sync attachment checksum failed for ${attachment.entryName}.")
                    }
                    writeEntry(zip, attachment.entryName, bytes)
                }
            }
            writeEntry(zip, IntegrityEntry, integrityBytes)
        }
    }

    fun writeEncryptedArchive(archive: StudyFlowSyncBundleArchive, output: OutputStream, password: CharArray) {
        require(archive.manifest.encryptedForRemote) { "Remote sync bundles must declare encryptedForRemote=true." }
        encryptedCodec.encrypt(output, password) { encryptedOutput ->
            writeArchive(archive, encryptedOutput)
        }
    }

    fun encodeArchive(archive: StudyFlowSyncBundleArchive): ByteArray =
        ByteArrayOutputStream().also { output -> writeArchive(archive, output) }.toByteArray()

    fun encodeEncryptedArchive(archive: StudyFlowSyncBundleArchive, password: CharArray): ByteArray =
        ByteArrayOutputStream().also { output -> writeEncryptedArchive(archive, output, password) }.toByteArray()

    fun readArchive(input: InputStream): StudyFlowSyncBundleArchive {
        val entries = readEntries(input)
        val required = setOf(ManifestEntry, CheckpointEntry, DatabaseSnapshotEntry, IntegrityEntry)
        if (!entries.keys.containsAll(required)) {
            val missing = required - entries.keys
            throw StudyPlanPackageException("Sync bundle is incomplete: ${missing.joinToString()}.")
        }
        val integrity = decode<StudyFlowSyncIntegrity>(entries.getValue(IntegrityEntry))
        require(integrity.algorithm == "SHA-256") { "Unsupported sync integrity algorithm: ${integrity.algorithm}" }
        entries.forEach { (name, bytes) ->
            if (name != IntegrityEntry) {
                val expected = integrity.entries[name]
                    ?: throw StudyPlanPackageException("Missing sync checksum for $name.")
                if (!sha256(bytes).equals(expected, ignoreCase = true)) {
                    throw StudyPlanPackageException("Sync checksum verification failed for $name.")
                }
            }
        }
        val manifest = decode<StudyFlowSyncBundleManifest>(entries.getValue(ManifestEntry))
        val checkpoint = decode<StudyFlowSyncCheckpoint>(entries.getValue(CheckpointEntry))
        if (manifest.checkpoint != checkpoint) {
            throw StudyPlanPackageException("Sync manifest and checkpoint metadata do not match.")
        }
        validateManifest(manifest, entries.getValue(manifest.databaseEntryName).size.toLong())
        val allowed = required + manifest.attachments.map { it.entryName }.toSet()
        val extras = entries.keys - allowed
        if (extras.isNotEmpty()) {
            throw StudyPlanPackageException("Unexpected sync bundle entry: ${extras.sorted().first()}")
        }
        val attachments = manifest.attachments.associate { attachment ->
            val bytes = entries[attachment.entryName]
                ?: throw StudyPlanPackageException("Sync bundle is missing attachment ${attachment.entryName}.")
            if (bytes.size.toLong() != attachment.byteSize) {
                throw StudyPlanPackageException("Sync attachment size does not match ${attachment.entryName}.")
            }
            if (!sha256(bytes).equals(attachment.checksumSha256, ignoreCase = true)) {
                throw StudyPlanPackageException("Sync attachment checksum failed for ${attachment.entryName}.")
            }
            attachment.entryName to bytes
        }
        return StudyFlowSyncBundleArchive(
            manifest = manifest,
            databaseSnapshot = entries.getValue(manifest.databaseEntryName),
            attachmentBytes = attachments,
        )
    }

    fun readEncryptedArchive(
        input: InputStream,
        password: CharArray,
        maximumEncryptedBytes: Long,
    ): StudyFlowSyncBundleArchive {
        val plain = ByteArrayOutputStream()
        encryptedCodec.decrypt(input, plain, password, maximumEncryptedBytes)
        return readArchive(ByteArrayInputStream(plain.toByteArray()))
    }

    fun buildManifest(
        applicationVersion: String,
        checkpoint: StudyFlowSyncCheckpoint,
        databaseSnapshot: ByteArray,
        encryptedForRemote: Boolean = true,
        attachments: Map<String, ByteArray> = emptyMap(),
    ): StudyFlowSyncBundleManifest = StudyFlowSyncBundleManifest(
        applicationVersion = applicationVersion,
        checkpoint = checkpoint,
        encryptedForRemote = encryptedForRemote,
        databaseByteSize = databaseSnapshot.size.toLong(),
        databaseChecksumSha256 = sha256(databaseSnapshot),
        attachments = attachments.toSortedMap().map { (entryName, bytes) ->
            requireSafeEntryName(entryName)
            StudyFlowSyncAttachment(
                entryName = entryName,
                byteSize = bytes.size.toLong(),
                checksumSha256 = sha256(bytes),
            )
        },
    )

    private fun validateManifest(manifest: StudyFlowSyncBundleManifest, actualDatabaseBytes: Long) {
        require(manifest.format == StudyFlowSyncBundleFormat) { "Unsupported sync bundle format: ${manifest.format}" }
        require(manifest.schemaVersion == StudyFlowSyncBundleSchemaVersion) {
            "Unsupported sync bundle schema version: ${manifest.schemaVersion}"
        }
        require(manifest.checkpoint.id.isNotBlank()) { "Sync checkpoint id cannot be blank." }
        require(manifest.checkpoint.deviceId.isNotBlank()) { "Sync checkpoint device id cannot be blank." }
        require(manifest.checkpoint.databaseHashSha256 == manifest.databaseChecksumSha256) {
            "Sync checkpoint database hash must match the manifest."
        }
        require(manifest.databaseByteSize == actualDatabaseBytes) {
            "Sync database snapshot size does not match manifest."
        }
        require(manifest.databaseEntryName == DatabaseSnapshotEntry) { "Sync database entry path is fixed." }
        require(manifest.databaseChecksumSha256.matches(Sha256Regex)) { "Sync database checksum must be SHA-256 hex." }
        manifest.attachments.forEach { attachment ->
            require(attachment.entryName.startsWith(AttachmentsPrefix)) {
                "Sync attachments must live under attachments/."
            }
            requireSafeEntryName(attachment.entryName)
            require(attachment.byteSize >= 0L) { "Sync attachment size cannot be negative." }
            require(attachment.checksumSha256.matches(Sha256Regex)) { "Sync attachment checksum must be SHA-256 hex." }
        }
    }

    private fun readEntries(input: InputStream): Map<String, ByteArray> {
        val entries = linkedMapOf<String, ByteArray>()
        var totalBytes = 0L
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) {
                    requireSafeEntryName(entry.name)
                    require(!entries.containsKey(entry.name)) { "Duplicate sync bundle entry: ${entry.name}" }
                    require(entries.size < MaxEntryCount) { "Sync bundle contains too many entries." }
                    val bytes = readBounded(zip, MaxEntryBytes)
                    totalBytes += bytes.size
                    require(totalBytes <= MaxTotalBytes) { "Sync bundle is larger than the supported limit." }
                    entries[entry.name] = bytes
                }
                zip.closeEntry()
            }
        }
        return entries
    }

    private inline fun <reified T> decode(bytes: ByteArray): T =
        try {
            json.decodeFromString(bytes.decodeToString())
        } catch (error: Exception) {
            throw StudyPlanPackageException("Sync bundle JSON is invalid: ${error.message}")
        }

    private fun writeEntry(zip: ZipOutputStream, name: String, bytes: ByteArray) {
        requireSafeEntryName(name)
        require(bytes.size <= MaxEntryBytes) { "Sync bundle entry is larger than the supported limit." }
        zip.putNextEntry(ZipEntry(name))
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun readBounded(input: InputStream, maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            require(total <= maxBytes) { "Sync bundle entry is larger than the supported limit." }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun requireSafeEntryName(name: String) {
        require(name.isNotBlank()) { "Sync bundle entry name cannot be blank." }
        require(!name.startsWith("/")) { "Sync bundle entry path must be relative." }
        require(".." !in name.split('/')) { "Sync bundle entry path cannot escape the archive." }
        require('\\' !in name) { "Sync bundle entry path must use forward slashes." }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { byte -> "%02x".format(byte) }

    companion object {
        const val ManifestEntry = "manifest.json"
        const val CheckpointEntry = "checkpoint.json"
        const val DatabaseSnapshotEntry = "database.snapshot"
        const val IntegrityEntry = "integrity.sha256"
        const val AttachmentsPrefix = "attachments/"
        private const val MaxEntryCount = 2_048
        private const val MaxEntryBytes = 64 * 1024 * 1024
        private const val MaxTotalBytes = 512 * 1024 * 1024
        private val Sha256Regex = Regex("[0-9a-f]{64}")
    }
}
