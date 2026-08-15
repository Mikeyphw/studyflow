package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.AdaptiveRescheduleChange
import com.mikeyphw.studyflow.core.model.AdaptiveRescheduleChangeType
import com.mikeyphw.studyflow.core.model.AdaptiveReschedulePreview
import com.mikeyphw.studyflow.core.model.ExamReadinessScore
import com.mikeyphw.studyflow.core.model.PlannedActivity
import com.mikeyphw.studyflow.core.model.PlannedActivityStatus
import com.mikeyphw.studyflow.core.model.StudyActivityType
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudySchedule

enum class StudySprintFocus {
    REVIEW_RECOVERY,
    SCHEDULE_RECOVERY,
    PRACTICE_ACCURACY,
    WEAKNESS,
    BALANCED,
}

data class StudySprintPreview(
    val studyPlanId: com.mikeyphw.studyflow.core.model.StudyPlanId,
    val generatedAtEpochMillis: Long,
    val horizonDays: Int,
    val focus: StudySprintFocus,
    val candidateCount: Int,
    val totalMinutes: Int,
    val scheduleVersionToken: String,
    val schedulePreview: AdaptiveReschedulePreview,
) {
    val executableChangeCount: Int = schedulePreview.executableChangeCount
    val executable: Boolean = executableChangeCount > 0
}

object StudySprintPlanner {
    fun preview(
        plan: StudyPlan,
        analytics: StudyAnalyticsSnapshot,
        readiness: ExamReadinessScore,
        schedule: StudySchedule,
        nowEpochMillis: Long,
    ): StudySprintPreview {
        require(analytics.studyPlanId == plan.id) { "Sprint analytics belong to another Study Plan." }
        require(readiness.studyPlanId == plan.id) { "Sprint readiness belongs to another Study Plan." }
        require(schedule.studyPlanId == plan.id) { "Sprint schedule belongs to another Study Plan." }
        require(schedule.strategy == null || schedule.strategy.studyPlanId == plan.id) {
            "Sprint strategy evidence belongs to another Study Plan."
        }
        require(schedule.scheduleRules.all { rule -> rule.studyPlanId == plan.id }) {
            "Sprint schedule-rule evidence belongs to another Study Plan."
        }
        require(schedule.plannedActivities.all { activity -> activity.studyPlanId == plan.id }) {
            "Sprint activity evidence belongs to another Study Plan."
        }
        val horizonEnd = nowEpochMillis + HorizonDays * MillisPerDay
        val candidates = schedule.plannedActivities
            .asSequence()
            .filter { activity -> isSprintCandidate(activity, nowEpochMillis, horizonEnd) }
            .sortedWith(compareBy<PlannedActivity> { it.scheduledStartEpochMillis }.thenBy { it.id.value })
            .take(MaxCandidates)
            .toList()
        val focus = chooseFocus(analytics)
        val changes = buildChanges(candidates, focus, analytics)
        val versionToken = AdaptiveReschedulePlanner.scheduleVersionToken(schedule)
        val adaptivePreview = AdaptiveReschedulePreview(
            studyPlanId = plan.id,
            generatedAtEpochMillis = nowEpochMillis,
            readinessScorePercent = readiness.scorePercent,
            missedUnlockedActivities = schedule.plannedActivities.count { activity ->
                activity.status == PlannedActivityStatus.MISSED && !activity.locked
            },
            overdueReviewCount = analytics.overdueReviewCount,
            scheduleAdherencePercent = analytics.scheduleAdherencePercent.coerceIn(0, 100),
            actionSummary = "Study sprint preview",
            changes = changes.ifEmpty { listOf(keepSteady(plan)) },
            scheduleVersionToken = versionToken,
        )
        return StudySprintPreview(
            studyPlanId = plan.id,
            generatedAtEpochMillis = nowEpochMillis,
            horizonDays = HorizonDays,
            focus = focus,
            candidateCount = candidates.size,
            totalMinutes = candidates.sumOf(PlannedActivity::plannedDurationMinutes),
            scheduleVersionToken = versionToken,
            schedulePreview = adaptivePreview,
        )
    }

    internal fun requireSafeApply(
        preview: StudySprintPreview,
        schedule: StudySchedule,
        nowEpochMillis: Long,
    ) {
        require(preview.horizonDays == HorizonDays) { "Study sprint horizon is not seven days." }
        require(preview.candidateCount in 0..MaxCandidates) { "Study sprint candidate count is invalid." }
        require(schedule.studyPlanId == preview.studyPlanId) {
            "Study sprint current schedule belongs to another Study Plan."
        }
        require(schedule.strategy == null || schedule.strategy.studyPlanId == preview.studyPlanId) {
            "Study sprint current strategy belongs to another Study Plan."
        }
        require(schedule.scheduleRules.all { rule -> rule.studyPlanId == preview.studyPlanId }) {
            "Study sprint current schedule rules belong to another Study Plan."
        }
        require(schedule.plannedActivities.all { activity -> activity.studyPlanId == preview.studyPlanId }) {
            "Study sprint current activities belong to another Study Plan."
        }
        require(preview.schedulePreview.studyPlanId == preview.studyPlanId) {
            "Study sprint schedule preview belongs to another Study Plan."
        }
        require(preview.schedulePreview.scheduleVersionToken == preview.scheduleVersionToken) {
            "Study sprint schedule version does not match the approved preview."
        }
        require(preview.schedulePreview.generatedAtEpochMillis == preview.generatedAtEpochMillis) {
            "Study sprint generation timestamp does not match the approved preview."
        }
        require(nowEpochMillis >= preview.generatedAtEpochMillis) {
            "Study sprint preview was generated in the future. Refresh before applying."
        }
        require(nowEpochMillis - preview.generatedAtEpochMillis <= PreviewFreshnessMillis) {
            "Study sprint preview is stale. Refresh before applying."
        }
        require(AdaptiveReschedulePlanner.scheduleVersionToken(schedule) == preview.scheduleVersionToken) {
            "Study sprint schedule changed after preview. Refresh before applying."
        }

        val horizonEnd = preview.generatedAtEpochMillis + HorizonDays * MillisPerDay
        val candidates = schedule.plannedActivities
            .asSequence()
            .filter { activity -> isSprintCandidate(activity, preview.generatedAtEpochMillis, horizonEnd) }
            .sortedWith(compareBy<PlannedActivity> { it.scheduledStartEpochMillis }.thenBy { it.id.value })
            .take(MaxCandidates)
            .toList()
        require(preview.candidateCount == candidates.size) {
            "Study sprint candidate set no longer matches the approved preview."
        }
        require(preview.totalMinutes == candidates.sumOf(PlannedActivity::plannedDurationMinutes)) {
            "Study sprint candidate minutes no longer match the approved preview."
        }
        val candidateIds = candidates.mapTo(mutableSetOf()) { activity -> activity.id }
        val executable = preview.schedulePreview.changes.filter { change -> change.executable }
        require(executable.size <= preview.candidateCount) {
            "Study sprint cannot mutate more activities than were previewed."
        }
        val targetedIds = mutableSetOf<com.mikeyphw.studyflow.core.model.PlannedActivityId>()
        executable.forEach { change ->
            require(!change.locked) { "Study sprint cannot mutate a locked activity." }
            val activityId = requireNotNull(change.plannedActivityId) {
                "Executable study sprint changes must target an existing activity."
            }
            require(activityId in candidateIds) {
                "Study sprint cannot mutate an activity outside the approved seven-day candidate set."
            }
            require(targetedIds.add(activityId)) { "Study sprint cannot target an activity more than once." }
            require(
                change.currentStatus == PlannedActivityStatus.PLANNED &&
                    change.proposedStatus == PlannedActivityStatus.PLANNED,
            ) { "Study sprint can only keep planned activities in the planned state." }
            require(
                change.currentStartEpochMillis != null &&
                    change.proposedStartEpochMillis == change.currentStartEpochMillis,
            ) { "Study sprint cannot move an activity to another time." }
            val currentDuration = requireNotNull(change.currentDurationMinutes) {
                "Study sprint changes must include the current duration."
            }
            val proposedDuration = requireNotNull(change.proposedDurationMinutes) {
                "Study sprint changes must include the proposed duration."
            }
            require(proposedDuration in 1..currentDuration) {
                "Study sprint can only preserve or shorten activity duration."
            }
            val currentType = requireNotNull(change.currentType) {
                "Study sprint changes must include the current activity type."
            }
            val proposedType = requireNotNull(change.proposedType) {
                "Study sprint changes must include the proposed activity type."
            }
            require(proposedDuration < currentDuration || proposedType != currentType) {
                "Executable study sprint changes cannot be no-ops."
            }
        }
    }

    private fun buildChanges(
        candidates: List<PlannedActivity>,
        focus: StudySprintFocus,
        analytics: StudyAnalyticsSnapshot,
    ): List<AdaptiveRescheduleChange> = candidates.mapIndexedNotNull { index, activity ->
        val targetType = targetType(focus, index, activity.type)
        val targetDuration = targetDuration(focus, activity.plannedDurationMinutes)
        if (targetType == activity.type && targetDuration == activity.plannedDurationMinutes) {
            return@mapIndexedNotNull null
        }
        AdaptiveRescheduleChange(
            id = "sprint:${activity.id.value}",
            type = changeType(focus),
            title = activity.title,
            reason = sprintReason(focus, analytics),
            plannedActivityId = activity.id,
            currentStartEpochMillis = activity.scheduledStartEpochMillis,
            proposedStartEpochMillis = activity.scheduledStartEpochMillis,
            currentDurationMinutes = activity.plannedDurationMinutes,
            proposedDurationMinutes = targetDuration,
            currentStatus = activity.status,
            proposedStatus = activity.status,
            locked = false,
            executable = true,
            currentType = activity.type,
            proposedType = targetType,
        )
    }

    private fun isSprintCandidate(activity: PlannedActivity, start: Long, end: Long): Boolean {
        val scheduledStart = activity.scheduledStartEpochMillis ?: return false
        return !activity.locked &&
            activity.status == PlannedActivityStatus.PLANNED &&
            scheduledStart in start..end
    }

    private fun chooseFocus(analytics: StudyAnalyticsSnapshot): StudySprintFocus = when {
        analytics.overdueReviewCount > 0 -> StudySprintFocus.REVIEW_RECOVERY
        analytics.scheduleAdherencePercent < 60 -> StudySprintFocus.SCHEDULE_RECOVERY
        analytics.questionAccuracyPercent in 1..59 -> StudySprintFocus.PRACTICE_ACCURACY
        analytics.weakAreas.isNotEmpty() -> StudySprintFocus.WEAKNESS
        else -> StudySprintFocus.BALANCED
    }

    private fun targetType(focus: StudySprintFocus, index: Int, current: StudyActivityType): StudyActivityType =
        when (focus) {
            StudySprintFocus.REVIEW_RECOVERY -> if (index % 2 == 0) StudyActivityType.REVIEW else current
            StudySprintFocus.PRACTICE_ACCURACY -> if (index % 2 == 0) StudyActivityType.THEORY else {
                StudyActivityType.QUESTIONS
            }
            StudySprintFocus.WEAKNESS -> if (index % 2 == 0) StudyActivityType.QUESTIONS else StudyActivityType.REVIEW
            StudySprintFocus.SCHEDULE_RECOVERY,
            StudySprintFocus.BALANCED -> current
        }

    private fun targetDuration(focus: StudySprintFocus, currentMinutes: Int): Int = when (focus) {
        StudySprintFocus.SCHEDULE_RECOVERY -> currentMinutes.coerceAtMost(RecoveryBlockMinutes)
        StudySprintFocus.REVIEW_RECOVERY,
        StudySprintFocus.PRACTICE_ACCURACY,
        StudySprintFocus.WEAKNESS -> currentMinutes.coerceAtMost(FocusBlockMinutes)
        StudySprintFocus.BALANCED -> currentMinutes
    }

    private fun changeType(focus: StudySprintFocus): AdaptiveRescheduleChangeType = when (focus) {
        StudySprintFocus.REVIEW_RECOVERY -> AdaptiveRescheduleChangeType.PROTECT_REVIEWS
        StudySprintFocus.SCHEDULE_RECOVERY -> AdaptiveRescheduleChangeType.REDUCE_OVERLOAD
        StudySprintFocus.PRACTICE_ACCURACY -> AdaptiveRescheduleChangeType.ADD_THEORY_BEFORE_QUESTIONS
        StudySprintFocus.WEAKNESS -> AdaptiveRescheduleChangeType.ADD_THEORY_BEFORE_QUESTIONS
        StudySprintFocus.BALANCED -> AdaptiveRescheduleChangeType.KEEP_STEADY
    }

    private fun sprintReason(focus: StudySprintFocus, analytics: StudyAnalyticsSnapshot): String = when (focus) {
        StudySprintFocus.REVIEW_RECOVERY -> "Prioritize ${analytics.overdueReviewCount} overdue review(s)."
        StudySprintFocus.SCHEDULE_RECOVERY -> "Shorten blocks while schedule adherence recovers."
        StudySprintFocus.PRACTICE_ACCURACY -> "Alternate concept repair and questions while accuracy recovers."
        StudySprintFocus.WEAKNESS -> "Bias the week toward targeted questions and review."
        StudySprintFocus.BALANCED -> "Keep the current seven-day mix."
    }

    private fun keepSteady(plan: StudyPlan): AdaptiveRescheduleChange =
        AdaptiveRescheduleChange(
            id = "sprint-steady:${plan.id.value}",
            type = AdaptiveRescheduleChangeType.KEEP_STEADY,
            title = "Keep current seven-day plan",
            reason = "No safe sprint mutation is needed for the current schedule.",
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

    private const val HorizonDays = 7
    private const val MaxCandidates = 7
    private const val FocusBlockMinutes = 60
    private const val RecoveryBlockMinutes = 45
    private const val MillisPerDay = 86_400_000L
    private const val PreviewFreshnessMillis = 120_000L
}
