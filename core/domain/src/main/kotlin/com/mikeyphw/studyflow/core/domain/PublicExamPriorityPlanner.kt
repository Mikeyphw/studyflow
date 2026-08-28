package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.SubjectId
import com.mikeyphw.studyflow.core.model.TopicId
import kotlin.math.max

/** Explainable, deterministic evidence for one public-exam study target. */
data class PublicExamPriorityEvidence(
    val studyPlanId: StudyPlanId,
    val subjectId: SubjectId,
    val topicId: TopicId?,
    val subjectName: String,
    val topicName: String?,
    val examWeight: Double,
    val examIncidencePercent: Int?,
    val masteryPercent: Int,
    val recentAccuracyPercent: Int?,
    val recurringErrorCount: Int,
    val overdueReviewCount: Int,
    val missedScheduleCount: Int,
    val daysUntilExam: Int?,
    val cycleDistance: Int?,
    val minutesStudiedLast7Days: Int,
    val hoursSinceLastStudy: Int?,
    val lockedByActiveSession: Boolean = false,
) {
    init {
        require(subjectName.isNotBlank())
        require(examWeight >= 0.0)
        require(examIncidencePercent == null || examIncidencePercent in 0..100)
        require(masteryPercent in 0..100)
        require(recentAccuracyPercent == null || recentAccuracyPercent in 0..100)
        require(recurringErrorCount >= 0 && overdueReviewCount >= 0 && missedScheduleCount >= 0)
        require(daysUntilExam == null || daysUntilExam >= 0)
        require(cycleDistance == null || cycleDistance >= 0)
        require(minutesStudiedLast7Days >= 0)
        require(hoursSinceLastStudy == null || hoursSinceLastStudy >= 0)
    }
}

enum class PublicExamPriorityReasonCode {
    HIGH_EXAM_WEIGHT,
    HIGH_INCIDENCE,
    LOW_MASTERY,
    LOW_ACCURACY,
    RECURRING_ERRORS,
    OVERDUE_REVIEW,
    MISSED_SCHEDULE,
    EXAM_NEAR,
    CYCLE_POSITION,
    COOLDOWN,
    STARVATION_RELIEF,
}

data class PublicExamPriorityRecommendation(
    val evidence: PublicExamPriorityEvidence,
    val score: Double,
    val reasonCodes: List<PublicExamPriorityReasonCode>,
)

object PublicExamPriorityPlanner {
    fun rank(
        planId: StudyPlanId,
        evidence: List<PublicExamPriorityEvidence>,
        limit: Int = 5,
    ): List<PublicExamPriorityRecommendation> {
        require(limit in 1..20)
        val owned = evidence.filter { it.studyPlanId == planId && !it.lockedByActiveSession }
        if (owned.isEmpty()) return emptyList()
        val maxWeight = owned.maxOf { it.examWeight }.coerceAtLeast(1.0)
        val scored = owned.map { item -> score(item, maxWeight) }
            .sortedWith(
                compareByDescending<PublicExamPriorityRecommendation> { it.score }
                    .thenBy { it.evidence.subjectId.value }
                    .thenBy { it.evidence.topicId?.value.orEmpty() },
            )
        // Avoid one subject starving all others when equally actionable alternatives exist.
        val selected = mutableListOf<PublicExamPriorityRecommendation>()
        val deferred = mutableListOf<PublicExamPriorityRecommendation>()
        val perSubject = mutableMapOf<SubjectId, Int>()
        scored.forEach { candidate ->
            if ((perSubject[candidate.evidence.subjectId] ?: 0) < 2) {
                selected += candidate
                perSubject[candidate.evidence.subjectId] = (perSubject[candidate.evidence.subjectId] ?: 0) + 1
            } else {
                deferred += candidate
            }
        }
        return (selected + deferred).take(limit)
    }

    private fun score(item: PublicExamPriorityEvidence, maxWeight: Double): PublicExamPriorityRecommendation {
        val components = linkedMapOf(
            PublicExamPriorityReasonCode.HIGH_EXAM_WEIGHT to (item.examWeight / maxWeight).coerceIn(0.0, 1.0) * 0.17,
            PublicExamPriorityReasonCode.HIGH_INCIDENCE to (item.examIncidencePercent ?: 0) / 100.0 * 0.12,
            PublicExamPriorityReasonCode.LOW_MASTERY to (100 - item.masteryPercent) / 100.0 * 0.15,
            PublicExamPriorityReasonCode.LOW_ACCURACY to (100 - (item.recentAccuracyPercent ?: 100)) / 100.0 * 0.12,
            PublicExamPriorityReasonCode.RECURRING_ERRORS to (item.recurringErrorCount.coerceAtMost(5) / 5.0) * 0.12,
            PublicExamPriorityReasonCode.OVERDUE_REVIEW to (item.overdueReviewCount.coerceAtMost(5) / 5.0) * 0.12,
            PublicExamPriorityReasonCode.MISSED_SCHEDULE to (item.missedScheduleCount.coerceAtMost(4) / 4.0) * 0.06,
            PublicExamPriorityReasonCode.EXAM_NEAR to nearExam(item.daysUntilExam) * 0.07,
            PublicExamPriorityReasonCode.CYCLE_POSITION to cyclePriority(item.cycleDistance) * 0.04,
            PublicExamPriorityReasonCode.STARVATION_RELIEF to starvation(item.hoursSinceLastStudy) * 0.08,
        )
        val saturationPenalty = (item.minutesStudiedLast7Days / 600.0).coerceIn(0.0, 1.0) * 0.08
        val raw = components.values.sum() - saturationPenalty
        val reasons = components.entries.sortedByDescending(Map.Entry<PublicExamPriorityReasonCode, Double>::value)
            .filter { it.value >= 0.025 }
            .take(4)
            .map(Map.Entry<PublicExamPriorityReasonCode, Double>::key)
            .toMutableList()
        if (saturationPenalty >= 0.04) reasons += PublicExamPriorityReasonCode.COOLDOWN
        return PublicExamPriorityRecommendation(item, raw.coerceIn(0.0, 1.0), reasons.distinct())
    }

    private fun nearExam(days: Int?): Double = when {
        days == null -> 0.0
        days <= 7 -> 1.0
        days <= 30 -> 0.7
        days <= 90 -> 0.35
        else -> 0.0
    }

    private fun cyclePriority(distance: Int?): Double = when (distance) {
        null -> 0.0
        0 -> 1.0
        1 -> 0.6
        2 -> 0.3
        else -> 0.0
    }

    private fun starvation(hours: Int?): Double = when {
        hours == null -> 0.0
        hours >= 168 -> 1.0
        hours >= 72 -> 0.65
        hours >= 24 -> 0.25
        else -> 0.0
    }
}
