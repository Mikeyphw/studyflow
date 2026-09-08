package com.mikeyphw.studyflow.tv

/**
 * Build-time contract for the Android TV application introduced in TVOV01 and expanded by the
 * structured TV roadmap overlays.
 *
 * The TV APK remains a separate, measured target. TVOV07 and TVOV08 add real Today and Study
 * Session, Question Practice, Mock Exam, Review Notebook, and Progress surfaces through
 * native Android views, small Kotlin models, and the shared tv-control protocol instead of pulling phone feature modules into the TV APK.
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
    const val currentTvOverlay = "TVOV18"
    const val navigationShellIntroduced = true
    const val navigationShellRequiresNoComposeRuntime = true
    const val navigationShellUsesSharedControlProtocol = true
    const val navigationDestinationCount = 9
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

// TVOV11 Review and error-notebook contract.
@Suppress("ConstPropertyName")
object StudyFlowTvReviewNotebookBuildContract {
    const val reviewNotebookIntroduced = true
    const val reviewNotebookRequiresNoComposeRuntime = true
    const val reviewNotebookUsesSharedControlProtocol = true
    const val reviewNotebookSupportsRemoteAndPhoneControls = true
    const val reviewNotebookEmitsCastState = true
    const val reviewNotebookItemCount = 4
    const val reviewNotebookActionCount = 7
}

// TVOV12 Progress and analytics contract.
@Suppress("ConstPropertyName")
object StudyFlowTvProgressAnalyticsBuildContract {
    const val progressAnalyticsIntroduced = true
    const val progressAnalyticsRequiresNoComposeRuntime = true
    const val progressAnalyticsUsesSharedControlProtocol = true
    const val progressAnalyticsSupportsRemoteAndPhoneControls = true
    const val progressAnalyticsEmitsCastState = true
    const val progressAnalyticsMetricCount = 4
    const val progressAnalyticsSubjectCount = 4
    const val progressAnalyticsActionCount = 4
}


// TVOV13 Read-only schedule contract.
@Suppress("ConstPropertyName")
object StudyFlowTvScheduleBuildContract {
    const val readOnlyScheduleIntroduced = true
    const val scheduleRequiresNoComposeRuntime = true
    const val scheduleUsesSharedControlProtocol = true
    const val scheduleSupportsRemoteAndPhoneNavigation = true
    const val scheduleEditingDisabledOnTv = true
    const val scheduleEmitsCastState = true
    const val scheduleItemCount = 5
    const val scheduleActionCount = 5
}

// TVOV14 Limited library/reference material contract.
@Suppress("ConstPropertyName")
object StudyFlowTvLibraryBuildContract {
    const val limitedLibraryReferenceIntroduced = true
    const val libraryRequiresNoComposeRuntime = true
    const val libraryUsesSharedControlProtocol = true
    const val librarySupportsRemoteAndPhoneNavigation = true
    const val libraryFileManagementDisabledOnTv = true
    const val libraryEmitsCastState = true
    const val libraryReferenceCount = 4
    const val libraryActionCount = 5
}

// TVOV15 Minimal TV settings contract.
@Suppress("ConstPropertyName")
object StudyFlowTvSettingsBuildContract {
    const val minimalTvSettingsIntroduced = true
    const val settingsRequireNoComposeRuntime = true
    const val settingsArePresentationOnly = true
    const val settingsUseSharedControlProtocol = true
    const val accountManagementDisabledOnTv = true
    const val importExportDisabledOnTv = true
    const val pairingRevocationVisible = true
    const val focusAccessibilityPreferenceRepresented = true
    const val settingsCardCount = 4
    const val settingsActionCount = 4
}

// TVOV16 Cross-device control/state continuity contract.
@Suppress("ConstPropertyName")
object StudyFlowTvControlContinuityBuildContract {
    const val controlStateContinuityIntroduced = true
    const val localAndPairedPhoneUseSameReducer = true
    const val controlTransportStillDeferred = true
    const val monotonicCommandRevisionRequired = true
    const val duplicateAndStaleCommandsRejected = true
    const val expiredAndRevokedPairingsRejected = true
    const val continuitySurfaceCount = 10
}

// TVOV17 Accessibility and 10-foot UI seal contract.
@Suppress("ConstPropertyName")
object StudyFlowTvAccessibilityBuildContract {
    const val accessibilityTenFootSealIntroduced = true
    const val nativeAccessibilitySemanticsRequired = true
    const val scrollableFocusedContentRequired = true
    const val semanticHeadingsRequired = true
    const val politeLiveStatusRequired = true
    const val highVisibilityFocusAffectsRendering = true
    const val minimumBodyTextSp = 20
    const val minimumControlTextSp = 18
    const val minimumFocusTargetDp = 64
}

// TVOV18 Size optimization, R8, and resource shrinking contract.
@Suppress("ConstPropertyName")
object StudyFlowTvReleaseOptimizationBuildContract {
    const val sizeOptimizationSealIntroduced = true
    const val r8Required = true
    const val resourceShrinkingRequired = true
    const val optimizedDefaultRulesRequired = true
    const val mappingEvidenceRequired = true
    const val releaseTargetMiB = 25
    const val releaseHardCeilingMiB = 35
}

