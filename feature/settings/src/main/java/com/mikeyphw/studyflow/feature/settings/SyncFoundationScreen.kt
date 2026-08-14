package com.mikeyphw.studyflow.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mikeyphw.studyflow.core.designsystem.StudyFlowSectionCard
import com.mikeyphw.studyflow.core.domain.CloudlessBackupQueueAction
import com.mikeyphw.studyflow.core.domain.CloudlessBackupQueueItem
import com.mikeyphw.studyflow.core.domain.CloudlessBackupQueuePlanner
import com.mikeyphw.studyflow.core.domain.CloudlessBackupQueueStatus
import com.mikeyphw.studyflow.core.model.StudySyncChangeSummary
import com.mikeyphw.studyflow.core.model.StudySyncConflictPolicy
import com.mikeyphw.studyflow.core.model.StudySyncConnectionStatus
import com.mikeyphw.studyflow.core.model.StudySyncState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncFoundationScreen(
    state: StudySyncState,
    summary: StudySyncChangeSummary?,
    onPrepare: () -> Unit,
    onDisable: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sync_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.go_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            StudyFlowSectionCard(title = stringResource(R.string.sync_foundation_title)) {
                Text(
                    text = stringResource(R.string.sync_foundation_message),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(text = stringResource(R.string.sync_device_value, state.deviceName))
                Text(
                    text = stringResource(
                        R.string.sync_status_value,
                        stringResource(syncConnectionStatusLabel(state.connectionStatus)),
                    ),
                )
                Text(
                    text = stringResource(
                        R.string.sync_conflict_value,
                        stringResource(syncConflictPolicyLabel(state.conflictPolicy)),
                    ),
                )
            }
            StudyFlowSectionCard(title = stringResource(R.string.sync_local_changes_title)) {
                val currentSummary = summary
                if (currentSummary == null) {
                    Text(
                        text = stringResource(R.string.sync_local_changes_unknown),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(text = stringResource(R.string.sync_plan_count, currentSummary.planCount))
                    Text(text = stringResource(R.string.sync_active_plan_count, currentSummary.activePlanCount))
                    Text(text = stringResource(R.string.sync_change_count, currentSummary.changeCount))
                }
            }
            LocalCheckpointReadinessCard(state = state, summary = summary)
            StudyFlowSectionCard(title = stringResource(R.string.sync_checkpoint_controls_title)) {
                Text(
                    text = stringResource(R.string.sync_checkpoint_controls_message),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onPrepare, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.sync_prepare_checkpoint))
                }
                OutlinedButton(
                    onClick = onDisable,
                    enabled = state.enabled,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.sync_disable_checkpoint))
                }
            }
        }
    }
}


@Composable
private fun LocalCheckpointReadinessCard(
    state: StudySyncState,
    summary: StudySyncChangeSummary?,
) {
    StudyFlowSectionCard(title = stringResource(R.string.sync_checkpoint_readiness_title)) {
        val currentSummary = summary
        if (currentSummary == null) {
            Text(
                text = stringResource(R.string.sync_checkpoint_waiting_for_inspection),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val queuePlan = CloudlessBackupQueuePlanner.plan(
                state = state,
                summary = currentSummary,
                nowEpochMillis = System.currentTimeMillis(),
            )
            Text(
                text = stringResource(
                    R.string.sync_checkpoint_readiness_summary,
                    queuePlan.readyCount,
                    queuePlan.blockedCount,
                    queuePlan.waitingCount,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            queuePlan.items.take(4).forEach { item ->
                CloudlessBackupQueueRow(item)
            }
        }
    }
}

@Composable
private fun CloudlessBackupQueueRow(item: CloudlessBackupQueueItem) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = stringResource(item.action.titleRes()), style = MaterialTheme.typography.titleSmall)
                Text(
                    text = stringResource(item.action.messageRes(), item.pendingChangeCount),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                text = stringResource(item.status.labelRes()),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}


private fun syncConnectionStatusLabel(status: StudySyncConnectionStatus): Int = when (status) {
    StudySyncConnectionStatus.DISABLED -> R.string.sync_connection_local_only
    StudySyncConnectionStatus.LOCAL_READY -> R.string.sync_connection_local_ready
    StudySyncConnectionStatus.CHECKPOINT_NEEDED -> R.string.sync_connection_checkpoint_needed
    StudySyncConnectionStatus.CHECKPOINT_BLOCKED -> R.string.sync_connection_checkpoint_error
}

private fun syncConflictPolicyLabel(policy: StudySyncConflictPolicy): Int = when (policy) {
    StudySyncConflictPolicy.KEEP_LOCAL -> R.string.sync_conflict_local_only
    StudySyncConflictPolicy.MANUAL_REVIEW -> R.string.sync_conflict_manual_review
}


private fun CloudlessBackupQueueAction.titleRes(): Int = when (this) {
    CloudlessBackupQueueAction.PREPARE_CHECKPOINT -> R.string.sync_queue_prepare_checkpoint
    CloudlessBackupQueueAction.CHECKPOINT_CURRENT -> R.string.sync_checkpoint_current
    CloudlessBackupQueueAction.REVIEW_LOCAL_CONFLICT_POLICY -> R.string.sync_queue_review_policy
    CloudlessBackupQueueAction.WAIT_FOR_LOCAL_DATA -> R.string.sync_queue_wait_for_data
    CloudlessBackupQueueAction.UNBLOCK_CHECKPOINT -> R.string.sync_queue_unblock_checkpoint
}

private fun CloudlessBackupQueueAction.messageRes(): Int = when (this) {
    CloudlessBackupQueueAction.PREPARE_CHECKPOINT -> R.string.sync_queue_prepare_checkpoint_message
    CloudlessBackupQueueAction.CHECKPOINT_CURRENT -> R.string.sync_checkpoint_current_message
    CloudlessBackupQueueAction.REVIEW_LOCAL_CONFLICT_POLICY -> R.string.sync_queue_review_policy_message
    CloudlessBackupQueueAction.WAIT_FOR_LOCAL_DATA -> R.string.sync_queue_wait_for_data_message
    CloudlessBackupQueueAction.UNBLOCK_CHECKPOINT -> R.string.sync_queue_unblock_checkpoint_message
}

private fun CloudlessBackupQueueStatus.labelRes(): Int = when (this) {
    CloudlessBackupQueueStatus.READY -> R.string.sync_queue_status_ready
    CloudlessBackupQueueStatus.WAITING -> R.string.sync_queue_status_waiting
    CloudlessBackupQueueStatus.BLOCKED -> R.string.sync_queue_status_blocked
}
