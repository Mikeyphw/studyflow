package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.Question
import com.mikeyphw.studyflow.core.model.QuestionId
import com.mikeyphw.studyflow.core.model.QuestionType
import com.mikeyphw.studyflow.core.model.StudyPlanId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.mikeyphw.studyflow.core.model.MockExam
import com.mikeyphw.studyflow.core.model.MockExamId
import com.mikeyphw.studyflow.core.model.StudyTest
import com.mikeyphw.studyflow.core.model.StudyTestId
import com.mikeyphw.studyflow.core.model.TestMode

class PracticeExecutionEngineOv19MockPauseTest {
    private val planId = StudyPlanId("plan")
    private val mockId = MockExamId("mock")
    private val question = Question(
        id = QuestionId("q1"),
        studyPlanId = planId,
        subjectId = null,
        topicId = null,
        type = QuestionType.MULTIPLE_CHOICE,
        statement = "Question",
        options = listOf("A", "B"),
        correctAnswer = "A",
        explanation = "Because",
        examBoard = null,
        source = null,
        year = null,
        difficulty = 1,
        favorite = false,
        disputed = false,
        tags = emptyList(),
        createdAtEpochMillis = 0L,
        updatedAtEpochMillis = 0L,
    )

    @Test
    fun mockPauseAndResumeRoundTripThroughAttemptMetadata() = runBlocking {
        val repository = MemoryPracticeRepository(mock())
        val execution = ManagePracticeExecution(repository, clock = { 5_000L })

        val paused = execution.pauseMockExam(planId, mockId)
        assertEquals(5_000L, PracticeAttemptCodec.tvMockPausedAtEpochMillis(paused.configurationJson))

        val resumed = ManagePracticeExecution(repository, clock = { 6_000L }).resumeMockExam(planId, mockId)
        assertNull(PracticeAttemptCodec.tvMockPausedAtEpochMillis(resumed.configurationJson))
        assertTrue(resumed.configurationJson.contains("questionIds=q1"))
    }

    private fun mock() = MockExam(
        id = mockId,
        studyPlanId = planId,
        title = "Mock",
        mode = TestMode.EXAM,
        timeLimitSeconds = 3_600L,
        questionCount = 1,
        answeredCount = 0,
        correctCount = 0,
        scorePercent = null,
        negativeMarking = 0.0,
        minimumScorePercent = null,
        startedAtEpochMillis = 1_000L,
        submittedAtEpochMillis = null,
        configurationJson = PracticeAttemptCodec.configurationForQuestions(listOf(question)),
    )

    private class MemoryPracticeRepository(initialMock: MockExam) : StudyPracticeRepository {
        private var mockExam = initialMock
        override fun observeQuestions(planId: StudyPlanId): Flow<List<Question>> = flowOf(emptyList())
        override fun observeTests(planId: StudyPlanId): Flow<List<StudyTest>> = flowOf(emptyList())
        override fun observeMockExams(planId: StudyPlanId): Flow<List<MockExam>> = flowOf(listOf(mockExam))
        override suspend fun findQuestion(planId: StudyPlanId, questionId: QuestionId): Question? = null
        override suspend fun findTest(planId: StudyPlanId, testId: StudyTestId): StudyTest? = null
        override suspend fun findMockExam(planId: StudyPlanId, mockExamId: MockExamId): MockExam? = mockExam
        override suspend fun upsertQuestion(question: Question): Question = question
        override suspend fun importQuestionsAtomically(questions: List<Question>): List<Question> = questions
        override suspend fun deleteQuestion(planId: StudyPlanId, questionId: QuestionId) = Unit
        override suspend fun upsertTest(test: StudyTest): StudyTest = test
        override suspend fun upsertMockExam(mockExam: MockExam): MockExam { this.mockExam = mockExam; return mockExam }
        override suspend fun startTestIfNotStarted(
            planId: StudyPlanId,
            testId: StudyTestId,
            startedAtEpochMillis: Long,
        ): StudyTest = error("not used")
        override suspend fun updateTestAttemptIfOpen(expectedConfiguration: String, next: StudyTest): StudyTest = next
        override suspend fun submitTestIfOpen(
            expectedConfiguration: String,
            next: StudyTest,
            wrongAnswers: List<PracticeWrongAnswerCapture>,
        ): StudyTest = next
        override suspend fun startMockExamIfNotStarted(
            planId: StudyPlanId,
            mockExamId: MockExamId,
            startedAtEpochMillis: Long,
        ): MockExam = mockExam
        override suspend fun updateMockExamAttemptIfOpen(expectedConfiguration: String, next: MockExam): MockExam {
            check(expectedConfiguration == mockExam.configurationJson)
            mockExam = next
            return next
        }
        override suspend fun submitMockExamIfOpen(
            expectedConfiguration: String,
            next: MockExam,
            wrongAnswers: List<PracticeWrongAnswerCapture>,
        ): MockExam = updateMockExamAttemptIfOpen(expectedConfiguration, next)
    }
}
