package com.mikeyphw.studyflow.feature.reminders

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StudyRemindersScreenTest {
    @Test
    fun parsesMinuteOfDay() {
        assertEquals(8 * 60 + 30, parseMinuteOfDay("08:30"))
        assertNull(parseMinuteOfDay("24:00"))
        assertNull(parseMinuteOfDay("8:30"))
    }

    @Test
    fun formatsMinuteOfDay() {
        assertEquals("22:05", formatMinuteOfDay(22 * 60 + 5))
    }
}
