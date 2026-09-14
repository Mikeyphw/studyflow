package com.mikeyphw.studyflow.feature.plans

import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyPlanStatus
import com.mikeyphw.studyflow.core.model.StudyType
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Ov15PlanHonestyContractsTest {
    @Test
    fun archivedPlansDoNotExposeOpenAction() {
        assertFalse(canOpenPlanFromList(plan(StudyPlanStatus.ARCHIVED)))
        assertFalse(canOpenPlanFromList(plan(StudyPlanStatus.TRASHED)))
        assertTrue(canOpenPlanFromList(plan(StudyPlanStatus.ACTIVE)))
    }

    @Test
    fun examCountdownUsesLocalCalendarDayInsteadOfUtcFlooring() {
        val calendar = Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, 14, 23, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }
        assertEquals(1L, daysUntilExamLocalCalendar("2026-09-15", calendar.timeInMillis))
    }

    @Test
    fun nextActivityCopyRequiresConcreteActivityEvidence() {
        assertFalse(truthfulNextActivityAvailable(hasConcreteActivity = false))
        assertTrue(truthfulNextActivityAvailable(hasConcreteActivity = true))
    }

    private fun plan(status: StudyPlanStatus) = StudyPlan(
        id = StudyPlanId("plan-$status"),
        name = "Plan",
        description = "",
        type = StudyType.PUBLIC_EXAMINATION,
        status = status,
        examDateIso = null,
        weeklyGoalHours = 10,
        progressPercent = 0,
        studiedMinutesThisWeek = 0,
        questionsAnswered = 0,
        averageAccuracyPercent = 0,
    )
}
