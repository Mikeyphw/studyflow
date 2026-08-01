package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.Annex
import com.mikeyphw.studyflow.core.model.AnnexStorageMode
import com.mikeyphw.studyflow.core.model.StudyMaterial
import com.mikeyphw.studyflow.core.model.StudyMaterialId
import com.mikeyphw.studyflow.core.model.StudyMaterialType
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.VideoResource
import com.mikeyphw.studyflow.core.model.VideoResourceId
import com.mikeyphw.studyflow.core.model.VideoSourceType

class StudyResourceLaunchPlanner {
    fun planForMaterial(material: StudyMaterial): StudyResourceLaunchPlan = when {
        material.sourceUri.isNullOrBlank() -> StudyResourceLaunchPlan.MissingSource
        material.type == StudyMaterialType.LOCAL_FILE -> StudyResourceLaunchPlan.ExternalDocument(
            uri = material.sourceUri,
            mimeType = "*/*",
        )
        material.type == StudyMaterialType.PDF -> StudyResourceLaunchPlan.ExternalDocument(
            uri = material.sourceUri,
            mimeType = "application/pdf",
        )
        material.type == StudyMaterialType.VIDEO -> StudyResourceLaunchPlan.ExternalVideo(material.sourceUri)
        material.type == StudyMaterialType.WEBSITE || material.sourceUri.isWebUri() ->
            StudyResourceLaunchPlan.WebPage(material.sourceUri)
        else -> StudyResourceLaunchPlan.ExternalDocument(uri = material.sourceUri, mimeType = "*/*")
    }

    fun planForVideo(video: VideoResource): StudyResourceLaunchPlan = when (video.sourceType) {
        VideoSourceType.YOUTUBE -> StudyResourceLaunchPlan.WebPage(video.sourceUri)
        VideoSourceType.DIRECT_MEDIA -> StudyResourceLaunchPlan.ExternalVideo(video.sourceUri)
        VideoSourceType.LOCAL_FILE -> StudyResourceLaunchPlan.ExternalVideo(video.sourceUri)
        VideoSourceType.WEB_PAGE -> StudyResourceLaunchPlan.WebPage(video.sourceUri)
    }

    fun planForAnnex(annex: Annex): StudyResourceLaunchPlan = when {
        annex.storageReference.isBlank() -> StudyResourceLaunchPlan.MissingSource
        annex.storageMode == AnnexStorageMode.IMPORTED_COPY -> StudyResourceLaunchPlan.AppPrivateAnnex(
            storageReference = annex.storageReference,
            mimeType = annex.mimeType,
        )
        else -> StudyResourceLaunchPlan.ExternalDocument(
            uri = annex.storageReference,
            mimeType = annex.mimeType,
        )
    }

    private fun String.isWebUri(): Boolean = startsWith("https://") || startsWith("http://")
}

sealed interface StudyResourceLaunchPlan {
    data class WebPage(val uri: String) : StudyResourceLaunchPlan
    data class ExternalVideo(val uri: String) : StudyResourceLaunchPlan
    data class ExternalDocument(val uri: String, val mimeType: String) : StudyResourceLaunchPlan
    data class AppPrivateAnnex(val storageReference: String, val mimeType: String) : StudyResourceLaunchPlan
    data object MissingSource : StudyResourceLaunchPlan
}

class ManageStudyResourceViewer(
    private val repository: StudyLibraryRepository,
    private val planner: StudyResourceLaunchPlanner = StudyResourceLaunchPlanner(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun planForMaterial(planId: StudyPlanId, materialId: StudyMaterialId): StudyResourceLaunchPlan {
        val material = requireNotNull(repository.findMaterial(planId, materialId)) { "Material does not exist." }
        return planner.planForMaterial(material)
    }

    suspend fun planForVideo(planId: StudyPlanId, videoId: VideoResourceId): StudyResourceLaunchPlan {
        val video = requireNotNull(repository.findVideo(planId, videoId)) { "Video does not exist." }
        return planner.planForVideo(video)
    }

    fun planForAnnex(annex: Annex): StudyResourceLaunchPlan = planner.planForAnnex(annex)

    suspend fun markMaterialOpened(planId: StudyPlanId, materialId: StudyMaterialId): StudyMaterial {
        val material = requireNotNull(repository.findMaterial(planId, materialId)) { "Material does not exist." }
        val nextCompleted = when (val total = material.totalUnits) {
            null -> material.completedUnits
            else -> material.completedUnits.coerceAtLeast(1).coerceAtMost(total)
        }
        return repository.upsertMaterial(
            material.copy(
                completedUnits = nextCompleted,
                updatedAtEpochMillis = clock(),
            ),
        )
    }

    suspend fun markVideoOpened(planId: StudyPlanId, videoId: VideoResourceId): VideoResource {
        val video = requireNotNull(repository.findVideo(planId, videoId)) { "Video does not exist." }
        return repository.upsertVideo(video.copy(updatedAtEpochMillis = clock()))
    }
}
