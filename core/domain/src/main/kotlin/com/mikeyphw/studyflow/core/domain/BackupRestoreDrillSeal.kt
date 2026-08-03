package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.portability.PortableAnnexPayload
import com.mikeyphw.studyflow.core.portability.PortableRow
import com.mikeyphw.studyflow.core.portability.PortableTable
import com.mikeyphw.studyflow.core.portability.PortableValue
import com.mikeyphw.studyflow.core.portability.PortableValueType
import com.mikeyphw.studyflow.core.portability.StudyPlanBackupIntegrityReport
import com.mikeyphw.studyflow.core.portability.StudyPlanEncryptedPackageCodec
import com.mikeyphw.studyflow.core.portability.StudyPlanExportMode
import com.mikeyphw.studyflow.core.portability.StudyPlanExportOptions
import com.mikeyphw.studyflow.core.portability.StudyPlanImportStrategy
import com.mikeyphw.studyflow.core.portability.StudyPlanPackageArchive
import com.mikeyphw.studyflow.core.portability.StudyPlanPackageCodec
import com.mikeyphw.studyflow.core.portability.StudyPlanPackageManifest
import com.mikeyphw.studyflow.core.portability.StudyPlanPackagePayload
import com.mikeyphw.studyflow.core.portability.StudyPlanPrivacyPolicy
import com.mikeyphw.studyflow.core.portability.StudyPlanRestoreDryRun
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

enum class BackupRestoreDrillKind {
    ENCRYPTED_EXPORT,
    WRONG_PASSWORD,
    TAMPER_DETECTION,
    METADATA_PRIVATE_ANNEX,
    RESTORE_DRY_RUN,
    RETENTION_CLEANUP,
    RECOVERY_REPORT,
}

enum class BackupRestoreDrillStatus {
    PASS,
    BLOCKED,
}

data class BackupRestoreDrillResult(
    val kind: BackupRestoreDrillKind,
    val status: BackupRestoreDrillStatus,
    val evidence: String,
    val destructive: Boolean = false,
) {
    val passed: Boolean = status == BackupRestoreDrillStatus.PASS
}

data class BackupRestoreDrillReport(
    val generatedAtEpochMillis: Long,
    val results: List<BackupRestoreDrillResult>,
    val recoveryReport: List<String>,
) {
    val passedCount: Int = results.count(BackupRestoreDrillResult::passed)
    val blockedCount: Int = results.size - passedCount
    val releaseReady: Boolean = results.isNotEmpty() &&
        blockedCount == 0 &&
        results.none(BackupRestoreDrillResult::destructive)
}

data class BackupRestoreDrillFixture(
    val encryptedPackageBytes: ByteArray,
    val password: CharArray,
    val wrongPassword: CharArray,
    val rawAnnexId: String,
) {
    fun clearPasswords() {
        password.fill('\u0000')
        wrongPassword.fill('\u0000')
    }
}

object BackupRestoreDrillSeal {
    private val packageCodec = StudyPlanPackageCodec()
    private val encryptedCodec = StudyPlanEncryptedPackageCodec()

    fun metadataPrivateAnnexFixture(
        password: CharArray = "correct horse battery staple".toCharArray(),
        wrongPassword: CharArray = "wrong horse battery staple".toCharArray(),
        rawAnnexId: String = "annex-secret-123",
    ): BackupRestoreDrillFixture {
        val archive = metadataPrivateAnnexArchive(rawAnnexId, "private annex bytes".encodeToByteArray())
        val plainBytes = packageCodec.toByteArray(archive)
        val encryptedBytes = encryptedCodec.encrypt(plainBytes, password)
        return BackupRestoreDrillFixture(
            encryptedPackageBytes = encryptedBytes,
            password = password,
            wrongPassword = wrongPassword,
            rawAnnexId = rawAnnexId,
        )
    }

    suspend fun runManagedDrills(
        generatedAtEpochMillis: Long,
        planId: StudyPlanId,
        portability: ManageStudyPlanPortability,
        password: CharArray = "correct horse battery staple".toCharArray(),
        wrongPassword: CharArray = "wrong horse battery staple".toCharArray(),
        options: StudyPlanExportOptions = StudyPlanExportOptions(
            mode = StudyPlanExportMode.PRIVATE_STRIPPED,
            includeAnnexMetadata = true,
            includeAnnexPayloads = true,
            metadataPrivacyMode = true,
            redactNotes = true,
            redactHistory = true,
        ),
    ): BackupRestoreDrillReport {
        val exported = ByteArrayOutputStream().also { output ->
            portability.exportPlan(planId, options, output, password)
        }.toByteArray()
        return runPackageDrills(
            generatedAtEpochMillis = generatedAtEpochMillis,
            encryptedPackageBytes = exported,
            password = password,
            wrongPassword = wrongPassword,
            rawAnnexId = null,
            inspectBackup = { bytes, candidatePassword ->
                portability.inspectBackup(ByteArrayInputStream(bytes), candidatePassword)
            },
            dryRunImport = { bytes, strategy, targetPlanId, candidatePassword ->
                portability.dryRunImport(ByteArrayInputStream(bytes), strategy, targetPlanId, candidatePassword)
            },
        ).also {
            password.fill('\u0000')
            wrongPassword.fill('\u0000')
        }
    }

    suspend fun runPackageDrills(
        generatedAtEpochMillis: Long,
        encryptedPackageBytes: ByteArray,
        password: CharArray,
        wrongPassword: CharArray,
        rawAnnexId: String?,
        retentionPolicy: LocalDataRetentionPolicy = StudyFlowLocalDataRetentionPolicy.current(),
        inspectBackup: suspend (ByteArray, CharArray?) -> StudyPlanBackupIntegrityReport,
        dryRunImport: suspend (ByteArray, StudyPlanImportStrategy, StudyPlanId?, CharArray?) -> StudyPlanRestoreDryRun,
    ): BackupRestoreDrillReport {
        val integrity = runCatching { inspectBackup(encryptedPackageBytes, password) }
        val dryRun = runCatching {
            dryRunImport(
                encryptedPackageBytes,
                StudyPlanImportStrategy.CREATE_AS_NEW,
                null,
                password,
            )
        }
        val decodedArchive = runCatching {
            packageCodec.archiveFromByteArray(encryptedCodec.decrypt(encryptedPackageBytes, password))
        }.getOrNull()
        val wrongPasswordBlocked = runCatching {
            inspectBackup(encryptedPackageBytes, wrongPassword)
        }.isFailure
        val tamperBlocked = runCatching {
            inspectBackup(encryptedPackageBytes.tamperedCopy(), password)
        }.isFailure
        val annexPrivate = decodedArchive?.let { archive ->
            metadataPrivateAnnexInvariantHolds(
                rawAnnexId = rawAnnexId,
                archive = archive,
                packageBytes = encryptedCodec.decrypt(encryptedPackageBytes, password),
            )
        } ?: false
        val dryRunValue = dryRun.getOrNull()
        val retentionReady = retentionCleanupTruthfulnessHolds(retentionPolicy)
        val recoveryReport = recoveryReport(decodedArchive?.payload?.manifest, dryRunValue)
        return BackupRestoreDrillReport(
            generatedAtEpochMillis = generatedAtEpochMillis,
            results = listOf(
                result(
                    BackupRestoreDrillKind.ENCRYPTED_EXPORT,
                    encryptedCodec.isEncrypted(encryptedPackageBytes) && integrity.getOrNull()?.encrypted == true,
                    "Encrypted package is recognized and inspectBackup verifies the authenticated archive.",
                ),
                result(
                    BackupRestoreDrillKind.WRONG_PASSWORD,
                    wrongPasswordBlocked,
                    "Wrong password is rejected by the same backup inspection path used before restore.",
                ),
                result(
                    BackupRestoreDrillKind.TAMPER_DETECTION,
                    tamperBlocked,
                    "Ciphertext tampering fails authenticated backup inspection.",
                ),
                result(
                    BackupRestoreDrillKind.METADATA_PRIVATE_ANNEX,
                    annexPrivate,
                    "Metadata-private annex payload IDs and ZIP paths hide raw database IDs when annex payloads exist.",
                ),
                result(
                    BackupRestoreDrillKind.RESTORE_DRY_RUN,
                    dryRunValue != null &&
                        dryRunValue.databaseRowsChanged == 0 &&
                        dryRunValue.totalRows > 0,
                    "Restore dry-run uses dryRunImport and reports zero database writes before import.",
                ),
                result(
                    BackupRestoreDrillKind.RETENTION_CLEANUP,
                    retentionReady,
                    "Retention cleanup is truthful: exported files are not auto-deleted.",
                ),
                result(
                    BackupRestoreDrillKind.RECOVERY_REPORT,
                    recoveryReport.size >= 4 && recoveryReport.none(String::isBlank),
                    "Recovery report contains human-readable next steps.",
                ),
            ),
            recoveryReport = recoveryReport,
        )
    }

    fun recoveryReport(
        manifest: StudyPlanPackageManifest?,
        dryRun: StudyPlanRestoreDryRun?,
    ): List<String> = listOf(
        "Package schema ${manifest?.schemaVersion ?: "unknown"} for ${manifest?.displayName ?: "unknown package"}.",
        "Rows available for restore: ${dryRun?.totalRows ?: 0}.",
        "Restore strategy checked: ${dryRun?.strategy ?: StudyPlanImportStrategy.CREATE_AS_NEW}.",
        "Dry-run database writes: ${dryRun?.databaseRowsChanged ?: -1}.",
        "If password or tamper checks fail, keep the current local plan and ask for another export.",
    )

    private fun result(
        kind: BackupRestoreDrillKind,
        passed: Boolean,
        evidence: String,
    ): BackupRestoreDrillResult = BackupRestoreDrillResult(
        kind = kind,
        status = if (passed) BackupRestoreDrillStatus.PASS else BackupRestoreDrillStatus.BLOCKED,
        evidence = evidence,
    )

    private fun retentionCleanupTruthfulnessHolds(policy: LocalDataRetentionPolicy): Boolean =
        !policy.automaticDeletionEnabled &&
            LocalRetentionRuleType.EXPORTED_FILES in policy.rules.map { rule -> rule.type }

    private fun metadataPrivateAnnexArchive(
        rawAnnexId: String,
        annexBytes: ByteArray,
    ): StudyPlanPackageArchive {
        val privatePlanId = "private-0123456789abcdefabcd"
        val privateAnnexId = StudyPlanPrivacyPolicy.pseudonymizeIdentifier(privatePlanId, rawAnnexId)
        val entryName = "annex-payloads/$privateAnnexId/private-annex.pdf"
        val manifest = StudyPlanPackageManifest(
            applicationVersion = StudyPlanPrivacyPolicy.PrivateApplicationVersion,
            exportedAtEpochMillis = 0L,
            sourceStudyPlanId = privatePlanId,
            displayName = StudyPlanPrivacyPolicy.PrivateDisplayName,
            exportMode = StudyPlanExportMode.PRIVATE_STRIPPED,
            includesPrivateHistory = false,
            includesAnnexMetadata = true,
            includesAnnexPayloads = true,
            annexPayloads = listOf(
                PortableAnnexPayload(
                    annexId = privateAnnexId,
                    entryName = entryName,
                    fileName = "private-annex.pdf",
                    mimeType = "application/pdf",
                    byteSize = annexBytes.size.toLong(),
                    checksumSha256 = sha256(annexBytes),
                ),
            ),
            tableRowCounts = mapOf("study_plans" to 1, "annexes" to 1),
            metadataPrivacyMode = true,
            redactedSections = StudyPlanPrivacyPolicy.redactedSections(
                StudyPlanExportOptions(metadataPrivacyMode = true, redactHistory = true),
            ),
        )
        val payload = StudyPlanPackagePayload(
            manifest = manifest,
            tables = listOf(
                PortableTable(
                    name = "study_plans",
                    rows = listOf(
                        row(
                            "id" to text(privatePlanId),
                            "name" to text(manifest.displayName),
                            "description" to text(""),
                            "examDateIso" to nullValue(),
                            "status" to text("DRAFT"),
                            "progressPercent" to integer(0),
                            "studiedMinutesThisWeek" to integer(0),
                            "questionsAnswered" to integer(0),
                            "averageAccuracyPercent" to integer(0),
                            "trashedAtEpochMillis" to nullValue(),
                        ),
                    ),
                ),
                PortableTable(
                    name = "annexes",
                    rows = listOf(
                        row(
                            "id" to text(privateAnnexId),
                            "displayName" to text("private-annex.pdf"),
                            "mimeType" to text("application/pdf"),
                        ),
                    ),
                ),
            ),
        )
        StudyPlanPrivacyPolicy.validatePayload(payload)
        return StudyPlanPackageArchive(payload, mapOf(entryName to annexBytes))
    }

    private fun metadataPrivateAnnexInvariantHolds(
        rawAnnexId: String?,
        archive: StudyPlanPackageArchive,
        packageBytes: ByteArray,
    ): Boolean {
        val manifest = archive.payload.manifest
        val annex = manifest.annexPayloads.singleOrNull() ?: return rawAnnexId == null
        val entryNames = zipEntryNames(packageBytes)
        val rawIdHidden = rawAnnexId == null ||
            (rawAnnexId !in annex.annexId &&
                rawAnnexId !in annex.entryName &&
                entryNames.none { name -> rawAnnexId in name })
        return manifest.metadataPrivacyMode &&
            StudyPlanPrivacyPolicy.isPrivateIdentifier(annex.annexId) &&
            rawIdHidden &&
            annex.entryName in entryNames &&
            archive.annexPayloadBytes.containsKey(annex.entryName)
    }

    private fun ByteArray.tamperedCopy(): ByteArray = copyOf().also { copy ->
        copy[copy.lastIndex] = (copy.last().toInt() xor 0x01).toByte()
    }

    private fun zipEntryNames(bytes: ByteArray): Set<String> {
        val names = linkedSetOf<String>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                names += entry.name
                zip.closeEntry()
            }
        }
        return names
    }

    private fun row(vararg values: Pair<String, PortableValue>): PortableRow = PortableRow(values.toMap())

    private fun text(value: String): PortableValue = PortableValue(PortableValueType.TEXT, value)

    private fun integer(value: Number): PortableValue =
        PortableValue(PortableValueType.INTEGER, value.toLong().toString())

    private fun nullValue(): PortableValue = PortableValue(PortableValueType.NULL)

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { byte -> "%02x".format(byte) }
}
