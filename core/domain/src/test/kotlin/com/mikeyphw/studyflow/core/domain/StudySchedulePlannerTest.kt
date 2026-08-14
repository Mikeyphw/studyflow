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
        assertTrue(activities.first().id.value.startsWith("generated:"))
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
                rule(day = 1, minute = null, type = ScheduleRuleType.DAY_OFF),
            ),
        )

        assertTrue(activities.isEmpty())
    }

    @Test
    fun generationIdsAreDeterministicForTheSameRuleOccurrence() {
        val monday = utcMillis(2026, Calendar.AUGUST, 3)
        val request = ScheduleGenerationRequest(planId, monday, days = 7, replaceUnlocked = false)
        val rules = listOf(rule(day = 1, minute = 8 * 60))

        val first = planner.generateActivities(request, rules)
        val second = planner.generateActivities(request, rules)

        assertEquals(first.map { it.id }, second.map { it.id })
    }

    @Test
    fun dayOffSuppressesTimedRulesOnThatLocalDay() {
        val monday = utcMillis(2026, Calendar.AUGUST, 3)
        val activities = planner.generateActivities(
            request = ScheduleGenerationRequest(planId, monday, days = 1, replaceUnlocked = false),
            rules = listOf(
                rule(day = 1, minute = 8 * 60),
                rule(day = 1, minute = null, type = ScheduleRuleType.DAY_OFF),
            ),
        )

        assertTrue(activities.isEmpty())
    }


    @Test
    fun acceptsExistingOnboardingWeeklyRruleSyntax() {
        val monday = utcMillis(2026, Calendar.AUGUST, 3)
        val activities = planner.generateActivities(
            request = ScheduleGenerationRequest(planId, monday, days = 15, replaceUnlocked = false),
            rules = listOf(rule(day = 1, minute = 8 * 60, recurrence = "FREQ=WEEKLY")),
        )

        assertEquals(3, activities.size)
    }

    @Test
    fun supportsWeeklyRruleIntervalWithoutChangingLocalWeekSemantics() {
        val monday = utcMillis(2026, Calendar.AUGUST, 3)
        val activities = planner.generateActivities(
            request = ScheduleGenerationRequest(planId, monday, days = 22, replaceUnlocked = false),
            rules = listOf(rule(day = 1, minute = 8 * 60, recurrence = "FREQ=WEEKLY;INTERVAL=2")),
        )

        assertEquals(2, activities.size)
    }

    @Test
    fun rebalancesMissedActivitiesWithoutMovingLockedOnes() {
        val generated = planner.generateActivities(
            request = ScheduleGenerationRequest(planId, startEpochMillis = 0L, days = 7, replaceUnlocked = true),
            rules = listOf(rule(day = 1, minute = 0), rule(day = 2, minute = 0, locked = true)),
        )
        val now = generated.maxOf {
            (it.scheduledStartEpochMillis ?: 0L) + it.plannedDurationMinutes * 60_000L
        } + 5L * 60_000L
        val updates = planner.rebalanceMissedActivities(generated, MissedActivityHandling.RETURN_TO_BACKLOG, now)

        assertEquals(1, updates.size)
        assertEquals(PlannedActivityStatus.PLANNED, updates.single().status)
        assertEquals(null, updates.single().scheduledStartEpochMillis)
    }


    @Test
    fun rebalanceMutationRetainsExpectedSnapshotForAtomicCas() {
        val generated = planner.generateActivities(
            request = ScheduleGenerationRequest(planId, startEpochMillis = 0L, days = 7, replaceUnlocked = false),
            rules = listOf(rule(day = 1, minute = 0)),
        )
        val missed = generated.single().copy(status = PlannedActivityStatus.MISSED)
        val mutations = planner.rebalanceMissedMutations(
            activities = listOf(missed),
            handling = MissedActivityHandling.SKIP,
            nowEpochMillis = (missed.scheduledStartEpochMillis ?: 0L) + 10_000_000L,
        )

        assertEquals(PlannedActivityStatus.MISSED, mutations.single().expected.status)
        assertEquals(PlannedActivityStatus.SKIPPED, mutations.single().proposed.status)
        assertEquals(mutations.single().expected.id, mutations.single().proposed.id)
    }

    private fun rule(
        day: Int?,
        minute: Int?,
        type: ScheduleRuleType = ScheduleRuleType.RECURRING_BLOCK,
        enabled: Boolean = true,
        locked: Boolean = false,
        recurrence: String? = null,
    ) = ScheduleRule(
        id = ScheduleRuleId("rule-${day ?: 0}-${minute ?: 0}-${enabled}-${locked}"),
        studyPlanId = planId,
        subjectId = null,
        type = type,
        dayOfWeek = day,
        startMinuteOfDay = minute,
        durationMinutes = 45,
        recurrenceRule = recurrence,
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
