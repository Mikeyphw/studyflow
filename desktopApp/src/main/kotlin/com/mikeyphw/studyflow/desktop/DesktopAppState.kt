package com.mikeyphw.studyflow.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mikeyphw.studyflow.core.model.AppLanguage
import com.mikeyphw.studyflow.core.model.StudyMaterialId
import com.mikeyphw.studyflow.core.model.ThemeMode

class DesktopAppState(
    val services: DesktopStudyFlowServices,
    val shellState: DesktopShellState,
) {
    var selectedRoute: DesktopRoute by mutableStateOf(DesktopRoute.DASHBOARD)
        private set

    var selectedMaterialId: StudyMaterialId? by mutableStateOf(
        services.defaultPlanId?.let { planId ->
            services.libraryRepository.currentMaterials(planId).firstOrNull()?.id
        },
    )
        private set

    var searchQuery: String by mutableStateOf("compose")
        private set

    fun selectRoute(route: DesktopRoute) {
        selectedRoute = route
    }

    fun selectMaterial(materialId: StudyMaterialId) {
        selectedMaterialId = materialId
    }

    fun updateSearchQuery(query: String) {
        searchQuery = query
    }

    fun cycleThemeMode() {
        val next = when (settingsState().themeMode) {
            ThemeMode.SYSTEM_DEFAULT -> ThemeMode.LIGHT
            ThemeMode.LIGHT -> ThemeMode.DARK
            ThemeMode.DARK -> ThemeMode.SYSTEM_DEFAULT
        }
        services.setTheme(next)
    }

    fun cycleLanguage() {
        val next = when (settingsState().language) {
            AppLanguage.SYSTEM_DEFAULT -> AppLanguage.ENGLISH
            AppLanguage.ENGLISH -> AppLanguage.PORTUGUESE_BRAZIL
            AppLanguage.PORTUGUESE_BRAZIL -> AppLanguage.SYSTEM_DEFAULT
        }
        services.setLanguage(next)
    }

    fun dashboardState(): DesktopDashboardState = services.createDashboardState()

    fun libraryState(): DesktopLibraryScreenState = services.createLibraryState()

    fun plannerState(): DesktopPlannerScreenState = services.createPlannerState()

    fun practiceState(): DesktopPracticeScreenState = services.createPracticeState()

    fun analyticsState(): DesktopAnalyticsScreenState = services.createAnalyticsState()

    fun searchState(): DesktopSearchScreenState = services.createSearchState(searchQuery)

    fun settingsState(): DesktopSettingsScreenState = services.createSettingsState()
}

enum class DesktopRoute(val title: String) {
    DASHBOARD("Dashboard"),
    LIBRARY("Library"),
    PLANNER("Planner"),
    PRACTICE("Practice"),
    ANALYTICS("Analytics"),
    SEARCH("Search"),
    SETTINGS("Settings"),
}

enum class DesktopScreenStatus {
    LOADING,
    CONTENT,
    EMPTY,
    ERROR,
}
