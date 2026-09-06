package com.mikeyphw.studyflow.feature.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgressMetricEvidenceTest {
    @Test
    fun zeroAnswersAreNoDataRatherThanZeroPercent() {
        val metric = ProgressMetricEvidence.accuracy(correctAnswers = 0, answeredQuestions = 0)
        assertNull(metric.percent)
        assertNull(metric.confidence)
        assertEquals(0, metric.evidenceCount)
    }

    @Test
    fun observedZeroRemainsARealZero() {
        val metric = ProgressMetricEvidence.accuracy(correctAnswers = 0, answeredQuestions = 12)
        assertEquals(0, metric.percent)
        assertEquals(ProgressMetricConfidence.MEDIUM, metric.confidence)
    }

    @Test
    fun thirtyNineAnswersRemainMediumConfidence() {
        val metric = ProgressMetricEvidence.accuracy(correctAnswers = 28, answeredQuestions = 39)
        assertEquals(71, metric.percent)
        assertEquals(ProgressMetricConfidence.MEDIUM, metric.confidence)
    }

    @Test
    fun fiftyAnswersReachHighConfidence() {
        val metric = ProgressMetricEvidence.accuracy(correctAnswers = 36, answeredQuestions = 50)
        assertEquals(72, metric.percent)
        assertEquals(ProgressMetricConfidence.HIGH, metric.confidence)
    }

    @Test
    fun scheduleAndGoalPercentagesRequireEvidence() {
        assertNull(ProgressMetricEvidence.schedule(percent = 0, observedBlocks = 0).percent)
        assertNull(ProgressMetricEvidence.goals(percent = 0, observedGoals = 0).percent)
        assertEquals(0, ProgressMetricEvidence.schedule(percent = 0, observedBlocks = 1).percent)
        assertEquals(0, ProgressMetricEvidence.goals(percent = 0, observedGoals = 1).percent)
    }
}
