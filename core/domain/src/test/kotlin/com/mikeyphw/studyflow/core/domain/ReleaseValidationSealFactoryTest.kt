package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.ReleaseValidationGateKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseValidationSealFactoryTest {
    @Test
    fun sourceOnlySealBlocksReleaseUntilRealBuildEvidenceExists() {
        val seal = ReleaseValidationSealFactory.sourceOnlySeal(generatedAtEpochMillis = 10L)

        assertEquals(26, seal.currentPhase)
        assertEquals(26, seal.validatedPhase)
        assertFalse(seal.releaseReady)
        assertTrue(seal.blockerCount > 0)
        assertTrue(seal.gates.any { gate ->
            gate.kind == ReleaseValidationGateKind.GRADLE_CONFIGURATION && gate.blocksRelease
        })
        assertTrue(seal.gates.any { gate ->
            gate.kind == ReleaseValidationGateKind.DEVICE_SMOKE && gate.blocksRelease
        })
    }

    @Test
    fun completedSealCoversEveryRequiredGate() {
        val seal = ReleaseValidationSealFactory.completedSeal(
            generatedAtEpochMillis = 20L,
            deviceSmokeEvidence = "Device smoke completed.",
        )

        assertTrue(seal.releaseReady)
        assertEquals(0, seal.blockerCount)
        assertEquals(
            ReleaseValidationSealFactory.requiredKinds(),
            seal.gates.map { it.kind }.toSet(),
        )
    }
}
