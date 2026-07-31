package com.mikeyphw.studyflow.core.model

enum class StudyMaterialType {
    BOOK,
    PDF,
    WEBSITE,
    COURSE,
    NOTE,
    VIDEO,
    LOCAL_FILE,
    OTHER,
}

enum class VideoSourceType {
    YOUTUBE,
    DIRECT_MEDIA,
    WEB_PAGE,
    LOCAL_FILE,
}

enum class AnnexStorageMode {
    IMPORTED_COPY,
    LINKED_DOCUMENT,
}

data class StudyMaterial(
    val id: StudyMaterialId,
    val studyPlanId: StudyPlanId,
    val subjectId: SubjectId?,
    val topicId: TopicId?,
    val type: StudyMaterialType,
    val title: String,
    val description: String,
    val sourceUri: String?,
    val author: String?,
    val totalUnits: Int?,
    val completedUnits: Int,
    val primary: Boolean,
    val favorite: Boolean,
    val archived: Boolean,
    val tags: List<String>,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

data class VideoResource(
    val id: VideoResourceId,
    val studyPlanId: StudyPlanId,
    val subjectId: SubjectId?,
    val topicId: TopicId?,
    val title: String,
    val sourceType: VideoSourceType,
    val sourceUri: String,
    val canonicalSourceId: String?,
    val thumbnailUri: String?,
    val durationMillis: Long?,
    val playbackPositionMillis: Long,
    val completed: Boolean,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

data class Annex(
    val id: AnnexId,
    val studyPlanId: StudyPlanId,
    val subjectId: SubjectId?,
    val topicId: TopicId?,
    val materialId: StudyMaterialId?,
    val displayName: String,
    val mimeType: String,
    val byteSize: Long,
    val checksumSha256: String?,
    val storageMode: AnnexStorageMode,
    val storageReference: String,
    val persistedPermission: Boolean,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)
