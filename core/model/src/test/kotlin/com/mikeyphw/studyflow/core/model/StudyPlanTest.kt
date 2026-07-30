package com.mikeyphw.studyflow.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class StudyPlanTest {
    @Test
    fun `progress is represented independently from localized UI text`() {
        val plan = StudyPlan(
            id = StudyPlanId("plan-1"),
            name = "TRT 2027",
            description = "Preparation",
            type = StudyType.PUBLIC_EXAMINATION,
            status = StudyPlanStatus.ACTIVE,
            examDateIso = "2027-10-06",
            weeklyGoalHours = 20,
            progressPercent = 42,
            studiedMinutesThisWeek = 760,
            questionsAnswered = 428,
            averageAccuracyPercent = 74,
        )

        assertEquals(StudyPlanStatus.ACTIVE, plan.status)
        assertEquals(42, plan.progressPercent)
    }
}
