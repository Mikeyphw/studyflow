package com.mikeyphw.studyflow.feature.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mikeyphw.studyflow.core.designsystem.StudyFlowSectionCard
import com.mikeyphw.studyflow.core.designsystem.StudyFlowStatusPill
import com.mikeyphw.studyflow.core.domain.ManageStudyAnalytics
import com.mikeyphw.studyflow.core.domain.StudyAnalyticsSnapshot
import com.mikeyphw.studyflow.core.model.StudyPlan

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyAnalyticsScreen(
    plan: StudyPlan,
    analytics: ManageStudyAnalytics,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snapshot by analytics.observeSnapshot(plan.id).collectAsStateWithLifecycle(
        initialValue = StudyAnalyticsSnapshot(
            studyPlanId = plan.id,
            studiedMinutesLastSevenDays = 0,
            completedSessionsLastSevenDays = 0,
            scheduleAdherencePercent = 0,
            questionAccuracyPercent = 0,
            answeredQuestions = 0,
            dueReviewCount = 0,
            overdueReviewCount = 0,
            retentionEaseAverage = 0.0,
            goalCompletionPercent = 0,
            contentProgressPercent = 0,
            libraryProgressPercent = 0,
            consistencyScorePercent = 0,
            weakAreas = emptyList(),
            insights = emptyList(),
        ),
    )

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.analytics_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.go_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StudyFlowSectionCard(title = plan.name) {
                Text(
                    text = pluralStringResource(
                        R.plurals.analytics_summary,
                        snapshot.studiedMinutesLastSevenDays,
                        snapshot.studiedMinutesLastSevenDays,
                        snapshot.completedSessionsLastSevenDays,
                        snapshot.consistencyScorePercent,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StudyFlowSectionCard(title = stringResource(R.string.insights)) {
                snapshot.insights.forEach { insight ->
                    Text(text = insight.title, style = MaterialTheme.typography.titleMedium)
                    Text(text = insight.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            StudyFlowSectionCard(title = stringResource(R.string.analytics_title)) {
                snapshot.metricRows().forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StudyFlowStatusPill(text = stringResource(row.titleRes))
                        Text(text = row.value, style = MaterialTheme.typography.titleMedium)
                    }
                }
                Text(
                    text = pluralStringResource(
                        R.plurals.due_reviews,
                        snapshot.dueReviewCount,
                        snapshot.dueReviewCount,
                        snapshot.overdueReviewCount,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = pluralStringResource(
                        R.plurals.answered_questions,
                        snapshot.answeredQuestions,
                        snapshot.answeredQuestions,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.ease_value, snapshot.retentionEaseAverage),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StudyFlowSectionCard(title = stringResource(R.string.weak_areas)) {
                if (snapshot.weakAreas.isEmpty()) {
                    Text(stringResource(R.string.no_weak_areas), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    snapshot.weakAreas.forEach { area ->
                        Text(text = area.label, style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = stringResource(R.string.percent_value, area.masteryPercent),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
