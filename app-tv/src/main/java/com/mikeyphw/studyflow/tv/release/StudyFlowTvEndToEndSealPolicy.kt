package com.mikeyphw.studyflow.tv.release

/** Fail-closed TVOV26 policy. Source/build validation is not physical-device evidence. */
object StudyFlowTvEndToEndSealPolicy {
    const val overlay = "TVOV26"
    const val phonePackage = "com.mikeyphw.studyflow"
    const val tvPackage = "com.mikeyphw.studyflow.tv"
    const val tvVersionCode = 26
    const val tvVersionName = "0.1.0-tv26"

    val requiredChecks: Set<String> = linkedSetOf(
        "two_devices_classified",
        "signed_release_artifacts_installed",
        "tv_leanback_launch_dpad",
        "phone_controller_launch",
        "fresh_pairing",
        "authenticated_navigation",
        "live_cast_acknowledged",
        "canonical_domain_mutation",
        "tv_restart_resync",
        "phone_restart_resync",
        "stop_cast_waiting_state",
        "revoke_blocks_control",
    )

    fun canSeal(
        completedChecks: Set<String>,
        phoneArtifactHashMatchesInstalled: Boolean,
        tvArtifactHashMatchesInstalled: Boolean,
        physicalRun: Boolean,
    ): Boolean = physicalRun &&
        completedChecks == requiredChecks &&
        phoneArtifactHashMatchesInstalled &&
        tvArtifactHashMatchesInstalled
}
