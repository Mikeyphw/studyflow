package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.ExamReadinessBand
import com.mikeyphw.studyflow.core.model.MockExam
import com.mikeyphw.studyflow.core.model.MockExamId
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyPlanStatus
import com.mikeyphw.studyflow.core.model.StudySchedule
import com.mikeyphw.studyflow.core.model.StudyType
import com.mikeyphw.studyflow.core.model.TestMode
import org.junit.Assert.assertEquals
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
            schedule = StudySchedule(StudyPlanId("plan"), strategy = null, scheduleRules = emptyList(),
                plannedActivities = emptyList()),
            mockExams = listOf(mock(score = 78.0), mock(score = 82.0)),
            nowEpochMillis = 1_786_752_000_000L,
        )

        assertTrue(score.scorePercent in 70..100)
        assertEquals(ExamReadinessBand.ON_TRACK, score.band)
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
            schedule = StudySchedule(StudyPlanId("plan"), strategy = null, scheduleRules = emptyList(),
                plannedActivities = emptyList()),
            mockExams = listOf(mock(score = 45.0)),
            nowEpochMillis = 1_786_752_000_000L,
        )

        assertTrue(score.scorePercent < 45)
        assertEquals(ExamReadinessBand.CRITICAL, score.band)
        assertTrue(score.signals.any { it.severity == ExamReadinessBand.CRITICAL })
    }

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
    ) = StudyAnalyticsSnapshot(
        studyPlanId = StudyPlanId("plan"),
        studiedMinutesLastSevenDays = 300,
        completedSessionsLastSevenDays = 5,
        scheduleAdherencePercent = adherence,
        questionAccuracyPercent = accuracy,
        answeredQuestions = answered,
        dueReviewCount = dueReviews,
        overdueReviewCount = overdueReviews,
        retentionEaseAverage = 2.5,
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
