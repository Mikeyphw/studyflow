package com.mikeyphw.studyflow.core.data

import androidx.room.withTransaction
import com.mikeyphw.studyflow.core.database.StudyFlowDatabase
import com.mikeyphw.studyflow.core.database.mapper.asEntity
import com.mikeyphw.studyflow.core.database.mapper.asExternalModel
import com.mikeyphw.studyflow.core.domain.StudyPracticeRepository
import com.mikeyphw.studyflow.core.model.MockExam
import com.mikeyphw.studyflow.core.model.MockExamId
import com.mikeyphw.studyflow.core.model.Question
import com.mikeyphw.studyflow.core.model.QuestionId
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyTest
import com.mikeyphw.studyflow.core.model.StudyTestId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class RoomStudyPracticeRepository(
    private val database: StudyFlowDatabase,
) : StudyPracticeRepository {
    private val practiceDao = database.studyPracticeDao()
    private val planDao = database.studyPlanDao()

    override fun observeQuestions(planId: StudyPlanId): Flow<List<Question>> =
        practiceDao.observeQuestions(planId.value).map { questions -> questions.map { it.asExternalModel() } }

    override fun observeTests(planId: StudyPlanId): Flow<List<StudyTest>> =
        practiceDao.observeTests(planId.value).map { tests -> tests.map { it.asExternalModel() } }

    override fun observeMockExams(planId: StudyPlanId): Flow<List<MockExam>> =
        practiceDao.observeMockExams(planId.value).map { exams -> exams.map { it.asExternalModel() } }

    override suspend fun findQuestion(planId: StudyPlanId, questionId: QuestionId): Question? =
        practiceDao.findQuestion(planId.value, questionId.value)?.asExternalModel()

    override suspend fun findTest(planId: StudyPlanId, testId: StudyTestId): StudyTest? =
        practiceDao.findTest(planId.value, testId.value)?.asExternalModel()

    override suspend fun findMockExam(planId: StudyPlanId, mockExamId: MockExamId): MockExam? =
        practiceDao.findMockExam(planId.value, mockExamId.value)?.asExternalModel()

    override suspend fun upsertQuestion(question: Question): Question = database.withTransaction {
        requirePlan(question.studyPlanId)
        question.subjectId?.let { subjectId -> requireSubject(question.studyPlanId, subjectId.value) }
        question.topicId?.let { topicId -> requireTopic(question.studyPlanId, topicId.value) }
        practiceDao.upsertQuestion(question.asEntity())
        requireNotNull(practiceDao.findQuestion(question.studyPlanId.value, question.id.value)).asExternalModel()
    }

    override suspend fun deleteQuestion(planId: StudyPlanId, questionId: QuestionId) {
        database.withTransaction {
            require(practiceDao.deleteQuestion(planId.value, questionId.value) == 1) {
                "Question does not belong to this Study Plan."
            }
        }
    }

    override suspend fun upsertTest(test: StudyTest): StudyTest = database.withTransaction {
        requirePlan(test.studyPlanId)
        practiceDao.upsertTest(test.asEntity())
        requireNotNull(practiceDao.findTest(test.studyPlanId.value, test.id.value)).asExternalModel()
    }

    override suspend fun upsertMockExam(mockExam: MockExam): MockExam = database.withTransaction {
        requirePlan(mockExam.studyPlanId)
        practiceDao.upsertMockExam(mockExam.asEntity())
        requireNotNull(practiceDao.findMockExam(mockExam.studyPlanId.value, mockExam.id.value)).asExternalModel()
    }

    private suspend fun requirePlan(planId: StudyPlanId) {
        requireNotNull(planDao.findById(planId.value)) { "Study Plan does not exist." }
    }

    private suspend fun requireSubject(planId: StudyPlanId, subjectId: String) {
        requireNotNull(database.studyStructureDao().findSubject(planId.value, subjectId)) {
            "The selected subject does not belong to this Study Plan."
        }
    }

    private suspend fun requireTopic(planId: StudyPlanId, topicId: String) {
        requireNotNull(database.studyStructureDao().findTopic(planId.value, topicId)) {
            "The selected topic does not belong to this Study Plan."
        }
    }
}
