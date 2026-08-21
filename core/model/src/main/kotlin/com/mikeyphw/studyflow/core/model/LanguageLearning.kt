package com.mikeyphw.studyflow.core.model

enum class LanguageExpressionType {
    IDIOM,
    VOCABULARY,
    PHRASE,
    PHRASAL_VERB,
    COLLOCATION,
}

enum class LanguageRegister {
    NEUTRAL,
    CASUAL,
    FORMAL,
    SLANG,
    ACADEMIC,
    PROFESSIONAL,
}

enum class LanguageReviewDirection {
    EXPRESSION_TO_MEANING,
    MEANING_TO_EXPRESSION,
    FILL_IN_THE_BLANK,
    CHOOSE_NATURAL_USAGE,
    SPOT_THE_MISTAKE,
    CREATE_YOUR_OWN_SENTENCE,
}

data class UsageExample(
    val sentence: String,
    val translation: String?,
    val note: String?,
) {
    init {
        require(sentence.isNotBlank()) { "Usage example sentence cannot be blank." }
    }
}

data class LanguageDeckProfile(
    val id: LanguageDeckId,
    val studyPlanId: StudyPlanId,
    val targetLanguage: String,
    val nativeLanguage: String?,
    val regionFocus: String?,
    val defaultRegister: LanguageRegister,
    val reviewDirections: Set<LanguageReviewDirection>,
    val tags: List<String>,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
) {
    init {
        require(targetLanguage.isNotBlank()) { "Target language cannot be blank." }
        require(reviewDirections.isNotEmpty()) { "Language deck needs at least one review direction." }
        require(createdAtEpochMillis >= 0) { "Created timestamp cannot be negative." }
        require(updatedAtEpochMillis >= createdAtEpochMillis) { "Updated timestamp cannot precede created timestamp." }
    }
}

data class LanguageExpression(
    val id: LanguageExpressionId,
    val studyPlanId: StudyPlanId,
    val deckId: LanguageDeckId,
    val expression: String,
    val meaning: String,
    val literalTranslation: String?,
    val translationEquivalent: String?,
    val type: LanguageExpressionType,
    val register: LanguageRegister,
    val region: String?,
    val pronunciationNote: String?,
    val commonMistake: String?,
    val examples: List<UsageExample>,
    val tags: List<String>,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
) {
    init {
        require(expression.isNotBlank()) { "Language expression cannot be blank." }
        require(meaning.isNotBlank()) { "Language expression meaning cannot be blank." }
        require(createdAtEpochMillis >= 0) { "Created timestamp cannot be negative." }
        require(updatedAtEpochMillis >= createdAtEpochMillis) { "Updated timestamp cannot precede created timestamp." }
    }
}

data class IdiomCard(
    val id: IdiomCardId,
    val expressionId: LanguageExpressionId,
    val naturalExample: String,
    val userSentence: String?,
    val clozePrompt: String?,
    val acceptedAnswers: List<String>,
    val reviewDirections: Set<LanguageReviewDirection>,
    val difficulty: Int,
) {
    init {
        require(naturalExample.isNotBlank()) { "Idiom natural example cannot be blank." }
        require(acceptedAnswers.isNotEmpty()) { "Idiom card needs at least one accepted answer." }
        require(reviewDirections.isNotEmpty()) { "Idiom card needs at least one review direction." }
        require(difficulty in 1..5) { "Idiom difficulty must be between 1 and 5." }
    }
}

data class VocabularyCard(
    val id: VocabularyCardId,
    val expressionId: LanguageExpressionId,
    val partOfSpeech: String?,
    val synonyms: List<String>,
    val antonyms: List<String>,
    val reviewDirections: Set<LanguageReviewDirection>,
    val difficulty: Int,
) {
    init {
        require(reviewDirections.isNotEmpty()) { "Vocabulary card needs at least one review direction." }
        require(difficulty in 1..5) { "Vocabulary difficulty must be between 1 and 5." }
    }
}

fun defaultLanguageReviewDirections(type: LanguageExpressionType): Set<LanguageReviewDirection> = when (type) {
    LanguageExpressionType.IDIOM -> setOf(
        LanguageReviewDirection.EXPRESSION_TO_MEANING,
        LanguageReviewDirection.MEANING_TO_EXPRESSION,
        LanguageReviewDirection.FILL_IN_THE_BLANK,
        LanguageReviewDirection.CHOOSE_NATURAL_USAGE,
        LanguageReviewDirection.SPOT_THE_MISTAKE,
        LanguageReviewDirection.CREATE_YOUR_OWN_SENTENCE,
    )
    LanguageExpressionType.VOCABULARY -> setOf(
        LanguageReviewDirection.EXPRESSION_TO_MEANING,
        LanguageReviewDirection.MEANING_TO_EXPRESSION,
        LanguageReviewDirection.FILL_IN_THE_BLANK,
    )
    LanguageExpressionType.PHRASE,
    LanguageExpressionType.PHRASAL_VERB,
    LanguageExpressionType.COLLOCATION,
    -> setOf(
        LanguageReviewDirection.EXPRESSION_TO_MEANING,
        LanguageReviewDirection.CHOOSE_NATURAL_USAGE,
        LanguageReviewDirection.CREATE_YOUR_OWN_SENTENCE,
    )
}

fun LanguageExpression.toIdiomCard(
    cardId: IdiomCardId,
    clozePrompt: String? = null,
    userSentence: String? = null,
    difficulty: Int = 2,
): IdiomCard {
    require(type == LanguageExpressionType.IDIOM) { "Only idiom expressions can become idiom cards." }
    val naturalExample = examples.firstOrNull()?.sentence ?: expression
    return IdiomCard(
        id = cardId,
        expressionId = id,
        naturalExample = naturalExample,
        userSentence = userSentence,
        clozePrompt = clozePrompt,
        acceptedAnswers = listOf(expression),
        reviewDirections = defaultLanguageReviewDirections(LanguageExpressionType.IDIOM),
        difficulty = difficulty,
    )
}
