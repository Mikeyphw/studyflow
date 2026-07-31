package com.mikeyphw.studyflow.core.database.dao

import androidx.room.Dao
import androidx.room.Query

@Dao
interface PlanOwnershipDao {
    @Query("SELECT COUNT(*) FROM subjects WHERE studyPlanId = :planId")
    suspend fun countSubjects(planId: String): Int

    @Query("SELECT COUNT(*) FROM topics WHERE studyPlanId = :planId")
    suspend fun countTopics(planId: String): Int

    @Query("SELECT COUNT(*) FROM planned_activities WHERE studyPlanId = :planId")
    suspend fun countPlannedActivities(planId: String): Int

    @Query("SELECT COUNT(*) FROM study_sessions WHERE studyPlanId = :planId")
    suspend fun countStudySessions(planId: String): Int

    @Query("SELECT COUNT(*) FROM study_materials WHERE studyPlanId = :planId")
    suspend fun countMaterials(planId: String): Int

    @Query("SELECT COUNT(*) FROM video_resources WHERE studyPlanId = :planId")
    suspend fun countVideos(planId: String): Int

    @Query("SELECT COUNT(*) FROM annexes WHERE studyPlanId = :planId")
    suspend fun countAnnexes(planId: String): Int

    @Query("SELECT COUNT(*) FROM questions WHERE studyPlanId = :planId")
    suspend fun countQuestions(planId: String): Int

    @Query("SELECT COUNT(*) FROM review_items WHERE studyPlanId = :planId")
    suspend fun countReviewItems(planId: String): Int

    @Query("SELECT COUNT(*) FROM study_notes WHERE studyPlanId = :planId")
    suspend fun countNotes(planId: String): Int
}
