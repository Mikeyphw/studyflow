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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mikeyphw.studyflow.core.designsystem.StudyFlowSectionCard
import com.mikeyphw.studyflow.core.domain.CloudlessBackupQueueAction
import com.mikeyphw.studyflow.core.domain.CloudlessBackupQueueItem
import com.mikeyphw.studyflow.core.domain.CloudlessBackupQueuePlanner
import com.mikeyphw.studyflow.core.domain.CloudlessBackupQueueStatus
import com.mikeyphw.studyflow.core.domain.StudyManualFolderCheckpointDetails
import com.mikeyphw.studyflow.core.domain.StudyManualFolderScan
import com.mikeyphw.studyflow.core.domain.runRecoverableOperation
import com.mikeyphw.studyflow.core.model.StudyFlowSyncProviderIds
import com.mikeyphw.studyflow.core.model.StudySyncBackgroundMode
import com.mikeyphw.studyflow.core.model.StudySyncBackgroundSyncPlanner
import com.mikeyphw.studyflow.core.model.StudySyncChangeSummary
import com.mikeyphw.studyflow.core.model.StudySyncConflictReviewItem
import com.mikeyphw.studyflow.core.model.StudySyncConflictPolicy
import com.mikeyphw.studyflow.core.model.StudySyncConnectionStatus
import com.mikeyphw.studyflow.core.model.StudySyncManualAction
import com.mikeyphw.studyflow.core.model.StudySyncManualActionPlanner
import com.mikeyphw.studyflow.core.model.StudySyncState
import com.mikeyphw.studyflow.core.model.StudySyncLedgerSnapshot
import com.mikeyphw.studyflow.core.portability.StudyPlanEncryptedPackageCodec
import kotlinx.coroutines.launch

private enum class SyncMutation {
    PREPARE,
    DISABLE,
    KEEP_OFFLINE,
    UPLOAD_CHECKPOINT,
    CHECK_REMOTE,
    PREVIEW_RESTORE,
    VIEW_HISTORY,
}

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
    onChooseSyncFolder: () -> Unit,
    onUploadEncryptedCheckpoint: suspend (CharArray) -> Unit,
    onCheckRemoteCheckpoints: suspend () -> Unit,
    onPreviewRemoteRestore: suspend (CharArray) -> Unit,
    onViewSyncHistory: suspend () -> Unit,
    modifier: Modifier = Modifier,
    onKeepOffline: suspend () -> Unit = onDisable,
    onLearnMore: () -> Unit = {},
    remoteScan: StudyManualFolderScan? = null,
    restorePreview: StudyManualFolderCheckpointDetails? = null,
    syncHistory: StudySyncLedgerSnapshot? = null,
    conflictReviewItems: List<StudySyncConflictReviewItem> = emptyList(),
    onKeepLocalConflict: (StudySyncConflictReviewItem) -> Unit = {},
    onUseRemoteConflict: (StudySyncConflictReviewItem) -> Unit = {},
    onKeepBothConflict: (StudySyncConflictReviewItem) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var operationState by remember { mutableStateOf<SyncOperationState>(SyncOperationState.Idle) }
    var syncPassphrase by remember { mutableStateOf("") }

    fun execute(mutation: SyncMutation) {
        if (operationState is SyncOperationState.Running) return
        scope.launch {
            operationState = SyncOperationState.Running(mutation)
            runRecoverableOperation {
                when (mutation) {
                    SyncMutation.PREPARE -> onPrepare()
                    SyncMutation.DISABLE -> onDisable()
                    SyncMutation.KEEP_OFFLINE -> onKeepOffline()
                    SyncMutation.CHECK_REMOTE -> onCheckRemoteCheckpoints()
                    SyncMutation.VIEW_HISTORY -> onViewSyncHistory()
                    SyncMutation.UPLOAD_CHECKPOINT, SyncMutation.PREVIEW_RESTORE -> {
                        require(syncPassphrase.length >= StudyPlanEncryptedPackageCodec.MinimumPasswordLength) {
                            "Sync passphrase is too short."
                        }
                        val password = syncPassphrase.toCharArray()
                        try {
                            if (mutation == SyncMutation.UPLOAD_CHECKPOINT) {
                                onUploadEncryptedCheckpoint(password)
                            } else {
                                onPreviewRemoteRestore(password)
                            }
                        } finally {
                            password.fill('\u0000')
                        }
                    }
                }
            }.onSuccess {
                if (mutation == SyncMutation.UPLOAD_CHECKPOINT || mutation == SyncMutation.PREVIEW_RESTORE) {
                    syncPassphrase = ""
                }
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
                val automaticSyncLabel = if (state.settings.automaticSyncEnabled) {
                    R.string.sync_flag_enabled
                } else {
                    R.string.sync_flag_disabled
                }
                val remoteEncryptionLabel = if (state.settings.encryptRemoteBackups) {
                    R.string.sync_flag_enabled
                } else {
                    R.string.sync_flag_disabled
                }
                Text(
                    text = stringResource(
                        R.string.sync_automatic_value,
                        stringResource(automaticSyncLabel),
                    ),
                )
                Text(
                    text = stringResource(
                        R.string.sync_encryption_value,
                        stringResource(remoteEncryptionLabel),
                    ),
                )
                Text(
                    text = stringResource(
                        R.string.sync_background_mode_value,
                        stringResource(syncBackgroundModeLabel(state.settings.effectiveBackgroundMode)),
                    ),
                )
            }
            OptionalSyncOnboardingCard(
                operationRunning = operationRunning,
                onKeepOffline = { execute(SyncMutation.KEEP_OFFLINE) },
                onChooseSyncFolder = onChooseSyncFolder,
                onLearnMore = onLearnMore,
            )
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
            ManualFolderSyncCard(
                state = state,
                operationRunning = operationRunning,
                passphrase = syncPassphrase,
                onPassphraseChange = { syncPassphrase = it },
                onChooseSyncFolder = onChooseSyncFolder,
                onUploadEncryptedCheckpoint = { execute(SyncMutation.UPLOAD_CHECKPOINT) },
                onCheckRemoteCheckpoints = { execute(SyncMutation.CHECK_REMOTE) },
                onPreviewRemoteRestore = { execute(SyncMutation.PREVIEW_RESTORE) },
                onViewSyncHistory = { execute(SyncMutation.VIEW_HISTORY) },
            )
            remoteScan?.let { ManualFolderScanResultCard(it) }
            restorePreview?.let { ManualFolderRestorePreviewCard(it) }
            syncHistory?.let { SyncHistoryResultCard(it) }
            ConflictReviewCard(
                conflictReviewItems = conflictReviewItems,
                onKeepLocalConflict = onKeepLocalConflict,
                onUseRemoteConflict = onUseRemoteConflict,
                onKeepBothConflict = onKeepBothConflict,
            )
            BackgroundSyncModeCard(state = state)
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
                Button(
                    onClick = { execute(SyncMutation.PREPARE) },
                    enabled = !operationRunning,
                    modifier = Modifier.fillMaxWidth(),
                ) {
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
private fun ManualFolderSyncCard(
    state: StudySyncState,
    operationRunning: Boolean,
    passphrase: String,
    onPassphraseChange: (String) -> Unit,
    onChooseSyncFolder: () -> Unit,
    onUploadEncryptedCheckpoint: () -> Unit,
    onCheckRemoteCheckpoints: () -> Unit,
    onPreviewRemoteRestore: () -> Unit,
    onViewSyncHistory: () -> Unit,
) {
    val actions = StudySyncManualActionPlanner.actions(state)
    fun enabled(action: StudySyncManualAction): Boolean =
        actions.first { item -> item.action == action }.enabled && !operationRunning

    StudyFlowSectionCard(title = stringResource(R.string.sync_manual_folder_title)) {
        Text(
            text = stringResource(R.string.sync_manual_folder_message),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = if (state.localFolderSyncConfigured) {
                stringResource(R.string.sync_folder_selected)
            } else {
                stringResource(R.string.sync_folder_missing)
            },
        )
        Text(
            text = stringResource(
                R.string.sync_last_upload_value,
                state.settings.lastManualUploadAtEpochMillis?.toString()
                    ?: stringResource(R.string.sync_never),
            ),
        )
        Text(
            text = stringResource(
                R.string.sync_last_remote_check_value,
                state.settings.lastRemoteCheckAtEpochMillis?.toString()
                    ?: stringResource(R.string.sync_never),
            ),
        )
        Text(
            text = stringResource(
                R.string.sync_remote_checkpoint_count,
                state.settings.remoteCheckpointCount,
            ),
        )
        OutlinedTextField(
            value = passphrase,
            onValueChange = onPassphraseChange,
            enabled = state.localFolderSyncConfigured && !operationRunning,
            label = { Text(stringResource(R.string.sync_passphrase_label)) },
            supportingText = { Text(stringResource(R.string.sync_passphrase_help)) },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = onChooseSyncFolder,
            enabled = enabled(StudySyncManualAction.CHOOSE_SYNC_FOLDER),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.sync_choose_folder))
        }
        Button(
            onClick = onUploadEncryptedCheckpoint,
            enabled = enabled(StudySyncManualAction.PREPARE_UPLOAD_CHECKPOINT) &&
                passphrase.length >= StudyPlanEncryptedPackageCodec.MinimumPasswordLength,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.sync_prepare_upload_checkpoint))
        }
        OutlinedButton(
            onClick = onCheckRemoteCheckpoints,
            enabled = enabled(StudySyncManualAction.CHECK_REMOTE_CHECKPOINTS),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.sync_check_remote_checkpoint))
        }
        OutlinedButton(
            onClick = onPreviewRemoteRestore,
            enabled = enabled(StudySyncManualAction.PREVIEW_RESTORE) &&
                passphrase.length >= StudyPlanEncryptedPackageCodec.MinimumPasswordLength,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.sync_preview_restore))
        }
        OutlinedButton(
            onClick = onViewSyncHistory,
            enabled = enabled(StudySyncManualAction.VIEW_SYNC_HISTORY),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.sync_view_history))
        }
    }
}

@Composable
private fun BackgroundSyncModeCard(state: StudySyncState) {
    val plan = StudySyncBackgroundSyncPlanner.plan(state.settings)
    StudyFlowSectionCard(title = stringResource(R.string.sync_background_title)) {
        Text(
            text = stringResource(R.string.sync_background_message),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(
                R.string.sync_background_mode_value,
                stringResource(syncBackgroundModeLabel(plan.mode)),
            ),
        )
        val wifiLabel = if (plan.constraints.wifiOnly) {
            R.string.sync_flag_enabled
        } else {
            R.string.sync_flag_disabled
        }
        Text(
            text = stringResource(
                R.string.sync_background_wifi_value,
                stringResource(wifiLabel),
            ),
        )
        Text(
            text = stringResource(
                R.string.sync_background_battery_value,
                stringResource(
                    if (plan.constraints.batteryNotLow) {
                        R.string.sync_flag_enabled
                    } else {
                        R.string.sync_flag_disabled
                    },
                ),
            ),
        )
        val chargingLabel = if (plan.constraints.chargingOnly) {
            R.string.sync_flag_enabled
        } else {
            R.string.sync_flag_disabled
        }
        Text(
            text = stringResource(
                R.string.sync_background_charging_value,
                stringResource(chargingLabel),
            ),
        )
        plan.blockedReason?.let { reason ->
            Text(
                text = stringResource(R.string.sync_background_blocked_value, reason),
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun ManualFolderScanResultCard(scan: StudyManualFolderScan) {
    StudyFlowSectionCard(title = stringResource(R.string.sync_scan_result_title)) {
        Text(stringResource(R.string.sync_remote_checkpoint_count, scan.checkpointCount))
        Text(
            stringResource(
                R.string.sync_latest_remote_file,
                scan.latestFileName ?: stringResource(R.string.sync_none_found),
            ),
        )
        scan.latestModifiedAtEpochMillis?.let { modified ->
            Text(stringResource(R.string.sync_latest_remote_modified, modified.toString()))
        }
    }
}

@Composable
private fun ManualFolderRestorePreviewCard(preview: StudyManualFolderCheckpointDetails) {
    StudyFlowSectionCard(title = stringResource(R.string.sync_restore_preview_title)) {
        Text(stringResource(R.string.sync_restore_preview_file, preview.fileName))
        Text(stringResource(R.string.sync_restore_preview_device, preview.deviceName))
        Text(stringResource(R.string.sync_restore_preview_revision, preview.graphRevision))
        Text(stringResource(R.string.sync_restore_preview_changes, preview.changeCount))
        Text(stringResource(R.string.sync_restore_preview_database_bytes, preview.databaseByteSize))
        Text(stringResource(R.string.sync_restore_preview_attachments, preview.attachmentCount))
        Text(stringResource(R.string.sync_restore_preview_app_version, preview.applicationVersion))
        Text(
            text = stringResource(R.string.sync_restore_preview_read_only),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SyncHistoryResultCard(history: StudySyncLedgerSnapshot) {
    StudyFlowSectionCard(title = stringResource(R.string.sync_history_title)) {
        Text(stringResource(R.string.sync_history_devices, history.devices.size))
        Text(stringResource(R.string.sync_history_checkpoints, history.checkpoints.size))
        Text(stringResource(R.string.sync_history_events, history.events.size))
        history.latestCheckpoint?.let { checkpoint ->
            Text(stringResource(R.string.sync_history_latest_checkpoint, checkpoint.id))
        }
        history.events.maxByOrNull { event -> event.sequence }?.let { event ->
            Text(stringResource(R.string.sync_history_latest_event, event.message))
        }
    }
}

@Composable
private fun ConflictReviewCard(
    conflictReviewItems: List<StudySyncConflictReviewItem>,
    onKeepLocalConflict: (StudySyncConflictReviewItem) -> Unit,
    onUseRemoteConflict: (StudySyncConflictReviewItem) -> Unit,
    onKeepBothConflict: (StudySyncConflictReviewItem) -> Unit,
) {
    StudyFlowSectionCard(title = stringResource(R.string.sync_conflict_review_title)) {
        Text(
            text = stringResource(R.string.sync_conflict_review_message),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (conflictReviewItems.isEmpty()) {
            Text(text = stringResource(R.string.sync_conflict_review_empty))
        } else {
            conflictReviewItems.forEach { item ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    tonalElevation = 1.dp,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(
                                R.string.sync_conflict_review_item_title,
                                item.entityType,
                                item.entityId,
                            ),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = stringResource(R.string.sync_conflict_review_local, item.localSummary),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            text = stringResource(R.string.sync_conflict_review_remote, item.remoteSummary),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Button(
                            onClick = { onKeepLocalConflict(item) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.sync_conflict_keep_local))
                        }
                        OutlinedButton(
                            onClick = { onUseRemoteConflict(item) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.sync_conflict_use_remote))
                        }
                        OutlinedButton(
                            onClick = { onKeepBothConflict(item) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.sync_conflict_keep_both))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionalSyncOnboardingCard(
    operationRunning: Boolean,
    onKeepOffline: () -> Unit,
    onChooseSyncFolder: () -> Unit,
    onLearnMore: () -> Unit,
) {
    StudyFlowSectionCard(title = stringResource(R.string.sync_onboarding_title)) {
        Text(
            text = stringResource(R.string.sync_onboarding_message),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = onKeepOffline,
            enabled = !operationRunning,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.sync_keep_offline))
        }
        OutlinedButton(
            onClick = onChooseSyncFolder,
            enabled = !operationRunning,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.sync_choose_folder))
        }
        OutlinedButton(
            onClick = onLearnMore,
            enabled = !operationRunning,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.sync_learn_more))
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

private fun syncBackgroundModeLabel(mode: StudySyncBackgroundMode): Int = when (mode) {
    StudySyncBackgroundMode.OFF -> R.string.sync_background_mode_off
    StudySyncBackgroundMode.MANUAL_ONLY -> R.string.sync_background_mode_manual_only
    StudySyncBackgroundMode.UPLOAD_AFTER_CHANGES -> R.string.sync_background_mode_upload_after_changes
    StudySyncBackgroundMode.CHECK_DAILY -> R.string.sync_background_mode_check_daily
    StudySyncBackgroundMode.FULL_AUTOMATIC -> R.string.sync_background_mode_full_automatic
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
