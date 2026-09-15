package com.mikeyphw.studyflow.core.database.mapper

import android.util.Base64
import com.mikeyphw.studyflow.core.database.entity.IdiomCardEntity
import com.mikeyphw.studyflow.core.database.entity.LanguageDeckProfileEntity
import com.mikeyphw.studyflow.core.database.entity.LanguageExpressionEntity
import com.mikeyphw.studyflow.core.database.entity.LanguageReviewAttemptEntity
import com.mikeyphw.studyflow.core.database.entity.VocabularyCardEntity
import com.mikeyphw.studyflow.core.model.IdiomCard
import com.mikeyphw.studyflow.core.model.IdiomCardId
import com.mikeyphw.studyflow.core.model.LanguageDeckId
import com.mikeyphw.studyflow.core.model.LanguageDeckProfile
import com.mikeyphw.studyflow.core.model.LanguageExpression
import com.mikeyphw.studyflow.core.model.LanguageExpressionId
import com.mikeyphw.studyflow.core.model.LanguageExpressionType
import com.mikeyphw.studyflow.core.model.LanguageRegister
import com.mikeyphw.studyflow.core.model.LanguageReviewAttempt
import com.mikeyphw.studyflow.core.model.LanguageReviewAttemptId
import com.mikeyphw.studyflow.core.model.LanguageReviewDirection
import com.mikeyphw.studyflow.core.model.LanguageReviewOutcome
import com.mikeyphw.studyflow.core.model.ReviewItemId
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.UsageExample
import com.mikeyphw.studyflow.core.model.VocabularyCard
import com.mikeyphw.studyflow.core.model.VocabularyCardId
import java.nio.charset.StandardCharsets

private const val EncodedLanguagePrefix = "b64:"
private const val FieldSeparator = "|"

private fun String.encodeField(): String = EncodedLanguagePrefix + Base64.encodeToString(
    toByteArray(StandardCharsets.UTF_8),
    Base64.NO_WRAP,
)

private fun String.decodeField(field: String): String {
    val encoded = removePrefix(EncodedLanguagePrefix)
    return try {
        String(Base64.decode(encoded, Base64.NO_WRAP), StandardCharsets.UTF_8)
    } catch (error: IllegalArgumentException) {
        throw StudyFlowDatabaseCorruptionException("Invalid encoded $field.", error)
    }
}

private fun Set<LanguageReviewDirection>.encodeReviewDirectionsStable(): String =
    map { direction -> direction.name }.sorted().encodeStringListStable()

private fun String.decodeReviewDirectionsStable(field: String): Set<LanguageReviewDirection> =
    decodeStringListStable(field).map { value -> value.decodeEnum<LanguageReviewDirection>(field) }.toSet()

private fun List<UsageExample>.encodeUsageExamplesStable(): String = joinToString("\n") { example ->
    listOf(
        example.sentence.encodeField(),
        example.translation.orEmpty().encodeField(),
        example.note.orEmpty().encodeField(),
    ).joinToString(FieldSeparator)
}

private fun String.decodeUsageExamplesStable(field: String): List<UsageExample> {
    if (isEmpty()) return emptyList()
    return lineSequence().mapIndexed { index, raw ->
        val parts = raw.split(FieldSeparator)
        if (parts.size != 3) {
            throw StudyFlowDatabaseCorruptionException("Invalid usage example row $index in $field.")
        }
        UsageExample(
            sentence = parts[0].decodeField("$field[$index].sentence"),
            translation = parts[1].decodeField("$field[$index].translation").ifBlank { null },
            note = parts[2].decodeField("$field[$index].note").ifBlank { null },
        )
    }.toList()
}

fun LanguageDeckProfileEntity.asExternalModel(): LanguageDeckProfile = decodeModel("LanguageDeckProfileEntity") {
    LanguageDeckProfile(
        id = LanguageDeckId(id),
        studyPlanId = StudyPlanId(studyPlanId),
        targetLanguage = targetLanguage,
        nativeLanguage = nativeLanguage,
        regionFocus = regionFocus,
        defaultRegister = defaultRegister.decodeEnum("LanguageDeckProfileEntity.defaultRegister"),
        reviewDirections = reviewDirectionsJson.decodeReviewDirectionsStable("LanguageDeckProfileEntity.reviewDirectionsJson"),
        tags = tagsJson.decodeStringListStable("LanguageDeckProfileEntity.tagsJson"),
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )
}

fun LanguageDeckProfile.asEntity(): LanguageDeckProfileEntity = LanguageDeckProfileEntity(
    id = id.value,
    studyPlanId = studyPlanId.value,
    targetLanguage = targetLanguage,
    nativeLanguage = nativeLanguage,
    regionFocus = regionFocus,
    defaultRegister = defaultRegister.name,
    reviewDirectionsJson = reviewDirections.encodeReviewDirectionsStable(),
    tagsJson = tags.encodeStringListStable(),
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

fun LanguageExpressionEntity.asExternalModel(): LanguageExpression = decodeModel("LanguageExpressionEntity") {
    LanguageExpression(
        id = LanguageExpressionId(id),
        studyPlanId = StudyPlanId(studyPlanId),
        deckId = LanguageDeckId(deckId),
        expression = expression,
        meaning = meaning,
        literalTranslation = literalTranslation,
        translationEquivalent = translationEquivalent,
        type = type.decodeEnum("LanguageExpressionEntity.type"),
        register = register.decodeEnum("LanguageExpressionEntity.register"),
        region = region,
        pronunciationNote = pronunciationNote,
        commonMistake = commonMistake,
        examples = examplesJson.decodeUsageExamplesStable("LanguageExpressionEntity.examplesJson"),
        tags = tagsJson.decodeStringListStable("LanguageExpressionEntity.tagsJson"),
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )
}

fun LanguageExpression.asEntity(): LanguageExpressionEntity = LanguageExpressionEntity(
    id = id.value,
    studyPlanId = studyPlanId.value,
    deckId = deckId.value,
    expression = expression,
    meaning = meaning,
    literalTranslation = literalTranslation,
    translationEquivalent = translationEquivalent,
    type = type.name,
    register = register.name,
    region = region,
    pronunciationNote = pronunciationNote,
    commonMistake = commonMistake,
    examplesJson = examples.encodeUsageExamplesStable(),
    tagsJson = tags.encodeStringListStable(),
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

fun IdiomCardEntity.asExternalModel(): IdiomCard = decodeModel("IdiomCardEntity") {
    IdiomCard(
        id = IdiomCardId(id),
        expressionId = LanguageExpressionId(expressionId),
        naturalExample = naturalExample,
        userSentence = userSentence,
        clozePrompt = clozePrompt,
        acceptedAnswers = acceptedAnswersJson.decodeStringListStable("IdiomCardEntity.acceptedAnswersJson"),
        reviewDirections = reviewDirectionsJson.decodeReviewDirectionsStable("IdiomCardEntity.reviewDirectionsJson"),
        difficulty = difficulty,
    )
}

fun IdiomCard.asEntity(): IdiomCardEntity = IdiomCardEntity(
    id = id.value,
    expressionId = expressionId.value,
    naturalExample = naturalExample,
    userSentence = userSentence,
    clozePrompt = clozePrompt,
    acceptedAnswersJson = acceptedAnswers.encodeStringListStable(),
    reviewDirectionsJson = reviewDirections.encodeReviewDirectionsStable(),
    difficulty = difficulty,
)

fun VocabularyCardEntity.asExternalModel(): VocabularyCard = decodeModel("VocabularyCardEntity") {
    VocabularyCard(
        id = VocabularyCardId(id),
        expressionId = LanguageExpressionId(expressionId),
        partOfSpeech = partOfSpeech,
        synonyms = synonymsJson.decodeStringListStable("VocabularyCardEntity.synonymsJson"),
        antonyms = antonymsJson.decodeStringListStable("VocabularyCardEntity.antonymsJson"),
        reviewDirections = reviewDirectionsJson.decodeReviewDirectionsStable("VocabularyCardEntity.reviewDirectionsJson"),
        difficulty = difficulty,
    )
}

fun VocabularyCard.asEntity(): VocabularyCardEntity = VocabularyCardEntity(
    id = id.value,
    expressionId = expressionId.value,
    partOfSpeech = partOfSpeech,
    synonymsJson = synonyms.encodeStringListStable(),
    antonymsJson = antonyms.encodeStringListStable(),
    reviewDirectionsJson = reviewDirections.encodeReviewDirectionsStable(),
    difficulty = difficulty,
)

fun LanguageReviewAttemptEntity.asExternalModel(): LanguageReviewAttempt = decodeModel("LanguageReviewAttemptEntity") {
    LanguageReviewAttempt(
        id = LanguageReviewAttemptId(id),
        expressionId = LanguageExpressionId(expressionId),
        idiomCardId = idiomCardId?.let(::IdiomCardId),
        direction = direction.decodeEnum<LanguageReviewDirection>("LanguageReviewAttemptEntity.direction"),
        promptText = promptText,
        answerText = answerText,
        outcome = outcome.decodeEnum<LanguageReviewOutcome>("LanguageReviewAttemptEntity.outcome"),
        scorePercent = scorePercent,
        reviewItemId = reviewItemId?.let(::ReviewItemId),
        createdAtEpochMillis = createdAtEpochMillis,
    )
}

fun LanguageReviewAttempt.asEntity(studyPlanId: StudyPlanId): LanguageReviewAttemptEntity = LanguageReviewAttemptEntity(
    id = id.value,
    studyPlanId = studyPlanId.value,
    expressionId = expressionId.value,
    idiomCardId = idiomCardId?.value,
    direction = direction.name,
    promptText = promptText,
    answerText = answerText,
    outcome = outcome.name,
    scorePercent = scorePercent,
    reviewItemId = reviewItemId?.value,
    createdAtEpochMillis = createdAtEpochMillis,
)
