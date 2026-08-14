package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.ErrorEntry
import com.mikeyphw.studyflow.core.model.ErrorEntryId
import com.mikeyphw.studyflow.core.model.Flashcard
import com.mikeyphw.studyflow.core.model.FlashcardId
import com.mikeyphw.studyflow.core.model.QuestionId
import com.mikeyphw.studyflow.core.model.ReviewItem
import com.mikeyphw.studyflow.core.model.ReviewItemId
import com.mikeyphw.studyflow.core.model.StudyPlanId
import kotlinx.coroutines.flow.Flow

interface StudyReviewRepository {
    fun observeReviewItems(planId: StudyPlanId): Flow<List<ReviewItem>>
    fun observeFlashcards(planId: StudyPlanId): Flow<List<Flashcard>>
    fun observeErrorEntries(planId: StudyPlanId): Flow<List<ErrorEntry>>

    suspend fun findReviewItem(planId: StudyPlanId, reviewItemId: ReviewItemId): ReviewItem?
    suspend fun findFlashcard(planId: StudyPlanId, flashcardId: FlashcardId): Flashcard?
    suspend fun findErrorEntry(planId: StudyPlanId, errorEntryId: ErrorEntryId): ErrorEntry?
    suspend fun findErrorEntryForQuestion(planId: StudyPlanId, questionId: QuestionId): ErrorEntry?

    suspend fun upsertReviewItem(item: ReviewItem): ReviewItem
    suspend fun upsertFlashcard(flashcard: Flashcard): Flashcard
    suspend fun upsertErrorEntry(entry: ErrorEntry): ErrorEntry

    /** Suspends the card and cancels all pending reviews in one database transaction. */
    suspend fun suspendFlashcardAndCancelReviews(flashcard: Flashcard, completedAtEpochMillis: Long): Flashcard

    /**
     * Completes the current immutable review evidence row and inserts its next scheduled occurrence atomically.
     * Terminal/stale rows are rejected by the compare-and-set transition.
     */
    suspend fun completeReviewAndScheduleSuccessor(
        current: ReviewItem,
        completed: ReviewItem,
        successor: ReviewItem,
    ): ReviewItem

    suspend fun skipReviewIfActive(current: ReviewItem, skipped: ReviewItem): ReviewItem

    /** Serializes recurrence increment and guarantees at most one pending remediation review per error. */
    suspend fun recordWrongAnswerAndEnsureReview(entry: ErrorEntry, dueAtEpochMillis: Long): ErrorEntry
    suspend fun scheduleErrorReviewIfAbsent(entry: ErrorEntry, dueAtEpochMillis: Long): ReviewItem

    suspend fun deleteReviewItem(planId: StudyPlanId, reviewItemId: ReviewItemId)
    suspend fun deleteFlashcard(planId: StudyPlanId, flashcardId: FlashcardId)
    suspend fun deleteErrorEntry(planId: StudyPlanId, errorEntryId: ErrorEntryId)
}
