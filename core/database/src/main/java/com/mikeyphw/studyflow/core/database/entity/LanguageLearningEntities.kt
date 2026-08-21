package com.mikeyphw.studyflow.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "language_deck_profiles",
    foreignKeys = [
        ForeignKey(
            entity = StudyPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["studyPlanId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["studyPlanId"], unique = true)],
)
data class LanguageDeckProfileEntity(
    @PrimaryKey val id: String,
    val studyPlanId: String,
    val targetLanguage: String,
    val nativeLanguage: String?,
    val regionFocus: String?,
    val defaultRegister: String,
    val reviewDirectionsJson: String,
    val tagsJson: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "language_expressions",
    foreignKeys = [
        ForeignKey(
            entity = StudyPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["studyPlanId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = LanguageDeckProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["deckId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("studyPlanId"), Index("deckId"), Index("type"), Index("register")],
)
data class LanguageExpressionEntity(
    @PrimaryKey val id: String,
    val studyPlanId: String,
    val deckId: String,
    val expression: String,
    val meaning: String,
    val literalTranslation: String?,
    val translationEquivalent: String?,
    val type: String,
    val register: String,
    val region: String?,
    val pronunciationNote: String?,
    val commonMistake: String?,
    val examplesJson: String,
    val tagsJson: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "idiom_cards",
    foreignKeys = [
        ForeignKey(
            entity = LanguageExpressionEntity::class,
            parentColumns = ["id"],
            childColumns = ["expressionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("expressionId", unique = true)],
)
data class IdiomCardEntity(
    @PrimaryKey val id: String,
    val expressionId: String,
    val naturalExample: String,
    val userSentence: String?,
    val clozePrompt: String?,
    val acceptedAnswersJson: String,
    val reviewDirectionsJson: String,
    val difficulty: Int,
)

@Entity(
    tableName = "vocabulary_cards",
    foreignKeys = [
        ForeignKey(
            entity = LanguageExpressionEntity::class,
            parentColumns = ["id"],
            childColumns = ["expressionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("expressionId", unique = true)],
)
data class VocabularyCardEntity(
    @PrimaryKey val id: String,
    val expressionId: String,
    val partOfSpeech: String?,
    val synonymsJson: String,
    val antonymsJson: String,
    val reviewDirectionsJson: String,
    val difficulty: Int,
)
