package com.mikeyphw.studyflow.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mikeyphw.studyflow.core.designsystem.StudyFlowSectionCard
import com.mikeyphw.studyflow.core.diagnostics.DebugProbeCatalog
import com.mikeyphw.studyflow.core.diagnostics.DebugProbeDescriptor
import com.mikeyphw.studyflow.core.diagnostics.DebugProbeId
import com.mikeyphw.studyflow.core.diagnostics.DebugProbeMessage
import com.mikeyphw.studyflow.core.diagnostics.DebugProbeReport
import com.mikeyphw.studyflow.core.diagnostics.DebugProbeResult
import com.mikeyphw.studyflow.core.diagnostics.DebugProbeSafety
import com.mikeyphw.studyflow.core.diagnostics.DebugProbeStatus

@Composable
fun DebugProbePanel(
    report: DebugProbeReport,
    safeSuiteRunning: Boolean,
    onRunProbe: (DebugProbeId) -> Unit,
    onRunSafeSuite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            StudyFlowSectionCard(title = stringResource(R.string.debug_probes_title)) {
                Text(
                    text = stringResource(R.string.debug_probes_d3_message),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(
                        R.string.debug_probe_summary,
                        report.passedCount,
                        report.warningCount,
                        report.blockedCount,
                        report.failedCount,
                    ),
                )
                Button(
                    onClick = onRunSafeSuite,
                    enabled = !safeSuiteRunning && report.running.isEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(
                            if (safeSuiteRunning) R.string.debug_probe_safe_suite_running
                            else R.string.debug_probe_run_safe_suite,
                        ),
                    )
                }
                Text(
                    text = stringResource(R.string.debug_probe_safe_suite_note),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(DebugProbeCatalog.descriptors, key = { descriptor -> descriptor.id }) { descriptor ->
            DebugProbeCard(
                descriptor = descriptor,
                result = report.results[descriptor.id],
                running = descriptor.id in report.running,
                enabled = !safeSuiteRunning && report.running.isEmpty(),
                onRun = { onRunProbe(descriptor.id) },
            )
        }
    }
}

@Composable
private fun DebugProbeCard(
    descriptor: DebugProbeDescriptor,
    result: DebugProbeResult?,
    running: Boolean,
    enabled: Boolean,
    onRun: () -> Unit,
) {
    StudyFlowSectionCard(title = descriptor.id.label()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProbeStatusPill(if (running) DebugProbeStatus.RUNNING else result?.status ?: DebugProbeStatus.NOT_RUN)
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Text(
                    text = descriptor.safety.label(),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
        Text(
            text = descriptor.id.description(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        result?.message?.takeIf { it != DebugProbeMessage.NONE }?.let { message ->
            Text(text = message.label(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        result?.safeFacts?.forEach { (key, value) ->
            Text(stringResource(R.string.debug_probe_fact, key.factLabel(), value))
        }
        if (result != null && result.durationMillis > 0L) {
            Text(stringResource(R.string.debug_duration_value, result.durationMillis))
        }
        result?.exception?.let { failure ->
            Text(stringResource(R.string.debug_exception_value, failure.type, failure.fingerprint))
        }
        val buttonText = when {
            running -> stringResource(R.string.debug_probe_running)
            descriptor.safety == DebugProbeSafety.VISIBLE_SIDE_EFFECT -> {
                stringResource(R.string.debug_probe_run_side_effect)
            }
            else -> stringResource(R.string.debug_probe_run)
        }
        if (descriptor.safety == DebugProbeSafety.VISIBLE_SIDE_EFFECT) {
            OutlinedButton(onClick = onRun, enabled = enabled && !running, modifier = Modifier.fillMaxWidth()) {
                Text(buttonText)
            }
        } else {
            Button(onClick = onRun, enabled = enabled && !running, modifier = Modifier.fillMaxWidth()) {
                Text(buttonText)
            }
        }
    }
}

@Composable
private fun ProbeStatusPill(status: DebugProbeStatus) {
    Surface(
        color = when (status) {
            DebugProbeStatus.PASSED -> MaterialTheme.colorScheme.primaryContainer
            DebugProbeStatus.WARNING, DebugProbeStatus.BLOCKED -> MaterialTheme.colorScheme.tertiaryContainer
            DebugProbeStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Text(
            text = status.label(),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun DebugProbeId.label(): String = stringResource(
    when (this) {
        DebugProbeId.RECORDER_HEALTH -> R.string.debug_probe_recorder_title
        DebugProbeId.NOTIFICATION_HEALTH -> R.string.debug_probe_notification_title
        DebugProbeId.WIDGET_HEALTH -> R.string.debug_probe_widget_title
        DebugProbeId.PROCESS_EXIT_HISTORY -> R.string.debug_probe_process_title
        DebugProbeId.BACKUP_CONFIGURATION -> R.string.debug_probe_backup_config_title
        DebugProbeId.FILE_PROVIDER_CONFIGURATION -> R.string.debug_probe_file_provider_title
        DebugProbeId.REMINDER_RECONCILIATION -> R.string.debug_probe_reminder_title
        DebugProbeId.WIDGET_REFRESH -> R.string.debug_probe_widget_refresh_title
        DebugProbeId.BACKUP_DRY_RUN -> R.string.debug_probe_backup_dry_run_title
        DebugProbeId.SEARCH_QUERY -> R.string.debug_probe_search_title
        DebugProbeId.RECOMMENDATION_QUERY -> R.string.debug_probe_recommendation_title
        DebugProbeId.SYNC_FOUNDATION -> R.string.debug_probe_sync_title
    },
)

@Composable
private fun DebugProbeId.description(): String = stringResource(
    when (this) {
        DebugProbeId.RECORDER_HEALTH -> R.string.debug_probe_recorder_description
        DebugProbeId.NOTIFICATION_HEALTH -> R.string.debug_probe_notification_description
        DebugProbeId.WIDGET_HEALTH -> R.string.debug_probe_widget_description
        DebugProbeId.PROCESS_EXIT_HISTORY -> R.string.debug_probe_process_description
        DebugProbeId.BACKUP_CONFIGURATION -> R.string.debug_probe_backup_config_description
        DebugProbeId.FILE_PROVIDER_CONFIGURATION -> R.string.debug_probe_file_provider_description
        DebugProbeId.REMINDER_RECONCILIATION -> R.string.debug_probe_reminder_description
        DebugProbeId.WIDGET_REFRESH -> R.string.debug_probe_widget_refresh_description
        DebugProbeId.BACKUP_DRY_RUN -> R.string.debug_probe_backup_dry_run_description
        DebugProbeId.SEARCH_QUERY -> R.string.debug_probe_search_description
        DebugProbeId.RECOMMENDATION_QUERY -> R.string.debug_probe_recommendation_description
        DebugProbeId.SYNC_FOUNDATION -> R.string.debug_probe_sync_description
    },
)

@Composable
private fun DebugProbeSafety.label(): String = stringResource(
    if (this == DebugProbeSafety.READ_ONLY) R.string.debug_probe_read_only
    else R.string.debug_probe_visible_side_effect,
)

@Composable
private fun DebugProbeStatus.label(): String = stringResource(
    when (this) {
        DebugProbeStatus.NOT_RUN -> R.string.debug_probe_not_run
        DebugProbeStatus.RUNNING -> R.string.debug_probe_running
        DebugProbeStatus.PASSED -> R.string.debug_probe_passed
        DebugProbeStatus.WARNING -> R.string.debug_probe_warning
        DebugProbeStatus.BLOCKED -> R.string.debug_probe_blocked
        DebugProbeStatus.FAILED -> R.string.debug_probe_failed
        DebugProbeStatus.CANCELLED -> R.string.debug_probe_cancelled
    },
)

@Composable
private fun DebugProbeMessage.label(): String = stringResource(
    when (this) {
        DebugProbeMessage.NONE -> R.string.debug_probe_message_none
        DebugProbeMessage.MISSING_DEFAULT_PLAN -> R.string.debug_probe_message_missing_plan
        DebugProbeMessage.NOTIFICATION_PERMISSION_DENIED -> R.string.debug_probe_message_notification_permission
        DebugProbeMessage.NOTIFICATIONS_BLOCKED -> R.string.debug_probe_message_notifications_blocked
        DebugProbeMessage.CHANNEL_MISSING -> R.string.debug_probe_message_channel_missing
        DebugProbeMessage.CHANNEL_BLOCKED -> R.string.debug_probe_message_channel_blocked
        DebugProbeMessage.WIDGETS_UNSUPPORTED -> R.string.debug_probe_message_widgets_unsupported
        DebugProbeMessage.WIDGET_PROVIDER_MISSING -> R.string.debug_probe_message_widget_provider_missing
        DebugProbeMessage.WIDGET_PROVIDER_DISABLED -> R.string.debug_probe_message_widget_provider_disabled
        DebugProbeMessage.NO_WIDGET_INSTANCES -> R.string.debug_probe_message_no_widget
        DebugProbeMessage.PROCESS_HISTORY_UNAVAILABLE -> R.string.debug_probe_message_process_unavailable
        DebugProbeMessage.NO_PROCESS_EXIT_HISTORY -> R.string.debug_probe_message_no_process_history
        DebugProbeMessage.RECENT_CRASH_OR_ANR -> R.string.debug_probe_message_recent_crash
        DebugProbeMessage.RECENT_ABNORMAL_EXIT -> R.string.debug_probe_message_recent_abnormal_exit
        DebugProbeMessage.RECENT_LOW_MEMORY_EXIT -> R.string.debug_probe_message_recent_low_memory_exit
        DebugProbeMessage.BACKUP_POLICY_INVALID -> R.string.debug_probe_message_backup_policy_invalid
        DebugProbeMessage.BACKUP_RULES_INVALID -> R.string.debug_probe_message_backup_rules_invalid
        DebugProbeMessage.FILE_PROVIDER_INVALID -> R.string.debug_probe_message_file_provider
        DebugProbeMessage.WIDGET_REFRESH_NOT_OBSERVED -> R.string.debug_probe_message_widget_refresh_not_observed
        DebugProbeMessage.BACKUP_DRY_RUN_INTEGRITY_FAILED -> R.string.debug_probe_message_backup_dry_run
        DebugProbeMessage.SEARCH_CANARY_FAILED -> R.string.debug_probe_message_search_canary
        DebugProbeMessage.NO_SEARCH_EVIDENCE -> R.string.debug_probe_message_no_search_evidence
        DebugProbeMessage.RECOMMENDATION_CANARY_FAILED -> R.string.debug_probe_message_recommendation_canary
        DebugProbeMessage.NO_RECOMMENDATION_EVIDENCE -> R.string.debug_probe_message_no_recommendation_evidence
        DebugProbeMessage.SYNC_CANARY_FAILED -> R.string.debug_probe_message_sync_canary
        DebugProbeMessage.PROBE_TIMED_OUT -> R.string.debug_probe_message_timed_out
    },
)

@Composable
private fun String.factLabel(): String = stringResource(
    when (this) {
        "api_level" -> R.string.debug_probe_fact_api
        "available" -> R.string.debug_probe_fact_available
        "count" -> R.string.debug_probe_fact_count
        "enabled" -> R.string.debug_probe_fact_enabled
        "importance" -> R.string.debug_probe_fact_importance
        "provider_registered" -> R.string.debug_probe_fact_provider_registered
        "provider_metadata" -> R.string.debug_probe_fact_provider_metadata
        "legacy_rule_linked" -> R.string.debug_probe_fact_legacy_linked
        "modern_rule_linked" -> R.string.debug_probe_fact_modern_linked
        "legacy_rule_present" -> R.string.debug_probe_fact_legacy_present
        "modern_rule_present" -> R.string.debug_probe_fact_modern_present
        "path_metadata" -> R.string.debug_probe_fact_path_metadata
        else -> R.string.debug_probe_fact_value
    },
)
