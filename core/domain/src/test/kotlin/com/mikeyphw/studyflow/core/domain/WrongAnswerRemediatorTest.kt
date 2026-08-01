package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.ErrorCategory
import com.mikeyphw.studyflow.core.model.ErrorEntry
import com.mikeyphw.studyflow.core.model.ErrorEntryId
import com.mikeyphw.studyflow.core.model.QuestionId
import com.mikeyphw.studyflow.core.model.ReviewItem
import com.mikeyphw.studyflow.core.model.ReviewItemId
import com.mikeyphw.studyflow.core.model.ReviewMethod
import com.mikeyphw.studyflow.core.model.ReviewStatus
import com.mikeyphw.studyflow.core.model.StudyPlanId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WrongAnswerRemediatorTest {
    private val planId = StudyPlanId("plan")
    private val error = error(recurrence = 4)

    @Test
    fun repeatedConceptErrorsAreHighPriority() {
        val notebook = WrongAnswerRemediator.buildNotebook(
            errors = listOf(error),
            reviewItems = emptyList(),
            nowEpochMillis = 100,
        )

        assertEquals(1, notebook.activeCount)
        assertEquals(1, notebook.highPriorityCount)
        assertEquals(RemediationPriority.HIGH, notebook.entries.single().priority)
        assertEquals(RemediationSuggestion.REVIEW_THEORY, notebook.entries.single().suggestion)
    }

    @Test
    fun overdueReviewRaisesPriorityAndIsLinked() {
        val notebook = WrongAnswerRemediator.buildNotebook(
            errors = listOf(error(recurrence = 1)),
            reviewItems = listOf(review(dueAt = 1)),
            nowEpochMillis = 100,
        )

        assertEquals(RemediationPriority.HIGH, notebook.entries.single().priority)
        assertTrue(notebook.entries.single().hasPendingReview)
        assertTrue(notebook.entries.single().reviewIsDue)
    }

    private fun error(recurrence: Int) = ErrorEntry(
        id = ErrorEntryId("error"),
        studyPlanId = planId,
        questionId = QuestionId("question"),
        subjectId = null,
        topicId = null,
        category = ErrorCategory.CONCEPT_CONFUSION,
        chosenAnswer = "A",
        correctAnswer = "B",
        explanation = "Concept gap.",
        recurrenceCount = recurrence,
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = recurrence.toLong(),
    )

    private fun review(dueAt: Long) = ReviewItem(
        id = ReviewItemId("review"),
        studyPlanId = planId,
        subjectId = null,
        topicId = null,
        questionId = QuestionId("question"),
        flashcardId = null,
        errorEntryId = ErrorEntryId("error"),
        method = ReviewMethod.ERROR_BASED,
        status = ReviewStatus.SCHEDULED,
        dueAtEpochMillis = dueAt,
        intervalDays = 0,
        repetitionCount = 0,
        easeFactor = 2.5,
        completedAtEpochMillis = null,
    )
}
