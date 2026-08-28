package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.ErrorCategory
import com.mikeyphw.studyflow.core.model.ReviewItem
import com.mikeyphw.studyflow.core.model.ReviewItemId
import com.mikeyphw.studyflow.core.model.ReviewMethod
import com.mikeyphw.studyflow.core.model.ReviewStatus
import com.mikeyphw.studyflow.core.model.StudyPlanId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PublicExamReviewPolicyTest {
    @Test
    fun `default policy is one seven thirty days`() {
        assertEquals(listOf(1, 7, 30), PublicExamReviewPolicy().intervalsDays)
    }

    @Test
    fun `configuration codec preserves unknown settings fields`() {
        val input = "{\"other\":true}"
        val encoded = PublicExamReviewPolicyCodec.encode(input, PublicExamReviewPolicy(listOf(2, 8, 31)))

        assertTrue("\"other\":true" in encoded)
        assertEquals(listOf(2, 8, 31), PublicExamReviewPolicyCodec.decode(encoded).intervalsDays)
    }

    @Test
    fun `repeated errors increase initial review urgency`() {
        val policy = PublicExamReviewPolicy()
        val now = 1_000_000L

        val first = policy.initialDueAt(now, 1) - now
        val repeated = policy.initialDueAt(now, 3) - now
        val critical = policy.initialDueAt(now, 5) - now

        assertTrue(repeated < first)
        assertTrue(critical <= repeated)
    }

    @Test
    fun `successful error review advances through seven and thirty day intervals`() {
        val policy = PublicExamReviewPolicy()
        val current = item(repetition = 0)
        val reviewedAt = 10_000L

        val second = policy.successor(current, grade = 4, reviewedAtEpochMillis = reviewedAt)
        val third = policy.successor(second, grade = 4, reviewedAtEpochMillis = second.dueAtEpochMillis)

        assertEquals(7, second.intervalDays)
        assertEquals(30, third.intervalDays)
    }

    @Test
    fun `custom policy bounds reject descending intervals`() {
        assertFailsWith<IllegalArgumentException> {
            PublicExamReviewPolicy(listOf(7, 1, 30))
        }
    }

    @Test
    fun `every existing error category has an explicit remediation action`() {
        val actions = ErrorCategory.entries.map(PublicExamErrorRemediation::actionFor)
        assertEquals(ErrorCategory.entries.size, actions.size)
        assertTrue(actions.all { it.name.isNotBlank() })
    }

    private fun item(repetition: Int) = ReviewItem(
        id = ReviewItemId("review-$repetition"),
        studyPlanId = StudyPlanId("plan"),
        subjectId = null,
        topicId = null,
        questionId = null,
        flashcardId = null,
        errorEntryId = null,
        method = ReviewMethod.ERROR_BASED,
        status = ReviewStatus.DUE,
        dueAtEpochMillis = 0L,
        intervalDays = if (repetition == 0) 1 else 7,
        repetitionCount = repetition,
        easeFactor = 2.5,
        completedAtEpochMillis = null,
    )
}
