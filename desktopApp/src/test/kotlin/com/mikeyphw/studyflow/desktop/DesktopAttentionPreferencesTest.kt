package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.domain.StudyReminderPolicy
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyPlanSettings
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopAttentionPreferencesTest {
    @Test
    fun `persists global and per-plan attention policy`() {
        val root = Files.createTempDirectory("studyflow-att04-prefs")
        val planId = StudyPlanId("plan-att04")
        val legacy = StudyPlanSettings.defaults(planId)
        val first = DesktopAttentionPreferences.open(root)
        first.updateGlobal { it.copy(nativeNotificationsEnabled = false, trayDashboardEnabled = true, paused = true) }
        first.savePolicy(
            planId,
            StudyReminderPolicy(
                plannedStudyEnabled = false,
                dueReviewEnabled = true,
                missedActivityEnabled = true,
                dailySummaryEnabled = true,
                sessionTimerEnabled = true,
                examCountdownEnabled = true,
                plannedStudyLeadMinutes = 20,
                defaultSnoozeMinutes = 15,
            ),
        )

        val reopened = DesktopAttentionPreferences.open(root)
        val global = reopened.global()
        assertFalse(global.nativeNotificationsEnabled)
        assertTrue(global.trayDashboardEnabled)
        assertTrue(global.paused)
        val policy = reopened.policy(planId, legacy)
        assertFalse(policy.plannedStudyEnabled)
        assertTrue(policy.examCountdownEnabled)
        assertEquals(20, policy.plannedStudyLeadMinutes)
        assertEquals(15, policy.defaultSnoozeMinutes)
    }
}
