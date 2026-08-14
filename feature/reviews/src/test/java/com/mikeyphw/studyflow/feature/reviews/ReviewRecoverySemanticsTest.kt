package com.mikeyphw.studyflow.feature.reviews

import com.mikeyphw.studyflow.core.domain.ReviewQueue
import com.mikeyphw.studyflow.core.model.ErrorCategory
import com.mikeyphw.studyflow.core.model.ErrorEntry
import com.mikeyphw.studyflow.core.model.ErrorEntryId
import com.mikeyphw.studyflow.core.model.Flashcard
import com.mikeyphw.studyflow.core.model.FlashcardId
import com.mikeyphw.studyflow.core.model.FlashcardType
import com.mikeyphw.studyflow.core.model.ReviewItem
import com.mikeyphw.studyflow.core.model.ReviewItemId
import com.mikeyphw.studyflow.core.model.ReviewMethod
import com.mikeyphw.studyflow.core.model.ReviewStatus
import com.mikeyphw.studyflow.core.model.StudyPlanId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewRecoverySemanticsTest {
    private val planId = StudyPlanId("plan")

    @Test
    fun retryReconciliationRecognizesAlreadyCommittedMutations() {
        val errorId = ErrorEntryId("error")
        val completed = reviewItem("graded", ReviewStatus.COMPLETED)
        val scheduledError = reviewItem("error-review", ReviewStatus.DUE, errorId = errorId)
        val card = flashcard("card")
        val queue = ReviewQueue(
            due = listOf(scheduledError),
            upcoming = emptyList(),
            completedToday = listOf(completed),
            flashcards = listOf(card),
            errors = emptyList(),
        )

        assertTrue(queue.alreadyReflects(ReviewMutation.Grade(completed.id, 4)))
        assertTrue(queue.alreadyReflects(ReviewMutation.Skip(ReviewItemId("skipped"))))
        assertTrue(queue.alreadyReflects(ReviewMutation.ScheduleError(errorId)))
        assertTrue(queue.alreadyReflects(ReviewMutation.ResolveError(ErrorEntryId("resolved"))))
        assertTrue(queue.alreadyReflects(ReviewMutation.CreateFlashcard(card.id, "front", "back")))
    }

    @Test
    fun retryReconciliationDoesNotClaimUncommittedMutation() {
        val error = error("still-present")
        val active = reviewItem("active", ReviewStatus.DUE)
        val queue = ReviewQueue(
            due = listOf(active),
            upcoming = emptyList(),
            completedToday = emptyList(),
            flashcards = emptyList(),
            errors = listOf(error),
        )

        assertFalse(queue.alreadyReflects(ReviewMutation.Grade(active.id, 4)))
        assertFalse(queue.alreadyReflects(ReviewMutation.Skip(active.id)))
        assertFalse(queue.alreadyReflects(ReviewMutation.ScheduleError(error.id)))
        assertFalse(queue.alreadyReflects(ReviewMutation.ResolveError(error.id)))
        assertFalse(queue.alreadyReflects(ReviewMutation.CreateFlashcard(FlashcardId("missing"), "front", "back")))
    }

    private fun reviewItem(
        id: String,
        status: ReviewStatus,
        errorId: ErrorEntryId? = null,
    ) = ReviewItem(
        id = ReviewItemId(id),
        studyPlanId = planId,
        subjectId = null,
        topicId = null,
        questionId = null,
        flashcardId = null,
        errorEntryId = errorId,
        method = ReviewMethod.MANUAL,
        status = status,
        dueAtEpochMillis = 0L,
        intervalDays = 0,
        repetitionCount = 0,
        easeFactor = 2.5,
        completedAtEpochMillis = if (status == ReviewStatus.COMPLETED) 1L else null,
    )

    private fun flashcard(id: String) = Flashcard(
        id = FlashcardId(id),
        studyPlanId = planId,
        subjectId = null,
        topicId = null,
        type = FlashcardType.BASIC,
        front = "front",
        back = "back",
        clozeText = null,
        tags = emptyList(),
        suspended = false,
        createdAtEpochMillis = 0L,
        updatedAtEpochMillis = 0L,
    )

    private fun error(id: String) = ErrorEntry(
        id = ErrorEntryId(id),
        studyPlanId = planId,
        questionId = null,
        subjectId = null,
        topicId = null,
        category = ErrorCategory.OTHER,
        chosenAnswer = null,
        correctAnswer = null,
        explanation = "explanation",
        recurrenceCount = 1,
        createdAtEpochMillis = 0L,
        updatedAtEpochMillis = 0L,
    )
}
