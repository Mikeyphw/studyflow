package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.ExamReadinessBand
import com.mikeyphw.studyflow.core.model.ExamReadinessScore
import com.mikeyphw.studyflow.core.model.PlannedActivity
import com.mikeyphw.studyflow.core.model.PlannedActivityId
import com.mikeyphw.studyflow.core.model.PlannedActivityStatus
import com.mikeyphw.studyflow.core.model.StudyActivityType
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyPlanStatus
import com.mikeyphw.studyflow.core.model.StudySchedule
import com.mikeyphw.studyflow.core.model.StudyType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyExamRunbookPlannerTest {
    @Test
    fun noExamDateIsExplicitlyNotApplicable() {
        val runbook = StudyExamRunbookPlanner.build(
            plan = plan(examDateIso = null),
            analytics = analytics(),
            readiness = readiness(daysUntilExam = null),
            schedule = StudySchedule(PlanId, null, emptyList(), emptyList()),
            mockExams = emptyList(),
        )

        assertFalse(runbook.applicable)
        assertEquals(StudyExamRunbookState.NO_EXAM_DATE, runbook.state)
        assertTrue(runbook.items.isEmpty())
    }

    @Test
    fun runbookPrioritizesBacklogWeaknessPracticeAndFinalReview() {
        val runbook = StudyExamRunbookPlanner.build(
            plan = plan(examDateIso = "2026-08-16"),
            analytics = analytics(overdue = 4, accuracy = 52, weakAreas = listOf(WeakStudyArea("Math", 40, 35, 1))),
            readiness = readiness(daysUntilExam = 2),
            schedule = StudySchedule(PlanId, null, emptyList(), emptyList()),
            mockExams = emptyList(),
        )

        assertTrue(runbook.applicable)
        assertTrue(runbook.items.any { item -> item.action == StudyExamRunbookAction.RECOVER_REVIEWS })
        assertTrue(runbook.items.any { item -> item.action == StudyExamRunbookAction.ATTACK_WEAK_AREA })
        assertTrue(runbook.items.any { item -> item.action == StudyExamRunbookAction.PRACTICE_RECALL })
        assertTrue(runbook.items.any { item -> item.action == StudyExamRunbookAction.MOCK_REHEARSAL })
        assertTrue(runbook.items.any { item -> item.action == StudyExamRunbookAction.FINAL_REVIEW })
        assertTrue(runbook.items.size <= 6)
    }


    @Test
    fun boundedRunbookKeepsFinalReviewAheadOfLowerPriorityAdvice() {
        val runbook = StudyExamRunbookPlanner.build(
            plan = plan(examDateIso = "2026-08-16"),
            analytics = analytics(
                overdue = 4,
                accuracy = 52,
                adherence = 50,
                weakAreas = listOf(
                    WeakStudyArea("Math", 40, 35, 1),
                    WeakStudyArea("Physics", 45, 38, 2),
                ),
            ),
            readiness = readiness(daysUntilExam = 2),
            schedule = StudySchedule(PlanId, null, emptyList(), emptyList()),
            mockExams = emptyList(),
        )

        assertEquals(6, runbook.items.size)
        assertTrue(runbook.items.any { item -> item.action == StudyExamRunbookAction.FINAL_REVIEW })
        assertTrue(runbook.items.any { item -> item.action == StudyExamRunbookAction.RECOVER_REVIEWS })
        assertTrue(runbook.items.any { item -> item.action == StudyExamRunbookAction.PROTECT_SCHEDULE })
    }

    @Test
    fun nestedScheduleEvidenceFailsClosed() {
        val foreignActivity = PlannedActivity(
            id = PlannedActivityId("foreign"),
            studyPlanId = StudyPlanId("other"),
            subjectId = null,
            topicId = null,
            materialId = null,
            videoResourceId = null,
            type = StudyActivityType.THEORY,
            title = "Foreign",
            scheduledStartEpochMillis = 1_800_000_000_000L,
            plannedDurationMinutes = 30,
            status = PlannedActivityStatus.MISSED,
            locked = false,
            completedAtEpochMillis = null,
        )

        val result = runCatching {
            StudyExamRunbookPlanner.build(
                plan = plan(examDateIso = "2026-08-24"),
                analytics = analytics(),
                readiness = readiness(daysUntilExam = 10),
                schedule = StudySchedule(PlanId, null, emptyList(), listOf(foreignActivity)),
                mockExams = emptyList(),
            )
        }

        assertTrue(result.isFailure)
    }

    @Test
    fun crossPlanReadinessEvidenceFailsClosed() {
        val foreignReadiness = readiness(daysUntilExam = 10).copy(studyPlanId = StudyPlanId("other"))

        val result = runCatching {
            StudyExamRunbookPlanner.build(
                plan = plan(examDateIso = "2026-08-24"),
                analytics = analytics(),
                readiness = foreignReadiness,
                schedule = StudySchedule(PlanId, null, emptyList(), emptyList()),
                mockExams = emptyList(),
            )
        }

        assertTrue(result.isFailure)
    }

    @Test
    fun healthyEvidenceAvoidsRecoveryWhileStillSurfacingMissingMockEvidence() {
        val runbook = StudyExamRunbookPlanner.build(
            plan = plan(examDateIso = "2026-09-30"),
            analytics = analytics(overdue = 0, accuracy = 82, adherence = 90),
            readiness = readiness(daysUntilExam = 40),
            schedule = StudySchedule(PlanId, null, emptyList(), emptyList()),
            mockExams = emptyList(),
        )

        assertTrue(runbook.items.any { item -> item.action == StudyExamRunbookAction.MOCK_REHEARSAL })
        assertTrue(runbook.items.none { item -> item.action == StudyExamRunbookAction.RECOVER_REVIEWS })
        assertTrue(runbook.items.none { item -> item.action == StudyExamRunbookAction.PROTECT_SCHEDULE })
    }

    private fun plan(examDateIso: String?) = StudyPlan(
        id = PlanId,
        name = "Exam",
        description = "",
        type = StudyType.PUBLIC_EXAMINATION,
        status = StudyPlanStatus.ACTIVE,
        examDateIso = examDateIso,
        weeklyGoalHours = 10,
        progressPercent = 60,
        studiedMinutesThisWeek = 180,
        questionsAnswered = 80,
        averageAccuracyPercent = 70,
    )

    private fun analytics(
        overdue: Int = 0,
        accuracy: Int = 80,
        adherence: Int = 85,
        weakAreas: List<WeakStudyArea> = emptyList(),
    ) = StudyAnalyticsSnapshot(
        studyPlanId = PlanId,
        studiedMinutesLastSevenDays = 180,
        completedSessionsLastSevenDays = 4,
        scheduleAdherencePercent = adherence,
        questionAccuracyPercent = accuracy,
        answeredQuestions = 80,
        dueReviewCount = overdue,
        overdueReviewCount = overdue,
        retentionEaseAverage = 2.5,
        goalCompletionPercent = 70,
        contentProgressPercent = 65,
        libraryProgressPercent = 60,
        consistencyScorePercent = 80,
        weakAreas = weakAreas,
        insights = emptyList(),
    )

    private fun readiness(daysUntilExam: Int?) = ExamReadinessScore(
        studyPlanId = PlanId,
        scorePercent = 65,
        band = ExamReadinessBand.WATCH,
        daysUntilExam = daysUntilExam,
        coveragePercent = 65,
        reviewHealthPercent = 65,
        practiceHealthPercent = 65,
        mockExamHealthPercent = 65,
        consistencyPercent = 80,
        scheduleHealthPercent = 80,
        timeFeasibilityPercent = 70,
        signals = emptyList(),
    )

    private companion object {
        val PlanId = StudyPlanId("plan")
    }
}
