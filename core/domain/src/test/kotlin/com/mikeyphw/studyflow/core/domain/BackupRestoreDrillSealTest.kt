package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.portability.StudyPlanBackupIntegrityReport
import com.mikeyphw.studyflow.core.portability.StudyPlanEncryptedPackageCodec
import com.mikeyphw.studyflow.core.portability.StudyPlanImportStrategy
import com.mikeyphw.studyflow.core.portability.StudyPlanPackageCodec
import com.mikeyphw.studyflow.core.portability.StudyPlanPortabilityWarnings
import com.mikeyphw.studyflow.core.portability.StudyPlanRestoreDryRun
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupRestoreDrillSealTest {
    private val codec = StudyPlanPackageCodec()
    private val encryptedCodec = StudyPlanEncryptedPackageCodec()

    @Test
    fun packageDrillPassesEveryRequiredScenarioUsingRealDryRunCallback() = runBlocking {
        val fixture = BackupRestoreDrillSeal.metadataPrivateAnnexFixture()
        var dryRunCalled = false
        val report = BackupRestoreDrillSeal.runPackageDrills(
            generatedAtEpochMillis = 28L,
            encryptedPackageBytes = fixture.encryptedPackageBytes,
            password = fixture.password,
            wrongPassword = fixture.wrongPassword,
            rawAnnexId = fixture.rawAnnexId,
            inspectBackup = ::inspectBackupThroughCodec,
            dryRunImport = { bytes, strategy, targetPlanId, password ->
                dryRunCalled = true
                dryRunThroughCodec(bytes, strategy, targetPlanId, password)
            },
        )

        assertTrue("restore dry-run callback must be exercised", dryRunCalled)
        assertEquals(BackupRestoreDrillKind.entries.size, report.results.size)
        assertEquals(BackupRestoreDrillKind.entries.toSet(), report.results.map { it.kind }.toSet())
        assertEquals(0, report.blockedCount)
        assertTrue(report.releaseReady)
        assertTrue(report.recoveryReport.any { line -> "Dry-run database writes: 0" in line })
        fixture.clearPasswords()
    }

    @Test
    fun drillResultsStayNonDestructive() = runBlocking {
        val fixture = BackupRestoreDrillSeal.metadataPrivateAnnexFixture()
        val report = BackupRestoreDrillSeal.runPackageDrills(
            generatedAtEpochMillis = 28L,
            encryptedPackageBytes = fixture.encryptedPackageBytes,
            password = fixture.password,
            wrongPassword = fixture.wrongPassword,
            rawAnnexId = fixture.rawAnnexId,
            inspectBackup = ::inspectBackupThroughCodec,
            dryRunImport = ::dryRunThroughCodec,
        )

        assertFalse(report.results.any { result -> result.destructive })
        assertTrue(report.results.all { result -> result.passed })
        fixture.clearPasswords()
    }

    @Test
    fun dryRunDrillBlocksWhenDryRunWritesRows() = runBlocking {
        val fixture = BackupRestoreDrillSeal.metadataPrivateAnnexFixture()
        val report = BackupRestoreDrillSeal.runPackageDrills(
            generatedAtEpochMillis = 28L,
            encryptedPackageBytes = fixture.encryptedPackageBytes,
            password = fixture.password,
            wrongPassword = fixture.wrongPassword,
            rawAnnexId = fixture.rawAnnexId,
            inspectBackup = ::inspectBackupThroughCodec,
            dryRunImport = { bytes, strategy, targetPlanId, password ->
                dryRunThroughCodec(bytes, strategy, targetPlanId, password).copy(databaseRowsChanged = 1)
            },
        )

        assertFalse(report.releaseReady)
        assertEquals(
            BackupRestoreDrillStatus.BLOCKED,
            report.results.single { it.kind == BackupRestoreDrillKind.RESTORE_DRY_RUN }.status,
        )
        fixture.clearPasswords()
    }

    @Test
    fun `annex privacy drill is not applicable when package declares no annex payloads`() = runBlocking {
        val fixture = BackupRestoreDrillSeal.metadataPrivateAnnexFixture()
        val original = codec.archiveFromByteArray(encryptedCodec.decrypt(fixture.encryptedPackageBytes,
            fixture.password))
        val withoutAnnex = original.copy(
            payload = original.payload.copy(
                manifest = original.payload.manifest.copy(
                    includesAnnexPayloads = false,
                    annexPayloads = emptyList(),
                ),
            ),
            annexPayloadBytes = emptyMap(),
            annexPayloadStreams = emptyMap(),
        )
        val encrypted = encryptedCodec.encrypt(codec.toByteArray(withoutAnnex), fixture.password)
        val report = BackupRestoreDrillSeal.runPackageDrills(
            generatedAtEpochMillis = 28L,
            encryptedPackageBytes = encrypted,
            password = fixture.password,
            wrongPassword = fixture.wrongPassword,
            rawAnnexId = fixture.rawAnnexId,
            inspectBackup = ::inspectBackupThroughCodec,
            dryRunImport = ::dryRunThroughCodec,
        )

        assertEquals(
            BackupRestoreDrillStatus.NOT_APPLICABLE,
            report.results.single { it.kind == BackupRestoreDrillKind.METADATA_PRIVATE_ANNEX }.status,
        )
        assertEquals(1, report.notApplicableCount)
        assertTrue(report.releaseReady)
        fixture.clearPasswords()
    }

    @Test(expected = CancellationException::class)
    fun `drill cancellation is never converted into blocked evidence`() {
        runBlocking {
            val fixture = BackupRestoreDrillSeal.metadataPrivateAnnexFixture()
            try {
                BackupRestoreDrillSeal.runPackageDrills(
                    generatedAtEpochMillis = 28L,
                    encryptedPackageBytes = fixture.encryptedPackageBytes,
                    password = fixture.password,
                    wrongPassword = fixture.wrongPassword,
                    rawAnnexId = fixture.rawAnnexId,
                    inspectBackup = { _, _ -> throw CancellationException("cancel") },
                    dryRunImport = ::dryRunThroughCodec,
                )
            } finally {
                fixture.clearPasswords()
            }
        }
    }

    private fun inspectBackupThroughCodec(
        bytes: ByteArray,
        password: CharArray?,
    ): StudyPlanBackupIntegrityReport {
        val archive = codec.archiveFromByteArray(encryptedCodec.decrypt(bytes, requireNotNull(password)))
        val manifest = archive.payload.manifest
        return StudyPlanBackupIntegrityReport(
            encrypted = true,
            schemaVersion = manifest.schemaVersion,
            checksumVerified = true,
            totalRows = archive.payload.tables.sumOf { table -> table.rows.size },
            annexPayloadCount = manifest.annexPayloads.size,
            metadataPrivacyMode = manifest.metadataPrivacyMode,
            privateIdentifiersVerified = true,
            redactedSections = manifest.redactedSections,
            warnings = StudyPlanPortabilityWarnings.forManifest(manifest),
            canRestore = true,
        )
    }

    private fun dryRunThroughCodec(
        bytes: ByteArray,
        strategy: StudyPlanImportStrategy,
        targetPlanId: StudyPlanId?,
        password: CharArray?,
    ): StudyPlanRestoreDryRun {
        val archive = codec.archiveFromByteArray(encryptedCodec.decrypt(bytes, requireNotNull(password)))
        return StudyPlanRestoreDryRun(
            strategy = strategy,
            targetStudyPlanId = targetPlanId?.value,
            totalRows = archive.payload.tables.sumOf { table -> table.rows.size },
            tableRowCounts = archive.payload.manifest.tableRowCounts,
            encrypted = true,
            wouldCreateSafetySnapshot = strategy != StudyPlanImportStrategy.CREATE_AS_NEW,
            warnings = StudyPlanPortabilityWarnings.forManifest(archive.payload.manifest),
            databaseRowsChanged = 0,
        )
    }
}
