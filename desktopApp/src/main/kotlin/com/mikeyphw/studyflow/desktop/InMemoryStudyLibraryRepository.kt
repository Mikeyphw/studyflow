package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.domain.StudyLibraryRepository
import com.mikeyphw.studyflow.core.model.Annex
import com.mikeyphw.studyflow.core.model.AnnexId
import com.mikeyphw.studyflow.core.model.StudyMaterial
import com.mikeyphw.studyflow.core.model.StudyMaterialId
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.VideoResource
import com.mikeyphw.studyflow.core.model.VideoResourceId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class InMemoryStudyLibraryRepository(
    materials: List<StudyMaterial>,
    videos: List<VideoResource>,
    annexes: List<Annex>,
    private val onChanged: (List<StudyMaterial>, List<VideoResource>, List<Annex>) -> Unit = { _, _, _ -> },
    private val onImportedFilesRemoved: (List<StudyMaterialId>) -> Unit = {},
) : StudyLibraryRepository {
    private val materialState = MutableStateFlow(materials)
    private val videoState = MutableStateFlow(videos)
    private val annexState = MutableStateFlow(annexes)

    override fun observeMaterials(planId: StudyPlanId): Flow<List<StudyMaterial>> = materialState.map { values ->
        values.filter { it.studyPlanId == planId }
    }
    override fun observeVideos(planId: StudyPlanId): Flow<List<VideoResource>> = videoState.map { values ->
        values.filter { it.studyPlanId == planId }
    }
    override fun observeAnnexes(planId: StudyPlanId): Flow<List<Annex>> = annexState.map { values ->
        values.filter { it.studyPlanId == planId }
    }
    override suspend fun findMaterial(planId: StudyPlanId, materialId: StudyMaterialId): StudyMaterial? =
        currentMaterials(planId).firstOrNull { it.id == materialId }
    override suspend fun findVideo(planId: StudyPlanId, videoId: VideoResourceId): VideoResource? =
        currentVideos(planId).firstOrNull { it.id == videoId }
    override suspend fun findAnnex(planId: StudyPlanId, annexId: AnnexId): Annex? =
        currentAnnexes(planId).firstOrNull { it.id == annexId }

    override suspend fun upsertMaterial(material: StudyMaterial): StudyMaterial {
        materialState.value = materialState.value.filterNot { it.id == material.id } + material
        persist()
        return material
    }
    override suspend fun upsertVideo(video: VideoResource): VideoResource {
        videoState.value = videoState.value.filterNot { it.id == video.id } + video
        persist()
        return video
    }
    override suspend fun upsertAnnex(annex: Annex): Annex {
        annexState.value = annexState.value.filterNot { it.id == annex.id } + annex
        persist()
        return annex
    }

    override suspend fun deleteMaterial(planId: StudyPlanId, materialId: StudyMaterialId) {
        val removed = materialState.value.any { it.studyPlanId == planId && it.id == materialId }
        materialState.value = materialState.value.filterNot { it.studyPlanId == planId && it.id == materialId }
        annexState.value = annexState.value.filterNot { it.studyPlanId == planId && it.materialId == materialId }
        persist()
        if (removed) onImportedFilesRemoved(listOf(materialId))
    }
    override suspend fun deleteVideo(planId: StudyPlanId, videoId: VideoResourceId) {
        videoState.value = videoState.value.filterNot { it.studyPlanId == planId && it.id == videoId }
        persist()
    }
    override suspend fun deleteAnnex(planId: StudyPlanId, annexId: AnnexId) {
        annexState.value = annexState.value.filterNot { it.studyPlanId == planId && it.id == annexId }
        persist()
    }

    fun purgePlan(planId: StudyPlanId) {
        val removedMaterialIds = materialState.value.filter { it.studyPlanId == planId }.map(StudyMaterial::id)
        materialState.value = materialState.value.filterNot { it.studyPlanId == planId }
        videoState.value = videoState.value.filterNot { it.studyPlanId == planId }
        annexState.value = annexState.value.filterNot { it.studyPlanId == planId }
        persist()
        if (removedMaterialIds.isNotEmpty()) onImportedFilesRemoved(removedMaterialIds)
    }

    fun currentMaterials(planId: StudyPlanId): List<StudyMaterial> =
        materialState.value.filter { it.studyPlanId == planId }
    fun currentVideos(planId: StudyPlanId): List<VideoResource> = videoState.value.filter { it.studyPlanId == planId }
    fun currentAnnexes(planId: StudyPlanId): List<Annex> = annexState.value.filter { it.studyPlanId == planId }

    private fun persist() = onChanged(materialState.value, videoState.value, annexState.value)
}
