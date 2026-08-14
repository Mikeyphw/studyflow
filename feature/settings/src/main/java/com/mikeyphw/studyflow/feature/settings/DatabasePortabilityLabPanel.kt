package com.mikeyphw.studyflow.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.mikeyphw.studyflow.core.diagnostics.DatabaseHealthMessage
import com.mikeyphw.studyflow.core.diagnostics.DatabasePortabilityLabActionResult
import com.mikeyphw.studyflow.core.diagnostics.DatabasePortabilityLabReport
import com.mikeyphw.studyflow.core.diagnostics.DebugLabStatus
import com.mikeyphw.studyflow.core.diagnostics.PortabilityDrillDiagnostic
import com.mikeyphw.studyflow.core.diagnostics.PortabilityDrillKind
import com.mikeyphw.studyflow.core.diagnostics.PortabilityLabMessage

@Composable
fun DatabasePortabilityLabPanel(
    report: DatabasePortabilityLabReport,
    actionResult: DatabasePortabilityLabActionResult?,
    running: Boolean,
    onInspectDatabase: () -> Unit,
    onRunPortabilityDrills: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            StudyFlowSectionCard(title = stringResource(R.string.debug_database_lab_title)) {
                Text(
                    text = stringResource(R.string.debug_database_lab_message),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(stringResource(R.string.debug_lab_status_value, report.database.status.label()))
                if (report.database.message != DatabaseHealthMessage.NONE) {
                    Text(report.database.message.label(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(
                    onClick = onInspectDatabase,
                    enabled = !running,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.debug_database_lab_inspect))
                }
                Text(
                    stringResource(
                        R.string.debug_database_schema,
                        report.database.schemaVersion,
                        report.database.expectedSchemaVersion,
                    ),
                )
                Text(booleanFact(R.string.debug_database_quick_check, report.database.quickCheckPassed))
                Text(stringResource(R.string.debug_database_fk_violations, report.database.foreignKeyViolationCount))
                Text(stringResource(R.string.debug_database_logical_violations, report.database
                    .logicalIntegrityViolationCount))
                Text(stringResource(R.string.debug_database_table_count, report.database.userTableCount))
                Text(stringResource(R.string.debug_database_row_count, report.database.totalEntityRows))
                Text(booleanFact(R.string.debug_database_metadata, report.database.metadataPresent))
                Text(booleanFact(R.string.debug_database_migration_path, report.database.migrationPathComplete))
            }
        }
        item {
            StudyFlowSectionCard(title = stringResource(R.string.debug_portability_lab_title)) {
                Text(
                    text = stringResource(R.string.debug_portability_lab_message),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(stringResource(R.string.debug_lab_status_value, report.portabilityStatus.label()))
                if (report.portabilityMessage != PortabilityLabMessage.NONE) {
                    Text(report.portabilityMessage.label(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedButton(
                    onClick = onRunPortabilityDrills,
                    enabled = !running,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.debug_portability_lab_run))
                }
                Text(
                    text = stringResource(R.string.debug_portability_lab_zero_write),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                actionResult?.let { result ->
                    Text(result.label(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        items(report.drills, key = PortabilityDrillDiagnostic::kind) { drill ->
            StudyFlowSectionCard(title = drill.kind.label()) {
                Text(stringResource(R.string.debug_lab_status_value, drill.status.label()))
                Text(booleanFact(R.string.debug_portability_drill_destructive, drill.destructive))
            }
        }
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
private fun DatabaseHealthMessage.label(): String = stringResource(
    when (this) {
        DatabaseHealthMessage.NONE -> R.string.debug_probe_message_none
        DatabaseHealthMessage.DATABASE_UNAVAILABLE -> R.string.debug_database_unavailable
        DatabaseHealthMessage.SCHEMA_MISMATCH -> R.string.debug_database_schema_mismatch
        DatabaseHealthMessage.QUICK_CHECK_FAILED -> R.string.debug_database_quick_check_failed
        DatabaseHealthMessage.FOREIGN_KEY_VIOLATIONS -> R.string.debug_database_fk_failed
        DatabaseHealthMessage.LOGICAL_INTEGRITY_VIOLATIONS -> R.string.debug_database_logical_failed
        DatabaseHealthMessage.METADATA_MISSING -> R.string.debug_database_metadata_missing
        DatabaseHealthMessage.MIGRATION_PATH_INCOMPLETE -> R.string.debug_database_migration_incomplete
    },
)

@Composable
private fun PortabilityLabMessage.label(): String = stringResource(
    when (this) {
        PortabilityLabMessage.NONE -> R.string.debug_probe_message_none
        PortabilityLabMessage.MISSING_DEFAULT_PLAN -> R.string.debug_probe_message_missing_plan
        PortabilityLabMessage.DRILL_FAILED -> R.string.debug_portability_lab_failed
        PortabilityLabMessage.DRILL_BLOCKED -> R.string.debug_portability_lab_blocked
    },
)

@Composable
private fun DatabasePortabilityLabActionResult.label(): String = stringResource(
    when (this) {
        DatabasePortabilityLabActionResult.DATABASE_INSPECTED -> R.string.debug_database_action_inspected
        DatabasePortabilityLabActionResult.PORTABILITY_DRILLS_COMPLETED ->
            R.string.debug_portability_action_completed
        DatabasePortabilityLabActionResult.MISSING_DEFAULT_PLAN -> R.string.debug_probe_message_missing_plan
        DatabasePortabilityLabActionResult.FAILED -> R.string.debug_portability_lab_failed
    },
)

@Composable
private fun PortabilityDrillKind.label(): String = stringResource(
    when (this) {
        PortabilityDrillKind.ENCRYPTED_EXPORT -> R.string.debug_portability_drill_encrypted
        PortabilityDrillKind.WRONG_PASSWORD -> R.string.debug_portability_drill_wrong_password
        PortabilityDrillKind.TAMPER_DETECTION -> R.string.debug_portability_drill_tamper
        PortabilityDrillKind.METADATA_PRIVATE_ANNEX -> R.string.debug_portability_drill_annex
        PortabilityDrillKind.RESTORE_DRY_RUN -> R.string.debug_portability_drill_dry_run
        PortabilityDrillKind.RETENTION_CLEANUP -> R.string.debug_portability_drill_retention
        PortabilityDrillKind.RECOVERY_REPORT -> R.string.debug_portability_drill_recovery
        PortabilityDrillKind.REAL_RESTORE -> R.string.debug_portability_drill_real_restore
        PortabilityDrillKind.RESTORE_FAILURE_ISOLATION -> R.string.debug_portability_drill_failure_isolation
        PortabilityDrillKind.LOGICAL_INTEGRITY -> R.string.debug_portability_drill_logical_integrity
    },
)
