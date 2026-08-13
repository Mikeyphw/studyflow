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
    fun webClassificationWinsBeforePdfTypeAndIgnoresSchemeCase() {
        val plan = planner.planForMaterial(
            material(type = StudyMaterialType.PDF, sourceUri = "HTTPS://example.com/proof.pdf"),
        )
        assertEquals(StudyResourceLaunchPlan.WebPage("HTTPS://example.com/proof.pdf"), plan)
    }

    @Test
    fun externalDocumentRejectsUnknownScheme() {
        val plan = planner.planForMaterial(
            material(type = StudyMaterialType.PDF, sourceUri = "ftp://example.com/proof.pdf"),
        )
        assertEquals(
            StudyResourceLaunchPlan.InvalidSource(StudyResourceLaunchFailureReason.UNSUPPORTED_SCHEME),
            plan,
        )
    }

    @Test
    fun websiteMaterialRejectsContentUri() {
        val plan = planner.planForMaterial(
            material(type = StudyMaterialType.WEBSITE, sourceUri = "content://file"),
        )
        assertEquals(
            StudyResourceLaunchPlan.InvalidSource(StudyResourceLaunchFailureReason.EXPECTED_WEB_URI),
            plan,
        )
    }

    @Test
    fun localFileMaterialRejectsWebUri() {
        val plan = planner.planForMaterial(
            material(type = StudyMaterialType.LOCAL_FILE, sourceUri = "https://example.com/file.pdf"),
        )
        assertEquals(
            StudyResourceLaunchPlan.InvalidSource(StudyResourceLaunchFailureReason.EXPECTED_CONTENT_URI),
            plan,
        )
    }

    @Test
    fun linkedAnnexRequiresContentUriAndCarriesIntegrityMetadata() {
        val annex = Annex(
            id = AnnexId("linked"),
            studyPlanId = planId,
            subjectId = null,
            topicId = null,
            materialId = null,
            displayName = "proof.pdf",
            mimeType = "application/pdf",
            byteSize = 42,
            checksumSha256 = "a".repeat(64),
            storageMode = AnnexStorageMode.LINKED_DOCUMENT,
            storageReference = "content://documents/proof",
            persistedPermission = true,
            createdAtEpochMillis = 1,
            updatedAtEpochMillis = 1,
        )
        assertEquals(
            StudyResourceLaunchPlan.LinkedDocument(
                uri = "content://documents/proof",
                mimeType = "application/pdf",
                byteSize = 42,
                checksumSha256 = "a".repeat(64),
            ),
            planner.planForAnnex(annex),
        )
        assertEquals(
            StudyResourceLaunchPlan.InvalidSource(StudyResourceLaunchFailureReason.EXPECTED_CONTENT_URI),
            planner.planForAnnex(annex.copy(storageReference = "https://example.com/proof.pdf")),
        )
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
                storageReference = "shareable-annexes/plan/annex/proof.pdf",
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
