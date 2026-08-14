package com.mikeyphw.studyflow.core.model

enum class ExamReadinessBand {
    ON_TRACK,
    WATCH,
    AT_RISK,
    CRITICAL,
}

enum class ReadinessSignalKind {
    COVERAGE,
    RETENTION,
    PRACTICE,
    MOCK_EXAM,
    CONSISTENCY,
    SCHEDULE,
    TIME_LEFT,
    WEAK_AREAS,
}

data class ReadinessSignal(
    val kind: ReadinessSignalKind,
    val label: String,
    val scorePercent: Int,
    val message: String,
    val severity: ExamReadinessBand,
)

data class ExamReadinessScore(
    val studyPlanId: StudyPlanId,
    val scorePercent: Int,
    val band: ExamReadinessBand,
    val daysUntilExam: Int?,
    val coveragePercent: Int,
    val reviewHealthPercent: Int,
    val practiceHealthPercent: Int,
    val mockExamHealthPercent: Int,
    val consistencyPercent: Int,
    val scheduleHealthPercent: Int,
    val timeFeasibilityPercent: Int,
    val signals: List<ReadinessSignal>,
) {
    init {
        require(scorePercent in 0..100) { "Readiness score must be between 0 and 100." }
        require(coveragePercent in 0..100) { "Coverage score must be between 0 and 100." }
        require(reviewHealthPercent in 0..100) { "Review score must be between 0 and 100." }
        require(practiceHealthPercent in 0..100) { "Practice score must be between 0 and 100." }
        require(mockExamHealthPercent in 0..100) { "Mock exam score must be between 0 and 100." }
        require(consistencyPercent in 0..100) { "Consistency score must be between 0 and 100." }
        require(scheduleHealthPercent in 0..100) { "Schedule score must be between 0 and 100." }
        require(timeFeasibilityPercent in 0..100) { "Time-feasibility score must be between 0 and 100." }
        require(daysUntilExam == null || daysUntilExam >= 0) { "Days until exam cannot be negative." }
    }
}

enum class AdaptiveRescheduleChangeType {
    REDISTRIBUTE_MISSED,
    PROTECT_REVIEWS,
    REDUCE_OVERLOAD,
    ADD_THEORY_BEFORE_QUESTIONS,
    KEEP_STEADY,
}

data class AdaptiveRescheduleChange(
    val id: String,
    val type: AdaptiveRescheduleChangeType,
    val title: String,
    val reason: String,
    val plannedActivityId: PlannedActivityId?,
    val currentStartEpochMillis: Long?,
    val proposedStartEpochMillis: Long?,
    val currentDurationMinutes: Int?,
    val proposedDurationMinutes: Int?,
    val currentStatus: PlannedActivityStatus?,
    val proposedStatus: PlannedActivityStatus?,
    val locked: Boolean,
    val executable: Boolean,
    val currentType: StudyActivityType? = null,
    val proposedType: StudyActivityType? = null,
)

data class AdaptiveReschedulePreview(
    val studyPlanId: StudyPlanId,
    val generatedAtEpochMillis: Long,
    val readinessScorePercent: Int,
    val missedUnlockedActivities: Int,
    val overdueReviewCount: Int,
    val scheduleAdherencePercent: Int,
    val actionSummary: String,
    val changes: List<AdaptiveRescheduleChange>,
    val scheduleVersionToken: String = "",
) {
    val executableChangeCount: Int = changes.count { it.executable }
}

data class AdaptiveRescheduleApplyResult(
    val studyPlanId: StudyPlanId,
    val requestedChanges: Int,
    val appliedChanges: Int,
    val skippedChanges: Int,
)
