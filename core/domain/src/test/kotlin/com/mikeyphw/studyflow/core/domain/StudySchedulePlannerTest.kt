package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.MissedActivityHandling
import com.mikeyphw.studyflow.core.model.PlannedActivityStatus
import com.mikeyphw.studyflow.core.model.ScheduleGenerationRequest
import com.mikeyphw.studyflow.core.model.ScheduleRule
import com.mikeyphw.studyflow.core.model.ScheduleRuleId
import com.mikeyphw.studyflow.core.model.ScheduleRuleType
import com.mikeyphw.studyflow.core.model.StudyPlanId
import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudySchedulePlannerTest {
    private val planId = StudyPlanId("plan")
    private val timeZone = TimeZone.getTimeZone("UTC")
    private val planner = StudySchedulePlanner(timeZone = timeZone, idFactory = object {
        var next = 0
        operator fun invoke() = "activity-${next++}"
    }::invoke)

    @Test
    fun generatesActivitiesFromEnabledWeeklyRules() {
        val monday = utcMillis(2026, Calendar.AUGUST, 3)
        val activities = planner.generateActivities(
            request = ScheduleGenerationRequest(planId, monday, days = 7, replaceUnlocked = true),
            rules = listOf(rule(day = 1, minute = 8 * 60), rule(day = 3, minute = 19 * 60)),
        )

        assertEquals(2, activities.size)
        assertTrue(activities.first().id.value.startsWith("activity-"))
        assertEquals(45, activities.first().plannedDurationMinutes)
        assertTrue(activities.all { it.studyPlanId == planId })
    }

    @Test
    fun skipsDisabledAndNonTimedRules() {
        val monday = utcMillis(2026, Calendar.AUGUST, 3)
        val activities = planner.generateActivities(
            request = ScheduleGenerationRequest(planId, monday, days = 2, replaceUnlocked = true),
            rules = listOf(
                rule(day = 1, minute = 8 * 60, enabled = false),
                rule(day = null, minute = null, type = ScheduleRuleType.DAY_OFF),
            ),
        )

        assertTrue(activities.isEmpty())
    }

    @Test
    fun rebalancesMissedActivitiesWithoutMovingLockedOnes() {
        val generated = planner.generateActivities(
            request = ScheduleGenerationRequest(planId, startEpochMillis = 0L, days = 7, replaceUnlocked = true),
            rules = listOf(rule(day = 1, minute = 0), rule(day = 2, minute = 0, locked = true)),
        )
        val now = generated.maxOf { it.scheduledStartEpochMillis ?: 0L } + 1L
        val updates = planner.rebalanceMissedActivities(generated, MissedActivityHandling.RETURN_TO_BACKLOG, now)

        assertEquals(1, updates.size)
        assertEquals(PlannedActivityStatus.PLANNED, updates.single().status)
        assertEquals(null, updates.single().scheduledStartEpochMillis)
    }

    private fun rule(
        day: Int?,
        minute: Int?,
        type: ScheduleRuleType = ScheduleRuleType.RECURRING_BLOCK,
        enabled: Boolean = true,
        locked: Boolean = false,
    ) = ScheduleRule(
        id = ScheduleRuleId("rule-${day ?: 0}-${minute ?: 0}-${enabled}-${locked}"),
        studyPlanId = planId,
        subjectId = null,
        type = type,
        dayOfWeek = day,
        startMinuteOfDay = minute,
        durationMinutes = 45,
        recurrenceRule = null,
        locked = locked,
        enabled = enabled,
        position = 0,
    )
}

private fun utcMillis(year: Int, zeroBasedMonth: Int, dayOfMonth: Int): Long =
    Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, zeroBasedMonth)
        set(Calendar.DAY_OF_MONTH, dayOfMonth)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
