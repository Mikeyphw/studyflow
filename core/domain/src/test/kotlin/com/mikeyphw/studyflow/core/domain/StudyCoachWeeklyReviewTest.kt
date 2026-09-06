package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.AdaptiveReschedulePreview
import com.mikeyphw.studyflow.core.model.ExamReadinessBand
import com.mikeyphw.studyflow.core.model.ExamReadinessScore
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyPlanStatus
import com.mikeyphw.studyflow.core.model.StudyType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyCoachWeeklyReviewTest {
    @Test
    fun weeklyReviewSurfacesRiskAndSafeRescheduleAction() {
        val review = StudyCoachWeeklyReviewBuilder.build(
            StudyCoachWeeklyReviewInput(
                plan = plan,
                analytics = analytics(
                    overdueReviews = 4,
                    accuracy = 52,
                    adherence = 40,
                    consistency = 30,
                ),
                readiness = readiness(ExamReadinessBand.AT_RISK, score = 58),
                reschedulePreview = AdaptiveReschedulePreview(
                    studyPlanId = plan.id,
                    generatedAtEpochMillis = NOW,
                    readinessScorePercent = 58,
                    missedUnlockedActivities = 2,
                    overdueReviewCount = 4,
                    scheduleAdherencePercent = 40,
                    actionSummary = "Recover missed work.",
                    changes = listOf(
                        com.mikeyphw.studyflow.core.model.AdaptiveRescheduleChange(
                            id = "change",
                            type = com.mikeyphw.studyflow.core.model.AdaptiveRescheduleChangeType.PROTECT_REVIEWS,
                            title = "Protect reviews",
                            reason = "Review first.",
                            plannedActivityId = null,
                            currentStartEpochMillis = null,
                            proposedStartEpochMillis = null,
                            currentDurationMinutes = null,
                            proposedDurationMinutes = null,
                            currentStatus = null,
                            proposedStatus = null,
                            locked = false,
                            executable = true,
                        ),
                    ),
                ),
                evidence = emptyList(),
                generatedAtEpochMillis = NOW,
            ),
        )

        assertEquals(ExamReadinessBand.AT_RISK, review.readinessBand)
        assertTrue(review.actionCount >= 3)
        assertTrue(review.items.any { it.kind == StudyCoachWeeklyReviewItemKind.REVIEW_BACKLOG })
        assertTrue(review.items.any { it.kind == StudyCoachWeeklyReviewItemKind.RESCHEDULE_READY })
    }

    @Test
    fun unavailableReadinessDoesNotBecomeFalseRisk() {
        val unknown = ExamReadinessScore(
            studyPlanId = plan.id,
            scorePercent = null,
            band = null,
            daysUntilExam = null,
            coveragePercent = 0,
            reviewHealthPercent = null,
            practiceHealthPercent = null,
            mockExamHealthPercent = null,
            consistencyPercent = null,
            scheduleHealthPercent = null,
            timeFeasibilityPercent = null,
            signals = emptyList(),
            confidence = null,
            evidenceDimensionCount = 1,
            evidenceSampleCount = 0,
        )
        val review = StudyCoachWeeklyReviewBuilder.build(
            StudyCoachWeeklyReviewInput(
                plan = plan,
                analytics = analytics(
                    overdueReviews = 0,
                    accuracy = 0,
                    adherence = 0,
                    consistency = 0,
                ).copy(
                    studiedMinutesLastSevenDays = 0,
                    completedSessionsLastSevenDays = 0,
                    answeredQuestions = 0,
                    retentionEaseAverage = 0.0,
                ),
                readiness = unknown,
                reschedulePreview = null,
                evidence = emptyList(),
                generatedAtEpochMillis = NOW,
            ),
        )

        assertEquals(null, review.readinessBand)
        assertTrue(review.items.none { it.kind == StudyCoachWeeklyReviewItemKind.READINESS_RISK })
        assertTrue(review.items.any { it.kind == StudyCoachWeeklyReviewItemKind.EVIDENCE_EMPTY })
    }

    @Test
    fun weeklyReviewCelebratesStableConsistency() {
        val review = StudyCoachWeeklyReviewBuilder.build(
            StudyCoachWeeklyReviewInput(
                plan = plan,
                analytics = analytics(
                    overdueReviews = 0,
                    accuracy = 82,
                    adherence = 90,
                    consistency = 100,
                ),
                readiness = readiness(ExamReadinessBand.ON_TRACK, score = 86),
                reschedulePreview = null,
                evidence = listOf(
                    StudyEvidenceEvent(
                        id = "session:1",
                        kind = StudyEvidenceEventKind.SESSION_COMPLETED,
                        occurredAtEpochMillis = NOW,
                        label = "Theory",
                        minutes = 50,
                    ),
                ),
                generatedAtEpochMillis = NOW,
            ),
        )

        assertTrue(review.winCount >= 1)
        assertEquals(50, review.studiedMinutes)
        assertTrue(review.items.any { it.kind == StudyCoachWeeklyReviewItemKind.CONSISTENCY_WIN })
    }

    private val plan = StudyPlan(
        id = StudyPlanId("plan"),
        name = "Exam",
        description = "",
        type = StudyType.PUBLIC_EXAMINATION,
        status = StudyPlanStatus.ACTIVE,
        examDateIso = "2026-12-01",
        weeklyGoalHours = 10,
        progressPercent = 70,
        studiedMinutesThisWeek = 180,
        questionsAnswered = 80,
        averageAccuracyPercent = 75,
    )

    private fun analytics(
        overdueReviews: Int,
        accuracy: Int,
        adherence: Int,
        consistency: Int,
    ) = StudyAnalyticsSnapshot(
        studyPlanId = plan.id,
        studiedMinutesLastSevenDays = 180,
        completedSessionsLastSevenDays = 4,
        scheduleAdherencePercent = adherence,
        questionAccuracyPercent = accuracy,
        answeredQuestions = 40,
        dueReviewCount = overdueReviews,
        overdueReviewCount = overdueReviews,
        retentionEaseAverage = 2.5,
        goalCompletionPercent = 65,
        contentProgressPercent = 70,
        libraryProgressPercent = 60,
        consistencyScorePercent = consistency,
        weakAreas = emptyList(),
        insights = emptyList(),
    )

    private fun readiness(band: ExamReadinessBand, score: Int) = ExamReadinessScore(
        studyPlanId = plan.id,
        scorePercent = score,
        band = band,
        daysUntilExam = 30,
        coveragePercent = score,
        reviewHealthPercent = score,
        practiceHealthPercent = score,
        mockExamHealthPercent = score,
        consistencyPercent = score,
        scheduleHealthPercent = score,
        timeFeasibilityPercent = score,
        signals = emptyList(),
    )

    private companion object {
        const val NOW = 1_000_000L
    }
}
