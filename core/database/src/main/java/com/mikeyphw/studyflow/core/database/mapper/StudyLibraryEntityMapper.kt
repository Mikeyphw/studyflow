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

fun StudyMaterialEntity.asExternalModel(): StudyMaterial = decodeModel("StudyMaterialEntity") {
    StudyMaterial(
        id = StudyMaterialId(id),
        studyPlanId = StudyPlanId(studyPlanId),
        subjectId = subjectId?.let(::SubjectId),
        topicId = topicId?.let(::TopicId),
        type = type.decodeEnum<StudyMaterialType>("StudyLibraryEntityMapper.type"),
        title = title,
        description = description,
        sourceUri = sourceUri,
        author = author,
        totalUnits = totalUnits,
        completedUnits = completedUnits,
        primary = primary,
        favorite = favorite,
        archived = archived,
        tags = tagsJson.decodeStringListStable("StudyLibraryEntityMapper.tagsJson"),
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )
}

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
    tagsJson = tags.encodeStringListStable(),
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

fun VideoResourceEntity.asExternalModel(): VideoResource = decodeModel("VideoResourceEntity") {
    VideoResource(
        id = VideoResourceId(id),
        studyPlanId = StudyPlanId(studyPlanId),
        subjectId = subjectId?.let(::SubjectId),
        topicId = topicId?.let(::TopicId),
        title = title,
        sourceType = sourceType.decodeEnum<VideoSourceType>("StudyLibraryEntityMapper.sourceType"),
        sourceUri = sourceUri,
        canonicalSourceId = canonicalSourceId,
        thumbnailUri = thumbnailUri,
        durationMillis = durationMillis,
        playbackPositionMillis = playbackPositionMillis,
        completed = completed,
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )
}

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

fun AnnexEntity.asExternalModel(): Annex = decodeModel("AnnexEntity") {
    Annex(
        id = AnnexId(id),
        studyPlanId = StudyPlanId(studyPlanId),
        subjectId = subjectId?.let(::SubjectId),
        topicId = topicId?.let(::TopicId),
        materialId = materialId?.let(::StudyMaterialId),
        displayName = displayName,
        mimeType = mimeType,
        byteSize = byteSize,
        checksumSha256 = checksumSha256,
        storageMode = storageMode.decodeEnum<AnnexStorageMode>("StudyLibraryEntityMapper.storageMode"),
        storageReference = storageReference,
        persistedPermission = persistedPermission,
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )
}

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
