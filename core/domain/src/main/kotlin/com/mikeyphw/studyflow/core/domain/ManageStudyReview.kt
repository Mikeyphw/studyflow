package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.ErrorCategory
import com.mikeyphw.studyflow.core.model.ErrorEntry
import com.mikeyphw.studyflow.core.model.ErrorEntryId
import com.mikeyphw.studyflow.core.model.Flashcard
import com.mikeyphw.studyflow.core.model.FlashcardId
import com.mikeyphw.studyflow.core.model.FlashcardType
import com.mikeyphw.studyflow.core.model.Question
import com.mikeyphw.studyflow.core.model.ReviewItem
import com.mikeyphw.studyflow.core.model.ReviewItemId
import com.mikeyphw.studyflow.core.model.ReviewMethod
import com.mikeyphw.studyflow.core.model.ReviewStatus
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.SubjectId
import com.mikeyphw.studyflow.core.model.TopicId
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class ManageStudyReview(
    private val repository: StudyReviewRepository,
    private val practiceRepository: StudyPracticeRepository? = null,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    fun observeReviewItems(planId: StudyPlanId): Flow<List<ReviewItem>> = repository.observeReviewItems(planId)

    fun observeFlashcards(planId: StudyPlanId): Flow<List<Flashcard>> = repository.observeFlashcards(planId)

    fun observeErrorEntries(planId: StudyPlanId): Flow<List<ErrorEntry>> = observeFreshErrorEntries(planId)

    fun observeQueue(planId: StudyPlanId): Flow<ReviewQueue> = combine(
        combine(
            repository.observeReviewItems(planId),
            repository.observeFlashcards(planId),
            observeFreshErrorEntries(planId),
        ) { items, flashcards, errors -> Triple(items, flashcards, errors) },
        tickingClockFlow(clock),
    ) { (items, flashcards, errors), now ->
        buildQueue(items, flashcards, errors, now)
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
        combine(observeFreshErrorEntries(planId), repository.observeReviewItems(planId)) { errors, items -> errors to items },
        tickingClockFlow(clock),
    ) { (errors, items), now ->
        WrongAnswerRemediator.buildNotebook(errors, items, now)
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
        val now = clock()
        return repository.suspendFlashcardAndCancelReviews(
            flashcard.copy(suspended = true, updatedAtEpochMillis = now),
            completedAtEpochMillis = now,
        )
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
        return repository.recordWrongAnswerAndEnsureReview(
            entry = ErrorEntry(
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
            dueAtEpochMillis = now,
        )
    }

    suspend fun scheduleErrorReview(
        planId: StudyPlanId,
        errorEntryId: ErrorEntryId,
        dueAtEpochMillis: Long = clock(),
    ): ReviewItem {
        val entry = requireNotNull(repository.findErrorEntry(planId, errorEntryId)) {
            "Error entry does not exist."
        }
        return repository.scheduleErrorReviewIfAbsent(entry, dueAtEpochMillis)
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
        require(ReviewDuePolicy.isActive(item)) { "Terminal reviews cannot be graded or reopened." }
        val reviewedAt = clock()
        val scheduled = SpacedRepetitionScheduler.schedule(item, grade, reviewedAt)
        val completed = item.copy(
            status = ReviewStatus.COMPLETED,
            completedAtEpochMillis = reviewedAt,
        )
        val successor = scheduled.copy(
            id = ReviewItemId(UUID.randomUUID().toString()),
            status = ReviewStatus.SCHEDULED,
            completedAtEpochMillis = null,
        )
        return repository.completeReviewAndScheduleSuccessor(item, completed, successor)
    }

    suspend fun skipReview(planId: StudyPlanId, reviewItemId: ReviewItemId): ReviewItem {
        val item = requireNotNull(repository.findReviewItem(planId, reviewItemId)) {
            "Review item does not exist."
        }
        require(ReviewDuePolicy.isActive(item)) { "Terminal reviews cannot be skipped or reopened." }
        return repository.skipReviewIfActive(
            item,
            item.copy(status = ReviewStatus.SKIPPED, completedAtEpochMillis = clock()),
        )
    }

    suspend fun deleteReview(planId: StudyPlanId, reviewItemId: ReviewItemId) {
        repository.deleteReviewItem(planId, reviewItemId)
    }

    private fun observeFreshErrorEntries(planId: StudyPlanId): Flow<List<ErrorEntry>> {
        val questions = practiceRepository?.observeQuestions(planId) ?: return repository.observeErrorEntries(planId)
        return combine(repository.observeErrorEntries(planId), questions) { errors, currentQuestions ->
            val byId = currentQuestions.associateBy(Question::id)
            errors.map { error ->
                val question = error.questionId?.let(byId::get) ?: return@map error
                if (error.subjectId == question.subjectId && error.topicId == question.topicId) {
                    error
                } else {
                    error.copy(subjectId = question.subjectId, topicId = question.topicId)
                }
            }
        }
    }

    private fun buildQueue(
        items: List<ReviewItem>,
        flashcards: List<Flashcard>,
        errors: List<ErrorEntry>,
        now: Long,
    ): ReviewQueue {
        val activeFlashcards = flashcards.filterNot(Flashcard::suspended)
        val activeFlashcardIds = activeFlashcards.mapTo(mutableSetOf()) { it.id }
        val activeItems = items.filter { item ->
            ReviewDuePolicy.isActive(item) && (item.flashcardId == null || item.flashcardId in activeFlashcardIds)
        }
        val due = activeItems.filter { ReviewDuePolicy.isDue(it, now) }.sortedBy(ReviewItem::dueAtEpochMillis)
        val upcoming = activeItems.filterNot { ReviewDuePolicy.isDue(it, now) }.sortedBy(ReviewItem::dueAtEpochMillis)
        val localToday = ReviewDuePolicy.localDate(now, zoneId)
        val completedToday = items.filter { item ->
            ReviewDuePolicy.completedOnLocalDay(item, localToday, zoneId)
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
