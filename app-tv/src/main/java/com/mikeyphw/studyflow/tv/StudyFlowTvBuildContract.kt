package com.mikeyphw.studyflow.tv

/**
 * Build-time contract for the Android TV application introduced in TVOV01 and expanded by the
 * structured TV roadmap overlays.
 *
 * The TV APK remains a separate, measured target. TVOV07 and TVOV08 add real Today and Study
 * Session, Question Practice, and Mock Exam surfaces through native Android views, small Kotlin
 * models, and the shared tv-control protocol instead of pulling phone feature modules into the TV APK.
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
    const val dpadFocusPrimitivesIntroduced = true
    const val focusPrimitivesRequireNoComposeRuntime = true
    const val minimumFocusTargetDp = 64
    const val deterministicInitialFocusRequired = true
    const val currentTvOverlay = "TVOV10"
    const val navigationShellIntroduced = true
    const val navigationShellRequiresNoComposeRuntime = true
    const val navigationShellUsesSharedControlProtocol = true
    const val navigationDestinationCount = 8
    const val secondaryNavigationDestinationCount = 1
}

// TVOV06 Home surface contract.
@Suppress("ConstPropertyName")
object StudyFlowTvHomeBuildContract {
    const val homeSurfaceIntroduced = true
    const val homeSurfaceRequiresNoComposeRuntime = true
    const val homeSurfaceUsesNavigationShell = true
    const val homeSurfaceExposesPhonePairingEntry = true
    const val homeCardCount = 4
    const val homePrimaryActionCount = 3
}

// TVOV07 Today workspace contract.
@Suppress("ConstPropertyName")
object StudyFlowTvTodayBuildContract {
    const val todayWorkspaceIntroduced = true
    const val todayWorkspaceRequiresNoComposeRuntime = true
    const val todayWorkspaceUsesNavigationShell = true
    const val todayWorkspaceUsesSharedControlProtocol = true
    const val nextBlockReviewPracticeAndScheduleVisible = true
    const val todayBlockCount = 4
}

// TVOV08 Study session experience contract.
@Suppress("ConstPropertyName")
object StudyFlowTvStudySessionBuildContract {
    const val studySessionExperienceIntroduced = true
    const val studySessionRequiresNoComposeRuntime = true
    const val studySessionUsesSharedControlProtocol = true
    const val studySessionSupportsRemoteAndPhoneControls = true
    const val studySessionEmitsCastState = true
    const val studySessionSegmentCount = 4
    const val studySessionActionCount = 6
}


// TVOV09 Question practice contract.
@Suppress("ConstPropertyName")
object StudyFlowTvQuestionPracticeBuildContract {
    const val questionPracticeIntroduced = true
    const val questionPracticeRequiresNoComposeRuntime = true
    const val questionPracticeUsesSharedControlProtocol = true
    const val questionPracticeSupportsRemoteAndPhoneControls = true
    const val questionPracticeEmitsCastState = true
    const val questionPracticeQuestionCount = 3
    const val questionPracticeChoiceCount = 4
    const val questionPracticeActionCount = 8
}

// TVOV10 Mock Exam Studio contract.
@Suppress("ConstPropertyName")
object StudyFlowTvMockExamBuildContract {
    const val mockExamStudioIntroduced = true
    const val mockExamRequiresNoComposeRuntime = true
    const val mockExamUsesSharedControlProtocol = true
    const val mockExamSupportsRemoteAndPhoneControls = true
    const val mockExamEmitsCastState = true
    const val mockExamSectionCount = 4
    const val mockExamActionCount = 8
}
