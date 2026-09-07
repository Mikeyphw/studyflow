package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.Annex
import com.mikeyphw.studyflow.core.model.AnnexId
import com.mikeyphw.studyflow.core.model.StudyMaterial
import com.mikeyphw.studyflow.core.model.StudyMaterialId
import com.mikeyphw.studyflow.core.model.StudyMaterialType
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.VideoResource
import com.mikeyphw.studyflow.core.model.VideoResourceId
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyReferenceFreshnessTest {
    @Test
    fun `tracked reference becomes possibly stale only after the documented product window`() {
        val material = material(tags = StudyReferenceProvenanceCodec.encode(
            emptyList(), StudyReferenceOrigin.EDITAL_2022, LocalDate.parse("2026-06-01"), null,
        ))
        val current = StudyReferenceFreshnessPolicy.evaluate(material, LocalDate.parse("2026-08-30"))
        val stale = StudyReferenceFreshnessPolicy.evaluate(material, LocalDate.parse("2026-09-01"))
        assertEquals(StudyReferenceFreshnessStatus.CURRENT, current.status)
        assertEquals(90L, current.ageDays)
        assertEquals(StudyReferenceFreshnessStatus.POSSIBLY_STALE, stale.status)
        assertEquals(92L, stale.ageDays)
    }

    @Test
    fun `replacement preserves history by creating successor and archiving old material`() = runBlocking {
        val repo = FakeLibraryRepository(material())
        val manager = ManageStudyLibrary(repo, clock = { 500L })
        manager.markReferenceOrigin(PLAN, MATERIAL, StudyReferenceOrigin.EDITAL_2022, "2026-09-01")
        val replacement = manager.replaceReference(
            planId = PLAN,
            materialId = MATERIAL,
            replacementId = StudyMaterialId("replacement"),
            sourceUri = "https://example.com/current.pdf",
            verifiedOnIso = "2026-09-06",
        )
        val old = requireNotNull(repo.findMaterial(PLAN, MATERIAL))
        assertTrue(old.archived)
        assertEquals("https://example.com/current.pdf", replacement.sourceUri)
        val provenance = StudyReferenceProvenanceCodec.decode(replacement)
        assertEquals(MATERIAL, provenance.supersedesMaterialId)
        assertEquals(LocalDate.parse("2026-09-06"), provenance.verifiedOn)
    }

    private fun material(tags: List<String> = emptyList()) = StudyMaterial(
        id = MATERIAL,
        studyPlanId = PLAN,
        subjectId = null,
        topicId = null,
        type = StudyMaterialType.PDF,
        title = "Reference",
        description = "",
        sourceUri = "https://example.com/2022.pdf",
        author = null,
        totalUnits = 10,
        completedUnits = 5,
        primary = false,
        favorite = false,
        archived = false,
        tags = tags,
        createdAtEpochMillis = 1L,
        updatedAtEpochMillis = 1L,
    )

    private class FakeLibraryRepository(initial: StudyMaterial) : StudyLibraryRepository {
        private val materials = MutableStateFlow(listOf(initial))
        override fun observeMaterials(planId: StudyPlanId): Flow<List<StudyMaterial>> = materials
        override fun observeVideos(planId: StudyPlanId) = MutableStateFlow(emptyList<VideoResource>())
        override fun observeAnnexes(planId: StudyPlanId) = MutableStateFlow(emptyList<Annex>())
        override suspend fun findMaterial(planId: StudyPlanId, materialId: StudyMaterialId) =
            materials.value.firstOrNull { it.id == materialId && it.studyPlanId == planId }
        override suspend fun findVideo(planId: StudyPlanId, videoId: VideoResourceId): VideoResource? = null
        override suspend fun findAnnex(planId: StudyPlanId, annexId: AnnexId): Annex? = null
        override suspend fun upsertMaterial(material: StudyMaterial): StudyMaterial {
            materials.value = materials.value.filterNot { it.id == material.id } + material
            return material
        }
        override suspend fun upsertVideo(video: VideoResource): VideoResource = video
        override suspend fun upsertAnnex(annex: Annex): Annex = annex
        override suspend fun deleteMaterial(planId: StudyPlanId, materialId: StudyMaterialId) = Unit
        override suspend fun deleteVideo(planId: StudyPlanId, videoId: VideoResourceId) = Unit
        override suspend fun deleteAnnex(planId: StudyPlanId, annexId: AnnexId) = Unit
    }

    private companion object {
        val PLAN = StudyPlanId("plan")
        val MATERIAL = StudyMaterialId("material")
    }
}
