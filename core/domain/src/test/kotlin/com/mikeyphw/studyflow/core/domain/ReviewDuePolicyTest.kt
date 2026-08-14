package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.ReviewItem
import com.mikeyphw.studyflow.core.model.ReviewItemId
import com.mikeyphw.studyflow.core.model.ReviewMethod
import com.mikeyphw.studyflow.core.model.ReviewStatus
import com.mikeyphw.studyflow.core.model.StudyPlanId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewDuePolicyTest {
    @Test
    fun scheduledItemMaturesByDueTimeAndTerminalItemsStayTerminal() {
        val scheduled = review(ReviewStatus.SCHEDULED, dueAt = 100L)
        assertFalse(ReviewDuePolicy.isDue(scheduled, 99L))
        assertTrue(ReviewDuePolicy.isDue(scheduled, 100L))
        assertFalse(ReviewDuePolicy.isDue(scheduled.copy(status = ReviewStatus.COMPLETED, completedAtEpochMillis = 100L), 200L))
    }

    private fun review(status: ReviewStatus, dueAt: Long) = ReviewItem(
        id = ReviewItemId("review"),
        studyPlanId = StudyPlanId("plan"),
        subjectId = null,
        topicId = null,
        questionId = null,
        flashcardId = null,
        errorEntryId = null,
        method = ReviewMethod.SPACED_REPETITION,
        status = status,
        dueAtEpochMillis = dueAt,
        intervalDays = 1,
        repetitionCount = 0,
        easeFactor = 2.5,
        completedAtEpochMillis = null,
    )
}
