package com.mikeyphw.studyflow.feature.workspace

import com.mikeyphw.studyflow.core.model.PlannedActivity
import com.mikeyphw.studyflow.core.model.PlannedActivityId
import com.mikeyphw.studyflow.core.model.PlannedActivityStatus
import com.mikeyphw.studyflow.core.model.StudyActivityType
import com.mikeyphw.studyflow.core.model.StudyPlanId
import java.util.Calendar
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TodayWorkspaceTemporalLogicTest {
    private lateinit var previousTimeZone: TimeZone

    @Before
    fun useStableTimeZone() {
        previousTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun restoreTimeZone() {
        TimeZone.setDefault(previousTimeZone)
    }

    @Test
    fun `today activity wins over future activity without future-day metadata`() {
        val now = epoch(2026, Calendar.SEPTEMBER, 6, 8)
        val today = activity("today", epoch(2026, Calendar.SEPTEMBER, 6, 9))
        val tomorrow = activity("tomorrow", epoch(2026, Calendar.SEPTEMBER, 7, 9))

        val selection = selectTodayOrNextScheduledActivity(listOf(tomorrow, today), now)

        assertEquals(today.id, selection?.activity?.id)
        assertFalse(selection?.isFutureDay ?: true)
    }

    @Test
    fun `fallback selects earliest future scheduled activity and ignores stale missed history`() {
        val now = epoch(2026, Calendar.SEPTEMBER, 6, 12)
        val staleMissed = activity(
            id = "stale",
            start = epoch(2026, Calendar.SEPTEMBER, 5, 9),
            status = PlannedActivityStatus.MISSED,
        )
        val later = activity("later", epoch(2026, Calendar.SEPTEMBER, 8, 9))
        val next = activity("next", epoch(2026, Calendar.SEPTEMBER, 7, 9))

        val selection = selectTodayOrNextScheduledActivity(listOf(staleMissed, later, next), now)

        assertEquals(next.id, selection?.activity?.id)
        assertTrue(selection?.isFutureDay == true)
    }

    @Test
    fun `fallback never labels flexible or completed work as next scheduled block`() {
        val now = epoch(2026, Calendar.SEPTEMBER, 6, 12)
        val flexible = activity("flex", null)
        val completedFuture = activity(
            id = "done",
            start = epoch(2026, Calendar.SEPTEMBER, 7, 9),
            status = PlannedActivityStatus.COMPLETED,
        )

        assertNull(selectTodayOrNextScheduledActivity(listOf(flexible, completedFuture), now))
    }

    @Test
    fun `today timeline remains scoped to local day and excludes cancelled activity`() {
        val now = epoch(2026, Calendar.SEPTEMBER, 6, 12)
        val morning = activity("morning", epoch(2026, Calendar.SEPTEMBER, 6, 9))
        val afternoon = activity("afternoon", epoch(2026, Calendar.SEPTEMBER, 6, 14))
        val cancelled = activity(
            id = "cancelled",
            start = epoch(2026, Calendar.SEPTEMBER, 6, 10),
            status = PlannedActivityStatus.CANCELLED,
        )
        val tomorrow = activity("tomorrow", epoch(2026, Calendar.SEPTEMBER, 7, 9))

        assertEquals(
            listOf(morning.id, afternoon.id),
            activitiesForLocalDay(listOf(tomorrow, afternoon, cancelled, morning), now).map { it.id },
        )
    }

    private fun activity(
        id: String,
        start: Long?,
        status: PlannedActivityStatus = PlannedActivityStatus.PLANNED,
    ) = PlannedActivity(
        id = PlannedActivityId(id),
        studyPlanId = StudyPlanId("plan"),
        subjectId = null,
        topicId = null,
        materialId = null,
        videoResourceId = null,
        type = StudyActivityType.THEORY,
        title = id,
        scheduledStartEpochMillis = start,
        plannedDurationMinutes = 90,
        status = status,
        locked = false,
        completedAtEpochMillis = null,
    )

    private fun epoch(year: Int, month: Int, day: Int, hour: Int): Long = Calendar.getInstance().apply {
        clear()
        set(year, month, day, hour, 0, 0)
    }.timeInMillis
}
