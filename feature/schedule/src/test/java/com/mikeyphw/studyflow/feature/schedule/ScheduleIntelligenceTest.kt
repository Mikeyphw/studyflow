package com.mikeyphw.studyflow.feature.schedule

import com.mikeyphw.studyflow.core.model.PlannedActivity
import com.mikeyphw.studyflow.core.model.PlannedActivityId
import com.mikeyphw.studyflow.core.model.PlannedActivityStatus
import com.mikeyphw.studyflow.core.model.StudyActivityType
import com.mikeyphw.studyflow.core.model.StudyPlanId
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleIntelligenceTest {
    private val planId = StudyPlanId("plan")
    private val zone = ZoneId.of("UTC")

    @Test
    fun generatedPrefixIsDurableProvenance() {
        assertEquals(
            ScheduleActivityProvenance.GENERATED,
            ScheduleIntelligence.provenance(activity("generated:abc:1", 0L)),
        )
        assertEquals(ScheduleActivityProvenance.MANUAL, ScheduleIntelligence.provenance(activity("manual", 0L)))
    }

    @Test
    fun groupsScheduledDaysAndKeepsUnscheduledLast() {
        val groups = ScheduleIntelligence.groupByDay(
            listOf(activity("later", 86_400_000L), activity("unscheduled", null), activity("first", 0L)),
            zone,
        )
        assertEquals(3, groups.size)
        assertEquals("1970-01-01", groups[0].date.toString())
        assertEquals("1970-01-02", groups[1].date.toString())
        assertEquals(null, groups[2].date)
    }

    @Test
    fun detectsOverlapAndDailyOverload() {
        val first = activity("a", 0L, duration = 180)
        val overlap = activity("b", 60L * 60_000L, duration = 120)
        val report = ScheduleIntelligence.inspect(listOf(first, overlap), zone, dailyOverloadMinutes = 240)
        assertEquals(setOf(first.id, overlap.id), report.conflictActivityIds)
        assertEquals(1, report.overloadedDates.size)
    }

    @Test
    fun bulkMutationsProtectLockedAndHistory() {
        assertTrue(ScheduleIntelligence.isBulkMutable(activity("planned", 0L)))
        assertFalse(ScheduleIntelligence.isBulkMutable(activity("locked", 0L, locked = true)))
        assertFalse(ScheduleIntelligence.isBulkMutable(activity("done", 0L, status = PlannedActivityStatus.COMPLETED)))
    }

    private fun activity(
        id: String,
        start: Long?,
        duration: Int = 60,
        locked: Boolean = false,
        status: PlannedActivityStatus = PlannedActivityStatus.PLANNED,
    ) = PlannedActivity(
        id = PlannedActivityId(id),
        studyPlanId = planId,
        subjectId = null,
        topicId = null,
        materialId = null,
        videoResourceId = null,
        type = StudyActivityType.THEORY,
        title = id,
        scheduledStartEpochMillis = start,
        plannedDurationMinutes = duration,
        status = status,
        locked = locked,
        completedAtEpochMillis = null,
    )
}
