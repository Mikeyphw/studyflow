package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.AdaptiveReschedulePreview
import com.mikeyphw.studyflow.core.model.ExamReadinessBand
import com.mikeyphw.studyflow.core.model.ExamReadinessScore
import com.mikeyphw.studyflow.core.model.MockExam
import com.mikeyphw.studyflow.core.model.PlannedActivityStatus
import com.mikeyphw.studyflow.core.model.Question
import com.mikeyphw.studyflow.core.model.ReadinessSignalKind
import com.mikeyphw.studyflow.core.model.ReviewItem
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudySchedule
import com.mikeyphw.studyflow.core.model.StudyTest

/** Phase 61 keeps report content structured. Android/UI layers localize and render it to HTML/PDF. */
enum class StudyExamReportNextActionKind {
    REVIEW_BACKLOG,
    RECOVER_SCHEDULE,
    PRACTICE_WEAK_AREAS,
    RUN_MOCK_EXAM,
    REVIEW_CONTENT_CLEANUP,
    APPLY_SAFE_RESCHEDULE,
    KEEP_PLAN,
}

data class StudyExamReportSignal(
    val kind: ReadinessSignalKind,
    val scorePercent: Int,
)

enum class StudyExamReportMetricSource {
    ANALYTICS,
    READINESS,
    RAW_EVIDENCE,
    SCHEDULE,
    REVIEW_STATE,
    CONTENT_HEALTH,
}

data class StudyExamReportMetricProvenance(
    val metric: String,
    val source: StudyExamReportMetricSource,
)

data class StudyExamReportSnapshot(
    val studyPlanName: String,
    val generatedAtEpochMillis: Long,
    val readinessScorePercent: Int?,
    val readinessBand: ExamReadinessBand?,
    val readinessSignals: List<StudyExamReportSignal>,
    val weeklyCoachItems: List<StudyCoachWeeklyReviewItem>,
    val studiedMinutesLastSevenDays: Int,
    val evidenceCount: Int,
    val weakAreas: List<WeakStudyArea>,
    val missedActivityCount: Int,
    val dueReviewCount: Int,
    val overdueReviewCount: Int,
    val practiceTestCount: Int,
    val completedPracticeTestCount: Int,
    val practiceTestAccuracyPercent: Int?,
    val mockExamCount: Int,
    val completedMockExamCount: Int,
    val bestMockScorePercent: Int?,
    val averageMockScorePercent: Int?,
    val recentQuestionCount: Int,
    val executableRescheduleChangeCount: Int,
    val cleanupIssueCount: Int,
    val cleanupActionCount: Int,
    val cleanupHealthScorePercent: Int,
    val nextActions: List<StudyExamReportNextActionKind>,
    val metricProvenance: List<StudyExamReportMetricProvenance>,
    val freeformNotesIncluded: Boolean = false,
) {
    init {
        require(studyPlanName.isNotBlank()) { "Study Plan name cannot be blank." }
        require(generatedAtEpochMillis >= 0L) { "Report generation time cannot be negative." }
        require(readinessScorePercent == null || readinessScorePercent in 0..100)
        require(cleanupHealthScorePercent in 0..100)
        require(!freeformNotesIncluded) { "Phase 61 report export must omit freeform notes by default." }
    }
}

data class StudyExamReportInput(
    val plan: StudyPlan,
    val analytics: StudyAnalyticsSnapshot?,
    val readiness: ExamReadinessScore?,
    val weeklyReview: StudyCoachWeeklyReview,
    val evidence: List<StudyEvidenceEvent>,
    val totalEvidenceCount: Int = evidence.size,
    val requiredInputsReady: Boolean = true,
    val schedule: StudySchedule,
    val reviewItems: List<ReviewItem>,
    val questions: List<Question>,
    val tests: List<StudyTest>,
    val mockExams: List<MockExam>,
    val reschedulePreview: AdaptiveReschedulePreview?,
    val cleanupReport: StudyContentCleanupReport,
    val generatedAtEpochMillis: Long,
) {
    init {
        require(totalEvidenceCount >= evidence
            .size) { "Total evidence count cannot be smaller than the report sample." }
        require(generatedAtEpochMillis >= 0L) { "Report generation time cannot be negative." }
    }
}

object StudyExamReportBuilder {
    private const val RECENT_QUESTION_WINDOW_MILLIS = 14L * 86_400_000L

    fun build(input: StudyExamReportInput): StudyExamReportSnapshot {
        require(input.requiredInputsReady) { "Exam report export requires every authoritative input to be Ready." }
        val analytics = requireNotNull(input.analytics) {
            "Exam report export requires analytics input."
        }
        val readiness = requireNotNull(input.readiness) {
            "Exam report export requires readiness input."
        }
        val completedTests = input.tests.filter { test -> test.submittedAtEpochMillis != null }
        val testQuestionTotal = completedTests.sumOf(StudyTest::questionCount)
        val testCorrect = completedTests.sumOf(StudyTest::correctCount)
        val completedMockScores = input.mockExams.mapNotNull { mock ->
            mock.scorePercent?.takeIf { mock.submittedAtEpochMillis != null }
        }
        return StudyExamReportSnapshot(
            studyPlanName = input.plan.name,
            generatedAtEpochMillis = input.generatedAtEpochMillis,
            readinessScorePercent = readiness.scorePercent,
            readinessBand = readiness.band,
            readinessSignals = readiness.signals.map { signal ->
                StudyExamReportSignal(signal.kind, signal.scorePercent)
            },
            weeklyCoachItems = input.weeklyReview.items,
            studiedMinutesLastSevenDays = analytics.studiedMinutesLastSevenDays,
            evidenceCount = input.totalEvidenceCount,
            weakAreas = analytics.weakAreas,
            missedActivityCount = input.schedule.plannedActivities.count { activity ->
                activity.status == PlannedActivityStatus.MISSED
            },
            dueReviewCount = analytics.dueReviewCount,
            overdueReviewCount = analytics.overdueReviewCount,
            practiceTestCount = input.tests.size,
            completedPracticeTestCount = completedTests.size,
            practiceTestAccuracyPercent = if (testQuestionTotal == 0) {
                null
            } else {
                ((testCorrect.toDouble() / testQuestionTotal) * 100.0).toInt().coerceIn(0, 100)
            },
            mockExamCount = input.mockExams.size,
            completedMockExamCount = completedMockScores.size,
            bestMockScorePercent = completedMockScores.maxOrNull()?.toInt(),
            averageMockScorePercent = completedMockScores.takeIf { it.isNotEmpty() }?.average()?.toInt(),
            recentQuestionCount = input.questions.count { question ->
                question.createdAtEpochMillis >= input.generatedAtEpochMillis - RECENT_QUESTION_WINDOW_MILLIS
            },
            executableRescheduleChangeCount = input.reschedulePreview?.executableChangeCount ?: 0,
            cleanupIssueCount = input.cleanupReport.issueCount,
            cleanupActionCount = input.cleanupReport.actionCount,
            cleanupHealthScorePercent = input.cleanupReport.healthScorePercent,
            nextActions = nextActions(input),
            metricProvenance = metricProvenance(input),
        )
    }

    private fun metricProvenance(input: StudyExamReportInput): List<StudyExamReportMetricProvenance> = listOf(
        StudyExamReportMetricProvenance("readiness", StudyExamReportMetricSource.READINESS),
        StudyExamReportMetricProvenance("study_time", StudyExamReportMetricSource.ANALYTICS),
        StudyExamReportMetricProvenance("question_accuracy", StudyExamReportMetricSource.ANALYTICS),
        StudyExamReportMetricProvenance("evidence_count", StudyExamReportMetricSource.RAW_EVIDENCE),
        StudyExamReportMetricProvenance("schedule", StudyExamReportMetricSource.SCHEDULE),
        StudyExamReportMetricProvenance(
            "reviews",
            if (input.analytics != null) StudyExamReportMetricSource
                .ANALYTICS else StudyExamReportMetricSource.REVIEW_STATE,
        ),
        StudyExamReportMetricProvenance("content_health", StudyExamReportMetricSource.CONTENT_HEALTH),
    )

    private fun nextActions(input: StudyExamReportInput): List<StudyExamReportNextActionKind> = buildList {
        val analytics = input.analytics
        val readiness = input.readiness
        if ((analytics?.overdueReviewCount ?: 0) > 0) {
            add(StudyExamReportNextActionKind.REVIEW_BACKLOG)
        }
        if (input.schedule.plannedActivities.any { it.status == PlannedActivityStatus.MISSED }) {
            add(StudyExamReportNextActionKind.RECOVER_SCHEDULE)
        }
        if (analytics?.weakAreas?.isNotEmpty() == true || (analytics?.questionAccuracyPercent ?: 100) < 60) {
            add(StudyExamReportNextActionKind.PRACTICE_WEAK_AREAS)
        }
        if (readiness != null && readiness.mockExamHealthPercent < 60) {
            add(StudyExamReportNextActionKind.RUN_MOCK_EXAM)
        }
        if (input.cleanupReport.issueCount > 0) {
            add(StudyExamReportNextActionKind.REVIEW_CONTENT_CLEANUP)
        }
        if ((input.reschedulePreview?.executableChangeCount ?: 0) > 0) {
            add(StudyExamReportNextActionKind.APPLY_SAFE_RESCHEDULE)
        }
        if (isEmpty()) add(StudyExamReportNextActionKind.KEEP_PLAN)
    }.distinct().take(5)
}
