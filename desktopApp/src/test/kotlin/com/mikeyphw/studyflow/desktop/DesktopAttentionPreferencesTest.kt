package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.domain.StudyReminderPolicy
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionFocusPolicy
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionFocusProfile
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionQuietWindow
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
    @Test
    fun `persists ATT10 focus profile quiet hours and mirror cap`() {
        val root = Files.createTempDirectory("studyflow-att10-focus-prefs")
        val planId = StudyPlanId("plan-att10")
        val first = DesktopAttentionPreferences.open(root)
        first.saveFocusPolicy(
            planId,
            StudyFlowAttentionFocusPolicy(
                profile = StudyFlowAttentionFocusProfile.EXAM_MODE,
                quietWindow = StudyFlowAttentionQuietWindow(
                    enabled = true,
                    startMinute = 23 * 60,
                    endMinute = 6 * 60,
                    timezoneId = "America/Sao_Paulo",
                ),
                maxEscalationMirrors = 1,
            ),
        )

        val reopened = DesktopAttentionPreferences.open(root).focusPolicy(planId)
        assertEquals(StudyFlowAttentionFocusProfile.EXAM_MODE, reopened.profile)
        assertEquals("America/Sao_Paulo", reopened.quietWindow?.timezoneId)
        assertEquals(23 * 60, reopened.quietWindow?.startMinute)
        assertEquals(6 * 60, reopened.quietWindow?.endMinute)
        assertEquals(1, reopened.maxEscalationMirrors)
    }

    @Test
    fun `ATT10 focus policy migrates legacy quiet hours without inventing defaults`() {
        val root = Files.createTempDirectory("studyflow-att10-focus-migration")
        val planId = StudyPlanId("plan-att10-migration")
        val prefs = DesktopAttentionPreferences.open(root)
        val disabled = prefs.focusPolicy(planId, StudyPlanSettings.defaults(planId))
        assertEquals(null, disabled.quietWindow)

        val enabledPlanId = StudyPlanId("plan-att10-legacy-quiet")
        val legacy = StudyPlanSettings.defaults(enabledPlanId).copy(
            quietHoursStartMinute = 21 * 60,
            quietHoursEndMinute = 6 * 60,
        )
        val migrated = prefs.focusPolicy(enabledPlanId, legacy)
        assertEquals(21 * 60, migrated.quietWindow?.startMinute)
        assertEquals(6 * 60, migrated.quietWindow?.endMinute)
        assertTrue(migrated.quietWindow?.enabled == true)
    }

}
