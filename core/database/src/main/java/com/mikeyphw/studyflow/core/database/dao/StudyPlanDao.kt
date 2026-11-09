package com.mikeyphw.studyflow.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.mikeyphw.studyflow.core.database.entity.StudyPlanEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StudyPlanDao {
    @Query(
        """
        SELECT * FROM study_plans
        ORDER BY
            CASE status
                WHEN 'ACTIVE' THEN 0
                WHEN 'DRAFT' THEN 1
                WHEN 'PAUSED' THEN 2
                WHEN 'COMPLETED' THEN 3
                WHEN 'ARCHIVED' THEN 4
                ELSE 5
            END,
            updatedAtEpochMillis DESC,
            name COLLATE NOCASE ASC
        """,
    )
    fun observeAll(): Flow<List<StudyPlanEntity>>

    @Query("SELECT * FROM study_plans WHERE id = :planId LIMIT 1")
    fun observeById(planId: String): Flow<StudyPlanEntity?>

    @Query("SELECT * FROM study_plans WHERE id = :planId LIMIT 1")
    suspend fun findById(planId: String): StudyPlanEntity?

    @Query("SELECT COUNT(*) FROM study_plans WHERE id = :planId")
    suspend fun countById(planId: String): Int

    /** Indexed, transaction-scoped identity query; returns only plans without settings. */
    @Query(
        """
        SELECT plans.id FROM study_plans AS plans
        LEFT JOIN study_plan_settings AS settings ON settings.studyPlanId = plans.id
        WHERE settings.studyPlanId IS NULL
        """,
    )
    suspend fun findPlanIdsMissingSettings(): List<String>


    @Query("SELECT COUNT(*) FROM study_plans")
    suspend fun countAll(): Int

    @Query("SELECT * FROM study_plans")
    suspend fun snapshotAll(): List<StudyPlanEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(plan: StudyPlanEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(plans: List<StudyPlanEntity>)

    @Upsert
    suspend fun upsert(plan: StudyPlanEntity)

    @Query(
        """
        UPDATE study_plans
        SET progressPercent = :progressPercent, updatedAtEpochMillis = :updatedAtEpochMillis
        WHERE id = :planId
        """,
    )
    suspend fun updateProgress(
        planId: String,
        progressPercent: Int,
        updatedAtEpochMillis: Long,
    ): Int

    @Query("DELETE FROM study_plans WHERE id = :planId")
    suspend fun deleteById(planId: String): Int

    @Query("DELETE FROM study_plans")
    suspend fun deleteAll()
}
