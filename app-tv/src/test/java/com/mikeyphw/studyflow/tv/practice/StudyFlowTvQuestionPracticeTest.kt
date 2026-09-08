package com.mikeyphw.studyflow.tv.practice

import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvControlAction
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvSurface
import com.mikeyphw.studyflow.tv.focus.StudyFlowTvFocusPolicy
import com.mikeyphw.studyflow.tv.focus.StudyFlowTvFocusRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowTvQuestionPracticeTest {
    @Test
    fun questionPracticeOwnsAnswerSelectionExplanationAndPhoneControl() {
        assertEquals("TVOV09", StudyFlowTvQuestionPractice.overlay)
        assertTrue(StudyFlowTvQuestionPractice.ownsQuestionPracticeSurface)
        assertTrue(StudyFlowTvQuestionPractice.remoteCanSelectAnswersAndRevealExplanation)
        assertTrue(StudyFlowTvQuestionPractice.pairedPhoneCanSelectAnswersAndNavigateQuestions)
        assertTrue(StudyFlowTvQuestionPractice.emitsCastStateWithoutMirroring)
        assertTrue(StudyFlowTvQuestionPractice.usesSharedControlProtocol)
    }

    @Test
    fun defaultPracticeSetContainsRenderableQuestionsAndChoices() {
        val state = StudyFlowTvQuestionPractice.defaultState()

        assertEquals(StudyFlowTvQuestionPractice.questionCount, state.questions.size)
        assertEquals(StudyFlowTvQuestionPractice.choiceCount, state.activeQuestion.choices.size)
        assertEquals(0, state.answeredCount)
        assertEquals(0, state.progressPercent)
        assertFalse(state.completed)
    }

    @Test
    fun answerAndFinishWaitForCanonicalProjectionWhileNavigationRemainsLocal() {
        val ready = StudyFlowTvQuestionPractice.defaultState()
        val answered = StudyFlowTvQuestionPractice.applyAction(ready, StudyFlowTvPracticeAction.SELECT_B)
        val next = StudyFlowTvQuestionPractice.applyAction(ready, StudyFlowTvPracticeAction.NEXT_QUESTION)
        val completed = StudyFlowTvQuestionPractice.applyAction(next, StudyFlowTvPracticeAction.FINISH_SET)

        assertEquals(ready, answered)
        assertEquals(1, next.activeQuestionIndex)
        assertFalse(completed.completed)
        assertEquals(0, completed.answeredCount)
    }

    @Test
    fun practiceFocusCommandsAndCastStateUseSharedProtocols() {
        val answered = StudyFlowTvQuestionPractice.applyAction(
            StudyFlowTvQuestionPractice.defaultState(),
            StudyFlowTvPracticeAction.SELECT_A,
        )
        val focusItems = StudyFlowTvQuestionPractice.focusItems(answered)
        val command = StudyFlowTvQuestionPractice.commandForAction(
            action = StudyFlowTvPracticeAction.SELECT_A,
            state = answered,
            commandId = "practice-command-1",
            issuedAtEpochMillis = 4_000L,
        )
        val castState = StudyFlowTvQuestionPractice.castStateFor(
            state = answered,
            castId = "practice-cast-1",
            updatedAtEpochMillis = 5_000L,
        )

        assertEquals("practice-choice-a", StudyFlowTvFocusPolicy.initialFocusId(focusItems))
        assertTrue(focusItems.any { it.role == StudyFlowTvFocusRole.QUESTION_CHOICE })
        assertTrue(focusItems.any { it.role == StudyFlowTvFocusRole.PRIMARY_ACTION })
        assertEquals(StudyFlowTvSurface.QUESTION_SET, command.surface)
        assertEquals(StudyFlowTvControlAction.SELECT_ANSWER, command.action)
        assertEquals("A", command.optionId)
        assertEquals(StudyFlowTvSurface.QUESTION_SET, castState.surface)
        assertEquals(answered.progressPercent, castState.progressPercent ?: -1)
    }
}
