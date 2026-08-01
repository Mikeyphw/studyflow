package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.GoalPeriod
import com.mikeyphw.studyflow.core.model.GoalType
import com.mikeyphw.studyflow.core.model.StudyGoal
import com.mikeyphw.studyflow.core.model.StudyGoalId
import com.mikeyphw.studyflow.core.model.StudyPlanId
import org.junit.Assert.assertEquals
import org.junit.Test

class GoalProgressCalculatorTest {
    @Test
    fun goalPercentClampsToOneHundred() {
        assertEquals(100, GoalProgressCalculator.goalPercent(goal(current = 12.0, target = 10.0)))
    }

    @Test
    fun overallGoalPercentIgnoresArchivedGoals() {
        val progress = GoalProgressCalculator.overallGoalPercent(
            listOf(goal(current = 5.0, target = 10.0), goal(current = 10.0, target = 10.0, archived = true)),
        )
        assertEquals(50, progress)
    }

    private fun goal(current: Double, target: Double, archived: Boolean = false): StudyGoal = StudyGoal(
        id = StudyGoalId("goal-$current"),
        studyPlanId = StudyPlanId("plan-1"),
        subjectId = null,
        topicId = null,
        type = GoalType.STUDY_TIME,
        period = GoalPeriod.WEEKLY,
        title = "Goal",
        targetValue = target,
        currentValue = current,
        startsOnIso = "2026-08-01",
        endsOnIso = null,
        completedAtEpochMillis = null,
        archived = archived,
    )
}
