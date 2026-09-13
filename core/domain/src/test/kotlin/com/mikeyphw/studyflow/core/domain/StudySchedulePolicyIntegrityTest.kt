package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.MissedActivityHandling
import com.mikeyphw.studyflow.core.model.PlannedActivity
import com.mikeyphw.studyflow.core.model.PlannedActivityId
import com.mikeyphw.studyflow.core.model.PlannedActivityStatus
import com.mikeyphw.studyflow.core.model.ScheduleGenerationRequest
import com.mikeyphw.studyflow.core.model.ScheduleRule
import com.mikeyphw.studyflow.core.model.ScheduleRuleId
import com.mikeyphw.studyflow.core.model.ScheduleRuleType
import com.mikeyphw.studyflow.core.model.StudyActivityType
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyStrategy
import com.mikeyphw.studyflow.core.model.StudyStrategyId
import com.mikeyphw.studyflow.core.model.StudyStrategyType
import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class StudySchedulePolicyIntegrityTest {
    private val planId = StudyPlanId("plan")
    private val foreignPlanId = StudyPlanId("foreign")
    private val timeZone = TimeZone.getTimeZone("UTC")
    private val planner = StudySchedulePlanner(timeZone)

    @Test
    fun generationRejectsForeignRuleAndStrategyOwnership() {
        val monday = utcMillis(2026, Calendar.AUGUST, 3)
        val request = ScheduleGenerationRequest(planId, monday, days = 7, replaceUnlocked = false)
        assertThrows(IllegalArgumentException::class.java) {
            planner.generateActivities(
                request,
                listOf(rule("foreign-rule", foreignPlanId, 1, 9 * 60)),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            planner.generateActivities(
                request,
                listOf(rule("local-rule", planId, 1, 9 * 60)),
                strategy = StudyStrategy(
                    id = StudyStrategyId("foreign-strategy"),
                    studyPlanId = foreignPlanId,
                    type = StudyStrategyType.ADAPTIVE,
                    minimumSessionMinutes = 25,
                    maximumSessionMinutes = 90,
                    theoryRatioPercent = 50,
                    questionRatioPercent = 30,
                    reviewRatioPercent = 20,
                    adaptivePlanningEnabled = true,
                    configurationJson = "{}",
                ),
            )
        }
    }

    @Test
    fun biweeklyDayOffOnlySuppressesItsActualOccurrences() {
        val monday = utcMillis(2026, Calendar.AUGUST, 3)
        val generated = planner.generateActivities(
            ScheduleGenerationRequest(planId, monday, days = 22, replaceUnlocked = false),
            rules = listOf(
                rule("study", planId, 1, 10 * 60),
                rule(
                    "off",
                    planId,
                    day = 1,
                    minute = null,
                    type = ScheduleRuleType.DAY_OFF,
                    duration = 5,
                    recurrence = "FREQ=WEEKLY;INTERVAL=2",
                ),
            ),
        )

        assertEquals(
            listOf(
                utcMillis(2026, Calendar.AUGUST, 10, 10, 0),
                utcMillis(2026, Calendar.AUGUST, 24, 10, 0),
            ),
            generated.map { it.scheduledStartEpochMillis },
        )
    }

    @Test
    fun availabilityIntervalAndCountArePositiveOccurrencePolicies() {
        val monday = utcMillis(2026, Calendar.AUGUST, 3)
        val biweekly = planner.generateActivities(
            ScheduleGenerationRequest(planId, monday, days = 22, replaceUnlocked = false),
            rules = listOf(
                rule("study", planId, 1, 10 * 60),
                rule(
                    "availability",
                    planId,
                    day = 1,
                    minute = 9 * 60,
                    type = ScheduleRuleType.AVAILABILITY,
                    duration = 180,
                    recurrence = "FREQ=WEEKLY;INTERVAL=2",
                ),
            ),
        )
        assertEquals(2, biweekly.size)
        assertEquals(
            listOf(
                utcMillis(2026, Calendar.AUGUST, 3, 10, 0),
                utcMillis(2026, Calendar.AUGUST, 17, 10, 0),
            ),
            biweekly.map { it.scheduledStartEpochMillis },
        )

        val counted = planner.generateActivities(
            ScheduleGenerationRequest(planId, monday, days = 29, replaceUnlocked = false),
            rules = listOf(
                rule("study-count", planId, 1, 10 * 60),
                rule(
                    "availability-count",
                    planId,
                    day = 1,
                    minute = 9 * 60,
                    type = ScheduleRuleType.AVAILABILITY,
                    duration = 180,
                    recurrence = "FREQ=WEEKLY;COUNT=2",
                ),
            ),
        )
        assertEquals(2, counted.size)
    }

    @Test
    fun postponedMissedActivitySkipsDayOffAvailabilityAndExistingOverlap() {
        val sunday = utcMillis(2026, Calendar.AUGUST, 2)
        val monday = utcMillis(2026, Calendar.AUGUST, 3)
        val tuesday = utcMillis(2026, Calendar.AUGUST, 4)
        val missed = activity(
            id = "missed",
            start = sunday + 20 * 60 * 60_000L,
            status = PlannedActivityStatus.MISSED,
        )
        val reserved = activity(
            id = "reserved",
            start = tuesday + 9 * 60 * 60_000L,
            status = PlannedActivityStatus.PLANNED,
        )
        val rules = listOf(
            rule("monday-off", planId, 1, null, ScheduleRuleType.DAY_OFF, duration = 5),
            rule("tuesday-window", planId, 2, 9 * 60, ScheduleRuleType.AVAILABILITY, duration = 180),
        )
        val now = monday - 5 * 60_000L

        val result = planner.rebalanceMissedActivities(
            activities = listOf(missed, reserved),
            rules = rules,
            handling = MissedActivityHandling.POSTPONE,
            nowEpochMillis = now,
            policyRangeStartEpochMillis = sunday,
        )

        assertEquals(1, result.size)
        assertEquals(tuesday + 10 * 60 * 60_000L, result.single().scheduledStartEpochMillis)
        assertEquals(PlannedActivityStatus.PLANNED, result.single().status)
    }

    private fun rule(
        id: String,
        owner: StudyPlanId,
        day: Int,
        minute: Int?,
        type: ScheduleRuleType = ScheduleRuleType.RECURRING_BLOCK,
        duration: Int = 60,
        recurrence: String? = null,
    ) = ScheduleRule(
        id = ScheduleRuleId(id),
        studyPlanId = owner,
        subjectId = null,
        type = type,
        dayOfWeek = day,
        startMinuteOfDay = minute,
        durationMinutes = duration,
        recurrenceRule = recurrence,
        locked = false,
        enabled = true,
        position = 0,
    )

    private fun activity(id: String, start: Long, status: PlannedActivityStatus) = PlannedActivity(
        id = PlannedActivityId(id),
        studyPlanId = planId,
        subjectId = null,
        topicId = null,
        materialId = null,
        videoResourceId = null,
        type = StudyActivityType.THEORY,
        title = id,
        scheduledStartEpochMillis = start,
        plannedDurationMinutes = 60,
        status = status,
        locked = false,
        completedAtEpochMillis = null,
    )
}

private fun utcMillis(
    year: Int,
    zeroBasedMonth: Int,
    dayOfMonth: Int,
    hour: Int = 0,
    minute: Int = 0,
): Long = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
    set(Calendar.YEAR, year)
    set(Calendar.MONTH, zeroBasedMonth)
    set(Calendar.DAY_OF_MONTH, dayOfMonth)
    set(Calendar.HOUR_OF_DAY, hour)
    set(Calendar.MINUTE, minute)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis
