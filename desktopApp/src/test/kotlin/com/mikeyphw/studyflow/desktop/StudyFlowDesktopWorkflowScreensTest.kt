package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.model.AppPreferences
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowDesktopWorkflowScreensTest {
    @Test
    fun desktopPlannerAndPracticeStatesExposeDomainBackedData() {
        val appState = StudyFlowDesktopBootstrap.createAppState()
        val planner = appState.plannerState()
        val practice = appState.practiceState()

        assertEquals(DesktopScreenStatus.CONTENT, planner.status)
        assertTrue(planner.plannedActivities.isNotEmpty())
        assertTrue(planner.totalPlannedMinutes > 0)
        assertTrue(planner.nextActivityTitle.isNotBlank())
        assertEquals(DesktopScreenStatus.CONTENT, practice.status)
        assertTrue(practice.summary.questionCount >= 3)
        assertTrue(practice.deck.isNotEmpty())
        assertTrue(practice.tests.isNotEmpty())
    }

    @Test
    fun desktopAnalyticsSearchAndSettingsUseCoreContracts() {
        val appState = StudyFlowDesktopBootstrap.createAppState()
        val analytics = appState.analyticsState()

        assertEquals(DesktopScreenStatus.CONTENT, analytics.status)
        assertTrue(analytics.insights.isNotEmpty())
        assertTrue(analytics.weakAreas.isNotEmpty())
        assertTrue(analytics.libraryProgressPercent in 0..100)

        appState.updateSearchQuery("compose")
        val search = appState.searchState()
        assertEquals(DesktopScreenStatus.CONTENT, search.status)
        assertFalse(search.results.isEmpty())
        assertFalse(search.recommendations.isEmpty())

        val settings = appState.settingsState()
        assertEquals(DesktopScreenStatus.CONTENT, settings.status)
        assertTrue(settings.boundaryModules.contains(":core:domain"))
        assertTrue(settings.deferredDesktopIntegrations.contains("Native file pickers"))
    }

    @Test
    fun desktopRoutesIncludeWorkflowScreens() {
        val routes = DesktopRoute.entries.map(DesktopRoute::name)

        assertTrue(routes.contains("PLANNER"))
        assertTrue(routes.contains("PRACTICE"))
        assertTrue(routes.contains("ANALYTICS"))
        assertTrue(routes.contains("SEARCH"))
        assertTrue(routes.contains("SETTINGS"))
    }

    @Test
    fun emptyWorkflowRepositoriesRenderEmptyStates() {
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
        val discoveryRepository = DesktopStudyDiscoveryRepository(
            plans = planRepository,
            library = libraryRepository,
            schedule = scheduleRepository,
            practice = practiceRepository,
            analytics = analyticsRepository,
        )
        val services = DesktopStudyFlowServices(
            planRepository = planRepository,
            contentRepository = contentRepository,
            libraryRepository = libraryRepository,
            scheduleRepository = scheduleRepository,
            sessionRepository = sessionRepository,
            practiceRepository = practiceRepository,
            analyticsRepository = analyticsRepository,
            discoveryRepository = discoveryRepository,
            preferencesStore = DesktopPreferencesStore(AppPreferences(dynamicColorEnabled = false)),
            platformPorts = NoOpDesktopPlatformPorts,
        )
        val appState = DesktopAppState(services, createDesktopShellState())

        assertEquals(DesktopScreenStatus.EMPTY, appState.plannerState().status)
        assertEquals(DesktopScreenStatus.EMPTY, appState.practiceState().status)
        assertEquals(DesktopScreenStatus.EMPTY, appState.analyticsState().status)
        assertEquals(DesktopScreenStatus.EMPTY, appState.searchState().status)
        assertEquals(DesktopScreenStatus.CONTENT, appState.settingsState().status)
    }

    @Test
    fun manageDiscoveryIsCallableThroughDesktopServices() = runBlocking {
        val services = StudyFlowDesktopBootstrap.createAppState().services
        val results = services.manageStudyDiscovery.search("kotlin")

        assertTrue(results.any { result -> result.title.contains("Kotlin", ignoreCase = true) })
    }
}
