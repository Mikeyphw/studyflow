package com.mikeyphw.studyflow.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LanguageReviewPracticeTest {
    @Test
    fun `idiom prompts cover fill blank and natural usage choices`() {
        val expression = sampleExpression()
        val card = expression.toIdiomCard(
            cardId = IdiomCardId("card-call-it-a-day"),
            clozePrompt = "Let's _____ and continue tomorrow.",
        )

        val fillBlank = buildLanguageReviewPrompt(
            expression = expression,
            idiomCard = card,
            direction = LanguageReviewDirection.FILL_IN_THE_BLANK,
        )
        val naturalUsage = buildLanguageReviewPrompt(
            expression = expression,
            idiomCard = card,
            direction = LanguageReviewDirection.CHOOSE_NATURAL_USAGE,
        )

        assertTrue(fillBlank.promptText.contains("_____"))
        assertEquals(LanguageReviewAnswerKind.MULTIPLE_CHOICE, naturalUsage.kind)
        assertEquals(1, naturalUsage.choices.count { it.expected })
        assertFalse(naturalUsage.requiresManualReview)
    }

    @Test
    fun `answer evaluation feeds spaced review item scheduling`() {
        val expression = sampleExpression()
        val card = expression.toIdiomCard(cardId = IdiomCardId("card-call-it-a-day"))
        val prompt = buildLanguageReviewPrompt(
            expression = expression,
            idiomCard = card,
            direction = LanguageReviewDirection.MEANING_TO_EXPRESSION,
        )

        val attempt = evaluateLanguageReviewAnswer(
            prompt = prompt,
            attemptId = LanguageReviewAttemptId("attempt-1"),
            answerText = "Call it a day!",
            createdAtEpochMillis = 1_000L,
        )
        val decision = attempt.toSpacedReviewDecision(
            reviewItemId = ReviewItemId("review-1"),
            studyPlanId = expression.studyPlanId,
            flashcardId = FlashcardId("flashcard-1"),
            nowEpochMillis = 1_000L,
        )

        assertEquals(LanguageReviewOutcome.CORRECT, attempt.outcome)
        assertEquals(ReviewMethod.SPACED_REPETITION, decision.reviewItem.method)
        assertEquals(ReviewStatus.SCHEDULED, decision.reviewItem.status)
        assertEquals(4, decision.nextIntervalDays)
    }

    @Test
    fun `sentence creation stays manual instead of pretending to auto grade`() {
        val expression = sampleExpression()
        val card = expression.toIdiomCard(cardId = IdiomCardId("card-call-it-a-day"))
        val prompt = buildLanguageReviewPrompt(
            expression = expression,
            idiomCard = card,
            direction = LanguageReviewDirection.CREATE_YOUR_OWN_SENTENCE,
        )

        val attempt = evaluateLanguageReviewAnswer(
            prompt = prompt,
            attemptId = LanguageReviewAttemptId("attempt-2"),
            answerText = "After practice, let's call it a day.",
            createdAtEpochMillis = 1_000L,
        )

        assertTrue(prompt.requiresManualReview)
        assertEquals(LanguageReviewOutcome.NEEDS_MANUAL_REVIEW, attempt.outcome)
    }


    @Test
    fun `free-form sentence scoring rejects gibberish below valid target usage`() {
        val expression = sampleExpression()
        val card = expression.toIdiomCard(cardId = IdiomCardId("card-call-it-a-day"))
        val prompt = buildLanguageReviewPrompt(
            expression = expression,
            idiomCard = card,
            direction = LanguageReviewDirection.CREATE_YOUR_OWN_SENTENCE,
        )

        val gibberish = evaluateLanguageReviewAnswer(
            prompt = prompt,
            attemptId = LanguageReviewAttemptId("attempt-gibberish"),
            answerText = "asdf qwer zxcv",
            createdAtEpochMillis = 1_000L,
        )
        val validUsage = evaluateLanguageReviewAnswer(
            prompt = prompt,
            attemptId = LanguageReviewAttemptId("attempt-usage"),
            answerText = "After practice, let's call it a day.",
            createdAtEpochMillis = 1_001L,
        )

        assertEquals(LanguageReviewOutcome.INCORRECT, gibberish.outcome)
        assertTrue(gibberish.scorePercent < validUsage.scorePercent)
        assertEquals(LanguageReviewOutcome.NEEDS_MANUAL_REVIEW, validUsage.outcome)
    }

    @Test
    fun `spot mistake rejects repeated mistake and literal-only answers`() {
        val expression = sampleExpression()
        val card = expression.toIdiomCard(cardId = IdiomCardId("card-call-it-a-day"))
        val prompt = buildLanguageReviewPrompt(
            expression = expression,
            idiomCard = card,
            direction = LanguageReviewDirection.SPOT_THE_MISTAKE,
        )

        val repeated = evaluateLanguageReviewAnswer(
            prompt = prompt,
            attemptId = LanguageReviewAttemptId("attempt-repeat"),
            answerText = prompt.promptText,
            createdAtEpochMillis = 1_000L,
        )
        val literalOnly = evaluateLanguageReviewAnswer(
            prompt = prompt,
            attemptId = LanguageReviewAttemptId("attempt-literal"),
            answerText = "literal",
            createdAtEpochMillis = 1_001L,
        )
        val explained = evaluateLanguageReviewAnswer(
            prompt = prompt,
            attemptId = LanguageReviewAttemptId("attempt-explain"),
            answerText = "It is wrong because the idiom means stop working for now, not a literal day.",
            createdAtEpochMillis = 1_002L,
        )

        assertEquals(LanguageReviewOutcome.INCORRECT, repeated.outcome)
        assertEquals(LanguageReviewOutcome.INCORRECT, literalOnly.outcome)
        assertEquals(LanguageReviewOutcome.CORRECT, explained.outcome)
    }

    @Test
    fun `natural usage choices use deterministic non-fixed answer positions`() {
        val first = sampleExpression()
        val second = sampleExpression().copy(id = LanguageExpressionId("expr-break-the-ice"))
        val firstPrompt = buildLanguageReviewPrompt(
            expression = first,
            idiomCard = first.toIdiomCard(cardId = IdiomCardId("card-one")),
            direction = LanguageReviewDirection.CHOOSE_NATURAL_USAGE,
        )
        val secondPrompt = buildLanguageReviewPrompt(
            expression = second,
            idiomCard = second.toIdiomCard(cardId = IdiomCardId("card-two")),
            direction = LanguageReviewDirection.CHOOSE_NATURAL_USAGE,
        )

        val answerIndexes = setOf(
            firstPrompt.choices.indexOfFirst { it.expected },
            secondPrompt.choices.indexOfFirst { it.expected },
        )
        assertTrue(answerIndexes.any { it > 0 })
        assertEquals(1, firstPrompt.choices.count { it.expected })
        assertEquals(1, secondPrompt.choices.count { it.expected })
    }

    private fun sampleExpression(): LanguageExpression = LanguageExpression(
        id = LanguageExpressionId("expr-call-it-a-day"),
        studyPlanId = StudyPlanId("plan-idioms"),
        deckId = LanguageDeckId("deck-english"),
        expression = "call it a day",
        meaning = "stop working for now",
        literalTranslation = "chamar isso de um dia",
        translationEquivalent = "encerrar por hoje",
        type = LanguageExpressionType.IDIOM,
        register = LanguageRegister.CASUAL,
        region = "UK",
        pronunciationNote = null,
        commonMistake = "Do not translate it literally.",
        examples = listOf(UsageExample("Let's call it a day.", "Vamos encerrar por hoje.", null)),
        tags = listOf("work"),
        createdAtEpochMillis = 1L,
        updatedAtEpochMillis = 1L,
    )
}
