package com.mikeyphw.studyflow.tv.reviews

import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvControlAction
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvSurface
import com.mikeyphw.studyflow.tv.focus.StudyFlowTvFocusPolicy
import com.mikeyphw.studyflow.tv.focus.StudyFlowTvFocusRole
import com.mikeyphw.studyflow.tv.navigation.StudyFlowTvDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowTvReviewNotebookTest {
    @Test
    fun reviewNotebookOwnsSpacedReviewAndErrorNotebookRoadmapContract() {
        assertEquals("TVOV11", StudyFlowTvReviewNotebook.introducedOverlay)
        assertEquals("TVOV29", StudyFlowTvReviewNotebook.overlay)
        assertTrue(StudyFlowTvReviewNotebook.ownsReviewAndErrorNotebookSurface)
        assertTrue(StudyFlowTvReviewNotebook.spacedReviewQueueVisible)
        assertTrue(StudyFlowTvReviewNotebook.errorNotebookCanBeOpenedFromReview)
        assertTrue(StudyFlowTvReviewNotebook.remoteCanReviewMarkKnownExplainAndComplete)
        assertTrue(StudyFlowTvReviewNotebook.pairedPhoneCanControlReviewNotebook)
        assertTrue(StudyFlowTvReviewNotebook.emitsCastStateWithoutMirroring)
        assertTrue(StudyFlowTvReviewNotebook.usesSharedControlProtocol)
    }

    @Test
    fun defaultReviewNotebookContainsDueItemsConfidenceAndReasons() {
        val state = StudyFlowTvReviewNotebook.defaultState()

        assertEquals(StudyFlowTvReviewStatus.READY, state.status)
        assertEquals(StudyFlowTvReviewNotebook.reviewItemCount, state.items.size)
        assertEquals(2, state.dueCount)
        assertTrue(state.activeReview.reason.isNotBlank())
        assertTrue(state.activeReview.confidencePercent in 0..100)
    }

    @Test
    fun reviewEvidenceMutationsWaitForCanonicalProjection() {
        val ready = StudyFlowTvReviewNotebook.defaultState()
        val reviewing = StudyFlowTvReviewNotebook.applyAction(ready, StudyFlowTvReviewAction.START_REVIEW)
        val explained = StudyFlowTvReviewNotebook.applyAction(reviewing, StudyFlowTvReviewAction.SHOW_EXPLANATION)
        val errorNoted = StudyFlowTvReviewNotebook.applyAction(explained, StudyFlowTvReviewAction.ADD_ERROR_NOTE)
        val next = StudyFlowTvReviewNotebook.applyAction(explained, StudyFlowTvReviewAction.NEXT_ITEM)
        val known = StudyFlowTvReviewNotebook.applyAction(next, StudyFlowTvReviewAction.MARK_KNOWN)
        val completed = StudyFlowTvReviewNotebook.applyAction(next, StudyFlowTvReviewAction.COMPLETE_REVIEW)

        assertEquals(StudyFlowTvReviewStatus.REVIEWING, reviewing.status)
        assertTrue(explained.explanationVisible)
        assertEquals(explained, errorNoted)
        assertEquals(1, next.activeReviewIndex)
        assertEquals(next, known)
        assertEquals(next, completed)
    }

    @Test
    fun reviewNotebookFocusCommandsCastStateAndDestinationMappingUseSharedProtocols() {
        val reviewing = StudyFlowTvReviewNotebook.applyAction(
            StudyFlowTvReviewNotebook.defaultState(),
            StudyFlowTvReviewAction.START_REVIEW,
        )
        val focusItems = StudyFlowTvReviewNotebook.focusItems(reviewing)
        val command = StudyFlowTvReviewNotebook.commandForAction(
            action = StudyFlowTvReviewAction.ADD_ERROR_NOTE,
            state = reviewing,
            commandId = "review-command-1",
            issuedAtEpochMillis = 8_000L,
        )
        val castState = StudyFlowTvReviewNotebook.castStateFor(
            state = reviewing,
            castId = "review-cast-1",
            updatedAtEpochMillis = 9_000L,
        )

        assertEquals("review-item-constitution_flashcards", StudyFlowTvFocusPolicy.initialFocusId(focusItems))
        assertTrue(focusItems.any { it.role == StudyFlowTvFocusRole.CONTENT_CARD })
        assertTrue(focusItems.any { it.role == StudyFlowTvFocusRole.DIALOG_ACTION })
        assertEquals(StudyFlowTvSurface.REVIEW, command.surface)
        assertEquals(StudyFlowTvControlAction.MARK_FOR_REVIEW, command.action)
        assertEquals(StudyFlowTvSurface.REVIEW, castState.surface)
        assertEquals(reviewing.progressPercent, castState.progressPercent ?: -1)
        assertEquals(StudyFlowTvDestination.TODAY, StudyFlowTvReviewNotebook.destinationForAction(StudyFlowTvReviewAction.RETURN_TODAY))
    }
}
