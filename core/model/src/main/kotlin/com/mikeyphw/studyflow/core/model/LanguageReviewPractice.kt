package com.mikeyphw.studyflow.core.model

private const val OneDayMillis: Long = 24L * 60L * 60L * 1_000L

enum class LanguageReviewAnswerKind {
    TEXT,
    MULTIPLE_CHOICE,
    SENTENCE,
}

enum class LanguageReviewOutcome {
    CORRECT,
    PARTIAL,
    INCORRECT,
    NEEDS_MANUAL_REVIEW,
}

data class LanguageReviewChoice(
    val label: String,
    val expected: Boolean,
) {
    init {
        require(label.isNotBlank()) { "Language review choice cannot be blank." }
    }
}

data class LanguageReviewPrompt(
    val expressionId: LanguageExpressionId,
    val idiomCardId: IdiomCardId?,
    val direction: LanguageReviewDirection,
    val kind: LanguageReviewAnswerKind,
    val promptText: String,
    val instruction: String,
    val acceptedAnswers: List<String>,
    val choices: List<LanguageReviewChoice>,
    val exampleSentence: String?,
    val requiresManualReview: Boolean,
) {
    init {
        require(promptText.isNotBlank()) { "Language review prompt cannot be blank." }
        require(instruction.isNotBlank()) { "Language review instruction cannot be blank." }
        if (!requiresManualReview) {
            require(acceptedAnswers.isNotEmpty()) { "Auto-graded prompts need accepted answers." }
        }
        if (kind == LanguageReviewAnswerKind.MULTIPLE_CHOICE) {
            require(choices.count { it.expected } == 1) { "Multiple-choice prompts need exactly one expected option." }
            require(choices.size >= 2) { "Multiple-choice prompts need at least two options." }
        }
    }
}

data class LanguageReviewAttempt(
    val id: LanguageReviewAttemptId,
    val expressionId: LanguageExpressionId,
    val idiomCardId: IdiomCardId?,
    val direction: LanguageReviewDirection,
    val promptText: String,
    val answerText: String,
    val outcome: LanguageReviewOutcome,
    val scorePercent: Int,
    val reviewItemId: ReviewItemId?,
    val createdAtEpochMillis: Long,
) {
    init {
        require(promptText.isNotBlank()) { "Language review attempt prompt cannot be blank." }
        require(scorePercent in 0..100) { "Language review score must be 0 to 100." }
        require(createdAtEpochMillis >= 0) { "Language review timestamp cannot be negative." }
    }
}

data class LanguageReviewSchedulingDecision(
    val reviewItem: ReviewItem,
    val nextIntervalDays: Int,
    val reason: String,
)

fun buildLanguageReviewPrompt(
    expression: LanguageExpression,
    idiomCard: IdiomCard,
    direction: LanguageReviewDirection,
): LanguageReviewPrompt {
    require(expression.id == idiomCard.expressionId) { "Idiom card must belong to the expression." }
    require(direction in idiomCard.reviewDirections) { "Review direction is not enabled for this idiom card." }

    val answers = normalizedAcceptedAnswers(expression, idiomCard)
    return when (direction) {
        LanguageReviewDirection.EXPRESSION_TO_MEANING -> LanguageReviewPrompt(
            expressionId = expression.id,
            idiomCardId = idiomCard.id,
            direction = direction,
            kind = LanguageReviewAnswerKind.TEXT,
            promptText = expression.expression,
            instruction = "Explain the meaning naturally.",
            acceptedAnswers = listOfNotNull(expression.meaning, expression.translationEquivalent),
            choices = emptyList(),
            exampleSentence = idiomCard.naturalExample,
            requiresManualReview = false,
        )
        LanguageReviewDirection.MEANING_TO_EXPRESSION -> LanguageReviewPrompt(
            expressionId = expression.id,
            idiomCardId = idiomCard.id,
            direction = direction,
            kind = LanguageReviewAnswerKind.TEXT,
            promptText = expression.meaning,
            instruction = "Recall the expression or idiom.",
            acceptedAnswers = answers,
            choices = emptyList(),
            exampleSentence = idiomCard.naturalExample,
            requiresManualReview = false,
        )
        LanguageReviewDirection.FILL_IN_THE_BLANK -> LanguageReviewPrompt(
            expressionId = expression.id,
            idiomCardId = idiomCard.id,
            direction = direction,
            kind = LanguageReviewAnswerKind.TEXT,
            promptText = idiomCard.clozePrompt
                ?: idiomCard.naturalExample.replaceExpressionWithBlank(expression.expression),
            instruction = "Fill in the missing expression.",
            acceptedAnswers = answers,
            choices = emptyList(),
            exampleSentence = idiomCard.naturalExample,
            requiresManualReview = false,
        )
        LanguageReviewDirection.CHOOSE_NATURAL_USAGE -> {
            val choices = naturalUsageChoices(expression, idiomCard)
            LanguageReviewPrompt(
                expressionId = expression.id,
                idiomCardId = idiomCard.id,
                direction = direction,
                kind = LanguageReviewAnswerKind.MULTIPLE_CHOICE,
                promptText = expression.expression,
                instruction = "Choose the natural usage.",
                acceptedAnswers = listOf(idiomCard.naturalExample),
                choices = choices,
                exampleSentence = idiomCard.naturalExample,
                requiresManualReview = false,
            )
        }
        LanguageReviewDirection.SPOT_THE_MISTAKE -> LanguageReviewPrompt(
            expressionId = expression.id,
            idiomCardId = idiomCard.id,
            direction = direction,
            kind = LanguageReviewAnswerKind.TEXT,
            promptText = expression.commonMistake ?: expression.literalTranslation ?: expression.expression,
            instruction = "Explain what is unnatural or wrong; do not repeat the mistake.",
            acceptedAnswers = listOfNotNull(
                "The literal wording is unnatural; use ${expression.expression} to mean ${expression.meaning}.",
                expression.meaning,
                idiomCard.naturalExample,
            ),
            choices = emptyList(),
            exampleSentence = idiomCard.naturalExample,
            requiresManualReview = false,
        )
        LanguageReviewDirection.CREATE_YOUR_OWN_SENTENCE -> LanguageReviewPrompt(
            expressionId = expression.id,
            idiomCardId = idiomCard.id,
            direction = direction,
            kind = LanguageReviewAnswerKind.SENTENCE,
            promptText = expression.expression,
            instruction = "Create your own natural sentence using the expression.",
            acceptedAnswers = answers,
            choices = emptyList(),
            exampleSentence = idiomCard.naturalExample,
            requiresManualReview = true,
        )
    }
}

fun evaluateLanguageReviewAnswer(
    prompt: LanguageReviewPrompt,
    attemptId: LanguageReviewAttemptId,
    answerText: String,
    createdAtEpochMillis: Long,
    reviewItemId: ReviewItemId? = null,
): LanguageReviewAttempt {
    val normalizedAnswer = normalizeLanguageAnswer(answerText)
    val outcome = when {
        normalizedAnswer.isBlank() -> LanguageReviewOutcome.INCORRECT
        prompt.direction == LanguageReviewDirection.SPOT_THE_MISTAKE -> spotTheMistakeReviewOutcome(
            prompt,
            normalizedAnswer,
        )
        prompt.requiresManualReview -> sentenceReviewOutcome(prompt, normalizedAnswer)
        prompt.kind == LanguageReviewAnswerKind.MULTIPLE_CHOICE -> multipleChoiceReviewOutcome(prompt, normalizedAnswer)
        prompt.acceptedAnswers.any { accepted -> normalizeLanguageAnswer(accepted) == normalizedAnswer } -> {
            LanguageReviewOutcome.CORRECT
        }
        prompt.acceptedAnswers.any { accepted -> normalizedAnswer in normalizeLanguageAnswer(accepted) } -> {
            LanguageReviewOutcome.PARTIAL
        }
        prompt.acceptedAnswers.any { accepted -> normalizeLanguageAnswer(accepted) in normalizedAnswer } -> {
            LanguageReviewOutcome.PARTIAL
        }
        else -> LanguageReviewOutcome.INCORRECT
    }
    return LanguageReviewAttempt(
        id = attemptId,
        expressionId = prompt.expressionId,
        idiomCardId = prompt.idiomCardId,
        direction = prompt.direction,
        promptText = prompt.promptText,
        answerText = answerText,
        outcome = outcome,
        scorePercent = scorePercentFor(outcome),
        reviewItemId = reviewItemId,
        createdAtEpochMillis = createdAtEpochMillis,
    )
}

fun LanguageExpression.toLanguageReviewFlashcard(
    flashcardId: FlashcardId,
): Flashcard = Flashcard(
    id = flashcardId,
    studyPlanId = studyPlanId,
    subjectId = null,
    topicId = null,
    type = FlashcardType.BASIC,
    front = expression,
    back = listOfNotNull(meaning, translationEquivalent, examples.firstOrNull()?.sentence).joinToString("\n"),
    clozeText = null,
    tags = tags + type.name.lowercase(),
    suspended = false,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

fun LanguageReviewAttempt.toSpacedReviewDecision(
    reviewItemId: ReviewItemId,
    studyPlanId: StudyPlanId,
    flashcardId: FlashcardId,
    nowEpochMillis: Long,
): LanguageReviewSchedulingDecision {
    val nextIntervalDays = when (outcome) {
        LanguageReviewOutcome.CORRECT -> 4
        LanguageReviewOutcome.PARTIAL -> 1
        LanguageReviewOutcome.NEEDS_MANUAL_REVIEW -> 1
        LanguageReviewOutcome.INCORRECT -> 0
    }
    val status = if (nextIntervalDays == 0) ReviewStatus.DUE else ReviewStatus.SCHEDULED
    val reviewItem = ReviewItem(
        id = reviewItemId,
        studyPlanId = studyPlanId,
        subjectId = null,
        topicId = null,
        questionId = null,
        flashcardId = flashcardId,
        errorEntryId = null,
        method = ReviewMethod.SPACED_REPETITION,
        status = status,
        dueAtEpochMillis = nowEpochMillis + nextIntervalDays * OneDayMillis,
        intervalDays = nextIntervalDays,
        repetitionCount = if (outcome == LanguageReviewOutcome.CORRECT) 1 else 0,
        easeFactor = when (outcome) {
            LanguageReviewOutcome.CORRECT -> 2.5
            LanguageReviewOutcome.PARTIAL -> 2.0
            LanguageReviewOutcome.NEEDS_MANUAL_REVIEW -> 1.8
            LanguageReviewOutcome.INCORRECT -> 1.3
        },
        completedAtEpochMillis = null,
    )
    return LanguageReviewSchedulingDecision(
        reviewItem = reviewItem,
        nextIntervalDays = nextIntervalDays,
        reason = "language:${direction.name.lowercase()}:${outcome.name.lowercase()}",
    )
}

fun normalizeLanguageAnswer(value: String): String = value
    .trim()
    .lowercase()
    .replace(Regex("\\s+"), " ")
    .trim('.', '!', '?', ',', ';', ':', '"', '\'')

private fun normalizedAcceptedAnswers(
    expression: LanguageExpression,
    idiomCard: IdiomCard,
): List<String> = (idiomCard.acceptedAnswers + expression.expression)
    .map { it.trim() }
    .filter { it.isNotEmpty() }
    .distinctBy { normalizeLanguageAnswer(it) }

private fun String.replaceExpressionWithBlank(expression: String): String = replace(
    oldValue = expression,
    newValue = "_____",
    ignoreCase = true,
).takeIf { it != this } ?: "$this\n_____"

private fun naturalUsageChoices(
    expression: LanguageExpression,
    idiomCard: IdiomCard,
): List<LanguageReviewChoice> {
    val distractors = listOfNotNull(
        expression.commonMistake,
        expression.literalTranslation?.let { "Literal translation: $it" },
        idiomCard.clozePrompt,
    ).filter { it.isNotBlank() && normalizeLanguageAnswer(it) != normalizeLanguageAnswer(idiomCard.naturalExample) }
        .distinctBy(::normalizeLanguageAnswer)
        .take(3)
        .map { LanguageReviewChoice(it, expected = false) }
        .ifEmpty {
            listOf(
                LanguageReviewChoice("Use it as a word-for-word translation.", expected = false),
            )
        }

    return deterministicLanguageReviewChoiceOrder(
        correct = LanguageReviewChoice(idiomCard.naturalExample, expected = true),
        distractors = distractors,
        seed = "${expression.studyPlanId.value}:${expression.id.value}:${idiomCard.id.value}",
    )
}

internal fun deterministicLanguageReviewChoiceOrder(
    correct: LanguageReviewChoice,
    distractors: List<LanguageReviewChoice>,
    seed: String,
): List<LanguageReviewChoice> {
    require(correct.expected) { "Correct language choice must be marked expected." }
    require(distractors.all { !it.expected }) { "Distractors cannot be expected choices." }
    val orderedDistractors = distractors
        .distinctBy { normalizeLanguageAnswer(it.label) }
        .sortedBy { stableLanguageChoiceHash("$seed:${it.label}") }
    val size = orderedDistractors.size + 1
    val correctIndex = stableLanguageChoiceHash("$seed:correct:${correct.label}") % size
    return buildList(size) {
        orderedDistractors.forEachIndexed { index, choice ->
            if (index == correctIndex) add(correct)
            add(choice)
        }
        if (size - 1 == correctIndex) add(correct)
    }
}

private fun multipleChoiceReviewOutcome(
    prompt: LanguageReviewPrompt,
    normalizedAnswer: String,
): LanguageReviewOutcome {
    val expected = prompt.choices.singleOrNull { it.expected } ?: return LanguageReviewOutcome.INCORRECT
    return if (normalizeLanguageAnswer(expected.label) == normalizedAnswer) {
        LanguageReviewOutcome.CORRECT
    } else {
        LanguageReviewOutcome.INCORRECT
    }
}

private fun spotTheMistakeReviewOutcome(
    prompt: LanguageReviewPrompt,
    normalizedAnswer: String,
): LanguageReviewOutcome {
    val promptText = normalizeLanguageAnswer(prompt.promptText)
    if (normalizedAnswer == promptText || promptText in normalizedAnswer || normalizedAnswer in promptText) {
        return LanguageReviewOutcome.INCORRECT
    }
    if (normalizedAnswer in setOf("literal", "literal translation", "word for word", "literalmente")) {
        return LanguageReviewOutcome.INCORRECT
    }
    val hasMistakeReason = listOf(
        "wrong", "mistake", "unnatural", "natural", "literal", "translation", "idiom",
        "meaning", "instead", "correct", "errado", "erro", "natural", "literalmente", "tradução",
    ).any { marker -> marker in normalizedAnswer }
    val referencesAcceptedMeaning = prompt.acceptedAnswers.any { accepted ->
        val normalizedAccepted = normalizeLanguageAnswer(accepted)
        normalizedAccepted.split(' ').size >= 2 && normalizedAccepted in normalizedAnswer
    }
    return when {
        !hasMistakeReason -> LanguageReviewOutcome.INCORRECT
        referencesAcceptedMeaning || normalizedAnswer.split(' ').size >= 5 -> LanguageReviewOutcome.CORRECT
        else -> LanguageReviewOutcome.PARTIAL
    }
}

private fun sentenceReviewOutcome(
    prompt: LanguageReviewPrompt,
    normalizedAnswer: String,
): LanguageReviewOutcome {
    if (looksLikeLanguageGibberish(normalizedAnswer)) return LanguageReviewOutcome.INCORRECT
    val promptText = normalizeLanguageAnswer(prompt.promptText)
    if (normalizedAnswer == promptText || normalizedAnswer in prompt.acceptedAnswers.map(::normalizeLanguageAnswer)) {
        return LanguageReviewOutcome.PARTIAL
    }
    val targetUsage = prompt.acceptedAnswers.any { accepted ->
        val normalizedAccepted = normalizeLanguageAnswer(accepted)
        normalizedAccepted.isNotBlank() && normalizedAccepted in normalizedAnswer
    }
    val wordCount = normalizedAnswer.split(' ').filter(String::isNotBlank).size
    return when {
        targetUsage && wordCount >= 4 -> LanguageReviewOutcome.NEEDS_MANUAL_REVIEW
        targetUsage -> LanguageReviewOutcome.PARTIAL
        else -> LanguageReviewOutcome.INCORRECT
    }
}

private fun looksLikeLanguageGibberish(normalizedAnswer: String): Boolean {
    val tokens = normalizedAnswer.split(' ').filter(String::isNotBlank)
    if (tokens.isEmpty()) return true
    if (tokens.distinct().size == 1 && tokens.size > 1) return true
    if (tokens.any { token -> token in setOf("asdf", "qwer", "zxcv", "lorem", "ipsum", "blah") }) return true
    val letters = tokens.joinToString("").filter(Char::isLetter)
    if (letters.length < 3) return true
    val vowelCount = letters.count { it.lowercaseChar() in "aeiouáéíóúâêôãõàü" }
    return letters.length >= 8 && vowelCount == 0
}

private fun stableLanguageChoiceHash(value: String): Int = value.fold(0x45d9f3b) { acc, char ->
    ((acc * 31) xor char.code).and(0x7fffffff)
}

private fun scorePercentFor(outcome: LanguageReviewOutcome): Int = when (outcome) {
    LanguageReviewOutcome.CORRECT -> 100
    LanguageReviewOutcome.PARTIAL -> 60
    LanguageReviewOutcome.NEEDS_MANUAL_REVIEW -> 50
    LanguageReviewOutcome.INCORRECT -> 0
}
