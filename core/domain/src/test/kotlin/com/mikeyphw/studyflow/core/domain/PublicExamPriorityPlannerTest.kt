package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.SubjectId
import com.mikeyphw.studyflow.core.model.TopicId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PublicExamPriorityPlannerTest {
    private val plan = StudyPlanId("plan")

    private fun item(
        id: String,
        subject: String = "s-$id",
        weight: Double = 1.0,
        mastery: Int = 70,
        accuracy: Int? = 70,
        overdue: Int = 0,
        errors: Int = 0,
        days: Int? = 60,
        studied: Int = 0,
        since: Int? = 24,
        planId: StudyPlanId = plan,
    ) = PublicExamPriorityEvidence(
        studyPlanId = planId,
        subjectId = SubjectId(subject),
        topicId = TopicId("t-$id"),
        subjectName = subject,
        topicName = id,
        examWeight = weight,
        examIncidencePercent = 50,
        masteryPercent = mastery,
        recentAccuracyPercent = accuracy,
        recurringErrorCount = errors,
        overdueReviewCount = overdue,
        missedScheduleCount = 0,
        daysUntilExam = days,
        cycleDistance = 1,
        minutesStudiedLast7Days = studied,
        hoursSinceLastStudy = since,
    )

    @Test
    fun `weakness and overdue evidence can beat raw weight`() {
        val evidence = listOf(
            item("weight", weight = 5.0, mastery = 90),
            item("weak", weight = 2.0, mastery = 25, overdue = 4, errors = 3),
        )

        val ranked = PublicExamPriorityPlanner.rank(plan, evidence)

        assertEquals("weak", ranked.first().evidence.topicName)
        assertTrue(PublicExamPriorityReasonCode.OVERDUE_REVIEW in ranked.first().reasonCodes)
    }

    @Test
    fun `near exam urgency and starvation are explainable`() {
        val evidence = listOf(
            item("near", days = 5, since = 190),
            item("far", days = 150, since = 2),
        )

        val first = PublicExamPriorityPlanner.rank(plan, evidence).first()

        assertEquals("near", first.evidence.topicName)
        assertTrue(PublicExamPriorityReasonCode.EXAM_NEAR in first.reasonCodes)
        assertTrue(PublicExamPriorityReasonCode.STARVATION_RELIEF in first.reasonCodes)
    }

    @Test
    fun `cooldown prevents saturated topic domination`() {
        val evidence = listOf(
            item("hot", weight = 5.0, studied = 900),
            item("fresh", weight = 3.0, studied = 0),
        )

        val ranked = PublicExamPriorityPlanner.rank(plan, evidence)

        assertEquals("fresh", ranked.first().evidence.topicName)
    }

    @Test
    fun `cross plan and locked evidence are excluded and ties deterministic`() {
        val other = StudyPlanId("other")
        val locked = item("locked").copy(lockedByActiveSession = true)
        val evidence = listOf(
            item("b", subject = "same"),
            item("a", subject = "same"),
            item("other", planId = other),
            locked,
        )

        val ranked = PublicExamPriorityPlanner.rank(plan, evidence)

        assertEquals(listOf("a", "b"), ranked.map { it.evidence.topicName })
    }
}
