package com.mikeyphw.studyflow.feature.practice

import com.mikeyphw.studyflow.core.model.Question
import com.mikeyphw.studyflow.core.model.QuestionId
import com.mikeyphw.studyflow.core.model.QuestionType
import com.mikeyphw.studyflow.core.model.StudyPlanId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Ov15PracticeHonestyContractsTest {
    @Test
    fun optionlessQuestionsDoNotExposeCorrectAnswerAsOnlyButton() {
        val shortAnswer = question(type = QuestionType.SHORT_ANSWER, options = emptyList(), correctAnswer = "42")

        assertTrue(questionRequiresTextAnswer(shortAnswer))
        assertEquals(emptyList<String>(), questionAnswerChoices(shortAnswer))
    }

    @Test
    fun manualEditorValidatesMultipleChoiceCorrectAnswerAgainstOptions() {
        val invalid = QuestionEditorState(
            statement = "Capital?",
            optionA = "Paris",
            optionB = "Berlin",
            correctAnswer = "Rome",
            type = QuestionType.MULTIPLE_CHOICE,
        )

        assertFalse(invalid.canSave)
        assertTrue(
            QuestionEditorValidationIssue.MULTIPLE_CHOICE_ANSWER_NOT_IN_OPTIONS in invalid.validation().issues,
        )
    }

    @Test
    fun manualEditorRepresentsEverySupportedQuestionType() {
        val base = QuestionEditorState(statement = "Prompt", correctAnswer = "True")

        QuestionType.entries.forEach { type ->
            val state = when (type) {
                QuestionType.MULTIPLE_CHOICE -> base.copy(
                    type = type,
                    optionA = "True",
                    optionB = "False",
                )
                QuestionType.TRUE_FALSE -> base.copy(type = type, correctAnswer = "False")
                QuestionType.SHORT_ANSWER,
                QuestionType.ESSAY,
                -> base.copy(type = type, correctAnswer = "Answer")
            }
            assertTrue("$type should be saveable", state.canSave)
        }
    }

    private fun question(
        type: QuestionType,
        options: List<String>,
        correctAnswer: String,
    ) = Question(
        id = QuestionId("q-${type.name}"),
        studyPlanId = StudyPlanId("plan"),
        subjectId = null,
        topicId = null,
        type = type,
        statement = "Question",
        options = options,
        correctAnswer = correctAnswer,
        explanation = "",
        examBoard = null,
        source = null,
        year = null,
        difficulty = 3,
        favorite = false,
        disputed = false,
        tags = emptyList(),
        createdAtEpochMillis = 0L,
        updatedAtEpochMillis = 0L,
    )
}
