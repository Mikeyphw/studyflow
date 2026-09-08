package com.mikeyphw.studyflow.tv.home

import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvControlAction
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvSurface
import com.mikeyphw.studyflow.tv.focus.StudyFlowTvFocusPolicy
import com.mikeyphw.studyflow.tv.focus.StudyFlowTvFocusRole
import com.mikeyphw.studyflow.tv.navigation.StudyFlowTvDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowTvHomeSurfaceTest {
    @Test
    fun homeSurfaceOwnsTheDefaultTvEntryPoint() {
        assertEquals("TVOV06", StudyFlowTvHomeSurface.overlay)
        assertTrue(StudyFlowTvHomeSurface.ownsDefaultTvDestination)
        assertTrue(StudyFlowTvHomeSurface.structuredHomeCards)
        assertTrue(StudyFlowTvHomeSurface.phonePairingEntryVisible)
        assertTrue(StudyFlowTvHomeSurface.usesSharedNavigationDestinations)
        assertTrue(StudyFlowTvHomeSurface.usesSharedControlProtocol)
        assertEquals(StudyFlowTvDestination.HOME, StudyFlowTvHomeSurface.defaultDestination())
    }

    @Test
    fun cardsAreStableUniqueAndRouteToTvDestinations() {
        val cards = StudyFlowTvHomeSurface.cards

        assertEquals(StudyFlowTvHomeSurface.cardCount, cards.size)
        assertEquals(cards.size, cards.map { it.id }.toSet().size)
        assertEquals(StudyFlowTvDestination.TODAY, cards.first().destination)
        assertTrue(cards.map { it.destination }.contains(StudyFlowTvDestination.PRACTICE))
        assertTrue(cards.map { it.destination }.contains(StudyFlowTvDestination.REVIEWS))
        assertTrue(cards.map { it.destination }.contains(StudyFlowTvDestination.MOCK_EXAM))
    }

    @Test
    fun homeFocusItemsUseCardsAndPrimaryActionsWithoutWrappingAssumptions() {
        val focusItems = StudyFlowTvHomeSurface.focusItems()

        assertEquals(StudyFlowTvHomeSurface.cardCount + StudyFlowTvHomeSurface.primaryActionCount, focusItems.size)
        assertEquals("home-card-today", StudyFlowTvFocusPolicy.initialFocusId(focusItems))
        assertEquals(StudyFlowTvFocusRole.CONTENT_CARD, focusItems.first().role)
        assertEquals(StudyFlowTvFocusRole.PRIMARY_ACTION, focusItems.last().role)
    }

    @Test
    fun primaryActionsMapToStudyFlowControlCommands() {
        val continueCommand = StudyFlowTvHomeSurface.commandForAction(
            action = StudyFlowTvHomeAction.CONTINUE_STUDYING,
            commandId = "home-command-1",
            issuedAtEpochMillis = 1_000L,
        )
        val pairCommand = StudyFlowTvHomeSurface.commandForAction(
            action = StudyFlowTvHomeAction.PAIR_PHONE,
            commandId = "home-command-2",
            issuedAtEpochMillis = 2_000L,
        )

        assertEquals(StudyFlowTvSurface.STUDY_SESSION, continueCommand.surface)
        assertEquals(StudyFlowTvControlAction.OPEN, continueCommand.action)
        assertEquals("session", continueCommand.targetId)
        assertEquals(StudyFlowTvSurface.SETTINGS, pairCommand.surface)
        assertEquals("manage-pairing", pairCommand.targetId)
        assertTrue(pairCommand.requiresUserPresence)
    }
}
