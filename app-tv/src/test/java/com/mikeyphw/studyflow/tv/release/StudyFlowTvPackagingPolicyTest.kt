package com.mikeyphw.studyflow.tv.release

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class StudyFlowTvPackagingPolicyTest {
    @Test
    fun packageIdentityAndTvLauncherContractAreStable() {
        assertEquals("TVOV19", StudyFlowTvPackagingPolicy.overlay)
        assertEquals("com.mikeyphw.studyflow.tv", StudyFlowTvPackagingPolicy.applicationId)
        assertEquals(22, StudyFlowTvPackagingPolicy.versionCode)
        assertEquals("0.1.0-tv22", StudyFlowTvPackagingPolicy.versionName)
        assertTrue(StudyFlowTvPackagingPolicy.backupDisabled)
        assertTrue(StudyFlowTvPackagingPolicy.cleartextTrafficDisabled)
        assertEquals(160, StudyFlowTvPackagingPolicy.bannerWidthDp)
        assertEquals(90, StudyFlowTvPackagingPolicy.bannerHeightDp)
    }

    @Test
    fun devtoolTvTargetRejectsPhoneTasks() {
        assertTrue(StudyFlowTvPackagingPolicy.isAllowedTvTargetTask("verifyStudyFlowTvManifestPackaging"))
        assertTrue(StudyFlowTvPackagingPolicy.isAllowedTvTargetTask(":app-tv:assembleRelease"))
        assertTrue(StudyFlowTvPackagingPolicy.isAllowedTvTargetTask(":core:tvcontrol:test"))
        assertTrue(StudyFlowTvPackagingPolicy.isAllowedTvTargetTask(":core:tvlink:testDebugUnitTest"))
        assertFalse(StudyFlowTvPackagingPolicy.isAllowedTvTargetTask(":app:assembleDebug"))
        assertFalse(StudyFlowTvPackagingPolicy.isAllowedTvTargetTask(":feature:practice:test"))
    }
}
