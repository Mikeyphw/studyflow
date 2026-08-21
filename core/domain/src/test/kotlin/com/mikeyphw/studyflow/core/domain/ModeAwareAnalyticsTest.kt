package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.StudyContentMode
import com.mikeyphw.studyflow.core.model.StudyPlanId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModeAwareAnalyticsTest {
    @Test
    fun `programming analytics promote modules exercises debugging and projects`() {
        val profile = ModeAwareAnalyticsCalculator.buildProfile(
            snapshot = snapshot(),
            contentMode = StudyContentMode.PROGRAMMING_COURSE,
        )

        assertEquals(ModeAwareWeakAreaLabel.WEAK_CONCEPTS, profile.weakAreaLabel)
        assertEquals(StudyContentMode.PROGRAMMING_COURSE, profile.contentMode)
        assertTrue(profile.metrics.any { metric -> metric.key == ModeAwareAnalyticsMetricKey.MODULES_COMPLETED })
        assertTrue(profile.metrics.any { metric -> metric.key == ModeAwareAnalyticsMetricKey.EXERCISES_COMPLETED })
        assertTrue(profile.metrics.any { metric -> metric.key == ModeAwareAnalyticsMetricKey.DEBUGGING_ACCURACY })
        assertTrue(profile.metrics.any { metric -> metric.key == ModeAwareAnalyticsMetricKey.PROJECT_PROGRESS })
    }

    @Test
    fun `language analytics promote idioms usage and mistake patterns`() {
        val profile = ModeAwareAnalyticsCalculator.buildProfile(
            snapshot = snapshot(),
            contentMode = StudyContentMode.LANGUAGE_DECK,
        )

        assertEquals(ModeAwareWeakAreaLabel.LANGUAGE_MISTAKE_PATTERNS, profile.weakAreaLabel)
        assertTrue(profile.metrics.any { metric -> metric.key == ModeAwareAnalyticsMetricKey.IDIOMS_LEARNED })
        assertTrue(profile.metrics.any { metric -> metric.key == ModeAwareAnalyticsMetricKey.IDIOMS_DUE })
        assertTrue(profile.metrics.any { metric -> metric.key == ModeAwareAnalyticsMetricKey.USAGE_ACCURACY })
        assertTrue(profile.metrics.any { metric -> metric.key == ModeAwareAnalyticsMetricKey.MISTAKE_PATTERNS })
    }

    @Test
    fun `exam analytics keep the original exam metrics`() {
        val profile = ModeAwareAnalyticsCalculator.buildProfile(
            snapshot = snapshot(),
            contentMode = StudyContentMode.EXAM_PLAN,
        )

        assertEquals(ModeAwareWeakAreaLabel.WEAK_AREAS, profile.weakAreaLabel)
        assertEquals(
            listOf(
                ModeAwareAnalyticsMetricKey.STUDY_CONSISTENCY,
                ModeAwareAnalyticsMetricKey.SCHEDULE_ADHERENCE,
                ModeAwareAnalyticsMetricKey.QUESTION_ACCURACY,
                ModeAwareAnalyticsMetricKey.GOAL_COMPLETION,
                ModeAwareAnalyticsMetricKey.CONTENT_PROGRESS,
                ModeAwareAnalyticsMetricKey.LIBRARY_PROGRESS,
            ),
            profile.metrics.map { metric -> metric.key },
        )
    }

    private fun snapshot() = StudyAnalyticsSnapshot(
        studyPlanId = StudyPlanId("plan"),
        studiedMinutesLastSevenDays = 120,
        completedSessionsLastSevenDays = 4,
        scheduleAdherencePercent = 70,
        questionAccuracyPercent = 80,
        answeredQuestions = 24,
        dueReviewCount = 5,
        overdueReviewCount = 1,
        retentionEaseAverage = 2.5,
        goalCompletionPercent = 50,
        contentProgressPercent = 40,
        libraryProgressPercent = 30,
        consistencyScorePercent = 60,
        weakAreas = listOf(WeakStudyArea("Loops", 40, 45, 1)),
        insights = emptyList(),
    )
}
