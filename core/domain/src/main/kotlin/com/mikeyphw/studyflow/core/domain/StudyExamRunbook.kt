package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.ExamReadinessBand
import com.mikeyphw.studyflow.core.model.ExamReadinessScore
import com.mikeyphw.studyflow.core.model.MockExam
import com.mikeyphw.studyflow.core.model.PlannedActivityStatus
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudySchedule

enum class StudyExamRunbookState {
    READY,
    NO_EXAM_DATE,
}

enum class StudyExamRunbookWindow {
    NOW,
    THIS_WEEK,
    EXAM_EVE,
}

enum class StudyExamRunbookAction {
    RECOVER_REVIEWS,
    ATTACK_WEAK_AREA,
    PRACTICE_RECALL,
    MOCK_REHEARSAL,
    PROTECT_SCHEDULE,
    FINAL_REVIEW,
    MAINTAIN_MOMENTUM,
}

data class StudyExamRunbookItem(
    val id: String,
    val window: StudyExamRunbookWindow,
    val action: StudyExamRunbookAction,
    val count: Int? = null,
    val focusLabel: String? = null,
)

data class StudyExamRunbook(
    val state: StudyExamRunbookState,
    val daysUntilExam: Int?,
    val readinessBand: ExamReadinessBand,
    val items: List<StudyExamRunbookItem>,
) {
    val applicable: Boolean = state == StudyExamRunbookState.READY
}

object StudyExamRunbookPlanner {
    fun build(
        plan: StudyPlan,
        analytics: StudyAnalyticsSnapshot,
        readiness: ExamReadinessScore,
        schedule: StudySchedule,
        mockExams: List<MockExam>,
    ): StudyExamRunbook {
        require(analytics.studyPlanId == plan.id) { "Runbook analytics belong to another Study Plan." }
        require(readiness.studyPlanId == plan.id) { "Runbook readiness belongs to another Study Plan." }
        require(schedule.studyPlanId == plan.id) { "Runbook schedule belongs to another Study Plan." }
        require(schedule.strategy == null || schedule.strategy.studyPlanId == plan.id) {
            "Runbook strategy evidence belongs to another Study Plan."
        }
        require(schedule.scheduleRules.all { rule -> rule.studyPlanId == plan.id }) {
            "Runbook schedule-rule evidence belongs to another Study Plan."
        }
        require(schedule.plannedActivities.all { activity -> activity.studyPlanId == plan.id }) {
            "Runbook activity evidence belongs to another Study Plan."
        }
        require(mockExams.all { mock -> mock.studyPlanId == plan.id }) {
            "Runbook mock evidence belongs to another Study Plan."
        }
        val daysUntilExam = readiness.daysUntilExam
        if (plan.examDateIso == null || daysUntilExam == null) {
            return StudyExamRunbook(
                state = StudyExamRunbookState.NO_EXAM_DATE,
                daysUntilExam = null,
                readinessBand = readiness.band,
                items = emptyList(),
            )
        }

        val items = buildList {
            if (analytics.overdueReviewCount > 0) {
                add(
                    StudyExamRunbookItem(
                        id = "reviews-overdue",
                        window = StudyExamRunbookWindow.NOW,
                        action = StudyExamRunbookAction.RECOVER_REVIEWS,
                        count = analytics.overdueReviewCount,
                    ),
                )
            }
            analytics.weakAreas.take(MaxWeakAreas).forEachIndexed { index, weakArea ->
                add(
                    StudyExamRunbookItem(
                        id = "weak-area-$index",
                        window = StudyExamRunbookWindow.THIS_WEEK,
                        action = StudyExamRunbookAction.ATTACK_WEAK_AREA,
                        focusLabel = weakArea.label,
                    ),
                )
            }
            if (analytics.questionAccuracyPercent in 1 until PracticeAccuracyHealthy) {
                add(
                    StudyExamRunbookItem(
                        id = "practice-recall",
                        window = StudyExamRunbookWindow.THIS_WEEK,
                        action = StudyExamRunbookAction.PRACTICE_RECALL,
                    ),
                )
            }
            val submittedMockCount = mockExams.count { mock -> mock.submittedAtEpochMillis != null }
            if (daysUntilExam >= MinimumMockLeadDays && submittedMockCount == 0) {
                add(
                    StudyExamRunbookItem(
                        id = "mock-rehearsal",
                        window = StudyExamRunbookWindow.THIS_WEEK,
                        action = StudyExamRunbookAction.MOCK_REHEARSAL,
                    ),
                )
            }
            val missedCount = schedule.plannedActivities.count { activity ->
                activity.status == PlannedActivityStatus.MISSED
            }
            if (missedCount > 0 || analytics.scheduleAdherencePercent < ScheduleAdherenceHealthy) {
                add(
                    StudyExamRunbookItem(
                        id = "protect-schedule",
                        window = StudyExamRunbookWindow.NOW,
                        action = StudyExamRunbookAction.PROTECT_SCHEDULE,
                        count = missedCount.takeIf { count -> count > 0 },
                    ),
                )
            }
            if (daysUntilExam <= FinalReviewWindowDays) {
                add(
                    StudyExamRunbookItem(
                        id = "final-review",
                        window = StudyExamRunbookWindow.EXAM_EVE,
                        action = StudyExamRunbookAction.FINAL_REVIEW,
                    ),
                )
            }
            if (isEmpty()) {
                add(
                    StudyExamRunbookItem(
                        id = "maintain-momentum",
                        window = StudyExamRunbookWindow.THIS_WEEK,
                        action = StudyExamRunbookAction.MAINTAIN_MOMENTUM,
                    ),
                )
            }
        }
        return StudyExamRunbook(
            state = StudyExamRunbookState.READY,
            daysUntilExam = daysUntilExam,
            readinessBand = readiness.band,
            items = items.sortedBy { item -> actionPriority(item.action) }.take(MaxItems),
        )
    }

    private fun actionPriority(action: StudyExamRunbookAction): Int = when (action) {
        StudyExamRunbookAction.RECOVER_REVIEWS -> 0
        StudyExamRunbookAction.FINAL_REVIEW -> 1
        StudyExamRunbookAction.PROTECT_SCHEDULE -> 2
        StudyExamRunbookAction.ATTACK_WEAK_AREA -> 3
        StudyExamRunbookAction.PRACTICE_RECALL -> 4
        StudyExamRunbookAction.MOCK_REHEARSAL -> 5
        StudyExamRunbookAction.MAINTAIN_MOMENTUM -> 6
    }

    private const val MaxWeakAreas = 2
    private const val MaxItems = 6
    private const val PracticeAccuracyHealthy = 70
    private const val ScheduleAdherenceHealthy = 70
    private const val MinimumMockLeadDays = 2
    private const val FinalReviewWindowDays = 2
}
