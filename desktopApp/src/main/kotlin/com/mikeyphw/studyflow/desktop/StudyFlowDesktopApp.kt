package com.mikeyphw.studyflow.desktop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun StudyFlowDesktopApp(
    appState: DesktopAppState = remember { StudyFlowDesktopBootstrap.createProductionAppState() },
) {
    val language = appState.currentLanguage()
    val textCatalog = remember(language) { DesktopTextCatalog.forLanguage(language) }
    val themeMode = appState.currentThemeMode()
    val darkTheme = resolveDesktopDarkTheme(themeMode, isSystemInDarkTheme())
    val colors = if (darkTheme) darkColors() else lightColors()
    CompositionLocalProvider(LocalDesktopTextCatalog provides textCatalog) {
        MaterialTheme(colors = colors) {
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
                        title = desktopText(appState.selectedRoute.title),
                        subtitle = appState.shellState.boundarySummary,
                    )
                    when (appState.selectedRoute) {
                        DesktopRoute.DASHBOARD -> DesktopDashboardScreen(
                            state = appState.dashboardState(),
                            onOpenLibrary = { appState.selectRoute(DesktopRoute.LIBRARY) },
                        )

                        DesktopRoute.PLAN -> DesktopPlanContentScreen(appState)
                        DesktopRoute.LIBRARY -> DesktopLibraryScreen(appState)

                        DesktopRoute.PLANNER -> DesktopPlannerScreen(appState)
                        DesktopRoute.PRACTICE -> DesktopPracticeScreen(appState)
                        DesktopRoute.REVIEWS -> DesktopReviewScreen(appState)
                        DesktopRoute.WRITING -> DesktopWritingWorkspaceScreen(appState)
                        DesktopRoute.MODES -> DesktopContentModeScreen(appState)
                        DesktopRoute.PUBLIC_EXAM -> DesktopPublicExamScreen(appState)
                        DesktopRoute.ANALYTICS -> DesktopAnalyticsScreen(
                            state = appState.analyticsState(),
                            progress = appState.progressReadinessState(),
                            actionMessage = appState.progressActionMessage,
                            goalTitle = appState.goalTitleDraft,
                            goalTarget = appState.goalTargetDraft,
                            goalType = appState.goalTypeDraft,
                            goalPeriod = appState.goalPeriodDraft,
                            goalActionMessage = appState.goalActionMessage,
                            onGoalTitleChanged = appState::updateGoalTitleDraft,
                            onGoalTargetChanged = appState::updateGoalTargetDraft,
                            onCycleGoalType = appState::cycleGoalType,
                            onCycleGoalPeriod = appState::cycleGoalPeriod,
                            onCreateGoal = appState::createGoal,
                            onIncrementGoal = appState::incrementGoal,
                            onArchiveGoal = appState::archiveGoal,
                            onRestoreGoal = appState::restoreGoal,
                            onDeleteGoal = appState::deleteGoal,
                            onApplySprint = appState::applyStudySprint,
                            onExportReport = appState::exportExamReport,
                        )
                        DesktopRoute.SEARCH -> DesktopSearchScreen(
                            state = appState.searchState(),
                            onQueryChanged = appState::updateSearchQuery,
                        )
                        DesktopRoute.SETTINGS -> DesktopSettingsScreen(
                            state = appState.settingsState(),
                            onCycleTheme = appState::cycleThemeMode,
                            onCycleLanguage = appState::cycleLanguage,
                            onToggleReminders = appState::toggleDesktopReminders,
                            onToggleNativeNotifications = appState::toggleDesktopNativeNotifications,
                            onToggleTrayDashboard = appState::toggleDesktopTrayDashboard,
                            onToggleReminderPause = appState::toggleDesktopReminderPause,
                            onReconcileAttention = appState::reconcileDesktopAttention,
                            onTestNotification = appState::testDesktopNotification,
                            onToggleReminderCategory = appState::toggleDesktopReminderCategory,
                            linkState = appState.linkState(),
                            linkQrInput = appState.linkQrInput,
                            linkManualEndpoint = appState.linkManualEndpoint,
                            linkActionMessage = appState.linkActionMessage,
                            onCreatePairingOffer = appState::createLinkPairingOffer,
                            onLinkQrChanged = appState::updateLinkQrInput,
                            onPairFromQr = appState::pairLinkFromQr,
                            onManualEndpointChanged = appState::updateLinkManualEndpoint,
                            onRememberEndpoint = appState::rememberLinkEndpoint,
                            onSyncPeers = appState::syncLinkPeers,
                            onRevokePeer = appState::revokeLinkPeer,
                            onForgetPeer = appState::forgetLinkPeer,
                            portabilityPath = appState.portabilityPathDraft,
                            portabilityPassword = appState.portabilityPasswordDraft,
                            portabilityActionMessage = appState.portabilityActionMessage,
                            onPortabilityPathChanged = appState::updatePortabilityPath,
                            onPortabilityPasswordChanged = appState::updatePortabilityPassword,
                            onChoosePortabilityImport = appState::choosePortabilityImportFile,
                            onChoosePortabilityExport = appState::choosePortabilityExportFile,
                            onExportPortable = appState::exportPortableStructure,
                            onImportPortable = appState::importPortableStructure,
                            onCreateEncryptedBackup = appState::createEncryptedWorkspaceBackup,
                            onRestoreEncryptedBackup = appState::restoreEncryptedWorkspaceBackup,
                            onMigrateWorkspace = appState::migrateDesktopWorkspace,
                        )
                    }
                }
            }
        }
    }
}
}
