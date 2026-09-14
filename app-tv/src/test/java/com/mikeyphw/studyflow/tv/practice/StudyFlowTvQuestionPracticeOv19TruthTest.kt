package com.mikeyphw.studyflow.tv.practice

import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvControlAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowTvQuestionPracticeOv19TruthTest {
    @Test
    fun allTwentyProjectedPracticeQuestionsAreReachableAndCommandsUseCanonicalSourceIds() {
        var state = StudyFlowTvQuestionPractice.defaultState()

        repeat(StudyFlowTvQuestionPractice.questionCount - 1) {
            state = StudyFlowTvQuestionPractice.applyAction(state, StudyFlowTvPracticeAction.NEXT_QUESTION)
        }
        val command = StudyFlowTvQuestionPractice.commandForAction(
            action = StudyFlowTvPracticeAction.SELECT_A,
            state = state,
            commandId = "ov19-practice-answer",
            issuedAtEpochMillis = 1_000L,
        )

        assertEquals(20, StudyFlowTvQuestionPractice.questionCount)
        assertEquals(StudyFlowTvPracticeQuestionId.QUESTION_TWENTY, state.activeQuestion.id)
        assertEquals("tv-practice-question-20", command.targetId)
        assertEquals(StudyFlowTvControlAction.SELECT_ANSWER, command.action)
    }

    @Test
    fun nonPersistablePracticeSetsDoNotExposeSyntheticMutationActions() {
        val state = StudyFlowTvQuestionPractice.defaultState().copy(
            canPersistActions = false,
            availabilityReason = "Practice actions require an open canonical attempt.",
        )
        val actions = StudyFlowTvQuestionPractice.availableActions(state)

        assertFalse(actions.contains(StudyFlowTvPracticeAction.SELECT_A))
        assertFalse(actions.contains(StudyFlowTvPracticeAction.SELECT_B))
        assertFalse(actions.contains(StudyFlowTvPracticeAction.SELECT_C))
        assertFalse(actions.contains(StudyFlowTvPracticeAction.SELECT_D))
        assertFalse(actions.contains(StudyFlowTvPracticeAction.FINISH_SET))
        assertTrue(actions.contains(StudyFlowTvPracticeAction.NEXT_QUESTION))
    }
}
