package com.mikeyphw.studyflow.core.model

enum class StudyStrategyType {
    FIXED_WEEKLY,
    STUDY_CYCLE,
    GOAL_BASED,
    ADAPTIVE,
    FREE_GUIDED,
    INTENSIVE_SPRINT,
    COUNTDOWN,
}

enum class ScheduleRuleType {
    FIXED_BLOCK,
    RECURRING_BLOCK,
    CYCLE_SLOT,
    AVAILABILITY,
    DAY_OFF,
}

enum class StudyActivityType {
    THEORY,
    QUESTIONS,
    REVIEW,
    FLASHCARDS,
    MOCK_EXAM,
    WRITING,
    VIDEO,
    READING,
    CUSTOM,
}

enum class PlannedActivityStatus {
    PLANNED,
    IN_PROGRESS,
    COMPLETED,
    SKIPPED,
    MISSED,
    CANCELLED,
}

enum class StudySessionStatus {
    RUNNING,
    PAUSED,
    COMPLETED,
    CANCELLED,
}

enum class GoalType {
    STUDY_TIME,
    SESSION_COUNT,
    QUESTION_COUNT,
    ACCURACY,
    TOPIC_COMPLETION,
    MOCK_EXAM_COUNT,
    CUSTOM,
}

enum class GoalPeriod {
    DAILY,
    WEEKLY,
    MONTHLY,
    CUSTOM,
}

enum class MissedActivityHandling {
    POSTPONE,
    REDISTRIBUTE,
    RETURN_TO_BACKLOG,
    SKIP,
    KEEP_UNCHANGED,
}

data class StudyStrategy(
    val id: StudyStrategyId,
    val studyPlanId: StudyPlanId,
    val type: StudyStrategyType,
    val minimumSessionMinutes: Int,
    val maximumSessionMinutes: Int,
    val theoryRatioPercent: Int,
    val questionRatioPercent: Int,
    val reviewRatioPercent: Int,
    val adaptivePlanningEnabled: Boolean,
    val configurationJson: String,
) {
    init {
        StudyDomainInvariants.requireStrategyDurations(minimumSessionMinutes, maximumSessionMinutes)
        require(theoryRatioPercent in 0..100 && questionRatioPercent in 0..100 && reviewRatioPercent in 0..100) {
            "Study strategy ratios must be between 0 and 100 percent."
        }
        require(theoryRatioPercent + questionRatioPercent + reviewRatioPercent == 100) {
            "Study strategy ratios must total 100 percent."
        }
    }
}

data class ScheduleRule(
    val id: ScheduleRuleId,
    val studyPlanId: StudyPlanId,
    val subjectId: SubjectId?,
    val type: ScheduleRuleType,
    val dayOfWeek: Int?,
    val startMinuteOfDay: Int?,
    val durationMinutes: Int,
    val recurrenceRule: String?,
    val locked: Boolean,
    val enabled: Boolean,
    val position: Int,
) {
    init {
        require(dayOfWeek == null || dayOfWeek in 1..7) { "Schedule day must be between 1 and 7." }
        require(startMinuteOfDay == null || startMinuteOfDay in 0..1439) { "Schedule start must be within the day." }
        StudyDomainInvariants.requireSessionMinutes(durationMinutes, "Schedule duration")
        require(position >= 0) { "Schedule position cannot be negative." }
    }
}

data class PlannedActivity(
    val id: PlannedActivityId,
    val studyPlanId: StudyPlanId,
    val subjectId: SubjectId?,
    val topicId: TopicId?,
    val materialId: StudyMaterialId?,
    val videoResourceId: VideoResourceId?,
    val type: StudyActivityType,
    val title: String,
    val scheduledStartEpochMillis: Long?,
    val plannedDurationMinutes: Int,
    val status: PlannedActivityStatus,
    val locked: Boolean,
    val completedAtEpochMillis: Long?,
) {
    init {
        require(title.isNotBlank()) { "Planned activity title cannot be blank." }
        StudyDomainInvariants.requireSessionMinutes(plannedDurationMinutes, "Planned duration")
    }
}

data class StudySession(
    val id: StudySessionId,
    val studyPlanId: StudyPlanId,
    val plannedActivityId: PlannedActivityId?,
    val subjectId: SubjectId?,
    val topicId: TopicId?,
    val materialId: StudyMaterialId?,
    val videoResourceId: VideoResourceId?,
    val type: StudyActivityType,
    val status: StudySessionStatus,
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long?,
    val grossDurationSeconds: Long,
    val pauseDurationSeconds: Long,
    val pagesCompleted: Int,
    val questionsAnswered: Int,
    val correctAnswers: Int,
    val concentrationRating: Int?,
    val difficultyRating: Int?,
    val notes: String,
    val targetDurationMinutes: Int? = null,
) {
    init {
        require(grossDurationSeconds >= 0) { "Gross duration cannot be negative." }
        require(pauseDurationSeconds >= 0) { "Pause duration cannot be negative." }
        require(pagesCompleted >= 0) { "Completed pages cannot be negative." }
        require(questionsAnswered >= 0) { "Answered questions cannot be negative." }
        require(correctAnswers in 0..questionsAnswered) { "Correct answers must be within answered questions." }
        require(concentrationRating == null || concentrationRating in 1..5) { "Concentration rating must be 1 to 5." }
        require(difficultyRating == null || difficultyRating in 1..5) { "Difficulty rating must be 1 to 5." }
        StudyDomainInvariants.requireOptionalSessionMinutes(targetDurationMinutes, "Target duration")
        require(endedAtEpochMillis == null || endedAtEpochMillis >= startedAtEpochMillis) {
            "Session end cannot precede start."
        }
    }
}

data class StudyGoal(
    val id: StudyGoalId,
    val studyPlanId: StudyPlanId,
    val subjectId: SubjectId?,
    val topicId: TopicId?,
    val type: GoalType,
    val period: GoalPeriod,
    val title: String,
    val targetValue: Double,
    val currentValue: Double,
    val startsOnIso: String,
    val endsOnIso: String?,
    val completedAtEpochMillis: Long?,
    val archived: Boolean,
) {
    init {
        require(title.isNotBlank()) { "Study goal title cannot be blank." }
        require(targetValue > 0.0) { "Study goal target must be positive." }
        require(currentValue >= 0.0) { "Study goal progress cannot be negative." }
    }
}

data class StudySchedule(
    val studyPlanId: StudyPlanId,
    val strategy: StudyStrategy?,
    val scheduleRules: List<ScheduleRule>,
    val plannedActivities: List<PlannedActivity>,
) {
    val pendingActivities: List<PlannedActivity> = plannedActivities.filter {
        it.status == PlannedActivityStatus.PLANNED || it.status == PlannedActivityStatus.MISSED
    }

    val lockedActivities: List<PlannedActivity> = plannedActivities.filter(PlannedActivity::locked)
}

data class ScheduleGenerationRequest(
    val studyPlanId: StudyPlanId,
    val startEpochMillis: Long,
    val days: Int,
    val replaceUnlocked: Boolean,
) {
    init {
        require(days in 1..366) { "Generation days must be between 1 and 366." }
    }
}

data class ScheduleGenerationResult(
    val generatedActivities: Int,
    val removedUnlockedActivities: Int,
)

data class RebalanceResult(
    val updatedActivities: Int,
    val handling: MissedActivityHandling,
)
