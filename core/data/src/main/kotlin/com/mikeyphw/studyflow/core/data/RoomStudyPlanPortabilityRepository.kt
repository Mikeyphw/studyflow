package com.mikeyphw.studyflow.core.data

import android.content.Context
import com.mikeyphw.studyflow.core.database.StudyFlowDatabase
import com.mikeyphw.studyflow.core.database.StudyPlanGraphPortabilityStore
import com.mikeyphw.studyflow.core.database.entity.PlanSnapshotEntity
import com.mikeyphw.studyflow.core.domain.StudyPlanPortabilityRepository
import com.mikeyphw.studyflow.core.model.AnnexStorageMode
import com.mikeyphw.studyflow.core.model.PlanSnapshot
import com.mikeyphw.studyflow.core.model.PlanSnapshotId
import com.mikeyphw.studyflow.core.model.PlanSnapshotKind
import com.mikeyphw.studyflow.core.model.PlanSnapshotLabels
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyPlanSettings
import com.mikeyphw.studyflow.core.database.mapper.asEntity
import com.mikeyphw.studyflow.core.portability.PortableAnnexPayload
import com.mikeyphw.studyflow.core.portability.StudyPlanBackupIntegrityReport
import com.mikeyphw.studyflow.core.portability.StudyPlanEncryptedPackageCodec
import com.mikeyphw.studyflow.core.portability.StudyPlanExportMode
import com.mikeyphw.studyflow.core.portability.StudyPlanExportOptions
import com.mikeyphw.studyflow.core.portability.StudyPlanImportPreview
import com.mikeyphw.studyflow.core.portability.StudyPlanImportResult
import com.mikeyphw.studyflow.core.portability.StudyPlanImportStrategy
import com.mikeyphw.studyflow.core.portability.StudyPlanPackageArchive
import com.mikeyphw.studyflow.core.portability.StudyPlanPackageCodec
import com.mikeyphw.studyflow.core.portability.StudyPlanPackagePayload
import com.mikeyphw.studyflow.core.portability.StudyPlanPackagePasswordRequiredException
import com.mikeyphw.studyflow.core.portability.StudyPlanPrivacyPolicy
import com.mikeyphw.studyflow.core.portability.StudyPlanPortabilityWarning
import com.mikeyphw.studyflow.core.portability.StudyPlanPortabilityWarningType
import com.mikeyphw.studyflow.core.portability.StudyPlanPortabilityWarnings
import com.mikeyphw.studyflow.core.portability.StudyPlanRestoreDryRun
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.PushbackInputStream
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class RoomStudyPlanPortabilityRepository(
    context: Context,
    private val database: StudyFlowDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
    private val applicationVersion: String = "unknown",
    private val codec: StudyPlanPackageCodec = StudyPlanPackageCodec(),
    private val encryptedCodec: StudyPlanEncryptedPackageCodec = StudyPlanEncryptedPackageCodec(),
) : StudyPlanPortabilityRepository {
    private val applicationFiles = context.applicationContext.filesDir
    private val applicationCache = context.applicationContext.cacheDir
    private val graphStore = StudyPlanGraphPortabilityStore(database)
    private val supportDao = database.studySupportDao()
    private val libraryDao = database.studyLibraryDao()

    override fun observeSnapshots(planId: StudyPlanId): Flow<List<PlanSnapshot>> =
        supportDao.observeSnapshots(planId.value).map { entities ->
            entities.map { it.asExternalModel() }
        }

    override suspend fun exportPlan(
        planId: StudyPlanId,
        options: StudyPlanExportOptions,
        output: OutputStream,
        password: CharArray?,
    ) {
        val payload = graphStore.exportPayload(
            planId = planId.value,
            options = options,
            applicationVersion = applicationVersion,
            exportedAtEpochMillis = clock(),
        )
        val archive = prepareArchive(planId, options, payload)
        if (password == null) {
            codec.writeArchive(archive, output)
        } else {
            encryptedCodec.encrypt(output, password) { encryptedOutput ->
                codec.writeArchive(archive, encryptedOutput)
            }
        }
    }

    override suspend fun previewImport(
        input: InputStream,
        password: CharArray?,
    ): StudyPlanImportPreview {
        val decoded = decodeArchive(input, password)
        graphStore.validatePayload(decoded.archive.payload)
        return codec.preview(decoded.archive).copy(encrypted = decoded.encrypted)
    }

    override suspend fun inspectBackup(
        input: InputStream,
        password: CharArray?,
    ): StudyPlanBackupIntegrityReport {
        val decoded = decodeArchive(input, password)
        val archive = decoded.archive
        graphStore.validatePayload(archive.payload)
        val manifest = archive.payload.manifest
        val warnings = StudyPlanPortabilityWarnings.forManifest(manifest)
        return StudyPlanBackupIntegrityReport(
            encrypted = decoded.encrypted,
            schemaVersion = manifest.schemaVersion,
            checksumVerified = true,
            totalRows = archive.payload.tables.sumOf { it.rows.size },
            annexPayloadCount = manifest.annexPayloads.size,
            metadataPrivacyMode = manifest.metadataPrivacyMode,
            redactedSections = manifest.redactedSections,
            warnings = warnings,
            canRestore = true,
        )
    }

    override suspend fun dryRunImport(
        input: InputStream,
        strategy: StudyPlanImportStrategy,
        targetPlanId: StudyPlanId?,
        password: CharArray?,
    ): StudyPlanRestoreDryRun {
        val decoded = decodeArchive(input, password)
        val archive = decoded.archive
        graphStore.validatePayload(archive.payload)
        require(strategy != StudyPlanImportStrategy.MERGE_COMPATIBLE || archive.annexPayloadBytes.isEmpty()) {
            "Annex payload import does not support merge yet. Import as new or replace an existing Study Plan."
        }
        when (strategy) {
            StudyPlanImportStrategy.CREATE_AS_NEW -> Unit
            StudyPlanImportStrategy.REPLACE_EXISTING,
            StudyPlanImportStrategy.MERGE_COMPATIBLE,
            -> require(database.studyPlanDao().countById(requireNotNull(targetPlanId).value) == 1) {
                "The target Study Plan does not exist."
            }
        }
        return StudyPlanRestoreDryRun(
            strategy = strategy,
            targetStudyPlanId = targetPlanId?.value,
            totalRows = archive.payload.tables.sumOf { it.rows.size },
            tableRowCounts = archive.payload.manifest.tableRowCounts,
            encrypted = decoded.encrypted,
            wouldCreateSafetySnapshot = strategy != StudyPlanImportStrategy.CREATE_AS_NEW,
            warnings = buildList {
                addAll(StudyPlanPortabilityWarnings.forManifest(archive.payload.manifest))
                if (strategy == StudyPlanImportStrategy.REPLACE_EXISTING) {
                    add(
                        StudyPlanPortabilityWarning(
                            StudyPlanPortabilityWarningType.REPLACE_CREATES_SAFETY_SNAPSHOT,
                        ),
                    )
                }
            },
            databaseRowsChanged = 0,
        )
    }

    override suspend fun importPlan(
        input: InputStream,
        strategy: StudyPlanImportStrategy,
        targetPlanId: StudyPlanId?,
        password: CharArray?,
    ): StudyPlanImportResult {
        val decoded = decodeArchive(input, password)
        val archive = decoded.archive
        require(strategy != StudyPlanImportStrategy.MERGE_COMPATIBLE || archive.annexPayloadBytes.isEmpty()) {
            "Annex payload import does not support merge yet. Import as new or replace an existing Study Plan."
        }
        val destinationId = when (strategy) {
            StudyPlanImportStrategy.CREATE_AS_NEW -> StudyPlanId(UUID.randomUUID().toString())
            StudyPlanImportStrategy.REPLACE_EXISTING,
            StudyPlanImportStrategy.MERGE_COMPATIBLE,
            -> requireNotNull(targetPlanId) { "This import strategy requires a target Study Plan." }
        }

        val safetySnapshot = if (strategy == StudyPlanImportStrategy.CREATE_AS_NEW) {
            null
        } else {
            writeSnapshotFile(
                planId = destinationId,
                kind = PlanSnapshotKind.PRE_IMPORT,
                label = PlanSnapshotLabels.PRE_IMPORT,
            )
        }

        try {
            val importedRows = graphStore.importPayload(
                payload = archive.payload,
                strategy = strategy,
                targetPlanId = destinationId.value,
                importedAtEpochMillis = clock(),
            )
            restoreAnnexPayloads(destinationId, archive)
            if (supportDao.findPlanSettings(destinationId.value) == null) {
                supportDao.upsertPlanSettings(StudyPlanSettings.defaults(destinationId).asEntity())
            }
            safetySnapshot?.let { supportDao.upsertSnapshots(listOf(it.asEntity())) }
            return StudyPlanImportResult(
                studyPlanId = destinationId.value,
                strategy = strategy,
                importedRows = importedRows,
                snapshotId = safetySnapshot?.id?.value,
            )
        } catch (error: Throwable) {
            safetySnapshot?.let { snapshot -> resolveSnapshotFile(snapshot.studyPlanId.value, snapshot.payloadReference).delete() }
            throw error
        }
    }

    override suspend fun createSnapshot(
        planId: StudyPlanId,
        kind: PlanSnapshotKind,
        label: String,
    ): PlanSnapshot = writeSnapshotFile(planId, kind, label).also { snapshot ->
        supportDao.upsertSnapshots(listOf(snapshot.asEntity()))
    }

    override suspend fun restoreSnapshot(snapshotId: PlanSnapshotId): StudyPlanImportResult {
        val stored = requireNotNull(supportDao.findSnapshot(snapshotId.value)) {
            "Snapshot ${snapshotId.value} does not exist."
        }
        val snapshot = stored.asExternalModel()
        val sourceFile = resolveSnapshotFile(snapshot.studyPlanId.value, snapshot.payloadReference)
        require(sourceFile.isFile) { "Snapshot package is missing from private storage." }
        require(sha256(sourceFile).equals(snapshot.checksumSha256, ignoreCase = true)) {
            "Snapshot checksum verification failed."
        }

        val safetySnapshot = writeSnapshotFile(
            planId = snapshot.studyPlanId,
            kind = PlanSnapshotKind.AUTOMATIC,
            label = PlanSnapshotLabels.PRE_RESTORE,
        )
        try {
            val archive = sourceFile.inputStream().use(codec::readArchive)
            val importedRows = graphStore.importPayload(
                payload = archive.payload,
                strategy = StudyPlanImportStrategy.REPLACE_EXISTING,
                targetPlanId = snapshot.studyPlanId.value,
                importedAtEpochMillis = clock(),
            )
            restoreAnnexPayloads(snapshot.studyPlanId, archive)
            supportDao.upsertSnapshots(listOf(snapshot.asEntity(), safetySnapshot.asEntity()))
            return StudyPlanImportResult(
                studyPlanId = snapshot.studyPlanId.value,
                strategy = StudyPlanImportStrategy.REPLACE_EXISTING,
                importedRows = importedRows,
                snapshotId = safetySnapshot.id.value,
            )
        } catch (error: Throwable) {
            resolveSnapshotFile(safetySnapshot.studyPlanId.value, safetySnapshot.payloadReference).delete()
            throw error
        }
    }

    override suspend fun deleteSnapshot(snapshotId: PlanSnapshotId) {
        val snapshot = supportDao.findSnapshot(snapshotId.value) ?: return
        resolveSnapshotFile(snapshot.studyPlanId, snapshot.payloadReference).delete()
        supportDao.deleteSnapshot(snapshotId.value)
    }

    private fun decodeArchive(input: InputStream, password: CharArray?): DecodedArchive {
        val magicSize = StudyPlanEncryptedPackageCodec.MagicSize
        val pushback = PushbackInputStream(input.buffered(), magicSize)
        val prefix = ByteArray(magicSize)
        val prefixSize = readPrefix(pushback, prefix)
        if (prefixSize > 0) pushback.unread(prefix, 0, prefixSize)
        val encrypted = prefixSize == magicSize && encryptedCodec.isEncryptedPrefix(prefix)
        prefix.fill(0)
        if (!encrypted) {
            return DecodedArchive(archive = codec.readArchive(pushback), encrypted = false)
        }
        val requiredPassword = password ?: throw StudyPlanPackagePasswordRequiredException(
            "This Study Plan package is encrypted and requires a password.",
        )
        val temporaryPlainPackage = File.createTempFile(
            "studyflow-authenticated-restore-",
            ".studyplan",
            applicationCache,
        )
        return try {
            temporaryPlainPackage.outputStream().buffered().use { output ->
                encryptedCodec.decrypt(
                    input = pushback,
                    output = output,
                    password = requiredPassword,
                    maximumEncryptedBytes = MaxEncryptedPackageBytes,
                )
            }
            DecodedArchive(
                archive = temporaryPlainPackage.inputStream().buffered().use(codec::readArchive),
                encrypted = true,
            )
        } finally {
            temporaryPlainPackage.delete()
        }
    }

    private fun readPrefix(input: InputStream, prefix: ByteArray): Int {
        var total = 0
        while (total < prefix.size) {
            val count = input.read(prefix, total, prefix.size - total)
            if (count <= 0) break
            total += count
        }
        return total
    }

    private fun readBounded(
        input: InputStream,
        limit: Long,
        tooLargeMessage: String,
    ): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        return try {
            var total = 0L
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= limit) { tooLargeMessage }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } finally {
            buffer.fill(0)
        }
    }

    private suspend fun prepareArchive(
        planId: StudyPlanId,
        options: StudyPlanExportOptions,
        payload: StudyPlanPackagePayload,
    ): StudyPlanPackageArchive {
        if (!options.includeAnnexPayloads || !payload.manifest.includesAnnexMetadata) {
            return StudyPlanPackageArchive(payload)
        }
        val annexRows = payload.tables.firstOrNull { it.name == "annexes" }?.rows.orEmpty()
        val sourceAnnexesByExportedId = libraryDao.findAnnexes(planId.value).associateBy { annex ->
            if (options.metadataPrivacyMode) {
                StudyPlanPrivacyPolicy.pseudonymizeIdentifier(payload.manifest.sourceStudyPlanId, annex.id)
            } else {
                annex.id
            }
        }
        val payloads = mutableListOf<PortableAnnexPayload>()
        val bytesByEntry = linkedMapOf<String, ByteArray>()
        annexRows.forEach { row ->
            val annexId = row.values["id"]?.value.orEmpty()
            if (annexId.isBlank()) return@forEach
            val annex = sourceAnnexesByExportedId[annexId] ?: return@forEach
            if (annex.storageMode != AnnexStorageMode.IMPORTED_COPY.name) return@forEach
            val file = annex.storageReference.takeIf(String::isNotBlank)
                ?.let { reference -> resolveAnnexFile(planId.value, reference) }
                ?: return@forEach
            require(file.isFile) { "Annex payload is missing from private storage." }
            require(file.length() == annex.byteSize) { "Annex byte-size verification failed before export." }
            val expectedChecksum = requireNotNull(annex.checksumSha256?.takeIf(String::isNotBlank)) {
                "Annex checksum is missing; migrate or re-import the annex before export."
            }
            require(sha256(file).equals(expectedChecksum, ignoreCase = true)) {
                "Annex checksum verification failed before export."
            }
            require(file.length() <= MaxAnnexPayloadBytes) {
                "Annex payload exceeds the supported package entry size."
            }
            val bytes = file.inputStream().use { input ->
                readBounded(
                    input = input,
                    limit = MaxAnnexPayloadBytes,
                    tooLargeMessage = "Annex payload exceeds the supported package entry size.",
                )
            }
            val checksum = sha256(bytes)
            val entryName = annexPayloadEntryName(annexId, annex.displayName)
            payloads += PortableAnnexPayload(
                annexId = annexId,
                entryName = entryName,
                fileName = annex.displayName,
                mimeType = annex.mimeType,
                byteSize = bytes.size.toLong(),
                checksumSha256 = checksum,
            )
            bytesByEntry[entryName] = bytes
        }
        val manifest = payload.manifest.copy(
            includesAnnexPayloads = payloads.isNotEmpty(),
            annexPayloads = payloads,
        )
        return StudyPlanPackageArchive(payload.copy(manifest = manifest), bytesByEntry)
    }

    private suspend fun restoreAnnexPayloads(
        planId: StudyPlanId,
        archive: StudyPlanPackageArchive,
    ) {
        archive.payload.manifest.annexPayloads.forEach { payload ->
            val importedAnnexId = "${planId.value}:${payload.annexId}"
            val bytes = archive.annexPayloadBytes.getValue(payload.entryName)
            val directory = annexDirectory(planId.value, importedAnnexId).apply { mkdirs() }
            val destination = File(directory, sanitizeFileName(payload.fileName)).canonicalFile
            require(destination.path.startsWith(directory.canonicalPath + File.separator)) { "Unsafe annex file path." }
            destination.writeBytes(bytes)
            val reference = destination.relativeTo(applicationFiles).invariantSeparatorsPath
            libraryDao.updateAnnexStorage(
                planId = planId.value,
                annexId = importedAnnexId,
                storageMode = AnnexStorageMode.IMPORTED_COPY.name,
                storageReference = reference,
                persistedPermission = false,
                byteSize = bytes.size.toLong(),
                checksumSha256 = sha256(bytes),
                updatedAtEpochMillis = clock(),
            )
        }
    }

    private suspend fun writeSnapshotFile(
        planId: StudyPlanId,
        kind: PlanSnapshotKind,
        label: String,
    ): PlanSnapshot {
        val id = PlanSnapshotId(UUID.randomUUID().toString())
        val directory = File(applicationFiles, "study-plans/${planId.value}/snapshots").apply { mkdirs() }
        val destination = File(directory, "${id.value}.studyplan")
        val temporary = File(directory, ".${id.value}.tmp")
        val payload = graphStore.exportPayload(
            planId = planId.value,
            options = StudyPlanExportOptions(
                mode = StudyPlanExportMode.FULL,
                includeAnnexMetadata = true,
                includeAnnexPayloads = true,
            ),
            applicationVersion = applicationVersion,
            exportedAtEpochMillis = clock(),
        )
        try {
            val archive = prepareArchive(
                planId = planId,
                options = StudyPlanExportOptions(StudyPlanExportMode.FULL, true, true),
                payload = payload,
            )
            temporary.outputStream().use { codec.writeArchive(archive, it) }
            check(temporary.renameTo(destination)) { "Could not commit the Study Plan snapshot file." }
        } catch (error: Throwable) {
            temporary.delete()
            destination.delete()
            throw error
        }
        val snapshotPayload = destination.inputStream().use(codec::read)
        return PlanSnapshot(
            id = id,
            studyPlanId = planId,
            kind = kind,
            label = label,
            schemaVersion = snapshotPayload.manifest.schemaVersion,
            payloadReference = destination.relativeTo(applicationFiles).invariantSeparatorsPath,
            checksumSha256 = sha256(destination),
            createdAtEpochMillis = clock(),
        )
    }

    private fun resolveSnapshotFile(planId: String, reference: String): File {
        val candidate = File(applicationFiles, reference).canonicalFile
        val root = File(applicationFiles, "study-plans/${sanitizeSegment(planId)}/snapshots").canonicalFile
        require(candidate.path.startsWith(root.path + File.separator)) {
            "Unsafe snapshot path."
        }
        return candidate
    }

    private fun resolveAnnexFile(planId: String, reference: String): File {
        val candidate = File(applicationFiles, reference).canonicalFile
        val root = File(applicationFiles, "shareable-annexes/${sanitizeSegment(planId)}").canonicalFile
        require(candidate.path.startsWith(root.path + File.separator)) {
            "Unsafe annex path."
        }
        return candidate
    }

    private fun annexDirectory(planId: String, annexId: String): File =
        File(applicationFiles, "shareable-annexes/${sanitizeSegment(planId)}/${sanitizeSegment(annexId)}")

    private fun annexPayloadEntryName(annexId: String, displayName: String): String =
        "annex-payloads/${sanitizeSegment(annexId)}/${sanitizeFileName(displayName)}"

    private fun sanitizeSegment(value: String): String = value.replace(Regex("[^A-Za-z0-9._:-]"), "_")

    private fun sanitizeFileName(value: String): String = value
        .replace(Regex("[\\/\u0000-\u001F]"), "_")
        .trim()
        .ifBlank { "annex.bin" }
        .take(120)

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private data class DecodedArchive(
        val archive: StudyPlanPackageArchive,
        val encrypted: Boolean,
    )

    private companion object {
        const val MaxAnnexPayloadBytes = 64L * 1024L * 1024L
        const val MaxEncryptedPackageBytes = 516L * 1024L * 1024L
    }

    private fun PlanSnapshotEntity.asExternalModel() = PlanSnapshot(
        id = PlanSnapshotId(id),
        studyPlanId = StudyPlanId(studyPlanId),
        kind = PlanSnapshotKind.valueOf(kind),
        label = label,
        schemaVersion = schemaVersion,
        payloadReference = payloadReference,
        checksumSha256 = checksumSha256,
        createdAtEpochMillis = createdAtEpochMillis,
    )

    private fun PlanSnapshot.asEntity() = PlanSnapshotEntity(
        id = id.value,
        studyPlanId = studyPlanId.value,
        kind = kind.name,
        label = label,
        schemaVersion = schemaVersion,
        payloadReference = payloadReference,
        checksumSha256 = checksumSha256,
        createdAtEpochMillis = createdAtEpochMillis,
    )
}
