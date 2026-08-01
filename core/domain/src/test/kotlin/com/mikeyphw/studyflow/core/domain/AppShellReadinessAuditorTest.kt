package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.AppLanguage
import com.mikeyphw.studyflow.core.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppShellReadinessAuditorTest {
    @Test
    fun reportsBackupReadinessWhenDefaultPlanExists() {
        val report = AppShellReadinessAuditor.evaluate(
            AppShellReadinessInput(
                language = AppLanguage.ENGLISH,
                themeMode = ThemeMode.DARK,
                dynamicColorEnabled = false,
                hasDefaultPlan = true,
            ),
        )

        assertTrue(report.backupEntryAvailable)
        assertEquals(100, report.readyPercent)
    }
}
