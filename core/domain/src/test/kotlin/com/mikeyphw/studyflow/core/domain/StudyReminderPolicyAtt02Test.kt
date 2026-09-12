package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyPlanSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class StudyReminderPolicyAtt02Test {
    @Test
    fun legacyReviewPreferenceMigratesWithoutMidnightSummaryDefault() {
        val settings = StudyPlanSettings.defaults(StudyPlanId("legacy")).copy(reviewNotificationsEnabled = false)
        val migrated = StudyReminderPolicy.fromLegacy(settings)

        assertFalse(migrated.dueReviewEnabled)
        assertEquals(8 * 60, migrated.dailySummaryMinuteOfDay)
        assertEquals(10, migrated.plannedStudyLeadMinutes)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOutOfRangeSummaryTime() {
        StudyReminderPolicy(dailySummaryMinuteOfDay = 1440)
    }
}
