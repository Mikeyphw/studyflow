package com.mikeyphw.studyflow.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mikeyphw.studyflow.core.designsystem.StudyFlowSectionCard
import com.mikeyphw.studyflow.core.model.AppLanguage
import com.mikeyphw.studyflow.core.model.AppPreferences
import com.mikeyphw.studyflow.core.model.StudySyncConnectionStatus
import com.mikeyphw.studyflow.core.model.StudySyncState
import com.mikeyphw.studyflow.core.model.ThemeMode

@Composable
fun SettingsScreen(
    preferences: AppPreferences,
    hasDefaultPlan: Boolean,
    syncState: StudySyncState,
    onLanguageChanged: (AppLanguage) -> Unit,
    onThemeModeChanged: (ThemeMode) -> Unit,
    onDynamicColorChanged: (Boolean) -> Unit,
    onOpenDefaultPlanBackup: () -> Unit,
    onOpenDefaultPlanReminders: () -> Unit,
    onOpenPrivacyCenter: () -> Unit,
    onOpenSyncCenter: () -> Unit,
    onOpenDebugWorkbench: () -> Unit,
    modifier: Modifier = Modifier,
    diagnosticsAvailable: Boolean = true,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineLarge,
            )
            Text(
                text = stringResource(R.string.settings_subtitle),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        StudyFlowSectionCard(title = stringResource(R.string.language_title)) {
            AppLanguage.entries.forEach { language ->
                SelectionRow(
                    label = stringResource(languageLabel(language)),
                    selected = preferences.language == language,
                    onClick = { onLanguageChanged(language) },
                )
            }
        }
        StudyFlowSectionCard(title = stringResource(R.string.appearance_title)) {
            ThemeMode.entries.forEach { themeMode ->
                SelectionRow(
                    label = stringResource(themeLabel(themeMode)),
                    selected = preferences.themeMode == themeMode,
                    onClick = { onThemeModeChanged(themeMode) },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(R.string.dynamic_color))
                    Text(
                        text = stringResource(R.string.dynamic_color_description),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = preferences.dynamicColorEnabled,
                    onCheckedChange = onDynamicColorChanged,
                )
            }
        }
        StudyFlowSectionCard(title = stringResource(R.string.backup_title)) {
            Text(
                text = if (hasDefaultPlan) {
                    stringResource(R.string.backup_ready)
                } else {
                    stringResource(R.string.backup_needs_default_plan)
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onOpenDefaultPlanBackup, enabled = hasDefaultPlan) {
                Text(stringResource(R.string.open_backup_center))
            }
        }
        StudyFlowSectionCard(title = stringResource(R.string.reminders_title)) {
            Text(
                text = stringResource(R.string.reminders_settings_message),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onOpenDefaultPlanReminders, enabled = hasDefaultPlan) {
                Text(stringResource(R.string.open_reminders_center))
            }
        }
        StudyFlowSectionCard(title = stringResource(R.string.privacy_title)) {
            Text(
                text = stringResource(R.string.privacy_message),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onOpenPrivacyCenter) {
                Text(stringResource(R.string.open_privacy_center))
            }
        }
        StudyFlowSectionCard(title = stringResource(R.string.sync_title)) {
            Text(
                text = stringResource(
                    R.string.sync_settings_message,
                    stringResource(syncConnectionStatusLabel(syncState.connectionStatus)),
                    syncState.pendingLocalChangeCount,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onOpenSyncCenter) {
                Text(stringResource(R.string.open_sync_center))
            }
        }
        if (diagnosticsAvailable) {
            StudyFlowSectionCard(title = stringResource(R.string.debug_workbench_title)) {
                Text(
                    text = stringResource(R.string.debug_workbench_settings_message),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onOpenDebugWorkbench) {
                    Text(stringResource(R.string.open_debug_workbench))
                }
            }
        }
    }
}

@Composable
private fun SelectionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(text = label, modifier = Modifier.padding(start = 8.dp))
    }
}

@StringRes
private fun languageLabel(language: AppLanguage): Int = when (language) {
    AppLanguage.SYSTEM_DEFAULT -> R.string.language_system
    AppLanguage.ENGLISH -> R.string.language_english
    AppLanguage.PORTUGUESE_BRAZIL -> R.string.language_portuguese_brazil
}

@StringRes
private fun themeLabel(themeMode: ThemeMode): Int = when (themeMode) {
    ThemeMode.SYSTEM_DEFAULT -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}

@StringRes
private fun syncConnectionStatusLabel(status: StudySyncConnectionStatus): Int = when (status) {
    StudySyncConnectionStatus.DISABLED -> R.string.sync_connection_local_only
    StudySyncConnectionStatus.LOCAL_READY -> R.string.sync_connection_local_ready
    StudySyncConnectionStatus.CHECKPOINT_NEEDED -> R.string.sync_connection_checkpoint_needed
    StudySyncConnectionStatus.CHECKPOINT_BLOCKED -> R.string.sync_connection_checkpoint_error
}
