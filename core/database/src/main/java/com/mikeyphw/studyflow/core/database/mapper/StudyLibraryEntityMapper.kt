package com.mikeyphw.studyflow.core.database.mapper

import com.mikeyphw.studyflow.core.database.entity.AnnexEntity
import com.mikeyphw.studyflow.core.database.entity.StudyMaterialEntity
import com.mikeyphw.studyflow.core.database.entity.VideoResourceEntity
import com.mikeyphw.studyflow.core.model.Annex
import com.mikeyphw.studyflow.core.model.AnnexId
import com.mikeyphw.studyflow.core.model.AnnexStorageMode
import com.mikeyphw.studyflow.core.model.StudyMaterial
import com.mikeyphw.studyflow.core.model.StudyMaterialId
import com.mikeyphw.studyflow.core.model.StudyMaterialType
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.SubjectId
import com.mikeyphw.studyflow.core.model.TopicId
import com.mikeyphw.studyflow.core.model.VideoResource
import com.mikeyphw.studyflow.core.model.VideoResourceId
import com.mikeyphw.studyflow.core.model.VideoSourceType
import java.nio.charset.StandardCharsets
import java.util.Base64

private const val ListSeparator = "\n"

fun StudyMaterialEntity.asExternalModel(): StudyMaterial = StudyMaterial(
    id = StudyMaterialId(id),
    studyPlanId = StudyPlanId(studyPlanId),
    subjectId = subjectId?.let(::SubjectId),
    topicId = topicId?.let(::TopicId),
    type = enumValueOf<StudyMaterialType>(type),
    title = title,
    description = description,
    sourceUri = sourceUri,
    author = author,
    totalUnits = totalUnits,
    completedUnits = completedUnits,
    primary = primary,
    favorite = favorite,
    archived = archived,
    tags = tagsJson.decodeStringList(),
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

fun StudyMaterial.asEntity(): StudyMaterialEntity = StudyMaterialEntity(
    id = id.value,
    studyPlanId = studyPlanId.value,
    subjectId = subjectId?.value,
    topicId = topicId?.value,
    type = type.name,
    title = title,
    description = description,
    sourceUri = sourceUri,
    author = author,
    totalUnits = totalUnits,
    completedUnits = completedUnits,
    primary = primary,
    favorite = favorite,
    archived = archived,
    tagsJson = tags.encodeStringList(),
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

fun VideoResourceEntity.asExternalModel(): VideoResource = VideoResource(
    id = VideoResourceId(id),
    studyPlanId = StudyPlanId(studyPlanId),
    subjectId = subjectId?.let(::SubjectId),
    topicId = topicId?.let(::TopicId),
    title = title,
    sourceType = enumValueOf<VideoSourceType>(sourceType),
    sourceUri = sourceUri,
    canonicalSourceId = canonicalSourceId,
    thumbnailUri = thumbnailUri,
    durationMillis = durationMillis,
    playbackPositionMillis = playbackPositionMillis,
    completed = completed,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

fun VideoResource.asEntity(): VideoResourceEntity = VideoResourceEntity(
    id = id.value,
    studyPlanId = studyPlanId.value,
    subjectId = subjectId?.value,
    topicId = topicId?.value,
    title = title,
    sourceType = sourceType.name,
    sourceUri = sourceUri,
    canonicalSourceId = canonicalSourceId,
    thumbnailUri = thumbnailUri,
    durationMillis = durationMillis,
    playbackPositionMillis = playbackPositionMillis,
    completed = completed,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

fun AnnexEntity.asExternalModel(): Annex = Annex(
    id = AnnexId(id),
    studyPlanId = StudyPlanId(studyPlanId),
    subjectId = subjectId?.let(::SubjectId),
    topicId = topicId?.let(::TopicId),
    materialId = materialId?.let(::StudyMaterialId),
    displayName = displayName,
    mimeType = mimeType,
    byteSize = byteSize,
    checksumSha256 = checksumSha256,
    storageMode = enumValueOf<AnnexStorageMode>(storageMode),
    storageReference = storageReference,
    persistedPermission = persistedPermission,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

fun Annex.asEntity(): AnnexEntity = AnnexEntity(
    id = id.value,
    studyPlanId = studyPlanId.value,
    subjectId = subjectId?.value,
    topicId = topicId?.value,
    materialId = materialId?.value,
    displayName = displayName,
    mimeType = mimeType,
    byteSize = byteSize,
    checksumSha256 = checksumSha256,
    storageMode = storageMode.name,
    storageReference = storageReference,
    persistedPermission = persistedPermission,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

private fun List<String>.encodeStringList(): String = joinToString(ListSeparator) { value ->
    Base64.getEncoder().encodeToString(value.toByteArray(StandardCharsets.UTF_8))
}

private fun String.decodeStringList(): List<String> = if (isBlank()) {
    emptyList()
} else {
    lineSequence().mapNotNull { encoded ->
        runCatching { String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8) }.getOrNull()
    }.toList()
}
