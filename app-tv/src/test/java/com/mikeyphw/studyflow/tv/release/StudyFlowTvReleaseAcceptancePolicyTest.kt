package com.mikeyphw.studyflow.tv.release

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class StudyFlowTvReleaseAcceptancePolicyTest {
    @Test
    fun noDeviceIsCleanSkipButConnectedFailureIsNot() {
        assertEquals(
            StudyFlowTvReleaseAcceptancePolicy.DeviceAcceptance.NO_DEVICE_SKIPPED,
            StudyFlowTvReleaseAcceptancePolicy.classifyDeviceAcceptance(false, false, false, false, false),
        )
        assertEquals(
            StudyFlowTvReleaseAcceptancePolicy.DeviceAcceptance.REAL_DEVICE_FAILED,
            StudyFlowTvReleaseAcceptancePolicy.classifyDeviceAcceptance(true, true, true, false, false),
        )
    }

    @Test
    fun artifactReadyPlusSkipOrAcceptedDeviceCanSeal() {
        val ready = StudyFlowTvReleaseAcceptancePolicy.ReleaseEvidence(
            optimizedReleaseBuilt = true,
            mappingPresent = true,
            packageIdentityVerified = true,
            leanbackManifestVerified = true,
            unitTestsPassed = true,
            lintPassed = true,
            releaseSizeMiB = 10.0,
        )
        assertTrue(ready.artifactReady)
        assertTrue(
            StudyFlowTvReleaseAcceptancePolicy.releaseSealCanPass(
                ready,
                StudyFlowTvReleaseAcceptancePolicy.DeviceAcceptance.NO_DEVICE_SKIPPED,
            ),
        )
        assertTrue(
            StudyFlowTvReleaseAcceptancePolicy.releaseSealCanPass(
                ready,
                StudyFlowTvReleaseAcceptancePolicy.DeviceAcceptance.REAL_DEVICE_ACCEPTED,
            ),
        )
        assertFalse(
            StudyFlowTvReleaseAcceptancePolicy.releaseSealCanPass(
                ready,
                StudyFlowTvReleaseAcceptancePolicy.DeviceAcceptance.REAL_DEVICE_FAILED,
            ),
        )
    }
}
