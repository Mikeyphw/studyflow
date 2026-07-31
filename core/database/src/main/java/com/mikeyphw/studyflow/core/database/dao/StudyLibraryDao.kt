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
    suspend fun upsertMaterials(materials: List<StudyMaterialEntity>)

    @Upsert
    suspend fun upsertVideos(videos: List<VideoResourceEntity>)

    @Upsert
    suspend fun upsertAnnexes(annexes: List<AnnexEntity>)

    @Query("SELECT * FROM study_materials WHERE studyPlanId = :planId ORDER BY title COLLATE NOCASE")
    fun observeMaterials(planId: String): Flow<List<StudyMaterialEntity>>

    @Query("SELECT * FROM video_resources WHERE studyPlanId = :planId ORDER BY updatedAtEpochMillis DESC")
    fun observeVideos(planId: String): Flow<List<VideoResourceEntity>>

    @Query("SELECT * FROM annexes WHERE studyPlanId = :planId ORDER BY displayName COLLATE NOCASE")
    fun observeAnnexes(planId: String): Flow<List<AnnexEntity>>
}
