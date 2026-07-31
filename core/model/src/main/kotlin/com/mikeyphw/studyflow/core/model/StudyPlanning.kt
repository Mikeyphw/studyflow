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
)

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
)

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
)

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
)

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
)

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
)

data class ScheduleGenerationResult(
    val generatedActivities: Int,
    val removedUnlockedActivities: Int,
)

data class RebalanceResult(
    val updatedActivities: Int,
    val handling: MissedActivityHandling,
)
