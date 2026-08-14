package com.mikeyphw.studyflow.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseReadinessSealPhase25Test {
    @Test
    fun phaseTwentyFiveRequiresExplicitHistoricalCompletionEvidence() {
        val report = ReleaseReadinessSeal.evaluate(
            shellReadiness = AppShellReadinessReport(true, true, true, true),
            releaseValidation = ReleaseValidationSealFactory.sourceOnlySeal(1L),
            completedPhaseNumbers = setOf(21, 24, 25),
        )
        assertEquals(45, report.totalPhaseCount)
        assertEquals(3, report.completedPhaseCount)
        assertTrue(report.phases.any { phase -> phase.number == 21 && phase.complete })
        assertTrue(report.phases.any { phase -> phase.number == 24 && phase.complete })
        assertTrue(report.phases.any { phase -> phase.number == 25 && phase.complete })
        assertFalse(report.phases.any { phase -> phase.number == 26 && phase.complete })
        assertFalse(report.productSealed)
    }
}
