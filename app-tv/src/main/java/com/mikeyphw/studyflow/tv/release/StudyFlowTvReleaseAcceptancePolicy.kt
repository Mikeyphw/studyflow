package com.mikeyphw.studyflow.tv.release

/** TVOV20 release/real-device acceptance semantics. */
object StudyFlowTvReleaseAcceptancePolicy {
    const val overlay = "TVOV20"
    const val releaseTargetMiB = 25
    const val releaseHardCeilingMiB = 35

    enum class DeviceAcceptance {
        NO_DEVICE_SKIPPED,
        REAL_DEVICE_ACCEPTED,
        REAL_DEVICE_FAILED,
    }

    data class ReleaseEvidence(
        val optimizedReleaseBuilt: Boolean,
        val mappingPresent: Boolean,
        val packageIdentityVerified: Boolean,
        val leanbackManifestVerified: Boolean,
        val unitTestsPassed: Boolean,
        val lintPassed: Boolean,
        val releaseSizeMiB: Double,
    ) {
        val artifactReady: Boolean
            get() = optimizedReleaseBuilt &&
                mappingPresent &&
                packageIdentityVerified &&
                leanbackManifestVerified &&
                unitTestsPassed &&
                lintPassed &&
                releaseSizeMiB <= releaseTargetMiB
    }

    fun classifyDeviceAcceptance(
        deviceAvailable: Boolean,
        leanbackDevice: Boolean,
        installOrUpgradeSucceeded: Boolean,
        launchSucceeded: Boolean,
        dpadSmokeSucceeded: Boolean,
    ): DeviceAcceptance {
        if (!deviceAvailable) return DeviceAcceptance.NO_DEVICE_SKIPPED
        return if (leanbackDevice && installOrUpgradeSucceeded && launchSucceeded && dpadSmokeSucceeded) {
            DeviceAcceptance.REAL_DEVICE_ACCEPTED
        } else {
            DeviceAcceptance.REAL_DEVICE_FAILED
        }
    }

    fun releaseSealCanPass(evidence: ReleaseEvidence, deviceAcceptance: DeviceAcceptance): Boolean =
        evidence.artifactReady && deviceAcceptance != DeviceAcceptance.REAL_DEVICE_FAILED
}
