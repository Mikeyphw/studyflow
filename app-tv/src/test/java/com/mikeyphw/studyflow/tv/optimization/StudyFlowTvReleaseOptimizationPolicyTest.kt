package com.mikeyphw.studyflow.tv.optimization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowTvReleaseOptimizationPolicyTest {
    @Test
    fun optimizedReleaseAtOrBelowTargetIsAccepted() {
        val result = StudyFlowTvReleaseOptimizationPolicy.evaluate(
            StudyFlowTvReleaseOptimizationSnapshot(
                releaseBytes = 12L * StudyFlowTvReleaseOptimizationPolicy.bytesPerMiB,
                minified = true,
                resourcesShrunk = true,
                mappingPresent = true,
            ),
        )
        assertEquals("TVOV18", StudyFlowTvReleaseOptimizationPolicy.overlay)
        assertEquals(StudyFlowTvReleaseBudgetBand.TARGET, result.budgetBand)
        assertTrue(result.optimized)
        assertTrue(result.acceptableForTvRelease)
    }

    @Test
    fun releaseAboveTargetOrWithoutR8EvidenceFailsTheTv18Seal() {
        val aboveTarget = StudyFlowTvReleaseOptimizationPolicy.evaluate(
            StudyFlowTvReleaseOptimizationSnapshot(
                releaseBytes = 30L * StudyFlowTvReleaseOptimizationPolicy.bytesPerMiB,
                minified = true,
                resourcesShrunk = true,
                mappingPresent = true,
            ),
        )
        val missingMapping = StudyFlowTvReleaseOptimizationPolicy.evaluate(
            StudyFlowTvReleaseOptimizationSnapshot(
                releaseBytes = 10L * StudyFlowTvReleaseOptimizationPolicy.bytesPerMiB,
                minified = true,
                resourcesShrunk = true,
                mappingPresent = false,
            ),
        )
        assertEquals(StudyFlowTvReleaseBudgetBand.ABOVE_TARGET, aboveTarget.budgetBand)
        assertFalse(aboveTarget.acceptableForTvRelease)
        assertFalse(missingMapping.optimized)
        assertFalse(missingMapping.acceptableForTvRelease)
    }

    @Test
    fun hardCeilingStillFailsClosed() {
        val result = StudyFlowTvReleaseOptimizationPolicy.evaluate(
            StudyFlowTvReleaseOptimizationSnapshot(
                releaseBytes = 36L * StudyFlowTvReleaseOptimizationPolicy.bytesPerMiB,
                minified = true,
                resourcesShrunk = true,
                mappingPresent = true,
            ),
        )
        assertEquals(StudyFlowTvReleaseBudgetBand.HARD_CEILING_EXCEEDED, result.budgetBand)
        assertFalse(result.acceptableForTvRelease)
    }
}
