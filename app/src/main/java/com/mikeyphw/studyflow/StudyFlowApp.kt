package com.mikeyphw.studyflow

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
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
import com.mikeyphw.studyflow.core.designsystem.rememberStudyFlowMotionEnabled
import com.mikeyphw.studyflow.core.designsystem.studyFlowAnimationSpec
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
import com.mikeyphw.studyflow.core.domain.GlobalSearchContentType
import com.mikeyphw.studyflow.core.domain.StudyManualFolderCheckpointDetails
import com.mikeyphw.studyflow.core.domain.StudyManualFolderScan
import com.mikeyphw.studyflow.core.domain.GlobalSearchDestination
import com.mikeyphw.studyflow.core.domain.GlobalSearchResult
import com.mikeyphw.studyflow.core.domain.OnboardingSampleExamStageKind
import com.mikeyphw.studyflow.core.domain.OnboardingSamplePlanBlueprint
import com.mikeyphw.studyflow.core.domain.OnboardingSamplePlanInput
import com.mikeyphw.studyflow.core.domain.OnboardingSamplePlanTemplate
import com.mikeyphw.studyflow.core.domain.PublicExamEditalImportChoice
import com.mikeyphw.studyflow.core.domain.OnboardingSampleSubjectKind
import com.mikeyphw.studyflow.core.domain.OnboardingSampleTopicKind
import com.mikeyphw.studyflow.core.domain.PublicExamGoalPreset
import com.mikeyphw.studyflow.core.domain.StudyOnboardingSamplePlanWizard
import com.mikeyphw.studyflow.core.domain.StudyRecommendation
import com.mikeyphw.studyflow.core.domain.StudyRecommendationDestination
import com.mikeyphw.studyflow.core.domain.destination
import com.mikeyphw.studyflow.core.domain.asStudyFlowLoadState
import com.mikeyphw.studyflow.core.domain.asStudyFlowOptionalLoadState
import com.mikeyphw.studyflow.core.domain.runRecoverableOperation
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionReliabilityExport
import com.mikeyphw.studyflow.core.model.AppPreferences
import com.mikeyphw.studyflow.core.model.ContentProgressStatus
import com.mikeyphw.studyflow.core.model.ExamStage
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudySyncLedgerSnapshot
import com.mikeyphw.studyflow.core.model.StudyFlowLoadState
import com.mikeyphw.studyflow.core.model.Subject
import com.mikeyphw.studyflow.core.model.SubjectId
import com.mikeyphw.studyflow.core.model.Topic
import com.mikeyphw.studyflow.core.model.TopicId
import com.mikeyphw.studyflow.core.model.StudyPlanStatus
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyPlanSettings
import com.mikeyphw.studyflow.core.model.StudyTarget
import com.mikeyphw.studyflow.core.model.StudyTargetId
import com.mikeyphw.studyflow.core.model.StudyType
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
import com.mikeyphw.studyflow.core.navigation.ContentManagerRoute
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
import com.mikeyphw.studyflow.core.navigation.TvControllerRoute
import com.mikeyphw.studyflow.core.domain.RuntimeSmokeWorkbench
import com.mikeyphw.studyflow.core.domain.ReminderNotificationType
import com.mikeyphw.studyflow.core.domain.StudyReminderPolicy
import com.mikeyphw.studyflow.core.model.RuntimeSmokeProbeKind
import com.mikeyphw.studyflow.feature.analytics.StudyAnalyticsScreen
import com.mikeyphw.studyflow.feature.dashboard.DashboardScreen
import com.mikeyphw.studyflow.feature.library.StudyLibraryScreen
import com.mikeyphw.studyflow.feature.plans.PlanEditorForm
import com.mikeyphw.studyflow.feature.plans.PlanEditorScreen
import com.mikeyphw.studyflow.feature.plans.PublicExamProfileForm
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
import com.mikeyphw.studyflow.tvcontrol.StudyFlowTvPhoneControllerScreen
import com.mikeyphw.studyflow.feature.settings.SyncFoundationScreen
import com.mikeyphw.studyflow.feature.subjects.StudyContentScreen
import java.util.UUID
import java.util.concurrent.CancellationException
import java.io.File
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

private suspend fun persistPublicExamProfile(
    container: AppContainer,
    plan: StudyPlan,
    existingTarget: StudyTarget?,
    form: PublicExamProfileForm,
) {
    require(plan.type == StudyType.PUBLIC_EXAMINATION) {
        "Public examination profile can only be saved for a public examination Study Plan."
    }
    val now = System.currentTimeMillis()
    val targetId = existingTarget?.id ?: StudyTargetId(UUID.randomUUID().toString())
    val target = StudyTarget(
        id = targetId,
        studyPlanId = plan.id,
        organization = form.organization.trim(),
        role = form.role.trim(),
        examBoard = form.examBoard.trim(),
        noticeUrl = form.noticeUrl.trim().ifBlank { null },
        confirmedExamDateIso = form.confirmedExamDateIso.trim().ifBlank { null },
        expectedExamDateIso = form.expectedExamDateIso.trim().ifBlank { null },
        openings = form.openings.toIntOrNull(),
        estimatedCutoffPercent = form.estimatedCutoffPercent.toIntOrNull(),
        createdAtEpochMillis = existingTarget?.createdAtEpochMillis ?: now,
        updatedAtEpochMillis = now,
    )
    container.manageStudyContent.saveStudyTarget(target)
    form.stages.forEachIndexed { index, stage ->
        container.manageStudyContent.saveExamStage(
            ExamStage(
                id = stage.id ?: com.mikeyphw.studyflow.core.model.ExamStageId(UUID.randomUUID().toString()),
                studyPlanId = plan.id,
                studyTargetId = targetId,
                name = stage.name.trim(),
                type = stage.type,
                scheduledDateIso = stage.scheduledDateIso.trim().ifBlank { null },
                weight = stage.weight.toDouble(),
                minimumScorePercent = stage.minimumScorePercent.toIntOrNull(),
                position = index,
            ),
        )
    }
}

@Composable
fun StudyFlowApp(
    container: AppContainer,
    modifier: Modifier = Modifier,
    reminderLaunch: StudyReminderLaunch? = null,
    onReminderLaunchConsumed: () -> Unit = {},
    tvControllerLaunchRequested: Boolean = false,
    onTvControllerLaunchConsumed: () -> Unit = {},
) {
    val initializationState by container.initializationState.collectAsStateWithLifecycle()
    if (initializationState is AppInitializationState.Initializing) {
        StudyFlowTheme(
            themeMode = ThemeMode.SYSTEM_DEFAULT,
            dynamicColorEnabled = false,
        ) {
            StudyFlowLoadingPane(modifier = modifier)
        }
        return
    }
    if (initializationState is AppInitializationState.Failed) {
        StudyFlowTheme(
            themeMode = ThemeMode.SYSTEM_DEFAULT,
            dynamicColorEnabled = false,
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
            dynamicColorEnabled = false,
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
            dynamicColorEnabled = false,
        ) {
            StudyFlowLoadingPane(modifier = modifier)
        }
        return
    }

    val readyPreferences = (preferencesState as StudyFlowLoadState.Ready<AppPreferences>).value
    val readyPlans = (plansState as StudyFlowLoadState.Ready<List<StudyPlan>>).value
    val readySyncState =
        (syncStateResult as StudyFlowLoadState.Ready<com.mikeyphw.studyflow.core.model.StudySyncState>).value


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
            tvControllerLaunchRequested = tvControllerLaunchRequested,
            onTvControllerLaunchConsumed = onTvControllerLaunchConsumed,
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
    tvControllerLaunchRequested: Boolean = false,
    onTvControllerLaunchConsumed: () -> Unit = {},
) {
    StudyFlowV2Navigation(
        container = container,
        plans = plans,
        preferences = preferences,
        syncState = syncState,
        modifier = modifier,
        reminderLaunch = reminderLaunch,
        onReminderLaunchConsumed = onReminderLaunchConsumed,
        tvControllerLaunchRequested = tvControllerLaunchRequested,
        onTvControllerLaunchConsumed = onTvControllerLaunchConsumed,
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
    val resources = LocalResources.current
    val copySuffix = stringResource(R.string.plan_copy_suffix)
    val debugClipboardLabel = stringResource(R.string.debug_clipboard_label)
    val debugBundleChooserTitle = stringResource(R.string.debug_bundle_chooser_title)
    val motionEnabled = rememberStudyFlowMotionEnabled()

    NavHost(
        navController = navController,
        startDestination = TodayRoute(),
        modifier = modifier.fillMaxSize(),
        enterTransition = {
            slideInHorizontally(
                animationSpec = studyFlowAnimationSpec(motionEnabled),
                initialOffsetX = { width -> width / 20 },
            ) + fadeIn(animationSpec = studyFlowAnimationSpec(motionEnabled))
        },
        exitTransition = {
            slideOutHorizontally(
                animationSpec = studyFlowAnimationSpec(motionEnabled),
                targetOffsetX = { width -> -width / 30 },
            ) + fadeOut(animationSpec = studyFlowAnimationSpec(motionEnabled))
        },
        popEnterTransition = {
            slideInHorizontally(
                animationSpec = studyFlowAnimationSpec(motionEnabled),
                initialOffsetX = { width -> -width / 20 },
            ) + fadeIn(animationSpec = studyFlowAnimationSpec(motionEnabled))
        },
        popExitTransition = {
            slideOutHorizontally(
                animationSpec = studyFlowAnimationSpec(motionEnabled),
                targetOffsetX = { width -> width / 30 },
            ) + fadeOut(animationSpec = studyFlowAnimationSpec(motionEnabled))
        },
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
                if (
                    completed.template == OnboardingSamplePlanTemplate.PUBLIC_EXAM_12_WEEK &&
                    completed.editalImportChoice == PublicExamEditalImportChoice.NOW
                ) {
                    navController.navigate(ContentManagerRoute(completed.planId.value)) {
                        popUpTo<PlanListRoute>()
                    }
                } else if (completed.template == OnboardingSamplePlanTemplate.PUBLIC_EXAM_12_WEEK) {
                    navController.navigate(PlanSettingsRoute(completed.planId.value)) {
                        popUpTo<PlanListRoute>()
                    }
                } else {
                    navController.navigate(DashboardRoute(completed.planId.value)) {
                        popUpTo<PlanListRoute>()
                    }
                }
            }
            OnboardingSamplePlanWizardScreen(
                onBack = navController::popBackStack,
                creating = provisioningState is SamplePlanProvisioningState.InFlight,
                creationFailed = provisioningState is SamplePlanProvisioningState.Failed,
                onCreateSample = { input ->
                    val blueprint = StudyOnboardingSamplePlanWizard.build(input)
                    val template = input.template
                    container.samplePlanProvisioner.clearFailure()
                    container.samplePlanProvisioner.startProvisioning(
                        blueprint = blueprint,
                        planName = resources.getString(template.planNameRes()),
                        planDescription = resources.getString(template.planDescriptionRes()),
                        subjectNames = localizedSubjectNames(resources),
                        topicNames = localizedTopicNames(resources),
                        goalNames = localizedPublicExamGoalNames(resources),
                        examStageNames = localizedExamStageNames(resources),
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
                    val targetFlow = remember(planId) { container.manageStudyContent.observeStudyTarget(planId) }
                    val target by targetFlow.collectAsStateWithLifecycle(initialValue = null)
                    val syllabusFlow = remember(planId) { container.manageStudyContent.observeSyllabus(planId) }
                    val syllabus by syllabusFlow.collectAsStateWithLifecycle(
                        initialValue = com.mikeyphw.studyflow.core.model.StudySyllabus(
                            studyPlanId = planId,
                            subjects = emptyList(),
                            topics = emptyList(),
                        ),
                    )
                    PlanSettingsScreen(
                        plan = currentPlan,
                        settings = settings,
                        studyTarget = target,
                        examStages = syllabus.examStages,
                        onBack = navController::popBackStack,
                        onSave = { updatedSettings ->
                            coroutineScope.launch {
                                container.manageStudyPlan.updateSettings(updatedSettings)
                                container.reminderDeliveryCoordinator.reconcilePlan(updatedSettings.studyPlanId)
                                navController.popBackStack()
                            }
                        },
                        onSavePublicExamProfile = { form ->
                            coroutineScope.launch {
                                persistPublicExamProfile(
                                    container = container,
                                    plan = currentPlan,
                                    existingTarget = target,
                                    form = form,
                                )
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
                    var reminderPolicy by remember(planId, settings.reviewNotificationsEnabled) {
                        mutableStateOf(StudyReminderPolicy.fromLegacy(settings))
                    }
                    var attentionFocusPolicy by remember(planId) {
                        mutableStateOf(container.reminderDeliveryCoordinator.focusPolicyFor(planId, settings))
                    }
                    var reliabilityRefresh by remember(planId) { mutableIntStateOf(0) }
                    LaunchedEffect(planId, settings.reviewNotificationsEnabled) {
                        reminderPolicy = container.reminderDeliveryCoordinator.policyFor(planId)
                        reliabilityRefresh += 1
                    }
                    val runtimeEvidence = remember(planId, reliabilityRefresh) {
                        container.reminderDeliveryCoordinator.runtimeEvidence()
                    }
                    val attentionReliabilityEvidence = remember(planId, reliabilityRefresh) {
                        container.reminderDeliveryCoordinator.attentionReliabilityEvidence(planId)
                    }
                    StudyRemindersScreen(
                        plan = currentPlan,
                        settings = settings,
                        reminderPolicy = reminderPolicy,
                        attentionFocusPolicy = attentionFocusPolicy,
                        runtimeEvidence = runtimeEvidence,
                        attentionReliabilityEvidence = attentionReliabilityEvidence,
                        schedule = container.manageStudySchedule,
                        review = container.manageStudyReview,
                        sessions = container.manageStudySession,
                        onReminderSettingsChanged = { updatedSettings, updatedPolicy ->
                            reminderPolicy = updatedPolicy
                            coroutineScope.launch {
                                container.manageStudyPlan.updateSettings(updatedSettings)
                                container.reminderDeliveryCoordinator.updatePolicy(
                                    updatedSettings.studyPlanId,
                                    updatedPolicy,
                                )
                                reliabilityRefresh += 1
                            }
                        },
                        onAttentionFocusPolicyChanged = { updatedFocusPolicy ->
                            attentionFocusPolicy = updatedFocusPolicy
                            coroutineScope.launch {
                                container.reminderDeliveryCoordinator.updateFocusPolicy(planId, updatedFocusPolicy)
                                reliabilityRefresh += 1
                            }
                        },
                        onRefreshReliability = {
                            coroutineScope.launch {
                                container.reminderDeliveryCoordinator.reconcilePlan(
                                    currentPlan.id,
                                    "reliability-refresh",
                                )
                                reliabilityRefresh += 1
                            }
                        },
                        onNotificationPermissionResult = { granted ->
                            if (granted) {
                                coroutineScope.launch {
                                    container.reminderDeliveryCoordinator.reconcilePlan(
                                        currentPlan.id,
                                        "notification-permission-granted",
                                    )
                                    reliabilityRefresh += 1
                                }
                            }
                        },
                        onCopyAttentionAuditJson = {
                            copyDebugText(
                                context,
                                "StudyFlow ATT11 notification audit JSON",
                                StudyFlowAttentionReliabilityExport.toJson(attentionReliabilityEvidence),
                            )
                        },
                        onCopyAttentionAuditCsv = {
                            copyDebugText(
                                context,
                                "StudyFlow ATT11 notification audit CSV",
                                StudyFlowAttentionReliabilityExport.toCsv(attentionReliabilityEvidence),
                            )
                        },
                        onShareCalendarIcs = {
                            coroutineScope.launch {
                                val feed = container.reminderDeliveryCoordinator.buildCalendarExport(planId)
                                val exportDir = File(context.cacheDir, "calendar-exports").apply { mkdirs() }
                                val calendarFile = File(
                                    exportDir,
                                    "studyflow-${planId.value.replace(Regex("[^A-Za-z0-9._-]"), "-")}.ics",
                                ).apply { writeText(feed.icsText, Charsets.UTF_8) }
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${BuildConfig.APPLICATION_ID}.fileprovider",
                                    calendarFile,
                                )
                                val share = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/calendar"
                                    putExtra(Intent.EXTRA_SUBJECT, feed.preferences.calendarName)
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    clipData = ClipData.newUri(
                                        context.contentResolver,
                                        feed.preferences.calendarName,
                                        uri,
                                    )
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(share, "Share StudyFlow calendar"))
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
            var summary by remember {
                mutableStateOf<com.mikeyphw.studyflow.core.model.StudySyncChangeSummary?>(null)
            }
            var summaryFailed by remember { mutableStateOf(false) }
            var summaryRetryNonce by remember { mutableLongStateOf(0L) }
            var remoteScan by remember { mutableStateOf<StudyManualFolderScan?>(null) }
            var restorePreview by remember { mutableStateOf<StudyManualFolderCheckpointDetails?>(null) }
            var syncHistory by remember { mutableStateOf<StudySyncLedgerSnapshot?>(null) }
            val syncFolderLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocumentTree(),
            ) { uri: Uri? ->
                if (uri == null) return@rememberLauncherForActivityResult
                coroutineScope.launch {
                    val permissionFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    var syncFolderGrantPersisted = false
                    runRecoverableOperation {
                        context.contentResolver.takePersistableUriPermission(uri, permissionFlags)
                        syncFolderGrantPersisted = true
                        container.manageStudyCloudSync.configureLocalFolderProvider(uri.toString())
                    }.onSuccess {
                        remoteScan = null
                        restorePreview = null
                        syncHistory = null
                        summary = container.manageStudyCloudSync.inspectLocalChanges()
                        summaryFailed = false
                    }.onFailure {
                        if (syncFolderGrantPersisted) {
                            runCatching {
                                context.contentResolver.releasePersistableUriPermission(uri, permissionFlags)
                            }
                        }
                        summaryFailed = true
                    }
                }
            }
            LaunchedEffect(syncState.lastLocalChangeToken, summaryRetryNonce) {
                runRecoverableOperation { container.manageStudyCloudSync.inspectLocalChanges() }
                    .onSuccess { value ->
                        summary = value
                        summaryFailed = false
                    }
                    .onFailure { summaryFailed = true }
            }
            SyncFoundationScreen(
                state = syncState,
                summary = summary,
                summaryFailed = summaryFailed,
                onRetrySummary = { summaryRetryNonce += 1 },
                onPrepare = {
                    container.manageStudyCloudSync.prepareLocalFoundation("This device")
                    summary = container.manageStudyCloudSync.inspectLocalChanges()
                    summaryFailed = false
                },
                onDisable = {
                    container.manageStudyCloudSync.clearLocalFoundation()
                    remoteScan = null
                    restorePreview = null
                    syncHistory = null
                    summary = container.manageStudyCloudSync.inspectLocalChanges()
                    summaryFailed = false
                },
                onChooseSyncFolder = { syncFolderLauncher.launch(null) },
                onUploadEncryptedCheckpoint = { password ->
                    container.manageStudyCloudSync.uploadManualFolderCheckpoint(password)
                    remoteScan = container.manageStudyCloudSync.scanManualFolderCheckpoints()
                    restorePreview = null
                    syncHistory = container.manageStudyCloudSync.inspectSyncLedger()
                    summary = container.manageStudyCloudSync.inspectLocalChanges()
                    summaryFailed = false
                },
                onCheckRemoteCheckpoints = {
                    remoteScan = container.manageStudyCloudSync.scanManualFolderCheckpoints()
                    restorePreview = null
                    summary = container.manageStudyCloudSync.inspectLocalChanges()
                    summaryFailed = false
                },
                onPreviewRemoteRestore = { password ->
                    restorePreview = container.manageStudyCloudSync.previewLatestManualFolderRestore(password)
                    remoteScan = container.manageStudyCloudSync.scanManualFolderCheckpoints()
                    syncHistory = container.manageStudyCloudSync.inspectSyncLedger()
                },
                onViewSyncHistory = {
                    syncHistory = container.manageStudyCloudSync.inspectSyncLedger()
                },
                onApplyRemoteRestore = { password ->
                    container.manageStudyCloudSync.applyLatestManualFolderRestore(password)
                    restorePreview = null
                    remoteScan = container.manageStudyCloudSync.scanManualFolderCheckpoints()
                    syncHistory = container.manageStudyCloudSync.inspectSyncLedger()
                    summary = container.manageStudyCloudSync.inspectLocalChanges()
                    summaryFailed = false
                },
                remoteScan = remoteScan,
                restorePreview = restorePreview,
                syncHistory = syncHistory,
                onBack = navController::popBackStack,
            )
        }
        if (BuildConfig.DEBUG) {
        composable<DebugWorkbenchRoute> {
            val debugViewModelFactory = remember(
                container,
                context,
                debugClipboardLabel,
                debugBundleChooserTitle,
            ) {
                StudyFlowDebugWorkbenchViewModel.factory(
                    context = context,
                    container = container,
                    clipboardLabel = debugClipboardLabel,
                    chooserTitle = debugBundleChooserTitle,
                )
            }
            val debugViewModel: StudyFlowDebugWorkbenchViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                factory = debugViewModelFactory,
            )
            LaunchedEffect(preferences.defaultStudyPlanId) {
                debugViewModel.updateDefaultPlanId(preferences.defaultStudyPlanId)
            }
            val debugState by debugViewModel.uiState.collectAsStateWithLifecycle()
            val shellReport = DebugWorkbenchShellPolicy.evaluate(
                store = container.diagnosticRecorder,
                timeline = debugState.timeline,
            )
            DebugWorkbenchScreen(
                shellReport = shellReport,
                timeline = debugState.timeline,
                actionResult = debugState.actionResult,
                selfCheckHistory = debugState.selfCheckHistory,
                supportBundleSealReport = debugState.supportBundleSealReport,
                onRefresh = debugViewModel::refresh,
                onCopyStatus = debugViewModel::copyStatus,
                onCopyTimeline = debugViewModel::copyTimeline,
                onClearHistory = debugViewModel::clearHistory,
                onExportSupportBundle = debugViewModel::exportSupportBundle,
                onOpenRuntimeSmokeWorkbench = { navController.navigate(RuntimeSmokeRoute) },
                onBack = navController::popBackStack,
                probeContent = {
                    DebugProbePanel(
                        report = debugState.probeReport,
                        safeSuiteRunning = debugState.safeSuiteRunning,
                        onRunProbe = debugViewModel::runProbe,
                        onRunSafeSuite = debugViewModel::runSafeSuite,
                    )
                },
                labContent = {
                    DebugLabsPanel(
                        reminderReport = debugState.reminderLabReport,
                        reminderActionResult = debugState.reminderLabAction,
                        reminderRunning = debugState.reminderLabRunning,
                        onInspectReminders = debugViewModel::inspectReminders,
                        onReconcileReminders = debugViewModel::reconcileReminders,
                        onPostDiagnosticNotification = debugViewModel::postDiagnosticNotification,
                        databasePortabilityReport = debugState.databasePortabilityReport,
                        databasePortabilityActionResult = debugState.databasePortabilityAction,
                        databasePortabilityRunning = debugState.databasePortabilityRunning,
                        onInspectDatabase = debugViewModel::inspectDatabase,
                        onRunPortabilityDrills = debugViewModel::runPortabilityDrills,
                        studyEngineReport = debugState.studyEngineReport,
                        studyEngineActionResult = debugState.studyEngineAction,
                        studyEngineRunning = debugState.studyEngineRunning,
                        onInspectStudyEngine = debugViewModel::inspectStudyEngine,
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

            fun runProbe(kind: RuntimeSmokeProbeKind) {
                coroutineScope.launch {
                    val probeId = when (kind) {
                        RuntimeSmokeProbeKind.REMINDER_RECONCILIATION -> DebugProbeId.REMINDER_RECONCILIATION
                        RuntimeSmokeProbeKind.WIDGET_REFRESH -> DebugProbeId.WIDGET_REFRESH
                        RuntimeSmokeProbeKind.BACKUP_DRY_RUN -> DebugProbeId.BACKUP_DRY_RUN
                        RuntimeSmokeProbeKind.SEARCH_QUERY -> DebugProbeId.SEARCH_QUERY
                        RuntimeSmokeProbeKind.RECOMMENDATION_QUERY -> DebugProbeId.RECOMMENDATION_QUERY
                        RuntimeSmokeProbeKind.SYNC_FOUNDATION -> DebugProbeId.SYNC_FOUNDATION
                    }
                    val registry = StudyFlowDebugProbeRegistry(
                        context = context.applicationContext,
                        container = container,
                        defaultPlanId = { preferences.defaultStudyPlanId },
                    )
                    val result = try {
                        registry.run(probeId)
                    } catch (cancelled: java.util.concurrent.CancellationException) {
                        throw cancelled
                    } catch (fatal: Error) {
                        throw fatal
                    } catch (_: Exception) {
                        null
                    }
                    if (result?.status == DebugProbeStatus.PASSED) {
                        markPassed(
                            kind,
                            "Behavioral probe passed (${probeId.name.lowercase()}).",
                        )
                    } else {
                        val detail = result?.let { probeResult ->
                            "${probeResult.status.name}: ${probeResult.message.name}"
                        } ?: "FAILED: probe execution error"
                        markBlocked(kind, detail)
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
        }
        composable<SettingsRoute> {
            SettingsScreen(
                preferences = preferences,
                hasDefaultPlan = preferences.defaultStudyPlanId != null,
                syncState = syncState,
                onLanguageChanged = container::updateAppLanguage,
                onThemeModeChanged = container.appPreferencesRepository::setThemeMode,
                onDynamicColorChanged = container.appPreferencesRepository::setDynamicColorEnabled,
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
                onOpenTvController = { navController.navigate(TvControllerRoute) },
                onOpenDebugWorkbench = { if (BuildConfig.DEBUG) navController.navigate(DebugWorkbenchRoute) },
                diagnosticsAvailable = BuildConfig.DEBUG,
            )
        }
        composable<TvControllerRoute> {
            StudyFlowTvPhoneControllerScreen(
                discovery = container.tvReceiverDiscovery,
                client = container.tvLinkClient,
                presentationSource = container.tvPresentationSource,
                domainActionExecutor = container.tvDomainActionExecutor,
                defaultPlanId = preferences.defaultStudyPlanId,
                onBack = navController::popBackStack,
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
    when (result.type) {
        GlobalSearchContentType.PLAN -> navigate(TodayRoute(planId))
        GlobalSearchContentType.SUBJECT,
        GlobalSearchContentType.TOPIC,
        -> navigate(PlanWorkspaceRoute(planId, PlanWorkspaceTab.CONTENT, result.id))
        GlobalSearchContentType.MATERIAL ->
            navigate(PlanWorkspaceRoute(planId, PlanWorkspaceTab.LIBRARY, result.id))
        GlobalSearchContentType.QUESTION ->
            navigate(PracticeWorkspaceRoute(planId, PracticeWorkspaceTab.QUESTIONS, result.id))
        GlobalSearchContentType.NOTE -> navigate(ProgressWorkspaceRoute(planId, targetId = result.id))
        GlobalSearchContentType.FLASHCARD ->
            navigate(PracticeWorkspaceRoute(planId, PracticeWorkspaceTab.REVIEWS, result.id))
        GlobalSearchContentType.ERROR ->
            navigate(PracticeWorkspaceRoute(planId, PracticeWorkspaceTab.ERRORS, result.id))
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

private fun localizedPublicExamGoalNames(resources: Resources): Map<PublicExamGoalPreset, String> =
    PublicExamGoalPreset.entries.associateWith { preset -> resources.getString(preset.titleRes()) }

private fun localizedExamStageNames(resources: Resources): Map<OnboardingSampleExamStageKind, String> =
    OnboardingSampleExamStageKind.entries.associateWith { stage -> resources.getString(stage.titleRes()) }


private fun PlanEditorForm.toNewStudyPlan(): StudyPlan = StudyPlan(
    id = StudyPlanId(UUID.randomUUID().toString()),
    name = name,
    description = description,
    type = studyType,
    status = StudyPlanStatus.ACTIVE,
    contentMode = contentMode,
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
    contentMode = form.contentMode,
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
