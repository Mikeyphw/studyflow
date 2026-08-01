package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.AppLanguage
import com.mikeyphw.studyflow.core.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseReadinessSealTest {
    @Test
    fun reportSealsAllMvpPhases() {
        val shell = AppShellReadinessAuditor.evaluate(
            AppShellReadinessInput(
                language = AppLanguage.SYSTEM_DEFAULT,
                themeMode = ThemeMode.SYSTEM_DEFAULT,
                dynamicColorEnabled = true,
                hasDefaultPlan = true,
            ),
        )

        val report = ReleaseReadinessSeal.evaluate(shell)

        assertEquals(15, report.totalPhaseCount)
        assertEquals(15, report.completedPhaseCount)
        assertEquals(100, report.completionPercent)
        assertTrue(report.mvpSealed)
    }
}
