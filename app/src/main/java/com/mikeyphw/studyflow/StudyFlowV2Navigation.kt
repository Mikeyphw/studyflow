package com.mikeyphw.studyflow

import android.os.SystemClock
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.mikeyphw.studyflow.core.designsystem.StudyFlowErrorPane
import com.mikeyphw.studyflow.core.designsystem.StudyFlowLoadingPane
import com.mikeyphw.studyflow.core.domain.ReminderNotificationType
import com.mikeyphw.studyflow.core.domain.asStudyFlowOptionalLoadState
import com.mikeyphw.studyflow.core.domain.StudyRecommendation
import com.mikeyphw.studyflow.core.domain.StudyRecommendationDestination
import com.mikeyphw.studyflow.core.model.AppPreferences
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudyFlowLoadState
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyPlanStatus
import com.mikeyphw.studyflow.core.model.StudySession
import com.mikeyphw.studyflow.core.model.StudySessionId
import com.mikeyphw.studyflow.core.model.StudySessionStatus
import com.mikeyphw.studyflow.core.model.StudySyncState
import com.mikeyphw.studyflow.core.navigation.AnalyticsDetailRoute
import com.mikeyphw.studyflow.core.navigation.AnalyticsRoute
import com.mikeyphw.studyflow.core.navigation.ContentManagerRoute
import com.mikeyphw.studyflow.core.navigation.CreatePlanRoute
import com.mikeyphw.studyflow.core.navigation.DashboardRoute
import com.mikeyphw.studyflow.core.navigation.DebugWorkbenchRoute
import com.mikeyphw.studyflow.core.navigation.EditPlanRoute
import com.mikeyphw.studyflow.core.navigation.FocusSessionRoute
import com.mikeyphw.studyflow.core.navigation.LibraryManagerRoute
import com.mikeyphw.studyflow.core.navigation.LibraryRoute
import com.mikeyphw.studyflow.core.navigation.PlanListRoute
import com.mikeyphw.studyflow.core.navigation.PlanPickerRoute
import com.mikeyphw.studyflow.core.navigation.PlanPortabilityRoute
import com.mikeyphw.studyflow.core.navigation.PlanSettingsRoute
import com.mikeyphw.studyflow.core.navigation.PlanWorkspaceRoute
import com.mikeyphw.studyflow.core.navigation.PlanWorkspaceTab
import com.mikeyphw.studyflow.core.navigation.PracticeRoute
import com.mikeyphw.studyflow.core.navigation.PracticeWorkspaceRoute
import com.mikeyphw.studyflow.core.navigation.PracticeWorkspaceTab
import com.mikeyphw.studyflow.core.navigation.PrivacyRoute
import com.mikeyphw.studyflow.core.navigation.ProgressCenterRoute
import com.mikeyphw.studyflow.core.navigation.ProgressRoute
import com.mikeyphw.studyflow.core.navigation.ProgressWorkspacePeriod
import com.mikeyphw.studyflow.core.navigation.ProgressWorkspaceRoute
import com.mikeyphw.studyflow.core.navigation.QuestionBankRoute
import com.mikeyphw.studyflow.core.navigation.QuickAddRoute
import com.mikeyphw.studyflow.core.navigation.RemindersRoute
import com.mikeyphw.studyflow.core.navigation.ReviewCenterRoute
import com.mikeyphw.studyflow.core.navigation.ReviewsRoute
import com.mikeyphw.studyflow.core.navigation.ScheduleManagerRoute
import com.mikeyphw.studyflow.core.navigation.ScheduleRoute
import com.mikeyphw.studyflow.core.navigation.SearchRoute
import com.mikeyphw.studyflow.core.navigation.SessionsRoute
import com.mikeyphw.studyflow.core.navigation.SettingsRoute
import com.mikeyphw.studyflow.core.navigation.StudyFlowWorkspaceRoot
import com.mikeyphw.studyflow.core.navigation.SubjectsRoute
import com.mikeyphw.studyflow.core.navigation.SyncRoute
import com.mikeyphw.studyflow.core.navigation.SystemHubRoute
import com.mikeyphw.studyflow.core.navigation.TodayRoute
import com.mikeyphw.studyflow.feature.analytics.StudyAnalyticsScreen
import com.mikeyphw.studyflow.feature.library.StudyLibraryScreen
import com.mikeyphw.studyflow.feature.practice.PracticeScreen
import com.mikeyphw.studyflow.feature.progress.StudyProgressScreen
import com.mikeyphw.studyflow.feature.reviews.StudyReviewScreen
import com.mikeyphw.studyflow.feature.schedule.StudyScheduleScreen
import com.mikeyphw.studyflow.feature.subjects.StudyContentScreen
import com.mikeyphw.studyflow.feature.workspace.FocusSessionScreen
import com.mikeyphw.studyflow.feature.workspace.PlanPickerScreen
import com.mikeyphw.studyflow.feature.workspace.PlanWorkspaceScreen
import com.mikeyphw.studyflow.feature.workspace.PracticeWorkspaceScreen
import com.mikeyphw.studyflow.feature.workspace.ProgressWorkspaceScreen
import com.mikeyphw.studyflow.feature.workspace.QuickAddScreen
import com.mikeyphw.studyflow.feature.workspace.SystemHubScreen
import com.mikeyphw.studyflow.feature.workspace.TodayWorkspaceScreen
import com.mikeyphw.studyflow.feature.workspace.WorkspaceNoPlanScreen
import com.mikeyphw.studyflow.feature.workspace.R as WorkspaceR
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

@Composable
internal fun StudyFlowV2Navigation(
    container: AppContainer,
    plans: List<StudyPlan>,
    preferences: AppPreferences,
    syncState: StudySyncState,
    modifier: Modifier = Modifier,
    reminderLaunch: StudyReminderLaunch? = null,
    onReminderLaunchConsumed: () -> Unit = {},
) {
    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState()
    val destination = entry?.destination
    val canonicalDefaultPlan = resolveCanonicalDefaultPlan(plans, preferences)
    val workspaceFallbackPlan = resolveWorkspacePlan(plans, preferences, "")
    val currentPlanId = entry.workspacePlanId().ifBlank { workspaceFallbackPlan?.id?.value.orEmpty() }
    val selectedRoot = destination.workspaceRoot()
    val activeSessions by remember(container.manageStudySession) {
        container.manageStudySession.observeActiveSessions()
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val activeSession = activeSessions.singleOrNull()
    var activeSessionTick by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val shellScope = rememberCoroutineScope()

    LaunchedEffect(activeSession?.id, activeSession?.status) {
        while (activeSession?.status == StudySessionStatus.RUNNING) {
            activeSessionTick = SystemClock.elapsedRealtime()
            delay(1_000)
        }
    }

    LaunchedEffect(preferences.defaultStudyPlanId, plans) {
        val staleDefaultId = preferences.defaultStudyPlanId
        if (staleDefaultId != null && canonicalDefaultPlan == null) {
            container.appPreferencesRepository.clearDefaultStudyPlanIf(staleDefaultId)
        }
    }

    LaunchedEffect(reminderLaunch, plans) {
        val launch = reminderLaunch ?: return@LaunchedEffect
        if (plans.isEmpty()) return@LaunchedEffect
        val plan = plans.firstOrNull { candidate ->
            candidate.id.value == launch.planId && candidate.status !in setOf(
                StudyPlanStatus.ARCHIVED,
                StudyPlanStatus.TRASHED,
            )
        }
        if (plan == null) {
            onReminderLaunchConsumed()
            return@LaunchedEffect
        }
        when (launch.type) {
            ReminderNotificationType.DUE_REVIEWS -> navController.navigate(
                PracticeWorkspaceRoute(plan.id.value, PracticeWorkspaceTab.REVIEWS),
            )
            ReminderNotificationType.PLANNED_ACTIVITY,
            ReminderNotificationType.MISSED_ACTIVITY -> navController.navigate(
                PlanWorkspaceRoute(plan.id.value, PlanWorkspaceTab.SCHEDULE),
            )
            ReminderNotificationType.DAILY_SUMMARY -> navController.navigate(TodayRoute(plan.id.value))
        }
        onReminderLaunchConsumed()
    }

    val content: @Composable () -> Unit = {
        StudyFlowNavHost(
            navController = navController,
            container = container,
            plans = plans,
            preferences = preferences,
            syncState = syncState,
            modifier = Modifier.fillMaxSize(),
        )
    }

    if (selectedRoot == null) {
        content()
    } else {
        StudyFlowShell(
            selectedRoot = selectedRoot,
            labels = mapOf(
                StudyFlowWorkspaceRoot.TODAY to stringResource(WorkspaceR.string.today_title),
                StudyFlowWorkspaceRoot.PLAN to stringResource(WorkspaceR.string.plan_title),
                StudyFlowWorkspaceRoot.PRACTICE to stringResource(WorkspaceR.string.practice_title),
                StudyFlowWorkspaceRoot.PROGRESS to stringResource(WorkspaceR.string.progress_title),
            ),
            onNavigate = { root ->
                navController.navigateWorkspaceRoot(root, currentPlanId)
            },
            activeSession = activeSession,
            activeSessionElapsed = activeSession?.let { session ->
                formatShellDuration(
                    remember(session, activeSessionTick) { container.manageStudySession.liveActiveSeconds(session) },
                )
            }.orEmpty(),
            activeSessionStatusLabel = activeSession?.let { session ->
                stringResource(
                    if (session.status == StudySessionStatus.PAUSED) {
                        WorkspaceR.string.status_paused
                    } else {
                        WorkspaceR.string.active_session
                    },
                )
            }.orEmpty(),
            pauseLabel = stringResource(WorkspaceR.string.pause),
            resumeLabel = stringResource(WorkspaceR.string.resume),
            onOpenActiveSession = { activeSession?.let { navController.navigate(it.focusRoute()) } },
            onToggleActiveSession = {
                activeSession?.let { session ->
                    shellScope.launch {
                        try {
                            if (session.status == StudySessionStatus.PAUSED) {
                                container.manageStudySession.resume(session.studyPlanId, session.id)
                            } else {
                                container.manageStudySession.pause(session.studyPlanId, session.id)
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            navController.navigate(session.focusRoute())
                        }
                    }
                }
            },
            modifier = modifier.fillMaxSize(),
            content = content,
        )
    }
}

internal fun NavGraphBuilder.studyFlowV2Destinations(
    navController: NavHostController,
    container: AppContainer,
    plans: List<StudyPlan>,
    preferences: AppPreferences,
) {
    composable<TodayRoute> { entry ->
        val route = entry.toRoute<TodayRoute>()
        val plan = resolveWorkspacePlan(plans, preferences, route.planId)
        if (plan == null) {
            WorkspaceNoPlanScreen(
                title = stringResource(WorkspaceR.string.today_title),
                onCreatePlan = { navController.navigate(CreatePlanRoute) },
                onSwitchPlan = {
                    navController.navigate(PlanPickerRoute(StudyFlowWorkspaceRoot.TODAY, route.planId))
                },
                onSearch = { navController.navigate(SearchRoute) },
                onSystem = { navController.navigate(SystemHubRoute(route.planId)) },
            )
        } else {
            TodayWorkspaceScreen(
                plan = plan,
                scheduleManager = container.manageStudySchedule,
                sessionManager = container.manageStudySession,
                reviewManager = container.manageStudyReview,
                analyticsManager = container.manageStudyAnalytics,
                discoveryManager = container.manageStudyDiscovery,
                publicExamManager = container.managePublicExam,
                onSwitchPlan = {
                    navController.navigate(PlanPickerRoute(StudyFlowWorkspaceRoot.TODAY, plan.id.value))
                },
                onSearch = { navController.navigate(SearchRoute) },
                onSystem = { navController.navigate(SystemHubRoute(plan.id.value)) },
                onQuickAdd = {
                    navController.navigate(QuickAddRoute(StudyFlowWorkspaceRoot.TODAY, plan.id.value))
                },
                onOpenFocus = { session -> navController.navigate(session.focusRoute()) },
                onOpenReviews = {
                    navController.navigate(
                        PracticeWorkspaceRoute(plan.id.value, PracticeWorkspaceTab.REVIEWS),
                    )
                },
                onOpenSchedule = {
                    navController.navigate(
                        PlanWorkspaceRoute(plan.id.value, PlanWorkspaceTab.SCHEDULE),
                    )
                },
                onOpenRecommendation = { recommendation ->
                    navController.navigateToWorkspaceRecommendation(recommendation)
                },
            )
        }
    }

    composable<PlanWorkspaceRoute> { entry ->
        val route = entry.toRoute<PlanWorkspaceRoute>()
        val plan = resolveWorkspacePlan(plans, preferences, route.planId)
        if (plan == null) {
            WorkspaceNoPlanScreen(
                title = stringResource(WorkspaceR.string.plan_title),
                onCreatePlan = { navController.navigate(CreatePlanRoute) },
                onSwitchPlan = {
                    navController.navigate(
                        PlanPickerRoute(
                            root = StudyFlowWorkspaceRoot.PLAN,
                            planId = route.planId,
                            planTab = route.tab,
                        ),
                    )
                },
                onSearch = { navController.navigate(SearchRoute) },
                onSystem = { navController.navigate(SystemHubRoute(route.planId)) },
            )
        } else {
            PlanWorkspaceScreen(
                plan = plan,
                selectedTab = route.tab,
                targetId = route.targetId,
                scheduleManager = container.manageStudySchedule,
                contentManager = container.manageStudyContent,
                libraryManager = container.manageStudyLibrary,
                practiceManager = container.manageStudyPractice,
                reviewManager = container.manageStudyReview,
                progressManager = container.manageStudyProgress,
                sessionManager = container.manageStudySession,
                onTabSelected = { tab ->
                    navController.navigateWorkspaceTab(PlanWorkspaceRoute(plan.id.value, tab))
                },
                onSwitchPlan = {
                    navController.navigate(
                        PlanPickerRoute(
                            root = StudyFlowWorkspaceRoot.PLAN,
                            planId = plan.id.value,
                            planTab = route.tab,
                        ),
                    )
                },
                onSearch = { navController.navigate(SearchRoute) },
                onSystem = { navController.navigate(SystemHubRoute(plan.id.value)) },
                onEditPlan = { navController.navigate(EditPlanRoute(plan.id.value)) },
                onPlanSettings = { navController.navigate(PlanSettingsRoute(plan.id.value)) },
                onSessions = { navController.navigate(SessionsRoute(plan.id.value)) },
                onReminders = { navController.navigate(RemindersRoute(plan.id.value)) },
                onPortability = { navController.navigate(PlanPortabilityRoute(plan.id.value)) },
                onManageSchedule = { navController.navigate(ScheduleManagerRoute(plan.id.value)) },
                onManageContent = { navController.navigate(ContentManagerRoute(plan.id.value)) },
                onManageLibrary = { navController.navigate(LibraryManagerRoute(plan.id.value)) },
            )
        }
    }

    composable<PracticeWorkspaceRoute> { entry ->
        val route = entry.toRoute<PracticeWorkspaceRoute>()
        val plan = resolveWorkspacePlan(plans, preferences, route.planId)
        if (plan == null) {
            WorkspaceNoPlanScreen(
                title = stringResource(WorkspaceR.string.practice_title),
                onCreatePlan = { navController.navigate(CreatePlanRoute) },
                onSwitchPlan = {
                    navController.navigate(
                        PlanPickerRoute(
                            root = StudyFlowWorkspaceRoot.PRACTICE,
                            planId = route.planId,
                            practiceTab = route.tab,
                        ),
                    )
                },
                onSearch = { navController.navigate(SearchRoute) },
                onSystem = { navController.navigate(SystemHubRoute(route.planId)) },
            )
        } else {
            PracticeWorkspaceScreen(
                plan = plan,
                selectedTab = route.tab,
                targetId = route.targetId,
                practiceManager = container.manageStudyPractice,
                reviewManager = container.manageStudyReview,
                contentManager = container.manageStudyContent,
                onTabSelected = { tab ->
                    navController.navigateWorkspaceTab(PracticeWorkspaceRoute(plan.id.value, tab))
                },
                onSwitchPlan = {
                    navController.navigate(
                        PlanPickerRoute(
                            root = StudyFlowWorkspaceRoot.PRACTICE,
                            planId = plan.id.value,
                            practiceTab = route.tab,
                        ),
                    )
                },
                onSearch = { navController.navigate(SearchRoute) },
                onSystem = { navController.navigate(SystemHubRoute(plan.id.value)) },
                onQuickAdd = {
                    navController.navigate(QuickAddRoute(StudyFlowWorkspaceRoot.PRACTICE, plan.id.value))
                },
                onOpenReviewCenter = { navController.navigate(ReviewCenterRoute(plan.id.value)) },
                onOpenQuestionBank = { navController.navigate(QuestionBankRoute(plan.id.value)) },
            )
        }
    }

    composable<ProgressWorkspaceRoute> { entry ->
        val route = entry.toRoute<ProgressWorkspaceRoute>()
        val plan = resolveWorkspacePlan(plans, preferences, route.planId)
        if (plan == null) {
            WorkspaceNoPlanScreen(
                title = stringResource(WorkspaceR.string.progress_title),
                onCreatePlan = { navController.navigate(CreatePlanRoute) },
                onSwitchPlan = {
                    navController.navigate(
                        PlanPickerRoute(
                            root = StudyFlowWorkspaceRoot.PROGRESS,
                            planId = route.planId,
                            progressPeriod = route.period,
                        ),
                    )
                },
                onSearch = { navController.navigate(SearchRoute) },
                onSystem = { navController.navigate(SystemHubRoute(route.planId)) },
            )
        } else {
            val context = LocalContext.current
            val reportExporter = remember(context) {
                StudyFlowExamReportExporter(context.applicationContext)
            }
            ProgressWorkspaceScreen(
                plan = plan,
                selectedPeriod = route.period,
                targetId = route.targetId,
                analyticsManager = container.manageStudyAnalytics,
                planManager = container.manageStudyPlan,
                progressManager = container.manageStudyProgress,
                contentManager = container.manageStudyContent,
                libraryManager = container.manageStudyLibrary,
                adaptiveRescheduleManager = container.manageAdaptiveReschedule,
                sessionManager = container.manageStudySession,
                scheduleManager = container.manageStudySchedule,
                reviewManager = container.manageStudyReview,
                practiceManager = container.manageStudyPractice,
                onPeriodSelected = { period ->
                    navController.navigateWorkspaceTab(ProgressWorkspaceRoute(plan.id.value, period))
                },
                onSwitchPlan = {
                    navController.navigate(
                        PlanPickerRoute(
                            root = StudyFlowWorkspaceRoot.PROGRESS,
                            planId = plan.id.value,
                            progressPeriod = route.period,
                        ),
                    )
                },
                onSearch = { navController.navigate(SearchRoute) },
                onSystem = { navController.navigate(SystemHubRoute(plan.id.value)) },
                onOpenProgressCenter = { navController.navigate(ProgressCenterRoute(plan.id.value)) },
                onOpenAnalytics = { navController.navigate(AnalyticsDetailRoute(plan.id.value)) },
                onOpenPlanOverview = {
                    navController.navigate(
                        PlanWorkspaceRoute(plan.id.value, PlanWorkspaceTab.OVERVIEW),
                    )
                },
                onOpenPlan = {
                    navController.navigate(
                        PlanWorkspaceRoute(plan.id.value, PlanWorkspaceTab.SCHEDULE),
                    )
                },
                onOpenContent = {
                    navController.navigate(
                        PlanWorkspaceRoute(plan.id.value, PlanWorkspaceTab.CONTENT),
                    )
                },
                onOpenPractice = {
                    navController.navigate(
                        PracticeWorkspaceRoute(plan.id.value, PracticeWorkspaceTab.QUESTIONS),
                    )
                },
                onOpenReviews = {
                    navController.navigate(
                        PracticeWorkspaceRoute(plan.id.value, PracticeWorkspaceTab.REVIEWS),
                    )
                },
                onExportExamReport = { document, format, chooserTitle ->
                    reportExporter.exportAndShare(document, format, chooserTitle)
                },
            )
        }
    }

    composable<FocusSessionRoute> { entry ->
        val route = entry.toRoute<FocusSessionRoute>()
        WorkspacePlanDestination(
            navController = navController,
            container = container,
            planId = route.planId,
        ) { currentPlan ->
            FocusSessionScreen(
                plan = currentPlan,
                sessionId = StudySessionId(route.sessionId),
                sessionManager = container.manageStudySession,
                onFinished = {
                    navController.navigateWorkspaceRoot(
                        root = StudyFlowWorkspaceRoot.TODAY,
                        planId = currentPlan.id.value,
                        restoreSavedStack = false,
                    )
                },
                onCancelled = {
                    navController.navigateWorkspaceRoot(
                        root = StudyFlowWorkspaceRoot.TODAY,
                        planId = currentPlan.id.value,
                        restoreSavedStack = false,
                    )
                },
            )
        }
    }

    composable<ScheduleManagerRoute> { entry ->
        val route = entry.toRoute<ScheduleManagerRoute>()
        WorkspacePlanDestination(
            navController = navController,
            container = container,
            planId = route.planId,
        ) { plan ->
            StudyScheduleScreen(
                plan = plan,
                scheduleManager = container.manageStudySchedule,
                contentManager = container.manageStudyContent,
                publicExamManager = container.managePublicExam,
                onBack = navController::popBackStack,
            )
        }
    }

    composable<ContentManagerRoute> { entry ->
        val route = entry.toRoute<ContentManagerRoute>()
        WorkspacePlanDestination(
            navController = navController,
            container = container,
            planId = route.planId,
        ) { plan ->
            StudyContentScreen(
                plan = plan,
                content = container.manageStudyContent,
                publicExamManager = container.managePublicExam,
                onBack = navController::popBackStack,
            )
        }
    }

    composable<LibraryManagerRoute> { entry ->
        val route = entry.toRoute<LibraryManagerRoute>()
        WorkspacePlanDestination(
            navController = navController,
            container = container,
            planId = route.planId,
        ) { plan ->
            StudyLibraryScreen(
                plan = plan,
                library = container.manageStudyLibrary,
                annexFiles = container.manageAnnexFileStorage,
                resourceViewer = container.manageStudyResourceViewer,
                onBack = navController::popBackStack,
            )
        }
    }

    composable<QuestionBankRoute> { entry ->
        val route = entry.toRoute<QuestionBankRoute>()
        WorkspacePlanDestination(
            navController = navController,
            container = container,
            planId = route.planId,
        ) { plan ->
            PracticeScreen(
                plan = plan,
                practice = container.manageStudyPractice,
                review = container.manageStudyReview,
                onBack = navController::popBackStack,
            )
        }
    }

    composable<ReviewCenterRoute> { entry ->
        val route = entry.toRoute<ReviewCenterRoute>()
        WorkspacePlanDestination(
            navController = navController,
            container = container,
            planId = route.planId,
        ) { plan ->
            StudyReviewScreen(
                plan = plan,
                review = container.manageStudyReview,
                practice = container.manageStudyPractice,
                content = container.manageStudyContent,
                publicExamManager = container.managePublicExam,
                onBack = navController::popBackStack,
            )
        }
    }

    composable<ProgressCenterRoute> { entry ->
        val route = entry.toRoute<ProgressCenterRoute>()
        WorkspacePlanDestination(
            navController = navController,
            container = container,
            planId = route.planId,
        ) { plan ->
            StudyProgressScreen(
                plan = plan,
                progress = container.manageStudyProgress,
                onBack = navController::popBackStack,
            )
        }
    }

    composable<AnalyticsDetailRoute> { entry ->
        val route = entry.toRoute<AnalyticsDetailRoute>()
        WorkspacePlanDestination(
            navController = navController,
            container = container,
            planId = route.planId,
        ) { plan ->
            StudyAnalyticsScreen(
                plan = plan,
                analytics = container.manageStudyAnalytics,
                onBack = navController::popBackStack,
            )
        }
    }

    composable<PlanPickerRoute> { entry ->
        val route = entry.toRoute<PlanPickerRoute>()
        val selectedPlan = resolveWorkspacePlan(plans, preferences, route.planId)
        val activeSessions by remember(container.manageStudySession) {
            container.manageStudySession.observeActiveSessions()
        }.collectAsStateWithLifecycle(initialValue = emptyList())
        val activeSession = activeSessions.singleOrNull()
        PlanPickerScreen(
            plans = plans,
            selectedPlanId = selectedPlan?.id?.value ?: route.planId,
            activeSession = activeSession,
            onPlanSelected = { plan ->
                navController.clearWorkspaceSavedStacks()
                navController.navigateWorkspaceRoot(
                    root = route.root,
                    planId = plan.id.value,
                    restoreSavedStack = false,
                    planTab = route.planTab,
                    practiceTab = route.practiceTab,
                    progressPeriod = route.progressPeriod,
                )
            },
            onCreatePlan = { navController.navigate(CreatePlanRoute) },
            onBack = navController::popBackStack,
        )
    }

    composable<QuickAddRoute> { entry ->
        val route = entry.toRoute<QuickAddRoute>()
        val plan = resolveWorkspacePlan(plans, preferences, route.planId)
        if (plan == null) {
            StudyFlowErrorPane(
                message = stringResource(R.string.unknown_plan_error),
                onRetry = null,
            )
        } else {
            QuickAddScreen(
                plan = plan,
                sourceRoot = route.root,
                sessionManager = container.manageStudySession,
                onSessionStarted = { session -> navController.navigate(session.focusRoute()) },
                onStudyBlock = { navController.navigate(ScheduleManagerRoute(plan.id.value)) },
                onQuestion = { navController.navigate(QuestionBankRoute(plan.id.value)) },
                onGoalOrNote = { navController.navigate(ProgressCenterRoute(plan.id.value)) },
                onMaterial = { navController.navigate(LibraryManagerRoute(plan.id.value)) },
                onBack = navController::popBackStack,
            )
        }
    }

    composable<SystemHubRoute> { entry ->
        val route = entry.toRoute<SystemHubRoute>()
        val plan = resolveWorkspacePlan(plans, preferences, route.planId)
        SystemHubScreen(
            hasPlan = plan != null,
            diagnosticsAvailable = BuildConfig.DEBUG,
            onAllPlans = { navController.navigate(PlanListRoute) },
            onSettings = { navController.navigate(SettingsRoute) },
            onReminders = { plan?.let { navController.navigate(RemindersRoute(it.id.value)) } },
            onPortability = { plan?.let { navController.navigate(PlanPortabilityRoute(it.id.value)) } },
            onSync = { navController.navigate(SyncRoute) },
            onPrivacy = { navController.navigate(PrivacyRoute) },
            onDebug = { navController.navigate(DebugWorkbenchRoute) },
            onBack = navController::popBackStack,
        )
    }
}

@Composable
private fun WorkspacePlanDestination(
    navController: NavHostController,
    container: AppContainer,
    planId: String,
    content: @Composable (StudyPlan) -> Unit,
) {
    val typedPlanId = remember(planId) { StudyPlanId(planId) }
    var loadAttempt by remember(typedPlanId) { mutableIntStateOf(0) }
    val planFlow = remember(typedPlanId, loadAttempt) {
        container.manageStudyPlan.observePlan(typedPlanId).asStudyFlowOptionalLoadState()
    }
    val planState by planFlow.collectAsStateWithLifecycle(initialValue = StudyFlowLoadState.Loading)
    when (val state = planState) {
        StudyFlowLoadState.Loading -> StudyFlowLoadingPane()
        StudyFlowLoadState.Missing -> StudyFlowErrorPane(
            message = stringResource(R.string.unknown_plan_error),
            onRetry = null,
        )
        is StudyFlowLoadState.Failed -> StudyFlowErrorPane(
            message = stringResource(R.string.app_initialization_failed),
            onRetry = { loadAttempt += 1 },
        )
        is StudyFlowLoadState.Ready -> {
            if (state.value.status in setOf(StudyPlanStatus.ARCHIVED, StudyPlanStatus.TRASHED)) {
                StudyFlowErrorPane(
                    message = stringResource(R.string.unknown_plan_error),
                    onRetry = null,
                )
            } else {
                content(state.value)
            }
        }
    }
}

internal fun NavHostController.clearWorkspaceSavedStacks() {
    clearBackStack<TodayRoute>()
    clearBackStack<PlanWorkspaceRoute>()
    clearBackStack<PracticeWorkspaceRoute>()
    clearBackStack<ProgressWorkspaceRoute>()
}

internal fun NavHostController.navigateWorkspaceRoot(
    root: StudyFlowWorkspaceRoot,
    planId: String,
    restoreSavedStack: Boolean = true,
    planTab: PlanWorkspaceTab = PlanWorkspaceTab.OVERVIEW,
    practiceTab: PracticeWorkspaceTab = PracticeWorkspaceTab.REVIEWS,
    progressPeriod: ProgressWorkspacePeriod = ProgressWorkspacePeriod.SEVEN_DAYS,
) {
    when (root) {
        StudyFlowWorkspaceRoot.TODAY -> navigateWorkspaceRootRoute(
            TodayRoute(planId),
            restoreSavedStack,
        )
        StudyFlowWorkspaceRoot.PLAN -> navigateWorkspaceRootRoute(
            PlanWorkspaceRoute(planId, planTab),
            restoreSavedStack,
        )
        StudyFlowWorkspaceRoot.PRACTICE -> navigateWorkspaceRootRoute(
            PracticeWorkspaceRoute(planId, practiceTab),
            restoreSavedStack,
        )
        StudyFlowWorkspaceRoot.PROGRESS -> navigateWorkspaceRootRoute(
            ProgressWorkspaceRoute(planId, progressPeriod),
            restoreSavedStack,
        )
    }
}

private inline fun <reified T : Any> NavHostController.navigateWorkspaceRootRoute(
    target: T,
    restoreSavedStack: Boolean,
) {
    navigate(target) {
        popUpTo(graph.findStartDestination().id) {
            saveState = restoreSavedStack
        }
        launchSingleTop = true
        restoreState = restoreSavedStack
    }
}

internal inline fun <reified T : Any> NavHostController.navigateWorkspaceTab(target: T) {
    val currentId = currentDestination?.id
    navigate(target) {
        currentId?.let { destinationId ->
            popUpTo(destinationId) { inclusive = true }
        }
        launchSingleTop = true
    }
}

internal fun NavHostController.navigateToWorkspaceRecommendation(
    recommendation: StudyRecommendation,
) {
    val planId = recommendation.studyPlanId.value
    when (recommendation.destination) {
        StudyRecommendationDestination.REVIEWS -> navigateWorkspaceRecommendationTarget(
            PracticeWorkspaceRoute(planId, PracticeWorkspaceTab.REVIEWS),
        )
        StudyRecommendationDestination.SCHEDULE -> navigateWorkspaceRecommendationTarget(
            PlanWorkspaceRoute(planId, PlanWorkspaceTab.SCHEDULE),
        )
        StudyRecommendationDestination.SUBJECTS -> navigateWorkspaceRecommendationTarget(
            PlanWorkspaceRoute(planId, PlanWorkspaceTab.CONTENT),
        )
        StudyRecommendationDestination.PRACTICE -> navigateWorkspaceRecommendationTarget(
            PracticeWorkspaceRoute(planId, PracticeWorkspaceTab.QUESTIONS),
        )
        StudyRecommendationDestination.LIBRARY -> navigateWorkspaceRecommendationTarget(
            PlanWorkspaceRoute(planId, PlanWorkspaceTab.LIBRARY),
        )
    }
}

private inline fun <reified T : Any> NavHostController.navigateWorkspaceRecommendationTarget(
    target: T,
) {
    navigate(target) { launchSingleTop = true }
}

private fun NavBackStackEntry?.workspacePlanId(): String {
    val entry = this ?: return ""
    return when {
        destination.hasRoute<TodayRoute>() -> toRoute<TodayRoute>().planId
        destination.hasRoute<PlanWorkspaceRoute>() -> toRoute<PlanWorkspaceRoute>().planId
        destination.hasRoute<PracticeWorkspaceRoute>() -> toRoute<PracticeWorkspaceRoute>().planId
        destination.hasRoute<ProgressWorkspaceRoute>() -> toRoute<ProgressWorkspaceRoute>().planId
        else -> ""
    }
}

private fun androidx.navigation.NavDestination?.workspaceRoot(): StudyFlowWorkspaceRoot? = when {
    this?.hasRoute<TodayRoute>() == true -> StudyFlowWorkspaceRoot.TODAY
    this?.hasRoute<PlanWorkspaceRoute>() == true -> StudyFlowWorkspaceRoot.PLAN
    this?.hasRoute<PracticeWorkspaceRoute>() == true -> StudyFlowWorkspaceRoot.PRACTICE
    this?.hasRoute<ProgressWorkspaceRoute>() == true -> StudyFlowWorkspaceRoot.PROGRESS
    else -> null
}

internal fun resolveWorkspacePlan(
    plans: List<StudyPlan>,
    preferences: AppPreferences,
    requestedPlanId: String,
): StudyPlan? {
    val visible = plans.filter(::isWorkspaceEligiblePlan)
    if (requestedPlanId.isNotBlank()) {
        // An explicit route id is authoritative. A stale id must never display another plan.
        return visible.firstOrNull { it.id.value == requestedPlanId }
    }
    return preferences.defaultStudyPlanId
        ?.let { defaultId -> visible.firstOrNull { it.id == defaultId } }
        ?: visible.firstOrNull { it.status == StudyPlanStatus.ACTIVE }
        ?: visible.firstOrNull()
}

internal fun resolveCanonicalDefaultPlan(
    plans: List<StudyPlan>,
    preferences: AppPreferences,
): StudyPlan? = preferences.defaultStudyPlanId?.let { defaultId ->
    plans.firstOrNull { plan -> plan.id == defaultId && isWorkspaceEligiblePlan(plan) }
}

internal fun isWorkspaceEligiblePlan(plan: StudyPlan): Boolean =
    plan.status !in setOf(StudyPlanStatus.ARCHIVED, StudyPlanStatus.TRASHED)

private fun formatShellDuration(totalSeconds: Long): String {
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

private fun StudySession.focusRoute(): FocusSessionRoute = FocusSessionRoute(
    planId = studyPlanId.value,
    sessionId = id.value,
)

@Composable
internal inline fun <reified T : Any> LegacyWorkspaceRedirect(
    navController: NavHostController,
    target: T,
) {
    LaunchedEffect(target) {
        val legacyDestinationId = navController.currentDestination?.id
        navController.navigate(target) {
            legacyDestinationId?.let { destinationId ->
                popUpTo(destinationId) { inclusive = true }
            }
            launchSingleTop = true
        }
    }
    com.mikeyphw.studyflow.core.designsystem.StudyFlowLoadingPane()
}
