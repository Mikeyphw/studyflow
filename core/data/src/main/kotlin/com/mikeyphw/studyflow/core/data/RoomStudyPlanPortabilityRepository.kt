package com.mikeyphw.studyflow.core.data

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import com.mikeyphw.studyflow.core.portability.StudyPlanImportDiffEngine
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
import kotlinx.coroutines.sync.withLock

internal class RoomStudyPlanPortabilityRepository(
    context: Context,
    private val database: StudyFlowDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
    private val applicationVersion: String = "unknown",
    private val codec: StudyPlanPackageCodec = StudyPlanPackageCodec(),
    private val encryptedCodec: StudyPlanEncryptedPackageCodec = StudyPlanEncryptedPackageCodec(),
    private val restoreFailureHook: suspend (RestoreFailurePoint) -> Unit = {},
    private val diagnosticRecorder: com.mikeyphw.studyflow.core.diagnostics.DiagnosticRecorder = com
        .mikeyphw.studyflow.core.diagnostics.NoOpDiagnosticRecorder,
) : StudyPlanPortabilityRepository {
    private val applicationContext = context.applicationContext
    private val applicationFiles = applicationContext.filesDir
    private val applicationCache = applicationContext.cacheDir
    private val contentResolver = applicationContext.contentResolver
    private val graphStore = StudyPlanGraphPortabilityStore(database)
    private val supportDao = database.studySupportDao()
    private val libraryDao = database.studyLibraryDao()

    init {
        cleanupStaleAuthenticatedRestoreTemps()
        cleanupStalePackageStagingDirectories()
    }

    override fun observeSnapshots(planId: StudyPlanId): Flow<List<PlanSnapshot>> =
        supportDao.observeSnapshots(planId.value).map { entities ->
            entities.map { it.asExternalModel() }
        }

    override suspend fun exportPlan(
        planId: StudyPlanId,
        options: StudyPlanExportOptions,
        output: OutputStream,
        password: CharArray?,
    ) = diagnosticRecorder.recordRepositoryMutation(
        area = com.mikeyphw.studyflow.core.diagnostics.DiagnosticArea.BACKUP_RESTORE,
        action = "plan-export",
    ) {
        PhysicalStorageMutationCoordinator.mutex.withLock {
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
    }

    override suspend fun previewImport(
        input: InputStream,
        password: CharArray?,
    ): StudyPlanImportPreview {
        val decoded = decodeArchive(input, password)
        return try {
            graphStore.validatePayload(decoded.archive.payload)
            codec.preview(decoded.archive).copy(encrypted = decoded.encrypted)
        } finally {
            decoded.cleanup()
        }
    }

    override suspend fun inspectBackup(
        input: InputStream,
        password: CharArray?,
    ): StudyPlanBackupIntegrityReport {
        val decoded = decodeArchive(input, password)
        return try {
            val archive = decoded.archive
            graphStore.validatePayload(archive.payload)
            val manifest = archive.payload.manifest
            val warnings = StudyPlanPortabilityWarnings.forManifest(manifest)
            StudyPlanBackupIntegrityReport(
                encrypted = decoded.encrypted,
                schemaVersion = manifest.schemaVersion,
                checksumVerified = true,
                totalRows = archive.payload.tables.sumOf { it.rows.size },
                annexPayloadCount = manifest.annexPayloads.size,
                metadataPrivacyMode = manifest.metadataPrivacyMode,
                privateIdentifiersVerified = manifest.metadataPrivacyMode,
                redactedSections = manifest.redactedSections,
                warnings = warnings,
                canRestore = true,
            )
        } finally {
            decoded.cleanup()
        }
    }

    override suspend fun dryRunImport(
        input: InputStream,
        strategy: StudyPlanImportStrategy,
        targetPlanId: StudyPlanId?,
        password: CharArray?,
    ): StudyPlanRestoreDryRun {
        val decoded = decodeArchive(input, password)
        return try {
            val archive = decoded.archive
            graphStore.validatePayload(archive.payload)
            require(strategy != StudyPlanImportStrategy.MERGE_COMPATIBLE || archive.payload.manifest
                .annexPayloads.isEmpty()) {
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
            val currentPayload = if (strategy == StudyPlanImportStrategy.CREATE_AS_NEW) {
                null
            } else {
                graphStore.exportPayload(
                    planId = requireNotNull(targetPlanId).value,
                    options = StudyPlanExportOptions(
                        mode = StudyPlanExportMode.FULL,
                        includeAnnexMetadata = true,
                        includeAnnexPayloads = false,
                    ),
                    applicationVersion = applicationVersion,
                    exportedAtEpochMillis = clock(),
                )
            }
            val tableDiffs = StudyPlanImportDiffEngine.diff(
                incoming = archive.payload,
                current = currentPayload,
                strategy = strategy,
                targetStudyPlanId = targetPlanId?.value,
            )
            StudyPlanRestoreDryRun(
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
                tableDiffs = tableDiffs,
                transactionalRollbackProtected = true,
            )
        } finally {
            decoded.cleanup()
        }
    }

    override suspend fun importPlan(
        input: InputStream,
        strategy: StudyPlanImportStrategy,
        targetPlanId: StudyPlanId?,
        password: CharArray?,
    ): StudyPlanImportResult = diagnosticRecorder.recordRepositoryMutation(
        area = com.mikeyphw.studyflow.core.diagnostics.DiagnosticArea.BACKUP_RESTORE,
        action = "plan-import",
    ) {
        PhysicalStorageMutationCoordinator.mutex.withLock {
        val decoded = decodeArchive(input, password)
        try {
            val archive = decoded.archive
            graphStore.validatePayload(archive.payload)
            require(strategy != StudyPlanImportStrategy.MERGE_COMPATIBLE || archive.payload.manifest
                .annexPayloads.isEmpty()) {
            "Annex payload import does not support merge yet. Import as new or replace an existing Study Plan."
        }
        val destinationId = when (strategy) {
            StudyPlanImportStrategy.CREATE_AS_NEW -> StudyPlanId(UUID.randomUUID().toString())
            StudyPlanImportStrategy.REPLACE_EXISTING,
            StudyPlanImportStrategy.MERGE_COMPATIBLE,
            -> requireNotNull(targetPlanId) { "This import strategy requires a target Study Plan." }
        }
        if (strategy != StudyPlanImportStrategy.CREATE_AS_NEW) {
            require(database.studyPlanDao().countById(destinationId.value) == 1) {
                "The target Study Plan does not exist."
            }
        }

        val oldAnnexes = if (strategy == StudyPlanImportStrategy.REPLACE_EXISTING) {
            libraryDao.findAnnexes(destinationId.value)
        } else {
            emptyList()
        }
        val staged = stageAnnexPayloads(destinationId, archive)
        try {
            restoreFailureHook(RestoreFailurePoint.AFTER_STAGING)
            val safetySnapshot = if (strategy == StudyPlanImportStrategy.CREATE_AS_NEW) {
                null
            } else {
                createDurableSafetySnapshot(destinationId, PlanSnapshotKind.PRE_IMPORT, PlanSnapshotLabels.PRE_IMPORT)
            }
            val importedRows = graphStore.importPayload(
                payload = archive.payload,
                strategy = strategy,
                targetPlanId = destinationId.value,
                importedAtEpochMillis = clock(),
                beforeCommit = {
                    commitStagedAnnexPayloads(destinationId, staged)
                    if (supportDao.findPlanSettings(destinationId.value) == null) {
                        supportDao.upsertPlanSettings(StudyPlanSettings.defaults(destinationId).asEntity())
                    }
                    restoreFailureHook(RestoreFailurePoint.BEFORE_DATABASE_COMMIT)
                },
            )
            staged.markCommitted()
            cleanupReplacedAnnexOwnership(oldAnnexes, staged.finalReferences)
            StudyPlanImportResult(
                studyPlanId = destinationId.value,
                strategy = strategy,
                importedRows = importedRows,
                snapshotId = safetySnapshot?.id?.value,
            )
        } catch (error: Throwable) {
            staged.rollbackCreatedFiles()
            // A safety snapshot, once durably created, is intentionally retained for recovery.
            throw error
        } finally {
            staged.cleanupStagingDirectory()
        }
        } finally {
            decoded.cleanup()
        }
    }
    }

    override suspend fun createSnapshot(
        planId: StudyPlanId,
        kind: PlanSnapshotKind,
        label: String,
    ): PlanSnapshot = PhysicalStorageMutationCoordinator.mutex.withLock {
        writeSnapshotFile(planId, kind, label).also { snapshot ->
            supportDao.upsertSnapshots(listOf(snapshot.asEntity()))
        }
    }

    override suspend fun restoreSnapshot(
        snapshotId: PlanSnapshotId): StudyPlanImportResult = diagnosticRecorder.recordRepositoryMutation(
        area = com.mikeyphw.studyflow.core.diagnostics.DiagnosticArea.BACKUP_RESTORE,
        action = "snapshot-restore",
    ) {
        PhysicalStorageMutationCoordinator.mutex.withLock {
            val stored = requireNotNull(supportDao.findSnapshot(snapshotId.value)) {
                "Snapshot ${snapshotId.value} does not exist."
            }
            val snapshot = stored.asExternalModel()
            val sourceFile = resolveSnapshotFile(snapshot.studyPlanId.value, snapshot.payloadReference)
            require(sourceFile.isFile) { "Snapshot package is missing from private storage." }
            require(sha256(sourceFile).equals(snapshot.checksumSha256, ignoreCase = true)) {
                "Snapshot checksum verification failed."
            }
            val packageStaging = createPackageStagingDirectory()
            val archive = try {
                sourceFile.inputStream().buffered().use { input ->
                    codec.readArchiveStreaming(input, packageStaging)
                }
            } catch (error: Throwable) {
                packageStaging.deleteRecursively()
                throw error
            }
            try {
                graphStore.validatePayload(archive.payload)
                val staged = stageAnnexPayloads(snapshot.studyPlanId, archive)
                val oldAnnexes = libraryDao.findAnnexes(snapshot.studyPlanId.value)
                try {
                    restoreFailureHook(RestoreFailurePoint.AFTER_STAGING)
                    val safetySnapshot = createDurableSafetySnapshot(
                        planId = snapshot.studyPlanId,
                        kind = PlanSnapshotKind.AUTOMATIC,
                        label = PlanSnapshotLabels.PRE_RESTORE,
                    )
                    val importedRows = graphStore.importPayload(
                        payload = archive.payload,
                        strategy = StudyPlanImportStrategy.REPLACE_EXISTING,
                        targetPlanId = snapshot.studyPlanId.value,
                        importedAtEpochMillis = clock(),
                        beforeCommit = {
                            commitStagedAnnexPayloads(snapshot.studyPlanId, staged)
                            if (supportDao.findPlanSettings(snapshot.studyPlanId.value) == null) {
                                supportDao.upsertPlanSettings(StudyPlanSettings.defaults(snapshot
                                    .studyPlanId).asEntity())
                            }
                            restoreFailureHook(RestoreFailurePoint.BEFORE_DATABASE_COMMIT)
                        },
                    )
                    staged.markCommitted()
                    cleanupReplacedAnnexOwnership(oldAnnexes, staged.finalReferences)
                    supportDao.upsertSnapshots(listOf(snapshot.asEntity()))
                    StudyPlanImportResult(
                        studyPlanId = snapshot.studyPlanId.value,
                        strategy = StudyPlanImportStrategy.REPLACE_EXISTING,
                        importedRows = importedRows,
                        snapshotId = safetySnapshot.id.value,
                    )
                } catch (error: Throwable) {
                    staged.rollbackCreatedFiles()
                    throw error
                } finally {
                    staged.cleanupStagingDirectory()
                }
            } finally {
                packageStaging.deleteRecursively()
            }
        }
    }

    override suspend fun deleteSnapshot(snapshotId: PlanSnapshotId) {
        PhysicalStorageMutationCoordinator.mutex.withLock {
            val snapshot = supportDao.findSnapshot(snapshotId.value) ?: return@withLock
            require(supportDao.deleteSnapshot(snapshotId.value) == 1) {
                "Snapshot changed before it could be deleted."
            }
            resolveSnapshotFile(snapshot.studyPlanId, snapshot.payloadReference).delete()
        }
    }

    private fun decodeArchive(input: InputStream, password: CharArray?): DecodedArchive {
        val magicSize = StudyPlanEncryptedPackageCodec.MagicSize
        val pushback = PushbackInputStream(input.buffered(), magicSize)
        val prefix = ByteArray(magicSize)
        val prefixSize = readPrefix(pushback, prefix)
        if (prefixSize > 0) pushback.unread(prefix, 0, prefixSize)
        val encrypted = prefixSize == magicSize && encryptedCodec.isEncryptedPrefix(prefix)
        prefix.fill(0)
        val packageStaging = createPackageStagingDirectory()
        if (!encrypted) {
            return try {
                DecodedArchive(
                    archive = codec.readArchiveStreaming(pushback, packageStaging),
                    encrypted = false,
                    stagingDirectory = packageStaging,
                )
            } catch (error: Throwable) {
                packageStaging.deleteRecursively()
                throw error
            }
        }
        val requiredPassword = password ?: run {
            packageStaging.deleteRecursively()
            throw StudyPlanPackagePasswordRequiredException(
                "This Study Plan package is encrypted and requires a password.",
            )
        }
        val temporaryPlainPackage = File.createTempFile(
            AuthenticatedRestoreTempPrefix,
            AuthenticatedRestoreTempSuffix,
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
                archive = temporaryPlainPackage.inputStream().buffered().use { plainInput ->
                    codec.readArchiveStreaming(plainInput, packageStaging)
                },
                encrypted = true,
                stagingDirectory = packageStaging,
            )
        } catch (error: Throwable) {
            packageStaging.deleteRecursively()
            throw error
        } finally {
            securelyDiscardPlaintextTemp(temporaryPlainPackage)
        }
    }

    private fun createPackageStagingDirectory(): File =
        File(applicationCache, "$PackageStagingPrefix${UUID.randomUUID()}").canonicalFile

    private fun cleanupStaleAuthenticatedRestoreTemps() {
        applicationCache.listFiles()
            .orEmpty()
            .filter { file ->
                file.isFile &&
                    file.name.startsWith(AuthenticatedRestoreTempPrefix) &&
                    file.name.endsWith(AuthenticatedRestoreTempSuffix)
            }
            .forEach(::securelyDiscardPlaintextTemp)
    }

    private fun cleanupStalePackageStagingDirectories() {
        applicationCache.listFiles()
            .orEmpty()
            .filter { it.isDirectory && it.name.startsWith(PackageStagingPrefix) }
            .forEach { directory -> runCatching { directory.deleteRecursively() } }
    }

    private fun securelyDiscardPlaintextTemp(file: File) {
        if (!file.exists()) return
        runCatching { file.outputStream().use { } }
        runCatching { file.delete() }
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
        val streamsByEntry = linkedMapOf<String, () -> InputStream>()
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
            val checksum = expectedChecksum.lowercase()
            val exportedFileName = if (options.metadataPrivacyMode) "private-annex.bin" else annex.displayName
            val entryName = if (options.metadataPrivacyMode) {
                "annex-payloads/${sanitizeSegment(annexId)}/payload.bin"
            } else {
                annexPayloadEntryName(annexId, exportedFileName)
            }
            payloads += PortableAnnexPayload(
                annexId = annexId,
                entryName = entryName,
                fileName = exportedFileName,
                mimeType = annex.mimeType,
                byteSize = file.length(),
                checksumSha256 = checksum,
            )
            streamsByEntry[entryName] = { file.inputStream() }
        }
        val manifest = payload.manifest.copy(
            includesAnnexPayloads = payloads.isNotEmpty(),
            annexPayloads = payloads,
        )
        return StudyPlanPackageArchive(
            payload = payload.copy(manifest = manifest),
            annexPayloadStreams = streamsByEntry,
        )
    }

    private suspend fun createDurableSafetySnapshot(
        planId: StudyPlanId,
        kind: PlanSnapshotKind,
        label: String,
    ): PlanSnapshot {
        val snapshot = writeSnapshotFile(planId, kind, label)
        try {
            supportDao.upsertSnapshots(listOf(snapshot.asEntity()))
            return snapshot
        } catch (error: Throwable) {
            resolveSnapshotFile(snapshot.studyPlanId.value, snapshot.payloadReference).delete()
            throw error
        }
    }

    private fun stageAnnexPayloads(
        planId: StudyPlanId,
        archive: StudyPlanPackageArchive,
    ): StagedAnnexRestore {
        val stagingRoot = File(applicationCache, "studyflow-restore-stage-${UUID.randomUUID()}").canonicalFile
        require(stagingRoot.mkdirs()) { "Could not create the restore staging directory." }
        val entries = mutableListOf<StagedAnnexEntry>()
        try {
            archive.payload.manifest.annexPayloads.forEach { payload ->
                val importedAnnexId = "${planId.value}:${payload.annexId}"
                val stagedFile = File(stagingRoot, "${sanitizeSegment(importedAnnexId)}.bin").canonicalFile
                require(stagedFile.path.startsWith(stagingRoot.path + File
                    .separator)) { "Unsafe restore staging path." }
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var totalBytes = 0L
                try {
                    archive.openAnnexPayload(payload.entryName).buffered().use { input ->
                        stagedFile.outputStream().buffered().use { output ->
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                totalBytes += count
                                require(totalBytes <= MaxAnnexPayloadBytes) {
                                    "Annex payload exceeds restore size limit." }
                                require(totalBytes <= payload
                                    .byteSize) { "Annex staged byte-size verification failed." }
                                digest.update(buffer, 0, count)
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                } finally {
                    buffer.fill(0)
                }
                require(totalBytes == payload.byteSize) { "Annex staged byte-size verification failed." }
                val checksum = digest.digest().joinToString("") { "%02x".format(it) }
                require(checksum.equals(payload.checksumSha256, ignoreCase = true)) {
                    "Annex staged checksum verification failed."
                }
                require(stagedFile.length() == payload.byteSize) { "Annex staging write was incomplete." }
                val directory = annexDirectory(planId.value, importedAnnexId).canonicalFile
                val destination = File(directory, sanitizeFileName(payload.fileName)).canonicalFile
                require(destination.path.startsWith(directory.path + File.separator)) { "Unsafe annex restore path." }
                entries += StagedAnnexEntry(
                    annexId = importedAnnexId,
                    temporaryFile = stagedFile,
                    destinationFile = destination,
                    storageReference = destination.relativeTo(applicationFiles).invariantSeparatorsPath,
                    byteSize = payload.byteSize,
                    checksumSha256 = payload.checksumSha256,
                )
            }
            return StagedAnnexRestore(stagingRoot, entries)
        } catch (error: Throwable) {
            stagingRoot.deleteRecursively()
            throw error
        }
    }

    private suspend fun commitStagedAnnexPayloads(
        planId: StudyPlanId,
        staged: StagedAnnexRestore,
    ) {
        staged.entries.forEach { entry ->
            val directory = requireNotNull(entry.destinationFile.parentFile) {
                "Restored annex destination must have a parent directory."
            }
            require(directory.mkdirs() || directory.isDirectory) { "Could not create restored annex directory." }
            require(!entry.destinationFile.exists()) { "Restored annex destination already exists." }
            check(entry.temporaryFile.renameTo(entry
                .destinationFile)) { "Could not atomically place restored annex bytes." }
            staged.createdFinalFiles += entry.destinationFile
            require(entry.destinationFile.length() == entry
                .byteSize) { "Restored annex byte-size verification failed." }
            require(sha256(entry.destinationFile).equals(entry.checksumSha256, ignoreCase = true)) {
                "Restored annex checksum verification failed."
            }
            require(
                libraryDao.updateAnnexStorage(
                    planId = planId.value,
                    annexId = entry.annexId,
                    storageMode = AnnexStorageMode.IMPORTED_COPY.name,
                    storageReference = entry.storageReference,
                    persistedPermission = false,
                    byteSize = entry.byteSize,
                    checksumSha256 = entry.checksumSha256,
                    updatedAtEpochMillis = clock(),
                ) == 1,
            ) { "Imported annex metadata could not be bound to its staged payload." }
        }
    }

    private suspend fun cleanupReplacedAnnexOwnership(
        oldAnnexes: List<com.mikeyphw.studyflow.core.database.entity.AnnexEntity>,
        newReferences: Set<String>,
    ) {
        oldAnnexes.forEach { annex ->
            when (AnnexStorageMode.valueOf(annex.storageMode)) {
                AnnexStorageMode.IMPORTED_COPY -> if (
                    annex.storageReference.isNotBlank() &&
                    annex.storageReference !in newReferences &&
                    libraryDao.countAnnexesByStorageReference(annex.storageReference) == 0
                ) {
                    runCatching {
                        val oldFile = resolveAnnexFile(annex.studyPlanId, annex.storageReference)
                        if (oldFile.exists()) oldFile.delete()
                        oldFile.parentFile?.takeIf { it.listFiles().isNullOrEmpty() }?.delete()
                    }
                }
                AnnexStorageMode.LINKED_DOCUMENT -> if (
                    annex.persistedPermission &&
                    annex.storageReference.isNotBlank() &&
                    libraryDao.countAnnexesByStorageReference(annex.storageReference) == 0
                ) {
                    runCatching {
                        contentResolver.releasePersistableUriPermission(
                            Uri.parse(annex.storageReference),
                            Intent.FLAG_GRANT_READ_URI_PERMISSION,
                        )
                    }
                }
            }
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
        return PlanSnapshot(
            id = id,
            studyPlanId = planId,
            kind = kind,
            label = label,
            schemaVersion = payload.manifest.schemaVersion,
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



    internal enum class RestoreFailurePoint { AFTER_STAGING, BEFORE_DATABASE_COMMIT }

    private data class StagedAnnexEntry(
        val annexId: String,
        val temporaryFile: File,
        val destinationFile: File,
        val storageReference: String,
        val byteSize: Long,
        val checksumSha256: String,
    )

    private class StagedAnnexRestore(
        private val stagingRoot: File,
        val entries: List<StagedAnnexEntry>,
    ) {
        val createdFinalFiles = mutableListOf<File>()
        val finalReferences: Set<String> get() = entries.mapTo(linkedSetOf()) { it.storageReference }
        private var committed = false

        fun markCommitted() { committed = true }

        fun rollbackCreatedFiles() {
            if (committed) return
            createdFinalFiles.asReversed().forEach { file ->
                runCatching {
                    file.delete()
                    file.parentFile?.takeIf { it.listFiles().isNullOrEmpty() }?.delete()
                }
            }
        }

        fun cleanupStagingDirectory() {
            runCatching { stagingRoot.deleteRecursively() }
        }
    }

    private data class DecodedArchive(
        val archive: StudyPlanPackageArchive,
        val encrypted: Boolean,
        val stagingDirectory: File,
    ) {
        fun cleanup() {
            runCatching { stagingDirectory.deleteRecursively() }
        }
    }

    private companion object {
        const val AuthenticatedRestoreTempPrefix = "studyflow-authenticated-restore-"
        const val AuthenticatedRestoreTempSuffix = ".studyplan"
        const val PackageStagingPrefix = "studyflow-package-stage-"
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
