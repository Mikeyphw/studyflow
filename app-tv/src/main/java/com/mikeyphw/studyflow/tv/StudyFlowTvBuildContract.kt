package com.mikeyphw.studyflow.tv

/**
 * Build-time contract for the minimal Android TV application introduced in TVOV01.
 *
 * The TV APK starts as a small receiver shell. Feature screens, paired-phone control, and cast
 * protocol code are added by later TV overlays only after their dependency and size impact is
 * measured.
 */
object StudyFlowTvBuildContract {
    const val tvOverlay = "TVOV01"
    const val applicationId = "com.mikeyphw.studyflow.tv"
    const val phoneApplicationId = "com.mikeyphw.studyflow"
    const val sizeTargetMiB = 25
    const val sizeHardCeilingMiB = 35
    const val startsWithoutFeatureModules = true
    const val startsWithoutCastSdk = true
    const val startsWithoutAndroidxTvUi = true
    const val pairedPhoneControlRequiredByRoadmap = true
    const val sharedControlProtocolModule = ":core:tvcontrol"
    const val controlProtocolVersion = 1
    const val castTransportDeferred = true
}
