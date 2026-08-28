package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.ErrorCategory
import com.mikeyphw.studyflow.core.model.MockExam
import com.mikeyphw.studyflow.core.model.MockExamId
import com.mikeyphw.studyflow.core.model.Question
import com.mikeyphw.studyflow.core.model.QuestionId
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyTest
import com.mikeyphw.studyflow.core.model.StudyTestId
import kotlinx.coroutines.flow.Flow

/** One wrong answer that must be committed with terminal attempt submission. */
data class PracticeWrongAnswerCapture(
    val question: Question,
    val chosenAnswer: String?,
    val category: ErrorCategory = ErrorCategory.OTHER,
)

interface StudyPracticeRepository {
    fun observeQuestions(planId: StudyPlanId): Flow<List<Question>>
    fun observeTests(planId: StudyPlanId): Flow<List<StudyTest>>
    fun observeMockExams(planId: StudyPlanId): Flow<List<MockExam>>

    suspend fun findQuestion(planId: StudyPlanId, questionId: QuestionId): Question?
    suspend fun findTest(planId: StudyPlanId, testId: StudyTestId): StudyTest?
    suspend fun findMockExam(planId: StudyPlanId, mockExamId: MockExamId): MockExam?

    suspend fun upsertQuestion(question: Question): Question
    suspend fun importQuestionsAtomically(questions: List<Question>): List<Question>

    /** Refuses hard deletion while any retained StudyTest/MockExam configuration references the question. */
    suspend fun deleteQuestion(planId: StudyPlanId, questionId: QuestionId)

    suspend fun upsertTest(test: StudyTest): StudyTest

    /** Atomically joins an existing open weakness drill for [key],
        or creates [test] without reusing submitted history. */
    suspend fun createWeaknessDrillIfAbsent(test: StudyTest, key: String): StudyTest = upsertTest(test)

    suspend fun upsertMockExam(mockExam: MockExam): MockExam

    suspend fun startTestIfNotStarted(planId: StudyPlanId, testId: StudyTestId, startedAtEpochMillis: Long): StudyTest
    suspend fun updateTestAttemptIfOpen(expectedConfiguration: String, next: StudyTest): StudyTest
    suspend fun submitTestIfOpen(
        expectedConfiguration: String,
        next: StudyTest,
        wrongAnswers: List<PracticeWrongAnswerCapture>,
    ): StudyTest

    suspend fun startMockExamIfNotStarted(
        planId: StudyPlanId,
        mockExamId: MockExamId,
        startedAtEpochMillis: Long,
    ): MockExam
    suspend fun updateMockExamAttemptIfOpen(expectedConfiguration: String, next: MockExam): MockExam
    suspend fun submitMockExamIfOpen(
        expectedConfiguration: String,
        next: MockExam,
        wrongAnswers: List<PracticeWrongAnswerCapture>,
    ): MockExam
}
