package com.mikeyphw.studyflow.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import com.mikeyphw.studyflow.core.database.entity.StudyPlanEntity

data class DiscoverySearchRow(
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

@Dao
interface StudyDiscoveryDao {
    @Query(
        """
        SELECT p.id AS id, p.id AS planId, p.name AS planName, p.name AS title,
            p.description AS subtitle, p.updatedAtEpochMillis AS updatedAtEpochMillis
        FROM study_plans p
        WHERE (:includeArchived OR p.status NOT IN ('ARCHIVED', 'TRASHED'))
            AND instr(lower(p.name || ' ' || p.description), lower(:query)) > 0
        """,
    )
    suspend fun searchPlans(query: String, includeArchived: Boolean): List<DiscoverySearchRow>

    @Query(
        """
        SELECT s.id AS id, s.studyPlanId AS planId, p.name AS planName, s.name AS title,
            s.description AS subtitle, p.updatedAtEpochMillis AS updatedAtEpochMillis
        FROM subjects s JOIN study_plans p ON p.id = s.studyPlanId
        WHERE (:includeArchived OR (p.status NOT IN ('ARCHIVED', 'TRASHED') AND s.archived = 0))
            AND instr(lower(s.name || ' ' || s.description), lower(:query)) > 0
        """,
    )
    suspend fun searchSubjects(query: String, includeArchived: Boolean): List<DiscoverySearchRow>

    @Query(
        """
        SELECT t.id AS id, t.studyPlanId AS planId, p.name AS planName, t.name AS title,
            t.description AS subtitle, p.updatedAtEpochMillis AS updatedAtEpochMillis
        FROM topics t
        JOIN study_plans p ON p.id = t.studyPlanId
        JOIN subjects s ON s.id = t.subjectId
        WHERE (:includeArchived OR (
            p.status NOT IN ('ARCHIVED', 'TRASHED') AND t.archived = 0 AND s.archived = 0
        ))
            AND instr(lower(t.name || ' ' || t.description), lower(:query)) > 0
        """,
    )
    suspend fun searchTopics(query: String, includeArchived: Boolean): List<DiscoverySearchRow>

    @Query(
        """
        SELECT q.id AS id, q.studyPlanId AS planId, p.name AS planName, q.statement AS title,
            q.explanation AS subtitle, q.updatedAtEpochMillis AS updatedAtEpochMillis
        FROM questions q
        JOIN study_plans p ON p.id = q.studyPlanId
        LEFT JOIN subjects s ON s.id = q.subjectId
        LEFT JOIN topics t ON t.id = q.topicId
        LEFT JOIN subjects topicSubject ON topicSubject.id = t.subjectId
        WHERE (:includeArchived OR (
            p.status NOT IN ('ARCHIVED', 'TRASHED')
                AND (q.subjectId IS NULL OR s.archived = 0)
                AND (q.topicId IS NULL OR (t.archived = 0 AND topicSubject.archived = 0))
        ))
            AND instr(lower(q.statement || ' ' || q.explanation || ' ' || coalesce(q.source, '')),
                lower(:query)) > 0
        """,
    )
    suspend fun searchQuestions(query: String, includeArchived: Boolean): List<DiscoverySearchRow>

    @Query(
        """
        SELECT n.id AS id, n.studyPlanId AS planId, p.name AS planName, n.title AS title,
            substr(n.content, 1, 180) AS subtitle, n.updatedAtEpochMillis AS updatedAtEpochMillis
        FROM study_notes n
        JOIN study_plans p ON p.id = n.studyPlanId
        LEFT JOIN subjects s ON s.id = n.subjectId
        LEFT JOIN topics t ON t.id = n.topicId
        LEFT JOIN subjects topicSubject ON topicSubject.id = t.subjectId
        LEFT JOIN study_materials m ON m.id = n.materialId
        LEFT JOIN subjects materialSubject ON materialSubject.id = m.subjectId
        LEFT JOIN topics materialTopic ON materialTopic.id = m.topicId
        LEFT JOIN subjects materialTopicSubject ON materialTopicSubject.id = materialTopic.subjectId
        WHERE (:includeArchived OR (
            p.status NOT IN ('ARCHIVED', 'TRASHED')
                AND (n.subjectId IS NULL OR s.archived = 0)
                AND (n.topicId IS NULL OR (t.archived = 0 AND topicSubject.archived = 0))
                AND (n.materialId IS NULL OR (
                    m.archived = 0
                        AND (m.subjectId IS NULL OR materialSubject.archived = 0)
                        AND (m.topicId IS NULL OR (
                            materialTopic.archived = 0 AND materialTopicSubject.archived = 0
                        ))
                ))
        ))
            AND instr(lower(n.title || ' ' || n.content || ' ' || n.tagsJson), lower(:query)) > 0
        """,
    )
    suspend fun searchNotes(query: String, includeArchived: Boolean): List<DiscoverySearchRow>

    @Query(
        """
        SELECT m.id AS id, m.studyPlanId AS planId, p.name AS planName, m.title AS title,
            m.description AS subtitle, m.updatedAtEpochMillis AS updatedAtEpochMillis
        FROM study_materials m
        JOIN study_plans p ON p.id = m.studyPlanId
        LEFT JOIN subjects s ON s.id = m.subjectId
        LEFT JOIN topics t ON t.id = m.topicId
        LEFT JOIN subjects topicSubject ON topicSubject.id = t.subjectId
        WHERE (:includeArchived OR (
            p.status NOT IN ('ARCHIVED', 'TRASHED') AND m.archived = 0
                AND (m.subjectId IS NULL OR s.archived = 0)
                AND (m.topicId IS NULL OR (t.archived = 0 AND topicSubject.archived = 0))
        ))
            AND instr(lower(m.title || ' ' || m.description || ' ' || m.tagsJson), lower(:query)) > 0
        """,
    )
    suspend fun searchMaterials(query: String, includeArchived: Boolean): List<DiscoverySearchRow>

    @Query(
        """
        SELECT f.id AS id, f.studyPlanId AS planId, p.name AS planName, f.front AS title,
            f.back AS subtitle, f.updatedAtEpochMillis AS updatedAtEpochMillis
        FROM flashcards f
        JOIN study_plans p ON p.id = f.studyPlanId
        LEFT JOIN subjects s ON s.id = f.subjectId
        LEFT JOIN topics t ON t.id = f.topicId
        LEFT JOIN subjects topicSubject ON topicSubject.id = t.subjectId
        WHERE (:includeArchived OR (
            p.status NOT IN ('ARCHIVED', 'TRASHED')
                AND (f.subjectId IS NULL OR s.archived = 0)
                AND (f.topicId IS NULL OR (t.archived = 0 AND topicSubject.archived = 0))
        ))
            AND instr(lower(f.front || ' ' || f.back || ' ' || coalesce(f.clozeText, '')), lower(:query)) > 0
        """,
    )
    suspend fun searchFlashcards(query: String, includeArchived: Boolean): List<DiscoverySearchRow>

    @Query(
        """
        SELECT e.id AS id, e.studyPlanId AS planId, p.name AS planName,
            coalesce(q.statement, e.category) AS title,
            e.explanation || ' · ' || e.category AS subtitle,
            e.updatedAtEpochMillis AS updatedAtEpochMillis
        FROM error_entries e
        JOIN study_plans p ON p.id = e.studyPlanId
        LEFT JOIN questions q ON q.id = e.questionId
        LEFT JOIN subjects s ON s.id = e.subjectId
        LEFT JOIN topics t ON t.id = e.topicId
        LEFT JOIN subjects topicSubject ON topicSubject.id = t.subjectId
        LEFT JOIN subjects questionSubject ON questionSubject.id = q.subjectId
        LEFT JOIN topics questionTopic ON questionTopic.id = q.topicId
        LEFT JOIN subjects questionTopicSubject ON questionTopicSubject.id = questionTopic.subjectId
        WHERE (:includeArchived OR (
            p.status NOT IN ('ARCHIVED', 'TRASHED')
                AND (e.subjectId IS NULL OR s.archived = 0)
                AND (e.topicId IS NULL OR (t.archived = 0 AND topicSubject.archived = 0))
                AND (e.questionId IS NULL OR (
                    (q.subjectId IS NULL OR questionSubject.archived = 0)
                        AND (q.topicId IS NULL OR (
                            questionTopic.archived = 0 AND questionTopicSubject.archived = 0
                        ))
                ))
        ))
            AND instr(lower(coalesce(q.statement, '') || ' ' || e.explanation || ' ' || e.category || ' ' ||
                coalesce(e.chosenAnswer, '') || ' ' || coalesce(e.correctAnswer, '')), lower(:query)) > 0
        """,
    )
    suspend fun searchErrors(query: String, includeArchived: Boolean): List<DiscoverySearchRow>

    @Query("SELECT * FROM study_plans WHERE id = :planId LIMIT 1")
    suspend fun findPlan(planId: String): StudyPlanEntity?

    @Query(
        """
        SELECT t.id AS topicId, t.name AS topicName, t.masteryPercent AS masteryPercent,
            t.priority AS priority,
            (SELECT COUNT(*) FROM questions q
                WHERE q.studyPlanId = :planId AND q.topicId = t.id) AS questionCount,
            (SELECT coalesce(sum(e.recurrenceCount), 0) FROM error_entries e
                WHERE e.studyPlanId = :planId AND e.topicId = t.id) AS errorFrequency
        FROM topics t
        JOIN subjects s ON s.id = t.subjectId
        WHERE t.studyPlanId = :planId AND t.archived = 0 AND s.archived = 0
        ORDER BY t.name COLLATE NOCASE, t.id
        """,
    )
    suspend fun findTopicRecommendations(planId: String): List<TopicRecommendationRow>

    @Query(
        """
        SELECT COUNT(*) FROM review_items r
        LEFT JOIN subjects s ON s.id = r.subjectId
        LEFT JOIN topics t ON t.id = r.topicId
        LEFT JOIN subjects topicSubject ON topicSubject.id = t.subjectId
        LEFT JOIN questions q ON q.id = r.questionId
        LEFT JOIN subjects questionSubject ON questionSubject.id = q.subjectId
        LEFT JOIN topics questionTopic ON questionTopic.id = q.topicId
        LEFT JOIN subjects questionTopicSubject ON questionTopicSubject.id = questionTopic.subjectId
        LEFT JOIN error_entries e ON e.id = r.errorEntryId
        LEFT JOIN subjects errorSubject ON errorSubject.id = e.subjectId
        LEFT JOIN topics errorTopic ON errorTopic.id = e.topicId
        LEFT JOIN subjects errorTopicSubject ON errorTopicSubject.id = errorTopic.subjectId
        LEFT JOIN questions errorQuestion ON errorQuestion.id = e.questionId
        LEFT JOIN subjects errorQuestionSubject ON errorQuestionSubject.id = errorQuestion.subjectId
        LEFT JOIN topics errorQuestionTopic ON errorQuestionTopic.id = errorQuestion.topicId
        LEFT JOIN subjects errorQuestionTopicSubject ON
            errorQuestionTopicSubject.id = errorQuestionTopic.subjectId
        LEFT JOIN flashcards f ON f.id = r.flashcardId
        LEFT JOIN subjects flashcardSubject ON flashcardSubject.id = f.subjectId
        LEFT JOIN topics flashcardTopic ON flashcardTopic.id = f.topicId
        LEFT JOIN subjects flashcardTopicSubject ON flashcardTopicSubject.id = flashcardTopic.subjectId
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
        LEFT JOIN subjects s ON s.id = a.subjectId
        LEFT JOIN topics t ON t.id = a.topicId
        LEFT JOIN subjects topicSubject ON topicSubject.id = t.subjectId
        LEFT JOIN study_materials m ON m.id = a.materialId
        LEFT JOIN subjects materialSubject ON materialSubject.id = m.subjectId
        LEFT JOIN topics materialTopic ON materialTopic.id = m.topicId
        LEFT JOIN subjects materialTopicSubject ON materialTopicSubject.id = materialTopic.subjectId
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
        LEFT JOIN subjects s ON s.id = a.subjectId
        LEFT JOIN topics t ON t.id = a.topicId
        LEFT JOIN subjects topicSubject ON topicSubject.id = t.subjectId
        LEFT JOIN study_materials m ON m.id = a.materialId
        LEFT JOIN subjects materialSubject ON materialSubject.id = m.subjectId
        LEFT JOIN topics materialTopic ON materialTopic.id = m.topicId
        LEFT JOIN subjects materialTopicSubject ON materialTopicSubject.id = materialTopic.subjectId
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
        JOIN study_materials m ON m.id = a.materialId
        LEFT JOIN subjects s ON s.id = m.subjectId
        LEFT JOIN topics t ON t.id = m.topicId
        LEFT JOIN subjects topicSubject ON topicSubject.id = t.subjectId
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
        LEFT JOIN subjects s ON s.id = g.subjectId
        LEFT JOIN topics t ON t.id = g.topicId
        LEFT JOIN subjects topicSubject ON topicSubject.id = t.subjectId
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
