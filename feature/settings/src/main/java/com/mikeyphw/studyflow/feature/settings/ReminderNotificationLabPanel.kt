package com.mikeyphw.studyflow.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mikeyphw.studyflow.core.designsystem.StudyFlowSectionCard
import com.mikeyphw.studyflow.core.diagnostics.DebugLabStatus
import com.mikeyphw.studyflow.core.diagnostics.ReminderCandidateDiagnostic
import com.mikeyphw.studyflow.core.diagnostics.ReminderCandidateKind
import com.mikeyphw.studyflow.core.diagnostics.ReminderLabActionResult
import com.mikeyphw.studyflow.core.diagnostics.ReminderLabMessage
import com.mikeyphw.studyflow.core.diagnostics.ReminderNotificationLabReport
import java.text.DateFormat
import java.util.Date

@Composable
fun ReminderNotificationLabPanel(
    report: ReminderNotificationLabReport,
    actionResult: ReminderLabActionResult?,
    running: Boolean,
    onInspect: () -> Unit,
    onReconcile: () -> Unit,
    onPostDiagnosticNotification: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            StudyFlowSectionCard(title = stringResource(R.string.debug_reminder_lab_title)) {
                Text(
                    text = stringResource(R.string.debug_reminder_lab_message),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(stringResource(R.string.debug_lab_status_value, report.status.label()))
                Text(stringResource(R.string.debug_reminder_lab_planner_status, report.plannerStatus.label()))
                Text(stringResource(R.string.debug_reminder_lab_platform_status, report.platformStatus.label()))
                Text(stringResource(R.string.debug_reminder_lab_scheduling_status, report.schedulingStatus.label()))
                Text(booleanFact(R.string.debug_reminder_lab_pipeline_verified, report.productionPipelineVerified))
                if (report.message != ReminderLabMessage.NONE) {
                    Text(
                        text = report.message.label(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = stringResource(R.string.debug_reminder_lab_exact_alarm_false),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onInspect, enabled = !running) {
                        Text(stringResource(R.string.debug_reminder_lab_inspect))
                    }
                    OutlinedButton(onClick = onReconcile, enabled = !running) {
                        Text(stringResource(R.string.debug_reminder_lab_reconcile))
                    }
                }
                OutlinedButton(
                    onClick = onPostDiagnosticNotification,
                    enabled = !running,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.debug_reminder_lab_post_notification))
                }
                actionResult?.let { result ->
                    Text(
                        text = result.label(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (report.status != DebugLabStatus.NOT_RUN) {
            item {
                StudyFlowSectionCard(title = stringResource(R.string.debug_reminder_lab_platform_title)) {
                    Text(booleanFact(R.string.debug_reminder_lab_permission, report.notificationPermissionGranted))
                    Text(booleanFact(R.string.debug_reminder_lab_notifications_enabled, report.notificationsEnabled))
                    Text(booleanFact(R.string.debug_reminder_lab_channel_present, report.channelPresent))
                    Text(booleanFact(R.string.debug_reminder_lab_channel_blocked, report.channelBlocked))
                    Text(booleanFact(R.string.debug_reminder_lab_reminders_enabled, report.remindersEnabled))
                    Text(booleanFact(R.string.debug_reminder_lab_quiet_hours, report.quietHoursActive))
                    report.channelImportance?.let { importance ->
                        Text(stringResource(R.string.debug_reminder_lab_importance, importance))
                    }
                }
            }
        }
        if (report.candidates.isEmpty() && report.status != DebugLabStatus.NOT_RUN) {
            item {
                StudyFlowSectionCard(title = stringResource(R.string.debug_reminder_lab_candidates_title)) {
                    Text(
                        text = stringResource(R.string.debug_reminder_lab_no_candidates),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(report.candidates, key = ReminderCandidateDiagnostic::kind) { candidate ->
                ReminderCandidateCard(candidate)
            }
        }
    }
}

@Composable
private fun ReminderCandidateCard(candidate: ReminderCandidateDiagnostic) {
    StudyFlowSectionCard(title = candidate.kind.label()) {
        Text(
            stringResource(
                R.string.debug_reminder_lab_scheduled,
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM)
                    .format(Date(candidate.scheduledAtEpochMillis)),
            ),
        )
        Text(
            stringResource(
                R.string.debug_reminder_lab_projected_trigger,
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM)
                    .format(Date(candidate.effectiveTriggerAtEpochMillis)),
            ),
        )
        Text(stringResource(R.string.debug_reminder_lab_window, candidate.deliveryWindowMillis / 60_000L))
        Text(stringResource(R.string.debug_reminder_lab_late_grace, candidate.lateDeliveryGraceMillis / 60_000L))
        Text(booleanFact(R.string.debug_reminder_lab_quiet_delay, candidate.delayedByQuietHours))
        Text(booleanFact(R.string.debug_reminder_lab_pending_identity, candidate.pendingIntentIdentityExists))
        Text(booleanFact(R.string.debug_reminder_lab_bookkeeping, candidate.scheduledBookkeepingPresent))
    }
}

@Composable
private fun booleanFact(label: Int, value: Boolean): String = stringResource(
    R.string.debug_lab_boolean_fact,
    stringResource(label),
    stringResource(if (value) R.string.debug_yes else R.string.debug_no),
)

@Composable
private fun DebugLabStatus.label(): String = stringResource(
    when (this) {
        DebugLabStatus.NOT_RUN -> R.string.debug_probe_not_run
        DebugLabStatus.RUNNING -> R.string.debug_probe_running
        DebugLabStatus.PASSED -> R.string.debug_probe_passed
        DebugLabStatus.EMPTY -> R.string.debug_probe_empty
        DebugLabStatus.NOT_APPLICABLE -> R.string.debug_probe_not_applicable
        DebugLabStatus.WARNING -> R.string.debug_probe_warning
        DebugLabStatus.BLOCKED -> R.string.debug_probe_blocked
        DebugLabStatus.FAILED -> R.string.debug_probe_failed
        DebugLabStatus.CANCELLED -> R.string.debug_probe_cancelled
    },
)

@Composable
private fun ReminderLabMessage.label(): String = stringResource(
    when (this) {
        ReminderLabMessage.NONE -> R.string.debug_probe_message_none
        ReminderLabMessage.MISSING_DEFAULT_PLAN -> R.string.debug_probe_message_missing_plan
        ReminderLabMessage.PLAN_UNAVAILABLE -> R.string.debug_reminder_lab_plan_unavailable
        ReminderLabMessage.PLAN_NOT_ACTIVE -> R.string.debug_reminder_lab_plan_not_active
        ReminderLabMessage.REMINDERS_DISABLED -> R.string.debug_reminder_lab_reminders_disabled
        ReminderLabMessage.NOTIFICATION_PERMISSION_DENIED -> R.string.debug_probe_message_notification_permission
        ReminderLabMessage.NOTIFICATIONS_BLOCKED -> R.string.debug_probe_message_notifications_blocked
        ReminderLabMessage.CHANNEL_MISSING -> R.string.debug_probe_message_channel_missing
        ReminderLabMessage.CHANNEL_BLOCKED -> R.string.debug_probe_message_channel_blocked
        ReminderLabMessage.NO_CANDIDATES -> R.string.debug_reminder_lab_no_candidates
        ReminderLabMessage.INSPECTION_FAILED -> R.string.debug_reminder_lab_failed
    },
)

@Composable
private fun ReminderLabActionResult.label(): String = stringResource(
    when (this) {
        ReminderLabActionResult.INSPECTED -> R.string.debug_reminder_lab_action_inspected
        ReminderLabActionResult.RECONCILED -> R.string.debug_reminder_lab_action_reconciled
        ReminderLabActionResult.DIAGNOSTIC_NOTIFICATION_POSTED ->
            R.string.debug_reminder_lab_action_notification_posted
        ReminderLabActionResult.DIAGNOSTIC_NOTIFICATION_BLOCKED ->
            R.string.debug_reminder_lab_action_notification_blocked
        ReminderLabActionResult.MISSING_DEFAULT_PLAN -> R.string.debug_probe_message_missing_plan
        ReminderLabActionResult.FAILED -> R.string.debug_reminder_lab_failed
    },
)

@Composable
private fun ReminderCandidateKind.label(): String = stringResource(
    when (this) {
        ReminderCandidateKind.DUE_REVIEWS -> R.string.debug_reminder_candidate_due_reviews
        ReminderCandidateKind.PLANNED_ACTIVITY -> R.string.debug_reminder_candidate_planned_activity
        ReminderCandidateKind.MISSED_ACTIVITY -> R.string.debug_reminder_candidate_missed_activity
        ReminderCandidateKind.DAILY_SUMMARY -> R.string.debug_reminder_candidate_daily_summary
    },
)
