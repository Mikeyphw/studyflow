package com.mikeyphw.studyflow.feature.privacy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mikeyphw.studyflow.core.designsystem.StudyFlowSectionCard
import com.mikeyphw.studyflow.core.domain.LocalRetentionRuleType
import com.mikeyphw.studyflow.core.domain.StudyFlowLocalDataRetentionPolicy

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyCenterScreen(
    hasDefaultPlan: Boolean,
    onOpenBackupCenter: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val policy = StudyFlowLocalDataRetentionPolicy.current()
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.privacy_center_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.go_back))
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StudyFlowSectionCard(title = stringResource(R.string.retention_policy_title)) {
                policy.rules.forEach { rule ->
                    Text(stringResource(rule.type.titleResource()), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(rule.type.descriptionResource()),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            StudyFlowSectionCard(title = stringResource(R.string.recovery_title)) {
                Text(
                    stringResource(R.string.recovery_explanation),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onOpenBackupCenter, enabled = hasDefaultPlan) {
                    Text(stringResource(R.string.open_backup_center))
                }
            }
            StudyFlowSectionCard(title = stringResource(R.string.security_boundary_title)) {
                Text(
                    stringResource(R.string.security_boundary_message),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun LocalRetentionRuleType.titleResource(): Int = when (this) {
    LocalRetentionRuleType.PLAN_DATA -> R.string.retention_active_title
    LocalRetentionRuleType.TRASH -> R.string.retention_trash_title
    LocalRetentionRuleType.PRIVATE_FILES -> R.string.retention_private_files_title
    LocalRetentionRuleType.EXPORTED_FILES -> R.string.retention_exports_title
}

private fun LocalRetentionRuleType.descriptionResource(): Int = when (this) {
    LocalRetentionRuleType.PLAN_DATA -> R.string.retention_active_message
    LocalRetentionRuleType.TRASH -> R.string.retention_trash_message
    LocalRetentionRuleType.PRIVATE_FILES -> R.string.retention_private_files_message
    LocalRetentionRuleType.EXPORTED_FILES -> R.string.retention_exports_message
}
