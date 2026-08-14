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

    @Query("SELECT * FROM study_strategies WHERE studyPlanId = :planId LIMIT 1")
    suspend fun findStrategy(planId: String): StudyStrategyEntity?

    @Query("SELECT * FROM schedule_rules WHERE studyPlanId = :planId ORDER BY position, dayOfWeek, startMinuteOfDay")
    fun observeScheduleRules(planId: String): Flow<List<ScheduleRuleEntity>>

    @Query("SELECT * FROM schedule_rules WHERE studyPlanId = :planId ORDER BY position, dayOfWeek, startMinuteOfDay")
    suspend fun findScheduleRules(planId: String): List<ScheduleRuleEntity>

    @Query("SELECT * FROM schedule_rules WHERE studyPlanId = :planId AND id = :ruleId LIMIT 1")
    suspend fun findScheduleRule(planId: String, ruleId: String): ScheduleRuleEntity?

    @Query("DELETE FROM schedule_rules WHERE studyPlanId = :planId AND id = :ruleId")
    suspend fun deleteScheduleRule(planId: String, ruleId: String): Int

    @Query(
        """
        DELETE FROM planned_activities
        WHERE studyPlanId = :planId
          AND id LIKE :generatedIdPattern
          AND status IN ('PLANNED', 'MISSED', 'SKIPPED', 'CANCELLED')
        """,
    )
    suspend fun deleteGeneratedActivitiesForRule(planId: String, generatedIdPattern: String): Int

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

    @Query(
        """
        DELETE FROM planned_activities
        WHERE studyPlanId = :planId
          AND locked = 0
          AND status IN ('PLANNED', 'MISSED')
        """,
    )
    suspend fun deleteReplaceablePlannedActivities(planId: String): Int


    @Query(
        """
        UPDATE planned_activities
        SET scheduledStartEpochMillis = :newStartEpochMillis,
            plannedDurationMinutes = :newDurationMinutes,
            status = :newStatus,
            completedAtEpochMillis = :newCompletedAtEpochMillis
        WHERE studyPlanId = :planId
          AND id = :activityId
          AND status = :expectedStatus
          AND plannedDurationMinutes = :expectedDurationMinutes
          AND (
            scheduledStartEpochMillis = :expectedStartEpochMillis
            OR (scheduledStartEpochMillis IS NULL AND :expectedStartEpochMillis IS NULL)
          )
        """,
    )
    suspend fun compareAndSetPlannedActivity(
        planId: String,
        activityId: String,
        expectedStatus: String,
        expectedStartEpochMillis: Long?,
        expectedDurationMinutes: Int,
        newStatus: String,
        newStartEpochMillis: Long?,
        newDurationMinutes: Int,
        newCompletedAtEpochMillis: Long?,
    ): Int

    @Query(
        """
        UPDATE planned_activities
        SET scheduledStartEpochMillis = :newStartEpochMillis,
            plannedDurationMinutes = :newDurationMinutes,
            status = :newStatus,
            completedAtEpochMillis = :newCompletedAtEpochMillis
        WHERE studyPlanId = :planId
          AND id = :activityId
          AND locked = 0
          AND type = :expectedType
          AND status = :expectedStatus
          AND plannedDurationMinutes = :expectedDurationMinutes
          AND (
            scheduledStartEpochMillis = :expectedStartEpochMillis
            OR (scheduledStartEpochMillis IS NULL AND :expectedStartEpochMillis IS NULL)
          )
        """,
    )
    suspend fun compareAndSetMissedRebalanceActivity(
        planId: String,
        activityId: String,
        expectedType: String,
        expectedStatus: String,
        expectedStartEpochMillis: Long?,
        expectedDurationMinutes: Int,
        newStatus: String,
        newStartEpochMillis: Long?,
        newDurationMinutes: Int,
        newCompletedAtEpochMillis: Long?,
    ): Int

    @Query(
        """
        UPDATE planned_activities
        SET type = :newType,
            scheduledStartEpochMillis = :newStartEpochMillis,
            plannedDurationMinutes = :newDurationMinutes,
            status = :newStatus,
            completedAtEpochMillis = :newCompletedAtEpochMillis
        WHERE studyPlanId = :planId
          AND id = :activityId
          AND locked = 0
          AND type = :expectedType
          AND status = :expectedStatus
          AND plannedDurationMinutes = :expectedDurationMinutes
          AND (
            scheduledStartEpochMillis = :expectedStartEpochMillis
            OR (scheduledStartEpochMillis IS NULL AND :expectedStartEpochMillis IS NULL)
          )
        """,
    )
    suspend fun compareAndSetAdaptivePlannedActivity(
        planId: String,
        activityId: String,
        expectedType: String,
        expectedStatus: String,
        expectedStartEpochMillis: Long?,
        expectedDurationMinutes: Int,
        newType: String,
        newStatus: String,
        newStartEpochMillis: Long?,
        newDurationMinutes: Int,
        newCompletedAtEpochMillis: Long?,
    ): Int


    @Query(
        """
        UPDATE planned_activities
        SET status = :newStatus,
            completedAtEpochMillis = :newCompletedAtEpochMillis
        WHERE studyPlanId = :planId
          AND id = :activityId
          AND status = :expectedStatus
        """,
    )
    suspend fun compareAndSetPlannedActivityStatus(
        planId: String,
        activityId: String,
        expectedStatus: String,
        newStatus: String,
        newCompletedAtEpochMillis: Long?,
    ): Int

    @Query(
        """
        UPDATE study_sessions
        SET status = :newStatus,
            endedAtEpochMillis = :endedAtEpochMillis,
            grossDurationSeconds = :grossDurationSeconds,
            pauseDurationSeconds = :pauseDurationSeconds
        WHERE studyPlanId = :planId
          AND id = :sessionId
          AND status = :expectedStatus
        """,
    )
    suspend fun compareAndSetStudySessionStatus(
        planId: String,
        sessionId: String,
        expectedStatus: String,
        newStatus: String,
        endedAtEpochMillis: Long?,
        grossDurationSeconds: Long,
        pauseDurationSeconds: Long,
    ): Int


    @Query(
        """
        UPDATE study_sessions
        SET status = 'COMPLETED',
            endedAtEpochMillis = :endedAtEpochMillis,
            grossDurationSeconds = :grossDurationSeconds,
            pauseDurationSeconds = :pauseDurationSeconds,
            pagesCompleted = :pagesCompleted,
            questionsAnswered = :questionsAnswered,
            correctAnswers = :correctAnswers,
            concentrationRating = :concentrationRating,
            difficultyRating = :difficultyRating,
            notes = :notes
        WHERE studyPlanId = :planId
          AND id = :sessionId
          AND status = :expectedStatus
        """,
    )
    suspend fun completeStudySessionIfStatus(
        planId: String,
        sessionId: String,
        expectedStatus: String,
        endedAtEpochMillis: Long,
        grossDurationSeconds: Long,
        pauseDurationSeconds: Long,
        pagesCompleted: Int,
        questionsAnswered: Int,
        correctAnswers: Int,
        concentrationRating: Int?,
        difficultyRating: Int?,
        notes: String,
    ): Int

    @Query("SELECT * FROM study_sessions WHERE studyPlanId = :planId ORDER BY startedAtEpochMillis DESC")
    fun observeStudySessions(planId: String): Flow<List<StudySessionEntity>>

    @Query(
        """
        SELECT * FROM study_goals
        WHERE studyPlanId = :planId
        ORDER BY archived, completedAtEpochMillis, startsOnIso DESC
        """,
    )
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
        """,
    )
    suspend fun findActiveStudySessions(planId: String): List<StudySessionEntity>

    @Query(
        """
        SELECT * FROM study_sessions
        WHERE status IN ('RUNNING', 'PAUSED')
        ORDER BY startedAtEpochMillis DESC
        """,
    )
    fun observeAllActiveStudySessions(): Flow<List<StudySessionEntity>>

    @Query(
        """
        SELECT * FROM study_sessions
        WHERE status IN ('RUNNING', 'PAUSED')
        ORDER BY startedAtEpochMillis DESC
        """,
    )
    suspend fun findAllActiveStudySessions(): List<StudySessionEntity>

    @Query(
        """
        UPDATE study_sessions
        SET notes = :notes
        WHERE studyPlanId = :planId
          AND id = :sessionId
          AND status IN ('RUNNING', 'PAUSED')
        """,
    )
    suspend fun updateActiveStudySessionNotes(
        planId: String,
        sessionId: String,
        notes: String,
    ): Int
}
