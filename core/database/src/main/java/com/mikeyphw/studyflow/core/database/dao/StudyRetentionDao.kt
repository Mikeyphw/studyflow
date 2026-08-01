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

    @Query("SELECT * FROM error_entries WHERE studyPlanId = :planId ORDER BY updatedAtEpochMillis DESC")
    fun observeErrorEntries(planId: String): Flow<List<ErrorEntryEntity>>

    @Query("SELECT * FROM error_entries WHERE studyPlanId = :planId AND id = :errorEntryId LIMIT 1")
    suspend fun findErrorEntry(planId: String, errorEntryId: String): ErrorEntryEntity?
}
