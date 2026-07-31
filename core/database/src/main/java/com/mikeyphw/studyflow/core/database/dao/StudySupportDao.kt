package com.mikeyphw.studyflow.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.mikeyphw.studyflow.core.database.entity.PlanSnapshotEntity
import com.mikeyphw.studyflow.core.database.entity.StudyNoteEntity
import com.mikeyphw.studyflow.core.database.entity.StudyPlanSettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StudySupportDao {
    @Upsert
    suspend fun upsertNotes(notes: List<StudyNoteEntity>)

    @Upsert
    suspend fun upsertSnapshots(snapshots: List<PlanSnapshotEntity>)

    @Upsert
    suspend fun upsertPlanSettings(settings: StudyPlanSettingsEntity)

    @Query("SELECT * FROM study_notes WHERE studyPlanId = :planId ORDER BY pinned DESC, updatedAtEpochMillis DESC")
    fun observeNotes(planId: String): Flow<List<StudyNoteEntity>>

    @Query("SELECT * FROM plan_snapshots WHERE studyPlanId = :planId ORDER BY createdAtEpochMillis DESC")
    fun observeSnapshots(planId: String): Flow<List<PlanSnapshotEntity>>

    @Query("SELECT * FROM study_plan_settings WHERE studyPlanId = :planId LIMIT 1")
    fun observePlanSettings(planId: String): Flow<StudyPlanSettingsEntity?>

    @Query("SELECT * FROM study_plan_settings WHERE studyPlanId = :planId LIMIT 1")
    suspend fun findPlanSettings(planId: String): StudyPlanSettingsEntity?
}
