package com.mikeyphw.studyflow.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun StudyFlowDesktopApp(
    appState: DesktopAppState = remember { StudyFlowDesktopBootstrap.createAppState() },
) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxSize()) {
                DesktopSidebar(
                    selectedRoute = appState.selectedRoute,
                    onRouteSelected = appState::selectRoute,
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    DesktopTopBar(
                        title = appState.selectedRoute.title,
                        subtitle = appState.shellState.boundarySummary,
                    )
                    when (appState.selectedRoute) {
                        DesktopRoute.DASHBOARD -> DesktopDashboardScreen(
                            state = appState.dashboardState(),
                            onOpenLibrary = { appState.selectRoute(DesktopRoute.LIBRARY) },
                        )

                        DesktopRoute.LIBRARY -> DesktopLibraryScreen(
                            state = appState.libraryState(),
                            selectedMaterialId = appState.selectedMaterialId,
                            onMaterialSelected = appState::selectMaterial,
                        )

                        DesktopRoute.PLANNER -> DesktopPlannerScreen(appState.plannerState())
                        DesktopRoute.PRACTICE -> DesktopPracticeScreen(appState.practiceState())
                        DesktopRoute.ANALYTICS -> DesktopAnalyticsScreen(appState.analyticsState())
                        DesktopRoute.SEARCH -> DesktopSearchScreen(
                            state = appState.searchState(),
                            onQueryChanged = appState::updateSearchQuery,
                        )
                        DesktopRoute.SETTINGS -> DesktopSettingsScreen(
                            state = appState.settingsState(),
                            onCycleTheme = appState::cycleThemeMode,
                            onCycleLanguage = appState::cycleLanguage,
                        )
                    }
                }
            }
        }
    }
}
