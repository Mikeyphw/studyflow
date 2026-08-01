package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.Annex
import com.mikeyphw.studyflow.core.model.AnnexId
import com.mikeyphw.studyflow.core.model.AnnexStorageMode
import com.mikeyphw.studyflow.core.model.StudyMaterial
import com.mikeyphw.studyflow.core.model.StudyMaterialId
import com.mikeyphw.studyflow.core.model.StudyMaterialType
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.VideoResource
import com.mikeyphw.studyflow.core.model.VideoResourceId
import com.mikeyphw.studyflow.core.model.VideoSourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyResourceLaunchPlannerTest {
    private val planner = StudyResourceLaunchPlanner()
    private val planId = StudyPlanId("plan")

    @Test
    fun pdfMaterialUsesExternalDocumentIntent() {
        val plan = planner.planForMaterial(
            material(type = StudyMaterialType.PDF, sourceUri = "content://file"),
        )
        assertEquals(StudyResourceLaunchPlan.ExternalDocument("content://file", "application/pdf"), plan)
    }

    @Test
    fun webVideoUsesWebPageIntent() {
        val plan = planner.planForVideo(
            video(sourceType = VideoSourceType.YOUTUBE, sourceUri = "https://youtu.be/demo"),
        )
        assertEquals(StudyResourceLaunchPlan.WebPage("https://youtu.be/demo"), plan)
    }

    @Test
    fun appPrivateAnnexUsesFileProviderLaunchPlan() {
        val plan = planner.planForAnnex(
            Annex(
                id = AnnexId("annex"),
                studyPlanId = planId,
                subjectId = null,
                topicId = null,
                materialId = null,
                displayName = "proof.pdf",
                mimeType = "application/pdf",
                byteSize = 10,
                checksumSha256 = null,
                storageMode = AnnexStorageMode.IMPORTED_COPY,
                storageReference = "study-plans/plan/annexes/annex/proof.pdf",
                persistedPermission = false,
                createdAtEpochMillis = 1,
                updatedAtEpochMillis = 1,
            ),
        )
        assertTrue(plan is StudyResourceLaunchPlan.AppPrivateAnnex)
    }

    private fun material(
        type: StudyMaterialType,
        sourceUri: String?,
    ) = StudyMaterial(
        id = StudyMaterialId("material"),
        studyPlanId = planId,
        subjectId = null,
        topicId = null,
        type = type,
        title = "Material",
        description = "",
        sourceUri = sourceUri,
        author = null,
        totalUnits = 3,
        completedUnits = 0,
        primary = false,
        favorite = false,
        archived = false,
        tags = emptyList(),
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = 1,
    )

    private fun video(
        sourceType: VideoSourceType,
        sourceUri: String,
    ) = VideoResource(
        id = VideoResourceId("video"),
        studyPlanId = planId,
        subjectId = null,
        topicId = null,
        title = "Video",
        sourceType = sourceType,
        sourceUri = sourceUri,
        canonicalSourceId = null,
        thumbnailUri = null,
        durationMillis = null,
        playbackPositionMillis = 0,
        completed = false,
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = 1,
    )
}
