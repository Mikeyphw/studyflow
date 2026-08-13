package com.mikeyphw.studyflow

import com.mikeyphw.studyflow.core.model.AppPreferences
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyPlanStatus
import com.mikeyphw.studyflow.core.model.StudyType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import com.mikeyphw.studyflow.reminders.resolveCanonicalWidgetPlan
import org.junit.Test

class StudyFlowPlanIdentityTest {
    @Test
    fun explicitStaleWorkspaceIdNeverFallsBackToDefault() {
        val default = plan("default", StudyPlanStatus.ACTIVE)
        val preferences = AppPreferences(defaultStudyPlanId = default.id)
        assertNull(resolveWorkspacePlan(listOf(default), preferences, "stale"))
    }

    @Test
    fun temporaryWorkspaceSelectionDoesNotReplaceCanonicalDefault() {
        val default = plan("default", StudyPlanStatus.ACTIVE)
        val temporary = plan("temporary", StudyPlanStatus.PAUSED)
        val preferences = AppPreferences(defaultStudyPlanId = default.id)

        assertEquals(temporary, resolveWorkspacePlan(listOf(default, temporary), preferences, temporary.id.value))
        assertEquals(default.id, preferences.defaultStudyPlanId)
        assertEquals(default, resolveCanonicalDefaultPlan(listOf(default, temporary), preferences))
    }

    @Test
    fun blankWorkspaceIdMayResolveCanonicalDefaultButArchivedDefaultIsNotEligible() {
        val active = plan("active", StudyPlanStatus.ACTIVE)
        val archived = plan("archived", StudyPlanStatus.ARCHIVED)
        assertEquals(active, resolveWorkspacePlan(listOf(active, archived), AppPreferences(
            defaultStudyPlanId = archived.id,
        ), ""))
        assertNull(resolveCanonicalDefaultPlan(listOf(active, archived), AppPreferences(
            defaultStudyPlanId = archived.id,
        )))
    }

    @Test
    fun widgetUsesOnlyStoredCanonicalDefault() {
        val firstActive = plan("first-active", StudyPlanStatus.ACTIVE)
        val canonical = plan("canonical", StudyPlanStatus.PAUSED)
        assertEquals(
            canonical,
            resolveCanonicalWidgetPlan(listOf(firstActive, canonical), canonical.id),
        )
        assertNull(resolveCanonicalWidgetPlan(listOf(firstActive), canonical.id))
        assertNull(
            resolveCanonicalWidgetPlan(
                listOf(plan("archived", StudyPlanStatus.ARCHIVED)),
                StudyPlanId("archived"),
            ),
        )
    }

    private fun plan(id: String, status: StudyPlanStatus) = StudyPlan(
        id = StudyPlanId(id),
        name = id,
        description = "",
        type = StudyType.FREE_STUDY,
        status = status,
        examDateIso = null,
        weeklyGoalHours = 5,
        progressPercent = 0,
        studiedMinutesThisWeek = 0,
        questionsAnswered = 0,
        averageAccuracyPercent = 0,
    )
}
