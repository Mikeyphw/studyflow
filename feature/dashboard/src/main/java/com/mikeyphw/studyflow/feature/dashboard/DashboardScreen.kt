package com.mikeyphw.studyflow.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.ImportExport
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mikeyphw.studyflow.core.designsystem.StudyFlowSectionCard
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudyPlanStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    plan: StudyPlan,
    availablePlans: List<StudyPlan>,
    isDefaultPlan: Boolean,
    onBack: () -> Unit,
    onSwitchPlan: (StudyPlan) -> Unit,
    onEditPlan: () -> Unit,
    onOpenAnalytics: () -> Unit,
    onOpenPlanSettings: () -> Unit,
    onOpenSyllabus: () -> Unit,
    onOpenSchedule: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenSessions: () -> Unit,
    onOpenPractice: () -> Unit,
    onOpenReviews: () -> Unit,
    onOpenReminders: () -> Unit,
    onOpenProgress: () -> Unit,
    onOpenPortability: () -> Unit,
    onToggleActiveState: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var switcherExpanded by rememberSaveable(plan.id.value) { mutableStateOf(false) }
    var actionsExpanded by rememberSaveable(plan.id.value) { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(text = plan.name)
                        if (isDefaultPlan) {
                            AssistChip(
                                onClick = {},
                                label = { Text(stringResource(R.string.default_plan)) },
                                leadingIcon = { Icon(Icons.Outlined.Star, contentDescription = null) },
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.go_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { switcherExpanded = true }) {
                        Icon(
                            imageVector = Icons.Outlined.SwapHoriz,
                            contentDescription = stringResource(R.string.switch_plan),
                        )
                    }
                    DropdownMenu(
                        expanded = switcherExpanded,
                        onDismissRequest = { switcherExpanded = false },
                    ) {
                        availablePlans
                            .filter { it.id != plan.id && it.status != StudyPlanStatus.TRASHED }
                            .forEach { candidate ->
                                DropdownMenuItem(
                                    text = { Text(candidate.name) },
                                    onClick = {
                                        switcherExpanded = false
                                        onSwitchPlan(candidate)
                                    },
                                )
                            }
                    }
                    IconButton(onClick = { actionsExpanded = true }) {
                        Icon(
                            imageVector = Icons.Outlined.MoreVert,
                            contentDescription = stringResource(R.string.more_actions),
                        )
                    }
                    DropdownMenu(
                        expanded = actionsExpanded,
                        onDismissRequest = { actionsExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.edit_plan)) },
                            leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                            onClick = {
                                actionsExpanded = false
                                onEditPlan()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.analytics)) },
                            leadingIcon = { Icon(Icons.Outlined.Flag, contentDescription = null) },
                            onClick = {
                                actionsExpanded = false
                                onOpenAnalytics()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.plan_settings)) },
                            leadingIcon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                            onClick = {
                                actionsExpanded = false
                                onOpenPlanSettings()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.syllabus)) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.ListAlt, contentDescription = null) },
                            onClick = {
                                actionsExpanded = false
                                onOpenSyllabus()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.schedule)) },
                            leadingIcon = { Icon(Icons.Outlined.EventNote, contentDescription = null) },
                            onClick = {
                                actionsExpanded = false
                                onOpenSchedule()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.library)) },
                            leadingIcon = { Icon(Icons.Outlined.FolderOpen, contentDescription = null) },
                            onClick = {
                                actionsExpanded = false
                                onOpenLibrary()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.sessions)) },
                            leadingIcon = { Icon(Icons.Outlined.PlayCircle, contentDescription = null) },
                            onClick = {
                                actionsExpanded = false
                                onOpenSessions()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.practice)) },
                            leadingIcon = { Icon(Icons.Outlined.Quiz, contentDescription = null) },
                            onClick = {
                                actionsExpanded = false
                                onOpenPractice()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.progress_center)) },
                            leadingIcon = { Icon(Icons.Outlined.Flag, contentDescription = null) },
                            onClick = {
                                actionsExpanded = false
                                onOpenProgress()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.reviews)) },
                            leadingIcon = { Icon(Icons.Outlined.Star, contentDescription = null) },
                            onClick = {
                                actionsExpanded = false
                                onOpenReviews()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.reminders)) },
                            leadingIcon = { Icon(Icons.Outlined.Notifications, contentDescription = null) },
                            onClick = {
                                actionsExpanded = false
                                onOpenReminders()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.portability_title)) },
                            leadingIcon = { Icon(Icons.Outlined.ImportExport, contentDescription = null) },
                            onClick = {
                                actionsExpanded = false
                                onOpenPortability()
                            },
                        )
                    }
                },
            )
        },
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val wide = maxWidth >= 760.dp
            val contentModifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
            if (wide) {
                Row(
                    modifier = contentModifier,
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    DashboardPrimaryColumn(
                        plan = plan,
                        onToggleActiveState = onToggleActiveState,
                        modifier = Modifier.weight(1.25f),
                    )
                    DashboardMetricsColumn(plan = plan, modifier = Modifier.weight(0.75f))
                }
            } else {
                Column(
                    modifier = contentModifier,
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    DashboardPrimaryColumn(plan = plan, onToggleActiveState = onToggleActiveState)
                    DashboardMetricsColumn(plan = plan)
                }
            }
        }
    }
}

@Composable
private fun DashboardPrimaryColumn(
    plan: StudyPlan,
    onToggleActiveState: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        statusMessage(plan.status)?.let { message ->
            StudyFlowSectionCard(title = stringResource(statusTitle(plan.status))) {
                Text(text = stringResource(message), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onToggleActiveState) {
                    Text(
                        stringResource(
                            if (plan.status == StudyPlanStatus.ACTIVE) R.string.pause_plan else R.string.resume_plan,
                        ),
                    )
                }
            }
        }
        StudyFlowSectionCard(title = stringResource(R.string.analytics_scope_title)) {
            Text(
                text = stringResource(R.string.analytics_scope_message),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        StudyFlowSectionCard(title = stringResource(R.string.next_best_action)) {
            Text(
                text = stringResource(R.string.foundation_recommendation),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = stringResource(R.string.foundation_recommendation_reason),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = {}) {
                Text(text = stringResource(R.string.start_session))
            }
        }
        StudyFlowSectionCard(title = stringResource(R.string.plan_progress)) {
            Text(text = stringResource(R.string.progress_value, plan.progressPercent))
            LinearProgressIndicator(
                progress = { plan.progressPercent / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = plan.description.ifBlank { stringResource(R.string.no_description) },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        StudyFlowSectionCard(title = stringResource(R.string.progress_scope_title)) {
            Text(
                text = stringResource(R.string.progress_scope_message),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        StudyFlowSectionCard(title = stringResource(R.string.library_scope_title)) {
            Text(
                text = stringResource(R.string.library_scope_message),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        StudyFlowSectionCard(title = stringResource(R.string.foundation_scope_title)) {
            Text(
                text = stringResource(R.string.foundation_scope_message),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DashboardMetricsColumn(
    plan: StudyPlan,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        MetricCard(
            title = stringResource(R.string.weekly_study_time),
            value = pluralStringResource(
                R.plurals.minutes_value,
                plan.studiedMinutesThisWeek,
                plan.studiedMinutesThisWeek,
            ),
        )
        MetricCard(
            title = stringResource(R.string.questions_answered),
            value = plan.questionsAnswered.toString(),
        )
        MetricCard(
            title = stringResource(R.string.average_accuracy),
            value = stringResource(R.string.progress_value, plan.averageAccuracyPercent),
        )
        MetricCard(
            title = stringResource(R.string.weekly_goal),
            value = pluralStringResource(
                R.plurals.hours_value,
                plan.weeklyGoalHours,
                plan.weeklyGoalHours,
            ),
        )
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
) {
    StudyFlowSectionCard(title = title) {
        Text(text = value, style = MaterialTheme.typography.headlineMedium)
    }
}

private fun statusMessage(status: StudyPlanStatus): Int? = when (status) {
    StudyPlanStatus.ACTIVE -> null
    StudyPlanStatus.DRAFT -> R.string.plan_draft_message
    StudyPlanStatus.PAUSED -> R.string.plan_paused_message
    StudyPlanStatus.COMPLETED -> R.string.plan_completed_message
    StudyPlanStatus.ARCHIVED,
    StudyPlanStatus.TRASHED,
    -> null
}

private fun statusTitle(status: StudyPlanStatus): Int = when (status) {
    StudyPlanStatus.ACTIVE -> R.string.pause_plan
    else -> R.string.resume_plan
}
