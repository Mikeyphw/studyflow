package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.ExamReadinessBand
import com.mikeyphw.studyflow.core.model.MockExam
import com.mikeyphw.studyflow.core.model.MockExamId
import com.mikeyphw.studyflow.core.model.ReadinessConfidence
import com.mikeyphw.studyflow.core.model.ReadinessSignalKind
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyPlanStatus
import com.mikeyphw.studyflow.core.model.StudySchedule
import com.mikeyphw.studyflow.core.model.StudyType
import com.mikeyphw.studyflow.core.model.TestMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExamReadinessEngineTest {
    @Test
    fun readinessCombinesCoverageRetentionMockPracticeAndTimeLeft() {
        val score = ExamReadinessEngine.calculate(
            plan = plan(progress = 70, examDateIso = "2026-08-21"),
            analytics = analytics(
                accuracy = 80,
                answered = 120,
                dueReviews = 2,
                overdueReviews = 0,
                adherence = 90,
                consistency = 80,
                coverage = 72,
            ),
            schedule = emptySchedule(),
            mockExams = listOf(mock(score = 78.0), mock(score = 82.0)),
            nowEpochMillis = 1_786_752_000_000L,
        )

        assertTrue(requireNotNull(score.scorePercent) in 70..100)
        assertEquals(ExamReadinessBand.ON_TRACK, score.band)
        assertTrue(score.confidence != null)
        assertEquals(7, score.signals.distinctBy { it.kind }.size)
    }

    @Test
    fun lowCoverageBacklogAndPoorAccuracyBecomeCritical() {
        val score = ExamReadinessEngine.calculate(
            plan = plan(progress = 20, examDateIso = "2026-08-08"),
            analytics = analytics(
                accuracy = 42,
                answered = 80,
                dueReviews = 8,
                overdueReviews = 6,
                adherence = 35,
                consistency = 20,
                coverage = 25,
            ),
            schedule = emptySchedule(),
            mockExams = listOf(mock(score = 45.0)),
            nowEpochMillis = 1_786_752_000_000L,
        )

        assertTrue(requireNotNull(score.scorePercent) < 45)
        assertEquals(ExamReadinessBand.CRITICAL, score.band)
        assertTrue(score.signals.any { it.severity == ExamReadinessBand.CRITICAL })
    }

    @Test
    fun freshImportedPlanDoesNotInventReadinessFromMissingEvidence() {
        val score = ExamReadinessEngine.calculate(
            plan = plan(progress = 0, examDateIso = null),
            analytics = analytics(
                accuracy = 0,
                answered = 0,
                dueReviews = 0,
                overdueReviews = 0,
                adherence = 0,
                consistency = 0,
                coverage = 0,
                studiedMinutes = 0,
                completedSessions = 0,
                retentionEase = 0.0,
            ),
            schedule = emptySchedule(),
            mockExams = emptyList(),
            nowEpochMillis = 1_786_752_000_000L,
        )

        assertNull(score.scorePercent)
        assertNull(score.band)
        assertNull(score.confidence)
        assertNull(score.reviewHealthPercent)
        assertNull(score.practiceHealthPercent)
        assertNull(score.mockExamHealthPercent)
        assertNull(score.consistencyPercent)
        assertNull(score.scheduleHealthPercent)
        assertNull(score.timeFeasibilityPercent)
        assertEquals(0, score.coveragePercent)
        assertEquals(1, score.evidenceDimensionCount)
        assertTrue(score.signals.first { it.kind == ReadinessSignalKind.PRACTICE }.scorePercent == null)
    }

    @Test
    fun eightQuestionsRemainInsufficientEvidence() {
        val score = ExamReadinessEngine.calculate(
            plan = plan(progress = 10, examDateIso = null),
            analytics = analytics(
                accuracy = 88,
                answered = 8,
                dueReviews = 0,
                overdueReviews = 0,
                adherence = 0,
                consistency = 0,
                coverage = 10,
                studiedMinutes = 0,
                completedSessions = 0,
                retentionEase = 0.0,
            ),
            schedule = emptySchedule(),
            mockExams = emptyList(),
            nowEpochMillis = 1_786_752_000_000L,
        )

        assertNull(score.scorePercent)
        assertNull(score.confidence)
        assertTrue(score.practiceHealthPercent != null)
    }

    @Test
    fun enoughPracticeProducesLowConfidenceInsteadOfFalsePrecision() {
        val score = ExamReadinessEngine.calculate(
            plan = plan(progress = 20, examDateIso = null),
            analytics = analytics(
                accuracy = 75,
                answered = 30,
                dueReviews = 0,
                overdueReviews = 0,
                adherence = 0,
                consistency = 0,
                coverage = 20,
                studiedMinutes = 0,
                completedSessions = 0,
                retentionEase = 0.0,
            ),
            schedule = emptySchedule(),
            mockExams = emptyList(),
            nowEpochMillis = 1_786_752_000_000L,
        )

        assertTrue(score.scorePercent != null)
        assertEquals(ReadinessConfidence.LOW, score.confidence)
    }

    @Test
    fun broadEvidenceCanReachHighConfidence() {
        val score = ExamReadinessEngine.calculate(
            plan = plan(progress = 80, examDateIso = "2026-10-01"),
            analytics = analytics(
                accuracy = 84,
                answered = 800,
                dueReviews = 2,
                overdueReviews = 0,
                adherence = 92,
                consistency = 88,
                coverage = 82,
                studiedMinutes = 900,
                completedSessions = 8,
                retentionEase = 2.4,
            ),
            schedule = emptySchedule(),
            mockExams = listOf(mock(82.0), mock(86.0)),
            nowEpochMillis = 1_786_752_000_000L,
        )

        assertTrue(score.scorePercent != null)
        assertEquals(ReadinessConfidence.HIGH, score.confidence)
    }

    private fun emptySchedule() = StudySchedule(
        StudyPlanId("plan"),
        strategy = null,
        scheduleRules = emptyList(),
        plannedActivities = emptyList(),
    )

    private fun plan(progress: Int, examDateIso: String?) = StudyPlan(
        id = StudyPlanId("plan"),
        name = "Exam",
        description = "",
        type = StudyType.PUBLIC_EXAMINATION,
        status = StudyPlanStatus.ACTIVE,
        examDateIso = examDateIso,
        weeklyGoalHours = 12,
        progressPercent = progress,
        studiedMinutesThisWeek = 300,
        questionsAnswered = 120,
        averageAccuracyPercent = 75,
    )

    private fun analytics(
        accuracy: Int,
        answered: Int,
        dueReviews: Int,
        overdueReviews: Int,
        adherence: Int,
        consistency: Int,
        coverage: Int,
        studiedMinutes: Int = 300,
        completedSessions: Int = 5,
        retentionEase: Double = 2.5,
    ) = StudyAnalyticsSnapshot(
        studyPlanId = StudyPlanId("plan"),
        studiedMinutesLastSevenDays = studiedMinutes,
        completedSessionsLastSevenDays = completedSessions,
        scheduleAdherencePercent = adherence,
        questionAccuracyPercent = accuracy,
        answeredQuestions = answered,
        dueReviewCount = dueReviews,
        overdueReviewCount = overdueReviews,
        retentionEaseAverage = retentionEase,
        goalCompletionPercent = 70,
        contentProgressPercent = coverage,
        libraryProgressPercent = 40,
        consistencyScorePercent = consistency,
        weakAreas = emptyList(),
        insights = emptyList(),
    )

    private fun mock(score: Double) = MockExam(
        id = MockExamId("mock-$score"),
        studyPlanId = StudyPlanId("plan"),
        title = "Mock",
        mode = TestMode.EXAM,
        timeLimitSeconds = 7200,
        questionCount = 100,
        answeredCount = 100,
        correctCount = score.toInt(),
        scorePercent = score,
        negativeMarking = 0.0,
        minimumScorePercent = 60,
        startedAtEpochMillis = 0L,
        submittedAtEpochMillis = 1L,
        configurationJson = "{}",
    )
}
