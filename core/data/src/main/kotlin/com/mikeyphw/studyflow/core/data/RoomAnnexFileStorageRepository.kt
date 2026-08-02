package com.mikeyphw.studyflow.core.data

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
import kotlinx.coroutines.withContext

internal class RoomAnnexFileStorageRepository(
    private val filesDir: File,
    private val database: StudyFlowDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) : AnnexFileStorageRepository {
    private val libraryDao = database.studyLibraryDao()
    private val planDao = database.studyPlanDao()

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
        val annexId = AnnexId(UUID.randomUUID().toString())
        val now = clock()
        val directory = annexDirectory(studyPlanId.value, annexId.value).apply { mkdirs() }
        val destination = File(directory, sanitizeFileName(displayName)).canonicalFile
        require(destination.path.startsWith(directory.canonicalPath + File.separator)) { "Unsafe annex file path." }
        val temporary = File(directory, ".${destination.name}.tmp")
        val digest = MessageDigest.getInstance("SHA-256")
        var copiedBytes = 0L
        try {
            input.use { source ->
                temporary.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                        copiedBytes += count
                    }
                }
            }
            declaredByteSize?.let { require(it == copiedBytes) { "Annex byte size changed while importing." } }
            check(temporary.renameTo(destination)) { "Could not commit imported annex file." }
            val checksum = digest.digest().joinToString("") { "%02x".format(it) }
            val relativeReference = destination.relativeTo(filesDir).invariantSeparatorsPath
            database.withTransaction {
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
            directory.delete()
            throw error
        }
    }

    override suspend fun deleteStoredAnnex(studyPlanId: StudyPlanId, annexId: AnnexId) {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                val annex = libraryDao.findAnnex(studyPlanId.value, annexId.value)
                annex?.storageReference?.takeIf(String::isNotBlank)?.let { reference ->
                    resolveAnnexReference(reference).delete()
                }
                libraryDao.deleteAnnex(studyPlanId.value, annexId.value)
            }
        }
    }

    override suspend fun cleanupOrphanedAnnexFiles(studyPlanId: StudyPlanId): AnnexFileCleanupResult =
        withContext(Dispatchers.IO) {
            val root = File(filesDir, "study-plans/${studyPlanId.value}/annexes").canonicalFile
            if (!root.exists()) return@withContext AnnexFileCleanupResult(0, 0)
            val referenced = libraryDao.findAnnexes(studyPlanId.value)
                .mapNotNull { annex -> annex.storageReference.takeIf(String::isNotBlank) }
                .map { reference -> resolveAnnexReference(reference).canonicalPath }
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

    internal fun resolveAnnexReference(reference: String): File {
        val candidate = File(filesDir, reference).canonicalFile
        require(candidate.path.startsWith(filesDir.canonicalPath + File.separator)) { "Unsafe annex path." }
        return candidate
    }

    private fun annexDirectory(planId: String, annexId: String): File =
        File(filesDir, "study-plans/${sanitizeSegment(planId)}/annexes/${sanitizeSegment(annexId)}")

    private fun sanitizeSegment(value: String): String = value.replace(Regex("[^A-Za-z0-9._:-]"), "_")

    private fun sanitizeFileName(value: String): String = value
        .replace(Regex("[\\/\u0000-\u001F]"), "_")
        .trim()
        .ifBlank { "annex.bin" }
        .take(120)
}
