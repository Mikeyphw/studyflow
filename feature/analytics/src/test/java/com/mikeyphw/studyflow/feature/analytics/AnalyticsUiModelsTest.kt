package com.mikeyphw.studyflow.feature.analytics

import com.mikeyphw.studyflow.core.domain.StudyAnalyticsSnapshot
import com.mikeyphw.studyflow.core.model.StudyPlanId
import org.junit.Assert.assertEquals
import org.junit.Test

class AnalyticsUiModelsTest {
    @Test
    fun snapshotCreatesMetricRows() {
        val rows = StudyAnalyticsSnapshot(
            studyPlanId = StudyPlanId("plan"),
            studiedMinutesLastSevenDays = 0,
            completedSessionsLastSevenDays = 0,
            scheduleAdherencePercent = 10,
            questionAccuracyPercent = 20,
            answeredQuestions = 0,
            dueReviewCount = 0,
            overdueReviewCount = 0,
            retentionEaseAverage = 0.0,
            goalCompletionPercent = 30,
            contentProgressPercent = 40,
            libraryProgressPercent = 50,
            consistencyScorePercent = 60,
            weakAreas = emptyList(),
            insights = emptyList(),
        ).metricRows()

        assertEquals(6, rows.size)
        assertEquals("60%", rows.first().value)
    }
}
