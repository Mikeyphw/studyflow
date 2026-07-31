package com.mikeyphw.studyflow.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.mikeyphw.studyflow.core.database.entity.PlannedActivityEntity
import com.mikeyphw.studyflow.core.database.entity.ScheduleRuleEntity
import com.mikeyphw.studyflow.core.database.entity.StudyGoalEntity
import com.mikeyphw.studyflow.core.database.entity.StudySessionEntity
import com.mikeyphw.studyflow.core.database.entity.StudyStrategyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StudyPlanningDao {
    @Upsert
    suspend fun upsertStrategy(strategy: StudyStrategyEntity)

    @Upsert
    suspend fun upsertScheduleRules(rules: List<ScheduleRuleEntity>)

    @Upsert
    suspend fun upsertPlannedActivities(activities: List<PlannedActivityEntity>)

    @Upsert
    suspend fun upsertStudySessions(sessions: List<StudySessionEntity>)

    @Upsert
    suspend fun upsertStudyGoals(goals: List<StudyGoalEntity>)

    @Query(
        """
        SELECT * FROM planned_activities
        WHERE studyPlanId = :planId
        ORDER BY scheduledStartEpochMillis IS NULL, scheduledStartEpochMillis, title COLLATE NOCASE
        """,
    )
    fun observePlannedActivities(planId: String): Flow<List<PlannedActivityEntity>>

    @Query("SELECT * FROM study_sessions WHERE studyPlanId = :planId ORDER BY startedAtEpochMillis DESC")
    fun observeStudySessions(planId: String): Flow<List<StudySessionEntity>>
}
