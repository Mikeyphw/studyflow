package com.mikeyphw.studyflow.core.data

import androidx.room.withTransaction
import com.mikeyphw.studyflow.core.database.StudyFlowDatabase
import com.mikeyphw.studyflow.core.database.mapper.asEntity
import com.mikeyphw.studyflow.core.database.mapper.asExternalModel
import com.mikeyphw.studyflow.core.domain.PracticeAttemptCodec
import com.mikeyphw.studyflow.core.domain.PracticeWrongAnswerCapture
import com.mikeyphw.studyflow.core.domain.StudyPracticeRepository
import com.mikeyphw.studyflow.core.model.ErrorEntry
import com.mikeyphw.studyflow.core.model.ErrorEntryId
import com.mikeyphw.studyflow.core.model.MockExam
import com.mikeyphw.studyflow.core.model.MockExamId
import com.mikeyphw.studyflow.core.model.Question
import com.mikeyphw.studyflow.core.model.QuestionId
import com.mikeyphw.studyflow.core.model.ReviewItem
import com.mikeyphw.studyflow.core.model.ReviewItemId
import com.mikeyphw.studyflow.core.model.ReviewMethod
import com.mikeyphw.studyflow.core.model.ReviewStatus
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyTest
import com.mikeyphw.studyflow.core.model.StudyTestId
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class RoomStudyPracticeRepository(
    private val database: StudyFlowDatabase,
    private val diagnosticRecorder: com.mikeyphw.studyflow.core.diagnostics.DiagnosticRecorder = com
        .mikeyphw.studyflow.core.diagnostics.NoOpDiagnosticRecorder,
) : StudyPracticeRepository {
    private val practiceDao = database.studyPracticeDao()
    private val retentionDao = database.studyRetentionDao()
    private val planDao = database.studyPlanDao()

    override fun observeQuestions(planId: StudyPlanId): Flow<List<Question>> =
        practiceDao.observeQuestions(planId.value).map { rows -> rows.map { it.asExternalModel() } }
    override fun observeTests(planId: StudyPlanId): Flow<List<StudyTest>> =
        practiceDao.observeTests(planId.value).map { rows -> rows.map { it.asExternalModel() } }
    override fun observeMockExams(planId: StudyPlanId): Flow<List<MockExam>> =
        practiceDao.observeMockExams(planId.value).map { rows -> rows.map { it.asExternalModel() } }

    override suspend fun findQuestion(planId: StudyPlanId, questionId: QuestionId): Question? =
        practiceDao.findQuestion(planId.value, questionId.value)?.asExternalModel()
    override suspend fun findTest(planId: StudyPlanId, testId: StudyTestId): StudyTest? =
        practiceDao.findTest(planId.value, testId.value)?.asExternalModel()
    override suspend fun findMockExam(planId: StudyPlanId, mockExamId: MockExamId): MockExam? =
        practiceDao.findMockExam(planId.value, mockExamId.value)?.asExternalModel()

    override suspend fun upsertQuestion(question: Question): Question = database.withTransaction {
        validateQuestionOwnership(question)
        practiceDao.upsertQuestion(question.asEntity())
        requireNotNull(practiceDao.findQuestion(question.studyPlanId.value, question.id.value)).asExternalModel()
    }

    override suspend fun importQuestionsAtomically(questions: List<Question>): List<Question> = database
        .withTransaction {
        require(questions.isNotEmpty()) { "Question import batch cannot be empty." }
        val planId = questions.first().studyPlanId
        require(questions.all { it.studyPlanId == planId }) { "Question import batch must belong to one Study Plan." }
        require(questions.map(Question::id).distinct().size == questions
            .size) { "Question import contains duplicate IDs." }
        questions.forEach { validateQuestionOwnership(it) }
        practiceDao.upsertQuestions(questions.map { question -> question.asEntity() })
        questions.map { question ->
            requireNotNull(practiceDao.findQuestion(planId.value, question.id.value)).asExternalModel()
        }
    }

    override suspend fun deleteQuestion(planId: StudyPlanId, questionId: QuestionId) {
        database.withTransaction {
            val referenced = practiceDao.findAllTests(planId.value).any { row ->
                questionId in PracticeAttemptCodec.referencedQuestionIds(row.configurationJson)
            } || practiceDao.findAllMockExams(planId.value).any { row ->
                questionId in PracticeAttemptCodec.referencedQuestionIds(row.configurationJson)
            }
            require(!referenced) {
                "Question is retained by practice/mock attempt history and cannot be hard-deleted."
            }
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

    override suspend fun createWeaknessDrillIfAbsent(test: StudyTest, key: String): StudyTest = database
        .withTransaction {
        requirePlan(test.studyPlanId)
        val marker = "weaknessDrillKey=$key"
        practiceDao.findAllTests(test.studyPlanId.value)
            .firstOrNull { row ->
                row.submittedAtEpochMillis == null && row.configurationJson.lineSequence().any { it.trim() == marker }
            }
            ?.asExternalModel()
            ?: run {
                practiceDao.upsertTest(test.asEntity())
                requireNotNull(practiceDao.findTest(test.studyPlanId.value, test.id.value)).asExternalModel()
            }
    }

    override suspend fun upsertMockExam(mockExam: MockExam): MockExam = database.withTransaction {
        requirePlan(mockExam.studyPlanId)
        practiceDao.upsertMockExam(mockExam.asEntity())
        requireNotNull(practiceDao.findMockExam(mockExam.studyPlanId.value, mockExam.id.value)).asExternalModel()
    }

    override suspend fun startTestIfNotStarted(
        planId: StudyPlanId,
        testId: StudyTestId,
        startedAtEpochMillis: Long,
    ): StudyTest = database.withTransaction {
        val existing = requireNotNull(practiceDao.findTest(planId.value, testId
            .value)) { "Practice Test does not exist." }
        require(existing.submittedAtEpochMillis == null) { "Submitted Practice Tests cannot be restarted." }
        if (existing.startedAtEpochMillis == null) {
            require(practiceDao.startStudyTestIfNotStarted(planId.value, testId.value, startedAtEpochMillis) == 1) {
                "Practice Test changed before it could be started."
            }
        }
        requireNotNull(practiceDao.findTest(planId.value, testId.value)).asExternalModel()
    }

    override suspend fun updateTestAttemptIfOpen(expectedConfiguration: String, next: StudyTest): StudyTest =
        database.withTransaction {
            require(next.submittedAtEpochMillis == null) { "Use terminal submission for submitted Practice Tests." }
            require(
                practiceDao.updateStudyTestAttemptIfOpen(
                    planId = next.studyPlanId.value,
                    testId = next.id.value,
                    expectedConfiguration = expectedConfiguration,
                    nextConfiguration = next.configurationJson,
                    answeredCount = next.answeredCount,
                    correctCount = next.correctCount,
                    startedAtEpochMillis = next.startedAtEpochMillis,
                ) == 1,
            ) { "Practice Test changed or was submitted; refusing stale answer write." }
            requireNotNull(practiceDao.findTest(next.studyPlanId.value, next.id.value)).asExternalModel()
        }

    override suspend fun submitTestIfOpen(
        expectedConfiguration: String,
        next: StudyTest,
        wrongAnswers: List<PracticeWrongAnswerCapture>,
    ): StudyTest = diagnosticRecorder.recordRepositoryMutation(
        area = com.mikeyphw.studyflow.core.diagnostics.DiagnosticArea.PRACTICE,
        action = "practice-submit",
    ) {
        database.withTransaction {
        val submittedAt = requireNotNull(next.submittedAtEpochMillis) { "Terminal submission requires a timestamp." }
        require(
            practiceDao.submitStudyTestIfOpen(
                planId = next.studyPlanId.value,
                testId = next.id.value,
                expectedConfiguration = expectedConfiguration,
                nextConfiguration = next.configurationJson,
                answeredCount = next.answeredCount,
                correctCount = next.correctCount,
                submittedAtEpochMillis = submittedAt,
            ) == 1,
        ) { "Practice Test changed or was already submitted; refusing duplicate terminal submit." }
        wrongAnswers.forEach { capture -> captureWrongAnswer(capture, submittedAt) }
        requireNotNull(practiceDao.findTest(next.studyPlanId.value, next.id.value)).asExternalModel()
    }
    }

    override suspend fun startMockExamIfNotStarted(
        planId: StudyPlanId,
        mockExamId: MockExamId,
        startedAtEpochMillis: Long,
    ): MockExam = database.withTransaction {
        val existing = requireNotNull(practiceDao.findMockExam(planId.value, mockExamId
            .value)) { "Mock Exam does not exist." }
        require(existing.submittedAtEpochMillis == null) { "Submitted Mock Exams cannot be restarted." }
        if (existing.startedAtEpochMillis == null) {
            require(practiceDao.startMockExamIfNotStarted(planId.value, mockExamId.value, startedAtEpochMillis) == 1) {
                "Mock Exam changed before it could be started."
            }
        }
        requireNotNull(practiceDao.findMockExam(planId.value, mockExamId.value)).asExternalModel()
    }

    override suspend fun updateMockExamAttemptIfOpen(expectedConfiguration: String, next: MockExam): MockExam =
        database.withTransaction {
            require(next.submittedAtEpochMillis == null) { "Use terminal submission for submitted Mock Exams." }
            require(
                practiceDao.updateMockExamAttemptIfOpen(
                    planId = next.studyPlanId.value,
                    mockExamId = next.id.value,
                    expectedConfiguration = expectedConfiguration,
                    nextConfiguration = next.configurationJson,
                    answeredCount = next.answeredCount,
                    correctCount = next.correctCount,
                    startedAtEpochMillis = next.startedAtEpochMillis,
                ) == 1,
            ) { "Mock Exam changed or was submitted; refusing stale answer write." }
            requireNotNull(practiceDao.findMockExam(next.studyPlanId.value, next.id.value)).asExternalModel()
        }

    override suspend fun submitMockExamIfOpen(
        expectedConfiguration: String,
        next: MockExam,
        wrongAnswers: List<PracticeWrongAnswerCapture>,
    ): MockExam = diagnosticRecorder.recordRepositoryMutation(
        area = com.mikeyphw.studyflow.core.diagnostics.DiagnosticArea.PRACTICE,
        action = "mock-exam-submit",
    ) {
        database.withTransaction {
        val submittedAt = requireNotNull(next.submittedAtEpochMillis) { "Terminal submission requires a timestamp." }
        val score = requireNotNull(next.scorePercent) { "Terminal Mock Exam submission requires a score." }
        require(
            practiceDao.submitMockExamIfOpen(
                planId = next.studyPlanId.value,
                mockExamId = next.id.value,
                expectedConfiguration = expectedConfiguration,
                nextConfiguration = next.configurationJson,
                answeredCount = next.answeredCount,
                correctCount = next.correctCount,
                scorePercent = score,
                submittedAtEpochMillis = submittedAt,
            ) == 1,
        ) { "Mock Exam changed or was already submitted; refusing duplicate terminal submit." }
        wrongAnswers.forEach { capture -> captureWrongAnswer(capture, submittedAt) }
        requireNotNull(practiceDao.findMockExam(next.studyPlanId.value, next.id.value)).asExternalModel()
    }
    }

    private suspend fun captureWrongAnswer(capture: PracticeWrongAnswerCapture, now: Long) {
        val question = capture.question
        val existing = retentionDao.findErrorEntryForQuestion(question.studyPlanId.value, question.id
            .value)?.asExternalModel()
        val entry = existing?.copy(
            subjectId = question.subjectId,
            topicId = question.topicId,
            category = capture.category,
            chosenAnswer = capture.chosenAnswer?.trim()?.ifBlank { null },
            correctAnswer = question.correctAnswer,
            explanation = question.explanation,
            recurrenceCount = existing.recurrenceCount + 1,
            updatedAtEpochMillis = now,
        ) ?: ErrorEntry(
            id = ErrorEntryId(UUID.randomUUID().toString()),
            studyPlanId = question.studyPlanId,
            questionId = question.id,
            subjectId = question.subjectId,
            topicId = question.topicId,
            category = capture.category,
            chosenAnswer = capture.chosenAnswer?.trim()?.ifBlank { null },
            correctAnswer = question.correctAnswer,
            explanation = question.explanation,
            recurrenceCount = 1,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
        )
        retentionDao.upsertErrorEntry(entry.asEntity())
        if (retentionDao.findPendingReviewForError(entry.studyPlanId.value, entry.id.value) == null) {
            retentionDao.upsertReviewItem(
                ReviewItem(
                    id = ReviewItemId(UUID.randomUUID().toString()),
                    studyPlanId = entry.studyPlanId,
                    subjectId = entry.subjectId,
                    topicId = entry.topicId,
                    questionId = entry.questionId,
                    flashcardId = null,
                    errorEntryId = entry.id,
                    method = ReviewMethod.ERROR_BASED,
                    status = ReviewStatus.SCHEDULED,
                    dueAtEpochMillis = now,
                    intervalDays = 0,
                    repetitionCount = 0,
                    easeFactor = 2.5,
                    completedAtEpochMillis = null,
                ).asEntity(),
            )
        }
    }

    private suspend fun validateQuestionOwnership(question: Question) {
        requirePlan(question.studyPlanId)
        val subject = question.subjectId?.let { id ->
            requireNotNull(database.studyStructureDao().findSubject(question.studyPlanId.value, id.value)) {
                "The selected subject does not belong to this Study Plan."
            }
        }
        val topic = question.topicId?.let { id ->
            requireNotNull(database.studyStructureDao().findTopic(question.studyPlanId.value, id.value)) {
                "The selected topic does not belong to this Study Plan."
            }
        }
        if (subject != null && topic != null) {
            require(topic.subjectId == subject.id) { "Question topic does not belong to the selected subject." }
        }
        question.examStageId?.let { stageId ->
            requireNotNull(database.studyStructureDao().findExamStage(question.studyPlanId.value, stageId.value)) {
                "Question exam stage does not belong to this Study Plan."
            }
        }
        question.examNoticeRevisionId?.let { revisionId ->
            requireNotNull(
                database.publicExamDao().findExamNoticeRevision(question.studyPlanId.value, revisionId.value),
            ) {
                "Question edital revision does not belong to this Study Plan."
            }
        }
    }

    private suspend fun requirePlan(planId: StudyPlanId) {
        requireNotNull(planDao.findById(planId.value)) { "Study Plan does not exist." }
    }
}
