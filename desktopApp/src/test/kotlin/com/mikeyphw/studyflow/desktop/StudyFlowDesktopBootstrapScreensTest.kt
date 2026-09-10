package com.mikeyphw.studyflow.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowDesktopBootstrapScreensTest {
    @Test
    fun desktopBootstrapWiresDomainManagersAndSampleState() {
        val appState = StudyFlowDesktopBootstrap.createAppState()
        val dashboard = appState.dashboardState()

        assertEquals(DesktopScreenStatus.CONTENT, dashboard.status)
        assertEquals("Desktop Study Plan", dashboard.activePlanName)
        assertTrue(dashboard.domainManagers.contains("ManageStudyPlan"))
        assertTrue(dashboard.domainManagers.contains("ManageStudyLibrary"))
        assertTrue(dashboard.boundaryModules.contains(":core:model"))
        assertTrue(dashboard.materialCount > 0)
        assertTrue(dashboard.readinessPercent > 0)
    }

    @Test
    fun desktopLibraryCreatesListAndDetailStateFromCoreModels() {
        val appState = StudyFlowDesktopBootstrap.createAppState()
        val library = appState.libraryState()

        assertEquals(DesktopScreenStatus.CONTENT, library.status)
        assertEquals("Desktop Study Plan", library.planName)
        assertFalse(library.materials.isEmpty())
        assertNotNull(appState.selectedMaterialId)
        assertTrue(library.summary.progressPercent in 0..100)
    }

    @Test
    fun navigationStateMovesBetweenDashboardAndLibrary() {
        val appState = StudyFlowDesktopBootstrap.createAppState()

        assertEquals(DesktopRoute.DASHBOARD, appState.selectedRoute)
        appState.selectRoute(DesktopRoute.LIBRARY)
        assertEquals(DesktopRoute.LIBRARY, appState.selectedRoute)
    }

    @Test
    fun emptyRepositoriesRenderEmptyStatesInsteadOfCrashing() {
        val planRepository = InMemoryStudyPlanRepository(emptyList())
        val contentRepository = DesktopStudyContentRepository(DesktopStudyContentState(), planRepository)
        val libraryRepository = InMemoryStudyLibraryRepository(emptyList(), emptyList(), emptyList())
        val scheduleRepository = InMemoryStudyScheduleRepository(emptyList(), emptyList(), emptyList())
        val sessionRepository = InMemoryStudySessionRepository(emptyList())
        val practiceRepository = InMemoryStudyPracticeRepository(emptyList(), emptyList(), emptyList())
        val analyticsRepository = InMemoryStudyAnalyticsRepository(
            plans = planRepository,
            library = libraryRepository,
            schedule = scheduleRepository,
            sessions = sessionRepository,
            practice = practiceRepository,
        )
        val services = DesktopStudyFlowServices(
            planRepository = planRepository,
            contentRepository = contentRepository,
            libraryRepository = libraryRepository,
            scheduleRepository = scheduleRepository,
            sessionRepository = sessionRepository,
            practiceRepository = practiceRepository,
            analyticsRepository = analyticsRepository,
            discoveryRepository = DesktopStudyDiscoveryRepository(
                plans = planRepository,
                library = libraryRepository,
                schedule = scheduleRepository,
                practice = practiceRepository,
                analytics = analyticsRepository,
            ),
            preferencesStore = DesktopPreferencesStore(),
            platformPorts = NoOpDesktopPlatformPorts,
        )
        val appState = DesktopAppState(
            services = services,
            shellState = createDesktopShellState(),
        )

        assertEquals(DesktopScreenStatus.EMPTY, appState.dashboardState().status)
        assertEquals(DesktopScreenStatus.EMPTY, appState.libraryState().status)
        assertEquals(null, appState.selectedMaterialId)
    }
}
