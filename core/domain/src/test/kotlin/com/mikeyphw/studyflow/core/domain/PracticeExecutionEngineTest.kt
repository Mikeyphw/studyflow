package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.ErrorCategory
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
        val test = test(PracticeAttemptCodec.configurationForQuestions(listOf(question)))
        val state = engine.stateForTest(test, listOf(question))
        val answered = engine.answer(state, question, "b", elapsedSeconds = 12)
        val encoded = PracticeAttemptCodec.encode(answered)
        val decoded = PracticeAttemptCodec.decode(
            attemptId = test.id.value,
            studyPlanId = test.studyPlanId,
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
    fun wrongAnswerClassificationRoundTripsAndDefaultsTruthfully() {
        val engine = PracticeExecutionEngine(clock = { 43L })
        val base = engine.stateForTest(test(PracticeAttemptCodec.configurationForQuestions(listOf(question))), listOf(question))
        val classified = engine.answer(
            base, question, "A", elapsedSeconds = 9, errorCategory = ErrorCategory.INTERPRETATION,
        )
        val decoded = PracticeAttemptCodec.decode(
            attemptId = "test", studyPlanId = planId, title = "Attempt", questionCount = 1,
            configuration = PracticeAttemptCodec.encode(classified), questions = listOf(question),
            startedAtEpochMillis = 1, submittedAtEpochMillis = null, timeLimitSeconds = null,
        )
        assertEquals(ErrorCategory.INTERPRETATION, decoded.answers.single().errorCategory)
        val unclassified = engine.answer(base, question, "A", elapsedSeconds = 10)
        assertEquals(ErrorCategory.OTHER, unclassified.answers.single().errorCategory)
    }

    @Test
    fun finishSummarizesAnsweredQuestions() {
        val engine = PracticeExecutionEngine(clock = { 100L })
        val state = engine.answer(
            state = engine.stateForTest(test(PracticeAttemptCodec.configurationForQuestions(listOf(question))), listOf(question)),
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

    @Test
    fun examModeHidesExplanationUntilTerminalSubmission() {
        val engine = PracticeExecutionEngine(clock = { 50L })
        val initial = engine.stateForTest(test(PracticeAttemptCodec.configurationForQuestions(listOf(question))).copy(mode = TestMode.EXAM), listOf(question))
        val answered = engine.answer(initial, question, "B", elapsedSeconds = 7)

        assertFalse(answered.explanationVisible(question.id))
        assertEquals(7L, answered.answers.single().elapsedSeconds)
        assertFalse(initial.explanationVisible(question.id))
        assertEquals(true, answered.copy(submittedAtEpochMillis = 50L).explanationVisible(question.id))
    }

    @Test
    fun scoringUsesTotalQuestionDenominatorAndNegativeMarking() {
        val engine = PracticeExecutionEngine(clock = { 100L })
        val second = question("q2", "A")
        val base = PracticeAttemptCodec.decode(
            attemptId = "mock",
            studyPlanId = planId,
            title = "Mock",
            mode = TestMode.EXAM,
            questionCount = 2,
            configuration = PracticeAttemptCodec.configurationForQuestions(listOf(question, second)),
            questions = listOf(question, second),
            startedAtEpochMillis = 1L,
            submittedAtEpochMillis = null,
            timeLimitSeconds = null,
            negativeMarking = 0.25,
            minimumScorePercent = 40,
        )
        val oneCorrect = engine.answer(base, question, "B", elapsedSeconds = 4)
        val oneCorrectOneWrong = engine.answer(oneCorrect, second, "B", elapsedSeconds = 5)
        val summary = engine.finish(oneCorrectOneWrong)

        assertEquals(2, summary.totalCount)
        assertEquals(38, summary.scorePercent)
        assertEquals(false, summary.passed)
    }


    @Test
    fun frozenAttemptGradesAgainstCreationSnapshotAfterQuestionMutation() {
        val engine = PracticeExecutionEngine(clock = { 77L })
        val frozenConfiguration = PracticeAttemptCodec.configurationForQuestions(listOf(question))
        val mutated = question.copy(correctAnswer = "A", updatedAtEpochMillis = 99L)
        val state = engine.stateForTest(test(frozenConfiguration), listOf(mutated))
        val answered = engine.answer(state, mutated, "B", elapsedSeconds = 3)

        assertEquals("B", state.questions.single().correctAnswer)
        assertEquals(true, answered.answers.single().correct)
        assertEquals("B", answered.answers.single().correctAnswer)
    }


    @Test
    fun frozenAttemptRemainsExecutableWhenBankQuestionIsDeleted() {
        val engine = PracticeExecutionEngine(clock = { 78L })
        val frozenConfiguration = PracticeAttemptCodec.configurationForQuestions(listOf(question))
        val state = engine.stateForTest(test(frozenConfiguration), emptyList())
        val answered = engine.answer(state, question, "B", elapsedSeconds = 4)

        assertEquals("Question", state.questions.single().statement)
        assertEquals(true, answered.answers.single().correct)
    }

    @Test
    fun malformedLegacyAttemptCannotBorrowCurrentQuestionIdentity() {
        val engine = PracticeExecutionEngine()
        val unstarted = test("").copy(startedAtEpochMillis = null)
        val result = runCatching { engine.stateForTest(unstarted, listOf(question)) }

        assertEquals(true, result.isFailure)
    }

    @Test
    fun startedLegacyAttemptWithoutSnapshotFailsClosed() {
        val engine = PracticeExecutionEngine()
        val result = runCatching { engine.stateForTest(test("questionIds=q1"), listOf(question)) }

        assertEquals(true, result.isFailure)
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
