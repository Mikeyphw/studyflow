package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.MissedActivityHandling
import com.mikeyphw.studyflow.core.model.PlannedActivity
import com.mikeyphw.studyflow.core.model.PlannedActivityId
import com.mikeyphw.studyflow.core.model.PlannedActivityStatus
import com.mikeyphw.studyflow.core.model.ScheduleGenerationRequest
import com.mikeyphw.studyflow.core.model.ScheduleRule
import com.mikeyphw.studyflow.core.model.ScheduleRuleType
import com.mikeyphw.studyflow.core.model.StudyActivityType
import com.mikeyphw.studyflow.core.model.StudyPlanId
import java.util.Calendar
import java.util.TimeZone
import java.util.UUID

class StudySchedulePlanner(
    private val timeZone: TimeZone = TimeZone.getDefault(),
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
) {
    fun generateActivities(
        request: ScheduleGenerationRequest,
        rules: List<ScheduleRule>,
    ): List<PlannedActivity> {
        require(request.days in 1..366) { "Generated schedules must cover between 1 and 366 days." }
        val startDate = startOfLocalDay(request.startEpochMillis)
        val schedulableTypes = setOf(
            ScheduleRuleType.FIXED_BLOCK,
            ScheduleRuleType.RECURRING_BLOCK,
            ScheduleRuleType.CYCLE_SLOT,
        )
        val activeRules = rules
            .filter { it.enabled && it.type in schedulableTypes }
            .filter { it.dayOfWeek != null && it.startMinuteOfDay != null }
            .sortedWith(
                compareBy<ScheduleRule> { it.position }
                    .thenBy { it.dayOfWeek }
                    .thenBy { it.startMinuteOfDay },
            )

        if (activeRules.isEmpty()) return emptyList()

        return (0 until request.days).flatMap { dayOffset ->
            val day = startDate.copyAndAddDays(dayOffset)
            activeRules.filter { it.dayOfWeek == day.studyFlowDayOfWeek }.map { rule ->
                val start = day.copyAndAddMinutes(rule.startMinuteOfDay!!).timeInMillis
                PlannedActivity(
                    id = PlannedActivityId(idFactory()),
                    studyPlanId = request.studyPlanId,
                    subjectId = rule.subjectId,
                    topicId = null,
                    materialId = null,
                    videoResourceId = null,
                    type = activityTypeFor(rule.type),
                    title = titleFor(rule),
                    scheduledStartEpochMillis = start,
                    plannedDurationMinutes = rule.durationMinutes,
                    status = PlannedActivityStatus.PLANNED,
                    locked = rule.locked,
                    completedAtEpochMillis = null,
                )
            }
        }
    }

    fun rebalanceMissedActivities(
        activities: List<PlannedActivity>,
        handling: MissedActivityHandling,
        nowEpochMillis: Long,
    ): List<PlannedActivity> {
        val missed = activities.filter { activity ->
            activity.status == PlannedActivityStatus.MISSED ||
                (activity.status == PlannedActivityStatus.PLANNED &&
                    !activity.locked &&
                    activity.isPast(nowEpochMillis))
        }
        if (missed.isEmpty()) return emptyList()

        return missed.mapIndexedNotNull { index, activity ->
            when (handling) {
                MissedActivityHandling.KEEP_UNCHANGED -> null
                MissedActivityHandling.SKIP -> activity.copy(status = PlannedActivityStatus.SKIPPED)
                MissedActivityHandling.RETURN_TO_BACKLOG -> activity.copy(
                    scheduledStartEpochMillis = null,
                    status = PlannedActivityStatus.PLANNED,
                )
                MissedActivityHandling.POSTPONE,
                MissedActivityHandling.REDISTRIBUTE -> activity.copy(
                    scheduledStartEpochMillis = nowEpochMillis +
                        ((index + 1L) * activity.plannedDurationMinutes.coerceAtLeast(15) * 60_000L),
                    status = PlannedActivityStatus.PLANNED,
                )
            }
        }
    }

    private fun PlannedActivity.isPast(nowEpochMillis: Long): Boolean =
        scheduledStartEpochMillis?.let { it < nowEpochMillis } == true

    private fun startOfLocalDay(epochMillis: Long): Calendar = Calendar.getInstance(timeZone).apply {
        timeInMillis = epochMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    private fun activityTypeFor(ruleType: ScheduleRuleType): StudyActivityType = when (ruleType) {
        ScheduleRuleType.CYCLE_SLOT -> StudyActivityType.CUSTOM
        else -> StudyActivityType.THEORY
    }

    private fun titleFor(rule: ScheduleRule): String = when (rule.type) {
        ScheduleRuleType.CYCLE_SLOT -> "Study cycle slot"
        ScheduleRuleType.FIXED_BLOCK -> "Fixed study block"
        ScheduleRuleType.RECURRING_BLOCK -> "Recurring study block"
        ScheduleRuleType.AVAILABILITY -> "Availability"
        ScheduleRuleType.DAY_OFF -> "Day off"
    }
}

private fun Calendar.copyAndAddDays(days: Int): Calendar = (clone() as Calendar).apply {
    add(Calendar.DAY_OF_YEAR, days)
}

private fun Calendar.copyAndAddMinutes(minutes: Int): Calendar = (clone() as Calendar).apply {
    add(Calendar.MINUTE, minutes)
}

private val Calendar.studyFlowDayOfWeek: Int
    get() = when (get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> 1
        Calendar.TUESDAY -> 2
        Calendar.WEDNESDAY -> 3
        Calendar.THURSDAY -> 4
        Calendar.FRIDAY -> 5
        Calendar.SATURDAY -> 6
        Calendar.SUNDAY -> 7
        else -> error("Unsupported day of week: ${get(Calendar.DAY_OF_WEEK)}")
    }
