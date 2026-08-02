package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.ErrorCategory
import com.mikeyphw.studyflow.core.model.ErrorEntry
import com.mikeyphw.studyflow.core.model.ErrorEntryId
import com.mikeyphw.studyflow.core.model.Flashcard
import com.mikeyphw.studyflow.core.model.FlashcardId
import com.mikeyphw.studyflow.core.model.FlashcardType
import com.mikeyphw.studyflow.core.model.MockExamId
import com.mikeyphw.studyflow.core.model.Question
import com.mikeyphw.studyflow.core.model.ReviewItem
import com.mikeyphw.studyflow.core.model.ReviewItemId
import com.mikeyphw.studyflow.core.model.ReviewMethod
import com.mikeyphw.studyflow.core.model.ReviewStatus
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyTestId
import com.mikeyphw.studyflow.core.model.SubjectId
import com.mikeyphw.studyflow.core.model.TopicId
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class ManageStudyReview(
    private val repository: StudyReviewRepository,
    private val practiceRepository: StudyPracticeRepository? = null,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun observeReviewItems(planId: StudyPlanId): Flow<List<ReviewItem>> = repository.observeReviewItems(planId)

    fun observeFlashcards(planId: StudyPlanId): Flow<List<Flashcard>> = repository.observeFlashcards(planId)

    fun observeErrorEntries(planId: StudyPlanId): Flow<List<ErrorEntry>> = repository.observeErrorEntries(planId)

    fun observeQueue(planId: StudyPlanId): Flow<ReviewQueue> = combine(
        repository.observeReviewItems(planId),
        repository.observeFlashcards(planId),
        repository.observeErrorEntries(planId),
    ) { items, flashcards, errors ->
        buildQueue(items, flashcards, errors, clock())
    }

    fun observeSummary(planId: StudyPlanId): Flow<ReviewSummary> = observeQueue(planId).combine(
        repository.observeReviewItems(planId),
    ) { queue, items ->
        val easeFactors = items.map(ReviewItem::easeFactor)
        ReviewSummary(
            dueCount = queue.dueCount,
            upcomingCount = queue.upcomingCount,
            flashcardCount = queue.flashcardCount,
            errorCount = queue.errorCount,
            averageEaseFactor = if (easeFactors.isNotEmpty()) easeFactors.average() else 0.0,
        )
    }

    fun observeWrongAnswerNotebook(planId: StudyPlanId): Flow<WrongAnswerNotebook> = combine(
        repository.observeErrorEntries(planId),
        repository.observeReviewItems(planId),
    ) { errors, items ->
        WrongAnswerRemediator.buildNotebook(errors, items, clock())
    }

    suspend fun createFlashcard(
        id: FlashcardId,
        studyPlanId: StudyPlanId,
        subjectId: SubjectId?,
        topicId: TopicId?,
        type: FlashcardType,
        front: String,
        back: String,
        clozeText: String?,
        tags: List<String>,
    ): Flashcard {
        require(front.isNotBlank()) { "Flashcard front cannot be blank." }
        require(back.isNotBlank()) { "Flashcard back cannot be blank." }
        require(type != FlashcardType.CLOZE || !clozeText.isNullOrBlank()) {
            "Cloze flashcards require cloze text."
        }
        val now = clock()
        val flashcard = repository.upsertFlashcard(
            Flashcard(
                id = id,
                studyPlanId = studyPlanId,
                subjectId = subjectId,
                topicId = topicId,
                type = type,
                front = front.trim(),
                back = back.trim(),
                clozeText = clozeText?.trim()?.ifBlank { null },
                tags = tags.map(String::trim).filter(String::isNotBlank).distinct(),
                suspended = false,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
            ),
        )
        repository.upsertReviewItem(
            SpacedRepetitionScheduler.initialReview(
                item = ReviewItem(
                    id = ReviewItemId(UUID.randomUUID().toString()),
                    studyPlanId = studyPlanId,
                    subjectId = subjectId,
                    topicId = topicId,
                    questionId = null,
                    flashcardId = flashcard.id,
                    errorEntryId = null,
                    method = ReviewMethod.SPACED_REPETITION,
                    status = ReviewStatus.DUE,
                    dueAtEpochMillis = now,
                    intervalDays = 0,
                    repetitionCount = 0,
                    easeFactor = 2.5,
                    completedAtEpochMillis = null,
                ),
                createdAtEpochMillis = now,
            ),
        )
        return flashcard
    }

    suspend fun suspendFlashcard(planId: StudyPlanId, flashcardId: FlashcardId): Flashcard {
        val flashcard = requireNotNull(repository.findFlashcard(planId, flashcardId)) {
            "Flashcard does not exist."
        }
        return repository.upsertFlashcard(flashcard.copy(suspended = true, updatedAtEpochMillis = clock()))
    }

    suspend fun scheduleTopicReview(
        id: ReviewItemId,
        planId: StudyPlanId,
        subjectId: SubjectId?,
        topicId: TopicId?,
        dueAtEpochMillis: Long = clock(),
    ): ReviewItem = repository.upsertReviewItem(
        ReviewItem(
            id = id,
            studyPlanId = planId,
            subjectId = subjectId,
            topicId = topicId,
            questionId = null,
            flashcardId = null,
            errorEntryId = null,
            method = ReviewMethod.FIXED_INTERVAL,
            status = if (dueAtEpochMillis <= clock()) ReviewStatus.DUE else ReviewStatus.SCHEDULED,
            dueAtEpochMillis = dueAtEpochMillis,
            intervalDays = 0,
            repetitionCount = 0,
            easeFactor = 2.5,
            completedAtEpochMillis = null,
        ),
    )

    suspend fun scheduleQuestionReview(
        planId: StudyPlanId,
        questionId: com.mikeyphw.studyflow.core.model.QuestionId,
        grade: Int,
    ): ReviewItem {
        require(grade in 0..5) { "Question review grade must be between 0 and 5." }
        val question = requireNotNull(practiceRepository?.findQuestion(planId, questionId)) {
            "Question does not exist."
        }
        return repository.upsertReviewItem(
            ReviewItem(
                id = ReviewItemId(UUID.randomUUID().toString()),
                studyPlanId = planId,
                subjectId = question.subjectId,
                topicId = question.topicId,
                questionId = question.id,
                flashcardId = null,
                errorEntryId = null,
                method = if (grade < 3) ReviewMethod.ERROR_BASED else ReviewMethod.QUESTION_BASED,
                status = ReviewStatus.DUE,
                dueAtEpochMillis = clock(),
                intervalDays = 0,
                repetitionCount = 0,
                easeFactor = 2.5,
                completedAtEpochMillis = null,
            ),
        )
    }

    suspend fun createErrorFromQuestion(
        id: ErrorEntryId,
        question: Question,
        category: ErrorCategory,
        chosenAnswer: String?,
    ): ErrorEntry = recordWrongAnswer(question, category, chosenAnswer, id)

    suspend fun recordWrongAnswer(
        question: Question,
        category: ErrorCategory,
        chosenAnswer: String?,
        newId: ErrorEntryId = ErrorEntryId(UUID.randomUUID().toString()),
    ): ErrorEntry {
        val now = clock()
        val existing = repository.findErrorEntryForQuestion(question.studyPlanId, question.id)
        val entry = repository.upsertErrorEntry(
            existing?.copy(
                category = category,
                chosenAnswer = chosenAnswer?.trim()?.ifBlank { null },
                correctAnswer = question.correctAnswer,
                explanation = question.explanation,
                recurrenceCount = existing.recurrenceCount + 1,
                updatedAtEpochMillis = now,
            ) ?: ErrorEntry(
                id = newId,
                studyPlanId = question.studyPlanId,
                questionId = question.id,
                subjectId = question.subjectId,
                topicId = question.topicId,
                category = category,
                chosenAnswer = chosenAnswer?.trim()?.ifBlank { null },
                correctAnswer = question.correctAnswer,
                explanation = question.explanation,
                recurrenceCount = 1,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
            ),
        )
        scheduleErrorReview(question.studyPlanId, entry.id, now)
        return entry
    }

    suspend fun captureWrongAnswersFromTest(
        planId: StudyPlanId,
        testId: StudyTestId,
        category: ErrorCategory = ErrorCategory.CONCEPT_CONFUSION,
    ): Int {
        val test = requireNotNull(practiceRepository?.findTest(planId, testId)) {
            "Practice Test does not exist."
        }
        val state = PracticeAttemptCodec.decode(
            attemptId = test.id.value,
            title = test.title,
            questionCount = test.questionCount,
            configuration = test.configurationJson,
            questions = emptyList(),
            startedAtEpochMillis = test.startedAtEpochMillis,
            submittedAtEpochMillis = test.submittedAtEpochMillis,
            timeLimitSeconds = test.timeLimitSeconds,
        )
        return captureWrongAnswers(planId, state.answers, category)
    }

    suspend fun captureWrongAnswersFromMockExam(
        planId: StudyPlanId,
        mockExamId: MockExamId,
        category: ErrorCategory = ErrorCategory.CONCEPT_CONFUSION,
    ): Int {
        val mockExam = requireNotNull(practiceRepository?.findMockExam(planId, mockExamId)) {
            "Mock Exam does not exist."
        }
        val state = PracticeAttemptCodec.decode(
            attemptId = mockExam.id.value,
            title = mockExam.title,
            questionCount = mockExam.questionCount,
            configuration = mockExam.configurationJson,
            questions = emptyList(),
            startedAtEpochMillis = mockExam.startedAtEpochMillis,
            submittedAtEpochMillis = mockExam.submittedAtEpochMillis,
            timeLimitSeconds = mockExam.timeLimitSeconds,
        )
        return captureWrongAnswers(planId, state.answers, category)
    }

    suspend fun scheduleErrorReview(
        planId: StudyPlanId,
        errorEntryId: ErrorEntryId,
        dueAtEpochMillis: Long = clock(),
    ): ReviewItem {
        val entry = requireNotNull(repository.findErrorEntry(planId, errorEntryId)) {
            "Error entry does not exist."
        }
        return repository.upsertReviewItem(
            ReviewItem(
                id = ReviewItemId(UUID.randomUUID().toString()),
                studyPlanId = planId,
                subjectId = entry.subjectId,
                topicId = entry.topicId,
                questionId = entry.questionId,
                flashcardId = null,
                errorEntryId = entry.id,
                method = ReviewMethod.ERROR_BASED,
                status = if (dueAtEpochMillis <= clock()) ReviewStatus.DUE else ReviewStatus.SCHEDULED,
                dueAtEpochMillis = dueAtEpochMillis,
                intervalDays = 0,
                repetitionCount = 0,
                easeFactor = 2.5,
                completedAtEpochMillis = null,
            ),
        )
    }

    suspend fun resolveErrorEntry(planId: StudyPlanId, errorEntryId: ErrorEntryId) {
        repository.deleteErrorEntry(planId, errorEntryId)
    }

    suspend fun remediationFor(planId: StudyPlanId, errorEntryId: ErrorEntryId): WrongAnswerRemediation {
        val error = requireNotNull(repository.findErrorEntry(planId, errorEntryId)) {
            "Error entry does not exist."
        }
        return WrongAnswerRemediator.buildNotebook(listOf(error), emptyList(), clock()).entries.single()
    }

    suspend fun gradeReview(planId: StudyPlanId, reviewItemId: ReviewItemId, grade: Int): ReviewItem {
        val item = requireNotNull(repository.findReviewItem(planId, reviewItemId)) {
            "Review item does not exist."
        }
        return repository.upsertReviewItem(SpacedRepetitionScheduler.schedule(item, grade, clock()))
    }

    suspend fun skipReview(planId: StudyPlanId, reviewItemId: ReviewItemId): ReviewItem {
        val item = requireNotNull(repository.findReviewItem(planId, reviewItemId)) {
            "Review item does not exist."
        }
        return repository.upsertReviewItem(
            item.copy(
                status = ReviewStatus.SKIPPED,
                completedAtEpochMillis = clock(),
            ),
        )
    }

    suspend fun deleteReview(planId: StudyPlanId, reviewItemId: ReviewItemId) {
        repository.deleteReviewItem(planId, reviewItemId)
    }

    private suspend fun captureWrongAnswers(
        planId: StudyPlanId,
        answers: List<PracticeAnswerRecord>,
        category: ErrorCategory,
    ): Int {
        var captured = 0
        answers.filterNot(PracticeAnswerRecord::correct).forEach { answer ->
            val question = practiceRepository?.findQuestion(planId, answer.questionId) ?: return@forEach
            recordWrongAnswer(question, category, answer.chosenAnswer)
            captured += 1
        }
        return captured
    }

    private fun buildQueue(
        items: List<ReviewItem>,
        flashcards: List<Flashcard>,
        errors: List<ErrorEntry>,
        now: Long,
    ): ReviewQueue {
        val activeFlashcards = flashcards.filterNot(Flashcard::suspended)
        val activeItems = items.filter { item -> item.status in setOf(ReviewStatus.SCHEDULED, ReviewStatus.DUE) }
        val due = activeItems.filter { item -> item.dueAtEpochMillis <= now }.sortedBy(ReviewItem::dueAtEpochMillis)
        val upcoming = activeItems.filter { item -> item.dueAtEpochMillis > now }.sortedBy(ReviewItem::dueAtEpochMillis)
        val completedToday = items.filter { item ->
            val completedAtEpochMillis = item.completedAtEpochMillis
            completedAtEpochMillis != null && now - completedAtEpochMillis < 86_400_000L
        }.sortedByDescending(ReviewItem::completedAtEpochMillis)
        return ReviewQueue(
            due = due,
            upcoming = upcoming,
            completedToday = completedToday,
            flashcards = activeFlashcards,
            errors = errors,
        )
    }
}
