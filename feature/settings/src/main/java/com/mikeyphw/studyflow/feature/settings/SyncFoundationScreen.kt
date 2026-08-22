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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mikeyphw.studyflow.core.designsystem.StudyFlowSectionCard
import com.mikeyphw.studyflow.core.domain.CloudlessBackupQueueAction
import com.mikeyphw.studyflow.core.domain.CloudlessBackupQueueItem
import com.mikeyphw.studyflow.core.domain.CloudlessBackupQueuePlanner
import com.mikeyphw.studyflow.core.domain.CloudlessBackupQueueStatus
import com.mikeyphw.studyflow.core.domain.runRecoverableOperation
import com.mikeyphw.studyflow.core.model.StudyFlowSyncProviderIds
import com.mikeyphw.studyflow.core.model.StudySyncChangeSummary
import com.mikeyphw.studyflow.core.model.StudySyncConflictPolicy
import com.mikeyphw.studyflow.core.model.StudySyncConnectionStatus
import com.mikeyphw.studyflow.core.model.StudySyncState
import kotlinx.coroutines.launch

private enum class SyncMutation { PREPARE, DISABLE }

private sealed interface SyncOperationState {
    data object Idle : SyncOperationState
    data class Running(val mutation: SyncMutation) : SyncOperationState
    data class Succeeded(val mutation: SyncMutation) : SyncOperationState
    data class Failed(val mutation: SyncMutation) : SyncOperationState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncFoundationScreen(
    state: StudySyncState,
    summary: StudySyncChangeSummary?,
    summaryFailed: Boolean,
    onRetrySummary: () -> Unit,
    onPrepare: suspend () -> Unit,
    onDisable: suspend () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var operationState by remember { mutableStateOf<SyncOperationState>(SyncOperationState.Idle) }

    fun execute(mutation: SyncMutation) {
        if (operationState is SyncOperationState.Running) return
        scope.launch {
            operationState = SyncOperationState.Running(mutation)
            runRecoverableOperation {
                when (mutation) {
                    SyncMutation.PREPARE -> onPrepare()
                    SyncMutation.DISABLE -> onDisable()
                }
            }.onSuccess {
                operationState = SyncOperationState.Succeeded(mutation)
            }.onFailure {
                operationState = SyncOperationState.Failed(mutation)
            }
        }
    }

    val operationRunning = operationState is SyncOperationState.Running

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
                Text(
                    text = stringResource(
                        R.string.sync_provider_value,
                        stringResource(syncProviderLabel(state.settings.providerId)),
                    ),
                )
                Text(
                    text = stringResource(
                        R.string.sync_automatic_value,
                        stringResource(if (state.settings.automaticSyncEnabled) R.string.sync_flag_enabled else R.string.sync_flag_disabled),
                    ),
                )
                Text(
                    text = stringResource(
                        R.string.sync_encryption_value,
                        stringResource(if (state.settings.encryptRemoteBackups) R.string.sync_flag_enabled else R.string.sync_flag_disabled),
                    ),
                )
            }
            StudyFlowSectionCard(title = stringResource(R.string.sync_local_changes_title)) {
                val currentSummary = summary
                if (summaryFailed) {
                    Text(
                        text = stringResource(R.string.sync_local_changes_failed),
                        color = MaterialTheme.colorScheme.error,
                    )
                    OutlinedButton(onClick = onRetrySummary) {
                        Text(stringResource(R.string.retry))
                    }
                } else if (currentSummary == null) {
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
            when (val operation = operationState) {
                SyncOperationState.Idle -> Unit
                is SyncOperationState.Running -> StudyFlowSectionCard(
                    title = stringResource(R.string.sync_operation_running),
                ) {
                    Text(stringResource(R.string.sync_operation_running_message))
                }
                is SyncOperationState.Succeeded -> StudyFlowSectionCard(
                    title = stringResource(R.string.sync_operation_succeeded),
                ) {
                    Text(stringResource(R.string.sync_operation_succeeded_message))
                }
                is SyncOperationState.Failed -> StudyFlowSectionCard(
                    title = stringResource(R.string.sync_operation_failed),
                ) {
                    Text(
                        stringResource(R.string.sync_operation_failed_message),
                        color = MaterialTheme.colorScheme.error,
                    )
                    OutlinedButton(onClick = { execute(operation.mutation) }) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }
            StudyFlowSectionCard(title = stringResource(R.string.sync_checkpoint_controls_title)) {
                Text(
                    text = stringResource(R.string.sync_checkpoint_controls_message),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = { execute(SyncMutation.PREPARE) }, enabled = !operationRunning,
                    modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.sync_prepare_checkpoint))
                }
                OutlinedButton(
                    onClick = { execute(SyncMutation.DISABLE) },
                    enabled = state.enabled && !operationRunning,
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
                    text = item.action.message(item.pendingChangeCount),
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

private fun syncProviderLabel(providerId: String?): Int = when (providerId) {
    null, StudyFlowSyncProviderIds.NONE -> R.string.sync_provider_none
    StudyFlowSyncProviderIds.LOCAL_FOLDER -> R.string.sync_provider_local_folder
    StudyFlowSyncProviderIds.WEBDAV -> R.string.sync_provider_webdav
    StudyFlowSyncProviderIds.GOOGLE_DRIVE -> R.string.sync_provider_google_drive
    else -> R.string.sync_provider_custom
}


private fun CloudlessBackupQueueAction.titleRes(): Int = when (this) {
    CloudlessBackupQueueAction.PREPARE_CHECKPOINT -> R.string.sync_queue_prepare_checkpoint
    CloudlessBackupQueueAction.CHECKPOINT_CURRENT -> R.string.sync_checkpoint_current
    CloudlessBackupQueueAction.REVIEW_LOCAL_CONFLICT_POLICY -> R.string.sync_queue_review_policy
    CloudlessBackupQueueAction.WAIT_FOR_LOCAL_DATA -> R.string.sync_queue_wait_for_data
    CloudlessBackupQueueAction.UNBLOCK_CHECKPOINT -> R.string.sync_queue_unblock_checkpoint
}

@Composable
private fun CloudlessBackupQueueAction.message(pendingChangeCount: Int): String = when (this) {
    CloudlessBackupQueueAction.PREPARE_CHECKPOINT -> pluralStringResource(
        R.plurals.sync_queue_prepare_checkpoint_message,
        pendingChangeCount,
        pendingChangeCount,
    )
    CloudlessBackupQueueAction.CHECKPOINT_CURRENT -> stringResource(R.string.sync_checkpoint_current_message)
    CloudlessBackupQueueAction.REVIEW_LOCAL_CONFLICT_POLICY -> pluralStringResource(
        R.plurals.sync_queue_review_policy_message,
        pendingChangeCount,
        pendingChangeCount,
    )
    CloudlessBackupQueueAction.WAIT_FOR_LOCAL_DATA -> stringResource(R.string.sync_queue_wait_for_data_message)
    CloudlessBackupQueueAction.UNBLOCK_CHECKPOINT -> pluralStringResource(
        R.plurals.sync_queue_unblock_checkpoint_message,
        pendingChangeCount,
        pendingChangeCount,
    )
}

private fun CloudlessBackupQueueStatus.labelRes(): Int = when (this) {
    CloudlessBackupQueueStatus.READY -> R.string.sync_queue_status_ready
    CloudlessBackupQueueStatus.WAITING -> R.string.sync_queue_status_waiting
    CloudlessBackupQueueStatus.BLOCKED -> R.string.sync_queue_status_blocked
}
