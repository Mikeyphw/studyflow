package com.mikeyphw.studyflow.core.portability

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyPlanPackageCodecAnnexPayloadTest {
    private val codec = StudyPlanPackageCodec()

    @Test
    fun archiveRoundTripPreservesAnnexPayloadBytes() {
        val bytes = "annex body".encodeToByteArray()
        val payloadEntry = PortableAnnexPayload(
            annexId = "annex-1",
            entryName = "annex-payloads/annex-1/file.txt",
            fileName = "file.txt",
            mimeType = "text/plain",
            byteSize = bytes.size.toLong(),
            checksumSha256 = "27b26252692cfc34e80c65150fc0b150487ed3ee4269920a991755f743a71799",
        )
        val payload = samplePayload(payloadEntry)
        val encoded = codec.toByteArray(StudyPlanPackageArchive(payload, mapOf(payloadEntry.entryName to bytes)))
        val decoded = codec.archiveFromByteArray(encoded)
        assertEquals(payload.manifest, decoded.payload.manifest)
        assertArrayEquals(bytes, decoded.annexPayloadBytes.getValue(payloadEntry.entryName))
    }

    @Test
    fun archiveWriterAcceptsStreamingAnnexPayloadSource() {
        val bytes = "annex body".encodeToByteArray()
        val payloadEntry = PortableAnnexPayload(
            annexId = "annex-1",
            entryName = "annex-payloads/annex-1/file.txt",
            fileName = "file.txt",
            mimeType = "text/plain",
            byteSize = bytes.size.toLong(),
            checksumSha256 = "27b26252692cfc34e80c65150fc0b150487ed3ee4269920a991755f743a71799",
        )
        val payload = samplePayload(payloadEntry)
        var opened = 0
        val archive = StudyPlanPackageArchive(
            payload = payload,
            annexPayloadStreams = mapOf(payloadEntry.entryName to {
                opened += 1
                bytes.inputStream()
            }),
        )

        val decoded = codec.archiveFromByteArray(codec.toByteArray(archive))

        assertEquals(1, opened)
        assertArrayEquals(bytes, decoded.annexPayloadBytes.getValue(payloadEntry.entryName))
    }


    @Test
    fun streamingReaderSpoolsAnnexPayloadWithoutByteArrayMaterialization() {
        val bytes = ByteArray(2 * 1024 * 1024) { index -> (index % 251).toByte() }
        val checksum = java.security.MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
        val payloadEntry = PortableAnnexPayload(
            annexId = "annex-large",
            entryName = "annex-payloads/annex-large/payload.bin",
            fileName = "payload.bin",
            mimeType = "application/octet-stream",
            byteSize = bytes.size.toLong(),
            checksumSha256 = checksum,
        )
        val payload = samplePayload(payloadEntry)
        val encoded = codec.toByteArray(
            StudyPlanPackageArchive(payload, mapOf(payloadEntry.entryName to bytes)),
        )
        val staging = kotlin.io.path.createTempDirectory("studyflow-package-stream-").toFile()
        try {
            val decoded = codec.readArchiveStreaming(encoded.inputStream(), staging)
            assertTrue(decoded.annexPayloadBytes.isEmpty())
            assertEquals(setOf(payloadEntry.entryName), decoded.annexPayloadStreams.keys)
            assertArrayEquals(bytes, decoded.openAnnexPayload(payloadEntry.entryName).use { it.readBytes() })
            assertEquals(1, staging.listFiles().orEmpty().size)
        } finally {
            staging.deleteRecursively()
        }
    }


    @Test(expected = StudyPlanPackageException::class)
    fun writerRejectsDuplicateAnnexIdsEvenWhenEntryNamesDiffer() {
        val first = PortableAnnexPayload(
            annexId = "annex-duplicate",
            entryName = "annex-payloads/one/payload.bin",
            fileName = "one.bin",
            mimeType = "application/octet-stream",
            byteSize = 0L,
            checksumSha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
        )
        val second = first.copy(entryName = "annex-payloads/two/payload.bin", fileName = "two.bin")
        codec.toByteArray(
            StudyPlanPackageArchive(
                payload = samplePayload(listOf(first, second)),
                annexPayloadBytes = mapOf(first.entryName to byteArrayOf(), second.entryName to byteArrayOf()),
            ),
        )
    }

    @Test(expected = StudyPlanPackageException::class)
    fun writerRejectsArchiveWhoseEntryCountWouldExceedReaderLimit() {
        val emptyChecksum = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        val payloads = (0 until 510).map { index ->
            PortableAnnexPayload(
                annexId = "annex-$index",
                entryName = "annex-payloads/annex-$index/payload.bin",
                fileName = "file-$index.bin",
                mimeType = "application/octet-stream",
                byteSize = 0L,
                checksumSha256 = emptyChecksum,
            )
        }
        val archive = StudyPlanPackageArchive(
            payload = samplePayload(payloads),
            annexPayloadBytes = payloads.associate { it.entryName to byteArrayOf() },
        )

        codec.toByteArray(archive)
    }

    @Test(expected = StudyPlanPackageException::class)
    fun writerRejectsDeclaredArchiveLargerThanReaderTotalLimitBeforeOpeningStreams() {
        val checksum = "0".repeat(64)
        val payloads = (0 until 9).map { index ->
            PortableAnnexPayload(
                annexId = "annex-$index",
                entryName = "annex-payloads/annex-$index/payload.bin",
                fileName = "file-$index.bin",
                mimeType = "application/octet-stream",
                byteSize = 64L * 1024L * 1024L,
                checksumSha256 = checksum,
            )
        }
        val archive = StudyPlanPackageArchive(
            payload = samplePayload(payloads),
            annexPayloadStreams = payloads.associate { payload ->
                payload.entryName to { error("oversized archive should fail before opening streams") }
            },
        )

        codec.toByteArray(archive)
    }

    private fun samplePayload(payload: PortableAnnexPayload) = samplePayload(listOf(payload))

    private fun samplePayload(payloads: List<PortableAnnexPayload>) = StudyPlanPackagePayload(
        manifest = StudyPlanPackageManifest(
            applicationVersion = "test",
            exportedAtEpochMillis = 1L,
            sourceStudyPlanId = "plan-1",
            displayName = "Plan",
            exportMode = StudyPlanExportMode.FULL,
            includesPrivateHistory = true,
            includesAnnexMetadata = true,
            includesAnnexPayloads = true,
            annexPayloads = payloads,
            tableRowCounts = mapOf("study_plans" to 1, "annexes" to payloads.size),
        ),
        tables = listOf(
            PortableTable(
                name = "study_plans",
                rows = listOf(PortableRow(mapOf("id" to PortableValue(PortableValueType.TEXT, "plan-1")))),
            ),
            PortableTable(
                name = "annexes",
                rows = payloads.map { payload ->
                    PortableRow(mapOf("id" to PortableValue(PortableValueType.TEXT, payload.annexId)))
                },
            ),
        ),
    )
}
