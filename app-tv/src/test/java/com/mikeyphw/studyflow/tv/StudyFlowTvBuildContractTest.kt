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
        assertEquals("TVOV08", StudyFlowTvBuildContract.currentTvOverlay)
        assertTrue(StudyFlowTvBuildContract.navigationShellIntroduced)
        assertTrue(StudyFlowTvBuildContract.navigationShellRequiresNoComposeRuntime)
        assertTrue(StudyFlowTvBuildContract.navigationShellUsesSharedControlProtocol)
        assertEquals(8, StudyFlowTvBuildContract.navigationDestinationCount)
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
}
