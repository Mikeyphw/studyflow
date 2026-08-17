package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.domain.PracticeAttemptCodec
import com.mikeyphw.studyflow.core.domain.PracticeWrongAnswerCapture
import com.mikeyphw.studyflow.core.domain.StudyPracticeRepository
import com.mikeyphw.studyflow.core.model.MockExam
import com.mikeyphw.studyflow.core.model.MockExamId
import com.mikeyphw.studyflow.core.model.Question
import com.mikeyphw.studyflow.core.model.QuestionId
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyTest
import com.mikeyphw.studyflow.core.model.StudyTestId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class InMemoryStudyPracticeRepository(
    questions: List<Question>,
    tests: List<StudyTest>,
    mockExams: List<MockExam>,
) : StudyPracticeRepository {
    private val questionState = MutableStateFlow(questions)
    private val testState = MutableStateFlow(tests)
    private val mockExamState = MutableStateFlow(mockExams)

    override fun observeQuestions(planId: StudyPlanId): Flow<List<Question>> = questionState.map { questions ->
        questions.filter { question -> question.studyPlanId == planId }
    }

    override fun observeTests(planId: StudyPlanId): Flow<List<StudyTest>> = testState.map { tests ->
        tests.filter { test -> test.studyPlanId == planId }
    }

    override fun observeMockExams(planId: StudyPlanId): Flow<List<MockExam>> = mockExamState.map { mockExams ->
        mockExams.filter { mockExam -> mockExam.studyPlanId == planId }
    }

    override suspend fun findQuestion(planId: StudyPlanId, questionId: QuestionId): Question? =
        currentQuestions(planId).firstOrNull { question -> question.id == questionId }

    override suspend fun findTest(planId: StudyPlanId, testId: StudyTestId): StudyTest? =
        currentTests(planId).firstOrNull { test -> test.id == testId }

    override suspend fun findMockExam(planId: StudyPlanId, mockExamId: MockExamId): MockExam? =
        currentMockExams(planId).firstOrNull { mockExam -> mockExam.id == mockExamId }

    override suspend fun upsertQuestion(question: Question): Question {
        questionState.value = questionState.value.filterNot { it.id == question.id } + question
        return question
    }

    override suspend fun importQuestionsAtomically(questions: List<Question>): List<Question> {
        val incomingIds = questions.map(Question::id).toSet()
        questionState.value = questionState.value.filterNot { it.id in incomingIds } + questions
        return questions
    }

    override suspend fun deleteQuestion(planId: StudyPlanId, questionId: QuestionId) {
        val referenced = currentTests(planId).any { test ->
            PracticeAttemptCodec.referencedQuestionIds(test.configurationJson).contains(questionId)
        } || currentMockExams(planId).any { exam ->
            PracticeAttemptCodec.referencedQuestionIds(exam.configurationJson).contains(questionId)
        }
        require(!referenced) { "Question is still referenced by retained practice attempts." }
        questionState.value = questionState.value.filterNot { question ->
            question.studyPlanId == planId && question.id == questionId
        }
    }

    override suspend fun upsertTest(test: StudyTest): StudyTest {
        testState.value = testState.value.filterNot { it.id == test.id } + test
        return test
    }

    override suspend fun createWeaknessDrillIfAbsent(test: StudyTest, key: String): StudyTest {
        val existing = currentTests(test.studyPlanId).firstOrNull { existing ->
            existing.submittedAtEpochMillis == null && existing.configurationJson.contains("weaknessKey=$key")
        }
        return existing ?: upsertTest(test.copy(configurationJson = test.configurationJson + "\nweaknessKey=$key"))
    }

    override suspend fun upsertMockExam(mockExam: MockExam): MockExam {
        mockExamState.value = mockExamState.value.filterNot { it.id == mockExam.id } + mockExam
        return mockExam
    }

    override suspend fun startTestIfNotStarted(
        planId: StudyPlanId,
        testId: StudyTestId,
        startedAtEpochMillis: Long,
    ): StudyTest {
        val current = requireNotNull(findTest(planId, testId)) { "Practice Test does not exist." }
        return if (current.startedAtEpochMillis != null) current else upsertTest(
            current.copy(startedAtEpochMillis = startedAtEpochMillis),
        )
    }

    override suspend fun updateTestAttemptIfOpen(expectedConfiguration: String, next: StudyTest): StudyTest {
        val current = requireNotNull(findTest(next.studyPlanId, next.id)) { "Practice Test does not exist." }
        require(current.submittedAtEpochMillis == null) { "Practice Test is already submitted." }
        require(current.configurationJson == expectedConfiguration) { "Practice Test configuration changed." }
        return upsertTest(next)
    }

    override suspend fun submitTestIfOpen(
        expectedConfiguration: String,
        next: StudyTest,
        wrongAnswers: List<PracticeWrongAnswerCapture>,
    ): StudyTest = updateTestAttemptIfOpen(expectedConfiguration, next)

    override suspend fun startMockExamIfNotStarted(
        planId: StudyPlanId,
        mockExamId: MockExamId,
        startedAtEpochMillis: Long,
    ): MockExam {
        val current = requireNotNull(findMockExam(planId, mockExamId)) { "Mock Exam does not exist." }
        return if (current.startedAtEpochMillis != null) current else upsertMockExam(
            current.copy(startedAtEpochMillis = startedAtEpochMillis),
        )
    }

    override suspend fun updateMockExamAttemptIfOpen(expectedConfiguration: String, next: MockExam): MockExam {
        val current = requireNotNull(findMockExam(next.studyPlanId, next.id)) { "Mock Exam does not exist." }
        require(current.submittedAtEpochMillis == null) { "Mock Exam is already submitted." }
        require(current.configurationJson == expectedConfiguration) { "Mock Exam configuration changed." }
        return upsertMockExam(next)
    }

    override suspend fun submitMockExamIfOpen(
        expectedConfiguration: String,
        next: MockExam,
        wrongAnswers: List<PracticeWrongAnswerCapture>,
    ): MockExam = updateMockExamAttemptIfOpen(expectedConfiguration, next)

    fun currentQuestions(planId: StudyPlanId): List<Question> = questionState.value
        .filter { question -> question.studyPlanId == planId }
        .sortedByDescending(Question::updatedAtEpochMillis)

    fun currentTests(planId: StudyPlanId): List<StudyTest> = testState.value
        .filter { test -> test.studyPlanId == planId }
        .sortedByDescending { test -> test.startedAtEpochMillis ?: 0L }

    fun currentMockExams(planId: StudyPlanId): List<MockExam> = mockExamState.value
        .filter { mockExam -> mockExam.studyPlanId == planId }
        .sortedByDescending { mockExam -> mockExam.startedAtEpochMillis ?: 0L }
}
