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
    private val onChanged: (List<Question>, List<StudyTest>, List<MockExam>) -> Unit = { _, _, _ -> },
) : StudyPracticeRepository {
    private val questionState = MutableStateFlow(questions)
    private val testState = MutableStateFlow(tests)
    private val mockExamState = MutableStateFlow(mockExams)
    private var wrongAnswerSink: suspend (List<PracticeWrongAnswerCapture>) -> Unit = {}

    override fun observeQuestions(planId: StudyPlanId): Flow<List<Question>> = questionState.map { values ->
        values.filter { it.studyPlanId == planId }
    }

    override fun observeTests(planId: StudyPlanId): Flow<List<StudyTest>> = testState.map { values ->
        values.filter { it.studyPlanId == planId }
    }

    override fun observeMockExams(planId: StudyPlanId): Flow<List<MockExam>> = mockExamState.map { values ->
        values.filter { it.studyPlanId == planId }
    }

    override suspend fun findQuestion(planId: StudyPlanId, questionId: QuestionId): Question? =
        currentQuestions(planId).firstOrNull { it.id == questionId }

    override suspend fun findTest(planId: StudyPlanId, testId: StudyTestId): StudyTest? =
        currentTests(planId).firstOrNull { it.id == testId }

    override suspend fun findMockExam(planId: StudyPlanId, mockExamId: MockExamId): MockExam? =
        currentMockExams(planId).firstOrNull { it.id == mockExamId }

    override suspend fun upsertQuestion(question: Question): Question {
        questionState.value = questionState.value.filterNot { it.id == question.id } + question
        persist()
        return question
    }

    override suspend fun importQuestionsAtomically(questions: List<Question>): List<Question> {
        val incomingIds = questions.map(Question::id).toSet()
        questionState.value = questionState.value.filterNot { it.id in incomingIds } + questions
        persist()
        return questions
    }

    override suspend fun deleteQuestion(planId: StudyPlanId, questionId: QuestionId) {
        val referenced = currentTests(planId).any { test ->
            PracticeAttemptCodec.referencedQuestionIds(test.configurationJson).contains(questionId)
        } || currentMockExams(planId).any { exam ->
            PracticeAttemptCodec.referencedQuestionIds(exam.configurationJson).contains(questionId)
        }
        require(!referenced) { "Question is still referenced by retained practice attempts." }
        questionState.value = questionState.value.filterNot { it.studyPlanId == planId && it.id == questionId }
        persist()
    }

    override suspend fun upsertTest(test: StudyTest): StudyTest {
        testState.value = testState.value.filterNot { it.id == test.id } + test
        persist()
        return test
    }

    override suspend fun createWeaknessDrillIfAbsent(test: StudyTest, key: String): StudyTest {
        val existing = currentTests(test.studyPlanId).firstOrNull { current ->
            current.submittedAtEpochMillis == null && current.configurationJson.contains("weaknessKey=$key")
        }
        return existing ?: upsertTest(test.copy(configurationJson = test.configurationJson + "\nweaknessKey=$key"))
    }

    override suspend fun upsertMockExam(mockExam: MockExam): MockExam {
        mockExamState.value = mockExamState.value.filterNot { it.id == mockExam.id } + mockExam
        persist()
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
    ): StudyTest {
        val submitted = updateTestAttemptIfOpen(expectedConfiguration, next)
        if (wrongAnswers.isNotEmpty()) wrongAnswerSink(wrongAnswers)
        return submitted
    }

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
    ): MockExam {
        val submitted = updateMockExamAttemptIfOpen(expectedConfiguration, next)
        if (wrongAnswers.isNotEmpty()) wrongAnswerSink(wrongAnswers)
        return submitted
    }

    fun attachWrongAnswerSink(sink: suspend (List<PracticeWrongAnswerCapture>) -> Unit) {
        wrongAnswerSink = sink
    }

    fun purgePlan(planId: StudyPlanId) {
        questionState.value = questionState.value.filterNot { it.studyPlanId == planId }
        testState.value = testState.value.filterNot { it.studyPlanId == planId }
        mockExamState.value = mockExamState.value.filterNot { it.studyPlanId == planId }
        persist()
    }

    fun currentQuestions(planId: StudyPlanId): List<Question> = questionState.value
        .filter { it.studyPlanId == planId }
        .sortedByDescending(Question::updatedAtEpochMillis)

    fun currentTests(planId: StudyPlanId): List<StudyTest> = testState.value
        .filter { it.studyPlanId == planId }
        .sortedByDescending { it.startedAtEpochMillis ?: 0L }

    fun currentMockExams(planId: StudyPlanId): List<MockExam> = mockExamState.value
        .filter { it.studyPlanId == planId }
        .sortedByDescending { it.startedAtEpochMillis ?: 0L }

    private fun persist() = onChanged(questionState.value, testState.value, mockExamState.value)
}
