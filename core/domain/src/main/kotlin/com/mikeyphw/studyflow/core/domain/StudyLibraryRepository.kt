package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.Annex
import com.mikeyphw.studyflow.core.model.AnnexId
import com.mikeyphw.studyflow.core.model.StudyMaterial
import com.mikeyphw.studyflow.core.model.StudyMaterialId
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.VideoResource
import com.mikeyphw.studyflow.core.model.VideoResourceId
import kotlinx.coroutines.flow.Flow

interface StudyLibraryRepository {
    fun observeMaterials(planId: StudyPlanId): Flow<List<StudyMaterial>>
    fun observeVideos(planId: StudyPlanId): Flow<List<VideoResource>>
    fun observeAnnexes(planId: StudyPlanId): Flow<List<Annex>>
    suspend fun findMaterial(planId: StudyPlanId, materialId: StudyMaterialId): StudyMaterial?
    suspend fun findVideo(planId: StudyPlanId, videoId: VideoResourceId): VideoResource?
    suspend fun findAnnex(planId: StudyPlanId, annexId: AnnexId): Annex?
    suspend fun upsertMaterial(material: StudyMaterial): StudyMaterial
    suspend fun upsertVideo(video: VideoResource): VideoResource
    suspend fun upsertAnnex(annex: Annex): Annex
    suspend fun deleteMaterial(planId: StudyPlanId, materialId: StudyMaterialId)
    suspend fun deleteVideo(planId: StudyPlanId, videoId: VideoResourceId)
    suspend fun deleteAnnex(planId: StudyPlanId, annexId: AnnexId)
}
