package com.mikeyphw.studyflow.core.portability

import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class StudyPlanPrivacyPolicyTest {
    @Test
    fun metadataPrivacyRemovesPlanIdentityFromManifestAndRootData() {
        val options = StudyPlanExportOptions(metadataPrivacyMode = true)
        val root = rootRow(
            name = "My civil service plan",
            description = "Private target",
            examDate = "2026-10-01",
        )

        val sanitized = StudyPlanPrivacyPolicy.sanitizeRoot(root, options)

        assertEquals(StudyPlanPrivacyPolicy.PrivateDisplayName, sanitized.text("name"))
        assertEquals("", sanitized.text("description"))
        assertEquals(PortableValueType.NULL, sanitized.values.getValue("examDateIso").type)
        assertTrue(StudyPlanPrivacyPolicy.MetadataSection in StudyPlanPrivacyPolicy.redactedSections(options))
    }

    @Test
    fun privateIdentifiersAreStableInsideOneExportButUnlinkableAcrossExports() {
        val rawIdentifier = "question-secret-123"
        val first = StudyPlanPrivacyPolicy.pseudonymizeIdentifier("private-export-a", rawIdentifier)
        val repeated = StudyPlanPrivacyPolicy.pseudonymizeIdentifier("private-export-a", rawIdentifier)
        val secondExport = StudyPlanPrivacyPolicy.pseudonymizeIdentifier("private-export-b", rawIdentifier)

        assertEquals(first, repeated)
        assertFalse(first == secondExport)
        assertFalse(first.contains(rawIdentifier))
        assertTrue(first.startsWith("private-"))
        assertTrue(StudyPlanPrivacyPolicy.isPrivateIdentifier(first))
        assertFalse(StudyPlanPrivacyPolicy.isPrivateIdentifier("question-secret-123"))
    }

    @Test
    fun noteAndHistoryRedactionDeclareEveryExcludedTable() {
        val excluded = StudyPlanPrivacyPolicy.excludedTableNames(
            StudyPlanExportOptions(redactNotes = true, redactHistory = true),
        )

        assertTrue("study_notes" in excluded)
        assertEquals(StudyPlanPrivacyPolicy.HistoryTableNames + "study_notes", excluded)
    }

    @Test
    fun historyRedactionClearsEmbeddedProgressInRetainedTables() {
        val options = StudyPlanExportOptions(redactHistory = true)
        val subject = StudyPlanPrivacyPolicy.sanitizeRow(
            "subjects",
            row(
                "progressPercent" to integer(75),
                "accuracyPercent" to integer(82),
                "status" to text("STUDYING"),
            ),
            options,
        )
        val material = StudyPlanPrivacyPolicy.sanitizeRow(
            "study_materials",
            row("completedUnits" to integer(9)),
            options,
        )
        val goal = StudyPlanPrivacyPolicy.sanitizeRow(
            "study_goals",
            row("currentValue" to real(8.0), "completedAtEpochMillis" to integer(50)),
            options,
        )

        assertEquals("0", subject.values.getValue("progressPercent").value)
        assertEquals("0", subject.values.getValue("accuracyPercent").value)
        assertEquals("NOT_STARTED", subject.text("status"))
        assertEquals("0", material.values.getValue("completedUnits").value)
        assertEquals("0.0", goal.values.getValue("currentValue").value)
        assertEquals(PortableValueType.NULL, goal.values.getValue("completedAtEpochMillis").type)
        assertTrue(StudyPlanPrivacyPolicy.HistorySection in StudyPlanPrivacyPolicy.redactedSections(options))
    }

    @Test
    fun privacyValidatorAcceptsACompletelyRedactedPayload() {
        val payload = privatePayload(subjectProgress = 0)

        StudyPlanPrivacyPolicy.validatePayload(payload)

        val warnings = StudyPlanPortabilityWarnings.forManifest(payload.manifest)
        assertTrue(warnings.any { it.type == StudyPlanPortabilityWarningType.PRIVACY_REDACTIONS })
    }

    @Test
    fun privacyValidatorRejectsProgressHiddenInARedactedPackage() {
        try {
            StudyPlanPrivacyPolicy.validatePayload(privatePayload(subjectProgress = 40))
            fail("History-redacted payload should not retain subject progress")
        } catch (_: IllegalArgumentException) {
            // Expected privacy-integrity failure.
        }
    }


    @Test
    fun metadataPrivateAnnexPayloadPackageUsesPrivateAnnexIdAndEntryPath() {
        val rawAnnexId = "annex-secret-123"
        val privatePlanId = "private-0123456789abcdefabcd"
        val privateAnnexId = StudyPlanPrivacyPolicy.pseudonymizeIdentifier(privatePlanId, rawAnnexId)
        val entryName = "annex-payloads/$privateAnnexId/payload.bin"
        val annexBytes = "private annex bytes".encodeToByteArray()
        val payload = privatePayloadWithAnnex(
            planId = privatePlanId,
            annexId = privateAnnexId,
            entryName = entryName,
            annexBytes = annexBytes,
        )

        StudyPlanPrivacyPolicy.validatePayload(payload)
        val packageBytes = StudyPlanPackageCodec().toByteArray(
            StudyPlanPackageArchive(payload, mapOf(entryName to annexBytes)),
        )
        val decoded = StudyPlanPackageCodec().archiveFromByteArray(packageBytes)
        val declared = decoded.payload.manifest.annexPayloads.single()

        assertEquals(privateAnnexId, declared.annexId)
        assertEquals(entryName, declared.entryName)
        assertFalse(declared.annexId.contains(rawAnnexId))
        assertFalse(declared.entryName.contains(rawAnnexId))
        assertTrue(entryName in zipEntryNames(packageBytes))
        assertFalse(zipEntryNames(packageBytes).any { rawAnnexId in it })
        assertArrayEquals(annexBytes, decoded.annexPayloadBytes.getValue(entryName))
    }

    @Test
    fun metadataPrivateAnnexPayloadValidatorRejectsRawAnnexIds() {
        val rawAnnexId = "annex-secret-123"
        try {
            StudyPlanPrivacyPolicy.validatePayload(
                privatePayloadWithAnnex(
                    planId = "private-0123456789abcdefabcd",
                    annexId = rawAnnexId,
                    entryName = "annex-payloads/$rawAnnexId/payload.bin",
                    annexBytes = "private annex bytes".encodeToByteArray(),
                ),
            )
            fail("Metadata-private annex payloads should not expose raw annex IDs")
        } catch (_: IllegalArgumentException) {
            // Expected privacy-integrity failure.
        }
    }

    @Test
    fun privateStrippedModeAlwaysRemovesHistoryEvenWithoutExplicitHistoryFlag() {
        val options = StudyPlanExportOptions(mode = StudyPlanExportMode.PRIVATE_STRIPPED)
        val excluded = StudyPlanPrivacyPolicy.excludedTableNames(options)
        val material = StudyPlanPrivacyPolicy.sanitizeRow(
            "study_materials",
            row("completedUnits" to integer(7)),
            options,
        )

        assertTrue(StudyPlanPrivacyPolicy.HistoryTableNames.all { it in excluded })
        assertEquals("0", material.values.getValue("completedUnits").value)
        assertTrue(StudyPlanPrivacyPolicy.HistorySection in StudyPlanPrivacyPolicy.redactedSections(options))
    }

    @Test
    fun unredactedFullExportLeavesProgressUntouched() {
        val row = row("completedUnits" to integer(3))
        val result = StudyPlanPrivacyPolicy.sanitizeRow(
            "study_materials",
            row,
            StudyPlanExportOptions(),
        )

        assertEquals(row, result)
        assertFalse(StudyPlanPrivacyPolicy.redactedSections(StudyPlanExportOptions()).isNotEmpty())
        assertNull(result.values["missing"])
    }


    private fun privatePayloadWithAnnex(
        planId: String,
        annexId: String,
        entryName: String,
        annexBytes: ByteArray,
    ): StudyPlanPackagePayload {
        val base = privatePayload(subjectProgress = 0)
        val annexPayload = PortableAnnexPayload(
            annexId = annexId,
            entryName = entryName,
            fileName = "private-annex.bin",
            mimeType = "application/pdf",
            byteSize = annexBytes.size.toLong(),
            checksumSha256 = sha256(annexBytes),
        )
        val manifest = base.manifest.copy(
            sourceStudyPlanId = planId,
            includesAnnexMetadata = true,
            includesAnnexPayloads = true,
            annexPayloads = listOf(annexPayload),
            tableRowCounts = base.manifest.tableRowCounts + ("annexes" to 1),
        )
        val annexTable = PortableTable(
            name = "annexes",
            rows = listOf(
                row(
                    "id" to text(annexId),
                    "displayName" to text("private-annex.pdf"),
                    "mimeType" to text("application/pdf"),
                ),
            ),
        )
        return base.copy(manifest = manifest, tables = base.tables + annexTable)
    }

    private fun zipEntryNames(bytes: ByteArray): Set<String> {
        val names = linkedSetOf<String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                names += entry.name
                zip.closeEntry()
            }
        }
        return names
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private fun privatePayload(subjectProgress: Int): StudyPlanPackagePayload {
        val sanitizedRoot = StudyPlanPrivacyPolicy.sanitizeRoot(
            rootRow(
                name = "Secret plan",
                description = "Secret description",
                examDate = "2026-10-01",
            ),
            StudyPlanExportOptions(metadataPrivacyMode = true, redactHistory = true),
        )
        val root = sanitizedRoot.copy(
            values = sanitizedRoot.values + ("id" to text("private-0123456789abcdefabcd")),
        )
        val subject = row(
            "progressPercent" to integer(subjectProgress),
            "accuracyPercent" to integer(0),
            "status" to text("NOT_STARTED"),
        )
        val tables = listOf(
            PortableTable("study_plans", listOf(root)),
            PortableTable("subjects", listOf(subject)),
        )
        return StudyPlanPackagePayload(
            manifest = StudyPlanPackageManifest(
                applicationVersion = StudyPlanPrivacyPolicy.PrivateApplicationVersion,
                exportedAtEpochMillis = 0L,
                sourceStudyPlanId = "private-0123456789abcdefabcd",
                displayName = StudyPlanPrivacyPolicy.PrivateDisplayName,
                exportMode = StudyPlanExportMode.FULL,
                includesPrivateHistory = false,
                includesAnnexMetadata = false,
                tableRowCounts = tables.associate { it.name to it.rows.size },
                metadataPrivacyMode = true,
                redactedSections = setOf(
                    StudyPlanPrivacyPolicy.MetadataSection,
                    StudyPlanPrivacyPolicy.NotesSection,
                    StudyPlanPrivacyPolicy.HistorySection,
                ),
            ),
            tables = tables,
        )
    }

    private fun rootRow(name: String, description: String, examDate: String) = row(
        "id" to text("plan-id"),
        "name" to text(name),
        "description" to text(description),
        "examDateIso" to text(examDate),
        "status" to text("ACTIVE"),
        "progressPercent" to integer(55),
        "studiedMinutesThisWeek" to integer(180),
        "questionsAnswered" to integer(20),
        "averageAccuracyPercent" to integer(70),
        "trashedAtEpochMillis" to nullValue(),
    )

    private fun row(vararg values: Pair<String, PortableValue>) = PortableRow(mapOf(*values))
    private fun text(value: String) = PortableValue(PortableValueType.TEXT, value)
    private fun integer(value: Number) = PortableValue(PortableValueType.INTEGER, value.toLong().toString())
    private fun real(value: Number) = PortableValue(PortableValueType.REAL, value.toDouble().toString())
    private fun nullValue() = PortableValue(PortableValueType.NULL)
    private fun PortableRow.text(name: String): String = values.getValue(name).value.orEmpty()
}
