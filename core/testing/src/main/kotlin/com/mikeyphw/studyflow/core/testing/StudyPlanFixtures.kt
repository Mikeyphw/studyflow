package com.mikeyphw.studyflow.core.testing

import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyPlanStatus
import com.mikeyphw.studyflow.core.model.StudyType

object StudyPlanFixtures {
    fun activePlan(
        id: String = "fixture-plan",
        name: String = "Fixture Study Plan",
    ): StudyPlan = StudyPlan(
        id = StudyPlanId(id),
        name = name,
        description = "Reusable test fixture.",
        type = StudyType.CERTIFICATION,
        status = StudyPlanStatus.ACTIVE,
        examDateIso = "2027-01-15",
        weeklyGoalHours = 12,
        progressPercent = 35,
        studiedMinutesThisWeek = 320,
        questionsAnswered = 120,
        averageAccuracyPercent = 72,
    )
}
