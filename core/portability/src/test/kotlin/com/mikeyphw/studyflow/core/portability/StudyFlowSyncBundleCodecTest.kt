package com.mikeyphw.studyflow.core.portability

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowSyncBundleCodecTest {
    private val codec = StudyFlowSyncBundleCodec()
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

    @Test(expected = StudyPlanPackageException::class)
    fun manifestRejectsRawRemoteUploadDeclaration() {
        val archive = sampleArchive(encryptedForRemote = false)
        codec.encodeEncryptedArchive(archive, "sync-passphrase".toCharArray())
    }

    private fun sampleArchive(encryptedForRemote: Boolean = true): StudyFlowSyncBundleArchive {
        val checkpoint = StudyFlowSyncCheckpoint(
            id = "checkpoint-1",
            deviceId = "device-1",
            deviceName = "Tablet",
            createdAtEpochMillis = 1_000L,
            databaseHashSha256 = sha256(databaseSnapshot),
            changeCount = 4,
            graphRevision = 7L,
        )
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

    private fun sha256(bytes: ByteArray): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte) }
}
