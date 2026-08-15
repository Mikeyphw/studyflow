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

class StudySprintPlannerTest {
    @Test
    fun previewIsBoundedToExistingUnlockedSevenDayActivities() {
        val schedule = StudySchedule(
            studyPlanId = PlanId,
            strategy = null,
            scheduleRules = emptyList(),
            plannedActivities = listOf(
                activity("one", start = Now + Day, locked = false),
                activity("locked", start = Now + 2 * Day, locked = true),
                activity("outside", start = Now + 9 * Day, locked = false),
                activity("complete", start = Now + Day, locked = false, status = PlannedActivityStatus.COMPLETED),
            ),
        )

        val preview = StudySprintPlanner.preview(
            plan = plan(),
            analytics = analytics(overdue = 3),
            readiness = readiness(),
            schedule = schedule,
            nowEpochMillis = Now,
        )

        assertEquals(StudySprintFocus.REVIEW_RECOVERY, preview.focus)
        assertEquals(1, preview.candidateCount)
        assertEquals(
            listOf(PlannedActivityId("one")),
            preview.schedulePreview.changes.mapNotNull { change -> change.plannedActivityId },
        )
        assertTrue(preview.scheduleVersionToken.isNotBlank())
        assertEquals(
            AdaptiveReschedulePlanner.scheduleVersionToken(schedule),
            preview.scheduleVersionToken,
        )
    }

    @Test
    fun previewNeverMutatesSourceAndUsesStableScheduleVersion() {
        val original = activity("questions", start = Now + Day, locked = false, type = StudyActivityType.QUESTIONS)
        val schedule = StudySchedule(PlanId, null, emptyList(), listOf(original))

        val preview = StudySprintPlanner.preview(
            plan = plan(),
            analytics = analytics(accuracy = 45),
            readiness = readiness(),
            schedule = schedule,
            nowEpochMillis = Now,
        )

        assertEquals(StudyActivityType.QUESTIONS, schedule.plannedActivities.single().type)
        assertTrue(preview.executable)
        assertTrue(preview.schedulePreview.changes.all { change -> change.currentType != change.proposedType })
    }

    @Test
    fun crossPlanScheduleEvidenceFailsClosed() {
        val schedule = StudySchedule(
            StudyPlanId("other"),
            null,
            emptyList(),
            emptyList(),
        )

        val result = runCatching {
            StudySprintPlanner.preview(
                plan = plan(),
                analytics = analytics(),
                readiness = readiness(),
                schedule = schedule,
                nowEpochMillis = Now,
            )
        }

        assertTrue(result.isFailure)
    }


    @Test
    fun nestedScheduleActivityEvidenceFailsClosed() {
        val schedule = StudySchedule(
            PlanId,
            null,
            emptyList(),
            listOf(activity("foreign", Now + Day, locked = false).copy(studyPlanId = StudyPlanId("other"))),
        )

        val result = runCatching {
            StudySprintPlanner.preview(
                plan = plan(),
                analytics = analytics(),
                readiness = readiness(),
                schedule = schedule,
                nowEpochMillis = Now,
            )
        }

        assertTrue(result.isFailure)
    }

    @Test
    fun safeApplyEnvelopeRejectsMovedLengthenedDuplicateOrMismatchedChanges() {
        val schedule = StudySchedule(
            PlanId,
            null,
            emptyList(),
            listOf(activity("one", Now + Day, locked = false)),
        )
        val preview = StudySprintPlanner.preview(
            plan = plan(),
            analytics = analytics(overdue = 3),
            readiness = readiness(),
            schedule = schedule,
            nowEpochMillis = Now,
        )
        StudySprintPlanner.requireSafeApply(preview, schedule, Now)
        val change = preview.schedulePreview.changes.single { candidate -> candidate.executable }

        val moved = preview.copy(
            schedulePreview = preview.schedulePreview.copy(
                changes = listOf(
                    change.copy(
                        proposedStartEpochMillis = requireNotNull(change.currentStartEpochMillis) + Day,
                    ),
                ),
            ),
        )
        assertTrue(runCatching { StudySprintPlanner.requireSafeApply(moved, schedule, Now) }.isFailure)

        val lengthened = preview.copy(
            schedulePreview = preview.schedulePreview.copy(
                changes = listOf(
                    change.copy(
                        proposedDurationMinutes = requireNotNull(change.currentDurationMinutes) + 15,
                    ),
                ),
            ),
        )
        assertTrue(runCatching { StudySprintPlanner.requireSafeApply(lengthened, schedule, Now) }.isFailure)

        val duplicate = preview.copy(
            candidateCount = 2,
            schedulePreview = preview.schedulePreview.copy(changes = listOf(change, change.copy(id = "duplicate"))),
        )
        assertTrue(runCatching { StudySprintPlanner.requireSafeApply(duplicate, schedule, Now) }.isFailure)

        val mismatchedVersion = preview.copy(scheduleVersionToken = "other-version")
        assertTrue(runCatching { StudySprintPlanner.requireSafeApply(mismatchedVersion, schedule, Now) }.isFailure)

        val outsideActivity = activity("outside", Now + 9 * Day, locked = false)
        val scheduleWithOutside = schedule.copy(plannedActivities = schedule.plannedActivities + outsideActivity)
        val outsideChange = change.copy(plannedActivityId = outsideActivity.id, id = "outside-change")
        val outsidePreview = StudySprintPlanner.preview(
            plan = plan(),
            analytics = analytics(overdue = 3),
            readiness = readiness(),
            schedule = scheduleWithOutside,
            nowEpochMillis = Now,
        ).copy(
            schedulePreview = preview.schedulePreview.copy(
                scheduleVersionToken = AdaptiveReschedulePlanner.scheduleVersionToken(scheduleWithOutside),
                changes = listOf(outsideChange),
            ),
            scheduleVersionToken = AdaptiveReschedulePlanner.scheduleVersionToken(scheduleWithOutside),
        )
        assertTrue(
            runCatching { StudySprintPlanner.requireSafeApply(outsidePreview, scheduleWithOutside, Now) }.isFailure,
        )

        assertTrue(
            runCatching { StudySprintPlanner.requireSafeApply(preview, schedule, Now + 3 * 60_000L) }.isFailure,
        )
    }

    @Test
    fun healthyScheduleCanReturnPreviewOnlyInsteadOfInventingMutation() {
        val schedule = StudySchedule(
            PlanId,
            null,
            emptyList(),
            listOf(activity("steady", Now + Day, locked = false, duration = 45)),
        )

        val preview = StudySprintPlanner.preview(
            plan = plan(),
            analytics = analytics(),
            readiness = readiness(),
            schedule = schedule,
            nowEpochMillis = Now,
        )

        assertEquals(StudySprintFocus.BALANCED, preview.focus)
        assertFalse(preview.executable)
        assertEquals(0, preview.executableChangeCount)
    }

    private fun plan() = StudyPlan(
        id = PlanId,
        name = "Plan",
        description = "",
        type = StudyType.PUBLIC_EXAMINATION,
        status = StudyPlanStatus.ACTIVE,
        examDateIso = "2026-09-01",
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
        weakAreas = emptyList(),
        insights = emptyList(),
    )

    private fun readiness() = ExamReadinessScore(
        studyPlanId = PlanId,
        scorePercent = 65,
        band = ExamReadinessBand.WATCH,
        daysUntilExam = 18,
        coveragePercent = 65,
        reviewHealthPercent = 65,
        practiceHealthPercent = 65,
        mockExamHealthPercent = 65,
        consistencyPercent = 80,
        scheduleHealthPercent = 80,
        timeFeasibilityPercent = 70,
        signals = emptyList(),
    )

    private fun activity(
        id: String,
        start: Long,
        locked: Boolean,
        status: PlannedActivityStatus = PlannedActivityStatus.PLANNED,
        type: StudyActivityType = StudyActivityType.THEORY,
        duration: Int = 90,
    ) = PlannedActivity(
        id = PlannedActivityId(id),
        studyPlanId = PlanId,
        subjectId = null,
        topicId = null,
        materialId = null,
        videoResourceId = null,
        type = type,
        title = id,
        scheduledStartEpochMillis = start,
        plannedDurationMinutes = duration,
        status = status,
        locked = locked,
        completedAtEpochMillis = if (status == PlannedActivityStatus.COMPLETED) start else null,
    )

    private companion object {
        val PlanId = StudyPlanId("plan")
        const val Now = 1_800_000_000_000L
        const val Day = 86_400_000L
    }
}
