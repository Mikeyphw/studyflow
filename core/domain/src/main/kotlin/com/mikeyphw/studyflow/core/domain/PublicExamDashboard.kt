package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.ContentProgressStatus
import com.mikeyphw.studyflow.core.model.ErrorEntry
import com.mikeyphw.studyflow.core.model.MockExam
import com.mikeyphw.studyflow.core.model.Question
import com.mikeyphw.studyflow.core.model.ReviewItem
import com.mikeyphw.studyflow.core.model.ReviewStatus
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudySession
import com.mikeyphw.studyflow.core.model.StudySessionStatus
import com.mikeyphw.studyflow.core.model.StudySyllabus
import com.mikeyphw.studyflow.core.model.StudyTarget
import com.mikeyphw.studyflow.core.model.SubjectId
import com.mikeyphw.studyflow.core.model.TopicId
import java.time.ZoneId

data class PublicExamDashboardMetrics(
    val editalCoveragePercent: Double?,
    val weightedMasteryPercent: Double?,
    val totalQuestions: Int,
    val accuracy7d: Double?,
    val accuracy30d: Double?,
    val accuracy90d: Double?,
    val questionsPerHour30d: Double?,
    val averageSecondsPerQuestion30d: Double?,
    val overdueReviews: Int,
    val recurringErrors: Int,
    val latestMockScorePercent: Double?,
    val estimatedCutoffGapPercent: Double?,
    val daysUntilExam: Int?,
)

data class PublicExamEditalMatrixRow(
    val subjectId: SubjectId,
    val topicId: TopicId?,
    val label: String,
    val examWeight: Double?,
    val incidencePercent: Int?,
    val questionsAnswered: Int,
    val accuracyPercent: Double?,
    val masteryPercent: Int,
    val status: ContentProgressStatus,
    val overdueReviewCount: Int,
    val recurringErrorCount: Int,
    val priorityScore: Double,
)

data class PublicExamDashboardSnapshot(
    val metrics: PublicExamDashboardMetrics,
    val matrix: List<PublicExamEditalMatrixRow>,
)

object PublicExamDashboardBuilder {
    private const val DayMillis = 86_400_000L

    fun build(
        plan: StudyPlan,
        target: StudyTarget?,
        syllabus: StudySyllabus,
        questions: List<Question>,
        reviews: List<ReviewItem>,
        errors: List<ErrorEntry>,
        sessions: List<StudySession>,
        mockExams: List<MockExam>,
        nowEpochMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): PublicExamDashboardSnapshot {
        require(syllabus.studyPlanId == plan.id) { "Dashboard syllabus belongs to another Study Plan." }
        require(target == null || target.studyPlanId == plan.id) {
            "Dashboard target belongs to another Study Plan."
        }
        val completedSessions = sessions.filter {
            it.studyPlanId == plan.id && it.status == StudySessionStatus.COMPLETED
        }
        val activeSubjects = syllabus.subjects.filterNot { it.archived }
        val activeTopics = syllabus.topics.filterNot { it.archived }
        val coverageUnits = activeTopics.ifEmpty { emptyList() }
        val coverage = when {
            coverageUnits.isNotEmpty() -> coverageUnits.map { it.progressPercent }.average()
            activeSubjects.isNotEmpty() -> activeSubjects.map { it.progressPercent }.average()
            else -> null
        }
        val weightedMastery = weightedMastery(activeSubjects, activeTopics)
        val currentScore = mockExams.filter { it.studyPlanId == plan.id && it.submittedAtEpochMillis != null }
            .maxByOrNull { it.submittedAtEpochMillis ?: Long.MIN_VALUE }?.scorePercent
        val targetCutoff = target?.estimatedCutoffPercent?.toDouble()
        val daysUntilExam = PublicExamDateResolver.daysUntilOfficial(plan, target, nowEpochMillis, zoneId)
        val metrics = PublicExamDashboardMetrics(
            editalCoveragePercent = coverage,
            weightedMasteryPercent = weightedMastery,
            totalQuestions = questions.count { it.studyPlanId == plan.id },
            accuracy7d = accuracy(completedSessions, nowEpochMillis - 7 * DayMillis),
            accuracy30d = accuracy(completedSessions, nowEpochMillis - 30 * DayMillis),
            accuracy90d = accuracy(completedSessions, nowEpochMillis - 90 * DayMillis),
            questionsPerHour30d = questionsPerHour(completedSessions, nowEpochMillis - 30 * DayMillis),
            averageSecondsPerQuestion30d = averageSecondsPerQuestion(
                completedSessions,
                nowEpochMillis - 30 * DayMillis,
            ),
            overdueReviews = reviews.count {
                it.studyPlanId == plan.id && it.status in setOf(ReviewStatus.SCHEDULED, ReviewStatus.DUE) &&
                    it.dueAtEpochMillis < nowEpochMillis
            },
            recurringErrors = errors.count { it.studyPlanId == plan.id && it.recurrenceCount > 1 },
            latestMockScorePercent = currentScore,
            estimatedCutoffGapPercent =
                if (currentScore != null && targetCutoff != null) currentScore - targetCutoff else null,
            daysUntilExam = daysUntilExam,
        )
        val matrix = buildMatrix(plan, syllabus, reviews, errors, completedSessions, nowEpochMillis)
        return PublicExamDashboardSnapshot(metrics, matrix)
    }

    private fun weightedMastery(
        subjects: List<com.mikeyphw.studyflow.core.model.Subject>,
        topics: List<com.mikeyphw.studyflow.core.model.Topic>,
    ): Double? {
        if (subjects.isEmpty()) return null
        val subjectWeights = subjects.associate { it.id to it.examWeight.coerceAtLeast(0.0) }
        val weighted = topics.mapNotNull { topic ->
            val subjectWeight = subjectWeights[topic.subjectId] ?: return@mapNotNull null
            val incidence = topic.examIncidencePercent?.div(100.0) ?: 1.0
            val weight = subjectWeight * incidence
            topic.masteryPercent * weight to weight
        }
        val totalWeight = weighted.sumOf { it.second }
        if (totalWeight > 0.0) return weighted.sumOf { it.first } / totalWeight
        return subjects.map { it.progressPercent }.average()
    }

    private fun buildMatrix(
        plan: StudyPlan,
        syllabus: StudySyllabus,
        reviews: List<ReviewItem>,
        errors: List<ErrorEntry>,
        sessions: List<StudySession>,
        now: Long,
    ): List<PublicExamEditalMatrixRow> {
        val rows = mutableListOf<PublicExamEditalMatrixRow>()
        val activeSubjects = syllabus.subjects.filterNot { it.archived }
        val maxExamWeight = activeSubjects.maxOfOrNull { it.examWeight.coerceAtLeast(0.0) } ?: 0.0
        activeSubjects.sortedBy { it.position }.forEach { subject ->
            val subjectTopics = syllabus.topics
                .filter { it.subjectId == subject.id && !it.archived }
                .sortedBy { it.position }
            if (subjectTopics.isEmpty()) {
                rows += row(
                    plan.id, subject.id, null, subject.name, subject.examWeight, maxExamWeight, null,
                    subject.progressPercent, subject.status, reviews, errors, sessions, now,
                )
            } else {
                subjectTopics.forEach { topic ->
                    rows += row(
                        plan.id, subject.id, topic.id, "${subject.name} · ${topic.name}", subject.examWeight,
                        maxExamWeight, topic.examIncidencePercent, topic.masteryPercent, topic.status,
                        reviews, errors, sessions, now,
                    )
                }
            }
        }
        return rows.sortedWith(compareByDescending<PublicExamEditalMatrixRow> { it.priorityScore }.thenBy { it.label })
    }

    private fun row(
        planId: com.mikeyphw.studyflow.core.model.StudyPlanId,
        subjectId: SubjectId,
        topicId: TopicId?,
        label: String,
        examWeight: Double,
        maxExamWeight: Double,
        incidence: Int?,
        mastery: Int,
        status: ContentProgressStatus,
        reviews: List<ReviewItem>,
        errors: List<ErrorEntry>,
        sessions: List<StudySession>,
        now: Long,
    ): PublicExamEditalMatrixRow {
        val scopedSessions = sessions.filter { it.studyPlanId == planId && it.subjectId == subjectId &&
            (topicId == null || it.topicId == topicId) }
        val answered = scopedSessions.sumOf { it.questionsAnswered }
        val correct = scopedSessions.sumOf { it.correctAnswers }
        val accuracy = if (answered > 0) correct * 100.0 / answered else null
        val overdue = reviews.count { it.studyPlanId == planId && it.subjectId == subjectId &&
            (topicId == null || it.topicId == topicId) &&
                it.status in setOf(ReviewStatus.SCHEDULED, ReviewStatus.DUE) &&
            it.dueAtEpochMillis < now }
        val recurring = errors.count { it.studyPlanId == planId && it.subjectId == subjectId &&
            (topicId == null || it.topicId == topicId) && it.recurrenceCount > 1 }
        val normalizedWeight = if (maxExamWeight > 0.0) {
            examWeight.coerceAtLeast(0.0) / maxExamWeight
        } else {
            0.0
        }
        // Unknown incidence stays null in the matrix; scoring uses a documented neutral midpoint only.
        val incidenceFactor = incidence?.div(100.0) ?: 0.5
        val weakness = (100 - mastery).coerceIn(0, 100) / 100.0
        val backlog = (overdue.coerceAtMost(5) + recurring.coerceAtMost(5)) / 10.0
        val priority = (0.35 * normalizedWeight + 0.20 * incidenceFactor + 0.30 * weakness + 0.15 * backlog)
            .coerceIn(0.0, 1.0)
        return PublicExamEditalMatrixRow(
            subjectId = subjectId,
            topicId = topicId,
            label = label,
            examWeight = examWeight.takeIf { it > 0.0 },
            incidencePercent = incidence,
            questionsAnswered = answered,
            accuracyPercent = accuracy,
            masteryPercent = mastery,
            status = status,
            overdueReviewCount = overdue,
            recurringErrorCount = recurring,
            priorityScore = priority,
        )
    }

    private fun accuracy(sessions: List<StudySession>, since: Long): Double? {
        val scoped = sessions.filter { it.startedAtEpochMillis >= since }
        val answered = scoped.sumOf { it.questionsAnswered }
        return if (answered > 0) scoped.sumOf { it.correctAnswers } * 100.0 / answered else null
    }

    private fun questionsPerHour(sessions: List<StudySession>, since: Long): Double? {
        val scoped = sessions.filter { it.startedAtEpochMillis >= since }
        val questions = scoped.sumOf { it.questionsAnswered }
        val seconds = scoped.sumOf { (it.grossDurationSeconds - it.pauseDurationSeconds).coerceAtLeast(0) }
        return if (questions > 0 && seconds > 0) questions / (seconds / 3600.0) else null
    }

    private fun averageSecondsPerQuestion(sessions: List<StudySession>, since: Long): Double? {
        val scoped = sessions.filter { it.startedAtEpochMillis >= since }
        val questions = scoped.sumOf { it.questionsAnswered }
        val seconds = scoped.sumOf { (it.grossDurationSeconds - it.pauseDurationSeconds).coerceAtLeast(0) }
        return if (questions > 0 && seconds > 0) seconds.toDouble() / questions else null
    }

}
