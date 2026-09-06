package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.AdaptiveRescheduleApplyResult
import com.mikeyphw.studyflow.core.model.AdaptiveRescheduleChange
import com.mikeyphw.studyflow.core.model.AdaptiveRescheduleChangeType
import com.mikeyphw.studyflow.core.model.AdaptiveReschedulePreview
import com.mikeyphw.studyflow.core.model.PlannedActivity
import com.mikeyphw.studyflow.core.model.PlannedActivityStatus
import com.mikeyphw.studyflow.core.model.StudyActivityType
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudySchedule
import java.security.MessageDigest
import java.util.Calendar
import java.util.TimeZone
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.isActive

class ManageAdaptiveReschedule(
    private val analyticsRepository: StudyAnalyticsRepository,
    private val scheduleRepository: StudyScheduleRepository,
    private val readinessManager: ManageExamReadiness,
    private val studyPlanRepository: StudyPlanRepository? = null,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private data class Inputs(
        val analytics: StudyAnalyticsSnapshot,
        val schedule: StudySchedule,
        val readiness: com.mikeyphw.studyflow.core.model.ExamReadinessScore,
        val settings: com.mikeyphw.studyflow.core.model.StudyPlanSettings?,
    )

    fun observePreview(plan: StudyPlan): Flow<AdaptiveReschedulePreview> {
        val inputs = combine(
            analyticsRepository.observeSnapshot(plan.id),
            scheduleRepository.observeSchedule(plan.id),
            readinessManager.observeReadiness(plan),
            studyPlanRepository?.observeStoredPlanSettings(plan.id) ?: flowOf(null),
        ) { analytics, schedule, readiness, settings ->
            Inputs(analytics, schedule, readiness, settings)
        }
        return combine(inputs, minuteTicker()) { value, now ->
            val persistedSettings = if (studyPlanRepository == null) null else requireNotNull(value.settings) {
                "Study Plan settings must be persisted before adaptive rescheduling."
            }
            if (persistedSettings?.adaptivePlanningEnabled == false) {
                AdaptiveReschedulePlanner.disabledPreview(
                    plan = plan,
                    analytics = value.analytics,
                    schedule = value.schedule,
                    readinessScorePercent = value.readiness.scorePercent,
                    nowEpochMillis = now,
                )
            } else {
                AdaptiveReschedulePlanner.preview(
                    plan = plan,
                    analytics = value.analytics,
                    schedule = value.schedule,
                    readinessScorePercent = value.readiness.scorePercent,
                    nowEpochMillis = now,
                )
            }
        }
    }

    suspend fun applySafePreview(
        plan: StudyPlan,
        approvedPreview: AdaptiveReschedulePreview,
    ): AdaptiveRescheduleApplyResult {
        require(approvedPreview.studyPlanId == plan.id) { "Adaptive preview belongs to another Study Plan." }
        val settings = studyPlanRepository?.let { plans ->
            requireNotNull(plans.observeStoredPlanSettings(plan.id).first()) {
                "Study Plan settings must be persisted before adaptive rescheduling."
            }
        }
        require(settings?.adaptivePlanningEnabled != false) {
            "Adaptive rescheduling is disabled in this Study Plan's settings."
        }
        require(approvedPreview.scheduleVersionToken.isNotBlank()) {
            "Adaptive preview is missing its schedule version. Refresh before applying."
        }
        val applied = scheduleRepository.applyAdaptivePreview(plan.id, approvedPreview)
        return AdaptiveRescheduleApplyResult(
            studyPlanId = plan.id,
            requestedChanges = approvedPreview.executableChangeCount,
            appliedChanges = applied,
            skippedChanges = (approvedPreview.executableChangeCount - applied).coerceAtLeast(0),
        )
    }

    private fun minuteTicker(): Flow<Long> = flow {
        while (currentCoroutineContext().isActive) {
            emit(clock())
            delay(60_000L)
        }
    }
}

object AdaptiveReschedulePlanner {
    fun disabledPreview(
        plan: StudyPlan,
        analytics: StudyAnalyticsSnapshot,
        schedule: StudySchedule,
        readinessScorePercent: Int?,
        nowEpochMillis: Long,
    ): AdaptiveReschedulePreview = AdaptiveReschedulePreview(
        studyPlanId = plan.id,
        generatedAtEpochMillis = nowEpochMillis,
        readinessScorePercent = readinessScorePercent?.coerceIn(0, 100),
        missedUnlockedActivities = schedule.plannedActivities.count {
            it.status == PlannedActivityStatus.MISSED && !it.locked
        },
        overdueReviewCount = analytics.overdueReviewCount,
        scheduleAdherencePercent = analytics.scheduleAdherencePercent.coerceIn(0, 100),
        actionSummary = "Adaptive planning is disabled for this Study Plan.",
        changes = listOf(keepSteady(plan.id)),
        scheduleVersionToken = scheduleVersionToken(schedule),
    )

    fun preview(
        plan: StudyPlan,
        analytics: StudyAnalyticsSnapshot,
        schedule: StudySchedule,
        readinessScorePercent: Int?,
        nowEpochMillis: Long,
    ): AdaptiveReschedulePreview {
        val proposed = buildList {
            addAll(redistributeMissed(schedule, nowEpochMillis))
            if (analytics.overdueReviewCount > 0) add(protectReviews(plan.id, analytics.overdueReviewCount))
            addAll(reduceOverload(schedule, nowEpochMillis))
            if (analytics.questionAccuracyPercent in 1..59) {
                addAll(addTheoryBeforeQuestions(schedule, analytics.questionAccuracyPercent, nowEpochMillis))
            }
        }
        val changes = proposed.filterNot(::isExecutableNoOp).ifEmpty { listOf(keepSteady(plan.id)) }
        return AdaptiveReschedulePreview(
            studyPlanId = plan.id,
            generatedAtEpochMillis = nowEpochMillis,
            readinessScorePercent = readinessScorePercent?.coerceIn(0, 100),
            missedUnlockedActivities = schedule.plannedActivities.count {
                it.status == PlannedActivityStatus.MISSED && !it.locked
            },
            overdueReviewCount = analytics.overdueReviewCount,
            scheduleAdherencePercent = analytics.scheduleAdherencePercent.coerceIn(0, 100),
            actionSummary = summaryFor(readinessScorePercent, changes),
            changes = changes,
            scheduleVersionToken = scheduleVersionToken(schedule),
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
                type = change.proposedType ?: activity.type,
                scheduledStartEpochMillis = change.proposedStartEpochMillis ?: activity.scheduledStartEpochMillis,
                plannedDurationMinutes = change.proposedDurationMinutes ?: activity.plannedDurationMinutes,
                status = change.proposedStatus ?: activity.status,
                completedAtEpochMillis = if (change.proposedStatus == PlannedActivityStatus
                    .PLANNED) null else activity.completedAtEpochMillis,
            )
        }
    }

    fun scheduleVersionToken(schedule: StudySchedule): String {
        val strategy = schedule.strategy?.let {
            listOf(it.id.value, it.type.name, it.minimumSessionMinutes, it.maximumSessionMinutes,
                it.theoryRatioPercent, it.questionRatioPercent, it.reviewRatioPercent,
                it.adaptivePlanningEnabled, it.configurationJson).joinToString("|")
        }.orEmpty()
        val rules = schedule.scheduleRules.sortedBy { it.id.value }.joinToString(";") {
            listOf(it.id.value, it.subjectId?.value, it.type.name, it.dayOfWeek, it.startMinuteOfDay,
                it.durationMinutes, it.recurrenceRule, it.locked, it.enabled, it.position).joinToString("|")
        }
        val activities = schedule.plannedActivities.sortedBy { it.id.value }.joinToString(";") {
            listOf(it.id.value, it.subjectId?.value, it.topicId?.value, it.materialId?.value,
                it.videoResourceId?.value, it.type.name, it.title, it.scheduledStartEpochMillis,
                it.plannedDurationMinutes, it.status.name, it.locked, it.completedAtEpochMillis).joinToString("|")
        }
        return sha256("${schedule.studyPlanId.value}#$strategy#$rules#$activities")
    }

    private fun redistributeMissed(schedule: StudySchedule, nowEpochMillis: Long): List<AdaptiveRescheduleChange> {
        val planner = StudySchedulePlanner()
        val accepted = mutableListOf<PlannedActivity>()
        return schedule.plannedActivities
            .filter { it.status == PlannedActivityStatus.MISSED && !it.locked }
            .sortedBy { it.scheduledStartEpochMillis ?: Long.MAX_VALUE }
            .take(5)
            .mapNotNull { activity ->
                val proposed = findSafeRecovery(activity, schedule, accepted, planner, nowEpochMillis)
                    ?: return@mapNotNull null
                accepted += proposed
                AdaptiveRescheduleChange(
                    id = "redistribute:${activity.id.value}",
                    type = AdaptiveRescheduleChangeType.REDISTRIBUTE_MISSED,
                    title = activity.title,
                    reason = "Missed unlocked block can be safely returned to the plan.",
                    plannedActivityId = activity.id,
                    currentStartEpochMillis = activity.scheduledStartEpochMillis,
                    proposedStartEpochMillis = proposed.scheduledStartEpochMillis,
                    currentDurationMinutes = activity.plannedDurationMinutes,
                    proposedDurationMinutes = activity.plannedDurationMinutes,
                    currentStatus = activity.status,
                    proposedStatus = PlannedActivityStatus.PLANNED,
                    locked = false,
                    executable = true,
                    currentType = activity.type,
                    proposedType = activity.type,
                )
            }
    }

    private fun findSafeRecovery(
        activity: PlannedActivity,
        schedule: StudySchedule,
        accepted: List<PlannedActivity>,
        planner: StudySchedulePlanner,
        nowEpochMillis: Long,
    ): PlannedActivity? {
        val zone = TimeZone.getDefault()
        val start = Calendar.getInstance(zone).apply {
            timeInMillis = nowEpochMillis
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        repeat(14 * 48) { step ->
            val candidateStart = start.timeInMillis + step * 30L * 60L * 1000L
            val candidate = activity.copy(
                scheduledStartEpochMillis = candidateStart,
                status = PlannedActivityStatus.PLANNED,
                completedAtEpochMillis = null,
            )
            val all = schedule.plannedActivities.filterNot { it.id == activity.id } + accepted + candidate
            if (runCatching { planner.validateCandidate(candidate, all, schedule.scheduleRules) }
                .isSuccess) return candidate
        }
        return null
    }

    private fun reduceOverload(schedule: StudySchedule, nowEpochMillis: Long): List<AdaptiveRescheduleChange> {
        val nextWeekEnd = nowEpochMillis + 7L * 86_400_000L
        val nextWeek = schedule.plannedActivities.filter { activity ->
            val start = activity.scheduledStartEpochMillis ?: return@filter false
            !activity.locked && activity.status == PlannedActivityStatus.PLANNED && start in nowEpochMillis..nextWeekEnd
        }
        val minutesByDay = nextWeek.groupBy { localDayKey(requireNotNull(it.scheduledStartEpochMillis)) }
            .mapValues { (_, activities) -> activities.sumOf { it.plannedDurationMinutes } }
        val overloadedDays = minutesByDay.filterValues { it > 180 }.keys
        return nextWeek
            .filter { localDayKey(requireNotNull(it.scheduledStartEpochMillis)) in overloadedDays }
            .filter { it.type !in setOf(StudyActivityType.REVIEW, StudyActivityType.QUESTIONS) && it
                .plannedDurationMinutes >= 40 }
            .take(3)
            .map { activity ->
                AdaptiveRescheduleChange(
                    id = "reduce:${activity.id.value}",
                    type = AdaptiveRescheduleChangeType.REDUCE_OVERLOAD,
                    title = activity.title,
                    reason = "This local calendar day is overloaded, so the block is shortened without deleting it.",
                    plannedActivityId = activity.id,
                    currentStartEpochMillis = activity.scheduledStartEpochMillis,
                    proposedStartEpochMillis = activity.scheduledStartEpochMillis,
                    currentDurationMinutes = activity.plannedDurationMinutes,
                    proposedDurationMinutes = (activity.plannedDurationMinutes - 10).coerceAtLeast(25),
                    currentStatus = activity.status,
                    proposedStatus = activity.status,
                    locked = false,
                    executable = true,
                    currentType = activity.type,
                    proposedType = activity.type,
                )
            }
    }

    private fun addTheoryBeforeQuestions(
        schedule: StudySchedule,
        accuracyPercent: Int,
        nowEpochMillis: Long,
    ): List<AdaptiveRescheduleChange> = schedule.plannedActivities
        .filter { !it.locked && it.status == PlannedActivityStatus.PLANNED &&
            it.type == StudyActivityType.QUESTIONS && (it.scheduledStartEpochMillis ?: Long
                .MAX_VALUE) >= nowEpochMillis }
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
                currentType = activity.type,
                proposedType = StudyActivityType.THEORY,
            )
        }

    private fun protectReviews(planId: StudyPlanId, overdueReviewCount: Int) = AdaptiveRescheduleChange(
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

    private fun keepSteady(planId: StudyPlanId) = AdaptiveRescheduleChange(
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

    private fun isExecutableNoOp(change: AdaptiveRescheduleChange): Boolean = change.executable &&
        change.currentStartEpochMillis == change.proposedStartEpochMillis &&
        change.currentDurationMinutes == change.proposedDurationMinutes &&
        change.currentStatus == change.proposedStatus &&
        (change.currentType == null || change.currentType == change.proposedType)

    private fun summaryFor(score: Int?, changes: List<AdaptiveRescheduleChange>): String = when {
        changes.any { it.type == AdaptiveRescheduleChangeType.REDISTRIBUTE_MISSED } ->
            "Recover missed work before adding new load."
        changes.any { it.type == AdaptiveRescheduleChangeType.PROTECT_REVIEWS } -> "Protect review debt first."
        score == null -> "Readiness needs more evidence; keep changes evidence-driven and reversible."
        score < 65 -> "The plan is risky; accept only safe, reversible changes."
        else -> "The plan is stable; no major reshuffle is needed."
    }

    private fun localDayKey(epochMillis: Long): String = Calendar.getInstance(TimeZone.getDefault()).run {
        timeInMillis = epochMillis
        "%04d-%03d".format(get(Calendar.YEAR), get(Calendar.DAY_OF_YEAR))
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.encodeToByteArray())
        .joinToString("") { "%02x".format(it) }
}
