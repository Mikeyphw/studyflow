package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.model.GoalPeriod
import com.mikeyphw.studyflow.core.model.GoalType
import com.mikeyphw.studyflow.core.model.StudyGoal
import com.mikeyphw.studyflow.core.model.StudyGoalId
import java.time.LocalDate
import java.util.UUID

class DesktopGoalWorkspaceController(private val services: DesktopStudyFlowServices) {
    fun goals(): List<StudyGoal> {
        val planId = services.defaultPlanId ?: return emptyList()
        return services.progressRepository.currentGoals(planId)
    }

    suspend fun createGoal(type: GoalType, period: GoalPeriod, title: String, targetValue: Double): StudyGoal {
        val planId = requireNotNull(services.currentDefaultPlanForDesktop()) { "No Study Plan is selected." }.id
        return services.manageStudyProgress.createGoal(
            id = StudyGoalId(UUID.randomUUID().toString()),
            studyPlanId = planId,
            subjectId = null,
            topicId = null,
            type = type,
            period = period,
            title = title,
            targetValue = targetValue,
            startsOnIso = LocalDate.now().toString(),
            endsOnIso = null,
        )
    }

    suspend fun incrementCustomGoal(goalId: StudyGoalId, amount: Double): StudyGoal {
        val planId = requireNotNull(services.defaultPlanId) { "No Study Plan is selected." }
        val goal = requireNotNull(services.progressRepository.currentGoals(planId).firstOrNull { it.id == goalId }) {
            "Goal does not exist."
        }
        require(goal.type == GoalType.CUSTOM) { "Only custom goals accept manual progress." }
        return services.manageStudyProgress.updateGoalProgress(planId, goalId, goal.currentValue + amount)
    }

    suspend fun archive(goalId: StudyGoalId) {
        val planId = requireNotNull(services.defaultPlanId) { "No Study Plan is selected." }
        services.manageStudyProgress.archiveGoal(planId, goalId)
    }

    suspend fun restore(goalId: StudyGoalId) {
        val planId = requireNotNull(services.defaultPlanId) { "No Study Plan is selected." }
        services.manageStudyProgress.restoreGoal(planId, goalId)
    }

    suspend fun delete(goalId: StudyGoalId) {
        val planId = requireNotNull(services.defaultPlanId) { "No Study Plan is selected." }
        services.manageStudyProgress.deleteGoal(planId, goalId)
    }
}
