package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.AppLanguage
import com.mikeyphw.studyflow.core.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseReadinessSealTest {
    @Test
    fun reportSealsAllDeliveredPhasesAndDebugWorkstreams() {
        val shell = AppShellReadinessAuditor.evaluate(
            AppShellReadinessInput(
                language = AppLanguage.SYSTEM_DEFAULT,
                themeMode = ThemeMode.SYSTEM_DEFAULT,
                dynamicColorEnabled = false,
                hasDefaultPlan = true,
            ),
        )

        val report = ReleaseReadinessSeal.evaluate(shell)

        assertEquals(39, report.totalPhaseCount)
        assertEquals(39, report.completedPhaseCount)
        assertEquals(100, report.completionPercent)
        assertTrue(report.phases.any { phase -> phase.number == 30 && phase.complete })
        assertTrue(report.phases.any { phase -> phase.number == 31 && phase.complete })
        assertTrue(report.phases.any { phase -> phase.number == 108 && phase.complete })
        assertTrue(report.mvpSealed)
    }
}
