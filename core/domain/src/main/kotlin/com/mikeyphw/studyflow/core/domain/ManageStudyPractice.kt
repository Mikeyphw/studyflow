package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.MockExam
import com.mikeyphw.studyflow.core.model.MockExamId
import com.mikeyphw.studyflow.core.model.ExamNoticeRevisionId
import com.mikeyphw.studyflow.core.model.ExamStageId
import com.mikeyphw.studyflow.core.model.ErrorCategory
import com.mikeyphw.studyflow.core.model.Question
import com.mikeyphw.studyflow.core.model.QuestionAuthorship
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
import kotlinx.coroutines.flow.first

class ManageStudyPractice(
    private val repository: StudyPracticeRepository,
    private val studyPlanRepository: StudyPlanRepository? = null,
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
        organization: String? = null,
        role: String? = null,
        examStageId: ExamStageId? = null,
        authorship: QuestionAuthorship = QuestionAuthorship.UNKNOWN,
        examNoticeRevisionId: ExamNoticeRevisionId? = null,
        externalReference: String? = null,
        points: Double? = null,
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
            organization = organization,
            role = role,
            examStageId = examStageId,
            authorship = authorship,
            examNoticeRevisionId = examNoticeRevisionId,
            externalReference = externalReference,
            points = points,
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
        val settings = studyPlanRepository?.let { plans ->
            requireNotNull(plans.observeStoredPlanSettings(planId).first()) {
                "Study Plan settings must be persisted before creating a Practice Test."
            }
        }
        val effectiveMode = if (settings?.strictExamModeEnabled == true) TestMode.EXAM else mode
        return repository.upsertTest(
            StudyTest(
                id = id,
                studyPlanId = planId,
                title = title.trim(),
                mode = effectiveMode,
                timeLimitSeconds = timeLimitSeconds,
                questionCount = questions.size,
                answeredCount = 0,
                correctCount = 0,
                startedAtEpochMillis = null,
                submittedAtEpochMillis = null,
                configurationJson = PracticeAttemptCodec.configurationForQuestions(questions),
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
        require(test.startedAtEpochMillis != null) { "Practice Test must be explicitly started before submission." }
        return repository.submitTestIfOpen(
            expectedConfiguration = test.configurationJson,
            next = test.copy(
                answeredCount = answeredCount,
                correctCount = correctCount,
                submittedAtEpochMillis = clock(),
            ),
            wrongAnswers = emptyList(),
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
        require(questions.all { it.studyPlanId == planId }) { "All Mock Exam questions must belong to the Study Plan." }
        require(timeLimitSeconds > 0) { "Mock Exam time limit must be positive." }
        require(negativeMarking.isFinite() && negativeMarking >= 0.0) {
            "Negative marking must be finite and non-negative."
        }
        require(minimumScorePercent == null || minimumScorePercent in 0..100) {
            "Minimum score must be between 0 and 100."
        }
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
                startedAtEpochMillis = null,
                submittedAtEpochMillis = null,
                configurationJson = PracticeAttemptCodec.configurationForQuestions(questions),
            ),
        )
    }


    fun previewMockExamStudio(
        planId: StudyPlanId,
        questions: List<Question>,
        request: MockExamStudioRequest = MockExamStudioRequest(),
    ): MockExamStudioBlueprint = MockExamStudioPlanner.preview(planId, questions, request)

    suspend fun createMockExamFromStudio(
        id: MockExamId,
        planId: StudyPlanId,
        questions: List<Question>,
        request: MockExamStudioRequest = MockExamStudioRequest(),
    ): MockExam {
        val blueprint = previewMockExamStudio(planId, questions, request)
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

    fun previewExamBlueprint(
        questions: List<Question>,
        blueprint: ExamBlueprint,
    ): ExamBlueprintPreview = ExamBlueprintPlanner.preview(questions, blueprint)

    suspend fun createMockExamFromBlueprint(
        id: MockExamId,
        planId: StudyPlanId,
        questions: List<Question>,
        blueprint: ExamBlueprint,
    ): MockExam {
        require(questions.all { it.studyPlanId == planId }) {
            "Blueprint question bank must belong to this Study Plan."
        }
        val preview = previewExamBlueprint(questions, blueprint)
        require(preview.executable) {
            "Exam blueprint is incomplete; preview the missing question supply before creating the mock."
        }
        val frozenConfiguration = PracticeAttemptCodec.freezeConfiguration(
            ExamBlueprintCodec.encodeConfiguration(preview),
            preview.selectedQuestions,
        )
        return repository.upsertMockExam(
            MockExam(
                id = id,
                studyPlanId = planId,
                title = blueprint.title.trim(),
                mode = if (blueprint.strictExamMode) TestMode.EXAM else TestMode.IMMEDIATE_FEEDBACK,
                timeLimitSeconds = blueprint.timeLimitMinutes * 60L,
                questionCount = preview.selectedQuestions.size,
                answeredCount = 0,
                correctCount = 0,
                scorePercent = null,
                negativeMarking = blueprint.scoringPolicy.legacyNegativeMarking,
                minimumScorePercent = blueprint.minimumOverallScorePercent,
                startedAtEpochMillis = null,
                submittedAtEpochMillis = null,
                configurationJson = frozenConfiguration,
            ),
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
        require(mockExam.startedAtEpochMillis != null) { "Mock Exam must be explicitly started before submission." }
        val incorrectCount = answeredCount - correctCount
        val score = (((correctCount - incorrectCount * mockExam.negativeMarking).coerceAtLeast(0.0) /
            mockExam.questionCount.toDouble()) * 100.0).coerceIn(0.0, 100.0)
        return repository.submitMockExamIfOpen(
            expectedConfiguration = mockExam.configurationJson,
            next = mockExam.copy(
                answeredCount = answeredCount,
                correctCount = correctCount,
                scorePercent = score,
                submittedAtEpochMillis = clock(),
            ),
            wrongAnswers = emptyList(),
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
        elapsedSeconds: Long,
        errorCategory: ErrorCategory? = null,
    ): StudyTest = execution.answerTestQuestion(
        planId, testId, questionId, chosenAnswer, elapsedSeconds, errorCategory,
    )

    suspend fun finishPracticeAttempt(planId: StudyPlanId, testId: StudyTestId): StudyTest =
        execution.finishTest(planId, testId)

    suspend fun startMockExamAttempt(planId: StudyPlanId, mockExamId: MockExamId): MockExam =
        execution.startMockExam(planId, mockExamId)

    suspend fun answerMockQuestion(
        planId: StudyPlanId,
        mockExamId: MockExamId,
        questionId: QuestionId,
        chosenAnswer: String,
        elapsedSeconds: Long,
        errorCategory: ErrorCategory? = null,
    ): MockExam = execution.answerMockQuestion(
        planId, mockExamId, questionId, chosenAnswer, elapsedSeconds, errorCategory,
    )

    suspend fun finishMockExamAttempt(planId: StudyPlanId, mockExamId: MockExamId): MockExam =
        execution.finishMockExam(planId, mockExamId)

    fun previewQuestionImport(
        planId: StudyPlanId,
        rawText: String,
        existingQuestions: List<Question>,
        requestedFormat: QuestionImportFormat = QuestionImportFormat.AUTO,
        defaultSubjectId: SubjectId? = null,
        defaultTopicId: TopicId? = null,
        ownership: QuestionImportOwnership? = null,
    ): QuestionImportPreview = QuestionImportPipeline.preview(
        planId = planId,
        rawText = rawText,
        existingQuestions = existingQuestions,
        requestedFormat = requestedFormat,
        defaultSubjectId = defaultSubjectId,
        defaultTopicId = defaultTopicId,
        ownership = ownership,
    )

    suspend fun importQuestionsFromText(
        planId: StudyPlanId,
        rawText: String,
        existingQuestions: List<Question>,
        requestedFormat: QuestionImportFormat = QuestionImportFormat.AUTO,
        defaultSubjectId: SubjectId? = null,
        defaultTopicId: TopicId? = null,
        ownership: QuestionImportOwnership? = null,
    ): QuestionImportResult {
        val preview = previewQuestionImport(
            planId = planId,
            rawText = rawText,
            existingQuestions = existingQuestions,
            requestedFormat = requestedFormat,
            defaultSubjectId = defaultSubjectId,
            defaultTopicId = defaultTopicId,
            ownership = ownership,
        )
        require(!preview.hasErrors) {
            "Question import contains validation errors; review the preview before importing." }
        val now = clock()
        val candidates = preview.drafts.filter(QuestionImportDraft::importable).map { draft ->
            Question(
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
                favorite = false,
                disputed = false,
                tags = draft.tags,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
                organization = draft.organization,
                role = draft.role,
                examStageId = draft.examStageId,
                authorship = draft.authorship,
                examNoticeRevisionId = draft.examNoticeRevisionId,
                externalReference = draft.externalReference,
                points = draft.points,
            )
        }
        val imported = if (candidates.isEmpty()) emptyList() else repository.importQuestionsAtomically(candidates)
        return QuestionImportResult(preview = preview, imported = imported)
    }

    fun buildWeaknessDrill(
        questions: List<Question>,
        remediations: List<WrongAnswerRemediation>,
        dueReviews: List<ReviewItem>,
        request: WeaknessDrillRequest = WeaknessDrillRequest(),
    ): WeaknessDrillPlan {
        val questionById = questions.associateBy(Question::id)
        val refreshedRemediations = remediations.map { remediation ->
            val currentQuestion = remediation.error.questionId?.let(questionById::get)
            if (currentQuestion == null) remediation else remediation.copy(
                error = remediation.error.copy(
                    subjectId = currentQuestion.subjectId,
                    topicId = currentQuestion.topicId,
                ),
            )
        }
        return WeaknessDrillGenerator.build(
            questions = questions,
            remediations = refreshedRemediations,
            dueReviews = dueReviews,
            request = request,
            nowEpochMillis = clock(),
        )
    }

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
        val key = drill.questions.map { it.id.value }.sorted().joinToString(",")
        val settings = studyPlanRepository?.let { plans ->
            requireNotNull(plans.observeStoredPlanSettings(planId).first()) {
                "Study Plan settings must be persisted before creating a weakness drill."
            }
        }
        val effectiveMode = if (settings?.strictExamModeEnabled == true) TestMode.EXAM else TestMode.IMMEDIATE_FEEDBACK
        val candidate = StudyTest(
            id = id,
            studyPlanId = planId,
            title = title.trim(),
            mode = effectiveMode,
            timeLimitSeconds = null,
            questionCount = drill.questions.size,
            answeredCount = 0,
            correctCount = 0,
            startedAtEpochMillis = null,
            submittedAtEpochMillis = null,
            configurationJson = PracticeAttemptCodec.configurationForQuestions(
                drill.questions,
                metadataLines = listOf("weaknessDrillKey=$key"),
            ),
        )
        return repository.createWeaknessDrillIfAbsent(candidate, key)
    }

    private suspend fun requireQuestion(planId: StudyPlanId, questionId: QuestionId): Question =
        requireNotNull(repository.findQuestion(planId, questionId)) { "Question does not exist." }

    private fun validateQuestion(question: Question) {
        require(question.statement.isNotBlank()) { "Question statement cannot be blank." }
        require(question.correctAnswer.isNotBlank()) { "Correct answer cannot be blank." }
        require(question.difficulty in 1..5) { "Question difficulty must be between 1 and 5." }
        if (question.type == QuestionType.MULTIPLE_CHOICE) {
            require(question.options.size >= 2) { "Multiple-choice questions need at least two options." }
            require(question.options.any { it.trim().equals(question.correctAnswer.trim(), ignoreCase = true) }) {
                "Multiple-choice correct answer must be one of the options."
            }
        }
        require(question.year == null || question.year in 1900..2200) { "Question year is outside the accepted range." }
    }

    private fun requireScores(answeredCount: Int, correctCount: Int, questionCount: Int) {
        require(answeredCount in 0..questionCount) { "Answered questions must be within the test size." }
        require(correctCount in 0..answeredCount) { "Correct answers must be within answered questions." }
    }
}
