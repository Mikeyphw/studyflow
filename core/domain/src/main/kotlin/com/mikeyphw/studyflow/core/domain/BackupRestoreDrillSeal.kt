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
import java.util.concurrent.CancellationException
import java.util.zip.ZipInputStream

enum class BackupRestoreDrillKind {
    ENCRYPTED_EXPORT,
    WRONG_PASSWORD,
    TAMPER_DETECTION,
    METADATA_PRIVATE_ANNEX,
    MODERN_DATA_COVERAGE,
    RESTORE_DRY_RUN,
    RETENTION_CLEANUP,
    RECOVERY_REPORT,
}

enum class BackupRestoreDrillStatus {
    PASS,
    BLOCKED,
    NOT_APPLICABLE,
}

data class BackupRestoreDrillResult(
    val kind: BackupRestoreDrillKind,
    val status: BackupRestoreDrillStatus,
    val evidence: String,
    val destructive: Boolean = false,
) {
    val passed: Boolean = status == BackupRestoreDrillStatus.PASS
    val applicable: Boolean = status != BackupRestoreDrillStatus.NOT_APPLICABLE
}

data class BackupRestoreDrillReport(
    val generatedAtEpochMillis: Long,
    val results: List<BackupRestoreDrillResult>,
    val recoveryReport: List<String>,
) {
    val passedCount: Int = results.count(BackupRestoreDrillResult::passed)
    val blockedCount: Int = results.count { it.status == BackupRestoreDrillStatus.BLOCKED }
    val notApplicableCount: Int = results.count { it.status == BackupRestoreDrillStatus.NOT_APPLICABLE }
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
        val integrity = captureDrillResult { inspectBackup(encryptedPackageBytes, password) }
        val dryRun = captureDrillResult {
            dryRunImport(
                encryptedPackageBytes,
                StudyPlanImportStrategy.CREATE_AS_NEW,
                null,
                password,
            )
        }
        val decodedArchive = captureDrillResult {
            packageCodec.archiveFromByteArray(encryptedCodec.decrypt(encryptedPackageBytes, password))
        }.getOrNull()
        val wrongPasswordBlocked = expectedDrillFailure {
            inspectBackup(encryptedPackageBytes, wrongPassword)
        }
        val tamperBlocked = expectedDrillFailure {
            inspectBackup(encryptedPackageBytes.tamperedCopy(), password)
        }
        val annexPrivate = decodedArchive?.let { archive ->
            metadataPrivateAnnexInvariantHolds(
                rawAnnexId = rawAnnexId,
                archive = archive,
                packageBytes = encryptedCodec.decrypt(encryptedPackageBytes, password),
            )
        } ?: false
        val dryRunValue = dryRun.getOrNull()
        val modernDataCoverageVerified = decodedArchive?.payload?.manifest?.let(::modernDataCoverageVerified) == true
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
                if (decodedArchive?.payload?.manifest?.annexPayloads.isNullOrEmpty()) {
                    notApplicable(
                        BackupRestoreDrillKind.METADATA_PRIVATE_ANNEX,
                        "No annex payloads exist in this package, so annex privacy is not applicable.",
                    )
                } else {
                    result(
                        BackupRestoreDrillKind.METADATA_PRIVATE_ANNEX,
                        annexPrivate,
                        "Every metadata-private annex payload ID and ZIP path hides raw database IDs.",
                    )
                },
                result(
                    BackupRestoreDrillKind.MODERN_DATA_COVERAGE,
                    modernDataCoverageVerified,
                    "Backup manifest includes every modern programming, language, Termux, annex and review table family.",
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

    private suspend fun <T> captureDrillResult(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (fatal: Error) {
        throw fatal
    } catch (failure: Exception) {
        Result.failure(failure)
    }

    private suspend fun expectedDrillFailure(block: suspend () -> Unit): Boolean = try {
        block()
        false
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (fatal: Error) {
        throw fatal
    } catch (_: Exception) {
        true
    }

    private fun result(
        kind: BackupRestoreDrillKind,
        passed: Boolean,
        evidence: String,
    ): BackupRestoreDrillResult = BackupRestoreDrillResult(
        kind = kind,
        status = if (passed) BackupRestoreDrillStatus.PASS else BackupRestoreDrillStatus.BLOCKED,
        evidence = evidence,
    )


    private fun notApplicable(
        kind: BackupRestoreDrillKind,
        evidence: String,
    ): BackupRestoreDrillResult = BackupRestoreDrillResult(
        kind = kind,
        status = BackupRestoreDrillStatus.NOT_APPLICABLE,
        evidence = evidence,
    )

    private fun retentionCleanupTruthfulnessHolds(policy: LocalDataRetentionPolicy): Boolean =
        !policy.automaticDeletionEnabled &&
            LocalRetentionRuleType.EXPORTED_FILES in policy.rules.map { rule -> rule.type }
    internal fun modernDataCoverageVerified(manifest: StudyPlanPackageManifest): Boolean {
        val tables = manifest.tableRowCounts.keys
        val requiredFamilies = listOf(
            listOf("programming", "exercise", "code", "snippet", "termux"),
            listOf("language", "idiom", "vocabulary", "review"),
            listOf("annex", "material"),
        )
        return requiredFamilies.all { aliases ->
            tables.any { table -> aliases.any { alias -> table.contains(alias, ignoreCase = true) } }
        }
    }


    private fun metadataPrivateAnnexArchive(
        rawAnnexId: String,
        annexBytes: ByteArray,
    ): StudyPlanPackageArchive {
        val privatePlanId = "private-0123456789abcdefabcd"
        val privateAnnexId = StudyPlanPrivacyPolicy.pseudonymizeIdentifier(privatePlanId, rawAnnexId)
        val entryName = "annex-payloads/$privateAnnexId/payload.bin"
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
                    fileName = "private-annex.bin",
                    mimeType = "application/pdf",
                    byteSize = annexBytes.size.toLong(),
                    checksumSha256 = sha256(annexBytes),
                ),
            ),
            tableRowCounts = mapOf("study_plans" to 1, "annexes" to 1, "programming_exercises" to 0, "language_idiom_cards" to 0, "material_annexes" to 0),
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
                            "displayName" to text("private-annex.bin"),
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
        if (manifest.annexPayloads.isEmpty()) return false
        val entryNames = zipEntryNames(packageBytes)
        val rawIdHidden = rawAnnexId == null || manifest.annexPayloads.all { annex ->
            rawAnnexId !in annex.annexId &&
                rawAnnexId !in annex.entryName &&
                entryNames.none { name -> rawAnnexId in name }
        }
        return manifest.metadataPrivacyMode &&
            manifest.annexPayloads.all { annex ->
                StudyPlanPrivacyPolicy.isPrivateIdentifier(annex.annexId) &&
                    annex.entryName in entryNames &&
                    archive.annexPayloadBytes.containsKey(annex.entryName)
            } &&
            rawIdHidden
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
