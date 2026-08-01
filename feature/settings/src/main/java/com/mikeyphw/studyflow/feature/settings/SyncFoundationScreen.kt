package com.mikeyphw.studyflow.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mikeyphw.studyflow.core.designsystem.StudyFlowSectionCard
import com.mikeyphw.studyflow.core.model.StudySyncChangeSummary
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
                Text(text = stringResource(R.string.sync_provider_value, state.provider.displayName))
                Text(text = stringResource(R.string.sync_status_value, state.connectionStatus.name))
                Text(text = stringResource(R.string.sync_conflict_value, state.conflictPolicy.name))
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
                    Text(text = stringResource(R.string.sync_change_token, currentSummary.localChangeToken))
                }
            }
            StudyFlowSectionCard(title = stringResource(R.string.sync_provider_gate_title)) {
                Text(
                    text = stringResource(R.string.sync_provider_gate_message),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onPrepare, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.sync_prepare_foundation))
                }
                OutlinedButton(
                    onClick = onDisable,
                    enabled = state.enabled,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.sync_disable_foundation))
                }
            }
        }
    }
}
