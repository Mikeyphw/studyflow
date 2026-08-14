package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.StudyPlanId

enum class StudyInsightSeverity {
    INFO,
    WARNING,
    CRITICAL,
}

enum class StudyInsightKind {
    OVERDUE_REVIEWS,
    SCHEDULE_DRIFT,
    ACCURACY_RISK,
    WEAK_AREAS,
    STABLE_PLAN,
}

data class StudyInsight(
    val kind: StudyInsightKind,
    val severity: StudyInsightSeverity,
)

data class WeakStudyArea(
    val label: String,
    val progressPercent: Int,
    val masteryPercent: Int,
    val priority: Int,
)

data class StudyAnalyticsSnapshot(
    val studyPlanId: StudyPlanId,
    val studiedMinutesLastSevenDays: Int,
    val completedSessionsLastSevenDays: Int,
    val scheduleAdherencePercent: Int,
    val questionAccuracyPercent: Int,
    val answeredQuestions: Int,
    val dueReviewCount: Int,
    val overdueReviewCount: Int,
    val retentionEaseAverage: Double,
    val goalCompletionPercent: Int,
    val contentProgressPercent: Int,
    val libraryProgressPercent: Int,
    val consistencyScorePercent: Int,
    val weakAreas: List<WeakStudyArea>,
    val insights: List<StudyInsight>,
) {
    val hasBacklog: Boolean = dueReviewCount > 0 || overdueReviewCount > 0
}

object StudyAnalyticsCalculator {
    fun percent(numerator: Int, denominator: Int): Int {
        if (denominator <= 0) return 0
        return ((numerator.coerceAtLeast(0).toDouble() / denominator.toDouble()) * 100).toInt().coerceIn(0, 100)
    }

    fun consistencyScore(activeStudyDays: Int, targetDays: Int = 5): Int = percent(activeStudyDays, targetDays)

    fun goalCompletion(current: Double, target: Double): Int {
        if (target <= 0.0) return 0
        return ((current.coerceAtLeast(0.0) / target) * 100.0).toInt().coerceIn(0, 100)
    }

    fun buildInsights(snapshot: StudyAnalyticsSnapshot): List<StudyInsight> = buildList {
        if (snapshot.overdueReviewCount > 0) {
            add(StudyInsight(StudyInsightKind.OVERDUE_REVIEWS, StudyInsightSeverity.WARNING))
        }
        if (snapshot.scheduleAdherencePercent < 60 && snapshot.completedSessionsLastSevenDays > 0) {
            add(StudyInsight(StudyInsightKind.SCHEDULE_DRIFT, StudyInsightSeverity.WARNING))
        }
        if (snapshot.questionAccuracyPercent in 1..59) {
            add(StudyInsight(StudyInsightKind.ACCURACY_RISK, StudyInsightSeverity.CRITICAL))
        }
        if (snapshot.weakAreas.isNotEmpty()) {
            add(StudyInsight(StudyInsightKind.WEAK_AREAS, StudyInsightSeverity.INFO))
        }
        if (isEmpty()) {
            add(StudyInsight(StudyInsightKind.STABLE_PLAN, StudyInsightSeverity.INFO))
        }
    }
}
