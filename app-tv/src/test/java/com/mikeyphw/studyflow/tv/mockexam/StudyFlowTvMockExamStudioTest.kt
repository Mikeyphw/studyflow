package com.mikeyphw.studyflow.tv.mockexam

import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvControlAction
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvSurface
import com.mikeyphw.studyflow.tv.focus.StudyFlowTvFocusPolicy
import com.mikeyphw.studyflow.tv.focus.StudyFlowTvFocusRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowTvMockExamStudioTest {
    @Test
    fun mockExamOwnsTimedSectionsRemoteControlsAndPhoneControl() {
        assertEquals("TVOV10", StudyFlowTvMockExamStudio.overlay)
        assertTrue(StudyFlowTvMockExamStudio.ownsMockExamSurface)
        assertTrue(StudyFlowTvMockExamStudio.timedSectionsVisible)
        assertTrue(StudyFlowTvMockExamStudio.remoteCanStartPauseSubmitAndReview)
        assertTrue(StudyFlowTvMockExamStudio.pairedPhoneCanControlMockExam)
        assertTrue(StudyFlowTvMockExamStudio.emitsCastStateWithoutMirroring)
        assertTrue(StudyFlowTvMockExamStudio.usesSharedControlProtocol)
    }

    @Test
    fun defaultMockExamContainsSectionsTimeAndQuestions() {
        val state = StudyFlowTvMockExamStudio.defaultState()

        assertEquals(StudyFlowTvMockExamStatus.READY, state.status)
        assertEquals(StudyFlowTvMockExamStudio.sectionCount, state.sections.size)
        assertTrue(state.totalQuestions > 0)
        assertTrue(state.totalMinutes > 0)
        assertEquals(0, state.progressPercent)
    }

    @Test
    fun canonicalMockLifecycleAndScoringAreNeverFabricatedOnTv() {
        val ready = StudyFlowTvMockExamStudio.defaultState()
        val started = StudyFlowTvMockExamStudio.applyAction(ready, StudyFlowTvMockExamAction.START)
        val marked = StudyFlowTvMockExamStudio.applyAction(ready, StudyFlowTvMockExamAction.MARK_FOR_REVIEW)
        val advanced = StudyFlowTvMockExamStudio.applyAction(ready, StudyFlowTvMockExamAction.NEXT_SECTION)
        val submitted = StudyFlowTvMockExamStudio.applyAction(advanced, StudyFlowTvMockExamAction.SUBMIT)

        assertEquals(ready, started)
        assertEquals(ready, marked)
        assertEquals(1, advanced.activeSectionIndex)
        assertEquals(0, advanced.elapsedMinutes)
        assertEquals(advanced, submitted)
        assertEquals(0, submitted.correctAnswerCount)
    }

    @Test
    fun mockExamFocusCommandsAndCastStateUseSharedProtocols() {
        val running = StudyFlowTvMockExamStudio.defaultState().copy(status = StudyFlowTvMockExamStatus.RUNNING)
        val focusItems = StudyFlowTvMockExamStudio.focusItems(running)
        val command = StudyFlowTvMockExamStudio.commandForAction(
            action = StudyFlowTvMockExamAction.MARK_FOR_REVIEW,
            state = running,
            commandId = "mock-command-1",
            issuedAtEpochMillis = 6_000L,
        )
        val castState = StudyFlowTvMockExamStudio.castStateFor(
            state = running,
            castId = "mock-cast-1",
            updatedAtEpochMillis = 7_000L,
        )

        assertEquals("mock-section-general_knowledge", StudyFlowTvFocusPolicy.initialFocusId(focusItems))
        assertTrue(focusItems.any { it.role == StudyFlowTvFocusRole.CONTENT_CARD })
        assertTrue(focusItems.any { it.role == StudyFlowTvFocusRole.PRIMARY_ACTION })
        assertEquals(StudyFlowTvSurface.MOCK_EXAM, command.surface)
        assertEquals(StudyFlowTvControlAction.MARK_FOR_REVIEW, command.action)
        assertEquals(StudyFlowTvSurface.MOCK_EXAM, castState.surface)
        assertEquals(running.progressPercent, castState.progressPercent ?: -1)
        val projectedSubmitted = running.copy(status = StudyFlowTvMockExamStatus.SUBMITTED, correctAnswerCount = 7)
        assertTrue(StudyFlowTvMockExamStudio.resultSummary(projectedSubmitted).contains("7/"))
    }
}
