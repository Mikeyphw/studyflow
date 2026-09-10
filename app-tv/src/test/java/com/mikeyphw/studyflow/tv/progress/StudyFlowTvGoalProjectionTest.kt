package com.mikeyphw.studyflow.tv.progress

import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvControlAction
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowTvGoalProjectionTest {
    @Test
    fun customGoalCanCompleteOrPauseThroughCanonicalProgressCommands() {
        val goal = StudyFlowTvGoal("goal-1", "Write essay", "CUSTOM", 50, false, false, true)
        assertEquals(
            listOf(StudyFlowTvGoalAction.COMPLETE, StudyFlowTvGoalAction.PAUSE),
            StudyFlowTvProgressAnalytics.goalActions(goal),
        )
        val command = StudyFlowTvProgressAnalytics.commandForGoalAction(goal, StudyFlowTvGoalAction.COMPLETE, "c1", 10L)
        assertEquals(StudyFlowTvSurface.PROGRESS, command.surface)
        assertEquals(StudyFlowTvControlAction.COMPLETE, command.action)
        assertEquals("goal-1", command.targetId)
    }

    @Test
    fun pausedGoalOnlyOffersResumeAndEvidenceGoalCannotManualComplete() {
        val paused = StudyFlowTvGoal("goal-2", "Read", "STUDY_TIME", 70, false, true, false)
        assertEquals(listOf(StudyFlowTvGoalAction.RESUME), StudyFlowTvProgressAnalytics.goalActions(paused))
        val activeEvidence = paused.copy(paused = false)
        assertEquals(listOf(StudyFlowTvGoalAction.PAUSE), StudyFlowTvProgressAnalytics.goalActions(activeEvidence))
        assertTrue(StudyFlowTvProgressAnalytics.goalProjectionAndActions)
    }
}
