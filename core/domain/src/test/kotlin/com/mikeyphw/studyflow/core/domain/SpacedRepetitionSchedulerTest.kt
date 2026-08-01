package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.ReviewItem
import com.mikeyphw.studyflow.core.model.ReviewItemId
import com.mikeyphw.studyflow.core.model.ReviewMethod
import com.mikeyphw.studyflow.core.model.ReviewStatus
import com.mikeyphw.studyflow.core.model.StudyPlanId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpacedRepetitionSchedulerTest {
    private val item = ReviewItem(
        id = ReviewItemId("review-1"),
        studyPlanId = StudyPlanId("plan-1"),
        subjectId = null,
        topicId = null,
        questionId = null,
        flashcardId = null,
        errorEntryId = null,
        method = ReviewMethod.SPACED_REPETITION,
        status = ReviewStatus.DUE,
        dueAtEpochMillis = 1_000L,
        intervalDays = 0,
        repetitionCount = 0,
        easeFactor = 2.5,
        completedAtEpochMillis = null,
    )

    @Test
    fun successfulGradeSchedulesNextReview() {
        val scheduled = SpacedRepetitionScheduler.schedule(item, grade = 5, reviewedAtEpochMillis = 10_000L)

        assertEquals(ReviewStatus.SCHEDULED, scheduled.status)
        assertEquals(1, scheduled.repetitionCount)
        assertEquals(1, scheduled.intervalDays)
        assertEquals(10_000L, scheduled.completedAtEpochMillis)
        assertTrue(scheduled.dueAtEpochMillis > 10_000L)
    }

    @Test
    fun failedGradeResetsRepetitionButKeepsItemAlive() {
        val mature = item.copy(repetitionCount = 4, intervalDays = 10, easeFactor = 2.4)
        val scheduled = SpacedRepetitionScheduler.schedule(mature, grade = 1, reviewedAtEpochMillis = 10_000L)

        assertEquals(ReviewStatus.SCHEDULED, scheduled.status)
        assertEquals(0, scheduled.repetitionCount)
        assertEquals(1, scheduled.intervalDays)
        assertTrue(scheduled.easeFactor >= 1.3)
    }
}
