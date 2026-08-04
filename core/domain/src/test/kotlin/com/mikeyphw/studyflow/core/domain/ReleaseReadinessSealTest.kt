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

        assertEquals(45, report.totalPhaseCount)
        assertEquals(45, report.completedPhaseCount)
        assertEquals(100, report.completionPercent)
        assertTrue(report.phases.any { phase -> phase.number == 30 && phase.complete })
        assertTrue(report.phases.any { phase -> phase.number == 31 && phase.complete })
        assertTrue(report.phases.any { phase -> phase.number == 32 && phase.complete })
        assertTrue(report.phases.any { phase -> phase.number == 33 && phase.complete })
        assertTrue(report.phases.any { phase -> phase.number == 34 && phase.complete })
        assertTrue(report.phases.any { phase -> phase.number == 35 && phase.complete })
        assertTrue(report.phases.any { phase -> phase.number == 36 && phase.complete })
        assertTrue(report.phases.any { phase -> phase.number == 37 && phase.complete })
        assertTrue(report.phases.any { phase -> phase.number == 108 && phase.complete })
        assertTrue(report.productSealed)
    }
}
