package com.mikeyphw.studyflow.core.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.room.withTransaction
import com.mikeyphw.studyflow.core.database.StudyFlowDatabase
import com.mikeyphw.studyflow.core.database.mapper.asEntity
import com.mikeyphw.studyflow.core.database.mapper.asExternalModel
import com.mikeyphw.studyflow.core.domain.AnnexFileCleanupResult
import com.mikeyphw.studyflow.core.domain.AnnexFileStorageRepository
import com.mikeyphw.studyflow.core.model.Annex
import com.mikeyphw.studyflow.core.model.AnnexId
import com.mikeyphw.studyflow.core.model.AnnexStorageMode
import com.mikeyphw.studyflow.core.model.StudyMaterialId
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.SubjectId
import com.mikeyphw.studyflow.core.model.TopicId
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class RoomAnnexFileStorageRepository(
    context: Context,
    private val database: StudyFlowDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) : AnnexFileStorageRepository {
    private val appContext = context.applicationContext
    private val filesDir = appContext.filesDir
    private val resolver = appContext.contentResolver
    private val libraryDao = database.studyLibraryDao()
    private val planDao = database.studyPlanDao()
    private val fileMutationMutex = PhysicalStorageMutationCoordinator.mutex

    override suspend fun importAnnexCopy(
        studyPlanId: StudyPlanId,
        subjectId: SubjectId?,
        topicId: TopicId?,
        materialId: StudyMaterialId?,
        displayName: String,
        mimeType: String,
        declaredByteSize: Long?,
        input: InputStream,
    ): Annex = withContext(Dispatchers.IO) {
        fileMutationMutex.withLock {
            declaredByteSize?.let { require(it <= MAX_ANNEX_BYTES) { "Annex exceeds the per-file import limit." } }
            val existingBytes = libraryDao.totalAnnexBytes(studyPlanId.value)
            require(existingBytes <= MAX_PLAN_ANNEX_BYTES) { "Study Plan annex storage already exceeds the supported limit." }
            val annexId = AnnexId(UUID.randomUUID().toString())
            val now = clock()
            val directory = annexDirectory(studyPlanId.value, annexId.value).apply { mkdirs() }.canonicalFile
            requireWithin(directory, planShareableRoot(studyPlanId.value), "Unsafe annex directory.")
            val destination = File(directory, sanitizeFileName(displayName)).canonicalFile
            requireWithin(destination, directory, "Unsafe annex file path.")
            val temporary = File(directory, ".${destination.name}.tmp").canonicalFile
            requireWithin(temporary, directory, "Unsafe annex temporary path.")
            val digest = MessageDigest.getInstance("SHA-256")
            var copiedBytes = 0L
            try {
                input.use { source ->
                    temporary.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val count = source.read(buffer)
                            if (count < 0) break
                            copiedBytes += count
                            require(copiedBytes <= MAX_ANNEX_BYTES) { "Annex exceeds the per-file import limit." }
                            require(existingBytes + copiedBytes <= MAX_PLAN_ANNEX_BYTES) {
                                "Study Plan annex storage exceeds the supported total limit."
                            }
                            digest.update(buffer, 0, count)
                            output.write(buffer, 0, count)
                        }
                    }
                }
                declaredByteSize?.let { require(it == copiedBytes) { "Annex byte size changed while importing." } }
                check(temporary.renameTo(destination)) { "Could not commit imported annex file." }
                val checksum = digest.digest().toHex()
                verifyFile(destination, copiedBytes, checksum)
                val relativeReference = destination.relativeTo(filesDir).invariantSeparatorsPath
                database.withTransaction {
                    validateOwnership(studyPlanId, subjectId, topicId, materialId)
                    val annex = Annex(
                        id = annexId,
                        studyPlanId = studyPlanId,
                        subjectId = subjectId,
                        topicId = topicId,
                        materialId = materialId,
                        displayName = displayName,
                        mimeType = mimeType,
                        byteSize = copiedBytes,
                        checksumSha256 = checksum,
                        storageMode = AnnexStorageMode.IMPORTED_COPY,
                        storageReference = relativeReference,
                        persistedPermission = false,
                        createdAtEpochMillis = now,
                        updatedAtEpochMillis = now,
                    )
                    libraryDao.upsertAnnex(annex.asEntity())
                    requireNotNull(libraryDao.findAnnex(studyPlanId.value, annexId.value)).asExternalModel()
                }
            } catch (error: Throwable) {
                temporary.delete()
                destination.delete()
                directory.takeIf { it.listFiles().isNullOrEmpty() }?.delete()
                throw error
            }
        }
    }

    override suspend fun linkDocument(
        studyPlanId: StudyPlanId,
        subjectId: SubjectId?,
        topicId: TopicId?,
        materialId: StudyMaterialId?,
        displayName: String,
        mimeType: String,
        storageReference: String,
    ): Annex = withContext(Dispatchers.IO) {
        fileMutationMutex.withLock {
            val uri = Uri.parse(storageReference)
            require(uri.scheme.equals("content", ignoreCase = true)) { "Linked annexes require a content URI." }
            val normalizedReference = uri.toString()
            val permissionFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            val existingReferenceCount = libraryDao.countAnnexesByStorageReference(normalizedReference)
            val alreadyPersisted = resolver.persistedUriPermissions.any { permission ->
                permission.uri == uri && permission.isReadPermission
            }
            if (!alreadyPersisted) {
                resolver.takePersistableUriPermission(uri, permissionFlags)
            }
            try {
                val digest = MessageDigest.getInstance("SHA-256")
                var bytes = 0L
                val source = requireNotNull(resolver.openInputStream(uri)) { "Linked document cannot be opened." }
                source.use {
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = it.read(buffer)
                        if (count < 0) break
                        bytes += count
                        require(bytes <= MAX_ANNEX_BYTES) { "Linked document exceeds the per-file limit." }
                        digest.update(buffer, 0, count)
                    }
                }
                val existingBytes = libraryDao.totalAnnexBytes(studyPlanId.value)
                require(existingBytes + bytes <= MAX_PLAN_ANNEX_BYTES) {
                    "Study Plan annex storage exceeds the supported total limit."
                }
                val annexId = AnnexId(UUID.randomUUID().toString())
                val now = clock()
                database.withTransaction {
                    validateOwnership(studyPlanId, subjectId, topicId, materialId)
                    val annex = Annex(
                        id = annexId,
                        studyPlanId = studyPlanId,
                        subjectId = subjectId,
                        topicId = topicId,
                        materialId = materialId,
                        displayName = displayName,
                        mimeType = mimeType,
                        byteSize = bytes,
                        checksumSha256 = digest.digest().toHex(),
                        storageMode = AnnexStorageMode.LINKED_DOCUMENT,
                        storageReference = normalizedReference,
                        persistedPermission = true,
                        createdAtEpochMillis = now,
                        updatedAtEpochMillis = now,
                    )
                    libraryDao.upsertAnnex(annex.asEntity())
                    requireNotNull(libraryDao.findAnnex(studyPlanId.value, annexId.value)).asExternalModel()
                }
            } catch (error: Throwable) {
                val stillReferenced = libraryDao.countAnnexesByStorageReference(normalizedReference) > 0
                if (!alreadyPersisted && existingReferenceCount == 0 && !stillReferenced) {
                    runCatching { resolver.releasePersistableUriPermission(uri, permissionFlags) }
                }
                throw error
            }
        }
    }

    override suspend fun deleteStoredAnnex(studyPlanId: StudyPlanId, annexId: AnnexId) {
        withContext(Dispatchers.IO) {
            fileMutationMutex.withLock {
                val deleted = database.withTransaction {
                    val annex = libraryDao.findAnnex(studyPlanId.value, annexId.value)
                        ?: return@withTransaction null
                    require(libraryDao.deleteAnnex(studyPlanId.value, annexId.value) == 1) {
                        "Annex disappeared while deleting."
                    }
                    annex
                } ?: return@withLock

                when (deleted.storageMode) {
                    AnnexStorageMode.IMPORTED_COPY.name -> if (
                        deleted.storageReference.isNotBlank() &&
                        libraryDao.countAnnexesByStorageReference(deleted.storageReference) == 0
                    ) {
                        val ownedFile = resolveAnnexReference(deleted.storageReference)
                        if (ownedFile.exists()) ownedFile.delete()
                        ownedFile.parentFile?.takeIf { it.listFiles().isNullOrEmpty() }?.delete()
                    }
                    AnnexStorageMode.LINKED_DOCUMENT.name -> if (
                        deleted.persistedPermission && libraryDao.countAnnexesByStorageReference(deleted.storageReference) == 0
                    ) {
                        releasePersistedReadPermission(deleted.storageReference)
                    }
                }
            }
        }
    }

    override suspend fun cleanupOrphanedAnnexFiles(studyPlanId: StudyPlanId): AnnexFileCleanupResult =
        withContext(Dispatchers.IO) {
            fileMutationMutex.withLock {
                val root = planShareableRoot(studyPlanId.value)
                if (!root.exists()) return@withLock AnnexFileCleanupResult(0, 0)
                // Imported-copy references used to be assumed plan-local. Pre-remediation duplication could
                // leave another plan pointing at bytes below this plan's shareable root, so orphan cleanup
                // must honor every surviving imported-copy row before removing app-owned bytes.
                val referenced = libraryDao.findImportedStorageReferences()
                    .mapNotNull { reference ->
                        runCatching { resolveAnnexReference(reference).canonicalPath }.getOrNull()
                    }
                    .toSet()
                var removedFiles = 0
                var removedBytes = 0L
                root.walkBottomUp()
                    .filter { file -> file.isFile && file.canonicalPath !in referenced }
                    .forEach { file ->
                        removedBytes += file.length()
                        if (file.delete()) removedFiles++
                    }
                root.walkBottomUp()
                    .filter { file -> file.isDirectory && file.listFiles().isNullOrEmpty() && file != root }
                    .forEach(File::delete)
                AnnexFileCleanupResult(removedFiles, removedBytes)
            }
        }

    override suspend fun migrateOwnedAnnexesToShareableRoot(): Int = withContext(Dispatchers.IO) {
        fileMutationMutex.withLock {
            var migrated = 0
            planDao.snapshotAll().forEach { plan ->
                libraryDao.findAnnexes(plan.id)
                    .filter { annex ->
                        annex.storageMode == AnnexStorageMode.IMPORTED_COPY.name &&
                            !annex.storageReference.startsWith("shareable-annexes/")
                    }
                    .forEach { annex ->
                        val source = resolveLegacyAnnexReference(annex.storageReference)
                        if (!source.isFile) return@forEach
                        verifyFile(source, annex.byteSize, annex.checksumSha256)
                        val verifiedChecksum = annex.checksumSha256?.takeIf(String::isNotBlank)
                            ?: sha256(source)
                        val directory = annexDirectory(plan.id, annex.id).apply { mkdirs() }.canonicalFile
                        requireWithin(directory, planShareableRoot(plan.id), "Unsafe migrated annex directory.")
                        val destination = File(directory, source.name).canonicalFile
                        requireWithin(destination, directory, "Unsafe migrated annex path.")
                        source.copyTo(destination, overwrite = false)
                        try {
                            verifyFile(destination, annex.byteSize, verifiedChecksum)
                            val relative = destination.relativeTo(filesDir).invariantSeparatorsPath
                            database.withTransaction {
                                require(
                                    libraryDao.updateAnnexStorage(
                                        planId = plan.id,
                                        annexId = annex.id,
                                        storageMode = AnnexStorageMode.IMPORTED_COPY.name,
                                        storageReference = relative,
                                        persistedPermission = false,
                                        byteSize = annex.byteSize,
                                        checksumSha256 = verifiedChecksum,
                                        updatedAtEpochMillis = clock(),
                                    ) == 1,
                                ) { "Annex disappeared while relocating private storage." }
                            }
                            // A historical duplicated row may still reference the same legacy file. Keep
                            // the source until the last database reference has moved away from it.
                            if (libraryDao.countAnnexesByStorageReference(annex.storageReference) == 0) {
                                source.delete()
                                source.parentFile?.takeIf { it.listFiles().isNullOrEmpty() }?.delete()
                            }
                            migrated++
                        } catch (error: Throwable) {
                            destination.delete()
                            directory.takeIf { it.listFiles().isNullOrEmpty() }?.delete()
                            throw error
                        }
                    }
            }
            migrated
        }
    }

    internal fun resolveAnnexReference(reference: String): File {
        val candidate = File(filesDir, reference).canonicalFile
        requireWithin(candidate, shareableRoot(), "Unsafe annex path.")
        return candidate
    }

    private fun resolveLegacyAnnexReference(reference: String): File {
        val candidate = File(filesDir, reference).canonicalFile
        val legacyRoot = File(filesDir, "study-plans").canonicalFile
        requireWithin(candidate, legacyRoot, "Unsafe legacy annex path.")
        val relativeParts = candidate.relativeTo(legacyRoot).invariantSeparatorsPath.split('/')
        require(relativeParts.size >= 3 && relativeParts[1] == "annexes") {
            "Legacy annex paths must remain inside a Study Plan annex directory."
        }
        return candidate
    }

    private suspend fun validateOwnership(
        studyPlanId: StudyPlanId,
        subjectId: SubjectId?,
        topicId: TopicId?,
        materialId: StudyMaterialId?,
    ) {
        requireNotNull(planDao.findById(studyPlanId.value)) { "Study Plan does not exist." }
        subjectId?.let { id ->
            requireNotNull(database.studyStructureDao().findSubject(studyPlanId.value, id.value)) {
                "The selected subject does not belong to this Study Plan."
            }
        }
        topicId?.let { id ->
            requireNotNull(database.studyStructureDao().findTopic(studyPlanId.value, id.value)) {
                "The selected topic does not belong to this Study Plan."
            }
        }
        materialId?.let { id ->
            requireNotNull(libraryDao.findMaterial(studyPlanId.value, id.value)) {
                "The selected material does not belong to this Study Plan."
            }
        }
    }

    private fun annexDirectory(planId: String, annexId: String): File =
        File(filesDir, "shareable-annexes/${sanitizeSegment(planId)}/${sanitizeSegment(annexId)}")

    private fun shareableRoot(): File = File(filesDir, "shareable-annexes").canonicalFile

    private fun planShareableRoot(planId: String): File =
        File(shareableRoot(), sanitizeSegment(planId)).canonicalFile

    private fun requireWithin(candidate: File, root: File, message: String) {
        val rootPath = root.canonicalPath
        val candidatePath = candidate.canonicalPath
        require(candidatePath.startsWith(rootPath + File.separator)) { message }
    }

    private fun verifyFile(file: File, byteSize: Long, checksum: String?) {
        require(file.isFile) { "Annex payload is missing." }
        require(file.length() == byteSize) { "Annex byte-size verification failed." }
        checksum?.let { expected ->
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            require(digest.digest().toHex().equals(expected, ignoreCase = true)) {
                "Annex checksum verification failed."
            }
        }
    }

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
        return digest.digest().toHex()
    }

    private fun releasePersistedReadPermission(storageReference: String) {
        val uri = Uri.parse(storageReference)
        if (!uri.scheme.equals("content", ignoreCase = true)) return
        runCatching {
            resolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun sanitizeSegment(value: String): String = value.replace(Regex("[^A-Za-z0-9._:-]"), "_")

    private fun sanitizeFileName(value: String): String = value
        .replace(Regex("[\\/\\u0000-\\u001F]"), "_")
        .trim()
        .ifBlank { "annex.bin" }
        .take(120)

    private fun ByteArray.toHex(): String = joinToString("") { byte -> "%02x".format(byte) }

    private companion object {
        const val MAX_ANNEX_BYTES = 256L * 1024L * 1024L
        const val MAX_PLAN_ANNEX_BYTES = 1024L * 1024L * 1024L
    }
}
