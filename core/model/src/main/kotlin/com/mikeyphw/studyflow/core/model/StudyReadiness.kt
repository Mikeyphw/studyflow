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

enum class ReadinessConfidence {
    LOW,
    MEDIUM,
    HIGH,
}

data class ReadinessSignal(
    val kind: ReadinessSignalKind,
    val label: String,
    val scorePercent: Int?,
    val message: String,
    val severity: ExamReadinessBand?,
) {
    init {
        require(scorePercent == null || scorePercent in 0..100) {
            "Readiness signal score must be between 0 and 100."
        }
        require((scorePercent == null) == (severity == null)) {
            "Readiness signal severity must be absent when its score is unavailable."
        }
    }
}

data class ExamReadinessScore(
    val studyPlanId: StudyPlanId,
    val scorePercent: Int?,
    val band: ExamReadinessBand?,
    val daysUntilExam: Int?,
    val coveragePercent: Int?,
    val reviewHealthPercent: Int?,
    val practiceHealthPercent: Int?,
    val mockExamHealthPercent: Int?,
    val consistencyPercent: Int?,
    val scheduleHealthPercent: Int?,
    val timeFeasibilityPercent: Int?,
    val signals: List<ReadinessSignal>,
    val confidence: ReadinessConfidence? = ReadinessConfidence.HIGH,
    val evidenceDimensionCount: Int = 7,
    val evidenceSampleCount: Int = 0,
) {
    init {
        require(scorePercent == null || scorePercent in 0..100) { "Readiness score must be between 0 and 100." }
        require((scorePercent == null) == (band == null)) {
            "Readiness band must be absent when the readiness score is unavailable."
        }
        require((scorePercent == null) == (confidence == null)) {
            "Readiness confidence must be absent when the readiness score is unavailable."
        }
        require(coveragePercent == null || coveragePercent in 0..100) { "Coverage score must be between 0 and 100." }
        require(reviewHealthPercent == null || reviewHealthPercent in 0..100) {
            "Review score must be between 0 and 100."
        }
        require(practiceHealthPercent == null || practiceHealthPercent in 0..100) {
            "Practice score must be between 0 and 100."
        }
        require(mockExamHealthPercent == null || mockExamHealthPercent in 0..100) {
            "Mock exam score must be between 0 and 100."
        }
        require(consistencyPercent == null || consistencyPercent in 0..100) {
            "Consistency score must be between 0 and 100."
        }
        require(scheduleHealthPercent == null || scheduleHealthPercent in 0..100) {
            "Schedule score must be between 0 and 100."
        }
        require(timeFeasibilityPercent == null || timeFeasibilityPercent in 0..100) {
            "Time-feasibility score must be between 0 and 100."
        }
        require(daysUntilExam == null || daysUntilExam >= 0) { "Days until exam cannot be negative." }
        require(evidenceDimensionCount >= 0) { "Evidence dimension count cannot be negative." }
        require(evidenceSampleCount >= 0) { "Evidence sample count cannot be negative." }
    }

    val hasSufficientEvidence: Boolean = scorePercent != null
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
    val readinessScorePercent: Int?,
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
