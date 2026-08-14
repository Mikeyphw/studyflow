package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.StudyPlanId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyAnalyticsCalculatorTest {
    @Test
    fun percentHandlesZeroDenominator() {
        assertEquals(0, StudyAnalyticsCalculator.percent(10, 0))
        assertEquals(50, StudyAnalyticsCalculator.percent(1, 2))
    }

    @Test
    fun insightsSurfaceBacklog() {
        val snapshot = StudyAnalyticsSnapshot(
            studyPlanId = StudyPlanId("plan"),
            studiedMinutesLastSevenDays = 30,
            completedSessionsLastSevenDays = 1,
            scheduleAdherencePercent = 80,
            questionAccuracyPercent = 75,
            answeredQuestions = 20,
            dueReviewCount = 3,
            overdueReviewCount = 1,
            retentionEaseAverage = 2.5,
            goalCompletionPercent = 20,
            contentProgressPercent = 30,
            libraryProgressPercent = 40,
            consistencyScorePercent = 50,
            weakAreas = emptyList(),
            insights = emptyList(),
        )

        val insights = StudyAnalyticsCalculator.buildInsights(snapshot)

        assertTrue(insights.any { it.kind == StudyInsightKind.OVERDUE_REVIEWS })
    }
}
