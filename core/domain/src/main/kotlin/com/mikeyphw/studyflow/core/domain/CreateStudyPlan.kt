package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudyPlanSettings

class CreateStudyPlan(
    private val repository: StudyPlanRepository,
) {
    suspend operator fun invoke(
        plan: StudyPlan,
        settings: StudyPlanSettings = StudyPlanSettings.defaults(plan.id),
    ) {
        require(plan.name.isNotBlank()) { "Study Plan name must not be blank." }
        require(plan.weeklyGoalHours in 1..100) { "Weekly goal must be between 1 and 100 hours." }
        require(settings.studyPlanId == plan.id) { "Plan settings must belong to the created Study Plan." }
        repository.create(plan, settings)
    }
}
