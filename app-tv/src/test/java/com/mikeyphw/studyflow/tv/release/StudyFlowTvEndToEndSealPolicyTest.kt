package com.mikeyphw.studyflow.tv.release

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowTvEndToEndSealPolicyTest {
    @Test
    fun `source-only or skipped device run can never seal`() {
        assertFalse(
            StudyFlowTvEndToEndSealPolicy.canSeal(
                completedChecks = StudyFlowTvEndToEndSealPolicy.requiredChecks,
                phoneArtifactHashMatchesInstalled = true,
                tvArtifactHashMatchesInstalled = true,
                physicalRun = false,
            ),
        )
    }

    @Test
    fun `every physical check and exact installed artifacts are mandatory`() {
        assertTrue(
            StudyFlowTvEndToEndSealPolicy.canSeal(
                completedChecks = StudyFlowTvEndToEndSealPolicy.requiredChecks,
                phoneArtifactHashMatchesInstalled = true,
                tvArtifactHashMatchesInstalled = true,
                physicalRun = true,
            ),
        )
        assertFalse(
            StudyFlowTvEndToEndSealPolicy.canSeal(
                completedChecks = StudyFlowTvEndToEndSealPolicy.requiredChecks - "fresh_pairing",
                phoneArtifactHashMatchesInstalled = true,
                tvArtifactHashMatchesInstalled = true,
                physicalRun = true,
            ),
        )
        assertFalse(
            StudyFlowTvEndToEndSealPolicy.canSeal(
                completedChecks = StudyFlowTvEndToEndSealPolicy.requiredChecks,
                phoneArtifactHashMatchesInstalled = true,
                tvArtifactHashMatchesInstalled = false,
                physicalRun = true,
            ),
        )
    }
}
