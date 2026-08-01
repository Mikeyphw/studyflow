package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.Question
import com.mikeyphw.studyflow.core.model.QuestionId
import com.mikeyphw.studyflow.core.model.QuestionType
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyTest
import com.mikeyphw.studyflow.core.model.StudyTestId
import com.mikeyphw.studyflow.core.model.TestMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PracticeExecutionEngineTest {
    private val planId = StudyPlanId("plan")
    private val question = question("q1", "B")

    @Test
    fun answerRecordsCorrectnessAndRoundTripsConfiguration() {
        val engine = PracticeExecutionEngine(clock = { 42L })
        val test = test("questionIds=q1")
        val state = engine.stateForTest(test, listOf(question))
        val answered = engine.answer(state, question, "b", elapsedSeconds = 12)
        val encoded = PracticeAttemptCodec.encode(answered)
        val decoded = PracticeAttemptCodec.decode(
            attemptId = test.id.value,
            title = test.title,
            questionCount = 1,
            configuration = encoded,
            questions = listOf(question),
            startedAtEpochMillis = 1,
            submittedAtEpochMillis = null,
            timeLimitSeconds = null,
        )

        assertEquals(1, decoded.answeredCount)
        assertEquals(1, decoded.correctCount)
        assertEquals("b", decoded.answers.single().chosenAnswer)
    }

    @Test
    fun finishSummarizesAnsweredQuestions() {
        val engine = PracticeExecutionEngine(clock = { 100L })
        val state = engine.answer(
            state = engine.stateForTest(test("questionIds=q1"), listOf(question)),
            question = question,
            chosenAnswer = "A",
            elapsedSeconds = 5,
        )
        val summary = engine.finish(state)

        assertEquals(1, summary.answeredCount)
        assertEquals(0, summary.correctCount)
        assertEquals(0, summary.scorePercent)
        assertFalse(state.submitted)
    }

    private fun test(configuration: String) = StudyTest(
        id = StudyTestId("test"),
        studyPlanId = planId,
        title = "Attempt",
        mode = TestMode.PRACTICE,
        timeLimitSeconds = null,
        questionCount = 1,
        answeredCount = 0,
        correctCount = 0,
        startedAtEpochMillis = 1,
        submittedAtEpochMillis = null,
        configurationJson = configuration,
    )

    private fun question(id: String, correctAnswer: String) = Question(
        id = QuestionId(id),
        studyPlanId = planId,
        subjectId = null,
        topicId = null,
        type = QuestionType.MULTIPLE_CHOICE,
        statement = "Question",
        options = listOf("A", "B"),
        correctAnswer = correctAnswer,
        explanation = "Because.",
        examBoard = null,
        source = null,
        year = null,
        difficulty = 3,
        favorite = false,
        disputed = false,
        tags = emptyList(),
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = 1,
    )
}
