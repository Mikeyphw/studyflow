package com.mikeyphw.studyflow.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.mikeyphw.studyflow.core.database.entity.IdiomCardEntity
import com.mikeyphw.studyflow.core.database.entity.LanguageDeckProfileEntity
import com.mikeyphw.studyflow.core.database.entity.LanguageExpressionEntity
import com.mikeyphw.studyflow.core.database.entity.LanguageReviewAttemptEntity
import com.mikeyphw.studyflow.core.database.entity.VocabularyCardEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LanguageLearningDao {
    @Upsert
    suspend fun upsertDeck(deck: LanguageDeckProfileEntity)

    @Upsert
    suspend fun upsertExpression(expression: LanguageExpressionEntity)

    @Upsert
    suspend fun upsertIdiomCard(card: IdiomCardEntity)

    @Upsert
    suspend fun upsertVocabularyCard(card: VocabularyCardEntity)

    @Upsert
    suspend fun upsertLanguageReviewAttempt(attempt: LanguageReviewAttemptEntity)

    @Query("SELECT * FROM language_deck_profiles WHERE studyPlanId = :planId LIMIT 1")
    fun observeDeck(planId: String): Flow<LanguageDeckProfileEntity?>

    @Query("SELECT * FROM language_deck_profiles WHERE studyPlanId = :planId LIMIT 1")
    suspend fun findDeck(planId: String): LanguageDeckProfileEntity?

    @Query("SELECT * FROM language_expressions WHERE studyPlanId = :planId ORDER BY updatedAtEpochMillis DESC")
    suspend fun findExpressions(planId: String): List<LanguageExpressionEntity>

    @Query("SELECT * FROM language_review_attempts WHERE studyPlanId = :planId ORDER BY createdAtEpochMillis DESC")
    suspend fun findLanguageReviewAttempts(planId: String): List<LanguageReviewAttemptEntity>

    @Query("SELECT * FROM language_expressions WHERE studyPlanId = :planId ORDER BY updatedAtEpochMillis DESC")
    fun observeExpressions(planId: String): Flow<List<LanguageExpressionEntity>>

    @Query("SELECT * FROM language_expressions WHERE deckId = :deckId ORDER BY expression COLLATE NOCASE")
    fun observeDeckExpressions(deckId: String): Flow<List<LanguageExpressionEntity>>

    @Query("SELECT * FROM language_expressions WHERE studyPlanId = :planId AND type = :type ORDER BY expression")
    fun observeExpressionsByType(planId: String, type: String): Flow<List<LanguageExpressionEntity>>

    @Query("SELECT * FROM idiom_cards WHERE expressionId = :expressionId LIMIT 1")
    suspend fun findIdiomCard(expressionId: String): IdiomCardEntity?

    @Query("SELECT * FROM vocabulary_cards WHERE expressionId = :expressionId LIMIT 1")
    suspend fun findVocabularyCard(expressionId: String): VocabularyCardEntity?

    @Query("SELECT * FROM language_review_attempts WHERE studyPlanId = :planId ORDER BY createdAtEpochMillis DESC")
    fun observeLanguageReviewAttempts(planId: String): Flow<List<LanguageReviewAttemptEntity>>

    @Query(
        "SELECT * FROM language_review_attempts " +
            "WHERE expressionId = :expressionId ORDER BY createdAtEpochMillis DESC",
    )
    fun observeExpressionReviewAttempts(expressionId: String): Flow<List<LanguageReviewAttemptEntity>>
}
