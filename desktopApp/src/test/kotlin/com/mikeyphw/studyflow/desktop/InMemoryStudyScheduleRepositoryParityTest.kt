package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.domain.AdaptiveReschedulePlanner
import com.mikeyphw.studyflow.core.domain.PlannedActivityRebalanceMutation
import com.mikeyphw.studyflow.core.domain.StudySchedulePlanner
import com.mikeyphw.studyflow.core.model.AdaptiveRescheduleChange
import com.mikeyphw.studyflow.core.model.AdaptiveRescheduleChangeType
import com.mikeyphw.studyflow.core.model.AdaptiveReschedulePreview
import com.mikeyphw.studyflow.core.model.PlannedActivity
import com.mikeyphw.studyflow.core.model.PlannedActivityId
import com.mikeyphw.studyflow.core.model.PlannedActivityStatus
import com.mikeyphw.studyflow.core.model.ScheduleRule
import com.mikeyphw.studyflow.core.model.ScheduleRuleId
import com.mikeyphw.studyflow.core.model.ScheduleRuleType
import com.mikeyphw.studyflow.core.model.StudyActivityType
import com.mikeyphw.studyflow.core.model.StudyPlanId
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class InMemoryStudyScheduleRepositoryParityTest {
    @Test
    fun replaceUnlockedPreservesHistoryAndSessionOwnedActivitiesAndReportsActualRemovalCount() = runBlocking {
        val repository = repository(
            activity("planned", status = PlannedActivityStatus.PLANNED),
            activity("missed", status = PlannedActivityStatus.MISSED),
            activity("completed", status = PlannedActivityStatus.COMPLETED),
            activity("running", status = PlannedActivityStatus.IN_PROGRESS),
            activity("locked", status = PlannedActivityStatus.PLANNED, locked = true),
        )
        val replacement = activity("replacement", start = 99_000L)

        val removed = repository.replaceUnlockedActivities(PlanId, listOf(replacement))
        val current = repository.currentActivities(PlanId).associateBy { it.id.value }

        assertEquals(2, removed)
        assertEquals(setOf("completed", "running", "locked", "replacement"), current.keys)
        assertEquals(PlannedActivityStatus.COMPLETED, current.getValue("completed").status)
        assertEquals(PlannedActivityStatus.IN_PROGRESS, current.getValue("running").status)
    }

    @Test
    fun generatedActivitiesRejectCrossPlanAndInProgressInputBeforeMutation() = runBlocking {
        val existing = activity("existing")
        val repository = repository(existing)

        expectFailure {
            repository.applyGeneratedActivities(
                PlanId,
                listOf(activity("foreign", planId = OtherPlanId)),
                replaceUnlocked = true,
            )
        }
        expectFailure {
            repository.applyGeneratedActivities(
                PlanId,
                listOf(activity("running-new", status = PlannedActivityStatus.IN_PROGRESS)),
                replaceUnlocked = true,
            )
        }

        assertEquals(listOf(existing), repository.currentActivities(PlanId))
    }

    @Test
    fun crudCannotBypassStudySessionOwnershipOrRewriteTerminalHistory() = runBlocking {
        val running = activity("running", status = PlannedActivityStatus.IN_PROGRESS)
        val completed = activity("completed", status = PlannedActivityStatus.COMPLETED)
        val planned = activity("planned")
        val repository = repository(running, completed, planned)

        expectFailure { repository.upsertPlannedActivity(running.copy(title = "edited")) }
        expectFailure {
            repository.updatePlannedActivityStatus(
                PlanId,
                running.id,
                PlannedActivityStatus.COMPLETED,
                completedAtEpochMillis = 4_000L,
            )
        }
        expectFailure { repository.deletePlannedActivity(PlanId, running.id) }
        expectFailure {
            repository.updatePlannedActivityStatus(
                PlanId,
                completed.id,
                PlannedActivityStatus.MISSED,
                completedAtEpochMillis = null,
            )
        }
        expectFailure {
            repository.updatePlannedActivityStatus(
                PlanId,
                planned.id,
                PlannedActivityStatus.IN_PROGRESS,
                completedAtEpochMillis = null,
            )
        }

        assertEquals(running, repository.currentActivities(PlanId).first { it.id == running.id })
        assertEquals(completed, repository.currentActivities(PlanId).first { it.id == completed.id })
    }

    @Test
    fun deletingRuleRetainsCompletedHistoryAndRefusesSessionOwnedGeneratedActivity() = runBlocking {
        val rule = rule("rule")
        val prefix = StudySchedulePlanner.generatedRulePrefix(rule.id)
        val planned = activity("${prefix}100", status = PlannedActivityStatus.PLANNED)
        val completed = activity("${prefix}200", status = PlannedActivityStatus.COMPLETED)
        val running = activity("${prefix}300", status = PlannedActivityStatus.IN_PROGRESS)
        val blocked = InMemoryStudyScheduleRepository(emptyList(), listOf(rule), listOf(planned, completed, running))

        expectFailure { blocked.deleteScheduleRule(PlanId, rule.id) }
        assertEquals(3, blocked.currentActivities(PlanId).size)
        assertEquals(1, blocked.currentRules(PlanId).size)

        val repository = InMemoryStudyScheduleRepository(emptyList(), listOf(rule), listOf(planned, completed))
        repository.deleteScheduleRule(PlanId, rule.id)

        assertTrue(repository.currentRules(PlanId).isEmpty())
        assertEquals(listOf(completed), repository.currentActivities(PlanId))
    }

    @Test
    fun adaptivePreviewUsesVersionAndCandidateValidationAtomically() = runBlocking {
        val first = activity("first", start = 10_000L)
        val second = activity("second", start = 20_000L)
        val repository = repository(first, second)
        val schedule = repository.currentSchedule(PlanId)
        val token = AdaptiveReschedulePlanner.scheduleVersionToken(schedule)
        val overlapping = preview(
            token = token,
            change = change(first, proposedStart = second.scheduledStartEpochMillis),
        )

        expectFailure { repository.applyAdaptivePreview(PlanId, overlapping) }
        assertEquals(listOf(first, second), repository.currentActivities(PlanId))

        val stale = preview(
            token = "stale-token",
            change = change(first, proposedStart = 40_000L),
        )
        expectFailure { repository.applyAdaptivePreview(PlanId, stale) }
        assertEquals(listOf(first, second), repository.currentActivities(PlanId))
    }

    @Test
    fun missedRebalanceRejectsStaleExpectedStateWithoutPartialMutation() = runBlocking {
        val first = activity("first", start = 10_000L, status = PlannedActivityStatus.MISSED)
        val second = activity("second", start = 20_000L, status = PlannedActivityStatus.MISSED)
        val repository = repository(first, second)
        val firstMutation = PlannedActivityRebalanceMutation(
            expected = first,
            proposed = first.copy(scheduledStartEpochMillis = 30_000L, status = PlannedActivityStatus.PLANNED),
        )
        val staleExpected = second.copy(plannedDurationMinutes = second.plannedDurationMinutes + 1)
        val secondMutation = PlannedActivityRebalanceMutation(
            expected = staleExpected,
            proposed = staleExpected.copy(scheduledStartEpochMillis = 40_000L, status = PlannedActivityStatus.PLANNED),
        )

        expectFailure { repository.applyMissedRebalance(PlanId, listOf(firstMutation, secondMutation)) }

        assertEquals(listOf(first, second), repository.currentActivities(PlanId))
    }

    @Test
    fun observeScheduleEmitsWhenRulesChange() = runBlocking {
        val repository = repository(activity("first"))
        assertTrue(repository.observeSchedule(PlanId).first().scheduleRules.isEmpty())
        val updated = async(start = CoroutineStart.UNDISPATCHED) {
            repository.observeSchedule(PlanId).first { schedule -> schedule.scheduleRules.isNotEmpty() }
        }

        repository.upsertScheduleRule(rule("rule"))

        assertEquals("rule", updated.await().scheduleRules.single().id.value)
    }

    private fun repository(vararg activities: PlannedActivity) =
        InMemoryStudyScheduleRepository(emptyList(), emptyList(), activities.toList())

    private fun activity(
        id: String,
        planId: StudyPlanId = PlanId,
        start: Long = 1_000L,
        status: PlannedActivityStatus = PlannedActivityStatus.PLANNED,
        locked: Boolean = false,
    ) = PlannedActivity(
        id = PlannedActivityId(id),
        studyPlanId = planId,
        subjectId = null,
        topicId = null,
        materialId = null,
        videoResourceId = null,
        type = StudyActivityType.THEORY,
        title = id,
        scheduledStartEpochMillis = start,
        plannedDurationMinutes = 45,
        status = status,
        locked = locked,
        completedAtEpochMillis = if (status == PlannedActivityStatus.COMPLETED) start + 45 * 60_000L else null,
    )

    private fun rule(id: String) = ScheduleRule(
        id = ScheduleRuleId(id),
        studyPlanId = PlanId,
        subjectId = null,
        type = ScheduleRuleType.RECURRING_BLOCK,
        dayOfWeek = 2,
        startMinuteOfDay = 9 * 60,
        durationMinutes = 45,
        recurrenceRule = "WEEKLY",
        locked = false,
        enabled = true,
        position = 0,
    )

    private fun preview(token: String, change: AdaptiveRescheduleChange) = AdaptiveReschedulePreview(
        studyPlanId = PlanId,
        generatedAtEpochMillis = 1L,
        readinessScorePercent = 50,
        missedUnlockedActivities = 0,
        overdueReviewCount = 0,
        scheduleAdherencePercent = 50,
        actionSummary = "BH07",
        changes = listOf(change),
        scheduleVersionToken = token,
    )

    private fun change(activity: PlannedActivity, proposedStart: Long?) = AdaptiveRescheduleChange(
        id = "change-${activity.id.value}",
        type = AdaptiveRescheduleChangeType.REDISTRIBUTE_MISSED,
        title = "Move",
        reason = "BH07 regression",
        plannedActivityId = activity.id,
        currentStartEpochMillis = activity.scheduledStartEpochMillis,
        proposedStartEpochMillis = proposedStart,
        currentDurationMinutes = activity.plannedDurationMinutes,
        proposedDurationMinutes = activity.plannedDurationMinutes,
        currentStatus = activity.status,
        proposedStatus = activity.status,
        locked = false,
        executable = true,
        currentType = activity.type,
        proposedType = activity.type,
    )

    private suspend fun expectFailure(block: suspend () -> Unit) {
        try {
            block()
            fail("Expected mutation to be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected: Room parity relies on require(...) for rejected mutation contracts.
        }
    }

    private companion object {
        val PlanId = StudyPlanId("bh07-plan")
        val OtherPlanId = StudyPlanId("bh07-other-plan")
    }
}
