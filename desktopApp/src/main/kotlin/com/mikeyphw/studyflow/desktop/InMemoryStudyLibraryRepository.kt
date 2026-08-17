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
) : StudyLibraryRepository {
    private val materialState = MutableStateFlow(materials)
    private val videoState = MutableStateFlow(videos)
    private val annexState = MutableStateFlow(annexes)

    override fun observeMaterials(planId: StudyPlanId): Flow<List<StudyMaterial>> = materialState.map { materials ->
        materials.filter { material -> material.studyPlanId == planId }
    }

    override fun observeVideos(planId: StudyPlanId): Flow<List<VideoResource>> = videoState.map { videos ->
        videos.filter { video -> video.studyPlanId == planId }
    }

    override fun observeAnnexes(planId: StudyPlanId): Flow<List<Annex>> = annexState.map { annexes ->
        annexes.filter { annex -> annex.studyPlanId == planId }
    }

    override suspend fun findMaterial(planId: StudyPlanId, materialId: StudyMaterialId): StudyMaterial? =
        currentMaterials(planId).firstOrNull { material -> material.id == materialId }

    override suspend fun findVideo(planId: StudyPlanId, videoId: VideoResourceId): VideoResource? =
        currentVideos(planId).firstOrNull { video -> video.id == videoId }

    override suspend fun findAnnex(planId: StudyPlanId, annexId: AnnexId): Annex? =
        currentAnnexes(planId).firstOrNull { annex -> annex.id == annexId }

    override suspend fun upsertMaterial(material: StudyMaterial): StudyMaterial {
        materialState.value = materialState.value
            .filterNot { existing -> existing.id == material.id }
            .plus(material)
        return material
    }

    override suspend fun upsertVideo(video: VideoResource): VideoResource {
        videoState.value = videoState.value
            .filterNot { existing -> existing.id == video.id }
            .plus(video)
        return video
    }

    override suspend fun upsertAnnex(annex: Annex): Annex {
        annexState.value = annexState.value
            .filterNot { existing -> existing.id == annex.id }
            .plus(annex)
        return annex
    }

    override suspend fun deleteMaterial(planId: StudyPlanId, materialId: StudyMaterialId) {
        materialState.value = materialState.value.filterNot { material ->
            material.studyPlanId == planId && material.id == materialId
        }
    }

    override suspend fun deleteVideo(planId: StudyPlanId, videoId: VideoResourceId) {
        videoState.value = videoState.value.filterNot { video -> video.studyPlanId == planId && video.id == videoId }
    }

    override suspend fun deleteAnnex(planId: StudyPlanId, annexId: AnnexId) {
        annexState.value = annexState.value.filterNot { annex -> annex.studyPlanId == planId && annex.id == annexId }
    }

    fun currentMaterials(planId: StudyPlanId): List<StudyMaterial> = materialState.value.filter { material ->
        material.studyPlanId == planId
    }

    fun currentVideos(planId: StudyPlanId): List<VideoResource> = videoState.value.filter { video ->
        video.studyPlanId == planId
    }

    fun currentAnnexes(planId: StudyPlanId): List<Annex> = annexState.value.filter { annex ->
        annex.studyPlanId == planId
    }
}
