package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.StudyPlan

class CreateStudyPlan(
    private val repository: StudyPlanRepository,
) {
    suspend operator fun invoke(plan: StudyPlan) {
        require(plan.name.isNotBlank()) { "Study Plan name must not be blank." }
        require(plan.weeklyGoalHours in 1..100) { "Weekly goal must be between 1 and 100 hours." }
        repository.create(plan)
    }
}
