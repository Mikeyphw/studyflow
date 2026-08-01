package com.mikeyphw.studyflow.core.data

import androidx.room.withTransaction
import com.mikeyphw.studyflow.core.database.StudyFlowDatabase
import com.mikeyphw.studyflow.core.database.mapper.asEntity
import com.mikeyphw.studyflow.core.database.mapper.asExternalModel
import com.mikeyphw.studyflow.core.domain.StudyLibraryRepository
import com.mikeyphw.studyflow.core.model.Annex
import com.mikeyphw.studyflow.core.model.AnnexId
import com.mikeyphw.studyflow.core.model.StudyMaterial
import com.mikeyphw.studyflow.core.model.StudyMaterialId
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.VideoResource
import com.mikeyphw.studyflow.core.model.VideoResourceId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class RoomStudyLibraryRepository(
    private val database: StudyFlowDatabase,
) : StudyLibraryRepository {
    private val libraryDao = database.studyLibraryDao()
    private val planDao = database.studyPlanDao()

    override fun observeMaterials(planId: StudyPlanId): Flow<List<StudyMaterial>> =
        libraryDao.observeMaterials(planId.value).map { materials -> materials.map { it.asExternalModel() } }

    override fun observeVideos(planId: StudyPlanId): Flow<List<VideoResource>> =
        libraryDao.observeVideos(planId.value).map { videos -> videos.map { it.asExternalModel() } }

    override fun observeAnnexes(planId: StudyPlanId): Flow<List<Annex>> =
        libraryDao.observeAnnexes(planId.value).map { annexes -> annexes.map { it.asExternalModel() } }

    override suspend fun findMaterial(planId: StudyPlanId, materialId: StudyMaterialId): StudyMaterial? =
        libraryDao.findMaterial(planId.value, materialId.value)?.asExternalModel()

    override suspend fun findVideo(planId: StudyPlanId, videoId: VideoResourceId): VideoResource? =
        libraryDao.findVideo(planId.value, videoId.value)?.asExternalModel()

    override suspend fun findAnnex(planId: StudyPlanId, annexId: AnnexId): Annex? =
        libraryDao.findAnnex(planId.value, annexId.value)?.asExternalModel()

    override suspend fun upsertMaterial(material: StudyMaterial): StudyMaterial = database.withTransaction {
        requirePlan(material.studyPlanId)
        material.subjectId?.let { subjectId -> requireSubject(material.studyPlanId, subjectId.value) }
        material.topicId?.let { topicId -> requireTopic(material.studyPlanId, topicId.value) }
        libraryDao.upsertMaterial(material.asEntity())
        requireNotNull(libraryDao.findMaterial(material.studyPlanId.value, material.id.value)).asExternalModel()
    }

    override suspend fun upsertVideo(video: VideoResource): VideoResource = database.withTransaction {
        requirePlan(video.studyPlanId)
        video.subjectId?.let { subjectId -> requireSubject(video.studyPlanId, subjectId.value) }
        video.topicId?.let { topicId -> requireTopic(video.studyPlanId, topicId.value) }
        libraryDao.upsertVideo(video.asEntity())
        requireNotNull(libraryDao.findVideo(video.studyPlanId.value, video.id.value)).asExternalModel()
    }

    override suspend fun upsertAnnex(annex: Annex): Annex = database.withTransaction {
        requirePlan(annex.studyPlanId)
        annex.subjectId?.let { subjectId -> requireSubject(annex.studyPlanId, subjectId.value) }
        annex.topicId?.let { topicId -> requireTopic(annex.studyPlanId, topicId.value) }
        annex.materialId?.let { materialId -> requireMaterial(annex.studyPlanId, materialId.value) }
        libraryDao.upsertAnnex(annex.asEntity())
        requireNotNull(libraryDao.findAnnex(annex.studyPlanId.value, annex.id.value)).asExternalModel()
    }

    override suspend fun deleteMaterial(planId: StudyPlanId, materialId: StudyMaterialId) {
        database.withTransaction {
            require(libraryDao.deleteMaterial(planId.value, materialId.value) == 1) {
                "Material does not belong to this Study Plan."
            }
        }
    }

    override suspend fun deleteVideo(planId: StudyPlanId, videoId: VideoResourceId) {
        database.withTransaction {
            require(libraryDao.deleteVideo(planId.value, videoId.value) == 1) {
                "Video does not belong to this Study Plan."
            }
        }
    }

    override suspend fun deleteAnnex(planId: StudyPlanId, annexId: AnnexId) {
        database.withTransaction {
            require(libraryDao.deleteAnnex(planId.value, annexId.value) == 1) {
                "Annex does not belong to this Study Plan."
            }
        }
    }

    private suspend fun requirePlan(planId: StudyPlanId) {
        requireNotNull(planDao.findById(planId.value)) { "Study Plan does not exist." }
    }

    private suspend fun requireSubject(planId: StudyPlanId, subjectId: String) {
        requireNotNull(database.studyStructureDao().findSubject(planId.value, subjectId)) {
            "The selected subject does not belong to this Study Plan."
        }
    }

    private suspend fun requireTopic(planId: StudyPlanId, topicId: String) {
        requireNotNull(database.studyStructureDao().findTopic(planId.value, topicId)) {
            "The selected topic does not belong to this Study Plan."
        }
    }

    private suspend fun requireMaterial(planId: StudyPlanId, materialId: String) {
        requireNotNull(libraryDao.findMaterial(planId.value, materialId)) {
            "The selected material does not belong to this Study Plan."
        }
    }
}
