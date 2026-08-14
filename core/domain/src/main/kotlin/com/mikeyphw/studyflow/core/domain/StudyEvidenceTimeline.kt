package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.MockExam
import com.mikeyphw.studyflow.core.model.PlannedActivity
import com.mikeyphw.studyflow.core.model.PlannedActivityStatus
import com.mikeyphw.studyflow.core.model.Question
import com.mikeyphw.studyflow.core.model.ReviewItem
import com.mikeyphw.studyflow.core.model.ReviewStatus
import com.mikeyphw.studyflow.core.model.StudyGoal
import com.mikeyphw.studyflow.core.model.StudyNote
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudySession
import com.mikeyphw.studyflow.core.model.StudySessionStatus
import com.mikeyphw.studyflow.core.model.StudyTest

enum class StudyEvidenceEventKind {
    SESSION_COMPLETED,
    SESSION_CANCELLED,
    PLANNED_ACTIVITY_COMPLETED,
    PLANNED_ACTIVITY_MISSED,
    REVIEW_DUE,
    REVIEW_COMPLETED,
    QUESTION_ADDED,
    PRACTICE_TEST_CREATED,
    PRACTICE_TEST_SUBMITTED,
    MOCK_EXAM_CREATED,
    MOCK_EXAM_SUBMITTED,
    GOAL_COMPLETED,
    NOTE_PINNED,
}

data class StudyEvidenceEvent(
    val id: String,
    val kind: StudyEvidenceEventKind,
    val occurredAtEpochMillis: Long,
    val label: String,
    val minutes: Int? = null,
    val count: Int? = null,
    val percent: Int? = null,
)

data class StudyEvidenceTimelineInput(
    val studyPlanId: StudyPlanId? = null,
    val plannedActivities: List<PlannedActivity>,
    val sessions: List<StudySession>,
    val reviewItems: List<ReviewItem>,
    val questions: List<Question>,
    val tests: List<StudyTest>,
    val mockExams: List<MockExam>,
    val goals: List<StudyGoal>,
    val notes: List<StudyNote>,
    val nowEpochMillis: Long,
    val limit: Int = 12,
) {
    init {
        require(nowEpochMillis >= 0L) { "Timeline inspection time cannot be negative." }
        require(limit in 1..50) { "Timeline limit must be between 1 and 50." }
    }
}

object StudyEvidenceTimelineBuilder {
    fun build(input: StudyEvidenceTimelineInput): List<StudyEvidenceEvent> = buildAll(input).take(input.limit)

    fun buildAll(input: StudyEvidenceTimelineInput): List<StudyEvidenceEvent> {
        val targetPlanId = input.studyPlanId
        val linkedCompletedActivityIds = input.sessions
            .asSequence()
            .filter { session ->
                (targetPlanId == null || session.studyPlanId == targetPlanId) &&
                    session.status == StudySessionStatus.COMPLETED
            }
            .mapNotNull(StudySession::plannedActivityId)
            .toSet()
        return buildList {
            addAll(
                input.sessions
                    .filter { session -> targetPlanId == null || session.studyPlanId == targetPlanId }
                    .mapNotNull(::sessionEvent),
            )
            addAll(
                input.plannedActivities
                    .filter { activity -> targetPlanId == null || activity.studyPlanId == targetPlanId }
                    .filterNot { activity ->
                        activity.status == PlannedActivityStatus.COMPLETED && activity.id in linkedCompletedActivityIds
                    }
                    .mapNotNull(::plannedActivityEvent),
            )
            addAll(
                input.reviewItems
                    .filter { review -> targetPlanId == null || review.studyPlanId == targetPlanId }
                    .mapNotNull(::reviewEvent),
            )
            addAll(
                input.questions
                    .filter { question -> targetPlanId == null || question.studyPlanId == targetPlanId }
                    .map(::questionEvent),
            )
            addAll(
                input.tests
                    .filter { test -> targetPlanId == null || test.studyPlanId == targetPlanId }
                    .mapNotNull(::testEvent),
            )
            addAll(
                input.mockExams
                    .filter { mockExam -> targetPlanId == null || mockExam.studyPlanId == targetPlanId }
                    .mapNotNull(::mockExamEvent),
            )
            addAll(
                input.goals
                    .filter { goal -> targetPlanId == null || goal.studyPlanId == targetPlanId }
                    .mapNotNull(::goalEvent),
            )
            addAll(
                input.notes
                    .filter { note -> targetPlanId == null || note.studyPlanId == targetPlanId }
                    .mapNotNull(::noteEvent),
            )
        }
            .filter { event -> event.occurredAtEpochMillis <= input.nowEpochMillis }
            .sortedWith(
                compareByDescending<StudyEvidenceEvent> { event -> event.occurredAtEpochMillis }
                    .thenBy { event -> event.id },
            )
    }

    private fun sessionEvent(session: StudySession): StudyEvidenceEvent? {
        val eventTime = session.endedAtEpochMillis ?: session.startedAtEpochMillis
        val kind = when (session.status) {
            StudySessionStatus.COMPLETED -> StudyEvidenceEventKind.SESSION_COMPLETED
            StudySessionStatus.CANCELLED -> StudyEvidenceEventKind.SESSION_CANCELLED
            StudySessionStatus.RUNNING,
            StudySessionStatus.PAUSED -> return null
        }
        val seconds = StudySessionTimer.activeSeconds(session, eventTime)
        return StudyEvidenceEvent(
            id = "session:${session.id.value}",
            kind = kind,
            occurredAtEpochMillis = eventTime,
            label = session.type.name,
            minutes = (seconds / 60L).toInt(),
            count = session.questionsAnswered.takeIf { value -> value > 0 },
            percent = if (session.questionsAnswered > 0) {
                StudyAnalyticsCalculator.percent(session.correctAnswers, session.questionsAnswered)
            } else {
                null
            },
        )
    }

    private fun plannedActivityEvent(activity: PlannedActivity): StudyEvidenceEvent? {
        val kind = when (activity.status) {
            PlannedActivityStatus.COMPLETED -> StudyEvidenceEventKind.PLANNED_ACTIVITY_COMPLETED
            PlannedActivityStatus.MISSED -> StudyEvidenceEventKind.PLANNED_ACTIVITY_MISSED
            PlannedActivityStatus.PLANNED,
            PlannedActivityStatus.IN_PROGRESS,
            PlannedActivityStatus.SKIPPED,
            PlannedActivityStatus.CANCELLED -> return null
        }
        val eventTime = activity.completedAtEpochMillis ?: activity.scheduledStartEpochMillis ?: return null
        return StudyEvidenceEvent(
            id = "activity:${activity.id.value}",
            kind = kind,
            occurredAtEpochMillis = eventTime,
            label = activity.title,
            minutes = activity.plannedDurationMinutes,
        )
    }

    private fun reviewEvent(review: ReviewItem): StudyEvidenceEvent? {
        val completedAt = review.completedAtEpochMillis
        return if (completedAt != null && review.status == ReviewStatus.COMPLETED) {
            StudyEvidenceEvent(
                id = "review:${review.id.value}:completed",
                kind = StudyEvidenceEventKind.REVIEW_COMPLETED,
                occurredAtEpochMillis = completedAt,
                label = review.method.name,
                count = review.repetitionCount,
            )
        } else if (ReviewDuePolicy.isDue(review, review.dueAtEpochMillis)) {
            StudyEvidenceEvent(
                id = "review:${review.id.value}:due",
                kind = StudyEvidenceEventKind.REVIEW_DUE,
                occurredAtEpochMillis = review.dueAtEpochMillis,
                label = review.method.name,
                count = review.repetitionCount,
            )
        } else {
            null
        }
    }

    private fun questionEvent(question: Question): StudyEvidenceEvent = StudyEvidenceEvent(
        id = "question:${question.id.value}",
        kind = StudyEvidenceEventKind.QUESTION_ADDED,
        occurredAtEpochMillis = question.createdAtEpochMillis,
        label = question.statement.take(80),
        count = question.tags.size.takeIf { value -> value > 0 },
    )

    private fun testEvent(test: StudyTest): StudyEvidenceEvent? {
        val submittedAt = test.submittedAtEpochMillis
        val kind = if (submittedAt == null) {
            StudyEvidenceEventKind.PRACTICE_TEST_CREATED
        } else {
            StudyEvidenceEventKind.PRACTICE_TEST_SUBMITTED
        }
        val eventTime = submittedAt ?: test.startedAtEpochMillis ?: return null
        return StudyEvidenceEvent(
            id = "test:${test.id.value}",
            kind = kind,
            occurredAtEpochMillis = eventTime,
            label = test.title,
            count = test.questionCount,
            percent = if (submittedAt != null && test.questionCount > 0) {
                StudyAnalyticsCalculator.percent(test.correctCount, test.questionCount)
            } else {
                null
            },
        )
    }

    private fun mockExamEvent(mockExam: MockExam): StudyEvidenceEvent? {
        val submittedAt = mockExam.submittedAtEpochMillis
        val kind = if (submittedAt == null) {
            StudyEvidenceEventKind.MOCK_EXAM_CREATED
        } else {
            StudyEvidenceEventKind.MOCK_EXAM_SUBMITTED
        }
        val eventTime = submittedAt ?: mockExam.startedAtEpochMillis ?: return null
        return StudyEvidenceEvent(
            id = "mock:${mockExam.id.value}",
            kind = kind,
            occurredAtEpochMillis = eventTime,
            label = mockExam.title,
            count = mockExam.questionCount,
            percent = mockExam.scorePercent?.toInt(),
        )
    }

    private fun goalEvent(goal: StudyGoal): StudyEvidenceEvent? {
        val completedAt = goal.completedAtEpochMillis ?: return null
        return StudyEvidenceEvent(
            id = "goal:${goal.id.value}",
            kind = StudyEvidenceEventKind.GOAL_COMPLETED,
            occurredAtEpochMillis = completedAt,
            label = goal.title,
            percent = 100,
        )
    }

    private fun noteEvent(note: StudyNote): StudyEvidenceEvent? {
        if (!note.pinned) return null
        return StudyEvidenceEvent(
            id = "note:${note.id.value}",
            kind = StudyEvidenceEventKind.NOTE_PINNED,
            occurredAtEpochMillis = note.updatedAtEpochMillis,
            label = note.title,
            count = note.tags.size.takeIf { value -> value > 0 },
        )
    }
}
