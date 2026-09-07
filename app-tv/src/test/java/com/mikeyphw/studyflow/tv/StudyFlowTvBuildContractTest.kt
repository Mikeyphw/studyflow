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
    }
}
