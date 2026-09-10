package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.domain.ReviewDuePolicy
import com.mikeyphw.studyflow.core.domain.StudyReviewRepository
import com.mikeyphw.studyflow.core.model.ErrorEntry
import com.mikeyphw.studyflow.core.model.ErrorEntryId
import com.mikeyphw.studyflow.core.model.Flashcard
import com.mikeyphw.studyflow.core.model.FlashcardId
import com.mikeyphw.studyflow.core.model.QuestionId
import com.mikeyphw.studyflow.core.model.ReviewItem
import com.mikeyphw.studyflow.core.model.ReviewItemId
import com.mikeyphw.studyflow.core.model.ReviewMethod
import com.mikeyphw.studyflow.core.model.ReviewStatus
import com.mikeyphw.studyflow.core.model.StudyPlanId
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class InMemoryStudyReviewRepository(
    reviews: List<ReviewItem> = emptyList(),
    flashcards: List<Flashcard> = emptyList(),
    errors: List<ErrorEntry> = emptyList(),
    private val onChanged: (List<ReviewItem>, List<Flashcard>, List<ErrorEntry>) -> Unit = { _, _, _ -> },
) : StudyReviewRepository {
    private val reviewState = MutableStateFlow(reviews)
    private val flashcardState = MutableStateFlow(flashcards)
    private val errorState = MutableStateFlow(errors)

    override fun observeReviewItems(planId: StudyPlanId): Flow<List<ReviewItem>> = reviewState.map { values ->
        values.filter { it.studyPlanId == planId }
    }
    override fun observeFlashcards(planId: StudyPlanId): Flow<List<Flashcard>> = flashcardState.map { values ->
        values.filter { it.studyPlanId == planId }
    }
    override fun observeErrorEntries(planId: StudyPlanId): Flow<List<ErrorEntry>> = errorState.map { values ->
        values.filter { it.studyPlanId == planId }
    }

    override suspend fun findReviewItem(planId: StudyPlanId, reviewItemId: ReviewItemId): ReviewItem? =
        currentReviews(planId).firstOrNull { it.id == reviewItemId }
    override suspend fun findFlashcard(planId: StudyPlanId, flashcardId: FlashcardId): Flashcard? =
        currentFlashcards(planId).firstOrNull { it.id == flashcardId }
    override suspend fun findErrorEntry(planId: StudyPlanId, errorEntryId: ErrorEntryId): ErrorEntry? =
        currentErrors(planId).firstOrNull { it.id == errorEntryId }
    override suspend fun findErrorEntryForQuestion(planId: StudyPlanId, questionId: QuestionId): ErrorEntry? =
        currentErrors(planId).firstOrNull { it.questionId == questionId }

    override suspend fun upsertReviewItem(item: ReviewItem): ReviewItem {
        reviewState.value = reviewState.value.filterNot { it.id == item.id } + item
        persist()
        return item
    }
    override suspend fun upsertFlashcard(flashcard: Flashcard): Flashcard {
        flashcardState.value = flashcardState.value.filterNot { it.id == flashcard.id } + flashcard
        persist()
        return flashcard
    }
    override suspend fun upsertErrorEntry(entry: ErrorEntry): ErrorEntry {
        errorState.value = errorState.value.filterNot { it.id == entry.id } + entry
        persist()
        return entry
    }

    override suspend fun suspendFlashcardAndCancelReviews(
        flashcard: Flashcard,
        completedAtEpochMillis: Long,
    ): Flashcard {
        val current = requireNotNull(findFlashcard(flashcard.studyPlanId, flashcard.id)) { "Flashcard does not exist." }
        require(!current.suspended) { "Flashcard is already suspended." }
        flashcardState.value = flashcardState.value.filterNot { it.id == flashcard.id } + flashcard
        reviewState.value = reviewState.value.map { item ->
            if (item.flashcardId == flashcard.id && ReviewDuePolicy.isActive(item)) {
                item.copy(status = ReviewStatus.CANCELLED, completedAtEpochMillis = completedAtEpochMillis)
            } else item
        }
        persist()
        return flashcard
    }

    override suspend fun completeReviewAndScheduleSuccessor(
        current: ReviewItem,
        completed: ReviewItem,
        successor: ReviewItem,
    ): ReviewItem {
        val stored = requireNotNull(findReviewItem(current.studyPlanId, current.id)) { "Review item does not exist." }
        require(stored == current && ReviewDuePolicy.isActive(stored)) { "Review item changed or is terminal." }
        reviewState.value = reviewState.value
            .filterNot { it.id == current.id || it.id == successor.id } + completed + successor
        persist()
        return completed
    }

    override suspend fun skipReviewIfActive(current: ReviewItem, skipped: ReviewItem): ReviewItem {
        val stored = requireNotNull(findReviewItem(current.studyPlanId, current.id)) { "Review item does not exist." }
        require(stored == current && ReviewDuePolicy.isActive(stored)) { "Review item changed or is terminal." }
        reviewState.value = reviewState.value.filterNot { it.id == current.id } + skipped
        persist()
        return skipped
    }

    override suspend fun recordWrongAnswerAndEnsureReview(entry: ErrorEntry, dueAtEpochMillis: Long): ErrorEntry {
        errorState.value = errorState.value.filterNot { it.id == entry.id } + entry
        ensureErrorReview(entry, dueAtEpochMillis)
        persist()
        return entry
    }

    override suspend fun scheduleErrorReviewIfAbsent(entry: ErrorEntry, dueAtEpochMillis: Long): ReviewItem {
        val review = ensureErrorReview(entry, dueAtEpochMillis)
        persist()
        return review
    }

    override suspend fun deleteReviewItem(planId: StudyPlanId, reviewItemId: ReviewItemId) {
        reviewState.value = reviewState.value.filterNot { it.studyPlanId == planId && it.id == reviewItemId }
        persist()
    }
    override suspend fun deleteFlashcard(planId: StudyPlanId, flashcardId: FlashcardId) {
        flashcardState.value = flashcardState.value.filterNot { it.studyPlanId == planId && it.id == flashcardId }
        reviewState.value = reviewState.value.filterNot { it.studyPlanId == planId && it.flashcardId == flashcardId }
        persist()
    }
    override suspend fun deleteErrorEntry(planId: StudyPlanId, errorEntryId: ErrorEntryId) {
        errorState.value = errorState.value.filterNot { it.studyPlanId == planId && it.id == errorEntryId }
        reviewState.value = reviewState.value.filterNot { it.studyPlanId == planId && it.errorEntryId == errorEntryId }
        persist()
    }

    fun currentReviews(planId: StudyPlanId): List<ReviewItem> = reviewState.value.filter { it.studyPlanId == planId }
        .sortedBy(ReviewItem::dueAtEpochMillis)
    fun currentFlashcards(planId: StudyPlanId): List<Flashcard> =
        flashcardState.value.filter { it.studyPlanId == planId }
        .sortedByDescending(Flashcard::updatedAtEpochMillis)
    fun currentErrors(planId: StudyPlanId): List<ErrorEntry> = errorState.value.filter { it.studyPlanId == planId }
        .sortedByDescending(ErrorEntry::updatedAtEpochMillis)

    fun purgePlan(planId: StudyPlanId) {
        reviewState.value = reviewState.value.filterNot { it.studyPlanId == planId }
        flashcardState.value = flashcardState.value.filterNot { it.studyPlanId == planId }
        errorState.value = errorState.value.filterNot { it.studyPlanId == planId }
        persist()
    }

    private fun ensureErrorReview(entry: ErrorEntry, dueAtEpochMillis: Long): ReviewItem {
        val existing = reviewState.value.firstOrNull { item ->
            item.studyPlanId == entry.studyPlanId && item.errorEntryId == entry.id && ReviewDuePolicy.isActive(item)
        }
        if (existing != null) return existing
        val created = ReviewItem(
            id = ReviewItemId(UUID.randomUUID().toString()),
            studyPlanId = entry.studyPlanId,
            subjectId = entry.subjectId,
            topicId = entry.topicId,
            questionId = entry.questionId,
            flashcardId = null,
            errorEntryId = entry.id,
            method = ReviewMethod.ERROR_BASED,
            status = ReviewStatus.DUE
                .takeIf { dueAtEpochMillis <= System.currentTimeMillis() }
                ?: ReviewStatus.SCHEDULED,
            dueAtEpochMillis = dueAtEpochMillis,
            intervalDays = 0,
            repetitionCount = 0,
            easeFactor = 2.5,
            completedAtEpochMillis = null,
        )
        reviewState.value = reviewState.value + created
        return created
    }

    private fun persist() = onChanged(reviewState.value, flashcardState.value, errorState.value)
}
