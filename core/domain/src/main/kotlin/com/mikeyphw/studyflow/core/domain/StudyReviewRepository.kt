package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.ErrorEntry
import com.mikeyphw.studyflow.core.model.ErrorEntryId
import com.mikeyphw.studyflow.core.model.Flashcard
import com.mikeyphw.studyflow.core.model.FlashcardId
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

    suspend fun findErrorEntryForQuestion(
        planId: StudyPlanId,
        questionId: com.mikeyphw.studyflow.core.model.QuestionId,
    ): ErrorEntry?

    suspend fun upsertReviewItem(item: ReviewItem): ReviewItem

    suspend fun upsertFlashcard(flashcard: Flashcard): Flashcard

    suspend fun upsertErrorEntry(entry: ErrorEntry): ErrorEntry

    suspend fun deleteReviewItem(planId: StudyPlanId, reviewItemId: ReviewItemId)

    suspend fun deleteFlashcard(planId: StudyPlanId, flashcardId: FlashcardId)

    suspend fun deleteErrorEntry(planId: StudyPlanId, errorEntryId: ErrorEntryId)
}
