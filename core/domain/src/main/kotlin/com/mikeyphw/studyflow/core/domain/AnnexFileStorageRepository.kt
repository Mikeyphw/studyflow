package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.Annex
import com.mikeyphw.studyflow.core.model.AnnexId
import com.mikeyphw.studyflow.core.model.StudyMaterialId
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.SubjectId
import com.mikeyphw.studyflow.core.model.TopicId
import java.io.InputStream

interface AnnexFileStorageRepository {
    suspend fun importAnnexCopy(
        studyPlanId: StudyPlanId,
        subjectId: SubjectId?,
        topicId: TopicId?,
        materialId: StudyMaterialId?,
        displayName: String,
        mimeType: String,
        declaredByteSize: Long?,
        input: InputStream,
    ): Annex

    suspend fun linkDocument(
        studyPlanId: StudyPlanId,
        subjectId: SubjectId?,
        topicId: TopicId?,
        materialId: StudyMaterialId?,
        displayName: String,
        mimeType: String,
        storageReference: String,
    ): Annex

    suspend fun deleteStoredAnnex(studyPlanId: StudyPlanId, annexId: AnnexId)

    suspend fun cleanupOrphanedAnnexFiles(studyPlanId: StudyPlanId): AnnexFileCleanupResult

    suspend fun migrateOwnedAnnexesToShareableRoot(): Int = 0
}

data class AnnexFileCleanupResult(
    val removedFiles: Int,
    val removedBytes: Long,
)

class ManageAnnexFileStorage(
    private val repository: AnnexFileStorageRepository,
) {
    suspend fun importCopy(
        studyPlanId: StudyPlanId,
        subjectId: SubjectId? = null,
        topicId: TopicId? = null,
        materialId: StudyMaterialId? = null,
        displayName: String,
        mimeType: String,
        declaredByteSize: Long? = null,
        input: InputStream,
    ): Annex {
        require(displayName.isNotBlank()) { "Annex name cannot be blank." }
        require(declaredByteSize == null || declaredByteSize >= 0L) { "Annex size cannot be negative." }
        return repository.importAnnexCopy(
            studyPlanId = studyPlanId,
            subjectId = subjectId,
            topicId = topicId,
            materialId = materialId,
            displayName = displayName.trim(),
            mimeType = mimeType.ifBlank { "application/octet-stream" },
            declaredByteSize = declaredByteSize,
            input = input,
        )
    }

    suspend fun linkDocument(
        studyPlanId: StudyPlanId,
        subjectId: SubjectId? = null,
        topicId: TopicId? = null,
        materialId: StudyMaterialId? = null,
        displayName: String,
        mimeType: String,
        storageReference: String,
    ): Annex {
        require(displayName.isNotBlank()) { "Annex name cannot be blank." }
        require(storageReference.isNotBlank()) { "Document URI cannot be blank." }
        return repository.linkDocument(
            studyPlanId = studyPlanId,
            subjectId = subjectId,
            topicId = topicId,
            materialId = materialId,
            displayName = displayName.trim(),
            mimeType = mimeType.ifBlank { "application/octet-stream" },
            storageReference = storageReference.trim(),
        )
    }

    suspend fun deleteStoredAnnex(studyPlanId: StudyPlanId, annexId: AnnexId) =
        repository.deleteStoredAnnex(studyPlanId, annexId)

    suspend fun cleanupOrphans(studyPlanId: StudyPlanId): AnnexFileCleanupResult =
        repository.cleanupOrphanedAnnexFiles(studyPlanId)
}
