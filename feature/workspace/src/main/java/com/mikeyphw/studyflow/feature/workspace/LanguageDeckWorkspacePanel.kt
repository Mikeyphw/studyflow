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
import com.mikeyphw.studyflow.core.designsystem.StudyFlowFocusActionSurface
import com.mikeyphw.studyflow.core.designsystem.StudyFlowMetricStrip
import com.mikeyphw.studyflow.core.model.LanguageDeckDashboardSummary

@Composable
internal fun LanguageDeckWorkspacePanel(
    summary: LanguageDeckDashboardSummary,
    onOpenReview: () -> Unit,
    onOpenPlan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StudyFlowFocusActionSurface(
            supportingLabel = stringResource(R.string.language_deck_mode_label),
            title = stringResource(R.string.language_deck_workspace_title),
            message = stringResource(R.string.language_deck_workspace_message),
            actionLabel = stringResource(R.string.language_deck_review_now),
            onAction = onOpenReview,
            secondaryLabel = stringResource(R.string.language_deck_plan),
            onSecondaryAction = onOpenPlan,
        )
        StudyFlowMetricStrip(
            firstLabel = stringResource(R.string.language_deck_expressions),
            firstValue = summary.expressionCount.toString(),
            firstSupportingText = stringResource(R.string.language_deck_idiom_count, summary.idiomCount),
            secondLabel = stringResource(R.string.language_deck_due),
            secondValue = summary.dueCount.toString(),
            secondSupportingText = stringResource(R.string.percent_value, summary.usageAccuracyPercent),
        )
        Text(
            text = stringResource(R.string.language_deck_learned_count, summary.learnedCount, summary.vocabularyCount),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
