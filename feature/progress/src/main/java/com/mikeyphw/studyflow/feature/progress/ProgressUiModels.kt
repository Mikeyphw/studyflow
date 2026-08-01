package com.mikeyphw.studyflow.feature.progress

import com.mikeyphw.studyflow.core.domain.GoalProgressCalculator
import com.mikeyphw.studyflow.core.model.StudyGoal

enum class ProgressFilter {
    GOALS,
    NOTES,
}

data class GoalRowState(
    val title: String,
    val progressPercent: Int,
    val archived: Boolean,
)

fun StudyGoal.asRowState(): GoalRowState = GoalRowState(
    title = title,
    progressPercent = GoalProgressCalculator.goalPercent(this),
    archived = archived,
)
