package com.mikeyphw.studyflow.feature.schedule

import com.mikeyphw.studyflow.core.model.PlannedActivity
import com.mikeyphw.studyflow.core.model.PlannedActivityId
import com.mikeyphw.studyflow.core.model.PlannedActivityStatus
import com.mikeyphw.studyflow.core.model.StudyActivityType
import com.mikeyphw.studyflow.core.model.StudyPlanId
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleCalendarWindowTest {
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 9, 9)

    @Test
    fun `this week and next week use Monday through Sunday`() {
        val thisWeek = ScheduleCalendarPlanner.window(ScheduleCalendarView.THIS_WEEK, today)
        val nextWeek = ScheduleCalendarPlanner.window(ScheduleCalendarView.NEXT_WEEK, today)

        assertEquals(LocalDate.of(2026, 9, 7), thisWeek.startsOn)
        assertEquals(LocalDate.of(2026, 9, 13), thisWeek.endsOnInclusive)
        assertEquals(LocalDate.of(2026, 9, 14), nextWeek.startsOn)
        assertEquals(LocalDate.of(2026, 9, 20), nextWeek.endsOnInclusive)
    }

    @Test
    fun `bounded views hide unscheduled and out of range activities`() {
        val activities = listOf(
            activity("today", LocalDate.of(2026, 9, 9)),
            activity("next-week", LocalDate.of(2026, 9, 15)),
            activity("unscheduled", null),
        )

        assertEquals(
            listOf("today"),
            ScheduleCalendarPlanner.filter(activities, ScheduleCalendarView.TODAY, today, zone).map { it.id.value },
        )
        assertEquals(
            listOf("next-week"),
            ScheduleCalendarPlanner.filter(activities, ScheduleCalendarView.NEXT_WEEK, today, zone).map { it.id.value },
        )
        assertEquals(3, ScheduleCalendarPlanner.filter(activities, ScheduleCalendarView.CALENDAR, today, zone).size)
    }

    @Test
    fun `summary minutes exactly match visible activities`() {
        val activities = listOf(activity("a", today, 30), activity("b", today, 45))
        assertEquals(75, ScheduleCalendarPlanner.plannedMinutes(activities))
        assertTrue(ScheduleCalendarPlanner.window(ScheduleCalendarView.CALENDAR, today).bounded.not())
    }

    private fun activity(id: String, date: LocalDate?, minutes: Int = 60) = PlannedActivity(
        id = PlannedActivityId(id),
        studyPlanId = StudyPlanId("plan"),
        subjectId = null,
        topicId = null,
        materialId = null,
        videoResourceId = null,
        type = StudyActivityType.THEORY,
        title = id,
        scheduledStartEpochMillis = date?.atStartOfDay(zone)?.toInstant()?.toEpochMilli(),
        plannedDurationMinutes = minutes,
        status = PlannedActivityStatus.PLANNED,
        locked = false,
        completedAtEpochMillis = null,
    )
}
