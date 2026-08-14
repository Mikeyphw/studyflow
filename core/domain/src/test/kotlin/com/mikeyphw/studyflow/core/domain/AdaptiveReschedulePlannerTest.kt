package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.AdaptiveRescheduleChangeType
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveReschedulePlannerTest {
    @Test
    fun previewRedistributesMissedUnlockedWorkAndKeepsLockedBlocksUntouched() {
        val locked = activity("locked", locked = true, status = PlannedActivityStatus.MISSED)
        val missed = activity("missed", locked = false, status = PlannedActivityStatus.MISSED)
        val preview = AdaptiveReschedulePlanner.preview(
            plan = plan(),
            analytics = analytics(overdue = 0, accuracy = 75, adherence = 40),
            schedule = StudySchedule(StudyPlanId("plan"), null, emptyList(), listOf(locked, missed)),
            readinessScorePercent = 58,
            nowEpochMillis = 1_000_000L,
        )

        assertEquals(1, preview.executableChangeCount)
        assertTrue(preview.changes.none { it.plannedActivityId == locked.id && it.executable })
        assertEquals(AdaptiveRescheduleChangeType.REDISTRIBUTE_MISSED, preview.changes.first().type)
    }

    @Test
    fun applyPreviewUpdatesOnlyExecutableExistingActivities() {
        val missed = activity("missed", locked = false, status = PlannedActivityStatus.MISSED)
        val schedule = StudySchedule(StudyPlanId("plan"), null, emptyList(), listOf(missed))
        val preview = AdaptiveReschedulePlanner.preview(
            plan = plan(),
            analytics = analytics(overdue = 3, accuracy = 75, adherence = 50),
            schedule = schedule,
            readinessScorePercent = 52,
            nowEpochMillis = 1_000_000L,
        )
        val updates = AdaptiveReschedulePlanner.applyPreview(schedule.plannedActivities, preview)

        assertEquals(1, updates.size)
        assertEquals(PlannedActivityStatus.PLANNED, updates.single().status)
        assertEquals(null, updates.single().completedAtEpochMillis)
    }

    @Test
    fun theoryRecoveryIsARealTypeChangeInsteadOfAnExecutableNoOp() {
        val questions = activity("questions", locked = false, status = PlannedActivityStatus.PLANNED).copy(
            type = StudyActivityType.QUESTIONS,
            scheduledStartEpochMillis = 2_000_000L,
        )
        val schedule = StudySchedule(StudyPlanId("plan"), null, emptyList(), listOf(questions))
        val preview = AdaptiveReschedulePlanner.preview(
            plan = plan(),
            analytics = analytics(overdue = 0, accuracy = 40, adherence = 80),
            schedule = schedule,
            readinessScorePercent = 70,
            nowEpochMillis = 1_000_000L,
        )
        val update = AdaptiveReschedulePlanner.applyPreview(schedule.plannedActivities, preview).single()

        assertEquals(StudyActivityType.THEORY, update.type)
        assertTrue(preview.changes.any { it.type == AdaptiveRescheduleChangeType
            .ADD_THEORY_BEFORE_QUESTIONS && it.executable })
    }

    @Test
    fun scheduleVersionChangesWhenApprovedInputsChange() {
        val activity = activity("one", locked = false, status = PlannedActivityStatus.PLANNED)
        val first = StudySchedule(StudyPlanId("plan"), null, emptyList(), listOf(activity))
        val second = first.copy(plannedActivities = listOf(activity.copy(plannedDurationMinutes = 60)))

        assertNotEquals(
            AdaptiveReschedulePlanner.scheduleVersionToken(first),
            AdaptiveReschedulePlanner.scheduleVersionToken(second),
        )
    }

    private fun plan() = StudyPlan(
        id = StudyPlanId("plan"),
        name = "Exam",
        description = "",
        type = StudyType.PUBLIC_EXAMINATION,
        status = StudyPlanStatus.ACTIVE,
        examDateIso = null,
        weeklyGoalHours = 10,
        progressPercent = 50,
        studiedMinutesThisWeek = 100,
        questionsAnswered = 40,
        averageAccuracyPercent = 60,
    )

    private fun analytics(overdue: Int, accuracy: Int, adherence: Int) = StudyAnalyticsSnapshot(
        studyPlanId = StudyPlanId("plan"),
        studiedMinutesLastSevenDays = 120,
        completedSessionsLastSevenDays = 3,
        scheduleAdherencePercent = adherence,
        questionAccuracyPercent = accuracy,
        answeredQuestions = 40,
        dueReviewCount = overdue,
        overdueReviewCount = overdue,
        retentionEaseAverage = 2.3,
        goalCompletionPercent = 60,
        contentProgressPercent = 55,
        libraryProgressPercent = 50,
        consistencyScorePercent = 50,
        weakAreas = emptyList(),
        insights = emptyList(),
    )

    private fun activity(
        id: String,
        locked: Boolean,
        status: PlannedActivityStatus,
    ) = PlannedActivity(
        id = PlannedActivityId(id),
        studyPlanId = StudyPlanId("plan"),
        subjectId = null,
        topicId = null,
        materialId = null,
        videoResourceId = null,
        type = StudyActivityType.THEORY,
        title = id,
        scheduledStartEpochMillis = 100L,
        plannedDurationMinutes = 50,
        status = status,
        locked = locked,
        completedAtEpochMillis = if (status == PlannedActivityStatus.MISSED) 10L else null,
    )
}
