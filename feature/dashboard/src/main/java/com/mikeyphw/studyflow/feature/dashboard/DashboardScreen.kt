package com.mikeyphw.studyflow.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mikeyphw.studyflow.core.designsystem.StudyFlowSectionCard
import com.mikeyphw.studyflow.core.model.StudyPlan

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    plan: StudyPlan,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text = plan.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.go_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val wide = maxWidth >= 760.dp
            val contentModifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
            if (wide) {
                Row(
                    modifier = contentModifier,
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    DashboardPrimaryColumn(plan = plan, modifier = Modifier.weight(1.25f))
                    DashboardMetricsColumn(plan = plan, modifier = Modifier.weight(0.75f))
                }
            } else {
                Column(
                    modifier = contentModifier,
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    DashboardPrimaryColumn(plan = plan)
                    DashboardMetricsColumn(plan = plan)
                }
            }
        }
    }
}

@Composable
private fun DashboardPrimaryColumn(
    plan: StudyPlan,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        StudyFlowSectionCard(title = stringResource(R.string.next_best_action)) {
            Text(
                text = stringResource(R.string.foundation_recommendation),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = stringResource(R.string.foundation_recommendation_reason),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = {}) {
                Text(text = stringResource(R.string.start_session))
            }
        }
        StudyFlowSectionCard(title = stringResource(R.string.plan_progress)) {
            Text(text = stringResource(R.string.progress_value, plan.progressPercent))
            LinearProgressIndicator(
                progress = { plan.progressPercent / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = plan.description.ifBlank { stringResource(R.string.no_description) },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        StudyFlowSectionCard(title = stringResource(R.string.foundation_scope_title)) {
            Text(
                text = stringResource(R.string.foundation_scope_message),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DashboardMetricsColumn(
    plan: StudyPlan,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        MetricCard(
            title = stringResource(R.string.weekly_study_time),
            value = stringResource(R.string.minutes_value, plan.studiedMinutesThisWeek),
        )
        MetricCard(
            title = stringResource(R.string.questions_answered),
            value = plan.questionsAnswered.toString(),
        )
        MetricCard(
            title = stringResource(R.string.average_accuracy),
            value = stringResource(R.string.progress_value, plan.averageAccuracyPercent),
        )
        MetricCard(
            title = stringResource(R.string.weekly_goal),
            value = stringResource(R.string.hours_value, plan.weeklyGoalHours),
        )
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
) {
    StudyFlowSectionCard(title = title) {
        Text(text = value, style = MaterialTheme.typography.headlineMedium)
    }
}
