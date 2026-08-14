package com.mikeyphw.studyflow.core.data

import com.mikeyphw.studyflow.core.database.StudyFlowDatabase
import com.mikeyphw.studyflow.core.database.entity.AnnexEntity
import com.mikeyphw.studyflow.core.database.entity.PlanSnapshotEntity
import com.mikeyphw.studyflow.core.model.AnnexStorageMode
import java.io.File
import java.security.MessageDigest

/**
 * Prepares physically independent file-backed rows for a duplicated Study Plan.
 *
 * Bytes are copied before the Room transaction. If the database transaction fails, [Prepared.cleanup]
 * removes every destination created here. Linked SAF documents remain links because the app does not
 * own those external bytes; their rows still receive independent ids and deleting either plan never
 * deletes the external document.
 */
internal class PlanPhysicalResourceDuplicator(
    private val filesDir: File,
    private val database: StudyFlowDatabase,
) {
    suspend fun prepare(sourcePlanId: String, newPlanId: String): Prepared {
        val createdFiles = mutableListOf<File>()
        val createdDirectories = mutableListOf<File>()
        try {
            val annexes = database.studyLibraryDao().findAnnexes(sourcePlanId).map { source ->
                val newAnnexId = remap(newPlanId, source.id)
                val reference = if (source.storageMode == AnnexStorageMode.IMPORTED_COPY.name) {
                    val sourceFile = resolveAnnexReference(sourcePlanId, source.storageReference)
                    verifyFile(sourceFile, source.byteSize, source.checksumSha256, "annex")
                    val directory = File(
                        filesDir,
                        "shareable-annexes/${sanitizeSegment(newPlanId)}/${sanitizeSegment(newAnnexId)}",
                    ).canonicalFile
                    require(directory.mkdirs() || directory
                        .isDirectory) { "Could not create duplicated annex directory." }
                    createdDirectories += directory
                    val destination = File(directory, sourceFile.name).canonicalFile
                    require(destination.path.startsWith(directory.path + File
                        .separator)) { "Unsafe duplicated annex path." }
                    sourceFile.copyTo(destination, overwrite = false)
                    createdFiles += destination
                    verifyFile(destination, source.byteSize, source.checksumSha256, "duplicated annex")
                    destination.relativeTo(filesDir).invariantSeparatorsPath
                } else {
                    source.storageReference
                }
                source.copy(
                    id = newAnnexId,
                    studyPlanId = newPlanId,
                    subjectId = source.subjectId?.let { remap(newPlanId, it) },
                    topicId = source.topicId?.let { remap(newPlanId, it) },
                    materialId = source.materialId?.let { remap(newPlanId, it) },
                    storageReference = reference,
                )
            }
            val snapshots = database.studySupportDao().findSnapshots(sourcePlanId).map { source ->
                val sourceFile = resolveSnapshotReference(sourcePlanId, source.payloadReference)
                verifyFile(sourceFile, sourceFile.length(), source.checksumSha256, "snapshot")
                val newSnapshotId = remap(newPlanId, source.id)
                val directory = File(filesDir, "study-plans/${sanitizeSegment(newPlanId)}/snapshots").canonicalFile
                require(directory.mkdirs() || directory
                    .isDirectory) { "Could not create duplicated snapshot directory." }
                createdDirectories += directory
                val extension = sourceFile.extension.takeIf(String::isNotBlank)?.let { ".$it" }.orEmpty()
                val destination = File(directory, "${sanitizeSegment(newSnapshotId)}$extension").canonicalFile
                require(destination.path.startsWith(directory.path + File
                    .separator)) { "Unsafe duplicated snapshot path." }
                sourceFile.copyTo(destination, overwrite = false)
                createdFiles += destination
                verifyFile(destination, sourceFile.length(), source.checksumSha256, "duplicated snapshot")
                source.copy(
                    id = newSnapshotId,
                    studyPlanId = newPlanId,
                    payloadReference = destination.relativeTo(filesDir).invariantSeparatorsPath,
                )
            }
            return Prepared(annexes, snapshots, createdFiles, createdDirectories)
        } catch (error: Throwable) {
            cleanupCreatedFiles(createdFiles, createdDirectories)
            throw error
        }
    }

    suspend fun deletePlanOwnedFiles(planId: String) {
        // New copies have independent physical bytes, but old databases can contain rows in another
        // plan that still point into this plan's legacy/snapshot/shareable directories. Query after
        // the target plan's database cascade has committed and preserve every surviving reference.
        val retainedPaths = (
            database.studyLibraryDao().findImportedStorageReferences() +
                database.studySupportDao().findSnapshotPayloadReferences()
            )
            .mapTo(linkedSetOf(), ::resolveOwnedReferencePath)

        val root = File(filesDir, "study-plans/${sanitizeSegment(planId)}").canonicalFile
        val ownedRoot = File(filesDir, "study-plans").canonicalFile
        require(root.path.startsWith(ownedRoot.path + File.separator)) { "Unsafe Study Plan storage path." }
        deleteTreeExceptRetainedFiles(root, retainedPaths)

        val annexRoot = File(filesDir, "shareable-annexes/${sanitizeSegment(planId)}").canonicalFile
        val shareableRoot = File(filesDir, "shareable-annexes").canonicalFile
        require(annexRoot.path.startsWith(shareableRoot.path + File.separator)) { "Unsafe annex storage path." }
        deleteTreeExceptRetainedFiles(annexRoot, retainedPaths)
    }

    data class Prepared(
        val annexes: List<AnnexEntity>,
        val snapshots: List<PlanSnapshotEntity>,
        private val createdFiles: List<File>,
        private val createdDirectories: List<File>,
    ) {
        fun cleanup() = PlanPhysicalResourceDuplicator.cleanupCreatedFiles(createdFiles, createdDirectories)
    }

    private fun resolveAnnexReference(planId: String, reference: String): File {
        val file = File(filesDir, reference).canonicalFile
        val root = File(filesDir, "shareable-annexes/${sanitizeSegment(planId)}").canonicalFile
        require(file.path.startsWith(root.path + File.separator)) { "Unsafe duplicated annex source path." }
        require(file.isFile) { "Owned annex is missing from private storage." }
        return file
    }

    private fun resolveSnapshotReference(planId: String, reference: String): File {
        val file = File(filesDir, reference).canonicalFile
        val root = File(filesDir, "study-plans/${sanitizeSegment(planId)}/snapshots").canonicalFile
        require(file.path.startsWith(root.path + File.separator)) { "Unsafe duplicated snapshot source path." }
        require(file.isFile) { "Owned snapshot is missing from private storage." }
        return file
    }

    private fun resolveOwnedReferencePath(reference: String): String {
        val candidate = File(filesDir, reference).canonicalFile
        val root = filesDir.canonicalFile
        require(candidate.path.startsWith(root.path + File.separator)) { "Unsafe persisted file reference." }
        return candidate.canonicalPath
    }

    private fun deleteTreeExceptRetainedFiles(root: File, retainedPaths: Set<String>) {
        if (!root.exists()) return
        root.walkBottomUp().forEach { entry ->
            when {
                entry.isFile && entry.canonicalPath !in retainedPaths -> entry.delete()
                entry.isDirectory && entry.listFiles().isNullOrEmpty() -> entry.delete()
            }
        }
    }

    private fun verifyFile(file: File, expectedSize: Long, expectedChecksum: String?, label: String) {
        require(file.isFile) { "$label file is missing." }
        require(file.length() == expectedSize) { "$label byte-size verification failed." }
        expectedChecksum?.let { checksum ->
            require(sha256(file).equals(checksum, ignoreCase = true)) { "$label checksum verification failed." }
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
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun remap(newPlanId: String, oldId: String): String = "$newPlanId:$oldId"

    private fun sanitizeSegment(value: String): String = value.replace(Regex("[^A-Za-z0-9._:-]"), "_")

    private companion object {
        fun cleanupCreatedFiles(files: List<File>, directories: List<File>) {
            files.asReversed().forEach { file -> runCatching { file.delete() } }
            directories.distinct().asReversed().forEach { directory ->
                runCatching {
                    if (directory.exists() && directory.listFiles().isNullOrEmpty()) directory.delete()
                }
            }
        }
    }
}
