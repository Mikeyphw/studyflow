package com.mikeyphw.studyflow.feature.workspace

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mikeyphw.studyflow.core.designsystem.StudyFlowEmptyState
import com.mikeyphw.studyflow.core.designsystem.StudyFlowActionOrb
import com.mikeyphw.studyflow.core.designsystem.StudyFlowFocusActionSurface
import com.mikeyphw.studyflow.core.designsystem.StudyFlowMetricStrip
import com.mikeyphw.studyflow.core.designsystem.StudyFlowSectionTitle
import com.mikeyphw.studyflow.core.designsystem.StudyFlowTimelineItem
import com.mikeyphw.studyflow.core.designsystem.workspaceContentPadding
import com.mikeyphw.studyflow.core.domain.ManagePublicExam
import com.mikeyphw.studyflow.core.domain.ManageStudyAnalytics
import com.mikeyphw.studyflow.core.domain.StudyCycleAction
import com.mikeyphw.studyflow.core.domain.StudyActivityLaunchDestination
import com.mikeyphw.studyflow.core.domain.StudyActivityLaunchPlanner
import com.mikeyphw.studyflow.core.domain.ManageStudyDiscovery
import com.mikeyphw.studyflow.core.domain.ManageStudyReview
import com.mikeyphw.studyflow.core.domain.ManageStudySchedule
import com.mikeyphw.studyflow.core.domain.ManageStudySession
import com.mikeyphw.studyflow.core.domain.asStudyFlowLoadState
import com.mikeyphw.studyflow.core.domain.StudyRecommendation
import com.mikeyphw.studyflow.core.domain.StudyRecommendationPresentation
import com.mikeyphw.studyflow.core.domain.StudySessionTimer
import com.mikeyphw.studyflow.core.model.PlannedActivity
import com.mikeyphw.studyflow.core.model.PlannedActivityStatus
import com.mikeyphw.studyflow.core.model.ProgrammingCourseDashboardSummary
import com.mikeyphw.studyflow.core.model.StudyContentMode
import com.mikeyphw.studyflow.core.model.capabilities
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudySession
import com.mikeyphw.studyflow.core.model.StudySessionId
import com.mikeyphw.studyflow.core.model.StudySessionStatus
import com.mikeyphw.studyflow.core.model.StudyStrategyType
import com.mikeyphw.studyflow.core.model.StudyType
import com.mikeyphw.studyflow.core.model.SubjectId
import com.mikeyphw.studyflow.core.model.TopicId
import com.mikeyphw.studyflow.core.model.StudyFlowLoadState
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.UUID
import java.util.concurrent.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TodayWorkspaceScreen(
    plan: StudyPlan,
    scheduleManager: ManageStudySchedule,
    sessionManager: ManageStudySession,
    reviewManager: ManageStudyReview,
    analyticsManager: ManageStudyAnalytics,
    discoveryManager: ManageStudyDiscovery,
    publicExamManager: ManagePublicExam,
    onSwitchPlan: () -> Unit,
    onSearch: () -> Unit,
    onSystem: () -> Unit,
    onQuickAdd: () -> Unit,
    onOpenFocus: (StudySession) -> Unit,
    onOpenReviews: (SubjectId?, TopicId?) -> Unit,
    onOpenQuestions: (SubjectId?, TopicId?) -> Unit,
    onOpenMockExamStudio: () -> Unit,
    onOpenSchedule: () -> Unit,
    onOpenRecommendation: (StudyRecommendation) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var loadAttempt by remember(plan.id) { mutableIntStateOf(0) }
    val scheduleFlow = remember(plan.id, loadAttempt) {
        scheduleManager.observeSchedule(plan.id).asStudyFlowLoadState()
    }
    val sessionsFlow = remember(plan.id, loadAttempt) {
        sessionManager.observeSessions(plan.id).asStudyFlowLoadState()
    }
    val reviewFlow = remember(plan.id, loadAttempt) {
        reviewManager.observeSummary(plan.id).asStudyFlowLoadState()
    }
    val analyticsFlow = remember(plan.id, loadAttempt) {
        analyticsManager.observeSnapshot(plan.id).asStudyFlowLoadState()
    }
    val scheduleState by scheduleFlow.collectAsStateWithLifecycle(initialValue = StudyFlowLoadState.Loading)
    val sessionsState by sessionsFlow.collectAsStateWithLifecycle(initialValue = StudyFlowLoadState.Loading)
    val reviewState by reviewFlow.collectAsStateWithLifecycle(initialValue = StudyFlowLoadState.Loading)
    val analyticsState by analyticsFlow.collectAsStateWithLifecycle(initialValue = StudyFlowLoadState.Loading)
    var recommendations by remember(plan.id) { mutableStateOf<List<StudyRecommendation>>(emptyList()) }
    var recommendationUnavailable by remember(plan.id) { mutableStateOf(false) }
    val cycleFlow = remember(plan.id) { publicExamManager.observeStudyCycle(plan.id) }
    val cycleSnapshot by cycleFlow.collectAsStateWithLifecycle(initialValue = null)
    var cycleAction by remember(plan.id) { mutableStateOf<StudyCycleAction?>(null) }
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val operationFailed = stringResource(R.string.operation_failed)
    if (
        !workspaceLoadGate(
            states = listOf(scheduleState, sessionsState, reviewState, analyticsState),
            onRetry = { loadAttempt += 1 },
            modifier = modifier,
        )
    ) return
    val readySchedule = scheduleState.readyValue()
    val readySessions = sessionsState.readyValue()
    val readyReviewSummary = reviewState.readyValue()
    val readyAnalytics = analyticsState.readyValue()

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = System.currentTimeMillis()
            delay(60_000L)
        }
    }

    LaunchedEffect(
        plan.id,
        readySchedule.strategy?.type,
        cycleSnapshot?.cycle?.currentItemId,
        cycleSnapshot?.cycle?.enabled,
    ) {
        cycleAction = if (
            plan.type == StudyType.PUBLIC_EXAMINATION &&
            readySchedule.strategy?.type == StudyStrategyType.STUDY_CYCLE
        ) {
            try {
                publicExamManager.nextStudyCycleAction(plan.id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
    }

    LaunchedEffect(plan.id, readySchedule.plannedActivities.size, readyReviewSummary.dueCount) {
        try {
            recommendations = discoveryManager.recommendations(plan.id)
            recommendationUnavailable = false
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            recommendations = emptyList()
            recommendationUnavailable = true
        }
    }

    val activeSession = readySessions.firstOrNull { session ->
        session.status == StudySessionStatus.RUNNING || session.status == StudySessionStatus.PAUSED
    }
    val todayActivities = remember(readySchedule.plannedActivities, currentTime) {
        activitiesForLocalDay(readySchedule.plannedActivities, currentTime)
    }
    val nextActivity = remember(readySchedule.plannedActivities, currentTime) {
        selectTodayOrNextScheduledActivity(readySchedule.plannedActivities, currentTime)
    }
    val dueReviewCount = readyReviewSummary.dueCount
    val recommendation = recommendations.firstOrNull()
    val weeklyGoalMinutes = (plan.weeklyGoalHours * 60).coerceAtLeast(1)
    val weekStart = localWeekStart(currentTime)
    val weeklyStudiedMinutes = readySessions
        .filter { session ->
            session.status == StudySessionStatus.COMPLETED && session.startedAtEpochMillis >= weekStart
        }
        .sumOf { session ->
            StudySessionTimer.activeSeconds(session, session.endedAtEpochMillis ?: currentTime)
        }
        .div(60L)
        .toInt()
    val weeklyProgress = weeklyStudiedMinutes.toFloat() / weeklyGoalMinutes.toFloat()

    fun startFocusActivity(activity: PlannedActivity) {
        scope.launch {
            try {
                val session = sessionManager.startFromActivity(
                    sessionId = StudySessionId(UUID.randomUUID().toString()),
                    activity = activity,
                )
                onOpenFocus(session)
            } catch (_: Exception) {
                snackbar.showSnackbar(operationFailed)
            }
        }
    }

    fun launchActivity(activity: PlannedActivity) {
        val intent = StudyActivityLaunchPlanner.plan(activity)
        when (intent.destination) {
            StudyActivityLaunchDestination.QUESTIONS -> onOpenQuestions(intent.subjectId, intent.topicId)
            StudyActivityLaunchDestination.REVIEWS -> onOpenReviews(intent.subjectId, intent.topicId)
            StudyActivityLaunchDestination.MOCK_EXAM_STUDIO -> onOpenMockExamStudio()
            StudyActivityLaunchDestination.WRITING_WORKSPACE,
            StudyActivityLaunchDestination.FOCUS_SESSION,
            -> startFocusActivity(activity)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            WorkspaceHeader(
                title = stringResource(R.string.today_title),
                eyebrow = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(currentTime)),
                plan = plan,
                onSwitchPlan = onSwitchPlan,
                onSearch = onSearch,
                onSystem = onSystem,
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = workspaceContentPadding(bottom = 180.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "primary", contentType = "focus-action") {
                    when {
                        activeSession != null -> StudyFlowFocusActionSurface(
                            supportingLabel = stringResource(R.string.active_session),
                            title = activeSession.notes.ifBlank { stringResource(R.string.continue_studying) },
                            message = sessionMessage(activeSession),
                            actionLabel = stringResource(R.string.resume_session),
                            onAction = { onOpenFocus(activeSession) },
                        )
                        dueReviewCount > 0 -> StudyFlowFocusActionSurface(
                            supportingLabel = stringResource(R.string.recommended_next),
                            title = stringResource(R.string.due_reviews),
                            message = pluralStringResource(
                                R.plurals.practice_due_message,
                                dueReviewCount,
                                dueReviewCount,
                            ),
                            actionLabel = stringResource(R.string.review_now),
                            onAction = { onOpenReviews(null, null) },
                            secondaryLabel = recommendation?.let { stringResource(R.string.open) },
                            onSecondaryAction = recommendation?.let { { onOpenRecommendation(it) } },
                        )
                        cycleAction != null -> StudyFlowFocusActionSurface(
                            supportingLabel = stringResource(R.string.study_cycle_next),
                            title = cycleAction?.title.orEmpty(),
                            message = stringResource(
                                R.string.study_cycle_duration,
                                cycleAction?.targetDurationMinutes ?: 0,
                            ),
                            actionLabel = stringResource(R.string.start_focus),
                            onAction = {
                                scope.launch {
                                    try {
                                        publicExamManager.startCurrentStudyCycleItem(plan.id)?.let(onOpenFocus)
                                    } catch (_: Exception) {
                                        snackbar.showSnackbar(operationFailed)
                                    }
                                }
                            },
                            secondaryLabel = stringResource(R.string.adjust_plan),
                            onSecondaryAction = onOpenSchedule,
                        )
                        nextActivity != null -> StudyFlowFocusActionSurface(
                            supportingLabel = stringResource(R.string.planned_next),
                            title = nextActivity.activity.title,
                            message = activityMetadata(
                                activity = nextActivity.activity,
                                includeDate = nextActivity.isFutureDay,
                            ),
                            actionLabel = stringResource(R.string.start_focus),
                            onAction = { launchActivity(nextActivity.activity) },
                            secondaryLabel = stringResource(R.string.adjust_plan),
                            onSecondaryAction = onOpenSchedule,
                        )
                        recommendation != null -> StudyFlowFocusActionSurface(
                            supportingLabel = stringResource(R.string.recommended_next),
                            title = recommendation.targetLabel.ifBlank {
                                stringResource(R.string.continue_studying)
                            },
                            message = recommendationPresentation(recommendation.presentation),
                            actionLabel = stringResource(R.string.open),
                            onAction = { onOpenRecommendation(recommendation) },
                        )
                        else -> StudyFlowFocusActionSurface(
                            supportingLabel = stringResource(R.string.recommended_next),
                            title = stringResource(R.string.free_study),
                            message = stringResource(R.string.free_study_message),
                            actionLabel = stringResource(R.string.start_session),
                            onAction = onQuickAdd,
                            secondaryLabel = stringResource(R.string.adjust_plan),
                            onSecondaryAction = onOpenSchedule,
                        )
                    }
                }

                if (plan.contentMode == StudyContentMode.PROGRAMMING_COURSE) {
                    item(key = "programming-course-panel", contentType = "programming-course-panel") {
                        ProgrammingCourseWorkspacePanel(
                            summary = ProgrammingCourseDashboardSummary(
                                moduleCount = readySchedule.plannedActivities.size,
                                exerciseCount = readyAnalytics.answeredQuestions,
                                projectCount = readySchedule.plannedActivities.count { activity ->
                                    activity.title.contains("project", ignoreCase = true)
                                },
                                weakConceptCount = readyAnalytics.weakAreas.size,
                                termuxRuntimeReady = plan.contentMode.capabilities.supportsTermuxRuntime,
                            ),
                            onOpenPractice = { onOpenReviews(null, null) },
                            onOpenPlan = onOpenSchedule,
                        )
                    }
                }

                item(key = "section:today-path", contentType = "section") {
                    StudyFlowSectionTitle(
                        title = stringResource(R.string.today_timeline),
                        actionLabel = stringResource(R.string.adjust_plan),
                        onAction = onOpenSchedule,
                    )
                }
                if (todayActivities.isEmpty() && dueReviewCount == 0) {
                    item(key = "empty-timeline", contentType = "empty") {
                        StudyFlowEmptyState(
                            title = stringResource(R.string.nothing_scheduled),
                            message = stringResource(R.string.free_study_message),
                            actionLabel = stringResource(R.string.adjust_plan),
                            onAction = onOpenSchedule,
                        )
                    }
                } else {
                    items(
                        items = todayActivities,
                        key = { activity -> "activity:${activity.id.value}" },
                        contentType = { "today-activity" },
                    ) { activity ->
                        StudyFlowTimelineItem(
                            title = activity.title,
                            metadata = activityMetadata(activity),
                            icon = if (activity.status == PlannedActivityStatus.COMPLETED) {
                                Icons.Outlined.CheckCircle
                            } else {
                                Icons.Outlined.Event
                            },
                            status = activityStatus(activity.status),
                            actionLabel = if (activity.status in setOf(
                                    PlannedActivityStatus.PLANNED,
                                    PlannedActivityStatus.MISSED,
                                )
                            ) {
                                stringResource(R.string.start_focus)
                            } else {
                                null
                            },
                            onAction = if (activity.status in setOf(
                                    PlannedActivityStatus.PLANNED,
                                    PlannedActivityStatus.MISSED,
                                )
                            ) {
                                { launchActivity(activity) }
                            } else {
                                null
                            },
                        )
                    }
                    if (dueReviewCount > 0) {
                        item(key = "review-batch", contentType = "today-review-batch") {
                            StudyFlowTimelineItem(
                                title = stringResource(R.string.due_reviews),
                                metadata = pluralStringResource(
                                    R.plurals.reviews_value,
                                    dueReviewCount,
                                    dueReviewCount,
                                ),
                                icon = Icons.Outlined.Refresh,
                                actionLabel = stringResource(R.string.review_now),
                                onAction = { onOpenReviews(null, null) },
                            )
                        }
                    }
                }

                item(key = "section:weekly-pace", contentType = "section") {
                    StudyFlowSectionTitle(title = stringResource(R.string.weekly_pace))
                }
                item(key = "weekly-metrics", contentType = "metrics") {
                    StudyFlowMetricStrip(
                        firstLabel = stringResource(R.string.studied_time),
                        firstValue = stringResource(R.string.minutes_value, weeklyStudiedMinutes),
                        firstSupportingText = stringResource(
                            R.string.percent_value,
                            (weeklyProgress * 100f).toInt().coerceIn(0, 999),
                        ),
                        secondLabel = stringResource(R.string.accuracy),
                        secondValue = stringResource(
                            R.string.percent_value,
                            readyAnalytics.questionAccuracyPercent,
                        ),
                        secondSupportingText = pluralStringResource(
                            R.plurals.questions_value,
                            readyAnalytics.answeredQuestions,
                            readyAnalytics.answeredQuestions,
                        ),
                    )
                }
                item(key = "section:coach", contentType = "section") {
                    StudyFlowSectionTitle(
                        title = stringResource(R.string.today_coach_title),
                        actionLabel = stringResource(R.string.adjust_plan),
                        onAction = onOpenSchedule,
                    )
                }
                item(key = "coach", contentType = "coach-row") {
                    val coachMessage = when {
                        dueReviewCount > 0 -> stringResource(R.string.today_coach_backlog)
                        readyAnalytics.scheduleAdherencePercent < 60 -> {
                            stringResource(R.string.today_coach_schedule)
                        }
                        else -> stringResource(R.string.today_coach_stable)
                    }
                    Text(
                        text = if (recommendationUnavailable) {
                            "$coachMessage ${stringResource(R.string.operation_failed)}"
                        } else {
                            coachMessage
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    )
                }
            }
        }
        StudyFlowActionOrb(
            icon = Icons.Outlined.PlayCircle,
            label = stringResource(R.string.start_focus),
            contentDescription = stringResource(R.string.quick_add),
            onClick = onQuickAdd,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 20.dp, vertical = 84.dp),
        )
    }

}

private fun localWeekStart(nowEpochMillis: Long): Long {
    val calendar = Calendar.getInstance().apply {
        timeInMillis = nowEpochMillis
        firstDayOfWeek = Calendar.MONDAY
        set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return calendar.timeInMillis
}

@Composable
private fun sessionMessage(session: StudySession): String = when (session.status) {
    StudySessionStatus.PAUSED -> stringResource(
        R.string.paused_minutes,
        session.grossDurationSeconds / 60,
    )
    StudySessionStatus.RUNNING -> stringResource(
        R.string.running_activity,
        session.type.name.replace('_', ' ').lowercase(),
    )
    StudySessionStatus.COMPLETED -> stringResource(R.string.completed)
    StudySessionStatus.CANCELLED -> stringResource(R.string.cancelled)
}

@Composable
private fun activityMetadata(
    activity: PlannedActivity,
    includeDate: Boolean = false,
): String {
    val configuration = LocalConfiguration.current
    val locale =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            configuration.locales[0]
        } else {
            @Suppress("DEPRECATION")
            configuration.locale
        }
    val epoch = activity.scheduledStartEpochMillis
    val time = epoch?.let {
        DateFormat.getTimeInstance(DateFormat.SHORT, locale).format(Date(it))
    } ?: stringResource(R.string.flexible)
    val duration = stringResource(R.string.minutes_value, activity.plannedDurationMinutes)
    if (!includeDate || epoch == null) return "$time · $duration"

    val date = Date(epoch)
    val weekday = SimpleDateFormat("EEE", locale).format(date)
    val shortDate = DateFormat.getDateInstance(DateFormat.SHORT, locale).format(date)
    return stringResource(
        R.string.dated_activity_metadata,
        "$weekday, $shortDate",
        time,
        duration,
    )
}

@Composable
private fun recommendationPresentation(presentation: StudyRecommendationPresentation): String = stringResource(
    when (presentation) {
        StudyRecommendationPresentation.DUE_REVIEWS -> R.string.recommendation_due_reviews
        StudyRecommendationPresentation.PLANNED_ACTIVITY -> R.string.recommendation_planned_activity
        StudyRecommendationPresentation.WEAK_TOPIC -> R.string.recommendation_weak_topic
        StudyRecommendationPresentation.PRACTICE_TOPIC -> R.string.recommendation_practice_topic
        StudyRecommendationPresentation.OVERDUE_RESOURCE -> R.string.recommendation_overdue_resource
    },
)

@Composable
private fun activityStatus(status: PlannedActivityStatus): String = stringResource(
    when (status) {
        PlannedActivityStatus.PLANNED -> R.string.planned
        PlannedActivityStatus.IN_PROGRESS -> R.string.workspace_in_progress
        PlannedActivityStatus.COMPLETED -> R.string.completed
        PlannedActivityStatus.MISSED -> R.string.missed
        PlannedActivityStatus.SKIPPED,
        PlannedActivityStatus.CANCELLED,
        -> R.string.completed
    },
)
