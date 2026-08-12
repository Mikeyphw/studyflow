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
) {
    init {
        require(title.isNotBlank()) { "Study material title cannot be blank." }
        require(totalUnits == null || totalUnits >= 0) { "Total units cannot be negative." }
        require(completedUnits >= 0) { "Completed units cannot be negative." }
        require(totalUnits == null || completedUnits <= totalUnits) { "Completed units cannot exceed total units." }
    }
}

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
) {
    init {
        require(title.isNotBlank()) { "Video title cannot be blank." }
        require(sourceUri.isNotBlank()) { "Video source cannot be blank." }
        require(durationMillis == null || durationMillis >= 0) { "Video duration cannot be negative." }
        require(playbackPositionMillis >= 0) { "Playback position cannot be negative." }
    }
}

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
) {
    init {
        require(displayName.isNotBlank()) { "Annex display name cannot be blank." }
        require(mimeType.isNotBlank()) { "Annex MIME type cannot be blank." }
        require(byteSize >= 0) { "Annex byte size cannot be negative." }
        require(storageReference.isNotBlank()) { "Annex storage reference cannot be blank." }
        require(checksumSha256 == null || checksumSha256.matches(Regex("[0-9a-fA-F]{64}"))) {
            "Annex checksum must be SHA-256 when present."
        }
    }
}
