package com.mikeyphw.studyflow

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ListAlt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.mikeyphw.studyflow.core.designsystem.StudyFlowErrorPane
import com.mikeyphw.studyflow.core.designsystem.StudyFlowTheme
import com.mikeyphw.studyflow.core.i18n.AppLocaleController
import com.mikeyphw.studyflow.core.model.AppPreferences
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyPlanStatus
import com.mikeyphw.studyflow.core.navigation.CreatePlanRoute
import com.mikeyphw.studyflow.core.navigation.DashboardRoute
import com.mikeyphw.studyflow.core.navigation.PlanListRoute
import com.mikeyphw.studyflow.core.navigation.SettingsRoute
import com.mikeyphw.studyflow.feature.dashboard.DashboardScreen
import com.mikeyphw.studyflow.feature.plans.CreatePlanForm
import com.mikeyphw.studyflow.feature.plans.CreatePlanScreen
import com.mikeyphw.studyflow.feature.plans.PlanListScreen
import com.mikeyphw.studyflow.feature.settings.SettingsScreen
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
    val showPlansSelected = destination?.hasRoute<PlanListRoute>() == true ||
        destination?.hasRoute<DashboardRoute>() == true ||
        destination?.hasRoute<CreatePlanRoute>() == true
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
                                icon = { Icon(Icons.Outlined.ListAlt, contentDescription = null) },
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
                            icon = { Icon(Icons.Outlined.ListAlt, contentDescription = null) },
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
                                icon = { Icon(Icons.Outlined.ListAlt, contentDescription = null) },
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

    NavHost(
        navController = navController,
        startDestination = PlanListRoute,
        modifier = modifier.fillMaxSize(),
    ) {
        composable<PlanListRoute> {
            PlanListScreen(
                plans = plans,
                onPlanSelected = { plan -> navController.navigate(DashboardRoute(plan.id.value)) },
                onCreatePlan = { navController.navigate(CreatePlanRoute) },
            )
        }
        composable<CreatePlanRoute> {
            CreatePlanScreen(
                onBack = navController::popBackStack,
                onCreate = { form ->
                    val plan = form.toStudyPlan()
                    coroutineScope.launch {
                        container.createStudyPlan(plan)
                        navController.navigate(DashboardRoute(plan.id.value)) {
                            popUpTo<PlanListRoute>()
                        }
                    }
                },
            )
        }
        composable<DashboardRoute> { entry ->
            val route = entry.toRoute<DashboardRoute>()
            val planFlow = remember(route.planId) {
                container.studyPlanRepository.observePlan(StudyPlanId(route.planId))
            }
            val plan by planFlow.collectAsStateWithLifecycle(initialValue = null)
            if (plan == null) {
                StudyFlowErrorPane(
                    message = stringResource(R.string.unknown_plan_error),
                    onRetry = navController::popBackStack,
                )
            } else {
                DashboardScreen(
                    plan = requireNotNull(plan),
                    onBack = navController::popBackStack,
                )
            }
        }
        composable<SettingsRoute> {
            SettingsScreen(
                preferences = preferences,
                onLanguageChanged = { language ->
                    coroutineScope.launch {
                        container.appPreferencesRepository.setLanguage(language)
                    }
                },
                onThemeModeChanged = { themeMode ->
                    coroutineScope.launch {
                        container.appPreferencesRepository.setThemeMode(themeMode)
                    }
                },
                onDynamicColorChanged = { enabled ->
                    coroutineScope.launch {
                        container.appPreferencesRepository.setDynamicColorEnabled(enabled)
                    }
                },
            )
        }
    }
}

private fun NavHostController.navigateToPlans() {
    navigate(PlanListRoute) {
        popUpTo<PlanListRoute> { inclusive = false }
        launchSingleTop = true
    }
}

private fun NavHostController.navigateToSettings() {
    navigate(SettingsRoute) {
        launchSingleTop = true
    }
}

private fun CreatePlanForm.toStudyPlan(): StudyPlan = StudyPlan(
    id = StudyPlanId(UUID.randomUUID().toString()),
    name = name.trim(),
    description = description.trim(),
    type = studyType,
    status = StudyPlanStatus.ACTIVE,
    examDateIso = examDateIso.trim().ifBlank { null },
    weeklyGoalHours = weeklyGoalHours,
    progressPercent = 0,
    studiedMinutesThisWeek = 0,
    questionsAnswered = 0,
    averageAccuracyPercent = 0,
)
