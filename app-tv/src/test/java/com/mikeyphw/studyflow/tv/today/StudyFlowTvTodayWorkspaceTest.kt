package com.mikeyphw.studyflow.tv.today

import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvControlAction
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvSurface
import com.mikeyphw.studyflow.tv.focus.StudyFlowTvFocusPolicy
import com.mikeyphw.studyflow.tv.focus.StudyFlowTvFocusRole
import com.mikeyphw.studyflow.tv.navigation.StudyFlowTvDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowTvTodayWorkspaceTest {
    @Test
    fun todayWorkspaceOwnsTheTodayDestinationAndCoreWorkload() {
        assertEquals("TVOV07", StudyFlowTvTodayWorkspace.overlay)
        assertTrue(StudyFlowTvTodayWorkspace.ownsTodayDestination)
        assertTrue(StudyFlowTvTodayWorkspace.nextStudyBlockVisible)
        assertTrue(StudyFlowTvTodayWorkspace.reviewQueueVisible)
        assertTrue(StudyFlowTvTodayWorkspace.practiceQueueVisible)
        assertTrue(StudyFlowTvTodayWorkspace.weeklyPlanSummaryVisible)
        assertTrue(StudyFlowTvTodayWorkspace.pairedPhoneCanOpenTodayActions)
    }

    @Test
    fun todaySnapshotValidatesUserVisibleStudyLoad() {
        val snapshot = StudyFlowTvTodayWorkspace.defaultSnapshot()
        val workspace = StudyFlowTvTodayWorkspace.workspaceForSnapshot(snapshot)

        assertEquals(StudyFlowTvTodayWorkspace.blockCount, workspace.blocks.size)
        assertTrue(snapshot.nextBlockMinutes > 0)
        assertTrue(snapshot.weeklyProgressPercent in 0..100)
        assertEquals(workspace.blocks.size, workspace.blocks.map { it.id }.toSet().size)
    }

    @Test
    fun todayActionsRouteToConcreteTvDestinations() {
        assertEquals(StudyFlowTvDestination.STUDY_SESSION, StudyFlowTvTodayWorkspace.destinationForAction(StudyFlowTvTodayAction.START_NEXT_BLOCK))
        assertEquals(StudyFlowTvDestination.REVIEWS, StudyFlowTvTodayWorkspace.destinationForAction(StudyFlowTvTodayAction.START_REVIEWS))
        assertEquals(StudyFlowTvDestination.PRACTICE, StudyFlowTvTodayWorkspace.destinationForAction(StudyFlowTvTodayAction.START_PRACTICE))
        assertEquals(StudyFlowTvDestination.SCHEDULE, StudyFlowTvTodayWorkspace.destinationForAction(StudyFlowTvTodayAction.OPEN_SCHEDULE))
    }

    @Test
    fun todayFocusAndPhoneCommandsUseSharedProtocols() {
        val focusItems = StudyFlowTvTodayWorkspace.focusItems()
        val command = StudyFlowTvTodayWorkspace.commandForAction(
            action = StudyFlowTvTodayAction.START_NEXT_BLOCK,
            commandId = "today-command-1",
            issuedAtEpochMillis = 1_000L,
        )

        assertEquals(StudyFlowTvTodayWorkspace.blockCount, focusItems.size)
        assertEquals("today-block-next_study_block", StudyFlowTvFocusPolicy.initialFocusId(focusItems))
        assertTrue(focusItems.all { it.role == StudyFlowTvFocusRole.CONTENT_CARD })
        assertEquals(StudyFlowTvSurface.STUDY_SESSION, command.surface)
        assertEquals(StudyFlowTvControlAction.OPEN, command.action)
        assertEquals("session", command.targetId)
        assertTrue(command.requiresUserPresence)
    }
}
