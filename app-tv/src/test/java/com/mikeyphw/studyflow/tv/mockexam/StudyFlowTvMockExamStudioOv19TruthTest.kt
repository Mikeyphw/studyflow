package com.mikeyphw.studyflow.tv.mockexam

import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvControlAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowTvMockExamStudioOv19TruthTest {
    @Test
    fun pauseResumeAndSubmitCommandsAreCanonicalExamScopedMutations() {
        val running = StudyFlowTvMockExamStudio.defaultState().copy(
            status = StudyFlowTvMockExamStatus.RUNNING,
            elapsedMinutes = 10,
            projectedAtEpochMillis = 60_000L,
        )
        val pause = StudyFlowTvMockExamStudio.commandForAction(
            action = StudyFlowTvMockExamAction.PAUSE,
            state = running,
            commandId = "ov19-pause",
            issuedAtEpochMillis = 90_000L,
        )
        val submit = StudyFlowTvMockExamStudio.commandForAction(
            action = StudyFlowTvMockExamAction.SUBMIT,
            state = running,
            commandId = "ov19-submit",
            issuedAtEpochMillis = 91_000L,
        )

        assertEquals(StudyFlowTvControlAction.PAUSE, pause.action)
        assertEquals(running.examId, pause.targetId)
        assertEquals(StudyFlowTvControlAction.SUBMIT, submit.action)
        assertEquals(running.examId, submit.targetId)
        assertEquals(12, StudyFlowTvMockExamStudio.liveElapsedMinutes(running, 180_000L))
    }

    @Test
    fun nonPersistableMockProjectionDoesNotOfferFakeInteractiveMutations() {
        val submitted = StudyFlowTvMockExamStudio.defaultState().copy(
            status = StudyFlowTvMockExamStatus.SUBMITTED,
            canPersistActions = false,
        )
        val actions = StudyFlowTvMockExamStudio.availableActions(submitted)

        assertEquals(listOf(StudyFlowTvMockExamAction.RETURN_HOME), actions)
        assertFalse(actions.contains(StudyFlowTvMockExamAction.PAUSE))
        assertFalse(actions.contains(StudyFlowTvMockExamAction.SUBMIT))
        assertTrue(StudyFlowTvMockExamStudio.resultSummary(submitted).contains("answered"))
    }
}
