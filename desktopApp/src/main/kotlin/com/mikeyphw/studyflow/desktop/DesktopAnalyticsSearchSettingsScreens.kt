package com.mikeyphw.studyflow.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Button
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun DesktopAnalyticsScreen(state: DesktopAnalyticsScreenState) {
    if (state.status == DesktopScreenStatus.EMPTY) {
        DesktopStateMessage(
            title = "No analytics yet",
            message = "Analytics are wired, but the desktop repositories do not contain Study Plan data.",
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            DesktopMetricCard(
                label = "Study time",
                value = "${state.studiedHoursSevenDays}h",
                detail = "${state.completedSessions} completed sessions",
                modifier = Modifier.weight(1f),
            )
            DesktopMetricCard(
                label = "Adherence",
                value = "${state.scheduleAdherencePercent}%",
                detail = "Schedule completion",
                modifier = Modifier.weight(1f),
            )
            DesktopMetricCard(
                label = "Practice",
                value = "${state.questionAccuracyPercent}%",
                detail = "${state.answeredQuestions} answered questions",
                modifier = Modifier.weight(1f),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            DesktopMetricCard(
                label = "Library",
                value = "${state.libraryProgressPercent}%",
                detail = "Resource progress",
                modifier = Modifier.weight(1f),
            )
            DesktopMetricCard(
                label = "Consistency",
                value = "${state.consistencyScorePercent}%",
                detail = "Last seven days",
                modifier = Modifier.weight(1f),
            )
            DesktopMetricCard(
                label = "Reviews",
                value = state.dueReviewCount.toString(),
                detail = "${state.overdueReviewCount} overdue",
                modifier = Modifier.weight(1f),
            )
        }

        DesktopSectionCard(title = "Weak areas") {
            state.weakAreas.forEach { area ->
                Text("• ${area.label}: ${area.masteryPercent}% mastery, priority ${area.priority}")
            }
        }

        DesktopSectionCard(title = "Insights") {
            state.insights.forEach { insight ->
                Text("• ${insight.kind.name.replace('_', ' ')} — ${insight.severity.name.lowercase()}")
            }
        }
    }
}

@Composable
fun DesktopSearchScreen(
    state: DesktopSearchScreenState,
    onQueryChanged: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChanged,
            label = { Text("Search StudyFlow") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            DesktopSectionCard(title = "Results", modifier = Modifier.weight(2f)) {
                if (state.results.isEmpty()) {
                    Text("Type at least two characters to search plans, materials, activities, and questions.")
                } else {
                    DesktopScrollableList(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.52f)) {
                        items(state.results.size) { index ->
                            val result = state.results[index]
                            DesktopStateMessage(
                                title = result.title,
                                message = "${result.typeLabel} • ${result.subtitle} • score ${result.score}",
                            )
                        }
                    }
                }
            }
            DesktopSectionCard(title = "Recommendations", modifier = Modifier.weight(1f)) {
                if (state.recommendations.isEmpty()) {
                    Text("No recommendations yet.")
                } else {
                    state.recommendations.forEach { recommendation ->
                        Text(
                            text = "• ${recommendation.title}: ${recommendation.destinationLabel} " +
                                "(${recommendation.reasonLabel})",
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        DesktopSectionCard(title = "Recent searches") {
            if (state.recentSearches.isEmpty()) {
                Text("No recent searches yet.")
            } else {
                Text(state.recentSearches.joinToString(" • "))
            }
        }
    }
}

@Composable
fun DesktopSettingsScreen(
    state: DesktopSettingsScreenState,
    onCycleTheme: () -> Unit,
    onCycleLanguage: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            DesktopMetricCard(
                label = "Default plan",
                value = state.defaultPlanLabel,
                detail = "Stored in desktop preview preferences",
                modifier = Modifier.weight(1f),
            )
            DesktopMetricCard(
                label = "Theme",
                value = state.themeMode.name.replace('_', ' '),
                detail = "Dynamic color: ${state.dynamicColorEnabled}",
                modifier = Modifier.weight(1f),
            )
            DesktopMetricCard(
                label = "Language",
                value = state.language.name.replace('_', ' '),
                detail = "Desktop-safe AppPreferences",
                modifier = Modifier.weight(1f),
            )
        }

        DesktopSectionCard(title = "Desktop settings") {
            Text("Settings use core:model AppPreferences and remain in memory until the filesystem overlay.")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onCycleTheme) { Text("Cycle theme") }
                Button(onClick = onCycleLanguage) { Text("Cycle language") }
            }
        }

        DesktopSectionCard(title = "Allowed boundary modules") {
            state.boundaryModules.forEach { module -> Text("• $module") }
        }

        DesktopSectionCard(title = "Deferred desktop integrations") {
            Text("These are intentionally not implemented until native desktop adapters are introduced:")
            state.deferredDesktopIntegrations.forEach { item ->
                Text("• $item", fontWeight = FontWeight.Bold)
            }
        }
    }
}
