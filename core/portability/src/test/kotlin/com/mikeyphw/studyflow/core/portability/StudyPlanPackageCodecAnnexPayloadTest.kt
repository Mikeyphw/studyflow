package com.mikeyphw.studyflow.core.portability

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
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

    private fun samplePayload(payload: PortableAnnexPayload) = StudyPlanPackagePayload(
        manifest = StudyPlanPackageManifest(
            applicationVersion = "test",
            exportedAtEpochMillis = 1L,
            sourceStudyPlanId = "plan-1",
            displayName = "Plan",
            exportMode = StudyPlanExportMode.FULL,
            includesPrivateHistory = true,
            includesAnnexMetadata = true,
            includesAnnexPayloads = true,
            annexPayloads = listOf(payload),
            tableRowCounts = mapOf("study_plans" to 1, "annexes" to 1),
        ),
        tables = listOf(
            PortableTable(
                name = "study_plans",
                rows = listOf(PortableRow(mapOf("id" to PortableValue(PortableValueType.TEXT, "plan-1")))),
            ),
            PortableTable(
                name = "annexes",
                rows = listOf(PortableRow(mapOf("id" to PortableValue(PortableValueType.TEXT, "annex-1")))),
            ),
        ),
    )
}
