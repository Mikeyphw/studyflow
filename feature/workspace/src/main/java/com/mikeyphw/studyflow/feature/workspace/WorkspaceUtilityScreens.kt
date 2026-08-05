package com.mikeyphw.studyflow.feature.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.OutlinedTextField
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
import com.mikeyphw.studyflow.core.designsystem.StudyFlowEmptyState
import com.mikeyphw.studyflow.core.designsystem.StudyFlowTimelineRow
import com.mikeyphw.studyflow.core.designsystem.studyFlowSectionHeader
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
        if (activeSession != null && plan.id.value != selectedPlanId) {
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
                        status = if (plan.id.value == selectedPlanId) stringResource(R.string.in_progress) else null,
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
    WorkspaceUtilityScaffold(
        title = stringResource(R.string.quick_add_title),
        onBack = onBack,
        modifier = modifier,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item(key = "quick-subtitle", contentType = "workspace-utility") {
                Text(stringResource(R.string.quick_add_context, quickAddSourceLabel(sourceRoot)))
            }
            item(key = "quick-session", contentType = "workspace-utility") {
                StudyFlowTimelineRow(
                    title = stringResource(R.string.quick_session),
                    metadata = stringResource(R.string.start_focus),
                    icon = Icons.Outlined.PlayCircle,
                    onClick = { showQuickSession = true },
                )
            }
            item(key = "study-block", contentType = "workspace-utility") {
                StudyFlowTimelineRow(
                    title = stringResource(R.string.study_block),
                    metadata = stringResource(R.string.manage_schedule),
                    icon = Icons.Outlined.AddTask,
                    onClick = onStudyBlock,
                )
            }
            item(key = "question", contentType = "workspace-utility") {
                StudyFlowTimelineRow(
                    title = stringResource(R.string.question),
                    metadata = stringResource(R.string.open_question_bank),
                    icon = Icons.Outlined.Quiz,
                    onClick = onQuestion,
                )
            }
            item(key = "goal-note", contentType = "workspace-utility") {
                StudyFlowTimelineRow(
                    title = stringResource(R.string.goal_or_note),
                    metadata = stringResource(R.string.open_progress_center),
                    icon = Icons.AutoMirrored.Outlined.ListAlt,
                    onClick = onGoalOrNote,
                )
            }
            item(key = "material", contentType = "workspace-utility") {
                StudyFlowTimelineRow(
                    title = stringResource(R.string.material),
                    metadata = stringResource(R.string.manage_library),
                    icon = Icons.Outlined.FolderOpen,
                    onClick = onMaterial,
                )
            }
        }
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
    var title by rememberSaveable { mutableStateOf(plan.name) }
    var duration by rememberSaveable { mutableStateOf("25") }
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
                    onValueChange = { duration = it.filter(Char::isDigit).take(3) },
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
                        runCatching {
                            sessionManager.startQuickSession(
                                sessionId = StudySessionId(UUID.randomUUID().toString()),
                                studyPlanId = plan.id,
                                title = title,
                                type = StudyActivityType.CUSTOM,
                                targetDurationMinutes = duration.toIntOrNull()?.coerceIn(5, 240),
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
