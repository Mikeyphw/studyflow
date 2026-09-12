package com.mikeyphw.studyflow.reminders

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mikeyphw.studyflow.core.domain.StudyReminderPolicy
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyPlanSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidStudyReminderPolicyStoreAtt02Test {
    @Test
    fun migratesLegacyReviewSettingThenPersistsFullPerPlanPolicy() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences("studyflow_android_attention_policy_v1", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
        val planId = StudyPlanId("att02-policy-test")
        val legacy = StudyPlanSettings.defaults(planId).copy(reviewNotificationsEnabled = false)
        val store = AndroidStudyReminderPolicyStore(context)

        val migrated = store.load(planId, legacy)
        assertFalse(migrated.dueReviewEnabled)
        assertEquals(8 * 60, migrated.dailySummaryMinuteOfDay)

        val updated = StudyReminderPolicy(
            plannedStudyEnabled = false,
            dueReviewEnabled = true,
            missedActivityEnabled = false,
            dailySummaryEnabled = true,
            sessionTimerEnabled = false,
            examCountdownEnabled = true,
            plannedStudyLeadMinutes = 25,
            dailySummaryMinuteOfDay = 18 * 60 + 30,
            missedActivityDelayMinutes = 45,
            defaultSnoozeMinutes = 15,
            sessionEndingLeadMinutes = 3,
            examCountdownDays = 14,
        )
        store.save(planId, updated)

        assertEquals(updated, store.load(planId, legacy))
    }
}
