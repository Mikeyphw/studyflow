package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyExamReportBuilderTest {
    @Test
    fun buildsStructuredLocalReportAndDropsReadinessProse() {
        val snapshot = StudyExamReportBuilder.build(
            StudyExamReportInput(
                plan = plan,
                analytics = analytics,
                readiness = readiness,
                weeklyReview = weeklyReview,
                evidence = listOf(evidence),
                schedule = schedule,
                reviewItems = listOf(dueReview),
                questions = listOf(recentQuestion),
                tests = listOf(practiceTest),
                mockExams = listOf(mockExam("m1", 80.0), mockExam("m2", 60.0)),
                reschedulePreview = reschedulePreview,
                cleanupReport = cleanupReport,
                generatedAtEpochMillis = NOW,
            ),
        )

        assertEquals(68, snapshot.readinessScorePercent)
        assertEquals(ReadinessSignalKind.COVERAGE, snapshot.readinessSignals.single().kind)
        assertEquals(66, snapshot.readinessSignals.single().scorePercent)
        assertFalse(snapshot.toString().contains("Legacy English label"))
        assertFalse(snapshot.toString().contains("Legacy English message"))
        assertFalse(snapshot.freeformNotesIncluded)
        assertEquals(1, snapshot.practiceTestCount)
        assertEquals(1, snapshot.completedPracticeTestCount)
        assertEquals(75, snapshot.practiceTestAccuracyPercent)
        assertEquals(2, snapshot.mockExamCount)
        assertEquals(80, snapshot.bestMockScorePercent)
        assertEquals(70, snapshot.averageMockScorePercent)
        assertEquals(1, snapshot.recentQuestionCount)
        assertEquals(2, snapshot.executableRescheduleChangeCount)
        assertEquals(3, snapshot.cleanupIssueCount)
        assertTrue(snapshot.nextActions.contains(StudyExamReportNextActionKind.REVIEW_BACKLOG))
        assertTrue(snapshot.nextActions.contains(StudyExamReportNextActionKind.RECOVER_SCHEDULE))
        assertTrue(snapshot.nextActions.contains(StudyExamReportNextActionKind.REVIEW_CONTENT_CLEANUP))
    }

    @Test
    fun emptyMockScoresStayNullAndStablePlansReturnKeepPlan() {
        val stable = StudyExamReportBuilder.build(
            StudyExamReportInput(
                plan = plan,
                analytics = analytics.copy(
                    overdueReviewCount = 0,
                    dueReviewCount = 0,
                    weakAreas = emptyList(),
                    questionAccuracyPercent = 90,
                ),
                readiness = readiness.copy(mockExamHealthPercent = 90, signals = emptyList()),
                weeklyReview = weeklyReview.copy(items = emptyList()),
                evidence = emptyList(),
                schedule = schedule.copy(plannedActivities = emptyList()),
                reviewItems = emptyList(),
                questions = emptyList(),
                tests = emptyList(),
                mockExams = listOf(mockExam("draft", null)),
                reschedulePreview = null,
                cleanupReport = StudyContentCleanupReport(NOW, 100, emptyList(), emptyList()),
                generatedAtEpochMillis = NOW,
            ),
        )

        assertNull(stable.bestMockScorePercent)
        assertNull(stable.averageMockScorePercent)
        assertEquals(listOf(StudyExamReportNextActionKind.KEEP_PLAN), stable.nextActions)
    }

    private val plan = StudyPlan(
        id = StudyPlanId("plan"),
        name = "Exam",
        description = "private note that must not be exported",
        type = StudyType.PUBLIC_EXAMINATION,
        status = StudyPlanStatus.ACTIVE,
        examDateIso = "2026-12-01",
        weeklyGoalHours = 10,
        progressPercent = 60,
        studiedMinutesThisWeek = 120,
        questionsAnswered = 40,
        averageAccuracyPercent = 65,
        createdAtEpochMillis = NOW - DAY_MILLIS,
        updatedAtEpochMillis = NOW,
    )

    private val analytics = StudyAnalyticsSnapshot(
        studyPlanId = plan.id,
        studiedMinutesLastSevenDays = 210,
        completedSessionsLastSevenDays = 4,
        scheduleAdherencePercent = 55,
        questionAccuracyPercent = 58,
        answeredQuestions = 40,
        dueReviewCount = 3,
        overdueReviewCount = 2,
        retentionEaseAverage = 2.2,
        goalCompletionPercent = 70,
        contentProgressPercent = 60,
        libraryProgressPercent = 50,
        consistencyScorePercent = 65,
        weakAreas = emptyList(),
        insights = emptyList(),
    )

    private val readiness = ExamReadinessScore(
        studyPlanId = plan.id,
        scorePercent = 68,
        band = ExamReadinessBand.WATCH,
        daysUntilExam = 30,
        coveragePercent = 66,
        reviewHealthPercent = 60,
        practiceHealthPercent = 58,
        mockExamHealthPercent = 55,
        consistencyPercent = 65,
        scheduleHealthPercent = 55,
        timeFeasibilityPercent = 70,
        signals = listOf(
            ReadinessSignal(
                kind = ReadinessSignalKind.COVERAGE,
                label = "Legacy English label",
                scorePercent = 66,
                message = "Legacy English message",
                severity = ExamReadinessBand.WATCH,
            ),
        ),
    )

    private val weeklyReview = StudyCoachWeeklyReview(
        studyPlanName = plan.name,
        generatedAtEpochMillis = NOW,
        studiedMinutes = 210,
        evidenceCount = 1,
        readinessBand = ExamReadinessBand.WATCH,
        items = listOf(
            StudyCoachWeeklyReviewItem(
                kind = StudyCoachWeeklyReviewItemKind.REVIEW_BACKLOG,
                tone = StudyCoachWeeklyReviewTone.ACTION,
                scorePercent = null,
                count = 2,
            ),
        ),
    )

    private val evidence = StudyEvidenceEvent(
        id = "session:1",
        kind = StudyEvidenceEventKind.SESSION_COMPLETED,
        occurredAtEpochMillis = NOW,
        label = "Session",
        minutes = 45,
    )

    private val schedule = StudySchedule(
        studyPlanId = plan.id,
        strategy = null,
        scheduleRules = emptyList(),
        plannedActivities = listOf(
            PlannedActivity(
                id = PlannedActivityId("missed"),
                studyPlanId = plan.id,
                subjectId = null,
                topicId = null,
                materialId = null,
                videoResourceId = null,
                type = StudyActivityType.THEORY,
                title = "Theory",
                scheduledStartEpochMillis = NOW - DAY_MILLIS,
                plannedDurationMinutes = 45,
                status = PlannedActivityStatus.MISSED,
                locked = false,
                completedAtEpochMillis = null,
            ),
        ),
    )

    private val dueReview = ReviewItem(
        id = ReviewItemId("review"),
        studyPlanId = plan.id,
        subjectId = null,
        topicId = null,
        questionId = null,
        flashcardId = null,
        errorEntryId = null,
        method = ReviewMethod.MANUAL,
        status = ReviewStatus.DUE,
        dueAtEpochMillis = NOW - 1,
        intervalDays = 1,
        repetitionCount = 0,
        easeFactor = 2.5,
        completedAtEpochMillis = null,
    )

    private val practiceTest = StudyTest(
        id = StudyTestId("test"),
        studyPlanId = plan.id,
        title = "Practice",
        mode = TestMode.PRACTICE,
        timeLimitSeconds = null,
        questionCount = 20,
        answeredCount = 20,
        correctCount = 15,
        startedAtEpochMillis = NOW - 3_600_000L,
        submittedAtEpochMillis = NOW,
        configurationJson = "{}",
    )

    private val recentQuestion = Question(
        id = QuestionId("recent"),
        studyPlanId = plan.id,
        subjectId = null,
        topicId = null,
        type = QuestionType.TRUE_FALSE,
        statement = "Statement",
        options = emptyList(),
        correctAnswer = "True",
        explanation = "",
        examBoard = null,
        source = null,
        year = null,
        difficulty = 1,
        favorite = false,
        disputed = false,
        tags = emptyList(),
        createdAtEpochMillis = NOW - DAY_MILLIS,
        updatedAtEpochMillis = NOW - DAY_MILLIS,
    )

    private val reschedulePreview = AdaptiveReschedulePreview(
        studyPlanId = plan.id,
        generatedAtEpochMillis = NOW,
        readinessScorePercent = 68,
        missedUnlockedActivities = 1,
        overdueReviewCount = 2,
        scheduleAdherencePercent = 55,
        actionSummary = "Legacy prose is not copied into the report.",
        changes = listOf(
            change("one"),
            change("two"),
        ),
    )

    private val cleanupReport = StudyContentCleanupReport(
        generatedAtEpochMillis = NOW,
        healthScorePercent = 80,
        issues = listOf(
            StudyContentCleanupIssue(
                kind = StudyContentCleanupIssueKind.DUPLICATE_QUESTION,
                severity = StudyContentCleanupSeverity.REVIEW,
                count = 3,
                affectedIds = listOf("q1", "q2", "q3"),
            ),
        ),
        previewActions = listOf(
            StudyContentCleanupPreviewAction(
                kind = StudyContentCleanupPreviewActionKind.REVIEW_DUPLICATE,
                issueKind = StudyContentCleanupIssueKind.DUPLICATE_QUESTION,
                affectedCount = 3,
            ),
        ),
    )

    private fun mockExam(id: String, score: Double?) = MockExam(
        id = MockExamId(id),
        studyPlanId = plan.id,
        title = id,
        mode = TestMode.EXAM,
        timeLimitSeconds = 3_600,
        questionCount = 20,
        answeredCount = if (score == null) 0 else 20,
        correctCount = if (score == null) 0 else (score / 5).toInt(),
        scorePercent = score,
        negativeMarking = 0.0,
        minimumScorePercent = 60,
        startedAtEpochMillis = if (score == null) null else NOW - 3_600_000L,
        submittedAtEpochMillis = if (score == null) null else NOW,
        configurationJson = "{}",
    )

    private fun change(id: String) = AdaptiveRescheduleChange(
        id = id,
        type = AdaptiveRescheduleChangeType.REDISTRIBUTE_MISSED,
        title = "Legacy title",
        reason = "Legacy reason",
        plannedActivityId = null,
        currentStartEpochMillis = null,
        proposedStartEpochMillis = null,
        currentDurationMinutes = null,
        proposedDurationMinutes = null,
        currentStatus = null,
        proposedStatus = null,
        locked = false,
        executable = true,
    )

    private companion object {
        const val DAY_MILLIS = 86_400_000L
        const val NOW = 30L * DAY_MILLIS
    }
}
