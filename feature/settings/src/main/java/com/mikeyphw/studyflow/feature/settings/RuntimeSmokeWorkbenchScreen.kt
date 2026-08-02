package com.mikeyphw.studyflow.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mikeyphw.studyflow.core.designsystem.StudyFlowSectionCard
import com.mikeyphw.studyflow.core.model.RuntimeSmokeProbe
import com.mikeyphw.studyflow.core.model.RuntimeSmokeProbeKind
import com.mikeyphw.studyflow.core.model.RuntimeSmokeProbeStatus
import com.mikeyphw.studyflow.core.model.RuntimeSmokeReport

@Composable
fun RuntimeSmokeWorkbenchScreen(
    report: RuntimeSmokeReport,
    hasDefaultPlan: Boolean,
    onRunProbe: (RuntimeSmokeProbeKind) -> Unit,
    onRunSafeProbeSet: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.runtime_smoke_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(R.string.runtime_smoke_message),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StudyFlowSectionCard(title = stringResource(R.string.runtime_smoke_summary_title)) {
                Text(
                            stringResource(
                                R.string.runtime_smoke_passed_value,
                                report.passedCount,
                                report.probes.size,
                            ),
                        )
                Text(stringResource(R.string.runtime_smoke_blocked_value, report.blockedCount))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onRunSafeProbeSet, enabled = hasDefaultPlan) {
                        Text(stringResource(R.string.runtime_smoke_run_safe_set))
                    }
                    OutlinedButton(onClick = onBack) {
                        Text(stringResource(R.string.go_back))
                    }
                }
            }
            report.probes.forEach { probe ->
                RuntimeSmokeProbeCard(
                    probe = probe,
                    enabled = hasDefaultPlan || probe.kind == RuntimeSmokeProbeKind.WIDGET_REFRESH,
                    onRunProbe = onRunProbe,
                )
            }
        }
    }
}

@Composable
private fun RuntimeSmokeProbeCard(
    probe: RuntimeSmokeProbe,
    enabled: Boolean,
    onRunProbe: (RuntimeSmokeProbeKind) -> Unit,
) {
    StudyFlowSectionCard(title = probe.title) {
        AssistChip(
            onClick = {},
            label = { Text(probe.status.label()) },
        )
        Text(
            text = probe.detail,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = { onRunProbe(probe.kind) },
            enabled = enabled && probe.safeForManualRun,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.runtime_smoke_run_probe))
        }
    }
}

private fun RuntimeSmokeProbeStatus.label(): String = when (this) {
    RuntimeSmokeProbeStatus.NOT_RUN -> "Not run"
    RuntimeSmokeProbeStatus.PASS -> "Passed"
    RuntimeSmokeProbeStatus.BLOCKED -> "Blocked"
}
