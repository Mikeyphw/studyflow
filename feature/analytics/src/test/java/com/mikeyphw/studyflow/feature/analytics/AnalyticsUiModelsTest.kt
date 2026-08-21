package com.mikeyphw.studyflow.feature.analytics

import com.mikeyphw.studyflow.core.domain.StudyAnalyticsSnapshot
import com.mikeyphw.studyflow.core.model.StudyContentMode
import com.mikeyphw.studyflow.core.model.StudyPlanId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyticsUiModelsTest {
    @Test
    fun snapshotCreatesDefaultExamMetricRows() {
        val rows = snapshot().metricRows()

        assertEquals(6, rows.size)
        assertEquals("60%", rows.first().value)
        assertTrue(rows.any { row -> row.titleRes == R.string.question_accuracy })
    }

    @Test
    fun programmingModeCreatesProgrammingMetricRows() {
        val rows = snapshot().metricRows(StudyContentMode.PROGRAMMING_COURSE)

        assertEquals(6, rows.size)
        assertTrue(rows.any { row -> row.titleRes == R.string.modules_completed })
        assertTrue(rows.any { row -> row.titleRes == R.string.exercises_completed && row.value == "24" })
        assertTrue(rows.any { row -> row.titleRes == R.string.debugging_accuracy })
        assertEquals(R.string.weak_concepts, StudyContentMode.PROGRAMMING_COURSE.weakAreasTitleRes())
    }

    @Test
    fun languageModeCreatesLanguageMetricRows() {
        val rows = snapshot().metricRows(StudyContentMode.LANGUAGE_DECK)

        assertTrue(rows.any { row -> row.titleRes == R.string.idioms_learned })
        assertTrue(rows.any { row -> row.titleRes == R.string.idioms_due && row.value == "5" })
        assertTrue(rows.any { row -> row.titleRes == R.string.usage_accuracy })
        assertEquals(R.string.mistake_patterns, StudyContentMode.LANGUAGE_DECK.weakAreasTitleRes())
    }

    private fun snapshot() = StudyAnalyticsSnapshot(
        studyPlanId = StudyPlanId("plan"),
        studiedMinutesLastSevenDays = 0,
        completedSessionsLastSevenDays = 0,
        scheduleAdherencePercent = 10,
        questionAccuracyPercent = 20,
        answeredQuestions = 24,
        dueReviewCount = 5,
        overdueReviewCount = 0,
        retentionEaseAverage = 0.0,
        goalCompletionPercent = 30,
        contentProgressPercent = 40,
        libraryProgressPercent = 50,
        consistencyScorePercent = 60,
        weakAreas = emptyList(),
        insights = emptyList(),
    )
}
