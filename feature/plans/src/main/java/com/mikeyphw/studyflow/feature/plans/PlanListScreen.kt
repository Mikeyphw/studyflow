package com.mikeyphw.studyflow.feature.plans

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mikeyphw.studyflow.core.designsystem.StudyFlowEmptyState
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudyPlanStatus

@Composable
fun PlanListScreen(
    plans: List<StudyPlan>,
    onPlanSelected: (StudyPlan) -> Unit,
    onCreatePlan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            if (plans.isNotEmpty()) {
                FloatingActionButton(onClick = onCreatePlan) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = stringResource(R.string.create_plan),
                    )
                }
            }
        },
    ) { padding ->
        if (plans.isEmpty()) {
            StudyFlowEmptyState(
                title = stringResource(R.string.empty_plans_title),
                message = stringResource(R.string.empty_plans_message),
                actionLabel = stringResource(R.string.create_plan),
                onAction = onCreatePlan,
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 20.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = stringResource(R.string.plans_title),
                            style = MaterialTheme.typography.headlineLarge,
                        )
                        Text(
                            text = stringResource(R.string.plans_subtitle),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(plans, key = { it.id.value }) { plan ->
                    StudyPlanCard(plan = plan, onClick = { onPlanSelected(plan) })
                }
            }
        }
    }
}

@Composable
private fun StudyPlanCard(
    plan: StudyPlan,
    onClick: () -> Unit,
) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = plan.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(statusLabel(plan.status)),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = stringResource(R.string.progress_percent, plan.progressPercent),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
            LinearProgressIndicator(
                progress = { plan.progressPercent / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.weekly_goal_hours, plan.weeklyGoalHours),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = plan.examDateIso ?: stringResource(R.string.no_exam_date),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = onClick, modifier = Modifier.align(Alignment.End)) {
                Text(text = stringResource(R.string.open_plan))
            }
        }
    }
}

@StringRes
private fun statusLabel(status: StudyPlanStatus): Int = when (status) {
    StudyPlanStatus.DRAFT -> R.string.status_draft
    StudyPlanStatus.ACTIVE -> R.string.status_active
    StudyPlanStatus.PAUSED -> R.string.status_paused
    StudyPlanStatus.COMPLETED -> R.string.status_completed
    StudyPlanStatus.ARCHIVED -> R.string.status_archived
}
