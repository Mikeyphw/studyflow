package com.mikeyphw.studyflow.tv.navigation

import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvControlAction
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvSurface
import com.mikeyphw.studyflow.tv.focus.StudyFlowTvFocusPolicy
import com.mikeyphw.studyflow.tv.focus.StudyFlowTvFocusRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowTvNavigationShellTest {
    @Test
    fun navigationDestinationsAreStableUniqueAndHomeFirst() {
        val destinations = StudyFlowTvNavigationShell.primaryDestinations

        assertEquals("TVOV05", StudyFlowTvNavigationShell.overlay)
        assertTrue(StudyFlowTvNavigationShell.shellOwnsTopLevelNavigation)
        assertTrue(StudyFlowTvNavigationShell.remoteAndPhoneControlShareDestinations)
        assertTrue(StudyFlowTvNavigationShell.routePayloadsUseSmallEnums)
        assertEquals(StudyFlowTvNavigationShell.destinationCount, destinations.size)
        assertEquals(StudyFlowTvDestination.HOME, StudyFlowTvNavigationShell.defaultDestination())
        assertEquals(destinations.size, destinations.map { it.route }.toSet().size)
        assertEquals(destinations.size, destinations.map { it.surface }.toSet().size)
    }

    @Test
    fun navigationFocusItemsUseTheTvFocusContract() {
        val focusItems = StudyFlowTvNavigationShell.focusItems()

        assertEquals(StudyFlowTvNavigationShell.destinationCount, focusItems.size)
        assertEquals("home", StudyFlowTvFocusPolicy.initialFocusId(focusItems))
        assertTrue(focusItems.all { it.role == StudyFlowTvFocusRole.NAVIGATION_ITEM })
        assertTrue(focusItems.all { it.column == StudyFlowTvNavigationShell.navigationRailColumn })
    }

    @Test
    fun routeAndSurfaceResolutionStaySmallAndExplicit() {
        assertEquals(StudyFlowTvDestination.PROGRESS, StudyFlowTvNavigationShell.destinationForRoute("progress"))
        assertEquals(StudyFlowTvDestination.HOME, StudyFlowTvNavigationShell.destinationForRoute("unknown"))
        assertEquals(StudyFlowTvDestination.PRACTICE, StudyFlowTvNavigationShell.destinationForSurface(StudyFlowTvSurface.QUESTION_SET))
        assertEquals(StudyFlowTvDestination.MOCK_EXAM, StudyFlowTvNavigationShell.destinationForSurface(StudyFlowTvSurface.MOCK_EXAM))
        assertNull(StudyFlowTvNavigationShell.destinationForSurface(StudyFlowTvSurface.LIBRARY))
    }

    @Test
    fun pairedPhoneOpenCommandsUseTheSameDestinationModel() {
        val command = StudyFlowTvNavigationShell.openCommandFor(
            destination = StudyFlowTvDestination.SCHEDULE,
            commandId = "command-1",
            issuedAtEpochMillis = 42L,
        )

        assertEquals(StudyFlowTvSurface.SCHEDULE, command.surface)
        assertEquals(StudyFlowTvControlAction.OPEN, command.action)
        assertEquals("schedule", command.targetId)
        assertTrue(command.requiresUserPresence)
        assertNotNull(command.commandId)
    }
}
