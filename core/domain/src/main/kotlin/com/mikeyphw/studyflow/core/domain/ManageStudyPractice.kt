package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.MockExam
import com.mikeyphw.studyflow.core.model.MockExamId
import com.mikeyphw.studyflow.core.model.Question
import com.mikeyphw.studyflow.core.model.QuestionId
import com.mikeyphw.studyflow.core.model.QuestionType
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.ReviewItem
import com.mikeyphw.studyflow.core.model.StudyTest
import com.mikeyphw.studyflow.core.model.StudyTestId
import com.mikeyphw.studyflow.core.model.SubjectId
import com.mikeyphw.studyflow.core.model.TestMode
import com.mikeyphw.studyflow.core.model.TopicId
import java.util.UUID
import kotlinx.coroutines.flow.combine

class ManageStudyPractice(
    private val repository: StudyPracticeRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val execution = ManagePracticeExecution(repository, clock = clock)

    fun observeQuestions(planId: StudyPlanId) = repository.observeQuestions(planId)

    fun observeTests(planId: StudyPlanId) = repository.observeTests(planId)

    fun observeMockExams(planId: StudyPlanId) = repository.observeMockExams(planId)

    fun observeSummary(planId: StudyPlanId) = combine(
        repository.observeQuestions(planId),
        repository.observeTests(planId),
        repository.observeMockExams(planId),
    ) { questions, tests, mockExams ->
        PracticeAnalytics.summarize(questions, tests, mockExams)
    }

    suspend fun saveQuestion(question: Question): Question {
        validateQuestion(question)
        return repository.upsertQuestion(question.copy(updatedAtEpochMillis = clock()))
    }

    suspend fun createQuestion(
        id: QuestionId,
        studyPlanId: StudyPlanId,
        subjectId: SubjectId?,
        topicId: TopicId?,
        type: QuestionType,
        statement: String,
        options: List<String>,
        correctAnswer: String,
        explanation: String,
        examBoard: String?,
        source: String?,
        year: Int?,
        difficulty: Int,
        tags: List<String>,
    ): Question = saveQuestion(
        Question(
            id = id,
            studyPlanId = studyPlanId,
            subjectId = subjectId,
            topicId = topicId,
            type = type,
            statement = statement.trim(),
            options = options.map(String::trim).filter(String::isNotBlank),
            correctAnswer = correctAnswer.trim(),
            explanation = explanation.trim(),
            examBoard = examBoard?.trim()?.ifBlank { null },
            source = source?.trim()?.ifBlank { null },
            year = year,
            difficulty = difficulty,
            favorite = false,
            disputed = false,
            tags = tags.map(String::trim).filter(String::isNotBlank).distinct(),
            createdAtEpochMillis = clock(),
            updatedAtEpochMillis = clock(),
        ),
    )

    suspend fun toggleFavorite(planId: StudyPlanId, questionId: QuestionId): Question {
        val question = requireQuestion(planId, questionId)
        return saveQuestion(question.copy(favorite = !question.favorite))
    }

    suspend fun toggleDisputed(planId: StudyPlanId, questionId: QuestionId): Question {
        val question = requireQuestion(planId, questionId)
        return saveQuestion(question.copy(disputed = !question.disputed))
    }

    suspend fun deleteQuestion(planId: StudyPlanId, questionId: QuestionId) {
        repository.deleteQuestion(planId, questionId)
    }

    suspend fun createPracticeTest(
        id: StudyTestId,
        planId: StudyPlanId,
        title: String,
        questions: List<Question>,
        mode: TestMode = TestMode.PRACTICE,
        timeLimitSeconds: Long? = null,
    ): StudyTest {
        require(title.isNotBlank()) { "Practice Test title cannot be blank." }
        require(questions.isNotEmpty()) { "Practice Test needs at least one question." }
        require(questions.all { it.studyPlanId == planId }) { "All questions must belong to the Study Plan." }
        val now = clock()
        return repository.upsertTest(
            StudyTest(
                id = id,
                studyPlanId = planId,
                title = title.trim(),
                mode = mode,
                timeLimitSeconds = timeLimitSeconds,
                questionCount = questions.size,
                answeredCount = 0,
                correctCount = 0,
                startedAtEpochMillis = now,
                submittedAtEpochMillis = null,
                configurationJson = questions.joinToString(prefix = "questionIds=", separator = ",") { it.id.value },
            ),
        )
    }

    suspend fun submitTest(
        planId: StudyPlanId,
        testId: StudyTestId,
        answeredCount: Int,
        correctCount: Int,
    ): StudyTest {
        val test = requireNotNull(repository.findTest(planId, testId)) { "Practice Test does not exist." }
        requireScores(answeredCount, correctCount, test.questionCount)
        return repository.upsertTest(
            test.copy(
                answeredCount = answeredCount,
                correctCount = correctCount,
                submittedAtEpochMillis = clock(),
            ),
        )
    }

    suspend fun createMockExam(
        id: MockExamId,
        planId: StudyPlanId,
        title: String,
        questions: List<Question>,
        timeLimitSeconds: Long,
        negativeMarking: Double,
        minimumScorePercent: Int?,
    ): MockExam {
        require(title.isNotBlank()) { "Mock Exam title cannot be blank." }
        require(questions.isNotEmpty()) { "Mock Exam needs at least one question." }
        require(timeLimitSeconds > 0) { "Mock Exam time limit must be positive." }
        require(negativeMarking >= 0.0) { "Negative marking cannot be negative." }
        require(minimumScorePercent == null || minimumScorePercent in 0..100) {
            "Minimum score must be between 0 and 100."
        }
        val now = clock()
        return repository.upsertMockExam(
            MockExam(
                id = id,
                studyPlanId = planId,
                title = title.trim(),
                mode = TestMode.EXAM,
                timeLimitSeconds = timeLimitSeconds,
                questionCount = questions.size,
                answeredCount = 0,
                correctCount = 0,
                scorePercent = null,
                negativeMarking = negativeMarking,
                minimumScorePercent = minimumScorePercent,
                startedAtEpochMillis = now,
                submittedAtEpochMillis = null,
                configurationJson = questions.joinToString(prefix = "questionIds=", separator = ",") { it.id.value },
            ),
        )
    }


    fun previewMockExamStudio(
        questions: List<Question>,
        request: MockExamStudioRequest = MockExamStudioRequest(),
    ): MockExamStudioBlueprint = MockExamStudioPlanner.preview(questions, request)

    suspend fun createMockExamFromStudio(
        id: MockExamId,
        planId: StudyPlanId,
        questions: List<Question>,
        request: MockExamStudioRequest = MockExamStudioRequest(),
    ): MockExam {
        val blueprint = previewMockExamStudio(questions, request)
        require(blueprint.executable) { "Mock Exam Studio needs at least one eligible question." }
        return createMockExam(
            id = id,
            planId = planId,
            title = blueprint.title,
            questions = blueprint.selectedQuestions,
            timeLimitSeconds = blueprint.timeLimitSeconds,
            negativeMarking = blueprint.negativeMarking,
            minimumScorePercent = blueprint.minimumScorePercent,
        )
    }

    suspend fun submitMockExam(
        planId: StudyPlanId,
        mockExamId: MockExamId,
        answeredCount: Int,
        correctCount: Int,
    ): MockExam {
        val mockExam = requireNotNull(repository.findMockExam(planId, mockExamId)) { "Mock Exam does not exist." }
        requireScores(answeredCount, correctCount, mockExam.questionCount)
        val score = PracticeAnalytics.accuracyPercent(correctCount, answeredCount).toDouble()
        return repository.upsertMockExam(
            mockExam.copy(
                answeredCount = answeredCount,
                correctCount = correctCount,
                scorePercent = score,
                submittedAtEpochMillis = clock(),
            ),
        )
    }

    fun buildDeck(questions: List<Question>, request: PracticeDeckRequest): List<Question> =
        PracticeAnalytics.buildDeck(questions, request)

    fun stateForTest(
        test: StudyTest,
        questions: List<Question>,
    ) = execution.stateForTest(test, questions)

    fun stateForMockExam(
        mockExam: MockExam,
        questions: List<Question>,
    ) = execution.stateForMockExam(mockExam, questions)

    suspend fun startPracticeTest(planId: StudyPlanId, testId: StudyTestId): StudyTest =
        execution.startTest(planId, testId)

    suspend fun answerPracticeQuestion(
        planId: StudyPlanId,
        testId: StudyTestId,
        questionId: QuestionId,
        chosenAnswer: String,
    ): StudyTest = execution.answerTestQuestion(planId, testId, questionId, chosenAnswer)

    suspend fun finishPracticeAttempt(planId: StudyPlanId, testId: StudyTestId): StudyTest =
        execution.finishTest(planId, testId)

    suspend fun startMockExamAttempt(planId: StudyPlanId, mockExamId: MockExamId): MockExam =
        execution.startMockExam(planId, mockExamId)

    suspend fun answerMockQuestion(
        planId: StudyPlanId,
        mockExamId: MockExamId,
        questionId: QuestionId,
        chosenAnswer: String,
    ): MockExam = execution.answerMockQuestion(planId, mockExamId, questionId, chosenAnswer)

    suspend fun finishMockExamAttempt(planId: StudyPlanId, mockExamId: MockExamId): MockExam =
        execution.finishMockExam(planId, mockExamId)

    fun previewQuestionImport(
        planId: StudyPlanId,
        rawText: String,
        existingQuestions: List<Question>,
        requestedFormat: QuestionImportFormat = QuestionImportFormat.AUTO,
        defaultSubjectId: SubjectId? = null,
        defaultTopicId: TopicId? = null,
    ): QuestionImportPreview = QuestionImportPipeline.preview(
        planId = planId,
        rawText = rawText,
        existingQuestions = existingQuestions,
        requestedFormat = requestedFormat,
        defaultSubjectId = defaultSubjectId,
        defaultTopicId = defaultTopicId,
    )

    suspend fun importQuestionsFromText(
        planId: StudyPlanId,
        rawText: String,
        existingQuestions: List<Question>,
        requestedFormat: QuestionImportFormat = QuestionImportFormat.AUTO,
        defaultSubjectId: SubjectId? = null,
        defaultTopicId: TopicId? = null,
    ): QuestionImportResult {
        val preview = previewQuestionImport(
            planId = planId,
            rawText = rawText,
            existingQuestions = existingQuestions,
            requestedFormat = requestedFormat,
            defaultSubjectId = defaultSubjectId,
            defaultTopicId = defaultTopicId,
        )
        val imported = mutableListOf<Question>()
        preview.drafts.filter(QuestionImportDraft::importable).forEach { draft ->
            imported += createQuestion(
                id = QuestionId(UUID.randomUUID().toString()),
                studyPlanId = planId,
                subjectId = draft.subjectId,
                topicId = draft.topicId,
                type = draft.type,
                statement = draft.statement,
                options = draft.options,
                correctAnswer = draft.correctAnswer,
                explanation = draft.explanation,
                examBoard = draft.examBoard,
                source = draft.source,
                year = draft.year,
                difficulty = draft.difficulty,
                tags = draft.tags,
            )
        }
        return QuestionImportResult(preview = preview, imported = imported)
    }

    fun buildWeaknessDrill(
        questions: List<Question>,
        remediations: List<WrongAnswerRemediation>,
        dueReviews: List<ReviewItem>,
        request: WeaknessDrillRequest = WeaknessDrillRequest(),
    ): WeaknessDrillPlan = WeaknessDrillGenerator.build(
        questions = questions,
        remediations = remediations,
        dueReviews = dueReviews,
        request = request,
    )

    suspend fun createWeaknessDrillTest(
        id: StudyTestId,
        planId: StudyPlanId,
        title: String,
        questions: List<Question>,
        remediations: List<WrongAnswerRemediation>,
        dueReviews: List<ReviewItem>,
        request: WeaknessDrillRequest = WeaknessDrillRequest(),
    ): StudyTest {
        val drill = buildWeaknessDrill(questions, remediations, dueReviews, request)
        require(drill.executable) { "Weakness drill needs enough matching weak questions." }
        return createPracticeTest(
            id = id,
            planId = planId,
            title = title,
            questions = drill.questions,
            mode = TestMode.IMMEDIATE_FEEDBACK,
            timeLimitSeconds = null,
        )
    }

    private suspend fun requireQuestion(planId: StudyPlanId, questionId: QuestionId): Question =
        requireNotNull(repository.findQuestion(planId, questionId)) { "Question does not exist." }

    private fun validateQuestion(question: Question) {
        require(question.statement.isNotBlank()) { "Question statement cannot be blank." }
        require(question.correctAnswer.isNotBlank()) { "Correct answer cannot be blank." }
        require(question.difficulty in 1..5) { "Question difficulty must be between 1 and 5." }
        if (question.type == QuestionType.MULTIPLE_CHOICE) {
            require(question.options.size >= 2) { "Multiple-choice questions need at least two options." }
        }
        require(question.year == null || question.year in 1900..2200) { "Question year is outside the accepted range." }
    }

    private fun requireScores(answeredCount: Int, correctCount: Int, questionCount: Int) {
        require(answeredCount in 0..questionCount) { "Answered questions must be within the test size." }
        require(correctCount in 0..answeredCount) { "Correct answers must be within answered questions." }
    }
}
