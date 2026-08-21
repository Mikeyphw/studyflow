package com.mikeyphw.studyflow.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LanguageLearningTest {
    @Test
    fun `idiom deck captures language-specific fields`() {
        val deck = LanguageDeckProfile(
            id = LanguageDeckId("english-idioms"),
            studyPlanId = StudyPlanId("plan-idioms"),
            targetLanguage = "English",
            nativeLanguage = "Portuguese",
            regionFocus = "UK",
            defaultRegister = LanguageRegister.CASUAL,
            reviewDirections = defaultLanguageReviewDirections(LanguageExpressionType.IDIOM),
            tags = listOf("work"),
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 1L,
        )
        val expression = sampleIdiom()

        assertEquals("English", deck.targetLanguage)
        assertEquals("call it a day", expression.expression)
        assertEquals("stop working for now", expression.meaning)
        assertEquals("UK", expression.region)
        assertTrue(expression.commonMistake!!.contains("literal"))
    }

    @Test
    fun `idiom cards enable recognition production usage and sentence creation`() {
        val card = sampleIdiom().toIdiomCard(
            cardId = IdiomCardId("card-call-it-a-day"),
            clozePrompt = "Let's _____ and continue tomorrow.",
            userSentence = "After three hours of debugging, let's call it a day.",
        )

        assertTrue(LanguageReviewDirection.EXPRESSION_TO_MEANING in card.reviewDirections)
        assertTrue(LanguageReviewDirection.MEANING_TO_EXPRESSION in card.reviewDirections)
        assertTrue(LanguageReviewDirection.FILL_IN_THE_BLANK in card.reviewDirections)
        assertTrue(LanguageReviewDirection.CHOOSE_NATURAL_USAGE in card.reviewDirections)
        assertTrue(LanguageReviewDirection.CREATE_YOUR_OWN_SENTENCE in card.reviewDirections)
    }

    @Test
    fun `idiom cards reject generic empty answers`() {
        assertFailsWith<IllegalArgumentException> {
            IdiomCard(
                id = IdiomCardId("bad"),
                expressionId = LanguageExpressionId("expr"),
                naturalExample = "Example",
                userSentence = null,
                clozePrompt = null,
                acceptedAnswers = emptyList(),
                reviewDirections = defaultLanguageReviewDirections(LanguageExpressionType.IDIOM),
                difficulty = 2,
            )
        }
    }

    private fun sampleIdiom(): LanguageExpression = LanguageExpression(
        id = LanguageExpressionId("expr-call-it-a-day"),
        studyPlanId = StudyPlanId("plan-idioms"),
        deckId = LanguageDeckId("english-idioms"),
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
        tags = listOf("work", "daily speech"),
        createdAtEpochMillis = 1L,
        updatedAtEpochMillis = 1L,
    )
}
