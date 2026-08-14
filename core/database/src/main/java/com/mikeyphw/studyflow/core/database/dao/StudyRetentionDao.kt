package com.mikeyphw.studyflow.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.mikeyphw.studyflow.core.database.entity.ErrorEntryEntity
import com.mikeyphw.studyflow.core.database.entity.FlashcardEntity
import com.mikeyphw.studyflow.core.database.entity.ReviewItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StudyRetentionDao {
    @Upsert
    suspend fun upsertReviewItem(item: ReviewItemEntity)

    @Upsert
    suspend fun upsertReviewItems(items: List<ReviewItemEntity>)

    @Upsert
    suspend fun upsertFlashcard(flashcard: FlashcardEntity)

    @Upsert
    suspend fun upsertFlashcards(flashcards: List<FlashcardEntity>)

    @Upsert
    suspend fun upsertErrorEntry(entry: ErrorEntryEntity)

    @Upsert
    suspend fun upsertErrorEntries(entries: List<ErrorEntryEntity>)

    @Query(
        """
        SELECT * FROM review_items
        WHERE studyPlanId = :planId
        ORDER BY dueAtEpochMillis, completedAtEpochMillis DESC
        """,
    )
    fun observeReviewItems(planId: String): Flow<List<ReviewItemEntity>>

    @Query("SELECT * FROM review_items WHERE studyPlanId = :planId AND id = :reviewItemId LIMIT 1")
    suspend fun findReviewItem(planId: String, reviewItemId: String): ReviewItemEntity?

    @Query("DELETE FROM review_items WHERE studyPlanId = :planId AND id = :reviewItemId")
    suspend fun deleteReviewItem(planId: String, reviewItemId: String): Int

    @Query("SELECT * FROM flashcards WHERE studyPlanId = :planId ORDER BY suspended, updatedAtEpochMillis DESC")
    fun observeFlashcards(planId: String): Flow<List<FlashcardEntity>>

    @Query("SELECT * FROM flashcards WHERE studyPlanId = :planId AND id = :flashcardId LIMIT 1")
    suspend fun findFlashcard(planId: String, flashcardId: String): FlashcardEntity?

    @Query("DELETE FROM flashcards WHERE studyPlanId = :planId AND id = :flashcardId")
    suspend fun deleteFlashcard(planId: String, flashcardId: String): Int


    @Query(
        """
        UPDATE error_entries
        SET recurrenceCount = recurrenceCount + 1,
            updatedAtEpochMillis = :updatedAtEpochMillis
        WHERE studyPlanId = :planId AND id = :errorEntryId
        """,
    )
    suspend fun incrementErrorRecurrence(
        planId: String,
        errorEntryId: String,
        updatedAtEpochMillis: Long,
    ): Int

    @Query(
        """
        UPDATE review_items
        SET status = :newStatus,
            dueAtEpochMillis = :newDueAtEpochMillis,
            intervalDays = :newIntervalDays,
            repetitionCount = :newRepetitionCount,
            easeFactor = :newEaseFactor,
            completedAtEpochMillis = :completedAtEpochMillis
        WHERE studyPlanId = :planId
          AND id = :reviewItemId
          AND status = :expectedStatus
        """,
    )
    suspend fun compareAndSetReviewState(
        planId: String,
        reviewItemId: String,
        expectedStatus: String,
        newStatus: String,
        newDueAtEpochMillis: Long,
        newIntervalDays: Int,
        newRepetitionCount: Int,
        newEaseFactor: Double,
        completedAtEpochMillis: Long?,
    ): Int

    @Query("SELECT * FROM error_entries WHERE studyPlanId = :planId ORDER BY updatedAtEpochMillis DESC")
    fun observeErrorEntries(planId: String): Flow<List<ErrorEntryEntity>>

    @Query("SELECT * FROM error_entries WHERE studyPlanId = :planId AND id = :errorEntryId LIMIT 1")
    suspend fun findErrorEntry(planId: String, errorEntryId: String): ErrorEntryEntity?

    @Query(
        """
        SELECT * FROM error_entries
        WHERE studyPlanId = :planId AND questionId = :questionId
        ORDER BY recurrenceCount DESC, updatedAtEpochMillis DESC
        LIMIT 1
        """,
    )
    suspend fun findErrorEntryForQuestion(planId: String, questionId: String): ErrorEntryEntity?

    @Query("DELETE FROM review_items WHERE studyPlanId = :planId AND errorEntryId = :errorEntryId")
    suspend fun deleteReviewsForErrorEntry(planId: String, errorEntryId: String): Int

    @Query("DELETE FROM error_entries WHERE studyPlanId = :planId AND id = :errorEntryId")
    suspend fun deleteErrorEntry(planId: String, errorEntryId: String): Int


    @Query(
        """
        SELECT * FROM review_items
        WHERE studyPlanId = :planId AND errorEntryId = :errorEntryId
          AND status IN ('SCHEDULED', 'DUE')
        ORDER BY dueAtEpochMillis, id
        LIMIT 1
        """,
    )
    suspend fun findPendingReviewForError(planId: String, errorEntryId: String): ReviewItemEntity?

    @Query(
        """
        UPDATE review_items
        SET status = 'CANCELLED', completedAtEpochMillis = :completedAtEpochMillis
        WHERE studyPlanId = :planId AND flashcardId = :flashcardId
          AND status IN ('SCHEDULED', 'DUE')
        """,
    )
    suspend fun cancelActiveReviewsForFlashcard(
        planId: String,
        flashcardId: String,
        completedAtEpochMillis: Long,
    ): Int
}
