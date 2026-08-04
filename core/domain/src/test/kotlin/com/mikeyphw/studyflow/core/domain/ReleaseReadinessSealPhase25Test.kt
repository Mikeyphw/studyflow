package com.mikeyphw.studyflow.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseReadinessSealPhase25Test {
    @Test
    fun evaluateStillRepresentsPhaseTwentyFiveInsideCurrentDeliveryScope() {
        val report = ReleaseReadinessSeal.evaluate(
            AppShellReadinessReport(
                languageConfigured = true,
                appearanceConfigured = true,
                backupEntryAvailable = true,
                accessibilityBaselineReady = true,
            ),
        )

        assertEquals(41, report.totalPhaseCount)
        assertEquals(41, report.completedPhaseCount)
        assertEquals(100, report.completionPercent)
        assertTrue(report.phases.any { phase -> phase.number == 21 && phase.complete })
        assertTrue(report.phases.any { phase -> phase.number == 24 && phase.complete })
        assertTrue(report.phases.any { phase -> phase.number == 25 && phase.complete })
    }
}
