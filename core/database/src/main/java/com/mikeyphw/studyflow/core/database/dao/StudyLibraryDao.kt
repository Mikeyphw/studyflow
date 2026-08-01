package com.mikeyphw.studyflow.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.mikeyphw.studyflow.core.database.entity.AnnexEntity
import com.mikeyphw.studyflow.core.database.entity.StudyMaterialEntity
import com.mikeyphw.studyflow.core.database.entity.VideoResourceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StudyLibraryDao {
    @Upsert
    suspend fun upsertMaterial(material: StudyMaterialEntity)

    @Upsert
    suspend fun upsertMaterials(materials: List<StudyMaterialEntity>)

    @Upsert
    suspend fun upsertVideo(video: VideoResourceEntity)

    @Upsert
    suspend fun upsertVideos(videos: List<VideoResourceEntity>)

    @Upsert
    suspend fun upsertAnnex(annex: AnnexEntity)

    @Upsert
    suspend fun upsertAnnexes(annexes: List<AnnexEntity>)

    @Query(
        """
        SELECT * FROM study_materials
        WHERE studyPlanId = :planId
        ORDER BY archived, favorite DESC, title COLLATE NOCASE
        """,
    )
    fun observeMaterials(planId: String): Flow<List<StudyMaterialEntity>>

    @Query("SELECT * FROM study_materials WHERE studyPlanId = :planId AND id = :materialId LIMIT 1")
    suspend fun findMaterial(planId: String, materialId: String): StudyMaterialEntity?

    @Query("DELETE FROM study_materials WHERE studyPlanId = :planId AND id = :materialId")
    suspend fun deleteMaterial(planId: String, materialId: String): Int

    @Query("SELECT * FROM video_resources WHERE studyPlanId = :planId ORDER BY completed, updatedAtEpochMillis DESC")
    fun observeVideos(planId: String): Flow<List<VideoResourceEntity>>

    @Query("SELECT * FROM video_resources WHERE studyPlanId = :planId AND id = :videoId LIMIT 1")
    suspend fun findVideo(planId: String, videoId: String): VideoResourceEntity?

    @Query("DELETE FROM video_resources WHERE studyPlanId = :planId AND id = :videoId")
    suspend fun deleteVideo(planId: String, videoId: String): Int

    @Query("SELECT * FROM annexes WHERE studyPlanId = :planId ORDER BY displayName COLLATE NOCASE")
    fun observeAnnexes(planId: String): Flow<List<AnnexEntity>>

    @Query("SELECT * FROM annexes WHERE studyPlanId = :planId AND id = :annexId LIMIT 1")
    suspend fun findAnnex(planId: String, annexId: String): AnnexEntity?

    @Query("DELETE FROM annexes WHERE studyPlanId = :planId AND id = :annexId")
    suspend fun deleteAnnex(planId: String, annexId: String): Int
}
