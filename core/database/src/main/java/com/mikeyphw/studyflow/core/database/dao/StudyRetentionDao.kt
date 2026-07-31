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
    suspend fun upsertReviewItems(items: List<ReviewItemEntity>)

    @Upsert
    suspend fun upsertFlashcards(flashcards: List<FlashcardEntity>)

    @Upsert
    suspend fun upsertErrorEntries(entries: List<ErrorEntryEntity>)

    @Query(
        """
        SELECT * FROM review_items
        WHERE studyPlanId = :planId
        ORDER BY completedAtEpochMillis IS NOT NULL, dueAtEpochMillis
        """,
    )
    fun observeReviewItems(planId: String): Flow<List<ReviewItemEntity>>

    @Query("SELECT * FROM flashcards WHERE studyPlanId = :planId ORDER BY updatedAtEpochMillis DESC")
    fun observeFlashcards(planId: String): Flow<List<FlashcardEntity>>

    @Query("SELECT * FROM error_entries WHERE studyPlanId = :planId ORDER BY updatedAtEpochMillis DESC")
    fun observeErrorEntries(planId: String): Flow<List<ErrorEntryEntity>>
}
