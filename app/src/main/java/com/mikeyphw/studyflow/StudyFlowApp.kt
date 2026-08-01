package com.mikeyphw.studyflow

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.mikeyphw.studyflow.core.designsystem.StudyFlowErrorPane
import com.mikeyphw.studyflow.core.designsystem.StudyFlowTheme
import com.mikeyphw.studyflow.core.i18n.AppLocaleController
import com.mikeyphw.studyflow.core.model.AppPreferences
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyPlanSettings
import com.mikeyphw.studyflow.core.model.StudyPlanStatus
import com.mikeyphw.studyflow.core.navigation.CreatePlanRoute
import com.mikeyphw.studyflow.core.navigation.DashboardRoute
import com.mikeyphw.studyflow.core.navigation.EditPlanRoute
import com.mikeyphw.studyflow.core.navigation.PlanListRoute
import com.mikeyphw.studyflow.core.navigation.PlanPortabilityRoute
import com.mikeyphw.studyflow.core.navigation.PlanSettingsRoute
import com.mikeyphw.studyflow.core.navigation.ScheduleRoute
import com.mikeyphw.studyflow.core.navigation.SessionsRoute
import com.mikeyphw.studyflow.core.navigation.SettingsRoute
import com.mikeyphw.studyflow.core.navigation.SubjectsRoute
import com.mikeyphw.studyflow.feature.dashboard.DashboardScreen
import com.mikeyphw.studyflow.feature.plans.PlanEditorForm
import com.mikeyphw.studyflow.feature.plans.PlanEditorScreen
import com.mikeyphw.studyflow.feature.plans.PlanListScreen
import com.mikeyphw.studyflow.feature.plans.PlanPortabilityScreen
import com.mikeyphw.studyflow.feature.plans.PlanSettingsScreen
import com.mikeyphw.studyflow.feature.plans.StudyPlanAction
import com.mikeyphw.studyflow.feature.schedule.StudyScheduleScreen
import com.mikeyphw.studyflow.feature.sessions.StudySessionScreen
import com.mikeyphw.studyflow.feature.settings.SettingsScreen
import com.mikeyphw.studyflow.feature.subjects.StudyContentScreen
import java.util.UUID
import kotlinx.coroutines.launch

private val MediumNavigationWidth = 600.dp
private val ExpandedNavigationWidth = 1000.dp

@Composable
fun StudyFlowApp(
    container: AppContainer,
    modifier: Modifier = Modifier,
) {
    val preferences by container.appPreferencesRepository.preferences.collectAsStateWithLifecycle(
        initialValue = AppPreferences(),
    )
    val plans by container.studyPlanRepository.plans.collectAsStateWithLifecycle(initialValue = emptyList())

    LaunchedEffect(preferences.language) {
        if (AppLocaleController.currentLanguage() != preferences.language) {
            AppLocaleController.apply(preferences.language)
        }
    }

    StudyFlowTheme(
        themeMode = preferences.themeMode,
        dynamicColorEnabled = preferences.dynamicColorEnabled,
    ) {
        StudyFlowNavigation(
            container = container,
            plans = plans,
            preferences = preferences,
            modifier = modifier,
        )
    }
}

@Composable
private fun StudyFlowNavigation(
    container: AppContainer,
    plans: List<StudyPlan>,
    preferences: AppPreferences,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val destination = currentBackStackEntry?.destination
    var defaultPlanOpened by rememberSaveable { mutableStateOf(false) }
    val defaultPlan = preferences.defaultStudyPlanId?.let { defaultId ->
        plans.firstOrNull { plan ->
            plan.id == defaultId && plan.status !in setOf(
                StudyPlanStatus.ARCHIVED,
                StudyPlanStatus.TRASHED,
            )
        }
    }

    LaunchedEffect(preferences.defaultStudyPlanId, plans, destination) {
        if (!defaultPlanOpened && defaultPlan != null && destination?.hasRoute<PlanListRoute>() == true) {
            defaultPlanOpened = true
            navController.navigate(DashboardRoute(defaultPlan.id.value))
        } else if (preferences.defaultStudyPlanId != null && defaultPlan == null && plans.isNotEmpty()) {
            container.appPreferencesRepository.setDefaultStudyPlan(null)
        }
    }

    val showPlansSelected = destination?.hasRoute<PlanListRoute>() == true ||
        destination?.hasRoute<DashboardRoute>() == true ||
        destination?.hasRoute<CreatePlanRoute>() == true ||
        destination?.hasRoute<EditPlanRoute>() == true ||
        destination?.hasRoute<PlanSettingsRoute>() == true ||
        destination?.hasRoute<PlanPortabilityRoute>() == true ||
        destination?.hasRoute<SubjectsRoute>() == true ||
        destination?.hasRoute<ScheduleRoute>() == true ||
        destination?.hasRoute<SessionsRoute>() == true
    val showSettingsSelected = destination?.hasRoute<SettingsRoute>() == true

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        when {
            maxWidth >= ExpandedNavigationWidth -> {
                PermanentNavigationDrawer(
                    drawerContent = {
                        PermanentDrawerSheet {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(24.dp),
                            )
                            NavigationDrawerItem(
                                selected = showPlansSelected,
                                onClick = { navController.navigateToPlans() },
                                icon = { Icon(Icons.AutoMirrored.Outlined.ListAlt, contentDescription = null) },
                                label = { Text(stringResource(R.string.navigation_plans)) },
                                modifier = Modifier.padding(horizontal = 12.dp),
                            )
                            NavigationDrawerItem(
                                selected = showSettingsSelected,
                                onClick = { navController.navigateToSettings() },
                                icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                                label = { Text(stringResource(R.string.navigation_settings)) },
                                modifier = Modifier.padding(horizontal = 12.dp),
                            )
                        }
                    },
                ) {
                    StudyFlowNavHost(
                        navController = navController,
                        container = container,
                        plans = plans,
                        preferences = preferences,
                    )
                }
            }
            maxWidth >= MediumNavigationWidth -> {
                Row(modifier = Modifier.fillMaxSize()) {
                    NavigationRail {
                        NavigationRailItem(
                            selected = showPlansSelected,
                            onClick = { navController.navigateToPlans() },
                            icon = { Icon(Icons.AutoMirrored.Outlined.ListAlt, contentDescription = null) },
                            label = { Text(stringResource(R.string.navigation_plans)) },
                        )
                        NavigationRailItem(
                            selected = showSettingsSelected,
                            onClick = { navController.navigateToSettings() },
                            icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                            label = { Text(stringResource(R.string.navigation_settings)) },
                        )
                    }
                    StudyFlowNavHost(
                        navController = navController,
                        container = container,
                        plans = plans,
                        preferences = preferences,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            else -> {
                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = showPlansSelected,
                                onClick = { navController.navigateToPlans() },
                                icon = { Icon(Icons.AutoMirrored.Outlined.ListAlt, contentDescription = null) },
                                label = { Text(stringResource(R.string.navigation_plans)) },
                            )
                            NavigationBarItem(
                                selected = showSettingsSelected,
                                onClick = { navController.navigateToSettings() },
                                icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                                label = { Text(stringResource(R.string.navigation_settings)) },
                            )
                        }
                    },
                ) { padding ->
                    StudyFlowNavHost(
                        navController = navController,
                        container = container,
                        plans = plans,
                        preferences = preferences,
                        modifier = Modifier.padding(padding),
                    )
                }
            }
        }
    }
}

@Composable
private fun StudyFlowNavHost(
    navController: NavHostController,
    container: AppContainer,
    plans: List<StudyPlan>,
    preferences: AppPreferences,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    val copySuffix = stringResource(R.string.plan_copy_suffix)

    NavHost(
        navController = navController,
        startDestination = PlanListRoute,
        modifier = modifier.fillMaxSize(),
    ) {
        composable<PlanListRoute> {
            PlanListScreen(
                plans = plans,
                defaultPlanId = preferences.defaultStudyPlanId,
                onPlanSelected = { plan -> navController.navigate(DashboardRoute(plan.id.value)) },
                onCreatePlan = { navController.navigate(CreatePlanRoute) },
                onImportPlan = { navController.navigate(PlanPortabilityRoute()) },
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
                        }
                        StudyPlanAction.PAUSE -> coroutineScope.launch {
                            container.manageStudyPlan.pause(plan.id)
                        }
                        StudyPlanAction.ARCHIVE -> coroutineScope.launch {
                            clearDefaultIfNeeded(container, preferences, plan.id)
                            container.manageStudyPlan.archive(plan.id)
                        }
                        StudyPlanAction.RESTORE_ARCHIVED -> coroutineScope.launch {
                            container.manageStudyPlan.restoreArchived(plan.id)
                        }
                        StudyPlanAction.MOVE_TO_TRASH -> coroutineScope.launch {
                            clearDefaultIfNeeded(container, preferences, plan.id)
                            container.manageStudyPlan.moveToTrash(plan.id)
                        }
                        StudyPlanAction.RESTORE_FROM_TRASH -> coroutineScope.launch {
                            container.manageStudyPlan.restoreFromTrash(plan.id)
                        }
                        StudyPlanAction.DELETE_PERMANENTLY -> coroutineScope.launch {
                            clearDefaultIfNeeded(container, preferences, plan.id)
                            container.manageStudyPlan.deletePermanently(plan.id)
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
                        navController.navigate(DashboardRoute(plan.id.value)) {
                            popUpTo<PlanListRoute>()
                        }
                    }
                },
            )
        }
        composable<EditPlanRoute> { entry ->
            val route = entry.toRoute<EditPlanRoute>()
            val planId = remember(route.planId) { StudyPlanId(route.planId) }
            val planFlow = remember(planId) { container.studyPlanRepository.observePlan(planId) }
            val settingsFlow = remember(planId) { container.studyPlanRepository.observePlanSettings(planId) }
            val plan by planFlow.collectAsStateWithLifecycle(initialValue = null)
            val settings by settingsFlow.collectAsStateWithLifecycle(
                initialValue = StudyPlanSettings.defaults(planId),
            )
            val currentPlan = plan
            if (currentPlan == null) {
                StudyFlowErrorPane(
                    message = stringResource(R.string.unknown_plan_error),
                    onRetry = navController::popBackStack,
                )
            } else {
                PlanEditorScreen(
                    initialPlan = currentPlan,
                    initialSettings = settings,
                    onBack = navController::popBackStack,
                    onSave = { form ->
                        coroutineScope.launch {
                            container.manageStudyPlan.update(currentPlan.withEditorForm(form))
                            container.manageStudyPlan.updateSettings(form.toSettings(currentPlan.id, settings))
                            navController.popBackStack()
                        }
                    },
                )
            }
        }
        composable<PlanSettingsRoute> { entry ->
            val route = entry.toRoute<PlanSettingsRoute>()
            val planId = remember(route.planId) { StudyPlanId(route.planId) }
            val planFlow = remember(planId) { container.studyPlanRepository.observePlan(planId) }
            val settingsFlow = remember(planId) { container.studyPlanRepository.observePlanSettings(planId) }
            val plan by planFlow.collectAsStateWithLifecycle(initialValue = null)
            val settings by settingsFlow.collectAsStateWithLifecycle(
                initialValue = StudyPlanSettings.defaults(planId),
            )
            val currentPlan = plan
            if (currentPlan == null) {
                StudyFlowErrorPane(
                    message = stringResource(R.string.unknown_plan_error),
                    onRetry = navController::popBackStack,
                )
            } else {
                PlanSettingsScreen(
                    plan = currentPlan,
                    settings = settings,
                    onBack = navController::popBackStack,
                    onSave = { updatedSettings ->
                        coroutineScope.launch {
                            container.manageStudyPlan.updateSettings(updatedSettings)
                            navController.popBackStack()
                        }
                    },
                )
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
            val planId = remember(route.planId) { StudyPlanId(route.planId) }
            val planFlow = remember(planId) { container.studyPlanRepository.observePlan(planId) }
            val plan by planFlow.collectAsStateWithLifecycle(initialValue = null)
            val currentPlan = plan
            if (currentPlan == null || currentPlan.status in setOf(StudyPlanStatus.TRASHED, StudyPlanStatus.ARCHIVED)) {
                StudyFlowErrorPane(
                    message = stringResource(R.string.unknown_plan_error),
                    onRetry = { navController.navigateToPlans() },
                )
            } else {
                DashboardScreen(
                    plan = currentPlan,
                    availablePlans = plans,
                    isDefaultPlan = preferences.defaultStudyPlanId == currentPlan.id,
                    onBack = navController::popBackStack,
                    onSwitchPlan = { candidate ->
                        navController.navigate(DashboardRoute(candidate.id.value)) {
                            launchSingleTop = true
                        }
                    },
                    onEditPlan = { navController.navigate(EditPlanRoute(currentPlan.id.value)) },
                    onOpenPlanSettings = {
                        navController.navigate(PlanSettingsRoute(currentPlan.id.value))
                    },
                    onOpenSyllabus = {
                        navController.navigate(SubjectsRoute(currentPlan.id.value))
                    },
                    onOpenSchedule = {
                        navController.navigate(ScheduleRoute(currentPlan.id.value))
                    },
                    onOpenSessions = {
                        navController.navigate(SessionsRoute(currentPlan.id.value))
                    },
                    onOpenPortability = {
                        navController.navigate(PlanPortabilityRoute(currentPlan.id.value))
                    },
                    onToggleActiveState = {
                        coroutineScope.launch {
                            if (currentPlan.status == StudyPlanStatus.ACTIVE) {
                                container.manageStudyPlan.pause(currentPlan.id)
                            } else {
                                container.manageStudyPlan.activate(currentPlan.id)
                            }
                        }
                    },
                )
            }
        }
        composable<ScheduleRoute> { entry ->
            val route = entry.toRoute<ScheduleRoute>()
            val planId = remember(route.planId) { StudyPlanId(route.planId) }
            val planFlow = remember(planId) { container.studyPlanRepository.observePlan(planId) }
            val plan by planFlow.collectAsStateWithLifecycle(initialValue = null)
            val currentPlan = plan
            if (currentPlan == null) {
                StudyFlowErrorPane(
                    message = stringResource(R.string.unknown_plan_error),
                    onRetry = navController::popBackStack,
                )
            } else {
                StudyScheduleScreen(
                    plan = currentPlan,
                    scheduleManager = container.manageStudySchedule,
                    onBack = navController::popBackStack,
                )
            }
        }
        composable<SessionsRoute> { entry ->
            val route = entry.toRoute<SessionsRoute>()
            val planId = remember(route.planId) { StudyPlanId(route.planId) }
            val planFlow = remember(planId) { container.studyPlanRepository.observePlan(planId) }
            val plan by planFlow.collectAsStateWithLifecycle(initialValue = null)
            val currentPlan = plan
            if (currentPlan == null) {
                StudyFlowErrorPane(
                    message = stringResource(R.string.unknown_plan_error),
                    onRetry = navController::popBackStack,
                )
            } else {
                StudySessionScreen(
                    plan = currentPlan,
                    sessionManager = container.manageStudySession,
                    scheduleManager = container.manageStudySchedule,
                    onBack = navController::popBackStack,
                )
            }
        }
        composable<SubjectsRoute> { entry ->
            val route = entry.toRoute<SubjectsRoute>()
            val planId = remember(route.planId) { StudyPlanId(route.planId) }
            val planFlow = remember(planId) { container.studyPlanRepository.observePlan(planId) }
            val plan by planFlow.collectAsStateWithLifecycle(initialValue = null)
            val currentPlan = plan
            if (currentPlan == null) {
                StudyFlowErrorPane(
                    message = stringResource(R.string.unknown_plan_error),
                    onRetry = navController::popBackStack,
                )
            } else {
                StudyContentScreen(
                    plan = currentPlan,
                    content = container.manageStudyContent,
                    onBack = navController::popBackStack,
                )
            }
        }
        composable<SettingsRoute> {
            SettingsScreen(
                preferences = preferences,
                onLanguageChanged = { language -> coroutineScope.launch {
                    container.appPreferencesRepository.setLanguage(language)
                } },
                onThemeModeChanged = { themeMode -> coroutineScope.launch {
                    container.appPreferencesRepository.setThemeMode(themeMode)
                } },
                onDynamicColorChanged = { enabled -> coroutineScope.launch {
                    container.appPreferencesRepository.setDynamicColorEnabled(enabled)
                } },
            )
        }
    }
}

private suspend fun clearDefaultIfNeeded(
    container: AppContainer,
    preferences: AppPreferences,
    planId: StudyPlanId,
) {
    if (preferences.defaultStudyPlanId == planId) {
        container.appPreferencesRepository.setDefaultStudyPlan(null)
    }
}

private fun NavHostController.navigateToPlans() {
    navigate(PlanListRoute) {
        popUpTo<PlanListRoute> { inclusive = false }
        launchSingleTop = true
    }
}

private fun NavHostController.navigateToSettings() {
    navigate(SettingsRoute) { launchSingleTop = true }
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
