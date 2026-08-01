package com.mikeyphw.studyflow.feature.practice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeUiModelsTest {
    @Test
    fun editorRequiresQuestionBodyAnswerAndTwoMultipleChoiceOptions() {
        assertFalse(QuestionEditorState(statement = "Q", correctAnswer = "A", optionA = "A").canSave)
        assertTrue(
            QuestionEditorState(
                statement = "Q",
                correctAnswer = "A",
                optionA = "A",
                optionB = "B",
            ).canSave,
        )
    }

    @Test
    fun editorNormalizesTags() {
        val state = QuestionEditorState(tags = "math, math; algebra ")
        assertEquals(listOf("math", "algebra"), state.tagList)
    }
}
