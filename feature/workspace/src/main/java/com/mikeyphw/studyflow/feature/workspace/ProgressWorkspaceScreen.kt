package com.mikeyphw.studyflow.feature.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.mikeyphw.studyflow.core.designsystem.StudyFlowPrimaryActionCard
import com.mikeyphw.studyflow.core.designsystem.StudyFlowCoachCard
import com.mikeyphw.studyflow.core.designsystem.StudyFlowEmptyState
import com.mikeyphw.studyflow.core.designsystem.StudyFlowMetricPulse
import com.mikeyphw.studyflow.core.designsystem.StudyFlowResponsivePair
import com.mikeyphw.studyflow.core.designsystem.StudyFlowTimelineRow
import com.mikeyphw.studyflow.core.designsystem.studyFlowSectionHeader
import com.mikeyphw.studyflow.core.designsystem.workspaceContentPadding
import com.mikeyphw.studyflow.core.domain.ManageAdaptiveReschedule
import com.mikeyphw.studyflow.core.domain.ManageExamReadiness
import com.mikeyphw.studyflow.core.domain.ManageStudyAnalytics
import com.mikeyphw.studyflow.core.domain.ManageStudyProgress
import com.mikeyphw.studyflow.core.domain.ManageStudySession
import com.mikeyphw.studyflow.core.domain.StudySessionTimer
import com.mikeyphw.studyflow.core.model.AdaptiveRescheduleChangeType
import com.mikeyphw.studyflow.core.model.ExamReadinessBand
import com.mikeyphw.studyflow.core.model.ReadinessSignalKind
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudySessionStatus
import com.mikeyphw.studyflow.core.navigation.ProgressWorkspacePeriod
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun ProgressWorkspaceScreen(
    plan: StudyPlan,
    selectedPeriod: ProgressWorkspacePeriod,
    analyticsManager: ManageStudyAnalytics,
    progressManager: ManageStudyProgress,
    readinessManager: ManageExamReadiness,
    adaptiveRescheduleManager: ManageAdaptiveReschedule,
    sessionManager: ManageStudySession,
    onPeriodSelected: (ProgressWorkspacePeriod) -> Unit,
    onSwitchPlan: () -> Unit,
    onSearch: () -> Unit,
    onSystem: () -> Unit,
    onOpenProgressCenter: () -> Unit,
    onOpenAnalytics: () -> Unit,
    onOpenPlan: () -> Unit,
    onOpenReviews: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val analyticsFlow = remember(plan.id) { analyticsManager.observeSnapshot(plan.id) }
    val progressFlow = remember(plan.id) { progressManager.observeSnapshot(plan.id) }
    val sessionsFlow = remember(plan.id) { sessionManager.observeSessions(plan.id) }
    val readinessFlow = remember(plan.id) { readinessManager.observeReadiness(plan) }
    val rescheduleFlow = remember(plan.id) { adaptiveRescheduleManager.observePreview(plan) }
    val analytics by analyticsFlow.collectAsStateWithLifecycle(initialValue = null)
    val progress by progressFlow.collectAsStateWithLifecycle(initialValue = null)
    val sessions by sessionsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val readiness by readinessFlow.collectAsStateWithLifecycle(initialValue = null)
    val reschedulePreview by rescheduleFlow.collectAsStateWithLifecycle(initialValue = null)
    val coroutineScope = rememberCoroutineScope()
    var appliedRescheduleChanges by rememberSaveable(plan.id.value) { mutableStateOf<Int?>(null) }
    var skippedRescheduleChanges by rememberSaveable(plan.id.value) { mutableStateOf<Int?>(null) }
    var rescheduleApplyFailed by rememberSaveable(plan.id.value) { mutableStateOf(false) }
    val cutoff = System.currentTimeMillis() - selectedPeriod.days * 86_400_000L
    val periodSessions = sessions.filter { session ->
        session.startedAtEpochMillis >= cutoff && session.status == StudySessionStatus.COMPLETED
    }
    val studiedSeconds = periodSessions.sumOf { session ->
        StudySessionTimer.activeSeconds(session, session.endedAtEpochMillis ?: System.currentTimeMillis())
    }
    val answered = periodSessions.sumOf { it.questionsAnswered }
    val correct = periodSessions.sumOf { it.correctAnswers }
    val periodAccuracy = if (answered == 0) null else ((correct.toDouble() / answered) * 100).toInt()
    val halfway = cutoff + (selectedPeriod.days * 86_400_000L / 2L)
    val earlierSessions = periodSessions.filter { it.startedAtEpochMillis < halfway }
    val recentSessions = periodSessions.filter { it.startedAtEpochMillis >= halfway }
    val earlierMinutes = earlierSessions.sumOf { session ->
        StudySessionTimer.activeSeconds(session, session.endedAtEpochMillis ?: System.currentTimeMillis())
    } / 60
    val recentMinutes = recentSessions.sumOf { session ->
        StudySessionTimer.activeSeconds(session, session.endedAtEpochMillis ?: System.currentTimeMillis())
    } / 60
    val trendLabel = trendLabel(recentMinutes, earlierMinutes)

    Scaffold(modifier = modifier) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            WorkspaceHeader(
                title = stringResource(R.string.progress_title),
                plan = plan,
                onSwitchPlan = onSwitchPlan,
                onSearch = onSearch,
                onSystem = onSystem,
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ProgressWorkspacePeriod.entries.forEach { period ->
                    FilterChip(
                        selected = period == selectedPeriod,
                        modifier = Modifier.heightIn(min = 48.dp),
                        onClick = { onPeriodSelected(period) },
                        label = {
                            Text(
                                stringResource(
                                    when (period) {
                                        ProgressWorkspacePeriod.SEVEN_DAYS -> R.string.progress_7_days
                                        ProgressWorkspacePeriod.THIRTY_DAYS -> R.string.progress_30_days
                                        ProgressWorkspacePeriod.NINETY_DAYS -> R.string.progress_90_days
                                    },
                                ),
                            )
                        },
                        leadingIcon = if (period == selectedPeriod) {
                            { androidx.compose.material3.Icon(Icons.Outlined.TrendingUp, contentDescription = null) }
                        } else {
                            null
                        },
                    )
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = workspaceContentPadding(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                studyFlowSectionHeader(key = "readiness", title = { stringResource(R.string.readiness_score) })
                item(key = "readiness-card", contentType = "readiness") {
                    val currentReadiness = readiness
                    StudyFlowPrimaryActionCard(
                        title = currentReadiness?.let { score ->
                            stringResource(R.string.percent_value, score.scorePercent)
                        } ?: stringResource(R.string.not_enough_data),
                        message = currentReadiness?.let { score ->
                            stringResource(readinessBandMessage(score.band))
                        } ?: stringResource(R.string.readiness_waiting_message),
                        supportingLabel = currentReadiness?.band?.let { band ->
                            stringResource(readinessBandLabel(band))
                        } ?: stringResource(R.string.readiness_score),
                        actionLabel = stringResource(R.string.open_analytics),
                        onAction = onOpenAnalytics,
                        secondaryLabel = stringResource(R.string.preview_reschedule),
                        onSecondaryAction = onOpenPlan,
                    )
                }
                item(key = "readiness-metrics", contentType = "metrics") {
                    StudyFlowResponsivePair(
                        first = { childModifier ->
                            StudyFlowMetricPulse(
                                label = stringResource(R.string.readiness_coverage),
                                value = readiness?.let { score ->
                                    stringResource(R.string.percent_value, score.coveragePercent)
                                } ?: stringResource(R.string.not_enough_data),
                                modifier = childModifier,
                            )
                        },
                        second = { childModifier ->
                            StudyFlowMetricPulse(
                                label = stringResource(R.string.readiness_time_feasibility),
                                value = readiness?.let { score ->
                                    stringResource(R.string.percent_value, score.timeFeasibilityPercent)
                                } ?: stringResource(R.string.not_enough_data),
                                supportingText = readiness?.daysUntilExam?.let { days ->
                                    stringResource(R.string.days_value, days)
                                },
                                modifier = childModifier,
                            )
                        },
                    )
                }
                items(
                    items = readiness?.signals.orEmpty(),
                    key = { signal -> "readiness:${signal.kind}" },
                    contentType = { "readiness-signal" },
                ) { signal ->
                    StudyFlowTimelineRow(
                        title = stringResource(readinessSignalLabel(signal.kind)),
                        metadata = stringResource(readinessSignalMessage(signal.kind)),
                        icon = Icons.Outlined.CheckCircle,
                        status = stringResource(R.string.percent_value, signal.scorePercent),
                        onClick = onOpenAnalytics,
                    )
                }

                studyFlowSectionHeader(
                    key = "adaptive-reschedule",
                    title = { stringResource(R.string.adaptive_reschedule) },
                )
                item(key = "adaptive-summary", contentType = "coach") {
                    StudyFlowCoachCard(
                        title = stringResource(R.string.reschedule_preview),
                        message = reschedulePreview?.let { preview ->
                            stringResource(
                                rescheduleSummary(
                                    types = preview.changes.map { it.type },
                                    readinessScorePercent = preview.readinessScorePercent,
                                ),
                            )
                        } ?: stringResource(R.string.readiness_waiting_message),
                        actionLabel = stringResource(R.string.apply_safe_reschedule),
                        onAction = {
                            coroutineScope.launch {
                                try {
                                    val result = adaptiveRescheduleManager.applySafePreview(plan)
                                    appliedRescheduleChanges = result.appliedChanges
                                    skippedRescheduleChanges = result.skippedChanges
                                    rescheduleApplyFailed = false
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: RuntimeException) {
                                    appliedRescheduleChanges = null
                                    skippedRescheduleChanges = null
                                    rescheduleApplyFailed = true
                                }
                            }
                        },
                    )
                }
                if (appliedRescheduleChanges != null || rescheduleApplyFailed) {
                    item(key = "adaptive-apply-feedback", contentType = "coach") {
                        StudyFlowCoachCard(
                            title = stringResource(R.string.reschedule_apply_result),
                            message = if (rescheduleApplyFailed) {
                                stringResource(R.string.reschedule_apply_failed)
                            } else {
                                stringResource(
                                    R.string.reschedule_apply_success,
                                    appliedRescheduleChanges ?: 0,
                                    skippedRescheduleChanges ?: 0,
                                )
                            },
                            actionLabel = stringResource(R.string.open_plan),
                            onAction = onOpenPlan,
                        )
                    }
                }
                items(
                    items = reschedulePreview?.changes.orEmpty(),
                    key = { change -> "reschedule:${change.id}" },
                    contentType = { "reschedule-change" },
                ) { change ->
                    StudyFlowTimelineRow(
                        title = if (change.plannedActivityId == null) {
                            stringResource(rescheduleChangeLabel(change.type))
                        } else {
                            change.title
                        },
                        metadata = stringResource(rescheduleChangeReason(change.type)),
                        icon = Icons.Outlined.AutoFixHigh,
                        status = stringResource(rescheduleChangeLabel(change.type)),
                        actionLabel = if (change.executable) {
                            stringResource(R.string.safe_change)
                        } else {
                            stringResource(R.string.preview_only)
                        },
                        onAction = onOpenPlan,
                    )
                }
                studyFlowSectionHeader(key = "period", title = { stringResource(R.string.progress_title) })
                item(key = "period-metrics", contentType = "metrics") {
                    StudyFlowResponsivePair(
                        first = { childModifier ->
                            StudyFlowMetricPulse(
                                label = stringResource(R.string.studied_time),
                                value = stringResource(R.string.minutes_value, (studiedSeconds / 60).toInt()),
                                supportingText = stringResource(R.string.sessions_value, periodSessions.size),
                                modifier = childModifier,
                            )
                        },
                        second = { childModifier ->
                            StudyFlowMetricPulse(
                                label = stringResource(R.string.accuracy),
                                value = periodAccuracy?.let { value ->
                                    stringResource(R.string.percent_value, value)
                                } ?: stringResource(R.string.not_enough_data),
                                supportingText = if (answered > 0) {
                                    stringResource(R.string.answered_value, answered)
                                } else {
                                    null
                                },
                                modifier = childModifier,
                            )
                        },
                    )
                }
                item(key = "quality-metrics", contentType = "metrics") {
                    StudyFlowResponsivePair(
                        first = { childModifier ->
                            StudyFlowMetricPulse(
                                label = stringResource(R.string.plan_schedule),
                                value = analytics?.let { snapshot ->
                                    stringResource(R.string.percent_value, snapshot.scheduleAdherencePercent)
                                } ?: stringResource(R.string.not_enough_data),
                                modifier = childModifier,
                            )
                        },
                        second = { childModifier ->
                            val goalPercent = progress?.goalProgressPercent ?: analytics?.goalCompletionPercent
                            StudyFlowMetricPulse(
                                label = stringResource(R.string.goal),
                                value = goalPercent?.let { value ->
                                    stringResource(R.string.percent_value, value)
                                } ?: stringResource(R.string.not_enough_data),
                                modifier = childModifier,
                            )
                        },
                    )
                }
                item(key = "period-trend", contentType = "trend") {
                    StudyFlowTimelineRow(
                        title = stringResource(R.string.period_trend),
                        metadata = stringResource(
                            R.string.period_trend_detail,
                            earlierMinutes.toInt(),
                            recentMinutes.toInt(),
                        ),
                        icon = Icons.Outlined.TrendingUp,
                        status = stringResource(trendLabel),
                        onClick = onOpenAnalytics,
                    )
                }

                studyFlowSectionHeader(key = "weak", title = { stringResource(R.string.weak_areas) })
                if (analytics?.weakAreas.isNullOrEmpty()) {
                    item(key = "weak-empty", contentType = "empty") {
                        StudyFlowEmptyState(
                            title = stringResource(R.string.weak_areas),
                            message = stringResource(R.string.no_weak_areas),
                            actionLabel = stringResource(R.string.open_analytics),
                            onAction = onOpenAnalytics,
                        )
                    }
                } else {
                    items(
                        items = analytics?.weakAreas.orEmpty(),
                        key = { area -> area.label },
                        contentType = { "weak-area" },
                    ) { area ->
                        StudyFlowTimelineRow(
                            title = area.label,
                            metadata = stringResource(R.string.percent_value, area.masteryPercent),
                            icon = Icons.Outlined.Insights,
                            status = stringResource(R.string.percent_value, area.progressPercent),
                            onClick = onOpenAnalytics,
                        )
                    }
                }

                studyFlowSectionHeader(key = "goals", title = { stringResource(R.string.goals_and_notes) })
                item(key = "goal-note-metrics", contentType = "metrics") {
                    StudyFlowResponsivePair(
                        first = { childModifier ->
                            StudyFlowMetricPulse(
                                label = stringResource(
                                    R.string.active_goals_value,
                                    progress?.activeGoals?.size ?: 0,
                                ),
                                value = progress?.goalProgressPercent?.let { value ->
                                    stringResource(R.string.percent_value, value)
                                } ?: stringResource(R.string.not_enough_data),
                                modifier = childModifier,
                            )
                        },
                        second = { childModifier ->
                            StudyFlowMetricPulse(
                                label = stringResource(R.string.notes_value, progress?.notes?.size ?: 0),
                                value = (progress?.pinnedNotes?.size ?: 0).toString(),
                                supportingText = stringResource(R.string.open_progress_center),
                                modifier = childModifier,
                            )
                        },
                    )
                }
                item(key = "adaptation", contentType = "coach") {
                    val message = when {
                        (analytics?.overdueReviewCount ?: 0) > 0 -> {
                            stringResource(R.string.adaptation_backlog)
                        }
                        periodAccuracy != null && periodAccuracy < 60 -> {
                            stringResource(R.string.adaptation_accuracy)
                        }
                        else -> stringResource(R.string.adaptation_steady)
                    }
                    StudyFlowCoachCard(
                        title = stringResource(R.string.adaptation_preview),
                        message = message,
                        actionLabel = stringResource(R.string.adjust_plan),
                        onAction = onOpenPlan,
                    )
                }
                items(
                    items = adaptationRows(
                        overdueReviews = analytics?.overdueReviewCount ?: 0,
                        periodAccuracy = periodAccuracy,
                        weakAreas = analytics?.weakAreas.orEmpty().map { it.label },
                    ),
                    key = { row -> "adapt:${row.titleRes}" },
                    contentType = { "adaptation-step" },
                ) { row ->
                    StudyFlowTimelineRow(
                        title = stringResource(row.titleRes),
                        metadata = stringResource(row.messageRes),
                        icon = Icons.Outlined.AutoFixHigh,
                        actionLabel = stringResource(row.actionRes),
                        onAction = when (row.destination) {
                            AdaptationDestination.ANALYTICS -> onOpenAnalytics
                            AdaptationDestination.PLAN -> onOpenPlan
                            AdaptationDestination.PROGRESS -> onOpenProgressCenter
                            AdaptationDestination.REVIEWS -> onOpenReviews
                        },
                    )
                }
                item(key = "progress-actions", contentType = "actions") {
                    StudyFlowResponsivePair(
                        first = { childModifier ->
                            androidx.compose.material3.Button(
                                onClick = onOpenProgressCenter,
                                modifier = childModifier,
                            ) {
                                androidx.compose.material3.Icon(Icons.Outlined.Flag, contentDescription = null)
                                Text(stringResource(R.string.open_progress_center))
                            }
                        },
                        second = { childModifier ->
                            androidx.compose.material3.OutlinedButton(
                                onClick = onOpenAnalytics,
                                modifier = childModifier,
                            ) {
                                Text(stringResource(R.string.open_analytics))
                            }
                        },
                    )
                }
            }
        }
    }
}


private fun readinessBandLabel(band: ExamReadinessBand): Int = when (band) {
    ExamReadinessBand.ON_TRACK -> R.string.readiness_on_track
    ExamReadinessBand.WATCH -> R.string.readiness_watch
    ExamReadinessBand.AT_RISK -> R.string.readiness_at_risk
    ExamReadinessBand.CRITICAL -> R.string.readiness_critical
}

private fun readinessBandMessage(band: ExamReadinessBand): Int = when (band) {
    ExamReadinessBand.ON_TRACK -> R.string.readiness_on_track_message
    ExamReadinessBand.WATCH -> R.string.readiness_watch_message
    ExamReadinessBand.AT_RISK -> R.string.readiness_at_risk_message
    ExamReadinessBand.CRITICAL -> R.string.readiness_critical_message
}

private fun rescheduleChangeLabel(type: AdaptiveRescheduleChangeType): Int = when (type) {
    AdaptiveRescheduleChangeType.REDISTRIBUTE_MISSED -> R.string.reschedule_redistribute_missed
    AdaptiveRescheduleChangeType.PROTECT_REVIEWS -> R.string.reschedule_protect_reviews
    AdaptiveRescheduleChangeType.REDUCE_OVERLOAD -> R.string.reschedule_reduce_overload
    AdaptiveRescheduleChangeType.ADD_THEORY_BEFORE_QUESTIONS -> R.string.reschedule_theory_before_questions
    AdaptiveRescheduleChangeType.KEEP_STEADY -> R.string.reschedule_keep_steady
}

private fun readinessSignalLabel(kind: ReadinessSignalKind): Int = when (kind) {
    ReadinessSignalKind.COVERAGE -> R.string.readiness_signal_coverage
    ReadinessSignalKind.RETENTION -> R.string.readiness_signal_retention
    ReadinessSignalKind.PRACTICE -> R.string.readiness_signal_practice
    ReadinessSignalKind.MOCK_EXAM -> R.string.readiness_signal_mock_exam
    ReadinessSignalKind.CONSISTENCY -> R.string.readiness_signal_consistency
    ReadinessSignalKind.SCHEDULE -> R.string.readiness_signal_schedule
    ReadinessSignalKind.TIME_LEFT -> R.string.readiness_signal_time_left
    ReadinessSignalKind.WEAK_AREAS -> R.string.readiness_signal_weak_areas
}

private fun readinessSignalMessage(kind: ReadinessSignalKind): Int = when (kind) {
    ReadinessSignalKind.COVERAGE -> R.string.readiness_signal_coverage_message
    ReadinessSignalKind.RETENTION -> R.string.readiness_signal_retention_message
    ReadinessSignalKind.PRACTICE -> R.string.readiness_signal_practice_message
    ReadinessSignalKind.MOCK_EXAM -> R.string.readiness_signal_mock_exam_message
    ReadinessSignalKind.CONSISTENCY -> R.string.readiness_signal_consistency_message
    ReadinessSignalKind.SCHEDULE -> R.string.readiness_signal_schedule_message
    ReadinessSignalKind.TIME_LEFT -> R.string.readiness_signal_time_left_message
    ReadinessSignalKind.WEAK_AREAS -> R.string.readiness_signal_weak_areas_message
}

private fun rescheduleChangeReason(type: AdaptiveRescheduleChangeType): Int = when (type) {
    AdaptiveRescheduleChangeType.REDISTRIBUTE_MISSED -> R.string.reschedule_reason_redistribute_missed
    AdaptiveRescheduleChangeType.PROTECT_REVIEWS -> R.string.reschedule_reason_protect_reviews
    AdaptiveRescheduleChangeType.REDUCE_OVERLOAD -> R.string.reschedule_reason_reduce_overload
    AdaptiveRescheduleChangeType.ADD_THEORY_BEFORE_QUESTIONS -> R.string.reschedule_reason_theory_before_questions
    AdaptiveRescheduleChangeType.KEEP_STEADY -> R.string.reschedule_reason_keep_steady
}

private fun rescheduleSummary(types: List<AdaptiveRescheduleChangeType>, readinessScorePercent: Int): Int = when {
    types.any { it == AdaptiveRescheduleChangeType.REDISTRIBUTE_MISSED } -> R.string.reschedule_summary_recover_missed
    types.any { it == AdaptiveRescheduleChangeType.PROTECT_REVIEWS } -> R.string.reschedule_summary_protect_reviews
    readinessScorePercent < 65 -> R.string.reschedule_summary_risky
    else -> R.string.reschedule_summary_stable
}

private fun trendLabel(recentMinutes: Long, earlierMinutes: Long): Int = when {
    recentMinutes >= earlierMinutes + 30L -> R.string.trend_up
    recentMinutes + 30L < earlierMinutes -> R.string.trend_down
    else -> R.string.trend_flat
}

private enum class AdaptationDestination {
    ANALYTICS,
    PLAN,
    PROGRESS,
    REVIEWS,
}

private data class AdaptationRow(
    val titleRes: Int,
    val messageRes: Int,
    val actionRes: Int,
    val destination: AdaptationDestination,
)

private fun adaptationRows(
    overdueReviews: Int,
    periodAccuracy: Int?,
    weakAreas: List<String>,
): List<AdaptationRow> = buildList {
    if (overdueReviews > 0) {
        add(
            AdaptationRow(
                titleRes = R.string.adaptation_step_reviews,
                messageRes = R.string.adaptation_step_reviews_message,
                actionRes = R.string.review_now,
                destination = AdaptationDestination.REVIEWS,
            ),
        )
    }
    if (periodAccuracy != null && periodAccuracy < 60) {
        add(
            AdaptationRow(
                titleRes = R.string.adaptation_step_theory,
                messageRes = R.string.adaptation_step_theory_message,
                actionRes = R.string.adjust_plan,
                destination = AdaptationDestination.PLAN,
            ),
        )
    }
    if (weakAreas.isNotEmpty()) {
        add(
            AdaptationRow(
                titleRes = R.string.adaptation_step_weak_area,
                messageRes = R.string.adaptation_step_weak_area_message,
                actionRes = R.string.open_analytics,
                destination = AdaptationDestination.ANALYTICS,
            ),
        )
    }
    if (isEmpty()) {
        add(
            AdaptationRow(
                titleRes = R.string.adaptation_step_steady,
                messageRes = R.string.adaptation_step_steady_message,
                actionRes = R.string.open_progress_center,
                destination = AdaptationDestination.PROGRESS,
            ),
        )
    }
}
