package com.mikeyphw.studyflow.feature.plans

import com.mikeyphw.studyflow.core.model.StudyContentMode
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyPlanStatus
import com.mikeyphw.studyflow.core.model.StudyType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CreatePlanFormTest {
    @Test
    fun `form retains domain values without localized state`() {
        val form = PlanEditorForm(
            name = "TRT 2027",
            description = "Preparation",
            studyType = StudyType.PUBLIC_EXAMINATION,
            contentMode = StudyContentMode.PROGRAMMING_COURSE,
            examDateIso = "2027-10-06",
            weeklyGoalHours = 20,
            preferredSessionMinutes = 45,
            adaptivePlanningEnabled = true,
            reviewNotificationsEnabled = true,
            strictExamModeEnabled = false,
        )

        assertEquals(StudyType.PUBLIC_EXAMINATION, form.studyType)
        assertEquals(StudyContentMode.PROGRAMMING_COURSE, form.contentMode)
    }

    @Test
    fun `date validator rejects calendar fiction`() {
        assertTrue(isValidIsoDateOrBlank(""))
        assertTrue(isValidIsoDateOrBlank("2027-10-06"))
        assertFalse(isValidIsoDateOrBlank("2027-02-30"))
        assertFalse(isValidIsoDateOrBlank("06/10/2027"))
    }

    @Test
    fun `time parser accepts only valid twenty four hour values`() {
        assertEquals(22 * 60 + 30, parseMinuteOfDay("22:30"))
        assertNull(parseMinuteOfDay("24:00"))
        assertNull(parseMinuteOfDay("7:00"))
    }

    @Test
    fun `collection filters isolate trash and archive`() {
        val current = plan("current", StudyPlanStatus.ACTIVE)
        val archived = plan("archived", StudyPlanStatus.ARCHIVED)
        val trashed = plan("trashed", StudyPlanStatus.TRASHED)
        val plans = listOf(current, archived, trashed)

        assertEquals(listOf(current), plansForFilter(plans, PlanCollectionFilter.CURRENT))
        assertEquals(listOf(archived), plansForFilter(plans, PlanCollectionFilter.ARCHIVED))
        assertEquals(listOf(trashed), plansForFilter(plans, PlanCollectionFilter.TRASH))
    }

    @Test
    fun `trashed plan only exposes restore and permanent deletion`() {
        assertEquals(
            listOf(StudyPlanAction.RESTORE_FROM_TRASH, StudyPlanAction.DELETE_PERMANENTLY),
            planActions(plan("trash", StudyPlanStatus.TRASHED), isDefault = false),
        )
    }

    private fun plan(id: String, status: StudyPlanStatus) = StudyPlan(
        id = StudyPlanId(id),
        name = id,
        description = "",
        type = StudyType.FREE_STUDY,
        status = status,
        examDateIso = null,
        weeklyGoalHours = 10,
        progressPercent = 0,
        studiedMinutesThisWeek = 0,
        questionsAnswered = 0,
        averageAccuracyPercent = 0,
    )
}
