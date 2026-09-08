package com.mikeyphw.studyflow.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowTvBuildContractTest {
    @Test
    fun minimalTvModuleStartsWithSeparatePackageAndStrictSizeBudget() {
        assertEquals("TVOV01", StudyFlowTvBuildContract.tvOverlay)
        assertEquals("com.mikeyphw.studyflow.tv", StudyFlowTvBuildContract.applicationId)
        assertEquals("com.mikeyphw.studyflow", StudyFlowTvBuildContract.phoneApplicationId)
        assertTrue(StudyFlowTvBuildContract.sizeTargetMiB < StudyFlowTvBuildContract.sizeHardCeilingMiB)
        assertTrue(StudyFlowTvBuildContract.startsWithoutFeatureModules)
        assertTrue(StudyFlowTvBuildContract.startsWithoutCastSdk)
        assertTrue(StudyFlowTvBuildContract.startsWithoutAndroidxTvUi)
        assertTrue(StudyFlowTvBuildContract.pairedPhoneControlRequiredByRoadmap)
        assertEquals(":core:tvcontrol", StudyFlowTvBuildContract.sharedControlProtocolModule)
        assertEquals(1, StudyFlowTvBuildContract.controlProtocolVersion)
        assertTrue(StudyFlowTvBuildContract.castTransportDeferred)
    }

    @Test
    fun tvNavigationFocusAndHomeContractsRemainInstalled() {
        assertTrue(StudyFlowTvBuildContract.dpadFocusPrimitivesIntroduced)
        assertTrue(StudyFlowTvBuildContract.focusPrimitivesRequireNoComposeRuntime)
        assertEquals(64, StudyFlowTvBuildContract.minimumFocusTargetDp)
        assertTrue(StudyFlowTvBuildContract.deterministicInitialFocusRequired)
        assertEquals("TVOV18", StudyFlowTvBuildContract.currentTvOverlay)
        assertTrue(StudyFlowTvBuildContract.navigationShellIntroduced)
        assertTrue(StudyFlowTvBuildContract.navigationShellRequiresNoComposeRuntime)
        assertTrue(StudyFlowTvBuildContract.navigationShellUsesSharedControlProtocol)
        assertEquals(9, StudyFlowTvBuildContract.navigationDestinationCount)
        assertEquals(1, StudyFlowTvBuildContract.secondaryNavigationDestinationCount)
        assertTrue(StudyFlowTvHomeBuildContract.homeSurfaceIntroduced)
        assertTrue(StudyFlowTvHomeBuildContract.homeSurfaceRequiresNoComposeRuntime)
        assertTrue(StudyFlowTvHomeBuildContract.homeSurfaceUsesNavigationShell)
        assertTrue(StudyFlowTvHomeBuildContract.homeSurfaceExposesPhonePairingEntry)
        assertEquals(4, StudyFlowTvHomeBuildContract.homeCardCount)
        assertEquals(3, StudyFlowTvHomeBuildContract.homePrimaryActionCount)
    }

    @Test
    fun todayAndStudySessionContractsAreRealTvSurfaces() {
        assertTrue(StudyFlowTvTodayBuildContract.todayWorkspaceIntroduced)
        assertTrue(StudyFlowTvTodayBuildContract.todayWorkspaceRequiresNoComposeRuntime)
        assertTrue(StudyFlowTvTodayBuildContract.todayWorkspaceUsesNavigationShell)
        assertTrue(StudyFlowTvTodayBuildContract.todayWorkspaceUsesSharedControlProtocol)
        assertTrue(StudyFlowTvTodayBuildContract.nextBlockReviewPracticeAndScheduleVisible)
        assertEquals(4, StudyFlowTvTodayBuildContract.todayBlockCount)
        assertTrue(StudyFlowTvStudySessionBuildContract.studySessionExperienceIntroduced)
        assertTrue(StudyFlowTvStudySessionBuildContract.studySessionRequiresNoComposeRuntime)
        assertTrue(StudyFlowTvStudySessionBuildContract.studySessionUsesSharedControlProtocol)
        assertTrue(StudyFlowTvStudySessionBuildContract.studySessionSupportsRemoteAndPhoneControls)
        assertTrue(StudyFlowTvStudySessionBuildContract.studySessionEmitsCastState)
        assertEquals(4, StudyFlowTvStudySessionBuildContract.studySessionSegmentCount)
        assertEquals(6, StudyFlowTvStudySessionBuildContract.studySessionActionCount)
    }

    @Test
    fun questionPracticeAndMockExamContractsAreRealTvSurfaces() {
        assertTrue(StudyFlowTvQuestionPracticeBuildContract.questionPracticeIntroduced)
        assertTrue(StudyFlowTvQuestionPracticeBuildContract.questionPracticeRequiresNoComposeRuntime)
        assertTrue(StudyFlowTvQuestionPracticeBuildContract.questionPracticeUsesSharedControlProtocol)
        assertTrue(StudyFlowTvQuestionPracticeBuildContract.questionPracticeSupportsRemoteAndPhoneControls)
        assertTrue(StudyFlowTvQuestionPracticeBuildContract.questionPracticeEmitsCastState)
        assertEquals(3, StudyFlowTvQuestionPracticeBuildContract.questionPracticeQuestionCount)
        assertEquals(4, StudyFlowTvQuestionPracticeBuildContract.questionPracticeChoiceCount)
        assertEquals(8, StudyFlowTvQuestionPracticeBuildContract.questionPracticeActionCount)
        assertTrue(StudyFlowTvMockExamBuildContract.mockExamStudioIntroduced)
        assertTrue(StudyFlowTvMockExamBuildContract.mockExamRequiresNoComposeRuntime)
        assertTrue(StudyFlowTvMockExamBuildContract.mockExamUsesSharedControlProtocol)
        assertTrue(StudyFlowTvMockExamBuildContract.mockExamSupportsRemoteAndPhoneControls)
        assertTrue(StudyFlowTvMockExamBuildContract.mockExamEmitsCastState)
        assertEquals(4, StudyFlowTvMockExamBuildContract.mockExamSectionCount)
        assertEquals(8, StudyFlowTvMockExamBuildContract.mockExamActionCount)
    }


    @Test
    fun reviewNotebookAndProgressContractsAreRealTvSurfaces() {
        assertTrue(StudyFlowTvReviewNotebookBuildContract.reviewNotebookIntroduced)
        assertTrue(StudyFlowTvReviewNotebookBuildContract.reviewNotebookRequiresNoComposeRuntime)
        assertTrue(StudyFlowTvReviewNotebookBuildContract.reviewNotebookUsesSharedControlProtocol)
        assertTrue(StudyFlowTvReviewNotebookBuildContract.reviewNotebookSupportsRemoteAndPhoneControls)
        assertTrue(StudyFlowTvReviewNotebookBuildContract.reviewNotebookEmitsCastState)
        assertEquals(4, StudyFlowTvReviewNotebookBuildContract.reviewNotebookItemCount)
        assertEquals(7, StudyFlowTvReviewNotebookBuildContract.reviewNotebookActionCount)
        assertTrue(StudyFlowTvProgressAnalyticsBuildContract.progressAnalyticsIntroduced)
        assertTrue(StudyFlowTvProgressAnalyticsBuildContract.progressAnalyticsRequiresNoComposeRuntime)
        assertTrue(StudyFlowTvProgressAnalyticsBuildContract.progressAnalyticsUsesSharedControlProtocol)
        assertTrue(StudyFlowTvProgressAnalyticsBuildContract.progressAnalyticsSupportsRemoteAndPhoneControls)
        assertTrue(StudyFlowTvProgressAnalyticsBuildContract.progressAnalyticsEmitsCastState)
        assertEquals(4, StudyFlowTvProgressAnalyticsBuildContract.progressAnalyticsMetricCount)
        assertEquals(4, StudyFlowTvProgressAnalyticsBuildContract.progressAnalyticsSubjectCount)
        assertEquals(4, StudyFlowTvProgressAnalyticsBuildContract.progressAnalyticsActionCount)
    }

    @Test
    fun scheduleAndLibraryContractsAreRealTvSurfaces() {
        assertTrue(StudyFlowTvScheduleBuildContract.readOnlyScheduleIntroduced)
        assertTrue(StudyFlowTvScheduleBuildContract.scheduleRequiresNoComposeRuntime)
        assertTrue(StudyFlowTvScheduleBuildContract.scheduleUsesSharedControlProtocol)
        assertTrue(StudyFlowTvScheduleBuildContract.scheduleSupportsRemoteAndPhoneNavigation)
        assertTrue(StudyFlowTvScheduleBuildContract.scheduleEditingDisabledOnTv)
        assertTrue(StudyFlowTvScheduleBuildContract.scheduleEmitsCastState)
        assertEquals(5, StudyFlowTvScheduleBuildContract.scheduleItemCount)
        assertEquals(5, StudyFlowTvScheduleBuildContract.scheduleActionCount)
        assertTrue(StudyFlowTvLibraryBuildContract.limitedLibraryReferenceIntroduced)
        assertTrue(StudyFlowTvLibraryBuildContract.libraryRequiresNoComposeRuntime)
        assertTrue(StudyFlowTvLibraryBuildContract.libraryUsesSharedControlProtocol)
        assertTrue(StudyFlowTvLibraryBuildContract.librarySupportsRemoteAndPhoneNavigation)
        assertTrue(StudyFlowTvLibraryBuildContract.libraryFileManagementDisabledOnTv)
        assertTrue(StudyFlowTvLibraryBuildContract.libraryEmitsCastState)
        assertEquals(4, StudyFlowTvLibraryBuildContract.libraryReferenceCount)
        assertEquals(5, StudyFlowTvLibraryBuildContract.libraryActionCount)
    }

    @Test
    fun settingsAndControlContinuityContractsAreInstalled() {
        assertTrue(StudyFlowTvSettingsBuildContract.minimalTvSettingsIntroduced)
        assertTrue(StudyFlowTvSettingsBuildContract.settingsRequireNoComposeRuntime)
        assertTrue(StudyFlowTvSettingsBuildContract.settingsArePresentationOnly)
        assertTrue(StudyFlowTvSettingsBuildContract.settingsUseSharedControlProtocol)
        assertTrue(StudyFlowTvSettingsBuildContract.accountManagementDisabledOnTv)
        assertTrue(StudyFlowTvSettingsBuildContract.importExportDisabledOnTv)
        assertTrue(StudyFlowTvSettingsBuildContract.pairingRevocationVisible)
        assertTrue(StudyFlowTvSettingsBuildContract.focusAccessibilityPreferenceRepresented)
        assertEquals(4, StudyFlowTvSettingsBuildContract.settingsCardCount)
        assertEquals(4, StudyFlowTvSettingsBuildContract.settingsActionCount)
        assertTrue(StudyFlowTvControlContinuityBuildContract.controlStateContinuityIntroduced)
        assertTrue(StudyFlowTvControlContinuityBuildContract.localAndPairedPhoneUseSameReducer)
        assertTrue(StudyFlowTvControlContinuityBuildContract.controlTransportStillDeferred)
        assertTrue(StudyFlowTvControlContinuityBuildContract.monotonicCommandRevisionRequired)
        assertTrue(StudyFlowTvControlContinuityBuildContract.duplicateAndStaleCommandsRejected)
        assertTrue(StudyFlowTvControlContinuityBuildContract.expiredAndRevokedPairingsRejected)
        assertEquals(10, StudyFlowTvControlContinuityBuildContract.continuitySurfaceCount)
    }


    @Test
    fun accessibilityAndTenFootUiSealIsExplicit() {
        assertTrue(StudyFlowTvAccessibilityBuildContract.accessibilityTenFootSealIntroduced)
        assertTrue(StudyFlowTvAccessibilityBuildContract.nativeAccessibilitySemanticsRequired)
        assertTrue(StudyFlowTvAccessibilityBuildContract.scrollableFocusedContentRequired)
        assertTrue(StudyFlowTvAccessibilityBuildContract.semanticHeadingsRequired)
        assertTrue(StudyFlowTvAccessibilityBuildContract.politeLiveStatusRequired)
        assertTrue(StudyFlowTvAccessibilityBuildContract.highVisibilityFocusAffectsRendering)
        assertEquals(20, StudyFlowTvAccessibilityBuildContract.minimumBodyTextSp)
        assertEquals(18, StudyFlowTvAccessibilityBuildContract.minimumControlTextSp)
        assertEquals(64, StudyFlowTvAccessibilityBuildContract.minimumFocusTargetDp)
    }

    @Test
    fun releaseOptimizationSealRequiresR8ShrinkingAndTargetBudget() {
        assertTrue(StudyFlowTvReleaseOptimizationBuildContract.sizeOptimizationSealIntroduced)
        assertTrue(StudyFlowTvReleaseOptimizationBuildContract.r8Required)
        assertTrue(StudyFlowTvReleaseOptimizationBuildContract.resourceShrinkingRequired)
        assertTrue(StudyFlowTvReleaseOptimizationBuildContract.optimizedDefaultRulesRequired)
        assertTrue(StudyFlowTvReleaseOptimizationBuildContract.mappingEvidenceRequired)
        assertEquals(25, StudyFlowTvReleaseOptimizationBuildContract.releaseTargetMiB)
        assertEquals(35, StudyFlowTvReleaseOptimizationBuildContract.releaseHardCeilingMiB)
    }

}
