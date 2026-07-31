package com.mikeyphw.studyflow.feature.schedule

import com.mikeyphw.studyflow.core.model.ScheduleRule
import com.mikeyphw.studyflow.core.model.ScheduleRuleId
import com.mikeyphw.studyflow.core.model.ScheduleRuleType
import com.mikeyphw.studyflow.core.model.StudyPlanId
import org.junit.Assert.assertEquals
import org.junit.Test

class StudyScheduleUiModelsTest {
    @Test
    fun formatsMinuteOfDayAsClockTime() {
        val rule = ScheduleRule(
            id = ScheduleRuleId("rule"),
            studyPlanId = StudyPlanId("plan"),
            subjectId = null,
            type = ScheduleRuleType.RECURRING_BLOCK,
            dayOfWeek = 1,
            startMinuteOfDay = 8 * 60 + 5,
            durationMinutes = 45,
            recurrenceRule = null,
            locked = false,
            enabled = true,
            position = 0,
        )

        assertEquals("08:05", rule.timeLabel())
    }
}
