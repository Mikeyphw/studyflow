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
    suspend fun upsertScheduleRule(rule: ScheduleRuleEntity)

    @Upsert
    suspend fun upsertScheduleRules(rules: List<ScheduleRuleEntity>)

    @Upsert
    suspend fun upsertPlannedActivity(activity: PlannedActivityEntity)

    @Upsert
    suspend fun upsertPlannedActivities(activities: List<PlannedActivityEntity>)

    @Upsert
    suspend fun upsertStudySession(session: StudySessionEntity)

    @Upsert
    suspend fun upsertStudySessions(sessions: List<StudySessionEntity>)

    @Upsert
    suspend fun upsertStudyGoal(goal: StudyGoalEntity)

    @Upsert
    suspend fun upsertStudyGoals(goals: List<StudyGoalEntity>)

    @Query("SELECT * FROM study_strategies WHERE studyPlanId = :planId LIMIT 1")
    fun observeStrategy(planId: String): Flow<StudyStrategyEntity?>

    @Query("SELECT * FROM schedule_rules WHERE studyPlanId = :planId ORDER BY position, dayOfWeek, startMinuteOfDay")
    fun observeScheduleRules(planId: String): Flow<List<ScheduleRuleEntity>>

    @Query("SELECT * FROM schedule_rules WHERE studyPlanId = :planId ORDER BY position, dayOfWeek, startMinuteOfDay")
    suspend fun findScheduleRules(planId: String): List<ScheduleRuleEntity>

    @Query("SELECT * FROM schedule_rules WHERE studyPlanId = :planId AND id = :ruleId LIMIT 1")
    suspend fun findScheduleRule(planId: String, ruleId: String): ScheduleRuleEntity?

    @Query("DELETE FROM schedule_rules WHERE studyPlanId = :planId AND id = :ruleId")
    suspend fun deleteScheduleRule(planId: String, ruleId: String): Int

    @Query("SELECT * FROM planned_activities WHERE studyPlanId = :planId AND id = :activityId LIMIT 1")
    suspend fun findPlannedActivity(planId: String, activityId: String): PlannedActivityEntity?

    @Query(
        """
        SELECT * FROM planned_activities
        WHERE studyPlanId = :planId
        ORDER BY scheduledStartEpochMillis IS NULL, scheduledStartEpochMillis, title COLLATE NOCASE
        """,
    )
    fun observePlannedActivities(planId: String): Flow<List<PlannedActivityEntity>>

    @Query(
        """
        SELECT * FROM planned_activities
        WHERE studyPlanId = :planId
        ORDER BY scheduledStartEpochMillis IS NULL, scheduledStartEpochMillis, title COLLATE NOCASE
        """,
    )
    suspend fun findPlannedActivities(planId: String): List<PlannedActivityEntity>

    @Query("DELETE FROM planned_activities WHERE studyPlanId = :planId AND id = :activityId")
    suspend fun deletePlannedActivity(planId: String, activityId: String): Int

    @Query("DELETE FROM planned_activities WHERE studyPlanId = :planId AND locked = 0")
    suspend fun deleteUnlockedPlannedActivities(planId: String): Int

    @Query("SELECT * FROM study_sessions WHERE studyPlanId = :planId ORDER BY startedAtEpochMillis DESC")
    fun observeStudySessions(planId: String): Flow<List<StudySessionEntity>>

    @Query("SELECT * FROM study_goals WHERE studyPlanId = :planId ORDER BY archived, completedAtEpochMillis, startsOnIso DESC")
    fun observeStudyGoals(planId: String): Flow<List<StudyGoalEntity>>

    @Query("SELECT * FROM study_goals WHERE studyPlanId = :planId AND id = :goalId LIMIT 1")
    suspend fun findStudyGoal(planId: String, goalId: String): StudyGoalEntity?

    @Query("DELETE FROM study_goals WHERE studyPlanId = :planId AND id = :goalId")
    suspend fun deleteStudyGoal(planId: String, goalId: String): Int

    @Query("SELECT * FROM study_sessions WHERE studyPlanId = :planId AND id = :sessionId LIMIT 1")
    suspend fun findStudySession(planId: String, sessionId: String): StudySessionEntity?

    @Query(
        """
        SELECT * FROM study_sessions
        WHERE studyPlanId = :planId AND status IN ('RUNNING', 'PAUSED')
        ORDER BY startedAtEpochMillis DESC
        LIMIT 1
        """,
    )
    suspend fun findActiveStudySession(planId: String): StudySessionEntity?
}
