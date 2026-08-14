package com.mikeyphw.studyflow.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.mikeyphw.studyflow.core.designsystem.StudyFlowSectionCard
import com.mikeyphw.studyflow.core.diagnostics.DebugWorkbenchShellReport
import com.mikeyphw.studyflow.core.diagnostics.RuntimeSelfCheckHistory
import com.mikeyphw.studyflow.core.diagnostics.SupportBundleSealMessage
import com.mikeyphw.studyflow.core.diagnostics.SupportBundleSealReport
import com.mikeyphw.studyflow.core.diagnostics.SupportBundleSealStatus
import com.mikeyphw.studyflow.core.diagnostics.DiagnosticArea
import com.mikeyphw.studyflow.core.diagnostics.DiagnosticOutcome
import com.mikeyphw.studyflow.core.diagnostics.DiagnosticRecorderHealth
import com.mikeyphw.studyflow.core.diagnostics.DiagnosticSeverity
import com.mikeyphw.studyflow.core.diagnostics.DiagnosticTimelineEntry
import com.mikeyphw.studyflow.core.diagnostics.DiagnosticTimelineSnapshot
import java.text.DateFormat
import java.util.Date

enum class DebugWorkbenchSection {
    OVERVIEW,
    TIMELINE,
    PROBES,
    LABS,
    REPORTS,
}

enum class DebugWorkbenchActionResult {
    REFRESHED,
    REFRESH_FAILED,
    STATUS_COPIED,
    TIMELINE_COPIED,
    COPY_FAILED,
    HISTORY_CLEARED,
    HISTORY_BUSY,
    BUNDLE_DIRECTORY_FAILED,
    BUNDLE_UNAVAILABLE,
    BUNDLE_SHARE_FAILED,
    BUNDLE_SEAL_FAILED,
    BUNDLE_READY,
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DebugWorkbenchScreen(
    shellReport: DebugWorkbenchShellReport,
    timeline: DiagnosticTimelineSnapshot,
    actionResult: DebugWorkbenchActionResult?,
    onRefresh: () -> Unit,
    onCopyStatus: () -> Unit,
    onCopyTimeline: () -> Unit,
    onClearHistory: () -> Unit,
    onExportSupportBundle: () -> Unit,
    onOpenRuntimeSmokeWorkbench: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    selfCheckHistory: RuntimeSelfCheckHistory = RuntimeSelfCheckHistory(),
    supportBundleSealReport: SupportBundleSealReport = SupportBundleSealReport.notRun(),
    probeContent: @Composable () -> Unit = {
        DebugProbeEntryPanel(onOpenRuntimeSmokeWorkbench)
    },
    labContent: @Composable () -> Unit = {
        DebugLabEmptyPanel()
    },
) {
    var sectionName by rememberSaveable { mutableStateOf(DebugWorkbenchSection.OVERVIEW.name) }
    val section = DebugWorkbenchSection.entries.firstOrNull { it.name == sectionName }
        ?: DebugWorkbenchSection.OVERVIEW
    Scaffold(modifier = modifier) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.debug_workbench_title),
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(R.string.debug_workbench_message),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DebugWorkbenchSection.entries.forEach { candidate ->
                        FilterChip(
                            selected = section == candidate,
                            onClick = { sectionName = candidate.name },
                            label = { Text(candidate.label()) },
                        )
                    }
                }
            }
            when (section) {
                DebugWorkbenchSection.OVERVIEW -> DebugOverview(
                    shellReport = shellReport,
                    actionResult = actionResult,
                    selfCheckHistory = selfCheckHistory,
                    supportBundleSealReport = supportBundleSealReport,
                    onRefresh = onRefresh,
                    onBack = onBack,
                )
                DebugWorkbenchSection.TIMELINE -> DebugTimeline(
                    timeline = timeline,
                    onRefresh = onRefresh,
                )
                DebugWorkbenchSection.PROBES -> probeContent()
                DebugWorkbenchSection.LABS -> labContent()
                DebugWorkbenchSection.REPORTS -> DebugReports(
                    shellReport = shellReport,
                    actionResult = actionResult,
                    selfCheckHistory = selfCheckHistory,
                    supportBundleSealReport = supportBundleSealReport,
                    onCopyStatus = onCopyStatus,
                    onCopyTimeline = onCopyTimeline,
                    onClearHistory = onClearHistory,
                    onExportSupportBundle = onExportSupportBundle,
                )
            }
        }
    }
}

@Composable
private fun DebugOverview(
    shellReport: DebugWorkbenchShellReport,
    actionResult: DebugWorkbenchActionResult?,
    selfCheckHistory: RuntimeSelfCheckHistory,
    supportBundleSealReport: SupportBundleSealReport,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            StudyFlowSectionCard(title = stringResource(R.string.debug_live_status_title)) {
                DebugStatusPill(shellReport.recorderHealth)
                Text(
                    stringResource(
                        R.string.debug_recording_value,
                        stringResource(if (shellReport.recordingEnabled) R.string.debug_yes else R.string.debug_no),
                    ),
                )
                Text(pluralStringResource(R.plurals.debug_event_count, shellReport.eventCount, shellReport.eventCount))
                Text(
                    stringResource(
                        R.string.debug_warning_error_count,
                        shellReport.warningCount,
                        shellReport.errorCount,
                    ),
                )
                Text(stringResource(R.string.debug_dropped_count, shellReport.droppedEventCount))
                Text(
                    text = shellReport.writerFailureFingerprint?.let { fingerprint ->
                        stringResource(R.string.debug_writer_failure_value, fingerprint)
                    } ?: stringResource(R.string.debug_writer_healthy),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onRefresh) {
                        Text(stringResource(R.string.debug_refresh))
                    }
                    OutlinedButton(onClick = onBack) {
                        Text(stringResource(R.string.go_back))
                    }
                }
            }
        }
        item {
            StudyFlowSectionCard(title = stringResource(R.string.debug_privacy_boundary_title)) {
                Text(
                    text = stringResource(R.string.debug_privacy_boundary_message),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            StudyFlowSectionCard(title = stringResource(R.string.debug_self_check_history_title)) {
                val latest = selfCheckHistory.latest
                Text(
                    text = stringResource(R.string.debug_self_check_history_count, selfCheckHistory.runs.size),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (latest == null) {
                    Text(stringResource(R.string.debug_self_check_history_empty))
                } else {
                    Text(stringResource(R.string.debug_self_check_latest_passed, latest.passedCount))
                    Text(stringResource(R.string.debug_self_check_latest_warning, latest.warningCount))
                    Text(stringResource(R.string.debug_self_check_latest_blocked, latest.blockedCount))
                    Text(stringResource(R.string.debug_self_check_latest_failed, latest.failedCount))
                    Text(stringResource(R.string.debug_self_check_latest_cancelled, latest.cancelledCount))
                    Text(stringResource(R.string.debug_self_check_latest_verdict, latest.verdict.name))
                }
            }
        }
        item {
            StudyFlowSectionCard(title = stringResource(R.string.debug_support_bundle_seal_title)) {
                Text(supportBundleSealReport.status.label())
                Text(supportBundleSealReport.message.label())
                Text(stringResource(R.string.debug_support_bundle_seal_entries, supportBundleSealReport.entryCount))
                supportBundleSealReport.failingEntryFingerprint?.let { fingerprint ->
                    Text(
                        text = stringResource(R.string.debug_support_bundle_seal_fingerprint, fingerprint),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }

        actionResult?.let { result ->
            item {
                StudyFlowSectionCard(title = stringResource(R.string.debug_last_action_title)) {
                    Text(result.label())
                }
            }
        }
    }
}

@Composable
private fun DebugTimeline(
    timeline: DiagnosticTimelineSnapshot,
    onRefresh: () -> Unit,
) {
    var severityName by rememberSaveable { mutableStateOf("ALL") }
    val selectedSeverity = DiagnosticSeverity.entries.firstOrNull { it.name == severityName }
    val visibleEntries = remember(timeline.entries, selectedSeverity) {
        timeline.entries.filter { entry -> selectedSeverity == null || entry.severity == selectedSeverity }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            StudyFlowSectionCard(title = stringResource(R.string.debug_timeline_title)) {
                Text(
                    text = pluralStringResource(
                        R.plurals.debug_timeline_summary,
                        timeline.eventCount,
                        timeline.eventCount,
                        timeline.malformedLineCount,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedSeverity == null,
                        onClick = { severityName = "ALL" },
                        label = { Text(stringResource(R.string.debug_filter_all)) },
                    )
                    DiagnosticSeverity.entries.forEach { severity ->
                        FilterChip(
                            selected = selectedSeverity == severity,
                            onClick = { severityName = severity.name },
                            label = { Text(severity.label()) },
                        )
                    }
                }
                Button(onClick = onRefresh) {
                    Text(stringResource(R.string.debug_refresh))
                }
            }
        }
        if (visibleEntries.isEmpty()) {
            item {
                StudyFlowSectionCard(title = stringResource(R.string.debug_timeline_empty_title)) {
                    Text(
                        text = stringResource(R.string.debug_timeline_empty_message),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(visibleEntries, key = { entry -> "${entry.timestampEpochMillis}:${entry.sequence}" }) { entry ->
                DiagnosticTimelineCard(entry)
            }
        }
    }
}

@Composable
private fun DiagnosticTimelineCard(entry: DiagnosticTimelineEntry) {
    StudyFlowSectionCard(title = entry.action.displayAction()) {
        Text(
            text = "${entry.area.label()} · ${entry.severity.label()} · ${entry.outcome.label()}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM)
                .format(Date(entry.timestampEpochMillis)),
            style = MaterialTheme.typography.bodySmall,
        )
        entry.durationMillis?.let { duration ->
            Text(stringResource(R.string.debug_duration_value, duration))
        }
        entry.safeDetails.forEach { (key, value) ->
            Text(
                text = "${key.displayAction()}: $value",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
        }
        entry.exception?.let { exception ->
            Text(
                text = stringResource(
                    R.string.debug_exception_value,
                    exception.type,
                    exception.fingerprint,
                ),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun DebugReports(
    shellReport: DebugWorkbenchShellReport,
    actionResult: DebugWorkbenchActionResult?,
    selfCheckHistory: RuntimeSelfCheckHistory,
    supportBundleSealReport: SupportBundleSealReport,
    onCopyStatus: () -> Unit,
    onCopyTimeline: () -> Unit,
    onClearHistory: () -> Unit,
    onExportSupportBundle: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            StudyFlowSectionCard(title = stringResource(R.string.debug_reports_title)) {
                Text(
                    text = stringResource(R.string.debug_reports_message),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onCopyStatus, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.debug_copy_status))
                }
                OutlinedButton(
                    onClick = onCopyTimeline,
                    enabled = shellReport.canCopyTimeline,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.debug_copy_timeline))
                }
                OutlinedButton(
                    onClick = onExportSupportBundle,
                    enabled = shellReport.canExportSupportBundle,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.debug_export_bundle))
                }
                OutlinedButton(
                    onClick = onClearHistory,
                    enabled = shellReport.canClearHistory,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.debug_clear_history))
                }
            }
        }
        item {
            StudyFlowSectionCard(title = stringResource(R.string.debug_self_check_history_title)) {
                val latest = selfCheckHistory.latest
                Text(
                    text = stringResource(R.string.debug_self_check_history_count, selfCheckHistory.runs.size),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (latest == null) {
                    Text(stringResource(R.string.debug_self_check_history_empty))
                } else {
                    Text(stringResource(R.string.debug_self_check_latest_passed, latest.passedCount))
                    Text(stringResource(R.string.debug_self_check_latest_warning, latest.warningCount))
                    Text(stringResource(R.string.debug_self_check_latest_blocked, latest.blockedCount))
                    Text(stringResource(R.string.debug_self_check_latest_failed, latest.failedCount))
                    Text(stringResource(R.string.debug_self_check_latest_cancelled, latest.cancelledCount))
                    Text(stringResource(R.string.debug_self_check_latest_verdict, latest.verdict.name))
                }
            }
        }
        item {
            StudyFlowSectionCard(title = stringResource(R.string.debug_support_bundle_seal_title)) {
                Text(supportBundleSealReport.status.label())
                Text(supportBundleSealReport.message.label())
                Text(stringResource(R.string.debug_support_bundle_seal_entries, supportBundleSealReport.entryCount))
                supportBundleSealReport.failingEntryFingerprint?.let { fingerprint ->
                    Text(
                        text = stringResource(R.string.debug_support_bundle_seal_fingerprint, fingerprint),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }

        actionResult?.let { result ->
            item {
                StudyFlowSectionCard(title = stringResource(R.string.debug_last_action_title)) {
                    Text(result.label())
                }
            }
        }
    }
}

@Composable
private fun DebugProbeEntryPanel(onOpenRuntimeSmokeWorkbench: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
    ) {
        item {
            StudyFlowSectionCard(title = stringResource(R.string.debug_probes_title)) {
                Text(
                    text = stringResource(R.string.debug_probes_entry_message),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onOpenRuntimeSmokeWorkbench) {
                    Text(stringResource(R.string.debug_open_runtime_smoke))
                }
            }
        }
    }
}

@Composable
private fun DebugLabEmptyPanel() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            StudyFlowSectionCard(title = stringResource(R.string.debug_labs_empty_title)) {
                Text(
                    text = stringResource(R.string.debug_labs_empty_message),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DebugStatusPill(health: DiagnosticRecorderHealth) {
    Surface(
        color = when (health) {
            DiagnosticRecorderHealth.HEALTHY -> MaterialTheme.colorScheme.primaryContainer
            DiagnosticRecorderHealth.DEGRADED -> MaterialTheme.colorScheme.tertiaryContainer
            DiagnosticRecorderHealth.FAILED -> MaterialTheme.colorScheme.errorContainer
            DiagnosticRecorderHealth.DISABLED -> MaterialTheme.colorScheme.surfaceVariant
        },
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Text(
            text = health.label(),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun DebugWorkbenchSection.label(): String = when (this) {
    DebugWorkbenchSection.OVERVIEW -> stringResource(R.string.debug_section_overview)
    DebugWorkbenchSection.TIMELINE -> stringResource(R.string.debug_section_timeline)
    DebugWorkbenchSection.PROBES -> stringResource(R.string.debug_section_probes)
    DebugWorkbenchSection.LABS -> stringResource(R.string.debug_section_labs)
    DebugWorkbenchSection.REPORTS -> stringResource(R.string.debug_section_reports)
}

@Composable
private fun DiagnosticRecorderHealth.label(): String = when (this) {
    DiagnosticRecorderHealth.DISABLED -> stringResource(R.string.debug_health_disabled)
    DiagnosticRecorderHealth.HEALTHY -> stringResource(R.string.debug_health_healthy)
    DiagnosticRecorderHealth.DEGRADED -> stringResource(R.string.debug_health_degraded)
    DiagnosticRecorderHealth.FAILED -> stringResource(R.string.debug_health_failed)
}

@Composable
private fun DiagnosticSeverity.label(): String = when (this) {
    DiagnosticSeverity.TRACE -> stringResource(R.string.debug_severity_trace)
    DiagnosticSeverity.INFO -> stringResource(R.string.debug_severity_info)
    DiagnosticSeverity.WARNING -> stringResource(R.string.debug_severity_warning)
    DiagnosticSeverity.ERROR -> stringResource(R.string.debug_severity_error)
}

@Composable
private fun DiagnosticArea.label(): String = when (this) {
    DiagnosticArea.APP_LIFECYCLE -> stringResource(R.string.debug_area_app_lifecycle)
    DiagnosticArea.INITIALIZATION -> stringResource(R.string.debug_area_initialization)
    DiagnosticArea.DATABASE -> stringResource(R.string.debug_area_database)
    DiagnosticArea.STUDY_PLANS -> stringResource(R.string.debug_area_study_plans)
    DiagnosticArea.SCHEDULE -> stringResource(R.string.debug_area_schedule)
    DiagnosticArea.STUDY_SESSIONS -> stringResource(R.string.debug_area_study_sessions)
    DiagnosticArea.PRACTICE -> stringResource(R.string.debug_area_practice)
    DiagnosticArea.REVIEWS -> stringResource(R.string.debug_area_reviews)
    DiagnosticArea.REMINDERS -> stringResource(R.string.debug_area_reminders)
    DiagnosticArea.NOTIFICATIONS -> stringResource(R.string.debug_area_notifications)
    DiagnosticArea.WIDGET -> stringResource(R.string.debug_area_widget)
    DiagnosticArea.BACKUP_RESTORE -> stringResource(R.string.debug_area_backup_restore)
    DiagnosticArea.ANNEX_STORAGE -> stringResource(R.string.debug_area_annex_storage)
    DiagnosticArea.SEARCH_RECOMMENDATIONS -> stringResource(R.string.debug_area_search)
    DiagnosticArea.SYNC_FOUNDATION -> stringResource(R.string.debug_area_sync)
    DiagnosticArea.PREFERENCES -> stringResource(R.string.debug_area_preferences)
    DiagnosticArea.VALIDATION -> stringResource(R.string.debug_area_validation)
}

@Composable
private fun DiagnosticOutcome.label(): String = when (this) {
    DiagnosticOutcome.STARTED -> stringResource(R.string.debug_outcome_started)
    DiagnosticOutcome.SUCCEEDED -> stringResource(R.string.debug_outcome_succeeded)
    DiagnosticOutcome.FAILED -> stringResource(R.string.debug_outcome_failed)
    DiagnosticOutcome.BLOCKED -> stringResource(R.string.debug_outcome_blocked)
    DiagnosticOutcome.CANCELLED -> stringResource(R.string.debug_outcome_cancelled)
}



@Composable
private fun DebugWorkbenchActionResult.label(): String = when (this) {
    DebugWorkbenchActionResult.REFRESHED -> stringResource(R.string.debug_action_refreshed)
    DebugWorkbenchActionResult.REFRESH_FAILED -> stringResource(R.string.debug_action_refresh_failed)
    DebugWorkbenchActionResult.STATUS_COPIED -> stringResource(R.string.debug_action_status_copied)
    DebugWorkbenchActionResult.TIMELINE_COPIED -> stringResource(R.string.debug_action_timeline_copied)
    DebugWorkbenchActionResult.COPY_FAILED -> stringResource(R.string.debug_action_copy_failed)
    DebugWorkbenchActionResult.HISTORY_CLEARED -> stringResource(R.string.debug_action_history_cleared)
    DebugWorkbenchActionResult.HISTORY_BUSY -> stringResource(R.string.debug_action_history_busy)
    DebugWorkbenchActionResult.BUNDLE_DIRECTORY_FAILED ->
        stringResource(R.string.debug_action_bundle_directory_failed)
    DebugWorkbenchActionResult.BUNDLE_UNAVAILABLE -> stringResource(R.string.debug_action_bundle_unavailable)
    DebugWorkbenchActionResult.BUNDLE_SHARE_FAILED -> stringResource(R.string.debug_action_bundle_share_failed)
    DebugWorkbenchActionResult.BUNDLE_SEAL_FAILED -> stringResource(R.string.debug_action_bundle_seal_failed)
    DebugWorkbenchActionResult.BUNDLE_READY -> stringResource(R.string.debug_action_bundle_ready)
}

@Composable
private fun SupportBundleSealStatus.label(): String = when (this) {
    SupportBundleSealStatus.NOT_RUN -> stringResource(R.string.debug_support_bundle_seal_not_run)
    SupportBundleSealStatus.PASSED -> stringResource(R.string.debug_support_bundle_seal_passed)
    SupportBundleSealStatus.FAILED -> stringResource(R.string.debug_support_bundle_seal_failed)
}

@Composable
private fun SupportBundleSealMessage.label(): String = when (this) {
    SupportBundleSealMessage.NONE -> stringResource(R.string.debug_support_bundle_message_none)
    SupportBundleSealMessage.BUNDLE_MISSING -> stringResource(R.string.debug_support_bundle_message_missing)
    SupportBundleSealMessage.ENTRY_NOT_ALLOWED -> stringResource(R.string.debug_support_bundle_message_not_allowed)
    SupportBundleSealMessage.ENTRY_TRAVERSAL -> stringResource(R.string.debug_support_bundle_message_traversal)
    SupportBundleSealMessage.MANIFEST_MISSING -> stringResource(R.string.debug_support_bundle_message_manifest)
    SupportBundleSealMessage.CHECKSUMS_MISSING -> stringResource(R.string.debug_support_bundle_message_checksums)
    SupportBundleSealMessage.REDACTION_REPORT_MISSING -> stringResource(
        R.string.debug_support_bundle_message_redaction,
    )
    SupportBundleSealMessage.CHECKSUM_INVALID -> stringResource(
        R.string.debug_support_bundle_message_checksum_invalid,
    )
    SupportBundleSealMessage.CHECKSUM_MISMATCH -> stringResource(
        R.string.debug_support_bundle_message_checksum_mismatch,
    )
    SupportBundleSealMessage.PRIVACY_SCAN_FAILED -> stringResource(
        R.string.debug_support_bundle_message_privacy_scan,
    )
    SupportBundleSealMessage.REDACTION_REPORT_INVALID -> stringResource(
        R.string.debug_support_bundle_message_redaction_invalid,
    )
}

private fun String.displayAction(): String = replace('-', ' ')
    .replace('_', ' ')
    .trim()
    .replaceFirstChar { character -> character.uppercase() }
