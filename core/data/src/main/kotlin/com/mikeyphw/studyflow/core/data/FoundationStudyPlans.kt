package com.mikeyphw.studyflow.core.data

import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyPlanStatus
import com.mikeyphw.studyflow.core.model.StudyType

object FoundationStudyPlans {
    fun create(): List<StudyPlan> = listOf(
        StudyPlan(
            id = StudyPlanId("trt-2027"),
            name = "TRT 2027",
            description = "Complete preparation for a technical analyst public examination.",
            type = StudyType.PUBLIC_EXAMINATION,
            status = StudyPlanStatus.ACTIVE,
            examDateIso = "2027-10-06",
            weeklyGoalHours = 20,
            progressPercent = 42,
            studiedMinutesThisWeek = 760,
            questionsAnswered = 428,
            averageAccuracyPercent = 74,
        ),
        StudyPlan(
            id = StudyPlanId("banco-do-brasil"),
            name = "Banco do Brasil",
            description = "Secondary plan focused on banking knowledge and customer service.",
            type = StudyType.PUBLIC_EXAMINATION,
            status = StudyPlanStatus.PAUSED,
            examDateIso = null,
            weeklyGoalHours = 8,
            progressPercent = 18,
            studiedMinutesThisWeek = 180,
            questionsAnswered = 92,
            averageAccuracyPercent = 67,
        ),
    )
}
