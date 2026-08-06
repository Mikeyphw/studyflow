package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.AdaptiveRescheduleApplyResult
import com.mikeyphw.studyflow.core.model.AdaptiveRescheduleChange
import com.mikeyphw.studyflow.core.model.AdaptiveRescheduleChangeType
import com.mikeyphw.studyflow.core.model.AdaptiveReschedulePreview
import com.mikeyphw.studyflow.core.model.MissedActivityHandling
import com.mikeyphw.studyflow.core.model.PlannedActivity
import com.mikeyphw.studyflow.core.model.PlannedActivityStatus
import com.mikeyphw.studyflow.core.model.StudyActivityType
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudySchedule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

class ManageAdaptiveReschedule(
    private val analyticsRepository: StudyAnalyticsRepository,
    private val scheduleRepository: StudyScheduleRepository,
    private val readinessManager: ManageExamReadiness,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun observePreview(plan: StudyPlan): Flow<AdaptiveReschedulePreview> = combine(
        analyticsRepository.observeSnapshot(plan.id),
        scheduleRepository.observeSchedule(plan.id),
        readinessManager.observeReadiness(plan),
    ) { analytics, schedule, readiness ->
        AdaptiveReschedulePlanner.preview(
            plan = plan,
            analytics = analytics,
            schedule = schedule,
            readinessScorePercent = readiness.scorePercent,
            nowEpochMillis = clock(),
        )
    }

    suspend fun applySafePreview(plan: StudyPlan): AdaptiveRescheduleApplyResult {
        val analytics = analyticsRepository.observeSnapshot(plan.id).first()
        val schedule = scheduleRepository.observeSchedule(plan.id).first()
        val readiness = readinessManager.observeReadiness(plan).first()
        val preview = AdaptiveReschedulePlanner.preview(
            plan = plan,
            analytics = analytics,
            schedule = schedule,
            readinessScorePercent = readiness.scorePercent,
            nowEpochMillis = clock(),
        )
        val updates = AdaptiveReschedulePlanner.applyPreview(schedule.plannedActivities, preview)
        val applied = if (updates.isEmpty()) 0 else scheduleRepository.updatePlannedActivities(plan.id, updates)
        return AdaptiveRescheduleApplyResult(
            studyPlanId = plan.id,
            requestedChanges = preview.executableChangeCount,
            appliedChanges = applied,
            skippedChanges = (preview.executableChangeCount - applied).coerceAtLeast(0),
        )
    }
}

object AdaptiveReschedulePlanner {
    fun preview(
        plan: StudyPlan,
        analytics: StudyAnalyticsSnapshot,
        schedule: StudySchedule,
        readinessScorePercent: Int,
        nowEpochMillis: Long,
    ): AdaptiveReschedulePreview {
        val changes = buildList {
            addAll(redistributeMissed(schedule, nowEpochMillis))
            if (analytics.overdueReviewCount > 0) {
                add(protectReviews(plan.id, analytics.overdueReviewCount))
            }
            addAll(reduceOverload(schedule, nowEpochMillis))
            if (analytics.questionAccuracyPercent in 1..59) {
                addAll(addTheoryBeforeQuestions(schedule, analytics.questionAccuracyPercent, nowEpochMillis))
            }
            if (isEmpty()) add(keepSteady(plan.id))
        }
        return AdaptiveReschedulePreview(
            studyPlanId = plan.id,
            generatedAtEpochMillis = nowEpochMillis,
            readinessScorePercent = readinessScorePercent.coerceIn(0, 100),
            missedUnlockedActivities = schedule.plannedActivities.count {
                it.status == PlannedActivityStatus.MISSED && !it.locked
            },
            overdueReviewCount = analytics.overdueReviewCount,
            scheduleAdherencePercent = analytics.scheduleAdherencePercent.coerceIn(0, 100),
            actionSummary = summaryFor(readinessScorePercent, changes),
            changes = changes,
        )
    }

    fun applyPreview(
        activities: List<PlannedActivity>,
        preview: AdaptiveReschedulePreview,
    ): List<PlannedActivity> {
        val byId = activities.associateBy { it.id }
        return preview.changes.mapNotNull { change ->
            val activityId = change.plannedActivityId ?: return@mapNotNull null
            val activity = byId[activityId] ?: return@mapNotNull null
            if (change.locked || !change.executable) return@mapNotNull null
            activity.copy(
                scheduledStartEpochMillis = change.proposedStartEpochMillis ?: activity.scheduledStartEpochMillis,
                plannedDurationMinutes = change.proposedDurationMinutes ?: activity.plannedDurationMinutes,
                status = change.proposedStatus ?: activity.status,
                completedAtEpochMillis = if (change.proposedStatus == PlannedActivityStatus.PLANNED) {
                    null
                } else {
                    activity.completedAtEpochMillis
                },
            )
        }
    }

    private fun redistributeMissed(schedule: StudySchedule, nowEpochMillis: Long): List<AdaptiveRescheduleChange> {
        val missed = schedule.plannedActivities
            .filter { it.status == PlannedActivityStatus.MISSED && !it.locked }
            .sortedWith(
                compareBy<PlannedActivity> { it.scheduledStartEpochMillis ?: Long.MAX_VALUE }
                    .thenBy { it.title },
            )
            .take(5)
        return missed.mapIndexed { index, activity ->
            val proposedStart = nextDayNoon(nowEpochMillis) + index * 2L * 60L * 60L * 1000L
            AdaptiveRescheduleChange(
                id = "redistribute:${activity.id.value}",
                type = AdaptiveRescheduleChangeType.REDISTRIBUTE_MISSED,
                title = activity.title,
                reason = "Missed unlocked block can be safely returned to the plan.",
                plannedActivityId = activity.id,
                currentStartEpochMillis = activity.scheduledStartEpochMillis,
                proposedStartEpochMillis = proposedStart,
                currentDurationMinutes = activity.plannedDurationMinutes,
                proposedDurationMinutes = activity.plannedDurationMinutes,
                currentStatus = activity.status,
                proposedStatus = PlannedActivityStatus.PLANNED,
                locked = false,
                executable = true,
            )
        }
    }

    private fun reduceOverload(schedule: StudySchedule, nowEpochMillis: Long): List<AdaptiveRescheduleChange> {
        val nextWeekEnd = nowEpochMillis + 7L * 86_400_000L
        val nextWeek = schedule.plannedActivities.filter { activity ->
            val start = activity.scheduledStartEpochMillis ?: return@filter false
            !activity.locked &&
                activity.status == PlannedActivityStatus.PLANNED &&
                start in nowEpochMillis..nextWeekEnd
        }
        val minutesByDay = nextWeek.groupBy { dayBucket(it.scheduledStartEpochMillis ?: 0L) }
            .mapValues { (_, activities) -> activities.sumOf { it.plannedDurationMinutes } }
        val overloadedDays = minutesByDay.filterValues { minutes -> minutes > 180 }.keys
        return nextWeek
            .filter { dayBucket(it.scheduledStartEpochMillis ?: 0L) in overloadedDays }
            .filter { it.type != StudyActivityType.REVIEW && it.plannedDurationMinutes >= 40 }
            .take(3)
            .map { activity ->
                AdaptiveRescheduleChange(
                    id = "reduce:${activity.id.value}",
                    type = AdaptiveRescheduleChangeType.REDUCE_OVERLOAD,
                    title = activity.title,
                    reason = "This day is overloaded, so the block is shortened without deleting it.",
                    plannedActivityId = activity.id,
                    currentStartEpochMillis = activity.scheduledStartEpochMillis,
                    proposedStartEpochMillis = activity.scheduledStartEpochMillis,
                    currentDurationMinutes = activity.plannedDurationMinutes,
                    proposedDurationMinutes = (activity.plannedDurationMinutes - 10).coerceAtLeast(25),
                    currentStatus = activity.status,
                    proposedStatus = activity.status,
                    locked = false,
                    executable = true,
                )
            }
    }

    private fun addTheoryBeforeQuestions(
        schedule: StudySchedule,
        accuracyPercent: Int,
        nowEpochMillis: Long,
    ): List<AdaptiveRescheduleChange> = schedule.plannedActivities
        .filter { activity ->
            !activity.locked &&
                activity.status == PlannedActivityStatus.PLANNED &&
                activity.type == StudyActivityType.QUESTIONS &&
                (activity.scheduledStartEpochMillis ?: Long.MAX_VALUE) >= nowEpochMillis
        }
        .sortedBy { it.scheduledStartEpochMillis ?: Long.MAX_VALUE }
        .take(1)
        .map { activity ->
            AdaptiveRescheduleChange(
                id = "theory-before-questions:${activity.id.value}",
                type = AdaptiveRescheduleChangeType.ADD_THEORY_BEFORE_QUESTIONS,
                title = activity.title,
                reason = "Accuracy is $accuracyPercent%, so this block becomes a theory-first review.",
                plannedActivityId = activity.id,
                currentStartEpochMillis = activity.scheduledStartEpochMillis,
                proposedStartEpochMillis = activity.scheduledStartEpochMillis,
                currentDurationMinutes = activity.plannedDurationMinutes,
                proposedDurationMinutes = activity.plannedDurationMinutes,
                currentStatus = activity.status,
                proposedStatus = PlannedActivityStatus.PLANNED,
                locked = false,
                executable = true,
            )
        }

    private fun protectReviews(planId: StudyPlanId, overdueReviewCount: Int): AdaptiveRescheduleChange =
        AdaptiveRescheduleChange(
            id = "protect-reviews:${planId.value}",
            type = AdaptiveRescheduleChangeType.PROTECT_REVIEWS,
            title = "Protect review recovery",
            reason = "$overdueReviewCount overdue review(s) should be cleared before adding new theory.",
            plannedActivityId = null,
            currentStartEpochMillis = null,
            proposedStartEpochMillis = null,
            currentDurationMinutes = null,
            proposedDurationMinutes = null,
            currentStatus = null,
            proposedStatus = null,
            locked = false,
            executable = false,
        )

    private fun keepSteady(planId: StudyPlanId): AdaptiveRescheduleChange = AdaptiveRescheduleChange(
        id = "steady:${planId.value}",
        type = AdaptiveRescheduleChangeType.KEEP_STEADY,
        title = "Keep the current plan",
        reason = "No safe automatic reschedule is needed. Continue with the current mix.",
        plannedActivityId = null,
        currentStartEpochMillis = null,
        proposedStartEpochMillis = null,
        currentDurationMinutes = null,
        proposedDurationMinutes = null,
        currentStatus = null,
        proposedStatus = null,
        locked = false,
        executable = false,
    )

    private fun summaryFor(score: Int, changes: List<AdaptiveRescheduleChange>): String = when {
        changes.any { it.type == AdaptiveRescheduleChangeType.REDISTRIBUTE_MISSED } ->
            "Recover missed work before adding new load."
        changes.any { it.type == AdaptiveRescheduleChangeType.PROTECT_REVIEWS } ->
            "Protect review debt first."
        score < 65 -> "The plan is risky; accept only safe, reversible changes."
        else -> "The plan is stable; no major reshuffle is needed."
    }

    private fun nextDayNoon(epochMillis: Long): Long {
        val day = dayBucket(epochMillis) + 1L
        return day * 86_400_000L + 12L * 60L * 60L * 1000L
    }

    private fun dayBucket(epochMillis: Long): Long = Math.floorDiv(epochMillis, 86_400_000L)

    @Suppress("unused")
    private fun missedHandlingReference(): MissedActivityHandling = MissedActivityHandling.REDISTRIBUTE
}
