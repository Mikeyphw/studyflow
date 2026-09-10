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
    const val castTransportDeferred = false
    const val dpadFocusPrimitivesIntroduced = true
    const val focusPrimitivesRequireNoComposeRuntime = true
    const val minimumFocusTargetDp = 64
    const val deterministicInitialFocusRequired = true
    const val currentTvOverlay = "TVOV28"
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
    const val controlTransportStillDeferred = false
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

// TVOV19 Android TV manifest, packaging, and Devtool target hardening contract.
@Suppress("ConstPropertyName")
object StudyFlowTvPackagingBuildContract {
    const val packagingHardeningIntroduced = true
    const val leanbackLauncherRequired = true
    const val touchscreenNotRequired = true
    const val backupAndDeviceTransferDisabled = true
    const val cleartextTrafficDisabled = true
    const val tvTargetRejectsPhoneTasks = true
    const val conditionalReleaseSigningSupported = true
    const val packageVersionCode = 26
}

// TVOV20 Real-device acceptance and release seal contract.
@Suppress("ConstPropertyName")
object StudyFlowTvFinalAcceptanceBuildContract {
    const val finalAcceptanceSealIntroduced = true
    const val optimizedReleaseArtifactRequired = true
    const val packageIdentityVerificationRequired = true
    const val leanbackDeviceCheckRequiredWhenConnected = true
    const val installOrUpgradeCheckRequiredWhenConnected = true
    const val launchAndDpadSmokeRequiredWhenConnected = true
    const val noDeviceMustSkipCleanly = true
    const val physicalDeviceAcceptanceMustNotBeClaimedWhenSkipped = true
    const val releaseTargetMiB = 25
}
// TVOV21 Typed presentation snapshots and canonical data projection contract.
@Suppress("ConstPropertyName")
object StudyFlowTvPresentationBuildContract {
    const val typedPresentationProtocolIntroduced = true
    const val phoneOwnsCanonicalProjection = true
    const val tvRuntimeFixtureDefaultsDisabled = true
    const val tvHasNoRepositoryOrDatabaseDependency = true
    const val presentationTransportStillDeferred = false
    const val projectedSurfaceCount = 8
}
// TVOV22 Local pairing, discovery, and transport contract.
@Suppress("ConstPropertyName")
object StudyFlowTvLocalLinkBuildContract {
    const val localPairingDiscoveryTransportIntroduced = true
    const val sharedAndroidLinkModule = ":core:tvlink"
    const val androidNsdDiscoveryRequired = true
    const val localTcpTransportRequired = true
    const val shortLivedSixDigitPairingRequired = true
    const val pairingNonceRequired = true
    const val expiringSessionSecretRequired = true
    const val persistentSessionCredentialRequired = true
    const val pairedCommandsReachSharedReducer = true
    const val typedPresentationSnapshotsReachTvInbox = true
    const val phoneControllerUiStillDeferredToTvov23 = false
}


// TVOV23 Android phone TV controller contract.
@Suppress("ConstPropertyName")
object StudyFlowTvPhoneControllerBuildContract {
    const val phoneControllerIntroduced = true
    const val settingsEntryIntroduced = true
    const val productionDiscoveryClientConsumed = true
    const val explicitSixDigitPairingUiRequired = true
    const val persistedSessionResumeRequired = true
    const val allTvDestinationsControllable = true
    const val studySessionControlsRequired = true
    const val questionAnswerControlsRequired = true
    const val mockExamControlsRequired = true
    const val reviewControlsRequired = true
    const val continuityRefreshRequired = true
    const val revokeAndForgetRequired = true
    const val livePresentationSynchronizationDeferredToTvov24 = false
}

// TVOV24 Real cast/state synchronization contract.
@Suppress("ConstPropertyName")
object StudyFlowTvCastSynchronizationBuildContract {
    const val realCastStateSynchronizationIntroduced = true
    const val canonicalPhonePresentationStreamRequired = true
    const val authenticatedTvov22TransportRequired = true
    const val explicitStartStopCastRequired = true
    const val presentationReceiptAcknowledgementRequired = true
    const val reconnectResyncRequired = true
    const val pendingStopRetriesAfterReconnect = true
    const val tvStopCastClearsPresentationInbox = true
}


// TVOV25 Domain truth and persistence convergence contract.
@Suppress("ConstPropertyName")
object StudyFlowTvDomainPersistenceBuildContract {
    const val domainPersistenceConvergenceIntroduced = true
    const val phoneOwnsCanonicalMutations = true
    const val tvRepositoryMutationForbidden = true
    const val canonicalManagersRequired = true
    const val idempotentActionJournalRequired = true
    const val localTvMutationOutboxRequired = true
    const val persistedProjectionRoundTripRequired = true
    const val fakeMockScoringForbidden = true
}


// TVOV26 End-to-end physical-device final seal contract.
@Suppress("ConstPropertyName")
object StudyFlowTvEndToEndPhysicalSealBuildContract {
    const val endToEndPhysicalSealIntroduced = true
    const val requiresTwoRealDevices = true
    const val requiresLeanbackTvAndNonTvPhone = true
    const val optimizedReleaseRuntimeRequired = true
    const val exactInstalledArtifactHashRequired = true
    const val freshPairingRequired = true
    const val authenticatedControlRequired = true
    const val liveCastAcknowledgementRequired = true
    const val canonicalDomainMutationRequired = true
    const val tvRestartResyncRequired = true
    const val phoneRestartResyncRequired = true
    const val revokeBlocksFurtherControl = true
    const val noDeviceSkipForbidden = true
}


// SFLP11 Android TV generic peer generalization contract.
@Suppress("ConstPropertyName")
object StudyFlowTvSflpGeneralizationBuildContract {
    const val androidTvGenericSflpPeerIntroduced = true
    const val androidTvClientKind = "android_tv"
    const val sharedGenericLinkModule = ":core:link"
    const val legacyPhoneTvTransportRemainsCompatibilityAdapter = true
    const val tvCanPairWithAndroidDesktopWebAndWebTv = true
    const val tvPresentationSinkCapabilityRequired = true
    const val tvWorkspaceSyncCapabilityRequired = true
    const val tvDpadInputCapabilityRequired = true
    const val tvPlanWriteCapabilityForbidden = true
    const val androidTvIsNotPhoneHub = true
}

// TVOV27 full native material-viewer contract.
@Suppress("ConstPropertyName")
object StudyFlowTvMaterialViewerBuildContract {
    const val fullNativeMaterialViewerIntroduced = true
    const val textPaginationRequired = true
    const val imageZoomRequired = true
    const val pdfNavigationRequired = true
    const val mediaPlaybackRequired = true
    const val frameworkRendererOnly = true
    const val fileManagementStillDisabledOnTv = true
}

// TVOV28 goal projection and phone-owned mutation contract.
@Suppress("ConstPropertyName")
object StudyFlowTvGoalBuildContract {
    const val goalProjectionIntroduced = true
    const val completePauseResumeVisible = true
    const val evidenceBackedGoalsRemainEvidenceOwned = true
    const val tvRepositoryMutationForbidden = true
    const val canonicalPhoneExecutionRequired = true
}
