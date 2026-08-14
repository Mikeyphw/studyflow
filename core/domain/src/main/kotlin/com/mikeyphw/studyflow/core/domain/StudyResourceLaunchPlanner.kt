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
import java.net.URI

class StudyResourceLaunchPlanner {
    fun planForMaterial(material: StudyMaterial): StudyResourceLaunchPlan =
        planForMaterialSource(material.type, material.sourceUri)

    fun planForMaterialSource(
        type: StudyMaterialType,
        sourceUri: String?,
    ): StudyResourceLaunchPlan {
        val source = classify(sourceUri) ?: return StudyResourceLaunchPlan.MissingSource
        if (source.kind == SourceKind.WEB) {
            return when (type) {
                StudyMaterialType.LOCAL_FILE ->
                    StudyResourceLaunchPlan.InvalidSource(StudyResourceLaunchFailureReason.EXPECTED_CONTENT_URI)
                else -> StudyResourceLaunchPlan.WebPage(source.value)
            }
        }
        if (type == StudyMaterialType.WEBSITE) {
            return StudyResourceLaunchPlan.InvalidSource(StudyResourceLaunchFailureReason.EXPECTED_WEB_URI)
        }
        if (source.kind != SourceKind.CONTENT) {
            return StudyResourceLaunchPlan.InvalidSource(StudyResourceLaunchFailureReason.UNSUPPORTED_SCHEME)
        }
        return when (type) {
            StudyMaterialType.VIDEO -> StudyResourceLaunchPlan.ExternalVideo(source.value)
            StudyMaterialType.PDF -> StudyResourceLaunchPlan.ExternalDocument(source.value, "application/pdf")
            else -> StudyResourceLaunchPlan.ExternalDocument(source.value, "*/*")
        }
    }

    fun planForVideo(video: VideoResource): StudyResourceLaunchPlan =
        planForVideoSource(video.sourceType, video.sourceUri)

    fun planForVideoSource(
        sourceType: VideoSourceType,
        sourceUri: String?,
    ): StudyResourceLaunchPlan {
        val source = classify(sourceUri) ?: return StudyResourceLaunchPlan.MissingSource
        if (source.kind == SourceKind.WEB) {
            return when (sourceType) {
                VideoSourceType.LOCAL_FILE ->
                    StudyResourceLaunchPlan.InvalidSource(StudyResourceLaunchFailureReason.EXPECTED_CONTENT_URI)
                else -> StudyResourceLaunchPlan.WebPage(source.value)
            }
        }
        if (source.kind != SourceKind.CONTENT) {
            return StudyResourceLaunchPlan.InvalidSource(StudyResourceLaunchFailureReason.UNSUPPORTED_SCHEME)
        }
        return when (sourceType) {
            VideoSourceType.DIRECT_MEDIA, VideoSourceType.LOCAL_FILE -> StudyResourceLaunchPlan
                .ExternalVideo(source.value)
            VideoSourceType.YOUTUBE, VideoSourceType.WEB_PAGE ->
                StudyResourceLaunchPlan.InvalidSource(StudyResourceLaunchFailureReason.EXPECTED_WEB_URI)
        }
    }

    fun planForAnnex(annex: Annex): StudyResourceLaunchPlan = when {
        annex.storageReference.isBlank() -> StudyResourceLaunchPlan.MissingSource
        annex.storageMode == AnnexStorageMode.IMPORTED_COPY -> StudyResourceLaunchPlan.AppPrivateAnnex(
            storageReference = annex.storageReference,
            mimeType = annex.mimeType,
            byteSize = annex.byteSize,
            checksumSha256 = annex.checksumSha256,
        )
        else -> {
            val source = classify(annex.storageReference)
            if (source?.kind == SourceKind.CONTENT) {
                StudyResourceLaunchPlan.LinkedDocument(
                    uri = source.value,
                    mimeType = annex.mimeType,
                    byteSize = annex.byteSize,
                    checksumSha256 = annex.checksumSha256,
                )
            } else {
                StudyResourceLaunchPlan.InvalidSource(StudyResourceLaunchFailureReason.EXPECTED_CONTENT_URI)
            }
        }
    }

    private fun classify(raw: String?): ClassifiedSource? {
        val value = raw?.trim()?.takeIf(String::isNotBlank) ?: return null
        val uri = runCatching { URI(value) }.getOrNull()
            ?: return ClassifiedSource(value, SourceKind.INVALID)
        return when (uri.scheme?.lowercase()) {
            "http", "https" -> ClassifiedSource(value, SourceKind.WEB)
            "content" -> ClassifiedSource(value, SourceKind.CONTENT)
            else -> ClassifiedSource(value, SourceKind.INVALID)
        }
    }

    private data class ClassifiedSource(val value: String, val kind: SourceKind)
    private enum class SourceKind { WEB, CONTENT, INVALID }
}

enum class StudyResourceLaunchFailureReason {
    UNSUPPORTED_SCHEME,
    EXPECTED_WEB_URI,
    EXPECTED_CONTENT_URI,
}

sealed interface StudyResourceLaunchPlan {
    data class WebPage(val uri: String) : StudyResourceLaunchPlan
    data class ExternalVideo(val uri: String) : StudyResourceLaunchPlan
    data class ExternalDocument(val uri: String, val mimeType: String) : StudyResourceLaunchPlan
    data class LinkedDocument(
        val uri: String,
        val mimeType: String,
        val byteSize: Long,
        val checksumSha256: String?,
    ) : StudyResourceLaunchPlan
    data class AppPrivateAnnex(
        val storageReference: String,
        val mimeType: String,
        val byteSize: Long,
        val checksumSha256: String?,
    ) : StudyResourceLaunchPlan
    data class InvalidSource(val reason: StudyResourceLaunchFailureReason) : StudyResourceLaunchPlan
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
        return repository.upsertMaterial(material.copy(updatedAtEpochMillis = clock()))
    }

    suspend fun markVideoOpened(planId: StudyPlanId, videoId: VideoResourceId): VideoResource {
        val video = requireNotNull(repository.findVideo(planId, videoId)) { "Video does not exist." }
        return repository.upsertVideo(video.copy(updatedAtEpochMillis = clock()))
    }
}
