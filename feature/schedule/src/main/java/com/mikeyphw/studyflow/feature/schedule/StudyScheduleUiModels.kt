package com.mikeyphw.studyflow.feature.schedule

import com.mikeyphw.studyflow.core.model.PlannedActivity
import com.mikeyphw.studyflow.core.model.PlannedActivityStatus
import com.mikeyphw.studyflow.core.model.ScheduleRule
import com.mikeyphw.studyflow.core.model.StudyActivityType
import com.mikeyphw.studyflow.core.model.StudyStrategyType

fun ScheduleRule.timeLabel(): String = startMinuteOfDay?.let { minute ->
    val hour = (minute / 60).toString().padStart(2, '0')
    val minutes = (minute % 60).toString().padStart(2, '0')
    "$hour:$minutes"
} ?: "--:--"

fun PlannedActivity.timeLabel(): String = scheduledStartEpochMillis?.let { millis ->
    val totalMinutes = (millis / 60_000L).mod(24 * 60).toInt()
    val hour = (totalMinutes / 60).toString().padStart(2, '0')
    val minutes = (totalMinutes % 60).toString().padStart(2, '0')
    "$hour:$minutes"
} ?: "--:--"

fun PlannedActivity.isActionable(): Boolean = status in setOf(
    PlannedActivityStatus.PLANNED,
    PlannedActivityStatus.IN_PROGRESS,
    PlannedActivityStatus.MISSED,
)

fun StudyStrategyType.labelKey(): Int = when (this) {
    StudyStrategyType.FIXED_WEEKLY -> R.string.strategy_fixed_weekly
    StudyStrategyType.STUDY_CYCLE -> R.string.strategy_study_cycle
    StudyStrategyType.GOAL_BASED -> R.string.strategy_goal_based
    StudyStrategyType.ADAPTIVE -> R.string.strategy_adaptive
    StudyStrategyType.FREE_GUIDED -> R.string.strategy_free_guided
    StudyStrategyType.INTENSIVE_SPRINT -> R.string.strategy_intensive_sprint
    StudyStrategyType.COUNTDOWN -> R.string.strategy_countdown
}

fun StudyActivityType.labelKey(): Int = when (this) {
    StudyActivityType.THEORY -> R.string.activity_type_theory
    StudyActivityType.QUESTIONS -> R.string.activity_type_questions
    StudyActivityType.REVIEW -> R.string.activity_type_review
    StudyActivityType.FLASHCARDS -> R.string.activity_type_flashcards
    StudyActivityType.MOCK_EXAM -> R.string.activity_type_mock_exam
    StudyActivityType.WRITING -> R.string.activity_type_writing
    StudyActivityType.VIDEO -> R.string.activity_type_video
    StudyActivityType.READING -> R.string.activity_type_reading
    StudyActivityType.CUSTOM -> R.string.activity_type_custom
}

fun PlannedActivityStatus.labelKey(): Int = when (this) {
    PlannedActivityStatus.PLANNED -> R.string.status_planned
    PlannedActivityStatus.IN_PROGRESS -> R.string.status_in_progress
    PlannedActivityStatus.COMPLETED -> R.string.status_completed
    PlannedActivityStatus.SKIPPED -> R.string.status_skipped
    PlannedActivityStatus.MISSED -> R.string.status_missed
    PlannedActivityStatus.CANCELLED -> R.string.status_cancelled
}

fun dayLabelKey(day: Int?): Int = when (day) {
    1 -> R.string.monday
    2 -> R.string.tuesday
    3 -> R.string.wednesday
    4 -> R.string.thursday
    5 -> R.string.friday
    6 -> R.string.saturday
    7 -> R.string.sunday
    else -> R.string.day_of_week
}
