package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.MockExam
import com.mikeyphw.studyflow.core.model.MockExamId
import com.mikeyphw.studyflow.core.model.Question
import com.mikeyphw.studyflow.core.model.QuestionId
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyTest
import com.mikeyphw.studyflow.core.model.StudyTestId
import kotlinx.coroutines.flow.Flow

interface StudyPracticeRepository {
    fun observeQuestions(planId: StudyPlanId): Flow<List<Question>>

    fun observeTests(planId: StudyPlanId): Flow<List<StudyTest>>

    fun observeMockExams(planId: StudyPlanId): Flow<List<MockExam>>

    suspend fun findQuestion(planId: StudyPlanId, questionId: QuestionId): Question?

    suspend fun findTest(planId: StudyPlanId, testId: StudyTestId): StudyTest?

    suspend fun findMockExam(planId: StudyPlanId, mockExamId: MockExamId): MockExam?

    suspend fun upsertQuestion(question: Question): Question

    suspend fun deleteQuestion(planId: StudyPlanId, questionId: QuestionId)

    suspend fun upsertTest(test: StudyTest): StudyTest

    suspend fun upsertMockExam(mockExam: MockExam): MockExam
}
