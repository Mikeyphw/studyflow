package com.mikeyphw.studyflow.feature.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AddTask
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mikeyphw.studyflow.core.designsystem.StudyFlowActionRow
import com.mikeyphw.studyflow.core.designsystem.StudyFlowEmptyState
import com.mikeyphw.studyflow.core.designsystem.StudyFlowTimelineRow
import com.mikeyphw.studyflow.core.designsystem.studyFlowSectionHeader
import com.mikeyphw.studyflow.core.domain.runRecoverableOperation
import com.mikeyphw.studyflow.core.domain.ManageStudySession
import com.mikeyphw.studyflow.core.model.StudyActivityType
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudyPlanStatus
import com.mikeyphw.studyflow.core.model.StudySession
import com.mikeyphw.studyflow.core.model.StudySessionId
import com.mikeyphw.studyflow.core.navigation.StudyFlowWorkspaceRoot
import java.util.UUID
import kotlinx.coroutines.launch

@Composable
fun PlanPickerScreen(
    plans: List<StudyPlan>,
    selectedPlanId: String,
    activeSession: StudySession?,
    onPlanSelected: (StudyPlan) -> Unit,
    onCreatePlan: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var blockedPlan by remember { mutableStateOf<StudyPlan?>(null) }
    val normalizedQuery = query.trim()
    val visiblePlans = plans.filter { plan ->
        plan.status != StudyPlanStatus.TRASHED &&
            (normalizedQuery.isBlank() || plan.name.contains(normalizedQuery, ignoreCase = true))
    }
    val available = visiblePlans.filter { it.status != StudyPlanStatus.ARCHIVED }
    val active = available.filter { it.status == StudyPlanStatus.ACTIVE || it.status == StudyPlanStatus.DRAFT }
    val other = available.filterNot(active::contains)
    val archived = visiblePlans.filter { it.status == StudyPlanStatus.ARCHIVED }
    val selectPlan: (StudyPlan) -> Unit = { plan ->
        if (activeSession != null && plan.id != activeSession.studyPlanId) {
            blockedPlan = plan
        } else {
            onPlanSelected(plan)
        }
    }
    WorkspaceUtilityScaffold(
        title = stringResource(R.string.plan_picker_title),
        onBack = onBack,
        modifier = modifier,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "picker-subtitle", contentType = "picker-copy") {
                Text(stringResource(R.string.plan_picker_subtitle))
            }
            item(key = "picker-search", contentType = "picker-search") {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.search_plans)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
            studyFlowSectionHeader(key = "active", title = { stringResource(R.string.active_plans) })
            if (active.isEmpty()) {
                item(key = "no-active", contentType = "workspace-utility") {
                    StudyFlowEmptyState(
                        title = stringResource(R.string.no_plan_title),
                        message = stringResource(R.string.no_plan_message),
                        actionLabel = stringResource(R.string.create_plan),
                        onAction = onCreatePlan,
                    )
                }
            } else {
                items(active, key = { plan -> plan.id.value }, contentType = { "plan-choice" }) { plan ->
                    StudyFlowTimelineRow(
                        title = plan.name,
                        metadata = stringResource(R.string.percent_value, plan.progressPercent),
                        icon = Icons.AutoMirrored.Outlined.ListAlt,
                        status = if (plan.id.value == selectedPlanId) {
                            stringResource(R.string.workspace_in_progress)
                        } else {
                            null
                        },
                        actionLabel = stringResource(R.string.open),
                        onAction = { selectPlan(plan) },
                    )
                }
            }
            if (other.isNotEmpty()) {
                studyFlowSectionHeader(key = "other", title = { stringResource(R.string.other_plans) })
                items(other, key = { plan -> "other:${plan.id.value}" }, contentType = { "plan-choice" }) { plan ->
                    StudyFlowTimelineRow(
                        title = plan.name,
                        metadata = stringResource(
                            when (plan.status) {
                                StudyPlanStatus.PAUSED -> R.string.status_paused
                                StudyPlanStatus.COMPLETED -> R.string.status_completed
                                else -> R.string.status_available
                            },
                        ),
                        icon = Icons.AutoMirrored.Outlined.ListAlt,
                        actionLabel = stringResource(R.string.open),
                        onAction = { selectPlan(plan) },
                    )
                }
            }
            if (archived.isNotEmpty()) {
                studyFlowSectionHeader(key = "archived", title = { stringResource(R.string.archived_plans) })
                items(
                    archived,
                    key = { plan -> "archived:${plan.id.value}" },
                    contentType = { "archived-plan" },
                ) { plan ->
                    StudyFlowTimelineRow(
                        title = plan.name,
                        metadata = stringResource(R.string.status_archived),
                        icon = Icons.AutoMirrored.Outlined.ListAlt,
                    )
                }
            }
        }
    }
    val blocked = blockedPlan
    if (blocked != null) {
        AlertDialog(
            onDismissRequest = { blockedPlan = null },
            title = { Text(stringResource(R.string.active_session_blocks_plan_switch_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.active_session_blocks_plan_switch_message,
                        blocked.name,
                    ),
                )
            },
            confirmButton = {
                Button(onClick = { blockedPlan = null }) {
                    Text(stringResource(R.string.keep_studying))
                }
            },
        )
    }
}

@Composable
fun SystemHubScreen(
    hasPlan: Boolean,
    diagnosticsAvailable: Boolean,
    onAllPlans: () -> Unit,
    onSettings: () -> Unit,
    onReminders: () -> Unit,
    onPortability: () -> Unit,
    onSync: () -> Unit,
    onPrivacy: () -> Unit,
    onDebug: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    WorkspaceUtilityScaffold(
        title = stringResource(R.string.system_hub_title),
        onBack = onBack,
        modifier = modifier,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item(key = "subtitle", contentType = "workspace-utility") {
                Text(stringResource(R.string.system_hub_subtitle))
            }
            item(key = "plans", contentType = "workspace-utility") {
                StudyFlowTimelineRow(
                    title = stringResource(R.string.all_plans),
                    metadata = stringResource(R.string.plan_picker_subtitle),
                    icon = Icons.AutoMirrored.Outlined.ListAlt,
                    onClick = onAllPlans,
                )
            }
            item(key = "settings", contentType = "workspace-utility") {
                StudyFlowTimelineRow(
                    title = stringResource(R.string.settings),
                    metadata = stringResource(R.string.system_hub_subtitle),
                    icon = Icons.Outlined.Settings,
                    onClick = onSettings,
                )
            }
            if (hasPlan) {
                item(key = "reminders", contentType = "workspace-utility") {
                    StudyFlowTimelineRow(
                        title = stringResource(R.string.reminders),
                        metadata = stringResource(R.string.plan_title),
                        icon = Icons.Outlined.Notifications,
                        onClick = onReminders,
                    )
                }
                item(key = "backup", contentType = "workspace-utility") {
                    StudyFlowTimelineRow(
                        title = stringResource(R.string.backup_restore),
                        metadata = stringResource(R.string.plan_title),
                        icon = Icons.Outlined.Backup,
                        onClick = onPortability,
                    )
                }
            }
            item(key = "sync", contentType = "workspace-utility") {
                StudyFlowTimelineRow(
                    title = stringResource(R.string.sync),
                    metadata = stringResource(R.string.system_hub_subtitle),
                    icon = Icons.Outlined.Sync,
                    onClick = onSync,
                )
            }
            item(key = "privacy", contentType = "workspace-utility") {
                StudyFlowTimelineRow(
                    title = stringResource(R.string.privacy),
                    metadata = stringResource(R.string.system_hub_subtitle),
                    icon = Icons.Outlined.Lock,
                    onClick = onPrivacy,
                )
            }
            if (diagnosticsAvailable) {
                item(key = "debug", contentType = "workspace-utility") {
                    StudyFlowTimelineRow(
                        title = stringResource(R.string.debug_workbench),
                        metadata = stringResource(R.string.system_hub_subtitle),
                        icon = Icons.Outlined.BugReport,
                        onClick = onDebug,
                    )
                }
            }
        }
    }
}

@Composable
fun QuickAddScreen(
    plan: StudyPlan,
    sourceRoot: StudyFlowWorkspaceRoot,
    sessionManager: ManageStudySession,
    onSessionStarted: (StudySession) -> Unit,
    onStudyBlock: () -> Unit,
    onQuestion: () -> Unit,
    onGoalOrNote: () -> Unit,
    onMaterial: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showQuickSession by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                )
            }
            Column(modifier = Modifier.padding(start = 6.dp)) {
                Text(
                    text = stringResource(R.string.quick_add_title),
                    style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = stringResource(R.string.quick_add_context, quickAddSourceLabel(sourceRoot)),
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        StudyFlowActionRow(
            icon = Icons.Outlined.PlayCircle,
            label = stringResource(R.string.quick_session),
            onClick = { showQuickSession = true },
        )
        StudyFlowActionRow(
            icon = Icons.Outlined.AddTask,
            label = stringResource(R.string.study_block),
            onClick = onStudyBlock,
        )
        StudyFlowActionRow(
            icon = Icons.Outlined.Quiz,
            label = stringResource(R.string.question),
            onClick = onQuestion,
        )
        StudyFlowActionRow(
            icon = Icons.AutoMirrored.Outlined.ListAlt,
            label = stringResource(R.string.goal_or_note),
            onClick = onGoalOrNote,
        )
        StudyFlowActionRow(
            icon = Icons.Outlined.FolderOpen,
            label = stringResource(R.string.material),
            onClick = onMaterial,
        )
    }
    if (showQuickSession) {
        QuickSessionDialog(
            plan = plan,
            sessionManager = sessionManager,
            onDismiss = { showQuickSession = false },
            onStarted = onSessionStarted,
        )
    }
}

@Composable
private fun quickAddSourceLabel(root: StudyFlowWorkspaceRoot): String = stringResource(
    when (root) {
        StudyFlowWorkspaceRoot.TODAY -> R.string.today_title
        StudyFlowWorkspaceRoot.PLAN -> R.string.plan_title
        StudyFlowWorkspaceRoot.PRACTICE -> R.string.practice_title
        StudyFlowWorkspaceRoot.PROGRESS -> R.string.progress_title
    },
)

@Composable
private fun QuickSessionDialog(
    plan: StudyPlan,
    sessionManager: ManageStudySession,
    onDismiss: () -> Unit,
    onStarted: (StudySession) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val preferredSessionMinutes by remember(plan.id, sessionManager) {
        sessionManager.observePreferredSessionMinutes(plan.id)
    }.collectAsStateWithLifecycle(initialValue = null)
    var title by rememberSaveable { mutableStateOf(plan.name) }
    var durationOverride by rememberSaveable { mutableStateOf<String?>(null) }
    val duration = durationOverride ?: preferredSessionMinutes?.toString().orEmpty()
    var errorVisible by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.quick_session)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.quick_session_title)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = duration,
                    onValueChange = { durationOverride = it.filter(Char::isDigit).take(3) },
                    label = { Text(stringResource(R.string.quick_session_duration)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                if (errorVisible) {
                    Text(stringResource(R.string.operation_failed))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    scope.launch {
                        runRecoverableOperation {
                            sessionManager.startQuickSession(
                                sessionId = StudySessionId(UUID.randomUUID().toString()),
                                studyPlanId = plan.id,
                                title = title,
                                type = StudyActivityType.CUSTOM,
                                targetDurationMinutes = durationOverride?.toIntOrNull(),
                            )
                        }.onSuccess { session ->
                            onDismiss()
                            onStarted(session)
                        }.onFailure {
                            errorVisible = true
                        }
                    }
                },
                enabled = title.isNotBlank() && (duration.toIntOrNull() ?: 0) in 5..240,
            ) {
                Text(stringResource(R.string.begin))
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.back))
            }
        },
    )
}
