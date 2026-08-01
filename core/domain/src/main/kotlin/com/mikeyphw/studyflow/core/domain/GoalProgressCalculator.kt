package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.StudyGoal
import com.mikeyphw.studyflow.core.model.StudyNote

object GoalProgressCalculator {
    fun goalPercent(goal: StudyGoal): Int {
        if (goal.targetValue <= 0.0) return 0
        return ((goal.currentValue.coerceAtLeast(0.0) / goal.targetValue) * 100.0).toInt().coerceIn(0, 100)
    }

    fun overallGoalPercent(goals: List<StudyGoal>): Int = if (goals.isEmpty()) {
        0
    } else {
        goals.filterNot(StudyGoal::archived).map(::goalPercent).ifEmpty { listOf(0) }.average().toInt()
    }
}

data class StudyProgressSnapshot(
    val goals: List<StudyGoal>,
    val notes: List<StudyNote>,
) {
    val activeGoals: List<StudyGoal> = goals.filterNot(StudyGoal::archived)
    val completedGoals: List<StudyGoal> = activeGoals.filter { goal -> goal.completedAtEpochMillis != null }
    val pinnedNotes: List<StudyNote> = notes.filter(StudyNote::pinned)
    val goalProgressPercent: Int = GoalProgressCalculator.overallGoalPercent(activeGoals)
}
