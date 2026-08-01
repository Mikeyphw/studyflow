package com.mikeyphw.studyflow.feature.reviews

import com.mikeyphw.studyflow.core.domain.ReviewQueue
import com.mikeyphw.studyflow.core.model.ReviewItem
import com.mikeyphw.studyflow.core.model.ReviewItemId
import com.mikeyphw.studyflow.core.model.ReviewMethod
import com.mikeyphw.studyflow.core.model.ReviewStatus
import com.mikeyphw.studyflow.core.model.StudyPlanId
import org.junit.Assert.assertEquals
import org.junit.Test

class ReviewUiModelsTest {
    @Test
    fun itemsForReturnsSelectedBucket() {
        val due = item("due")
        val upcoming = item("upcoming")
        val completed = item("completed")
        val queue = ReviewQueue(
            due = listOf(due),
            upcoming = listOf(upcoming),
            completedToday = listOf(completed),
            flashcards = emptyList(),
            errors = emptyList(),
        )

        assertEquals(listOf(due), queue.itemsFor(ReviewFilter.DUE))
        assertEquals(listOf(upcoming), queue.itemsFor(ReviewFilter.UPCOMING))
        assertEquals(listOf(completed), queue.itemsFor(ReviewFilter.COMPLETED))
    }

    private fun item(id: String) = ReviewItem(
        id = ReviewItemId(id),
        studyPlanId = StudyPlanId("plan"),
        subjectId = null,
        topicId = null,
        questionId = null,
        flashcardId = null,
        errorEntryId = null,
        method = ReviewMethod.MANUAL,
        status = ReviewStatus.DUE,
        dueAtEpochMillis = 0L,
        intervalDays = 0,
        repetitionCount = 0,
        easeFactor = 2.5,
        completedAtEpochMillis = null,
    )
}
