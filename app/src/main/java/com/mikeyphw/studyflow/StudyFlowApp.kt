package com.mikeyphw.studyflow

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.mikeyphw.studyflow.core.designsystem.StudyFlowErrorPane
import com.mikeyphw.studyflow.core.designsystem.StudyFlowLoadingPane
import com.mikeyphw.studyflow.core.designsystem.StudyFlowTheme
import com.mikeyphw.studyflow.core.diagnostics.DebugProbeId
import com.mikeyphw.studyflow.core.diagnostics.DebugProbeReport
import com.mikeyphw.studyflow.core.diagnostics.DebugProbeReportPolicy
import com.mikeyphw.studyflow.core.diagnostics.DebugProbeResult
import com.mikeyphw.studyflow.core.diagnostics.DebugProbeStatus
import com.mikeyphw.studyflow.core.diagnostics.DebugWorkbenchShellPolicy
import com.mikeyphw.studyflow.core.diagnostics.DiagnosticTimelineSnapshot
import com.mikeyphw.studyflow.core.diagnostics.ReminderLabActionResult
import com.mikeyphw.studyflow.core.diagnostics.RuntimeSelfCheckHistory
import com.mikeyphw.studyflow.core.diagnostics.SupportBundleSealReport
import com.mikeyphw.studyflow.core.diagnostics.ReminderNotificationLabReport
import com.mikeyphw.studyflow.core.diagnostics.DatabaseHealthLabReport
import com.mikeyphw.studyflow.core.diagnostics.DatabasePortabilityLabReport
import com.mikeyphw.studyflow.core.diagnostics.DatabasePortabilityLabActionResult
import com.mikeyphw.studyflow.core.diagnostics.DebugLabStatus
import com.mikeyphw.studyflow.core.diagnostics.StudyEngineLabActionResult
import com.mikeyphw.studyflow.core.diagnostics.StudyEngineLabMessage
import com.mikeyphw.studyflow.core.diagnostics.StudyEngineLabReport
import com.mikeyphw.studyflow.core.domain.GlobalSearchDestination
import com.mikeyphw.studyflow.core.domain.GlobalSearchResult
import com.mikeyphw.studyflow.core.domain.OnboardingSamplePlanBlueprint
import com.mikeyphw.studyflow.core.domain.OnboardingSamplePlanInput
import com.mikeyphw.studyflow.core.domain.OnboardingSamplePlanTemplate
import com.mikeyphw.studyflow.core.domain.OnboardingSampleSubjectKind
import com.mikeyphw.studyflow.core.domain.OnboardingSampleTopicKind
import com.mikeyphw.studyflow.core.domain.StudyOnboardingSamplePlanWizard
import com.mikeyphw.studyflow.core.domain.StudyRecommendation
import com.mikeyphw.studyflow.core.domain.StudyRecommendationDestination
import com.mikeyphw.studyflow.core.domain.destination
import com.mikeyphw.studyflow.core.domain.asStudyFlowLoadState
import com.mikeyphw.studyflow.core.domain.asStudyFlowOptionalLoadState
import com.mikeyphw.studyflow.core.i18n.AppLocaleController
import com.mikeyphw.studyflow.core.model.AppPreferences
import com.mikeyphw.studyflow.core.model.ContentProgressStatus
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudyFlowLoadState
import com.mikeyphw.studyflow.core.model.Subject
import com.mikeyphw.studyflow.core.model.SubjectId
import com.mikeyphw.studyflow.core.model.Topic
import com.mikeyphw.studyflow.core.model.TopicId
import com.mikeyphw.studyflow.core.model.StudyPlanStatus
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyPlanSettings
import com.mikeyphw.studyflow.core.model.ThemeMode
import com.mikeyphw.studyflow.core.navigation.AnalyticsRoute
import com.mikeyphw.studyflow.core.navigation.CreatePlanRoute
import com.mikeyphw.studyflow.core.navigation.DashboardRoute
import com.mikeyphw.studyflow.core.navigation.DebugWorkbenchRoute
import com.mikeyphw.studyflow.core.navigation.EditPlanRoute
import com.mikeyphw.studyflow.core.navigation.LibraryRoute
import com.mikeyphw.studyflow.core.navigation.OnboardingRoute
import com.mikeyphw.studyflow.core.navigation.PlanListRoute
import com.mikeyphw.studyflow.core.navigation.PlanPortabilityRoute
import com.mikeyphw.studyflow.core.navigation.PlanSettingsRoute
import com.mikeyphw.studyflow.core.navigation.ProgressWorkspaceRoute
import com.mikeyphw.studyflow.core.navigation.PracticeWorkspaceTab
import com.mikeyphw.studyflow.core.navigation.PracticeWorkspaceRoute
import com.mikeyphw.studyflow.core.navigation.PlanWorkspaceTab
import com.mikeyphw.studyflow.core.navigation.PlanWorkspaceRoute
import com.mikeyphw.studyflow.core.navigation.PracticeRoute
import com.mikeyphw.studyflow.core.navigation.PrivacyRoute
import com.mikeyphw.studyflow.core.navigation.ProgressRoute
import com.mikeyphw.studyflow.core.navigation.RemindersRoute
import com.mikeyphw.studyflow.core.navigation.RuntimeSmokeRoute
import com.mikeyphw.studyflow.core.navigation.ReviewsRoute
import com.mikeyphw.studyflow.core.navigation.ScheduleRoute
import com.mikeyphw.studyflow.core.navigation.SearchRoute
import com.mikeyphw.studyflow.core.navigation.SessionsRoute
import com.mikeyphw.studyflow.core.navigation.SettingsRoute
import com.mikeyphw.studyflow.core.navigation.SyncRoute
import com.mikeyphw.studyflow.core.navigation.SubjectsRoute
import com.mikeyphw.studyflow.core.navigation.TodayRoute
import com.mikeyphw.studyflow.core.domain.RuntimeSmokeWorkbench
import com.mikeyphw.studyflow.core.domain.ReminderNotificationType
import com.mikeyphw.studyflow.core.model.RuntimeSmokeProbeKind
import com.mikeyphw.studyflow.core.portability.StudyPlanExportMode
import com.mikeyphw.studyflow.core.portability.StudyPlanExportOptions
import com.mikeyphw.studyflow.core.portability.StudyPlanImportStrategy
import com.mikeyphw.studyflow.reminders.StudyFlowWidgetUpdateCoordinator
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import com.mikeyphw.studyflow.feature.analytics.StudyAnalyticsScreen
import com.mikeyphw.studyflow.feature.dashboard.DashboardScreen
import com.mikeyphw.studyflow.feature.library.StudyLibraryScreen
import com.mikeyphw.studyflow.feature.plans.PlanEditorForm
import com.mikeyphw.studyflow.feature.plans.PlanEditorScreen
import com.mikeyphw.studyflow.feature.plans.OnboardingSamplePlanWizardScreen
import com.mikeyphw.studyflow.feature.plans.PlanListScreen
import com.mikeyphw.studyflow.feature.plans.PlanPortabilityScreen
import com.mikeyphw.studyflow.feature.plans.PlanSettingsScreen
import com.mikeyphw.studyflow.feature.plans.planDescriptionRes
import com.mikeyphw.studyflow.feature.plans.planNameRes
import com.mikeyphw.studyflow.feature.plans.titleRes
import com.mikeyphw.studyflow.feature.plans.StudyPlanAction
import com.mikeyphw.studyflow.feature.practice.PracticeScreen
import com.mikeyphw.studyflow.feature.privacy.PrivacyCenterScreen
import com.mikeyphw.studyflow.feature.progress.StudyProgressScreen
import com.mikeyphw.studyflow.feature.reminders.StudyRemindersScreen
import com.mikeyphw.studyflow.feature.reviews.StudyReviewScreen
import com.mikeyphw.studyflow.feature.schedule.StudyScheduleScreen
import com.mikeyphw.studyflow.feature.search.StudySearchScreen
import com.mikeyphw.studyflow.feature.sessions.StudySessionScreen
import com.mikeyphw.studyflow.feature.settings.DebugWorkbenchActionResult
import com.mikeyphw.studyflow.feature.settings.DebugProbePanel
import com.mikeyphw.studyflow.feature.settings.DebugWorkbenchScreen
import com.mikeyphw.studyflow.feature.settings.DebugLabsPanel
import com.mikeyphw.studyflow.feature.settings.RuntimeSmokeWorkbenchScreen
import com.mikeyphw.studyflow.feature.settings.SettingsScreen
import com.mikeyphw.studyflow.feature.settings.SyncFoundationScreen
import com.mikeyphw.studyflow.feature.subjects.StudyContentScreen
import java.util.UUID
import java.util.concurrent.CancellationException
import kotlinx.coroutines.launch

private val MediumNavigationWidth = 600.dp
private val ExpandedNavigationWidth = 1000.dp

@Composable
private fun LoadedPlanDestination(
    container: AppContainer,
    planId: StudyPlanId,
    modifier: Modifier = Modifier,
    content: @Composable (StudyPlan) -> Unit,
) {
    var loadAttempt by remember(planId) { mutableIntStateOf(0) }
    val planFlow = remember(planId, loadAttempt) {
        container.manageStudyPlan.observePlan(planId).asStudyFlowOptionalLoadState()
    }
    val planState by planFlow.collectAsStateWithLifecycle(
        initialValue = StudyFlowLoadState.Loading,
    )

    when (val current = planState) {
        StudyFlowLoadState.Loading -> StudyFlowLoadingPane(modifier = modifier)
        StudyFlowLoadState.Missing -> {
            StudyFlowErrorPane(
                message = stringResource(R.string.unknown_plan_error),
                onRetry = null,
                modifier = modifier,
            )
        }
        is StudyFlowLoadState.Failed -> {
            StudyFlowErrorPane(
                message = stringResource(R.string.app_initialization_failed),
                onRetry = { loadAttempt += 1 },
                modifier = modifier,
            )
        }
        is StudyFlowLoadState.Ready -> {
            if (current.value.status in setOf(StudyPlanStatus.ARCHIVED, StudyPlanStatus.TRASHED)) {
                StudyFlowErrorPane(
                    message = stringResource(R.string.unknown_plan_error),
                    onRetry = null,
                    modifier = modifier,
                )
            } else {
                content(current.value)
            }
        }
    }
}

@Composable
private fun LoadedPlanSettingsDestination(
    container: AppContainer,
    planId: StudyPlanId,
    modifier: Modifier = Modifier,
    content: @Composable (StudyPlanSettings) -> Unit,
) {
    var loadAttempt by remember(planId) { mutableIntStateOf(0) }
    val settingsFlow = remember(planId, loadAttempt) {
        container.manageStudyPlan.observeSettings(planId).asStudyFlowOptionalLoadState()
    }
    val settingsState by settingsFlow.collectAsStateWithLifecycle(
        initialValue = StudyFlowLoadState.Loading,
    )
    when (val current = settingsState) {
        StudyFlowLoadState.Loading -> StudyFlowLoadingPane(modifier = modifier)
        StudyFlowLoadState.Missing -> StudyFlowErrorPane(
            message = stringResource(R.string.app_initialization_failed),
            onRetry = { loadAttempt += 1 },
            modifier = modifier,
        )
        is StudyFlowLoadState.Failed -> StudyFlowErrorPane(
            message = stringResource(R.string.app_initialization_failed),
            onRetry = { loadAttempt += 1 },
            modifier = modifier,
        )
        is StudyFlowLoadState.Ready -> content(current.value)
    }
}

@Composable
fun StudyFlowApp(
    container: AppContainer,
    modifier: Modifier = Modifier,
    reminderLaunch: StudyReminderLaunch? = null,
    onReminderLaunchConsumed: () -> Unit = {},
) {
    val initializationState by container.initializationState.collectAsStateWithLifecycle()
    if (initializationState is AppInitializationState.Initializing) {
        StudyFlowTheme(
            themeMode = ThemeMode.SYSTEM_DEFAULT,
            dynamicColorEnabled = true,
        ) {
            StudyFlowLoadingPane(modifier = modifier)
        }
        return
    }
    if (initializationState is AppInitializationState.Failed) {
        StudyFlowTheme(
            themeMode = ThemeMode.SYSTEM_DEFAULT,
            dynamicColorEnabled = true,
        ) {
            StudyFlowErrorPane(
                message = stringResource(R.string.app_initialization_failed),
                onRetry = container::retryInitialization,
                modifier = modifier,
            )
        }
        return
    }

    var rootLoadAttempt by remember { mutableIntStateOf(0) }
    val preferencesFlow = remember(rootLoadAttempt) {
        container.appPreferencesRepository.preferences.asStudyFlowLoadState()
    }
    val plansFlow = remember(rootLoadAttempt) {
        container.manageStudyPlan.plans.asStudyFlowLoadState()
    }
    val syncFlow = remember(rootLoadAttempt) {
        container.manageStudyCloudSync.syncState.asStudyFlowLoadState()
    }
    val preferencesState by preferencesFlow.collectAsStateWithLifecycle(
        initialValue = StudyFlowLoadState.Loading,
    )
    val plansState by plansFlow.collectAsStateWithLifecycle(
        initialValue = StudyFlowLoadState.Loading,
    )
    val syncStateResult by syncFlow.collectAsStateWithLifecycle(
        initialValue = StudyFlowLoadState.Loading,
    )
    val rootStates = listOf(preferencesState, plansState, syncStateResult)
    if (rootStates.any { state -> state is StudyFlowLoadState.Failed }) {
        StudyFlowTheme(
            themeMode = ThemeMode.SYSTEM_DEFAULT,
            dynamicColorEnabled = true,
        ) {
            StudyFlowErrorPane(
                message = stringResource(R.string.app_initialization_failed),
                onRetry = { rootLoadAttempt += 1 },
                modifier = modifier,
            )
        }
        return
    }
    if (rootStates.any { state -> state !is StudyFlowLoadState.Ready<*> }) {
        StudyFlowTheme(
            themeMode = ThemeMode.SYSTEM_DEFAULT,
            dynamicColorEnabled = true,
        ) {
            StudyFlowLoadingPane(modifier = modifier)
        }
        return
    }

    val readyPreferences = (preferencesState as StudyFlowLoadState.Ready<AppPreferences>).value
    val readyPlans = (plansState as StudyFlowLoadState.Ready<List<StudyPlan>>).value
    val readySyncState =
        (syncStateResult as StudyFlowLoadState.Ready<com.mikeyphw.studyflow.core.model.StudySyncState>).value

    LaunchedEffect(readyPreferences.language) {
        if (AppLocaleController.currentLanguage() != readyPreferences.language) {
            AppLocaleController.apply(readyPreferences.language)
        }
    }

    StudyFlowTheme(
        themeMode = readyPreferences.themeMode,
        dynamicColorEnabled = readyPreferences.dynamicColorEnabled,
    ) {
        StudyFlowNavigation(
            container = container,
            plans = readyPlans,
            preferences = readyPreferences,
            syncState = readySyncState,
            modifier = modifier,
            reminderLaunch = reminderLaunch,
            onReminderLaunchConsumed = onReminderLaunchConsumed,
        )
    }
}

@Composable
private fun StudyFlowNavigation(
    container: AppContainer,
    plans: List<StudyPlan>,
    preferences: AppPreferences,
    syncState: com.mikeyphw.studyflow.core.model.StudySyncState,
    modifier: Modifier = Modifier,
    reminderLaunch: StudyReminderLaunch? = null,
    onReminderLaunchConsumed: () -> Unit = {},
) {
    StudyFlowV2Navigation(
        container = container,
        plans = plans,
        preferences = preferences,
        syncState = syncState,
        modifier = modifier,
        reminderLaunch = reminderLaunch,
        onReminderLaunchConsumed = onReminderLaunchConsumed,
    )
}

@Composable
internal fun StudyFlowNavHost(
    navController: NavHostController,
    container: AppContainer,
    plans: List<StudyPlan>,
    preferences: AppPreferences,
    syncState: com.mikeyphw.studyflow.core.model.StudySyncState,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val copySuffix = stringResource(R.string.plan_copy_suffix)
    val debugClipboardLabel = stringResource(R.string.debug_clipboard_label)
    val debugBundleChooserTitle = stringResource(R.string.debug_bundle_chooser_title)

    NavHost(
        navController = navController,
        startDestination = TodayRoute(),
        modifier = modifier.fillMaxSize(),
    ) {
        studyFlowV2Destinations(
            navController = navController,
            container = container,
            plans = plans,
            preferences = preferences,
        )
        composable<PlanListRoute> {
            PlanListScreen(
                plans = plans,
                defaultPlanId = preferences.defaultStudyPlanId?.takeIf { defaultId ->
                    plans.any { plan ->
                        plan.id == defaultId && plan.status !in setOf(StudyPlanStatus.ARCHIVED, StudyPlanStatus.TRASHED)
                    }
                },
                onPlanSelected = { plan -> navController.navigate(DashboardRoute(plan.id.value)) },
                onCreatePlan = { navController.navigate(CreatePlanRoute) },
                onImportPlan = { navController.navigate(PlanPortabilityRoute()) },
                onStartSamplePlan = { navController.navigate(OnboardingRoute) },
                onPlanAction = { plan, action ->
                    when (action) {
                        StudyPlanAction.EDIT -> navController.navigate(EditPlanRoute(plan.id.value))
                        StudyPlanAction.PORTABILITY -> navController.navigate(PlanPortabilityRoute(plan.id.value))
                        StudyPlanAction.DUPLICATE -> coroutineScope.launch {
                            val duplicate = container.manageStudyPlan.duplicate(
                                sourcePlanId = plan.id,
                                newPlanId = StudyPlanId(UUID.randomUUID().toString()),
                                newName = "${plan.name} $copySuffix",
                            )
                            navController.navigate(DashboardRoute(duplicate.id.value))
                        }
                        StudyPlanAction.SET_DEFAULT -> coroutineScope.launch {
                            container.appPreferencesRepository.setDefaultStudyPlan(plan.id)
                        }
                        StudyPlanAction.CLEAR_DEFAULT -> coroutineScope.launch {
                            container.appPreferencesRepository.setDefaultStudyPlan(null)
                        }
                        StudyPlanAction.ACTIVATE -> coroutineScope.launch {
                            container.manageStudyPlan.activate(plan.id)
                            container.reminderDeliveryCoordinator.reconcilePlan(plan.id)
                        }
                        StudyPlanAction.PAUSE -> coroutineScope.launch {
                            container.manageStudyPlan.pause(plan.id)
                            container.reminderDeliveryCoordinator.reconcilePlan(plan.id)
                        }
                        StudyPlanAction.ARCHIVE -> coroutineScope.launch {
                            container.manageStudyPlan.archive(plan.id)
                            container.appPreferencesRepository.clearDefaultStudyPlanIf(plan.id)
                            container.reminderDeliveryCoordinator.reconcilePlan(plan.id)
                        }
                        StudyPlanAction.RESTORE_ARCHIVED -> coroutineScope.launch {
                            container.manageStudyPlan.restoreArchived(plan.id)
                            container.reminderDeliveryCoordinator.reconcilePlan(plan.id)
                        }
                        StudyPlanAction.MOVE_TO_TRASH -> coroutineScope.launch {
                            container.manageStudyPlan.moveToTrash(plan.id)
                            container.appPreferencesRepository.clearDefaultStudyPlanIf(plan.id)
                            container.reminderDeliveryCoordinator.reconcilePlan(plan.id)
                        }
                        StudyPlanAction.RESTORE_FROM_TRASH -> coroutineScope.launch {
                            container.manageStudyPlan.restoreFromTrash(plan.id)
                            container.reminderDeliveryCoordinator.reconcilePlan(plan.id)
                        }
                        StudyPlanAction.DELETE_PERMANENTLY -> coroutineScope.launch {
                            container.manageStudyPlan.deletePermanently(plan.id)
                            container.appPreferencesRepository.clearDefaultStudyPlanIf(plan.id)
                            container.reminderDeliveryCoordinator.reconcilePlan(plan.id)
                        }
                    }
                },
            )
        }
        composable<CreatePlanRoute> {
            PlanEditorScreen(
                initialPlan = null,
                initialSettings = null,
                onBack = navController::popBackStack,
                onSave = { form ->
                    val plan = form.toNewStudyPlan()
                    coroutineScope.launch {
                        container.createStudyPlan(plan, form.toSettings(plan.id))
                        container.reminderDeliveryCoordinator.reconcilePlan(plan.id)
                        navController.navigate(DashboardRoute(plan.id.value)) {
                            popUpTo<PlanListRoute>()
                        }
                    }
                },
            )
        }
        composable<OnboardingRoute> {
            val provisioningState by container.samplePlanProvisioner.state.collectAsStateWithLifecycle()
            LaunchedEffect(provisioningState) {
                val completed = provisioningState as? SamplePlanProvisioningState.Completed
                    ?: return@LaunchedEffect
                container.reminderDeliveryCoordinator.reconcilePlan(completed.planId)
                container.samplePlanProvisioner.consumeCompletion(completed.planId)
                navController.navigate(DashboardRoute(completed.planId.value)) {
                    popUpTo<PlanListRoute>()
                }
            }
            OnboardingSamplePlanWizardScreen(
                onBack = navController::popBackStack,
                creating = provisioningState is SamplePlanProvisioningState.InFlight,
                creationFailed = provisioningState is SamplePlanProvisioningState.Failed,
                onCreateSample = { template ->
                    val blueprint = StudyOnboardingSamplePlanWizard.build(
                        OnboardingSamplePlanInput(template = template),
                    )
                    val resources = context.resources
                    container.samplePlanProvisioner.clearFailure()
                    container.samplePlanProvisioner.startProvisioning(
                        blueprint = blueprint,
                        planName = resources.getString(template.planNameRes()),
                        planDescription = resources.getString(template.planDescriptionRes()),
                        subjectNames = localizedSubjectNames(resources),
                        topicNames = localizedTopicNames(resources),
                    )
                },
            )
        }
        composable<EditPlanRoute> { entry ->
            val route = entry.toRoute<EditPlanRoute>()
            val planId = remember(route.planId) { StudyPlanId(route.planId) }
            LoadedPlanDestination(container = container, planId = planId) { currentPlan ->
                LoadedPlanSettingsDestination(container = container, planId = planId) { settings ->
                    PlanEditorScreen(
                        initialPlan = currentPlan,
                        initialSettings = settings,
                        onBack = navController::popBackStack,
                        onSave = { form ->
                            coroutineScope.launch {
                                container.manageStudyPlan.update(currentPlan.withEditorForm(form))
                                container.manageStudyPlan.updateSettings(form.toSettings(currentPlan.id, settings))
                                container.reminderDeliveryCoordinator.reconcilePlan(currentPlan.id)
                                navController.popBackStack()
                            }
                        },
                    )
                }
            }
        }
        composable<PlanSettingsRoute> { entry ->
            val route = entry.toRoute<PlanSettingsRoute>()
            val planId = remember(route.planId) { StudyPlanId(route.planId) }
            LoadedPlanDestination(container = container, planId = planId) { currentPlan ->
                LoadedPlanSettingsDestination(container = container, planId = planId) { settings ->
                    PlanSettingsScreen(
                        plan = currentPlan,
                        settings = settings,
                        onBack = navController::popBackStack,
                        onSave = { updatedSettings ->
                            coroutineScope.launch {
                                container.manageStudyPlan.updateSettings(updatedSettings)
                                container.reminderDeliveryCoordinator.reconcilePlan(updatedSettings.studyPlanId)
                                navController.popBackStack()
                            }
                        },
                    )
                }
            }
        }
        composable<PlanPortabilityRoute> { entry ->
            val route = entry.toRoute<PlanPortabilityRoute>()
            val planId = route.planId.takeIf { it.isNotBlank() }?.let(::StudyPlanId)
            val plan = planId?.let { id -> plans.firstOrNull { it.id == id } }
            PlanPortabilityScreen(
                plan = plan,
                portability = container.manageStudyPlanPortability,
                onBack = navController::popBackStack,
                onImported = { importedPlanId ->
                    navController.navigate(DashboardRoute(importedPlanId)) {
                        popUpTo<PlanListRoute>()
                    }
                },
            )
        }
        composable<DashboardRoute> { entry ->
            val route = entry.toRoute<DashboardRoute>()
            LegacyWorkspaceRedirect(navController, TodayRoute(route.planId))
        }
        composable<AnalyticsRoute> { entry ->
            val route = entry.toRoute<AnalyticsRoute>()
            LegacyWorkspaceRedirect(navController, ProgressWorkspaceRoute(route.planId))
        }

        composable<ScheduleRoute> { entry ->
            val route = entry.toRoute<ScheduleRoute>()
            LegacyWorkspaceRedirect(
                navController,
                PlanWorkspaceRoute(route.planId, PlanWorkspaceTab.SCHEDULE),
            )
        }

        composable<LibraryRoute> { entry ->
            val route = entry.toRoute<LibraryRoute>()
            LegacyWorkspaceRedirect(
                navController,
                PlanWorkspaceRoute(route.planId, PlanWorkspaceTab.LIBRARY),
            )
        }
        composable<SessionsRoute> { entry ->
            val route = entry.toRoute<SessionsRoute>()
            val planId = remember(route.planId) { StudyPlanId(route.planId) }
            LoadedPlanDestination(container = container, planId = planId) { currentPlan ->
                StudySessionScreen(
                    plan = currentPlan,
                    sessionManager = container.manageStudySession,
                    scheduleManager = container.manageStudySchedule,
                    onBack = navController::popBackStack,
                )
            }
        }

        composable<PracticeRoute> { entry ->
            val route = entry.toRoute<PracticeRoute>()
            LegacyWorkspaceRedirect(
                navController,
                PracticeWorkspaceRoute(route.planId, PracticeWorkspaceTab.QUESTIONS),
            )
        }


        composable<ProgressRoute> { entry ->
            val route = entry.toRoute<ProgressRoute>()
            LegacyWorkspaceRedirect(navController, ProgressWorkspaceRoute(route.planId))
        }
        composable<ReviewsRoute> { entry ->
            val route = entry.toRoute<ReviewsRoute>()
            LegacyWorkspaceRedirect(
                navController,
                PracticeWorkspaceRoute(route.planId, PracticeWorkspaceTab.REVIEWS),
            )
        }
        composable<RemindersRoute> { entry ->
            val route = entry.toRoute<RemindersRoute>()
            val planId = remember(route.planId) { StudyPlanId(route.planId) }
            LoadedPlanDestination(container = container, planId = planId) { currentPlan ->
                LoadedPlanSettingsDestination(container = container, planId = planId) { settings ->
                    StudyRemindersScreen(
                        plan = currentPlan,
                        settings = settings,
                        schedule = container.manageStudySchedule,
                        review = container.manageStudyReview,
                        onSettingsChanged = { updatedSettings ->
                            coroutineScope.launch {
                                container.manageStudyPlan.updateSettings(updatedSettings)
                                container.reminderDeliveryCoordinator.reconcilePlan(updatedSettings.studyPlanId)
                            }
                        },
                        onNotificationPermissionResult = { granted ->
                            if (granted) {
                                coroutineScope.launch {
                                    container.reminderDeliveryCoordinator.reconcilePlan(currentPlan.id)
                                }
                            }
                        },
                        onBack = navController::popBackStack,
                    )
                }
            }
        }
        composable<SubjectsRoute> { entry ->
            val route = entry.toRoute<SubjectsRoute>()
            LegacyWorkspaceRedirect(
                navController,
                PlanWorkspaceRoute(route.planId, PlanWorkspaceTab.CONTENT),
            )
        }
        composable<PrivacyRoute> {
            PrivacyCenterScreen(
                hasDefaultPlan = preferences.defaultStudyPlanId != null,
                onOpenBackupCenter = {
                    navController.navigate(
                        PlanPortabilityRoute(preferences.defaultStudyPlanId?.value.orEmpty()),
                    )
                },
                onBack = navController::popBackStack,
            )
        }
        composable<SearchRoute> {
            StudySearchScreen(
                discovery = container.manageStudyDiscovery,
                recommendationPlanId = preferences.defaultStudyPlanId
                    ?: plans.firstOrNull { plan ->
                        plan.status !in setOf(
                            StudyPlanStatus.ARCHIVED,
                            StudyPlanStatus.TRASHED,
                            StudyPlanStatus.COMPLETED,
                        )
                    }?.id,
                recentSearches = preferences.recentSearches,
                onRecordRecentSearch = { query -> coroutineScope.launch {
                    container.appPreferencesRepository.recordRecentSearch(query)
                } },
                onClearRecentSearches = { coroutineScope.launch {
                    container.appPreferencesRepository.clearRecentSearches()
                } },
                onOpenResult = { result ->
                    navController.navigateToResult(result)
                },
                onOpenRecommendation = { recommendation ->
                    navController.navigateToRecommendation(recommendation)
                },
            )
        }
        composable<SyncRoute> {
            var summary by androidx.compose.runtime.remember {
                mutableStateOf<com.mikeyphw.studyflow.core.model.StudySyncChangeSummary?>(null)
            }
            LaunchedEffect(syncState.lastLocalChangeToken) {
                summary = container.manageStudyCloudSync.inspectLocalChanges()
            }
            SyncFoundationScreen(
                state = syncState,
                summary = summary,
                onPrepare = {
                    coroutineScope.launch {
                        container.manageStudyCloudSync.prepareLocalFoundation("This device")
                        summary = container.manageStudyCloudSync.inspectLocalChanges()
                    }
                },
                onDisable = {
                    coroutineScope.launch {
                        container.manageStudyCloudSync.clearLocalFoundation()
                        summary = container.manageStudyCloudSync.inspectLocalChanges()
                    }
                },
                onBack = navController::popBackStack,
            )
        }
        composable<DebugWorkbenchRoute> {
            var timeline by remember {
                mutableStateOf(DiagnosticTimelineSnapshot(emptyList(), 0, 0))
            }
            var probeReport by remember { mutableStateOf(DebugProbeReport.empty()) }
            var safeSuiteRunning by rememberSaveable { mutableStateOf(false) }
            var actionResult by rememberSaveable { mutableStateOf<DebugWorkbenchActionResult?>(null) }
            var selfCheckHistory by remember { mutableStateOf(RuntimeSelfCheckHistory()) }
            var supportBundleSealReport by remember { mutableStateOf(SupportBundleSealReport.notRun()) }
            var reminderLabReport by remember {
                mutableStateOf(ReminderNotificationLabReport(status = DebugLabStatus.NOT_RUN))
            }
            var reminderLabAction by rememberSaveable { mutableStateOf<ReminderLabActionResult?>(null) }
            var reminderLabRunning by rememberSaveable { mutableStateOf(false) }
            var databasePortabilityReport by remember {
                mutableStateOf(
                    DatabasePortabilityLabReport(
                        database = DatabaseHealthLabReport(status = DebugLabStatus.NOT_RUN),
                    ),
                )
            }
            var databasePortabilityAction by rememberSaveable {
                mutableStateOf<DatabasePortabilityLabActionResult?>(null)
            }
            var databasePortabilityRunning by rememberSaveable { mutableStateOf(false) }
            var studyEngineReport by remember {
                mutableStateOf(StudyEngineLabReport(status = DebugLabStatus.NOT_RUN))
            }
            var studyEngineAction by rememberSaveable { mutableStateOf<StudyEngineLabActionResult?>(null) }
            var studyEngineRunning by rememberSaveable { mutableStateOf(false) }
            val reminderLab = remember(container, preferences.defaultStudyPlanId) {
                StudyFlowReminderNotificationLab(
                    context = context,
                    container = container,
                    defaultPlanId = { preferences.defaultStudyPlanId },
                )
            }
            val databasePortabilityLab = remember(container, preferences.defaultStudyPlanId) {
                StudyFlowDatabasePortabilityLab(
                    container = container,
                    defaultPlanId = { preferences.defaultStudyPlanId },
                )
            }
            val studyEngineLab = remember(container, preferences.defaultStudyPlanId) {
                StudyFlowStudyEngineLab(
                    container = container,
                    defaultPlanId = { preferences.defaultStudyPlanId },
                )
            }
            val probeRegistry = remember(container, preferences.defaultStudyPlanId) {
                StudyFlowDebugProbeRegistry(
                    context = context,
                    container = container,
                    defaultPlanId = { preferences.defaultStudyPlanId },
                )
            }
            val runtimeSelfCheckSuite = remember(container, preferences.defaultStudyPlanId) {
                StudyFlowRuntimeSelfCheckSuite(
                    runProbe = probeRegistry::run,
                    recorder = container.diagnosticRecorder,
                )
            }
            val debugActions = remember(
                container,
                context,
                debugClipboardLabel,
                debugBundleChooserTitle,
            ) {
                StudyFlowDebugWorkbenchActions.android(
                    context = context,
                    store = container.diagnosticRecorder,
                    clipboardLabel = debugClipboardLabel,
                    chooserTitle = debugBundleChooserTitle,
                )
            }

            suspend fun refreshDiagnostics() {
                timeline = debugActions.loadTimeline()
            }

            suspend fun executeProbe(id: DebugProbeId) {
                probeReport = DebugProbeReportPolicy.markRunning(probeReport, id)
                try {
                    val result = probeRegistry.run(id)
                    probeReport = DebugProbeReportPolicy.complete(probeReport, result)
                } catch (cancelled: CancellationException) {
                    probeReport = DebugProbeReportPolicy.complete(
                        probeReport,
                        DebugProbeResult(
                            id = id,
                            status = DebugProbeStatus.CANCELLED,
                            completedAtEpochMillis = System.currentTimeMillis(),
                        ),
                    )
                    throw cancelled
                } finally {
                    refreshDiagnostics()
                }
            }

            LaunchedEffect(Unit) {
                refreshDiagnostics()
            }

            val shellReport = DebugWorkbenchShellPolicy.evaluate(
                store = container.diagnosticRecorder,
                timeline = timeline,
            )
            DebugWorkbenchScreen(
                shellReport = shellReport,
                timeline = timeline,
                actionResult = actionResult,
                selfCheckHistory = selfCheckHistory,
                supportBundleSealReport = supportBundleSealReport,
                onRefresh = {
                    coroutineScope.launch {
                        refreshDiagnostics()
                        actionResult = DebugWorkbenchActionResult.REFRESHED
                    }
                },
                onCopyStatus = {
                    copyDebugText(context, debugClipboardLabel, shellReport.toClipboardReport())
                    actionResult = DebugWorkbenchActionResult.STATUS_COPIED
                },
                onCopyTimeline = {
                    coroutineScope.launch {
                        val text = debugActions.copyTimeline()
                        copyDebugText(context, debugClipboardLabel, text)
                        actionResult = DebugWorkbenchActionResult.TIMELINE_COPIED
                    }
                },
                onClearHistory = {
                    coroutineScope.launch {
                        val cleared = debugActions.clearHistory()
                        actionResult = if (cleared) {
                            refreshDiagnostics()
                            DebugWorkbenchActionResult.HISTORY_CLEARED
                        } else {
                            DebugWorkbenchActionResult.HISTORY_BUSY
                        }
                    }
                },
                onExportSupportBundle = {
                    coroutineScope.launch {
                        actionResult = debugActions.exportAndShare()
                        supportBundleSealReport = debugActions.supportBundleSealReport()
                    }
                },
                onOpenRuntimeSmokeWorkbench = { navController.navigate(RuntimeSmokeRoute) },
                onBack = navController::popBackStack,
                probeContent = {
                    DebugProbePanel(
                        report = probeReport,
                        safeSuiteRunning = safeSuiteRunning,
                        onRunProbe = { id -> coroutineScope.launch { executeProbe(id) } },
                        onRunSafeSuite = {
                            if (!safeSuiteRunning) {
                                coroutineScope.launch {
                                    safeSuiteRunning = true
                                    try {
                                        val (updatedReport, run) = runtimeSelfCheckSuite.run(probeReport)
                                        probeReport = updatedReport
                                        selfCheckHistory = selfCheckHistory.record(run)
                                        refreshDiagnostics()
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } finally {
                                        safeSuiteRunning = false
                                    }
                                }
                            }
                        },
                    )
                },
                labContent = {
                    DebugLabsPanel(
                        reminderReport = reminderLabReport,
                        reminderActionResult = reminderLabAction,
                        reminderRunning = reminderLabRunning,
                        onInspectReminders = {
                            if (!reminderLabRunning) coroutineScope.launch {
                                reminderLabRunning = true
                                try {
                                    val (report, result) = reminderLab.inspect()
                                    reminderLabReport = report
                                    reminderLabAction = result
                                    refreshDiagnostics()
                                } finally {
                                    reminderLabRunning = false
                                }
                            }
                        },
                        onReconcileReminders = {
                            if (!reminderLabRunning) coroutineScope.launch {
                                reminderLabRunning = true
                                try {
                                    reminderLabAction = reminderLab.reconcile()
                                    reminderLabReport = reminderLab.inspect().first
                                    refreshDiagnostics()
                                } finally {
                                    reminderLabRunning = false
                                }
                            }
                        },
                        onPostDiagnosticNotification = {
                            if (!reminderLabRunning) coroutineScope.launch {
                                reminderLabRunning = true
                                try {
                                    reminderLabAction = reminderLab.postDiagnosticNotification()
                                    reminderLabReport = reminderLab.inspect().first
                                    refreshDiagnostics()
                                } finally {
                                    reminderLabRunning = false
                                }
                            }
                        },
                        databasePortabilityReport = databasePortabilityReport,
                        databasePortabilityActionResult = databasePortabilityAction,
                        databasePortabilityRunning = databasePortabilityRunning,
                        onInspectDatabase = {
                            if (!databasePortabilityRunning) coroutineScope.launch {
                                databasePortabilityRunning = true
                                try {
                                    val (report, result) = databasePortabilityLab.inspectDatabase(
                                        databasePortabilityReport,
                                    )
                                    databasePortabilityReport = report
                                    databasePortabilityAction = result
                                    refreshDiagnostics()
                                } finally {
                                    databasePortabilityRunning = false
                                }
                            }
                        },
                        onRunPortabilityDrills = {
                            if (!databasePortabilityRunning) coroutineScope.launch {
                                databasePortabilityRunning = true
                                try {
                                    val (report, result) = databasePortabilityLab.runPortabilityDrills(
                                        databasePortabilityReport,
                                    )
                                    databasePortabilityReport = report
                                    databasePortabilityAction = result
                                    refreshDiagnostics()
                                } finally {
                                    databasePortabilityRunning = false
                                }
                            }
                        },
                        studyEngineReport = studyEngineReport,
                        studyEngineActionResult = studyEngineAction,
                        studyEngineRunning = studyEngineRunning,
                        onInspectStudyEngine = {
                            if (!studyEngineRunning) coroutineScope.launch {
                                studyEngineRunning = true
                                try {
                                    val (report, result) = studyEngineLab.inspect()
                                    studyEngineReport = report
                                    studyEngineAction = result
                                    refreshDiagnostics()
                                } catch (cancelled: CancellationException) {
                                    studyEngineReport = studyEngineReport.copy(
                                        status = DebugLabStatus.CANCELLED,
                                        message = StudyEngineLabMessage.INSPECTION_FAILED,
                                    )
                                    throw cancelled
                                } finally {
                                    studyEngineRunning = false
                                }
                            }
                        },
                    )
                },
            )
        }
        composable<RuntimeSmokeRoute> {
            var report by androidx.compose.runtime.remember {
                mutableStateOf(
                    RuntimeSmokeWorkbench.initialReport(System.currentTimeMillis()),
                )
            }
            val defaultPlanId = preferences.defaultStudyPlanId

            fun markPassed(kind: RuntimeSmokeProbeKind, detail: String) {
                report = RuntimeSmokeWorkbench.markPassed(report, kind, detail)
            }

            fun markBlocked(kind: RuntimeSmokeProbeKind, detail: String) {
                report = RuntimeSmokeWorkbench.markBlocked(report, kind, detail)
            }

            suspend fun withDefaultPlan(
                kind: RuntimeSmokeProbeKind,
                block: suspend (StudyPlanId) -> Unit,
            ) {
                val planId = defaultPlanId
                if (planId == null) {
                    markBlocked(kind, "Set a default Study Plan before running this probe.")
                    return
                }
                block(planId)
            }

            fun runProbe(kind: RuntimeSmokeProbeKind) {
                coroutineScope.launch {
                    runCatching {
                        when (kind) {
                            RuntimeSmokeProbeKind.REMINDER_RECONCILIATION -> withDefaultPlan(kind) { planId ->
                                container.reminderDeliveryCoordinator.reconcilePlan(planId)
                                markPassed(
                                    kind,
                                    "Reminder reconciliation requested for the default Study Plan.",
                                )
                            }
                            RuntimeSmokeProbeKind.WIDGET_REFRESH -> {
                                StudyFlowWidgetUpdateCoordinator.refreshAll(context.applicationContext)
                                markPassed(kind, "Today widget refresh requested.")
                            }
                            RuntimeSmokeProbeKind.BACKUP_DRY_RUN -> withDefaultPlan(kind) { planId ->
                                val exported = ByteArrayOutputStream()
                                container.manageStudyPlanPortability.exportPlan(
                                    planId = planId,
                                    options = StudyPlanExportOptions(
                                        mode = StudyPlanExportMode.FULL,
                                        includeAnnexMetadata = true,
                                        includeAnnexPayloads = false,
                                        metadataPrivacyMode = true,
                                        redactNotes = true,
                                        redactHistory = true,
                                    ),
                                    output = exported,
                                )
                                val backupBytes = exported.toByteArray()
                                val integrity = container.manageStudyPlanPortability.inspectBackup(
                                    ByteArrayInputStream(backupBytes),
                                )
                                val dryRun = container.manageStudyPlanPortability.dryRunImport(
                                    input = ByteArrayInputStream(backupBytes),
                                    strategy = StudyPlanImportStrategy.CREATE_AS_NEW,
                                )
                                if (integrity.canRestore && dryRun.databaseRowsChanged == 0) {
                                    markPassed(
                                        kind,
                                        "Backup inspect and restore dry-run completed with zero writes.",
                                    )
                                } else {
                                    markBlocked(
                                        kind,
                                        "Backup dry-run did not satisfy integrity or zero-write checks.",
                                    )
                                }
                            }
                            RuntimeSmokeProbeKind.SEARCH_QUERY -> {
                                val results = container.manageStudyDiscovery.search("study")
                                markPassed(kind, "Global search query completed with ${results.size} result(s).")
                            }
                            RuntimeSmokeProbeKind.RECOMMENDATION_QUERY -> withDefaultPlan(kind) { planId ->
                                val recommendations = container.manageStudyDiscovery.recommendations(planId)
                                markPassed(
                                    kind,
                                    "Recommendation query completed with ${recommendations.size} result(s).",
                                )
                            }
                            RuntimeSmokeProbeKind.SYNC_FOUNDATION -> {
                                val summary = container.manageStudyCloudSync.inspectLocalChanges()
                                markPassed(
                                    kind,
                                    "Local checkpoint inspection completed for ${summary.planCount} plan(s).",
                                )
                            }
                        }
                    }.onFailure { failure ->
                        markBlocked(
                            kind,
                            failure.message ?: failure::class.java.simpleName,
                        )
                    }
                }
            }
            RuntimeSmokeWorkbenchScreen(
                report = report,
                hasDefaultPlan = defaultPlanId != null,
                onRunProbe = ::runProbe,
                onRunSafeProbeSet = {
                    RuntimeSmokeProbeKind.values().forEach(::runProbe)
                },
                onBack = navController::popBackStack,
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(
                preferences = preferences,
                hasDefaultPlan = preferences.defaultStudyPlanId != null,
                syncState = syncState,
                onLanguageChanged = { language -> coroutineScope.launch {
                    container.appPreferencesRepository.setLanguage(language)
                } },
                onThemeModeChanged = { themeMode -> coroutineScope.launch {
                    container.appPreferencesRepository.setThemeMode(themeMode)
                } },
                onDynamicColorChanged = { enabled -> coroutineScope.launch {
                    container.appPreferencesRepository.setDynamicColorEnabled(enabled)
                } },
                onOpenDefaultPlanBackup = {
                    navController.navigate(
                        PlanPortabilityRoute(preferences.defaultStudyPlanId?.value.orEmpty()),
                    )
                },
                onOpenDefaultPlanReminders = {
                    navController.navigate(RemindersRoute(preferences.defaultStudyPlanId?.value.orEmpty()))
                },
                onOpenPrivacyCenter = { navController.navigate(PrivacyRoute) },
                onOpenSyncCenter = { navController.navigate(SyncRoute) },
                onOpenDebugWorkbench = { navController.navigate(DebugWorkbenchRoute) },
                diagnosticsAvailable = BuildConfig.DEBUG,
            )
        }
    }
}


private fun copyDebugText(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard?.setPrimaryClip(
        ClipData.newPlainText(
            label,
            text,
        ),
    )
}

private fun NavHostController.navigateToPlans() {
    navigate(PlanListRoute) {
        popUpTo<PlanListRoute> { inclusive = false }
        launchSingleTop = true
    }
}

private fun NavHostController.navigateToSearch() {
    navigate(SearchRoute) { launchSingleTop = true }
}

private fun NavHostController.navigateToSettings() {
    navigate(SettingsRoute) { launchSingleTop = true }
}

private fun NavHostController.navigateToResult(result: GlobalSearchResult) {
    val planId = result.studyPlanId.value
    when (result.type.destination()) {
        GlobalSearchDestination.DASHBOARD -> navigate(TodayRoute(planId))
        GlobalSearchDestination.SUBJECTS -> navigate(
            PlanWorkspaceRoute(planId, PlanWorkspaceTab.CONTENT),
        )
        GlobalSearchDestination.PRACTICE -> navigate(
            PracticeWorkspaceRoute(planId, PracticeWorkspaceTab.QUESTIONS),
        )
        GlobalSearchDestination.PROGRESS -> navigate(ProgressWorkspaceRoute(planId))
        GlobalSearchDestination.LIBRARY -> navigate(
            PlanWorkspaceRoute(planId, PlanWorkspaceTab.LIBRARY),
        )
        GlobalSearchDestination.REVIEWS -> navigate(
            PracticeWorkspaceRoute(planId, PracticeWorkspaceTab.REVIEWS),
        )
    }
}

private fun NavHostController.navigateToRecommendation(recommendation: StudyRecommendation) {
    navigateToWorkspaceRecommendation(recommendation)
}

private fun localizedSubjectNames(resources: Resources): Map<OnboardingSampleSubjectKind, String> =
    OnboardingSampleSubjectKind.entries.associateWith { subject ->
        resources.getString(subject.titleRes())
    }

private fun localizedTopicNames(resources: Resources): Map<OnboardingSampleTopicKind, String> =
    OnboardingSampleTopicKind.entries.associateWith { topic ->
        resources.getString(topic.titleRes())
    }

private fun PlanEditorForm.toNewStudyPlan(): StudyPlan = StudyPlan(
    id = StudyPlanId(UUID.randomUUID().toString()),
    name = name,
    description = description,
    type = studyType,
    status = StudyPlanStatus.ACTIVE,
    examDateIso = examDateIso.ifBlank { null },
    weeklyGoalHours = weeklyGoalHours,
    progressPercent = 0,
    studiedMinutesThisWeek = 0,
    questionsAnswered = 0,
    averageAccuracyPercent = 0,
)

private fun StudyPlan.withEditorForm(form: PlanEditorForm): StudyPlan = copy(
    name = form.name,
    description = form.description,
    type = form.studyType,
    examDateIso = form.examDateIso.ifBlank { null },
    weeklyGoalHours = form.weeklyGoalHours,
)

private fun PlanEditorForm.toSettings(
    planId: StudyPlanId,
    current: StudyPlanSettings = StudyPlanSettings.defaults(planId),
): StudyPlanSettings = current.copy(
    studyPlanId = planId,
    adaptivePlanningEnabled = adaptivePlanningEnabled,
    reviewNotificationsEnabled = reviewNotificationsEnabled,
    strictExamModeEnabled = strictExamModeEnabled,
    preferredSessionMinutes = preferredSessionMinutes,
)
