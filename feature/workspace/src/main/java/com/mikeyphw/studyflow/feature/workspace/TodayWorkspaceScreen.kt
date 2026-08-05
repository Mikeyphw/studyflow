package com.mikeyphw.studyflow.feature.workspace

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mikeyphw.studyflow.core.designsystem.StudyFlowCoachCard
import com.mikeyphw.studyflow.core.designsystem.StudyFlowEmptyState
import com.mikeyphw.studyflow.core.designsystem.StudyFlowMetricPulse
import com.mikeyphw.studyflow.core.designsystem.StudyFlowPrimaryActionCard
import com.mikeyphw.studyflow.core.designsystem.StudyFlowResponsivePair
import com.mikeyphw.studyflow.core.designsystem.StudyFlowTimelineRow
import com.mikeyphw.studyflow.core.designsystem.studyFlowSectionHeader
import com.mikeyphw.studyflow.core.designsystem.workspaceContentPadding
import com.mikeyphw.studyflow.core.domain.ManageStudyAnalytics
import com.mikeyphw.studyflow.core.domain.ManageStudyDiscovery
import com.mikeyphw.studyflow.core.domain.ManageStudyReview
import com.mikeyphw.studyflow.core.domain.ManageStudySchedule
import com.mikeyphw.studyflow.core.domain.ManageStudySession
import com.mikeyphw.studyflow.core.domain.StudyRecommendation
import com.mikeyphw.studyflow.core.domain.StudyRecommendationPresentation
import com.mikeyphw.studyflow.core.domain.StudySessionTimer
import com.mikeyphw.studyflow.core.model.PlannedActivity
import com.mikeyphw.studyflow.core.model.PlannedActivityStatus
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudySchedule
import com.mikeyphw.studyflow.core.model.StudySession
import com.mikeyphw.studyflow.core.model.StudySessionId
import com.mikeyphw.studyflow.core.model.StudySessionStatus
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.UUID
import java.util.concurrent.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayWorkspaceScreen(
    plan: StudyPlan,
    scheduleManager: ManageStudySchedule,
    sessionManager: ManageStudySession,
    reviewManager: ManageStudyReview,
    analyticsManager: ManageStudyAnalytics,
    discoveryManager: ManageStudyDiscovery,
    onSwitchPlan: () -> Unit,
    onSearch: () -> Unit,
    onSystem: () -> Unit,
    onQuickAdd: () -> Unit,
    onOpenFocus: (StudySession) -> Unit,
    onOpenReviews: () -> Unit,
    onOpenSchedule: () -> Unit,
    onOpenRecommendation: (StudyRecommendation) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val scheduleFlow = remember(plan.id) { scheduleManager.observeSchedule(plan.id) }
    val sessionsFlow = remember(plan.id) { sessionManager.observeSessions(plan.id) }
    val reviewFlow = remember(plan.id) { reviewManager.observeSummary(plan.id) }
    val analyticsFlow = remember(plan.id) { analyticsManager.observeSnapshot(plan.id) }
    val schedule by scheduleFlow.collectAsStateWithLifecycle(
        initialValue = StudySchedule(plan.id, null, emptyList(), emptyList()),
    )
    val sessions by sessionsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val reviewSummary by reviewFlow.collectAsStateWithLifecycle(initialValue = null)
    val analytics by analyticsFlow.collectAsStateWithLifecycle(initialValue = null)
    var recommendations by remember(plan.id) { mutableStateOf<List<StudyRecommendation>>(emptyList()) }
    var recommendationUnavailable by remember(plan.id) { mutableStateOf(false) }
    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }
    val operationFailed = stringResource(R.string.operation_failed)

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = System.currentTimeMillis()
            delay(60_000L)
        }
    }

    LaunchedEffect(plan.id, schedule.plannedActivities.size, reviewSummary?.dueCount) {
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

    val activeSession = sessions.firstOrNull { session ->
        session.status == StudySessionStatus.RUNNING || session.status == StudySessionStatus.PAUSED
    }
    val todayActivities = remember(schedule.plannedActivities, currentTime) {
        val (start, end) = localDayBounds(currentTime)
        schedule.plannedActivities.filter { activity ->
            val startAt = activity.scheduledStartEpochMillis
            startAt != null && startAt in start until end && activity.status != PlannedActivityStatus.CANCELLED
        }.sortedBy { it.scheduledStartEpochMillis }
    }
    val nextActivity = todayActivities.firstOrNull { activity ->
        activity.status == PlannedActivityStatus.PLANNED || activity.status == PlannedActivityStatus.MISSED
    } ?: schedule.pendingActivities.minByOrNull { it.scheduledStartEpochMillis ?: Long.MAX_VALUE }
    val dueReviewCount = reviewSummary?.dueCount ?: 0
    val recommendation = recommendations.firstOrNull()
    val weeklyGoalMinutes = (plan.weeklyGoalHours * 60).coerceAtLeast(1)
    val weekStart = localWeekStart(currentTime)
    val weeklyStudiedMinutes = sessions
        .filter { session ->
            session.status == StudySessionStatus.COMPLETED && session.startedAtEpochMillis >= weekStart
        }
        .sumOf { session ->
            StudySessionTimer.activeSeconds(session, session.endedAtEpochMillis ?: currentTime)
        }
        .div(60L)
        .toInt()
    val weeklyProgress = weeklyStudiedMinutes.toFloat() / weeklyGoalMinutes.toFloat()

    fun startActivity(activity: PlannedActivity) {
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

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(onClick = onQuickAdd) {
                Icon(Icons.Outlined.PlayCircle, contentDescription = stringResource(R.string.quick_add))
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
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
                contentPadding = workspaceContentPadding(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item(key = "primary", contentType = "primary-action") {
                    when {
                        activeSession != null -> StudyFlowPrimaryActionCard(
                            supportingLabel = stringResource(R.string.active_session),
                            title = activeSession.notes.ifBlank { stringResource(R.string.continue_studying) },
                            message = sessionMessage(activeSession),
                            actionLabel = stringResource(R.string.resume_session),
                            onAction = { onOpenFocus(activeSession) },
                        )
                        dueReviewCount > 0 -> StudyFlowPrimaryActionCard(
                            supportingLabel = stringResource(R.string.recommended_next),
                            title = stringResource(R.string.due_reviews),
                            message = stringResource(R.string.practice_due_message, dueReviewCount),
                            actionLabel = stringResource(R.string.review_now),
                            onAction = onOpenReviews,
                            secondaryLabel = recommendation?.let { stringResource(R.string.open) },
                            onSecondaryAction = recommendation?.let { { onOpenRecommendation(it) } },
                        )
                        nextActivity != null -> StudyFlowPrimaryActionCard(
                            supportingLabel = stringResource(R.string.planned_next),
                            title = nextActivity.title,
                            message = activityMetadata(nextActivity),
                            actionLabel = stringResource(R.string.start_focus),
                            onAction = { startActivity(nextActivity) },
                            secondaryLabel = stringResource(R.string.adjust_plan),
                            onSecondaryAction = onOpenSchedule,
                        )
                        recommendation != null -> StudyFlowPrimaryActionCard(
                            supportingLabel = stringResource(R.string.recommended_next),
                            title = recommendation.targetLabel.ifBlank {
                                stringResource(R.string.continue_studying)
                            },
                            message = recommendationPresentation(recommendation.presentation),
                            actionLabel = stringResource(R.string.open),
                            onAction = { onOpenRecommendation(recommendation) },
                        )
                        else -> StudyFlowPrimaryActionCard(
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

                studyFlowSectionHeader(
                    key = "today-path",
                    title = { stringResource(R.string.today_timeline) },
                    actionLabel = { stringResource(R.string.adjust_plan) },
                    onAction = onOpenSchedule,
                )
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
                        StudyFlowTimelineRow(
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
                                { startActivity(activity) }
                            } else {
                                null
                            },
                        )
                    }
                    if (dueReviewCount > 0) {
                        item(key = "review-batch", contentType = "today-review-batch") {
                            StudyFlowTimelineRow(
                                title = stringResource(R.string.due_reviews),
                                metadata = stringResource(R.string.reviews_value, dueReviewCount),
                                icon = Icons.Outlined.Refresh,
                                actionLabel = stringResource(R.string.review_now),
                                onAction = onOpenReviews,
                            )
                        }
                    }
                }

                studyFlowSectionHeader(
                    key = "weekly-pace",
                    title = { stringResource(R.string.weekly_pace) },
                )
                item(key = "weekly-metrics", contentType = "metrics") {
                    StudyFlowResponsivePair(
                        first = { childModifier ->
                            StudyFlowMetricPulse(
                                label = stringResource(R.string.studied_time),
                                value = stringResource(R.string.minutes_value, weeklyStudiedMinutes),
                                progress = weeklyProgress,
                                modifier = childModifier,
                            )
                        },
                        second = { childModifier ->
                            StudyFlowMetricPulse(
                                label = stringResource(R.string.accuracy),
                                value = analytics?.let { snapshot ->
                                    stringResource(R.string.percent_value, snapshot.questionAccuracyPercent)
                                } ?: stringResource(R.string.not_enough_data),
                                supportingText = analytics?.let { snapshot ->
                                    stringResource(R.string.questions_value, snapshot.answeredQuestions)
                                },
                                modifier = childModifier,
                            )
                        },
                    )
                }
                item(key = "coach", contentType = "coach") {
                    val coachMessage = when {
                        dueReviewCount > 0 -> stringResource(R.string.today_coach_backlog)
                        (analytics?.scheduleAdherencePercent ?: 100) < 60 -> {
                            stringResource(R.string.today_coach_schedule)
                        }
                        else -> stringResource(R.string.today_coach_stable)
                    }
                    StudyFlowCoachCard(
                        title = stringResource(R.string.today_coach_title),
                        message = if (recommendationUnavailable) {
                            "$coachMessage ${stringResource(R.string.operation_failed)}"
                        } else {
                            coachMessage
                        },
                        actionLabel = stringResource(R.string.adjust_plan),
                        onAction = onOpenSchedule,
                    )
                }
            }
        }
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

private fun localDayBounds(nowEpochMillis: Long): Pair<Long, Long> {
    val calendar = Calendar.getInstance().apply {
        timeInMillis = nowEpochMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val start = calendar.timeInMillis
    calendar.add(Calendar.DAY_OF_MONTH, 1)
    return start to calendar.timeInMillis
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
private fun activityMetadata(activity: PlannedActivity): String {
    val time = activity.scheduledStartEpochMillis?.let { epoch ->
        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(epoch))
    } ?: stringResource(R.string.flexible)
    return "$time · ${stringResource(R.string.minutes_value, activity.plannedDurationMinutes)}"
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
        PlannedActivityStatus.IN_PROGRESS -> R.string.in_progress
        PlannedActivityStatus.COMPLETED -> R.string.completed
        PlannedActivityStatus.MISSED -> R.string.missed
        PlannedActivityStatus.SKIPPED,
        PlannedActivityStatus.CANCELLED,
        -> R.string.completed
    },
)
