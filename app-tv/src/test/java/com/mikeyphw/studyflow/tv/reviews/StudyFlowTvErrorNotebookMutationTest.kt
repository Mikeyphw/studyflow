package com.mikeyphw.studyflow.tv.reviews

import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvControlAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowTvErrorNotebookMutationTest {
    private fun state() = StudyFlowTvReviewState(
        status = StudyFlowTvReviewStatus.ERROR_NOTEBOOK,
        activeReviewIndex = 0,
        items = listOf(
            StudyFlowTvReviewItem(
                id = StudyFlowTvReviewId.ADMINISTRATIVE_LAW_ERRORS,
                sourceId = "review-1",
                title = "Error",
                subject = "Law",
                reason = "error notebook",
                confidencePercent = 40,
                dueNow = true,
                errorEntryId = "error-1",
                errorCategory = "OTHER",
                questionId = "question-1",
            ),
        ),
    )

    @Test
    fun errorEntryOffersReclassifyAndResolveThenLocalReopen() {
        val initial = state()
        assertTrue(StudyFlowTvReviewAction.RECLASSIFY_ERROR in StudyFlowTvReviewNotebook.availableActions(initial))
        assertTrue(StudyFlowTvReviewAction.RESOLVE_ERROR in StudyFlowTvReviewNotebook.availableActions(initial))
        val resolved = StudyFlowTvReviewNotebook.applyAction(initial, StudyFlowTvReviewAction.RESOLVE_ERROR)
        assertEquals(listOf(StudyFlowTvReviewAction.REOPEN_ERROR),
            StudyFlowTvReviewNotebook.availableActions(resolved).take(1))
    }

    @Test
    fun mutationsCarryCanonicalErrorIdAndCategory() {
        val initial = state()
        val reclassify = StudyFlowTvReviewNotebook.commandForAction(
            StudyFlowTvReviewAction.RECLASSIFY_ERROR, initial, "c1", 1L,
        )
        assertEquals(StudyFlowTvControlAction.RECLASSIFY, reclassify.action)
        assertEquals("error-1", reclassify.targetId)
        assertEquals("CONTENT_GAP", reclassify.optionId)
        val resolve = StudyFlowTvReviewNotebook.commandForAction(
            StudyFlowTvReviewAction.RESOLVE_ERROR, initial, "c2", 2L,
        )
        assertEquals(StudyFlowTvControlAction.RESOLVE, resolve.action)
        assertEquals("error-1", resolve.targetId)
    }
}
