package com.mikeyphw.studyflow.feature.workspace

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mikeyphw.studyflow.core.domain.ManageStudySchedule
import com.mikeyphw.studyflow.core.domain.StudyAnalyticsSnapshot
import com.mikeyphw.studyflow.core.domain.StudyExamRunbookAction
import com.mikeyphw.studyflow.core.domain.StudyExamRunbookPlanner
import com.mikeyphw.studyflow.core.domain.StudyScheduleRepository
import com.mikeyphw.studyflow.core.domain.StudySprintFocus
import com.mikeyphw.studyflow.core.domain.StudySprintPlanner
import com.mikeyphw.studyflow.core.model.AdaptiveReschedulePreview
import com.mikeyphw.studyflow.core.model.ExamReadinessBand
import com.mikeyphw.studyflow.core.model.ExamReadinessScore
import com.mikeyphw.studyflow.core.model.PlannedActivity
import com.mikeyphw.studyflow.core.model.PlannedActivityId
import com.mikeyphw.studyflow.core.model.PlannedActivityStatus
import com.mikeyphw.studyflow.core.model.ScheduleGenerationResult
import com.mikeyphw.studyflow.core.model.ScheduleRule
import com.mikeyphw.studyflow.core.model.ScheduleRuleId
import com.mikeyphw.studyflow.core.model.StudyActivityType
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyPlanStatus
import com.mikeyphw.studyflow.core.model.StudySchedule
import com.mikeyphw.studyflow.core.model.StudyStrategy
import com.mikeyphw.studyflow.core.model.StudyType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspacePhase63_64ConnectedTest {
    @Test
    fun phase63RunbookAndPhase64SprintUseTheVersionBoundWorkspaceBoundary() = runBlocking {
        val planId = StudyPlanId("workspace-phase-63-64")
        val plan = StudyPlan(
            id = planId,
            name = "Connected exam plan",
            description = "",
            type = StudyType.PUBLIC_EXAMINATION,
            status = StudyPlanStatus.ACTIVE,
            examDateIso = "2027-01-10",
            weeklyGoalHours = 8,
            progressPercent = 55,
            studiedMinutesThisWeek = 180,
            questionsAnswered = 80,
            averageAccuracyPercent = 58,
        )
        val analytics = StudyAnalyticsSnapshot(
            studyPlanId = planId,
            studiedMinutesLastSevenDays = 180,
            completedSessionsLastSevenDays = 4,
            scheduleAdherencePercent = 72,
            questionAccuracyPercent = 58,
            answeredQuestions = 80,
            dueReviewCount = 3,
            overdueReviewCount = 2,
            retentionEaseAverage = 2.4,
            goalCompletionPercent = 65,
            contentProgressPercent = 60,
            libraryProgressPercent = 60,
            consistencyScorePercent = 75,
            weakAreas = emptyList(),
            insights = emptyList(),
        )
        val readiness = ExamReadinessScore(
            studyPlanId = planId,
            scorePercent = 62,
            band = ExamReadinessBand.WATCH,
            daysUntilExam = 2,
            coveragePercent = 65,
            reviewHealthPercent = 55,
            practiceHealthPercent = 58,
            mockExamHealthPercent = 50,
            consistencyPercent = 75,
            scheduleHealthPercent = 72,
            timeFeasibilityPercent = 70,
            signals = emptyList(),
        )
        val activity = PlannedActivity(
            id = PlannedActivityId("workspace-sprint-candidate"),
            studyPlanId = planId,
            subjectId = null,
            topicId = null,
            materialId = null,
            videoResourceId = null,
            type = StudyActivityType.QUESTIONS,
            title = "Connected sprint candidate",
            scheduledStartEpochMillis = Now + Day,
            plannedDurationMinutes = 90,
            status = PlannedActivityStatus.PLANNED,
            locked = false,
            completedAtEpochMillis = null,
        )
        val schedule = StudySchedule(planId, null, emptyList(), listOf(activity))

        val runbook = StudyExamRunbookPlanner.build(plan, analytics, readiness, schedule, emptyList())
        assertTrue(runbook.applicable)
        assertTrue(runbook.items.any { item -> item.action == StudyExamRunbookAction.RECOVER_REVIEWS })
        assertTrue(runbook.items.any { item -> item.action == StudyExamRunbookAction.FINAL_REVIEW })

        val sprint = StudySprintPlanner.preview(plan, analytics, readiness, schedule, Now)
        assertEquals(StudySprintFocus.REVIEW_RECOVERY, sprint.focus)
        assertTrue(sprint.executable)
        assertTrue(sprint.schedulePreview.changes.any { change ->
            change.executable && change.plannedActivityId == activity.id
        })

        val repository = RecordingScheduleRepository(schedule)
        val manager = ManageStudySchedule(repository = repository, clock = { Now + 1_000L })
        assertTrue(manager.applyVersionedPreview(planId, sprint) > 0)
        assertSame(sprint.schedulePreview, repository.appliedPreview)

        val context = ApplicationProvider.getApplicationContext<Context>()
        assertTrue(context.getString(R.string.exam_runbook_title).isNotBlank())
        assertTrue(context.getString(R.string.study_sprint_title).isNotBlank())
    }

    private class RecordingScheduleRepository(initial: StudySchedule) : StudyScheduleRepository {
        private val schedule = MutableStateFlow(initial)
        var appliedPreview: AdaptiveReschedulePreview? = null

        override fun observeSchedule(planId: StudyPlanId) = schedule

        override suspend fun applyAdaptivePreview(
            planId: StudyPlanId,
            preview: AdaptiveReschedulePreview,
        ): Int {
            require(planId == schedule.value.studyPlanId)
            appliedPreview = preview
            return preview.executableChangeCount
        }

        override suspend fun upsertStrategy(strategy: StudyStrategy): StudyStrategy = unsupported()

        override suspend fun upsertScheduleRule(rule: ScheduleRule): ScheduleRule = unsupported()

        override suspend fun deleteScheduleRule(planId: StudyPlanId, ruleId: ScheduleRuleId): Unit = unsupported()

        override suspend fun upsertPlannedActivity(activity: PlannedActivity): PlannedActivity = unsupported()

        override suspend fun updatePlannedActivityStatus(
            planId: StudyPlanId,
            activityId: PlannedActivityId,
            status: PlannedActivityStatus,
            completedAtEpochMillis: Long?,
        ): PlannedActivity = unsupported()

        override suspend fun deletePlannedActivity(planId: StudyPlanId, activityId: PlannedActivityId): Unit =
            unsupported()

        override suspend fun replaceUnlockedActivities(
            planId: StudyPlanId,
            activities: List<PlannedActivity>,
        ): Int = unsupported()

        override suspend fun applyGeneratedActivities(
            planId: StudyPlanId,
            activities: List<PlannedActivity>,
            replaceUnlocked: Boolean,
        ): ScheduleGenerationResult = unsupported()

        private fun <T> unsupported(): T = error("Not used by the connected Phase 63/64 acceptance test")
    }

    private companion object {
        const val Now = 1_800_000_000_000L
        const val Day = 86_400_000L
    }
}
