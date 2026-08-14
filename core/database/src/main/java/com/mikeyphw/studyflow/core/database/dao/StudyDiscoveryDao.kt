package com.mikeyphw.studyflow.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.mikeyphw.studyflow.core.database.entity.StudyPlanEntity

data class IndexedDiscoverySearchRow(
    val type: String,
    val id: String,
    val planId: String,
    val planName: String,
    val title: String,
    val subtitle: String,
    val updatedAtEpochMillis: Long,
)

data class TopicRecommendationRow(
    val topicId: String,
    val topicName: String,
    val masteryPercent: Int,
    val priority: Int,
    val questionCount: Int,
    val errorFrequency: Int,
)

data class ActivityRecommendationRow(
    val id: String,
    val title: String,
    val scheduledStartEpochMillis: Long?,
    val status: String,
)

data class ResourceRecommendationRow(
    val id: String,
    val title: String,
    val completedUnits: Int,
    val totalUnits: Int?,
    val scheduledStartEpochMillis: Long,
)

data class GoalRecommendationRow(
    val title: String,
    val endsOnIso: String,
)

data class ScheduleAdherenceRow(
    val elapsedCount: Int,
    val completedCount: Int,
)

data class PracticeAccuracyRow(
    val answeredCount: Int,
    val correctCount: Int,
)

@Dao
interface StudyDiscoveryDao {
    @RawQuery
    suspend fun searchIndexed(query: SupportSQLiteQuery): List<IndexedDiscoverySearchRow>

    @Query("SELECT * FROM study_plans WHERE id = :planId LIMIT 1")
    suspend fun findPlan(planId: String): StudyPlanEntity?

    @Query(
        """
        SELECT coalesce(sum(answeredCount), 0) AS answeredCount,
            coalesce(sum(correctCount), 0) AS correctCount
        FROM (
            SELECT t.answeredCount AS answeredCount, t.correctCount AS correctCount
            FROM study_tests t
            WHERE t.studyPlanId = :planId AND t.submittedAtEpochMillis IS NOT NULL
            UNION ALL
            SELECT s.questionsAnswered AS answeredCount, s.correctAnswers AS correctCount
            FROM study_sessions s
            WHERE s.studyPlanId = :planId AND s.status = 'COMPLETED'
        )
        """,
    )
    suspend fun findPracticeAccuracy(planId: String): PracticeAccuracyRow

    @Query(
        """
        SELECT t.id AS topicId, t.name AS topicName, t.masteryPercent AS masteryPercent,
            t.priority AS priority,
            (SELECT COUNT(*) FROM questions q
                WHERE q.studyPlanId = :planId AND q.topicId = t.id) AS questionCount,
            (SELECT coalesce(sum(e.recurrenceCount), 0) FROM error_entries e
                WHERE e.studyPlanId = :planId AND e.topicId = t.id) AS errorFrequency
        FROM topics t
        JOIN subjects s ON s.id = t.subjectId AND s.studyPlanId = t.studyPlanId
        WHERE t.studyPlanId = :planId AND t.archived = 0 AND s.archived = 0
        ORDER BY t.name COLLATE NOCASE, t.id
        """,
    )
    suspend fun findTopicRecommendations(planId: String): List<TopicRecommendationRow>

    @Query(
        """
        SELECT COUNT(*) FROM review_items r
        LEFT JOIN subjects s ON s.id = r.subjectId AND s.studyPlanId = r.studyPlanId
        LEFT JOIN topics t ON t.id = r.topicId AND t.studyPlanId = r.studyPlanId
        LEFT JOIN subjects topicSubject ON topicSubject.id = t.subjectId AND topicSubject.studyPlanId = t.studyPlanId
        LEFT JOIN questions q ON q.id = r.questionId AND q.studyPlanId = r.studyPlanId
        LEFT JOIN subjects questionSubject ON questionSubject.id = q.subjectId
            AND questionSubject.studyPlanId = q.studyPlanId
        LEFT JOIN topics questionTopic ON questionTopic.id = q.topicId AND questionTopic.studyPlanId = q.studyPlanId
        LEFT JOIN subjects questionTopicSubject ON questionTopicSubject.id = questionTopic.subjectId
            AND questionTopicSubject.studyPlanId = questionTopic.studyPlanId
        LEFT JOIN error_entries e ON e.id = r.errorEntryId AND e.studyPlanId = r.studyPlanId
        LEFT JOIN subjects errorSubject ON errorSubject.id = e.subjectId AND errorSubject.studyPlanId = e.studyPlanId
        LEFT JOIN topics errorTopic ON errorTopic.id = e.topicId AND errorTopic.studyPlanId = e.studyPlanId
        LEFT JOIN subjects errorTopicSubject ON errorTopicSubject.id = errorTopic.subjectId
            AND errorTopicSubject.studyPlanId = errorTopic.studyPlanId
        LEFT JOIN questions errorQuestion ON errorQuestion.id = e.questionId
            AND errorQuestion.studyPlanId = e.studyPlanId
        LEFT JOIN subjects errorQuestionSubject ON errorQuestionSubject.id = errorQuestion.subjectId
            AND errorQuestionSubject.studyPlanId = errorQuestion.studyPlanId
        LEFT JOIN topics errorQuestionTopic ON errorQuestionTopic.id = errorQuestion.topicId
            AND errorQuestionTopic.studyPlanId = errorQuestion.studyPlanId
        LEFT JOIN subjects errorQuestionTopicSubject ON errorQuestionTopicSubject.id = errorQuestionTopic.subjectId
            AND errorQuestionTopicSubject.studyPlanId = errorQuestionTopic.studyPlanId
        LEFT JOIN flashcards f ON f.id = r.flashcardId AND f.studyPlanId = r.studyPlanId
        LEFT JOIN subjects flashcardSubject ON flashcardSubject.id = f.subjectId
            AND flashcardSubject.studyPlanId = f.studyPlanId
        LEFT JOIN topics flashcardTopic ON flashcardTopic.id = f.topicId AND flashcardTopic.studyPlanId = f.studyPlanId
        LEFT JOIN subjects flashcardTopicSubject ON flashcardTopicSubject.id = flashcardTopic.subjectId
            AND flashcardTopicSubject.studyPlanId = flashcardTopic.studyPlanId
        WHERE r.studyPlanId = :planId AND r.status IN ('SCHEDULED', 'DUE')
            AND r.dueAtEpochMillis <= :now
            AND (r.subjectId IS NULL OR s.archived = 0)
            AND (r.topicId IS NULL OR (t.archived = 0 AND topicSubject.archived = 0))
            AND (r.questionId IS NULL OR (
                q.id IS NOT NULL
                    AND (q.subjectId IS NULL OR questionSubject.archived = 0)
                    AND (q.topicId IS NULL OR (
                        questionTopic.archived = 0 AND questionTopicSubject.archived = 0
                    ))
            ))
            AND (r.errorEntryId IS NULL OR (
                e.id IS NOT NULL
                    AND (e.subjectId IS NULL OR errorSubject.archived = 0)
                    AND (e.topicId IS NULL OR (
                        errorTopic.archived = 0 AND errorTopicSubject.archived = 0
                    ))
                    AND (e.questionId IS NULL OR (
                        errorQuestion.id IS NOT NULL
                            AND (errorQuestion.subjectId IS NULL OR errorQuestionSubject.archived = 0)
                            AND (errorQuestion.topicId IS NULL OR (
                                errorQuestionTopic.archived = 0
                                    AND errorQuestionTopicSubject.archived = 0
                            ))
                    ))
            ))
            AND (r.flashcardId IS NULL OR (
                f.id IS NOT NULL AND f.suspended = 0
                    AND (f.subjectId IS NULL OR flashcardSubject.archived = 0)
                    AND (f.topicId IS NULL OR (
                        flashcardTopic.archived = 0 AND flashcardTopicSubject.archived = 0
                    ))
            ))
        """,
    )
    suspend fun countDueReviews(planId: String, now: Long): Int

    @Query(
        """
        SELECT a.id, a.title, a.scheduledStartEpochMillis, a.status FROM planned_activities a
        LEFT JOIN subjects s ON s.id = a.subjectId AND s.studyPlanId = a.studyPlanId
        LEFT JOIN topics t ON t.id = a.topicId AND t.studyPlanId = a.studyPlanId
        LEFT JOIN subjects topicSubject ON topicSubject.id = t.subjectId AND topicSubject.studyPlanId = t.studyPlanId
        LEFT JOIN study_materials m ON m.id = a.materialId AND m.studyPlanId = a.studyPlanId
        LEFT JOIN subjects materialSubject ON materialSubject.id = m.subjectId
            AND materialSubject.studyPlanId = m.studyPlanId
        LEFT JOIN topics materialTopic ON materialTopic.id = m.topicId AND materialTopic.studyPlanId = m.studyPlanId
        LEFT JOIN subjects materialTopicSubject ON materialTopicSubject.id = materialTopic.subjectId
            AND materialTopicSubject.studyPlanId = materialTopic.studyPlanId
        WHERE a.studyPlanId = :planId AND a.status IN ('PLANNED', 'MISSED')
            AND (a.subjectId IS NULL OR s.archived = 0)
            AND (a.topicId IS NULL OR (t.archived = 0 AND topicSubject.archived = 0))
            AND (a.materialId IS NULL OR (
                m.id IS NOT NULL AND m.archived = 0
                    AND (m.subjectId IS NULL OR materialSubject.archived = 0)
                    AND (m.topicId IS NULL OR (
                        materialTopic.archived = 0 AND materialTopicSubject.archived = 0
                    ))
            ))
        ORDER BY CASE WHEN a.status = 'MISSED' THEN 0 ELSE 1 END,
            CASE WHEN a.scheduledStartEpochMillis IS NULL THEN 1 ELSE 0 END,
            a.scheduledStartEpochMillis, a.id
        LIMIT 1
        """,
    )
    suspend fun findNextActivity(planId: String): ActivityRecommendationRow?

    @Query(
        """
        SELECT COUNT(*) AS elapsedCount,
            coalesce(sum(CASE WHEN a.status = 'COMPLETED' THEN 1 ELSE 0 END), 0) AS completedCount
        FROM planned_activities a
        LEFT JOIN subjects s ON s.id = a.subjectId AND s.studyPlanId = a.studyPlanId
        LEFT JOIN topics t ON t.id = a.topicId AND t.studyPlanId = a.studyPlanId
        LEFT JOIN subjects topicSubject ON topicSubject.id = t.subjectId AND topicSubject.studyPlanId = t.studyPlanId
        LEFT JOIN study_materials m ON m.id = a.materialId AND m.studyPlanId = a.studyPlanId
        LEFT JOIN subjects materialSubject ON materialSubject.id = m.subjectId
            AND materialSubject.studyPlanId = m.studyPlanId
        LEFT JOIN topics materialTopic ON materialTopic.id = m.topicId AND materialTopic.studyPlanId = m.studyPlanId
        LEFT JOIN subjects materialTopicSubject ON materialTopicSubject.id = materialTopic.subjectId
            AND materialTopicSubject.studyPlanId = materialTopic.studyPlanId
        WHERE a.studyPlanId = :planId AND a.scheduledStartEpochMillis IS NOT NULL
            AND a.scheduledStartEpochMillis <= :now
            AND (a.subjectId IS NULL OR s.archived = 0)
            AND (a.topicId IS NULL OR (t.archived = 0 AND topicSubject.archived = 0))
            AND (a.materialId IS NULL OR (
                m.id IS NOT NULL AND m.archived = 0
                    AND (m.subjectId IS NULL OR materialSubject.archived = 0)
                    AND (m.topicId IS NULL OR (
                        materialTopic.archived = 0 AND materialTopicSubject.archived = 0
                    ))
            ))
        """,
    )
    suspend fun findScheduleAdherence(planId: String, now: Long): ScheduleAdherenceRow

    @Query(
        """
        SELECT m.id AS id, m.title AS title, m.completedUnits AS completedUnits,
            m.totalUnits AS totalUnits, a.scheduledStartEpochMillis AS scheduledStartEpochMillis
        FROM planned_activities a
        JOIN study_materials m ON m.id = a.materialId AND m.studyPlanId = a.studyPlanId
        LEFT JOIN subjects s ON s.id = m.subjectId AND s.studyPlanId = m.studyPlanId
        LEFT JOIN topics t ON t.id = m.topicId AND t.studyPlanId = m.studyPlanId
        LEFT JOIN subjects topicSubject ON topicSubject.id = t.subjectId AND topicSubject.studyPlanId = t.studyPlanId
        WHERE a.studyPlanId = :planId AND a.scheduledStartEpochMillis < :now
            AND a.status IN ('PLANNED', 'MISSED', 'IN_PROGRESS') AND m.archived = 0
            AND (m.subjectId IS NULL OR s.archived = 0)
            AND (m.topicId IS NULL OR (t.archived = 0 AND topicSubject.archived = 0))
            AND (m.totalUnits IS NULL OR m.completedUnits < m.totalUnits)
        ORDER BY a.scheduledStartEpochMillis, m.title COLLATE NOCASE, m.id
        LIMIT 1
        """,
    )
    suspend fun findOverdueResource(planId: String, now: Long): ResourceRecommendationRow?

    @Query(
        """
        SELECT g.title, g.endsOnIso FROM study_goals g
        LEFT JOIN subjects s ON s.id = g.subjectId AND s.studyPlanId = g.studyPlanId
        LEFT JOIN topics t ON t.id = g.topicId AND t.studyPlanId = g.studyPlanId
        LEFT JOIN subjects topicSubject ON topicSubject.id = t.subjectId AND topicSubject.studyPlanId = t.studyPlanId
        WHERE g.studyPlanId = :planId AND g.archived = 0 AND g.completedAtEpochMillis IS NULL
            AND g.endsOnIso IS NOT NULL
            AND (g.subjectId IS NULL OR s.archived = 0)
            AND (g.topicId IS NULL OR (t.archived = 0 AND topicSubject.archived = 0))
        ORDER BY g.endsOnIso, g.title COLLATE NOCASE, g.id
        LIMIT 1
        """,
    )
    suspend fun findNearestGoal(planId: String): GoalRecommendationRow?
}
