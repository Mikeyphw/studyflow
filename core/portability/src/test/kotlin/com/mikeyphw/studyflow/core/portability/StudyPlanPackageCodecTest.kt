package com.mikeyphw.studyflow.core.portability

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class StudyPlanPackageCodecTest {
    private val codec = StudyPlanPackageCodec()

    @Test
    fun roundTripPreservesManifestAndRows() {
        val payload = samplePayload()
        assertEquals(payload, codec.fromByteArray(codec.toByteArray(payload)))
    }


    @Test
    fun rejectsPrivacyMetadataThatConflictsWithExportMode() {
        val payload = samplePayload().copy(
            manifest = samplePayload().manifest.copy(includesPrivateHistory = false),
        )
        assertThrows(StudyPlanPackageException::class.java) {
            codec.toByteArray(payload)
        }
    }

    @Test
    fun rejectsUnsafeZipEntry() {
        val bytes = ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry("../manifest.json"))
                zip.write("{}".encodeToByteArray())
                zip.closeEntry()
            }
        }.toByteArray()
        assertThrows(StudyPlanPackageException::class.java) {
            codec.read(ByteArrayInputStream(bytes))
        }
    }

    @Test
    fun rejectsArchivesWithTooManyEntries() {
        val bytes = ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { zip ->
                repeat(513) { index ->
                    zip.putNextEntry(ZipEntry("entry-$index.bin"))
                    zip.write(byteArrayOf(1))
                    zip.closeEntry()
                }
            }
        }.toByteArray()

        assertThrows(StudyPlanPackageException::class.java) {
            codec.read(ByteArrayInputStream(bytes))
        }
    }

    private fun samplePayload() = StudyPlanPackagePayload(
        manifest = StudyPlanPackageManifest(
            applicationVersion = "test",
            exportedAtEpochMillis = 1L,
            sourceStudyPlanId = "plan-1",
            displayName = "Plan",
            exportMode = StudyPlanExportMode.FULL,
            includesPrivateHistory = true,
            includesAnnexMetadata = false,
            tableRowCounts = mapOf("study_plans" to 1),
        ),
        tables = listOf(
            PortableTable(
                name = "study_plans",
                rows = listOf(PortableRow(mapOf("id" to PortableValue(PortableValueType.TEXT, "plan-1")))),
            ),
        ),
    )
}
