package com.mikeyphw.studyflow.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowDesktopBoundaryTest {
    @Test
    fun desktopBoundaryAllowsOnlyJvmSafeSharedModules() {
        assertEquals(
            listOf(":core:model", ":core:domain", ":core:link", ":core:portability"),
            StudyFlowDesktopBoundary.allowedSharedModules,
        )
        assertTrue(StudyFlowDesktopBoundary.isAllowedSharedDependency(":core:model"))
        assertTrue(StudyFlowDesktopBoundary.isAllowedSharedDependency(":core:domain"))
        assertTrue(StudyFlowDesktopBoundary.isAllowedSharedDependency(":core:link"))
        assertTrue(StudyFlowDesktopBoundary.isAllowedSharedDependency(":core:portability"))
        assertFalse(StudyFlowDesktopBoundary.isAllowedSharedDependency(":core:data"))
    }

    @Test
    fun desktopBoundaryRejectsAndroidAndFeatureProjectDependencies() {
        assertTrue(StudyFlowDesktopBoundary.isForbiddenProjectDependency(":app"))
        assertTrue(StudyFlowDesktopBoundary.isForbiddenProjectDependency(":feature:dashboard"))
        assertTrue(StudyFlowDesktopBoundary.isForbiddenProjectDependency(":core:database"))
        assertTrue(StudyFlowDesktopBoundary.isForbiddenProjectDependency(":core:designsystem"))
        assertFalse(StudyFlowDesktopBoundary.isForbiddenProjectDependency(":core:model"))
    }

    @Test
    fun shellStateExposesDesktopReadinessWithoutAndroidAssumptions() {
        val state = createDesktopShellState()

        assertEquals("StudyFlow", state.appName)
        assertEquals("Desktop shell ready", state.status)
        assertEquals(StudyFlowDesktopBoundary.allowedSharedModules, state.allowedSharedModules)
        assertTrue(state.boundarySummary.contains("JVM-safe"))
    }

    @Test
    fun desktopPlatformPortsStartWithSafeNoOpAdapters() {
        assertEquals(null, NoOpDesktopPlatformPorts.fileDialog.chooseImportPath())
        assertEquals(null, NoOpDesktopPlatformPorts.fileDialog.chooseExportPath("studyflow.zip"))
        NoOpDesktopPlatformPorts.notifications.showInfo("StudyFlow", "Desktop shell ready")
    }
}
