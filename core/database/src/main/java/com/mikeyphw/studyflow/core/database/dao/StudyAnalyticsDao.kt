package com.mikeyphw.studyflow.core.database.dao

import androidx.room.Dao
import androidx.room.Query

data class StudyAnalyticsAggregateRow(
    val studiedMinutesLastSevenDays: Int,
    val completedSessionsLastSevenDays: Int,
    val completedPlannedCount: Int,
    val elapsedPlannedCount: Int,
    val answeredQuestions: Int,
    val correctAnswers: Int,
    val dueReviewCount: Int,
    val overdueReviewCount: Int,
    val retentionEaseAverage: Double,
    val contentProgressAverage: Double,
    val materialProgressScoreSum: Int,
    val materialCount: Int,
    val completedVideoCount: Int,
    val videoCount: Int,
)

/** Bounded SQL aggregates used by the analytics hot path. */
@Dao
interface StudyAnalyticsDao {
    @Query(
        """
        SELECT
            (SELECT coalesce(sum(CAST(max(s.grossDurationSeconds, 0) / 60 AS INTEGER)), 0)
                FROM study_sessions s
                WHERE s.studyPlanId = :planId AND s.status = 'COMPLETED'
                    AND s.startedAtEpochMillis >= :sevenDayWindowStart) AS studiedMinutesLastSevenDays,
            (SELECT count(*) FROM study_sessions s
                WHERE s.studyPlanId = :planId AND s.status = 'COMPLETED'
                    AND s.startedAtEpochMillis >= :sevenDayWindowStart) AS completedSessionsLastSevenDays,
            (SELECT count(*) FROM planned_activities a
                WHERE a.studyPlanId = :planId AND a.status = 'COMPLETED') AS completedPlannedCount,
            (SELECT count(*) FROM planned_activities a
                WHERE a.studyPlanId = :planId AND a.scheduledStartEpochMillis IS NOT NULL
                    AND a.scheduledStartEpochMillis <= :nowEpochMillis
                    AND a.status <> 'CANCELLED') AS elapsedPlannedCount,
            ((SELECT coalesce(sum(t.answeredCount), 0) FROM study_tests t
                WHERE t.studyPlanId = :planId AND t.submittedAtEpochMillis IS NOT NULL) +
             (SELECT coalesce(sum(s.questionsAnswered), 0) FROM study_sessions s
                WHERE s.studyPlanId = :planId AND s.status = 'COMPLETED')) AS answeredQuestions,
            ((SELECT coalesce(sum(t.correctCount), 0) FROM study_tests t
                WHERE t.studyPlanId = :planId AND t.submittedAtEpochMillis IS NOT NULL) +
             (SELECT coalesce(sum(s.correctAnswers), 0) FROM study_sessions s
                WHERE s.studyPlanId = :planId AND s.status = 'COMPLETED')) AS correctAnswers,
            (SELECT count(*) FROM review_items r
                WHERE r.studyPlanId = :planId AND r.status IN ('SCHEDULED', 'DUE')
                    AND r.dueAtEpochMillis <= :nowEpochMillis) AS dueReviewCount,
            (SELECT count(*) FROM review_items r
                WHERE r.studyPlanId = :planId AND r.status IN ('SCHEDULED', 'DUE')
                    AND r.dueAtEpochMillis < :overdueCutoffEpochMillis) AS overdueReviewCount,
            (SELECT coalesce(avg(r.easeFactor), 0.0) FROM review_items r
                WHERE r.studyPlanId = :planId AND r.status = 'COMPLETED') AS retentionEaseAverage,
            (SELECT coalesce(avg(t.progressPercent), 0.0) FROM topics t
                WHERE t.studyPlanId = :planId AND t.archived = 0) AS contentProgressAverage,
            (SELECT coalesce(sum(CASE
                    WHEN m.totalUnits IS NOT NULL AND m.totalUnits > 0
                    THEN CAST((m.completedUnits * 100.0) / m.totalUnits AS INTEGER)
                    ELSE 0 END), 0)
                FROM study_materials m
                WHERE m.studyPlanId = :planId AND m.archived = 0) AS materialProgressScoreSum,
            (SELECT count(*) FROM study_materials m
                WHERE m.studyPlanId = :planId AND m.archived = 0) AS materialCount,
            (SELECT count(*) FROM video_resources v
                WHERE v.studyPlanId = :planId AND v.completed = 1) AS completedVideoCount,
            (SELECT count(*) FROM video_resources v
                WHERE v.studyPlanId = :planId) AS videoCount
        """,
    )
    suspend fun snapshot(
        planId: String,
        sevenDayWindowStart: Long,
        nowEpochMillis: Long,
        overdueCutoffEpochMillis: Long,
    ): StudyAnalyticsAggregateRow
}
