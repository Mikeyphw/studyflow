package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.Question
import com.mikeyphw.studyflow.core.model.QuestionId
import com.mikeyphw.studyflow.core.model.QuestionType
import com.mikeyphw.studyflow.core.model.StudyPlanId
import org.junit.Assert.assertEquals
import org.junit.Test

class PracticeAnalyticsTest {
    private val planId = StudyPlanId("plan")

    @Test
    fun accuracyRoundsCorrectly() {
        assertEquals(67, PracticeAnalytics.accuracyPercent(correct = 2, answered = 3))
        assertEquals(0, PracticeAnalytics.accuracyPercent(correct = 0, answered = 0))
    }

    @Test
    fun deckPrioritizesDisputedThenRecentQuestions() {
        val older = question("older", disputed = false, updated = 10)
        val recent = question("recent", disputed = false, updated = 30)
        val disputed = question("disputed", disputed = true, updated = 20)

        val deck = PracticeAnalytics.buildDeck(
            questions = listOf(older, recent, disputed),
            request = PracticeDeckRequest(limit = 2),
        )

        assertEquals(listOf("disputed", "recent"), deck.map { it.id.value })
    }

    private fun question(id: String, disputed: Boolean, updated: Long) = Question(
        id = QuestionId(id),
        studyPlanId = planId,
        subjectId = null,
        topicId = null,
        type = QuestionType.MULTIPLE_CHOICE,
        statement = "Statement $id",
        options = listOf("A", "B"),
        correctAnswer = "A",
        explanation = "Explanation",
        examBoard = null,
        source = null,
        year = null,
        difficulty = 3,
        favorite = false,
        disputed = disputed,
        tags = emptyList(),
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = updated,
    )
}
