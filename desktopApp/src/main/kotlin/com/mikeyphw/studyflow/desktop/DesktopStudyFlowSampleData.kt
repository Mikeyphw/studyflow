package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.model.Annex
import com.mikeyphw.studyflow.core.model.AnnexId
import com.mikeyphw.studyflow.core.model.AnnexStorageMode
import com.mikeyphw.studyflow.core.model.StudyMaterial
import com.mikeyphw.studyflow.core.model.StudyMaterialId
import com.mikeyphw.studyflow.core.model.StudyMaterialType
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyPlanStatus
import com.mikeyphw.studyflow.core.model.StudyType
import com.mikeyphw.studyflow.core.model.SubjectId
import com.mikeyphw.studyflow.core.model.VideoResource
import com.mikeyphw.studyflow.core.model.VideoResourceId
import com.mikeyphw.studyflow.core.model.VideoSourceType

data class DesktopStudyFlowSampleData(
    val plans: List<StudyPlan>,
    val materials: List<StudyMaterial>,
    val videos: List<VideoResource>,
    val annexes: List<Annex>,
) {
    companion object {
        fun create(): DesktopStudyFlowSampleData {
            val now = 1_786_925_000_000L
            val planId = StudyPlanId("desktop-sample-plan")
            val subjectId = SubjectId("desktop-sample-subject")
            return DesktopStudyFlowSampleData(
                plans = listOf(samplePlan(planId, now)),
                materials = sampleMaterials(planId, subjectId, now),
                videos = sampleVideos(planId, subjectId, now),
                annexes = sampleAnnexes(planId, subjectId, now),
            )
        }

        private fun samplePlan(planId: StudyPlanId, now: Long): StudyPlan = StudyPlan(
            id = planId,
            name = "Desktop Study Plan",
            description = "Sample JVM state rendered through the desktop bootstrap.",
            type = StudyType.FREE_STUDY,
            status = StudyPlanStatus.ACTIVE,
            examDateIso = null,
            weeklyGoalHours = 12,
            progressPercent = 42,
            studiedMinutesThisWeek = 285,
            questionsAnswered = 128,
            averageAccuracyPercent = 76,
            createdAtEpochMillis = now - ONE_WEEK_MILLIS,
            updatedAtEpochMillis = now,
        )

        private fun sampleMaterials(
            planId: StudyPlanId,
            subjectId: SubjectId,
            now: Long,
        ): List<StudyMaterial> = listOf(
            StudyMaterial(
                id = StudyMaterialId("desktop-material-kotlin"),
                studyPlanId = planId,
                subjectId = subjectId,
                topicId = null,
                type = StudyMaterialType.NOTE,
                title = "Kotlin foundations notes",
                description = "Local notes used to verify the library list and detail shell.",
                sourceUri = null,
                author = "StudyFlow sample",
                totalUnits = 10,
                completedUnits = 6,
                primary = true,
                favorite = true,
                archived = false,
                tags = listOf("kotlin", "desktop"),
                createdAtEpochMillis = now - ONE_WEEK_MILLIS,
                updatedAtEpochMillis = now,
            ),
            StudyMaterial(
                id = StudyMaterialId("desktop-material-compose"),
                studyPlanId = planId,
                subjectId = subjectId,
                topicId = null,
                type = StudyMaterialType.WEBSITE,
                title = "Compose Multiplatform docs",
                description = "External reference reserved for the future desktop resource launcher.",
                sourceUri = "https://kotlinlang.org/docs/compose-multiplatform.html",
                author = "JetBrains",
                totalUnits = 8,
                completedUnits = 3,
                primary = false,
                favorite = false,
                archived = false,
                tags = listOf("compose", "ui"),
                createdAtEpochMillis = now - ONE_WEEK_MILLIS / 2,
                updatedAtEpochMillis = now,
            ),
            StudyMaterial(
                id = StudyMaterialId("desktop-material-archive"),
                studyPlanId = planId,
                subjectId = subjectId,
                topicId = null,
                type = StudyMaterialType.PDF,
                title = "Archived scratch PDF",
                description = "Archived item stays out of the active desktop library list.",
                sourceUri = "content://studyflow/archived-scratch.pdf",
                author = null,
                totalUnits = 4,
                completedUnits = 4,
                primary = false,
                favorite = false,
                archived = true,
                tags = listOf("archive"),
                createdAtEpochMillis = now - ONE_WEEK_MILLIS,
                updatedAtEpochMillis = now,
            ),
        )

        private fun sampleVideos(
            planId: StudyPlanId,
            subjectId: SubjectId,
            now: Long,
        ): List<VideoResource> = listOf(
            VideoResource(
                id = VideoResourceId("desktop-video-compose"),
                studyPlanId = planId,
                subjectId = subjectId,
                topicId = null,
                title = "Desktop layout walkthrough",
                sourceType = VideoSourceType.WEB_PAGE,
                sourceUri = "https://kotlinlang.org/docs/multiplatform/compose-desktop.html",
                canonicalSourceId = null,
                thumbnailUri = null,
                durationMillis = 1_800_000L,
                playbackPositionMillis = 420_000L,
                completed = false,
                createdAtEpochMillis = now - ONE_WEEK_MILLIS / 3,
                updatedAtEpochMillis = now,
            ),
        )

        private fun sampleAnnexes(
            planId: StudyPlanId,
            subjectId: SubjectId,
            now: Long,
        ): List<Annex> = listOf(
            Annex(
                id = AnnexId("desktop-annex-checklist"),
                studyPlanId = planId,
                subjectId = subjectId,
                topicId = null,
                materialId = StudyMaterialId("desktop-material-kotlin"),
                displayName = "studyflow-desktop-checklist.txt",
                mimeType = "text/plain",
                byteSize = 512L,
                checksumSha256 = null,
                storageMode = AnnexStorageMode.IMPORTED_COPY,
                storageReference = "desktop/sample/studyflow-desktop-checklist.txt",
                persistedPermission = false,
                createdAtEpochMillis = now - ONE_WEEK_MILLIS / 4,
                updatedAtEpochMillis = now,
            ),
        )

        private const val ONE_WEEK_MILLIS: Long = 7L * 24L * 60L * 60L * 1000L
    }
}
