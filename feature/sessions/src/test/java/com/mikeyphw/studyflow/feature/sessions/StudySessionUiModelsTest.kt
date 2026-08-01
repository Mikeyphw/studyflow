package com.mikeyphw.studyflow.feature.sessions

import org.junit.Assert.assertEquals
import org.junit.Test

class StudySessionUiModelsTest {
    @Test
    fun durationUsesMinuteFormatBelowOneHour() {
        assertEquals("09:05", formatSessionDuration(545))
    }

    @Test
    fun durationUsesHourFormatAtOneHourOrMore() {
        assertEquals("1:02:03", formatSessionDuration(3_723))
    }

    @Test
    fun durationClampsNegativeInput() {
        assertEquals("00:00", formatSessionDuration(-10))
    }
}
