package com.mikeyphw.studyflow.core.portability

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowSyncBundleCodecTest {
    private val codec = StudyFlowSyncBundleCodec()
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = false
        explicitNulls = true
        prettyPrint = true
    }
    private val databaseSnapshot = "room database snapshot".encodeToByteArray()

    @Test
    fun archiveRoundTripKeepsManifestCheckpointDatabaseAndAttachments() {
        val archive = sampleArchive()
        val encoded = codec.encodeArchive(archive)
        val decoded = codec.readArchive(encoded.inputStream())

        assertEquals(archive.manifest, decoded.manifest)
        assertArrayEquals(databaseSnapshot, decoded.databaseSnapshot)
        assertArrayEquals(
            "annex".encodeToByteArray(),
            decoded.attachmentBytes.getValue("attachments/annex.bin"),
        )
    }

    @Test
    fun encryptedArchiveUsesExistingAuthenticatedEnvelope() {
        val encoded = codec.encodeEncryptedArchive(sampleArchive(), "sync-passphrase".toCharArray())

        assertTrue(codec.isEncrypted(encoded))
        assertFalse(encoded.decodeToString().contains("room database snapshot"))

        val decoded = codec.readEncryptedArchive(
            input = encoded.inputStream(),
            password = "sync-passphrase".toCharArray(),
            maximumEncryptedBytes = encoded.size.toLong(),
        )
        assertArrayEquals(databaseSnapshot, decoded.databaseSnapshot)
    }


    @Test
    fun streamingPreviewAndStageAvoidWholeArchiveHeapMaterialization() {
        val archive = sampleArchive()
        val password = "sync-passphrase".toCharArray()
        val encrypted = codec.encodeEncryptedArchive(archive, password)
        password.fill('\u0000')
        val root = kotlin.io.path.createTempDirectory("studyflow-sync-ov07-").toFile()
        val plaintext = java.io.File(root, "authenticated.zip")
        val staging = java.io.File(root, "stage")
        try {
            val readPassword = "sync-passphrase".toCharArray()
            encrypted.inputStream().use { input ->
                plaintext.outputStream().use { output ->
                    codec.decryptEncryptedArchive(input, output, readPassword, encrypted.size.toLong())
                }
            }
            readPassword.fill('\u0000')

            val preview = plaintext.inputStream().use(codec::readManifestStreaming)
            assertEquals(archive.manifest, preview)

            val staged = plaintext.inputStream().use { codec.stageArchive(it, staging) }
            assertEquals(archive.manifest, staged.manifest)
            assertArrayEquals(databaseSnapshot, staged.databaseSnapshotFile.readBytes())
            assertArrayEquals(
                "annex".encodeToByteArray(),
                staged.attachmentFiles.getValue("attachments/annex.bin").readBytes(),
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test(expected = StudyPlanPackageException::class)
    fun manifestRejectsRawRemoteUploadDeclaration() {
        val archive = sampleArchive(encryptedForRemote = false)
        codec.encodeEncryptedArchive(archive, "sync-passphrase".toCharArray())
    }

    @Test(expected = IllegalArgumentException::class)
    fun writerRejectsDatabaseHashMetadataThatDoesNotMatchSnapshotBytes() {
        val archive = sampleArchive()
        val falseHash = "0".repeat(64)
        val forgedManifest = archive.manifest.copy(
            checkpoint = archive.manifest.checkpoint.copy(databaseHashSha256 = falseHash),
            databaseChecksumSha256 = falseHash,
        )

        codec.encodeArchive(archive.copy(manifest = forgedManifest))
    }

    @Test(expected = IllegalArgumentException::class)
    fun readerRejectsDatabaseHashMetadataThatDoesNotMatchSnapshotBytes() {
        val falseHash = "0".repeat(64)
        val forged = rewriteMetadata(codec.encodeArchive(sampleArchive())) { manifest ->
            manifest.copy(
                checkpoint = manifest.checkpoint.copy(databaseHashSha256 = falseHash),
                databaseChecksumSha256 = falseHash,
            )
        }

        codec.readArchive(forged.inputStream())
    }

    @Test(expected = IllegalArgumentException::class)
    fun writerRejectsDuplicateAttachmentDeclarations() {
        val archive = sampleArchive()
        val duplicate = archive.manifest.attachments.single()

        codec.encodeArchive(
            archive.copy(
                manifest = archive.manifest.copy(attachments = listOf(duplicate, duplicate)),
            ),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun readerRejectsDuplicateAttachmentDeclarations() {
        val forged = rewriteMetadata(codec.encodeArchive(sampleArchive())) { manifest ->
            val duplicate = manifest.attachments.single()
            manifest.copy(attachments = listOf(duplicate, duplicate))
        }

        codec.readArchive(forged.inputStream())
    }

    @Test(expected = IllegalArgumentException::class)
    fun writerRejectsArchiveWhoseDeclaredTotalExceedsReaderBudget() {
        val attachmentSize = 64L * 1024L * 1024L
        val attachments = (1..8).map { index ->
            StudyFlowSyncAttachment(
                entryName = "attachments/oversized-total-$index.bin",
                byteSize = attachmentSize,
                checksumSha256 = sha256(ByteArray(0)),
            )
        }
        val archive = archiveWithDeclaredAttachments(attachments)

        codec.encodeArchive(archive)
    }

    @Test(expected = IllegalArgumentException::class)
    fun writerRejectsDeclaredEntryLargerThanReaderBudget() {
        val attachment = StudyFlowSyncAttachment(
            entryName = "attachments/oversized-entry.bin",
            byteSize = 64L * 1024L * 1024L + 1L,
            checksumSha256 = sha256(ByteArray(0)),
        )
        val archive = archiveWithDeclaredAttachments(listOf(attachment))

        codec.encodeArchive(archive)
    }

    @Test(expected = IllegalArgumentException::class)
    fun writerRejectsArchiveWhoseEntryCountExceedsReaderBudget() {
        val emptyHash = sha256(ByteArray(0))
        val attachments = (1..2_045).map { index ->
            StudyFlowSyncAttachment(
                entryName = "attachments/entry-$index.bin",
                byteSize = 0L,
                checksumSha256 = emptyHash,
            )
        }
        val archive = archiveWithDeclaredAttachments(attachments)

        codec.encodeArchive(archive)
    }

    private fun sampleArchive(encryptedForRemote: Boolean = true): StudyFlowSyncBundleArchive {
        val checkpoint = checkpoint()
        val attachmentBytes = mapOf("attachments/annex.bin" to "annex".encodeToByteArray())
        val manifest = codec.buildManifest(
            applicationVersion = "1.0",
            checkpoint = checkpoint,
            databaseSnapshot = databaseSnapshot,
            encryptedForRemote = encryptedForRemote,
            attachments = attachmentBytes,
        )
        return StudyFlowSyncBundleArchive(
            manifest = manifest,
            databaseSnapshot = databaseSnapshot,
            attachmentBytes = attachmentBytes,
        )
    }

    private fun archiveWithDeclaredAttachments(
        attachments: List<StudyFlowSyncAttachment>,
    ): StudyFlowSyncBundleArchive {
        val manifest = StudyFlowSyncBundleManifest(
            applicationVersion = "1.0",
            checkpoint = checkpoint(),
            databaseByteSize = databaseSnapshot.size.toLong(),
            databaseChecksumSha256 = sha256(databaseSnapshot),
            attachments = attachments,
        )
        val sources: Map<String, () -> InputStream> = attachments.associate { attachment ->
            attachment.entryName to { ByteArrayInputStream(ByteArray(0)) }
        }
        return StudyFlowSyncBundleArchive(
            manifest = manifest,
            databaseSnapshot = databaseSnapshot,
            attachmentStreams = sources,
        )
    }

    private fun checkpoint(): StudyFlowSyncCheckpoint = StudyFlowSyncCheckpoint(
        id = "checkpoint-1",
        deviceId = "device-1",
        deviceName = "Tablet",
        createdAtEpochMillis = 1_000L,
        databaseHashSha256 = sha256(databaseSnapshot),
        changeCount = 4,
        graphRevision = 7L,
    )

    private fun rewriteMetadata(
        encoded: ByteArray,
        transform: (StudyFlowSyncBundleManifest) -> StudyFlowSyncBundleManifest,
    ): ByteArray {
        val entries = readZipEntries(encoded).toMutableMap()
        val manifest = json.decodeFromString<StudyFlowSyncBundleManifest>(
            entries.getValue(StudyFlowSyncBundleCodec.ManifestEntry).decodeToString(),
        )
        val rewrittenManifest = transform(manifest)
        val manifestBytes = json.encodeToString(rewrittenManifest).encodeToByteArray()
        val checkpointBytes = json.encodeToString(rewrittenManifest.checkpoint).encodeToByteArray()
        entries[StudyFlowSyncBundleCodec.ManifestEntry] = manifestBytes
        entries[StudyFlowSyncBundleCodec.CheckpointEntry] = checkpointBytes

        val integrity = json.decodeFromString<StudyFlowSyncIntegrity>(
            entries.getValue(StudyFlowSyncBundleCodec.IntegrityEntry).decodeToString(),
        )
        val checksums = integrity.entries.toMutableMap().apply {
            this[StudyFlowSyncBundleCodec.ManifestEntry] = sha256(manifestBytes)
            this[StudyFlowSyncBundleCodec.CheckpointEntry] = sha256(checkpointBytes)
        }
        entries[StudyFlowSyncBundleCodec.IntegrityEntry] = json.encodeToString(
            integrity.copy(entries = checksums),
        ).encodeToByteArray()
        return writeZipEntries(entries)
    }

    private fun readZipEntries(encoded: ByteArray): LinkedHashMap<String, ByteArray> {
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(encoded.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) {
                    entries[entry.name] = zip.readBytes()
                }
                zip.closeEntry()
            }
        }
        return entries
    }

    private fun writeZipEntries(entries: Map<String, ByteArray>): ByteArray =
        ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { zip ->
                entries.forEach { (name, bytes) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
        }.toByteArray()

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte) }
}
