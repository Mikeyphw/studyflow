package com.mikeyphw.studyflow.core.data

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.mikeyphw.studyflow.core.database.StudyFlowSearchIndex
import com.mikeyphw.studyflow.core.domain.GlobalSearchContentType

internal object IndexedStudyDiscoveryQuery {
    private const val CandidateLimit = 320

    fun search(
        query: String,
        contentTypes: Set<GlobalSearchContentType>,
        includeArchived: Boolean,
    ): SupportSQLiteQuery? {
        val match = matchExpression(query) ?: return null
        if (contentTypes.isEmpty()) return null
        val typeList = contentTypes
            .sortedBy(GlobalSearchContentType::ordinal)
            .joinToString(",") { type -> "'${type.name}'" }
        val hits = """
            SELECT type, entityId
            FROM ${StudyFlowSearchIndex.TableName}
            WHERE ${StudyFlowSearchIndex.TableName} MATCH ?
                AND type IN ($typeList)
            LIMIT $CandidateLimit
        """.trimIndent()
        return SimpleSQLiteQuery(fullQuery(hits, includeArchived), arrayOf(match))
    }

    fun resolve(
        type: GlobalSearchContentType,
        id: String,
        includeArchived: Boolean,
    ): SupportSQLiteQuery {
        val hits = """
            SELECT type, entityId
            FROM ${StudyFlowSearchIndex.TableName}
            WHERE type = ? AND entityId = ?
            LIMIT 1
        """.trimIndent()
        return SimpleSQLiteQuery(fullQuery(hits, includeArchived), arrayOf(type.name, id))
    }

    internal fun matchExpression(query: String): String? {
        val tokens = SearchToken.findAll(query.lowercase())
            .map { match -> match.value }
            .filter { token -> token.isNotBlank() }
            .distinct()
            .take(MaximumQueryTokens)
            .toList()
        if (tokens.isEmpty()) return null
        return tokens.joinToString(separator = " ") { token -> "$token*" }
    }

    private fun fullQuery(hits: String, includeArchived: Boolean): String {
        val archived = if (includeArchived) 1 else 0
        return """
            WITH hits AS (
                $hits
            )
            SELECT 'PLAN' AS type, p.id AS id, p.id AS planId, p.name AS planName,
                p.name AS title, p.description AS subtitle, p.updatedAtEpochMillis AS updatedAtEpochMillis
            FROM hits h
            JOIN study_plans p ON h.type = 'PLAN' AND h.entityId = p.id
            WHERE ($archived OR p.status NOT IN ('ARCHIVED', 'TRASHED'))

            UNION ALL
            SELECT 'SUBJECT', s.id, s.studyPlanId, p.name, s.name, s.description, p.updatedAtEpochMillis
            FROM hits h
            JOIN subjects s ON h.type = 'SUBJECT' AND h.entityId = s.id
            JOIN study_plans p ON p.id = s.studyPlanId
            WHERE ($archived OR (p.status NOT IN ('ARCHIVED', 'TRASHED') AND s.archived = 0))

            UNION ALL
            SELECT 'TOPIC', t.id, t.studyPlanId, p.name, t.name, t.description, p.updatedAtEpochMillis
            FROM hits h
            JOIN topics t ON h.type = 'TOPIC' AND h.entityId = t.id
            JOIN study_plans p ON p.id = t.studyPlanId
            JOIN subjects s ON s.id = t.subjectId AND s.studyPlanId = t.studyPlanId
            WHERE ($archived OR (
                p.status NOT IN ('ARCHIVED', 'TRASHED') AND t.archived = 0 AND s.archived = 0
            ))

            UNION ALL
            SELECT 'QUESTION', q.id, q.studyPlanId, p.name, q.statement, q.explanation, q.updatedAtEpochMillis
            FROM hits h
            JOIN questions q ON h.type = 'QUESTION' AND h.entityId = q.id
            JOIN study_plans p ON p.id = q.studyPlanId
            LEFT JOIN subjects s ON s.id = q.subjectId AND s.studyPlanId = q.studyPlanId
            LEFT JOIN topics t ON t.id = q.topicId AND t.studyPlanId = q.studyPlanId
            LEFT JOIN subjects topicSubject ON topicSubject.id = t.subjectId AND topicSubject.studyPlanId = t.studyPlanId
            WHERE ($archived OR (
                p.status NOT IN ('ARCHIVED', 'TRASHED')
                    AND (q.subjectId IS NULL OR s.archived = 0)
                    AND (q.topicId IS NULL OR (t.archived = 0 AND topicSubject.archived = 0))
            ))

            UNION ALL
            SELECT 'NOTE', n.id, n.studyPlanId, p.name, n.title, substr(n.content, 1, 180), n.updatedAtEpochMillis
            FROM hits h
            JOIN study_notes n ON h.type = 'NOTE' AND h.entityId = n.id
            JOIN study_plans p ON p.id = n.studyPlanId
            LEFT JOIN subjects s ON s.id = n.subjectId AND s.studyPlanId = n.studyPlanId
            LEFT JOIN topics t ON t.id = n.topicId AND t.studyPlanId = n.studyPlanId
            LEFT JOIN subjects topicSubject ON topicSubject.id = t.subjectId AND topicSubject.studyPlanId = t.studyPlanId
            LEFT JOIN study_materials m ON m.id = n.materialId AND m.studyPlanId = n.studyPlanId
            LEFT JOIN subjects materialSubject ON materialSubject.id = m.subjectId AND materialSubject.studyPlanId = m.studyPlanId
            LEFT JOIN topics materialTopic ON materialTopic.id = m.topicId AND materialTopic.studyPlanId = m.studyPlanId
            LEFT JOIN subjects materialTopicSubject ON materialTopicSubject.id = materialTopic.subjectId
                AND materialTopicSubject.studyPlanId = materialTopic.studyPlanId
            WHERE ($archived OR (
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

            UNION ALL
            SELECT 'MATERIAL', m.id, m.studyPlanId, p.name, m.title, m.description, m.updatedAtEpochMillis
            FROM hits h
            JOIN study_materials m ON h.type = 'MATERIAL' AND h.entityId = m.id
            JOIN study_plans p ON p.id = m.studyPlanId
            LEFT JOIN subjects s ON s.id = m.subjectId AND s.studyPlanId = m.studyPlanId
            LEFT JOIN topics t ON t.id = m.topicId AND t.studyPlanId = m.studyPlanId
            LEFT JOIN subjects topicSubject ON topicSubject.id = t.subjectId AND topicSubject.studyPlanId = t.studyPlanId
            WHERE ($archived OR (
                p.status NOT IN ('ARCHIVED', 'TRASHED') AND m.archived = 0
                    AND (m.subjectId IS NULL OR s.archived = 0)
                    AND (m.topicId IS NULL OR (t.archived = 0 AND topicSubject.archived = 0))
            ))

            UNION ALL
            SELECT 'FLASHCARD', f.id, f.studyPlanId, p.name, f.front, f.back, f.updatedAtEpochMillis
            FROM hits h
            JOIN flashcards f ON h.type = 'FLASHCARD' AND h.entityId = f.id
            JOIN study_plans p ON p.id = f.studyPlanId
            LEFT JOIN subjects s ON s.id = f.subjectId AND s.studyPlanId = f.studyPlanId
            LEFT JOIN topics t ON t.id = f.topicId AND t.studyPlanId = f.studyPlanId
            LEFT JOIN subjects topicSubject ON topicSubject.id = t.subjectId AND topicSubject.studyPlanId = t.studyPlanId
            WHERE ($archived OR (
                p.status NOT IN ('ARCHIVED', 'TRASHED')
                    AND (f.subjectId IS NULL OR s.archived = 0)
                    AND (f.topicId IS NULL OR (t.archived = 0 AND topicSubject.archived = 0))
            ))

            UNION ALL
            SELECT 'ERROR', e.id, e.studyPlanId, p.name, coalesce(q.statement, e.category),
                e.explanation || ' · ' || e.category, e.updatedAtEpochMillis
            FROM hits h
            JOIN error_entries e ON h.type = 'ERROR' AND h.entityId = e.id
            JOIN study_plans p ON p.id = e.studyPlanId
            LEFT JOIN questions q ON q.id = e.questionId AND q.studyPlanId = e.studyPlanId
            LEFT JOIN subjects s ON s.id = e.subjectId AND s.studyPlanId = e.studyPlanId
            LEFT JOIN topics t ON t.id = e.topicId AND t.studyPlanId = e.studyPlanId
            LEFT JOIN subjects topicSubject ON topicSubject.id = t.subjectId AND topicSubject.studyPlanId = t.studyPlanId
            LEFT JOIN subjects questionSubject ON questionSubject.id = q.subjectId AND questionSubject.studyPlanId = q.studyPlanId
            LEFT JOIN topics questionTopic ON questionTopic.id = q.topicId AND questionTopic.studyPlanId = q.studyPlanId
            LEFT JOIN subjects questionTopicSubject ON questionTopicSubject.id = questionTopic.subjectId
                AND questionTopicSubject.studyPlanId = questionTopic.studyPlanId
            WHERE ($archived OR (
                p.status NOT IN ('ARCHIVED', 'TRASHED')
                    AND (e.subjectId IS NULL OR s.archived = 0)
                    AND (e.topicId IS NULL OR (t.archived = 0 AND topicSubject.archived = 0))
                    AND (e.questionId IS NULL OR (
                        q.id IS NOT NULL
                            AND (q.subjectId IS NULL OR questionSubject.archived = 0)
                            AND (q.topicId IS NULL OR (
                                questionTopic.archived = 0 AND questionTopicSubject.archived = 0
                            ))
                    ))
            ))
        """.trimIndent()
    }

    private val SearchToken = Regex("[\\p{L}\\p{N}]+")
    private const val MaximumQueryTokens = 8
}
