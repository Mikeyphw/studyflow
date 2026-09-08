package com.mikeyphw.studyflow.tv.release

/** TVOV19 package/manifest/Devtool isolation policy. Pure Kotlin so it is unit-testable off-device. */
object StudyFlowTvPackagingPolicy {
    const val overlay = "TVOV19"
    const val applicationId = "com.mikeyphw.studyflow.tv"
    const val namespace = "com.mikeyphw.studyflow.tv"
    const val versionCode = 20
    const val versionName = "0.1.0-tv20"
    const val leanbackFeature = "android.software.leanback"
    const val touchscreenFeature = "android.hardware.touchscreen"
    const val leanbackLauncherCategory = "android.intent.category.LEANBACK_LAUNCHER"
    const val phoneLauncherCategory = "android.intent.category.LAUNCHER"
    const val bannerWidthDp = 160
    const val bannerHeightDp = 90
    const val releaseSigningEnvironmentPrefix = "STUDYFLOW_RELEASE_"
    const val backupDisabled = true
    const val cleartextTrafficDisabled = true

    fun isAllowedTvTargetTask(task: String): Boolean =
        task.startsWith("verifyStudyFlowTv") ||
            task.startsWith(":app-tv:") ||
            task.startsWith(":core:tvcontrol:")

    fun hasTvPackageIdentity(applicationId: String, namespace: String): Boolean =
        applicationId == this.applicationId && namespace == this.namespace
}
