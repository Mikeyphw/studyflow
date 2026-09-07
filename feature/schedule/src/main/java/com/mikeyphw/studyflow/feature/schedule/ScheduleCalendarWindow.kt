package com.mikeyphw.studyflow.feature.schedule

import com.mikeyphw.studyflow.core.model.PlannedActivity
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

enum class ScheduleCalendarView {
    TODAY,
    THIS_WEEK,
    NEXT_WEEK,
    CALENDAR,
}

data class ScheduleCalendarWindow(
    val startsOn: LocalDate?,
    val endsOnInclusive: LocalDate?,
) {
    init {
        require(startsOn == null || endsOnInclusive == null || !endsOnInclusive.isBefore(startsOn)) {
            "Schedule calendar window end cannot precede its start."
        }
    }

    val bounded: Boolean get() = startsOn != null && endsOnInclusive != null
}

object ScheduleCalendarPlanner {
    fun window(view: ScheduleCalendarView, today: LocalDate): ScheduleCalendarWindow = when (view) {
        ScheduleCalendarView.TODAY -> ScheduleCalendarWindow(today, today)
        ScheduleCalendarView.THIS_WEEK -> {
            val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            ScheduleCalendarWindow(monday, monday.plusDays(6))
        }
        ScheduleCalendarView.NEXT_WEEK -> {
            val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(1)
            ScheduleCalendarWindow(monday, monday.plusDays(6))
        }
        ScheduleCalendarView.CALENDAR -> ScheduleCalendarWindow(null, null)
    }

    fun filter(
        activities: List<PlannedActivity>,
        view: ScheduleCalendarView,
        today: LocalDate,
        zoneId: ZoneId,
    ): List<PlannedActivity> {
        val window = window(view, today)
        if (!window.bounded) return activities
        val start = requireNotNull(window.startsOn)
        val end = requireNotNull(window.endsOnInclusive)
        return activities.filter { activity ->
            activity.scheduledStartEpochMillis?.let { epoch ->
                val date = Instant.ofEpochMilli(epoch).atZone(zoneId).toLocalDate()
                !date.isBefore(start) && !date.isAfter(end)
            } == true
        }
    }

    fun plannedMinutes(activities: List<PlannedActivity>): Int =
        activities.sumOf(PlannedActivity::plannedDurationMinutes)
}
