package com.mikeyphw.studyflow.feature.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mikeyphw.studyflow.core.designsystem.StudyFlowCodeBlock
import com.mikeyphw.studyflow.core.designsystem.StudyFlowFocusActionSurface
import com.mikeyphw.studyflow.core.designsystem.StudyFlowMetricStrip
import com.mikeyphw.studyflow.core.model.ProgrammingCourseDashboardSummary
import com.mikeyphw.studyflow.core.model.ProgrammingLanguage

@Composable
internal fun ProgrammingCourseWorkspacePanel(
    summary: ProgrammingCourseDashboardSummary,
    onOpenPractice: () -> Unit,
    onOpenPlan: () -> Unit,
    nextTermuxExerciseTitle: String?,
    onRunTermuxExercise: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StudyFlowFocusActionSurface(
            supportingLabel = stringResource(R.string.programming_mode_label),
            title = stringResource(R.string.programming_workspace_title),
            message = stringResource(R.string.programming_workspace_message),
            actionLabel = stringResource(R.string.open_programming_practice),
            onAction = onOpenPractice,
            secondaryLabel = stringResource(R.string.open_programming_plan),
            onSecondaryAction = onOpenPlan,
        )
        StudyFlowMetricStrip(
            firstLabel = stringResource(R.string.programming_modules),
            firstValue = summary.moduleCount.toString(),
            firstSupportingText = stringResource(R.string.programming_lessons),
            secondLabel = stringResource(R.string.programming_exercises),
            secondValue = summary.exerciseCount.toString(),
            secondSupportingText = stringResource(R.string.programming_projects_count, summary.projectCount),
        )
        StudyFlowCodeBlock(
            code = ProgrammingCoursePreviewCode,
            language = ProgrammingLanguage.KOTLIN,
            title = stringResource(R.string.programming_code_preview_title),
            showLineNumbers = false,
            copyEnabled = false,
        )
        Text(
            text = if (summary.termuxRuntimeReady) {
                stringResource(R.string.programming_termux_ready)
            } else {
                stringResource(R.string.programming_termux_waiting)
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        StudyFlowFocusActionSurface(
            supportingLabel = stringResource(R.string.programming_termux_execution_title),
            title = nextTermuxExerciseTitle ?: stringResource(R.string.programming_termux_execution_title),
            message = stringResource(R.string.programming_termux_execution_message),
            actionLabel = if (onRunTermuxExercise != null) {
                stringResource(R.string.programming_termux_run_next)
            } else {
                stringResource(R.string.open_programming_practice)
            },
            onAction = onRunTermuxExercise ?: onOpenPractice,
            secondaryLabel = stringResource(R.string.programming_termux_result_summary),
            onSecondaryAction = onOpenPlan,
        )
    }
}

private const val ProgrammingCoursePreviewCode = """fun nextStep() {
    println("Practice one concept, run tests, then review mistakes.")
}"""
