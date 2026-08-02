package com.mikeyphw.studyflow.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mikeyphw.studyflow.core.diagnostics.DatabasePortabilityLabActionResult
import com.mikeyphw.studyflow.core.diagnostics.DatabasePortabilityLabReport
import com.mikeyphw.studyflow.core.diagnostics.ReminderLabActionResult
import com.mikeyphw.studyflow.core.diagnostics.ReminderNotificationLabReport

private enum class DebugLabPage {
    REMINDERS,
    DATABASE_PORTABILITY,
}

@Composable
fun DebugLabsPanel(
    reminderReport: ReminderNotificationLabReport,
    reminderActionResult: ReminderLabActionResult?,
    reminderRunning: Boolean,
    onInspectReminders: () -> Unit,
    onReconcileReminders: () -> Unit,
    onPostDiagnosticNotification: () -> Unit,
    databasePortabilityReport: DatabasePortabilityLabReport,
    databasePortabilityActionResult: DatabasePortabilityLabActionResult?,
    databasePortabilityRunning: Boolean,
    onInspectDatabase: () -> Unit,
    onRunPortabilityDrills: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pageName by rememberSaveable { mutableStateOf(DebugLabPage.REMINDERS.name) }
    val page = DebugLabPage.entries.firstOrNull { it.name == pageName } ?: DebugLabPage.REMINDERS
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = page == DebugLabPage.REMINDERS,
                onClick = { pageName = DebugLabPage.REMINDERS.name },
                label = { Text(stringResource(R.string.debug_lab_page_reminders)) },
            )
            FilterChip(
                selected = page == DebugLabPage.DATABASE_PORTABILITY,
                onClick = { pageName = DebugLabPage.DATABASE_PORTABILITY.name },
                label = { Text(stringResource(R.string.debug_lab_page_database)) },
            )
        }
        when (page) {
            DebugLabPage.REMINDERS -> ReminderNotificationLabPanel(
                report = reminderReport,
                actionResult = reminderActionResult,
                running = reminderRunning,
                onInspect = onInspectReminders,
                onReconcile = onReconcileReminders,
                onPostDiagnosticNotification = onPostDiagnosticNotification,
                modifier = Modifier.fillMaxSize(),
            )
            DebugLabPage.DATABASE_PORTABILITY -> DatabasePortabilityLabPanel(
                report = databasePortabilityReport,
                actionResult = databasePortabilityActionResult,
                running = databasePortabilityRunning,
                onInspectDatabase = onInspectDatabase,
                onRunPortabilityDrills = onRunPortabilityDrills,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
