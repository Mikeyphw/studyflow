package com.mikeyphw.studyflow.widget

import com.mikeyphw.studyflow.core.model.PlannedActivityId
import com.mikeyphw.studyflow.core.model.StudyActivityType
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudySession
import com.mikeyphw.studyflow.core.model.StudySessionId
import com.mikeyphw.studyflow.core.model.StudySessionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StudyFlowWidgetActionPolicyOv14Test {
    @Test
    fun startNextOnlyTreatsConfiguredPlanSessionAsActive() {
        val planA = StudyPlanId("plan-a")
        val planB = StudyPlanId("plan-b")
        val other = session("other-session", planB)
        val configured = session("configured-session", planA)

        assertNull(StudyFlowWidgetActionPolicy.activeSessionForConfiguredPlan(listOf(other), planA))
        assertEquals(configured, StudyFlowWidgetActionPolicy.activeSessionForConfiguredPlan(listOf(other, configured), planA))
    }

    private fun session(id: String, planId: StudyPlanId) = StudySession(
        id = StudySessionId(id),
        studyPlanId = planId,
        plannedActivityId = PlannedActivityId("activity-$id"),
        subjectId = null,
        topicId = null,
        materialId = null,
        videoResourceId = null,
        type = StudyActivityType.THEORY,
        status = StudySessionStatus.RUNNING,
        startedAtEpochMillis = 100L,
        endedAtEpochMillis = null,
        grossDurationSeconds = 0L,
        pauseDurationSeconds = 0L,
        pagesCompleted = 0,
        questionsAnswered = 0,
        correctAnswers = 0,
        concentrationRating = null,
        difficultyRating = null,
        notes = null,
        targetDurationMinutes = 30,
    )
}
