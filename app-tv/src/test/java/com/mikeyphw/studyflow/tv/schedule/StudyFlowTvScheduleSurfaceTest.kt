package com.mikeyphw.studyflow.tv.schedule

import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvControlAction
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvSurface
import com.mikeyphw.studyflow.tv.focus.StudyFlowTvFocusPolicy
import com.mikeyphw.studyflow.tv.focus.StudyFlowTvFocusRole
import com.mikeyphw.studyflow.tv.navigation.StudyFlowTvDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowTvScheduleSurfaceTest {
    @Test
    fun scheduleOwnsRoadmapReadOnlyContract() {
        assertEquals("TVOV13", StudyFlowTvScheduleSurface.overlay)
        assertTrue(StudyFlowTvScheduleSurface.ownsReadOnlyScheduleSurface)
        assertTrue(StudyFlowTvScheduleSurface.todayAndWeekScheduleVisible)
        assertTrue(StudyFlowTvScheduleSurface.reviewWindowsAndMockExamsVisible)
        assertTrue(StudyFlowTvScheduleSurface.scheduleEditingDisabledOnTv)
        assertTrue(StudyFlowTvScheduleSurface.pairedPhoneCanNavigateScheduleButNotMutate)
        assertTrue(StudyFlowTvScheduleSurface.emitsCastStateWithoutMirroring)
        assertTrue(StudyFlowTvScheduleSurface.usesSharedControlProtocol)
    }

    @Test
    fun defaultScheduleShowsTodayWeekReviewWindowsAndMockExam() {
        val snapshot = StudyFlowTvScheduleSurface.defaultSnapshot()

        assertEquals(StudyFlowTvScheduleSurface.scheduleItemCount, snapshot.items.size)
        assertEquals(1, snapshot.reviewWindowCount)
        assertEquals(1, snapshot.mockExamCount)
        assertEquals(StudyFlowTvScheduleItemKind.REVIEW_WINDOW, snapshot.items.first().kind)
        assertEquals(StudyFlowTvDestination.REVIEWS, snapshot.items.first().destination)
        assertTrue(snapshot.weekLabel.contains("Week"))
    }

    @Test
    fun scheduleNavigationDoesNotMutatePlanData() {
        val snapshot = StudyFlowTvScheduleSurface.defaultSnapshot()
        val next = StudyFlowTvScheduleSurface.applyAction(snapshot, StudyFlowTvScheduleAction.NEXT_ITEM)
        val previous = StudyFlowTvScheduleSurface.applyAction(next, StudyFlowTvScheduleAction.PREVIOUS_ITEM)

        assertEquals(1, next.activeItemIndex)
        assertEquals(0, previous.activeItemIndex)
        assertEquals(snapshot.items, next.items)
        assertEquals(StudyFlowTvDestination.STUDY_SESSION, StudyFlowTvScheduleSurface.destinationForAction(StudyFlowTvScheduleAction.OPEN_ACTIVE_ITEM, next))
        assertEquals(StudyFlowTvDestination.MOCK_EXAM, StudyFlowTvScheduleSurface.destinationForAction(StudyFlowTvScheduleAction.OPEN_MOCK_EXAM, next))
    }

    @Test
    fun scheduleFocusCommandsAndCastStateUseSharedProtocol() {
        val snapshot = StudyFlowTvScheduleSurface.defaultSnapshot()
        val focusItems = StudyFlowTvScheduleSurface.focusItems(snapshot)
        val command = StudyFlowTvScheduleSurface.commandForAction(
            action = StudyFlowTvScheduleAction.NEXT_ITEM,
            snapshot = snapshot,
            commandId = "schedule-command-1",
            issuedAtEpochMillis = 13_000L,
        )
        val castState = StudyFlowTvScheduleSurface.castStateFor(
            snapshot = snapshot,
            castId = "schedule-cast-1",
            updatedAtEpochMillis = 14_000L,
        )

        assertEquals("schedule-item-morning_review", StudyFlowTvFocusPolicy.initialFocusId(focusItems))
        assertTrue(focusItems.any { it.role == StudyFlowTvFocusRole.CONTENT_CARD })
        assertTrue(focusItems.any { it.role == StudyFlowTvFocusRole.PRIMARY_ACTION })
        assertEquals(StudyFlowTvSurface.SCHEDULE, command.surface)
        assertEquals(StudyFlowTvControlAction.NEXT, command.action)
        assertEquals(StudyFlowTvSurface.SCHEDULE, castState.surface)
        assertEquals(snapshot.activeItem.id.name.lowercase(), castState.activeItemId)
    }
}
