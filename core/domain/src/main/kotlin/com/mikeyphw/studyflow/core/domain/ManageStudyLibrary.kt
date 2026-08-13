package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.Annex
import com.mikeyphw.studyflow.core.model.StudyMaterial
import com.mikeyphw.studyflow.core.model.StudyMaterialId
import com.mikeyphw.studyflow.core.model.StudyMaterialType
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.SubjectId
import com.mikeyphw.studyflow.core.model.TopicId
import com.mikeyphw.studyflow.core.model.VideoResource
import com.mikeyphw.studyflow.core.model.VideoResourceId
import com.mikeyphw.studyflow.core.model.VideoSourceType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class ManageStudyLibrary(
    private val repository: StudyLibraryRepository,
    private val clock: () -> Long = System::currentTimeMillis,
    private val resourcePlanner: StudyResourceLaunchPlanner = StudyResourceLaunchPlanner(),
) {
    fun observeMaterials(planId: StudyPlanId): Flow<List<StudyMaterial>> = repository.observeMaterials(planId)
    fun observeVideos(planId: StudyPlanId): Flow<List<VideoResource>> = repository.observeVideos(planId)
    fun observeAnnexes(planId: StudyPlanId): Flow<List<Annex>> = repository.observeAnnexes(planId)

    fun observeSnapshot(planId: StudyPlanId): Flow<StudyLibrarySnapshot> = combine(
        repository.observeMaterials(planId),
        repository.observeVideos(planId),
        repository.observeAnnexes(planId),
    ) { materials, videos, annexes -> StudyLibrarySnapshot(materials, videos, annexes) }

    fun isValidMaterialSource(type: StudyMaterialType, sourceUri: String?): Boolean {
        val normalized = sourceUri?.trim()?.ifBlank { null }
        if (type in EXTERNALLY_LAUNCHED_MATERIAL_TYPES && normalized == null) return false
        return normalized == null || resourcePlanner.planForMaterialSource(type, normalized).isLaunchable()
    }

    fun isValidVideoSource(sourceType: VideoSourceType, sourceUri: String?): Boolean =
        resourcePlanner.planForVideoSource(sourceType, sourceUri).isLaunchable()

    suspend fun createMaterial(
        id: StudyMaterialId,
        studyPlanId: StudyPlanId,
        subjectId: SubjectId?,
        topicId: TopicId?,
        type: StudyMaterialType,
        title: String,
        description: String,
        sourceUri: String?,
        author: String?,
        totalUnits: Int?,
        tags: List<String>,
    ): StudyMaterial {
        require(title.isNotBlank()) { "Material title cannot be blank." }
        require(totalUnits == null || totalUnits > 0) { "Total units must be positive when present." }
        val normalizedSource = sourceUri?.trim()?.ifBlank { null }
        if (type in EXTERNALLY_LAUNCHED_MATERIAL_TYPES) {
            require(normalizedSource != null) { "This material type requires a launchable source URI." }
        }
        normalizedSource?.let { source ->
            require(resourcePlanner.planForMaterialSource(type, source).isLaunchable()) {
                "Material source URI is invalid for the selected material type."
            }
        }
        val now = clock()
        return repository.upsertMaterial(
            StudyMaterial(
                id = id,
                studyPlanId = studyPlanId,
                subjectId = subjectId,
                topicId = topicId,
                type = type,
                title = title.trim(),
                description = description.trim(),
                sourceUri = normalizedSource,
                author = author?.trim()?.ifBlank { null },
                totalUnits = totalUnits,
                completedUnits = 0,
                primary = false,
                favorite = false,
                archived = false,
                tags = tags.cleanTags(),
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
            ),
        )
    }

    suspend fun toggleFavorite(planId: StudyPlanId, materialId: StudyMaterialId): StudyMaterial {
        val material = requireNotNull(repository.findMaterial(planId, materialId)) { "Material does not exist." }
        return repository.upsertMaterial(material.copy(favorite = !material.favorite, updatedAtEpochMillis = clock()))
    }

    suspend fun updateMaterialProgress(
        planId: StudyPlanId,
        materialId: StudyMaterialId,
        completedUnits: Int,
    ): StudyMaterial {
        val material = requireNotNull(repository.findMaterial(planId, materialId)) { "Material does not exist." }
        require(completedUnits >= 0) { "Completed units cannot be negative." }
        material.totalUnits?.let { total ->
            require(completedUnits <= total) { "Completed units cannot exceed total units." }
        }
        return repository.upsertMaterial(material.copy(completedUnits = completedUnits, updatedAtEpochMillis = clock()))
    }

    suspend fun archiveMaterial(planId: StudyPlanId, materialId: StudyMaterialId): StudyMaterial {
        val material = requireNotNull(repository.findMaterial(planId, materialId)) { "Material does not exist." }
        return repository.upsertMaterial(material.copy(archived = true, updatedAtEpochMillis = clock()))
    }

    suspend fun restoreMaterial(planId: StudyPlanId, materialId: StudyMaterialId): StudyMaterial {
        val material = requireNotNull(repository.findMaterial(planId, materialId)) { "Material does not exist." }
        return repository.upsertMaterial(material.copy(archived = false, updatedAtEpochMillis = clock()))
    }

    suspend fun createVideo(
        id: VideoResourceId,
        studyPlanId: StudyPlanId,
        subjectId: SubjectId?,
        topicId: TopicId?,
        title: String,
        sourceType: VideoSourceType,
        sourceUri: String,
        durationMillis: Long?,
    ): VideoResource {
        require(title.isNotBlank()) { "Video title cannot be blank." }
        require(sourceUri.isNotBlank()) { "Video source URI cannot be blank." }
        require(resourcePlanner.planForVideoSource(sourceType, sourceUri).isLaunchable()) {
            "Video source URI is invalid for the selected source type."
        }
        require(durationMillis == null || durationMillis > 0L) { "Duration must be positive when present." }
        val now = clock()
        return repository.upsertVideo(
            VideoResource(
                id = id,
                studyPlanId = studyPlanId,
                subjectId = subjectId,
                topicId = topicId,
                title = title.trim(),
                sourceType = sourceType,
                sourceUri = sourceUri.trim(),
                canonicalSourceId = null,
                thumbnailUri = null,
                durationMillis = durationMillis,
                playbackPositionMillis = 0L,
                completed = false,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
            ),
        )
    }

    suspend fun updateVideoProgress(
        planId: StudyPlanId,
        videoId: VideoResourceId,
        playbackPositionMillis: Long,
        completed: Boolean,
    ): VideoResource {
        val video = requireNotNull(repository.findVideo(planId, videoId)) { "Video does not exist." }
        require(playbackPositionMillis >= 0L) { "Playback position cannot be negative." }
        return repository.upsertVideo(
            video.copy(
                playbackPositionMillis = playbackPositionMillis,
                completed = completed,
                updatedAtEpochMillis = clock(),
            ),
        )
    }


    suspend fun deleteMaterial(
        planId: StudyPlanId,
        materialId: StudyMaterialId,
    ) = repository.deleteMaterial(planId, materialId)
    suspend fun deleteVideo(planId: StudyPlanId, videoId: VideoResourceId) = repository.deleteVideo(planId, videoId)
    suspend fun deleteAnnex(planId: StudyPlanId, annexId: AnnexId) = repository.deleteAnnex(planId, annexId)

    private fun StudyResourceLaunchPlan.isLaunchable(): Boolean =
        this !is StudyResourceLaunchPlan.InvalidSource && this !is StudyResourceLaunchPlan.MissingSource

    private fun List<String>.cleanTags(): List<String> = map(String::trim).filter(String::isNotBlank).distinct()

    private companion object {
        val EXTERNALLY_LAUNCHED_MATERIAL_TYPES = setOf(
            StudyMaterialType.PDF,
            StudyMaterialType.WEBSITE,
            StudyMaterialType.COURSE,
            StudyMaterialType.VIDEO,
            StudyMaterialType.LOCAL_FILE,
        )
    }
}
