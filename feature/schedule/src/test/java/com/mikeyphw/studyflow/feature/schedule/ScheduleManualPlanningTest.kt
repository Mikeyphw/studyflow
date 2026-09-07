package com.mikeyphw.studyflow.feature.schedule

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScheduleManualPlanningTest {
    private val zone = ZoneId.of("America/Sao_Paulo")

    @Test
    fun `manual planning requires valid calendar date and clock time`() {
        assertNull(scheduleStartEpochMillis("2026-02-30", "08:00", zone))
        assertNull(scheduleStartEpochMillis("2026-09-07", "25:00", zone))
    }

    @Test
    fun `manual planning preserves selected local date and time`() {
        val epoch = scheduleStartEpochMillis("2026-09-07", "09:30", zone)
        val resolved = Instant.ofEpochMilli(requireNotNull(epoch)).atZone(zone)
        assertEquals(LocalDate.of(2026, 9, 7), resolved.toLocalDate())
        assertEquals(9, resolved.hour)
        assertEquals(30, resolved.minute)
    }
}
