package com.mikeyphw.studyflow.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mikeyphw.studyflow.core.designsystem.StudyFlowSectionCard
import com.mikeyphw.studyflow.core.diagnostics.DebugLabStatus
import com.mikeyphw.studyflow.core.diagnostics.StudyEngineDiagnostic
import com.mikeyphw.studyflow.core.diagnostics.StudyEngineDiagnosticKind
import com.mikeyphw.studyflow.core.diagnostics.StudyEngineLabActionResult
import com.mikeyphw.studyflow.core.diagnostics.StudyEngineLabMessage
import com.mikeyphw.studyflow.core.diagnostics.StudyEngineLabReport

@Composable
fun StudyEngineLabPanel(
    report: StudyEngineLabReport,
    actionResult: StudyEngineLabActionResult?,
    running: Boolean,
    onInspect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            StudyFlowSectionCard(title = stringResource(R.string.debug_study_engine_lab_title)) {
                Text(
                    text = stringResource(R.string.debug_study_engine_lab_message),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(stringResource(R.string.debug_lab_status_value, report.status.label()))
                if (report.message != StudyEngineLabMessage.NONE) {
                    Text(report.message.label(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(
                    onClick = onInspect,
                    enabled = !running,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.debug_study_engine_lab_inspect))
                }
                actionResult?.let { result ->
                    Text(result.label(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (report.diagnostics.isNotEmpty()) {
                    Text(
                        text = stringResource(
                            R.string.debug_study_engine_lab_summary,
                            report.passedCount,
                            report.warningCount,
                            report.blockedCount,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        items(report.diagnostics, key = StudyEngineDiagnostic::kind) { diagnostic ->
            StudyFlowSectionCard(title = diagnostic.kind.label()) {
                Text(stringResource(R.string.debug_lab_status_value, diagnostic.status.label()))
                Text(stringResource(R.string.debug_study_engine_primary_count, diagnostic.primaryCount))
                Text(stringResource(R.string.debug_study_engine_secondary_count, diagnostic.secondaryCount))
                diagnostic.percent?.let { percent ->
                    Text(stringResource(R.string.debug_study_engine_percent, percent))
                }
                diagnostic.score?.let { score ->
                    Text(stringResource(R.string.debug_study_engine_score, score))
                }
            }
        }
    }
}

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
private fun StudyEngineLabMessage.label(): String = stringResource(
    when (this) {
        StudyEngineLabMessage.NONE -> R.string.debug_probe_message_none
        StudyEngineLabMessage.MISSING_DEFAULT_PLAN -> R.string.debug_probe_message_missing_plan
        StudyEngineLabMessage.INSPECTION_FAILED -> R.string.debug_study_engine_lab_failed
    },
)

@Composable
private fun StudyEngineLabActionResult.label(): String = stringResource(
    when (this) {
        StudyEngineLabActionResult.INSPECTED -> R.string.debug_study_engine_action_inspected
        StudyEngineLabActionResult.MISSING_DEFAULT_PLAN -> R.string.debug_probe_message_missing_plan
        StudyEngineLabActionResult.FAILED -> R.string.debug_study_engine_lab_failed
    },
)

@Composable
private fun StudyEngineDiagnosticKind.label(): String = stringResource(
    when (this) {
        StudyEngineDiagnosticKind.SCHEDULE_EXPANSION -> R.string.debug_study_engine_kind_schedule
        StudyEngineDiagnosticKind.SCHEDULE_PERSISTENCE -> R.string.debug_study_engine_kind_schedule_persistence
        StudyEngineDiagnosticKind.REVIEW_QUEUE -> R.string.debug_study_engine_kind_review
        StudyEngineDiagnosticKind.SRS_SCHEDULER_CANARY -> R.string.debug_study_engine_kind_srs_canary
        StudyEngineDiagnosticKind.PRACTICE_DECK -> R.string.debug_study_engine_kind_practice
        StudyEngineDiagnosticKind.PRACTICE_CANARY -> R.string.debug_study_engine_kind_practice_canary
        StudyEngineDiagnosticKind.MOCK_EXAM -> R.string.debug_study_engine_kind_mock
        StudyEngineDiagnosticKind.MOCK_PLANNER_CANARY -> R.string.debug_study_engine_kind_mock_canary
        StudyEngineDiagnosticKind.RECOMMENDATIONS -> R.string.debug_study_engine_kind_recommendations
        StudyEngineDiagnosticKind.SEARCH_QUERY -> R.string.debug_study_engine_kind_search
        StudyEngineDiagnosticKind.PROGRESS_SNAPSHOT -> R.string.debug_study_engine_kind_progress
        StudyEngineDiagnosticKind.ANALYTICS_SNAPSHOT -> R.string.debug_study_engine_kind_analytics
        StudyEngineDiagnosticKind.SYNC_FOUNDATION -> R.string.debug_study_engine_kind_sync
    },
)
