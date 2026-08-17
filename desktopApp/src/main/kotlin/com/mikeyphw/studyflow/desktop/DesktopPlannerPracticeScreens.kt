package com.mikeyphw.studyflow.desktop

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun DesktopPlannerScreen(state: DesktopPlannerScreenState) {
    if (state.status == DesktopScreenStatus.EMPTY) {
        DesktopStateMessage(
            title = "No planner data yet",
            message = "The desktop schedule/session boundary is ready, but no activities are available.",
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            DesktopMetricCard(
                label = "Next activity",
                value = state.nextActivityTitle,
                detail = state.planName,
                modifier = Modifier.weight(1f),
            )
            DesktopMetricCard(
                label = "Planned time",
                value = "${state.totalPlannedMinutes} min",
                detail = "Across ${state.plannedActivities.size} activities",
                modifier = Modifier.weight(1f),
            )
            DesktopMetricCard(
                label = "Missed",
                value = state.missedCount.toString(),
                detail = "${state.completedCount} completed",
                modifier = Modifier.weight(1f),
            )
        }

        state.activeSession?.let { session ->
            DesktopSectionCard(title = "Active session") {
                Text(session.title, fontWeight = FontWeight.Bold)
                Text("${session.statusLabel} • ${session.durationLabel} • ${session.scoreLabel}")
            }
        }

        DesktopSectionCard(title = "Planner") {
            DesktopScrollableList(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.55f)) {
                items(state.plannedActivities) { activity ->
                    DesktopActivityRow(activity)
                }
            }
        }

        DesktopSectionCard(title = "Recent sessions") {
            if (state.recentSessions.isEmpty()) {
                Text("No sessions have been recorded yet.")
            } else {
                state.recentSessions.forEach { session ->
                    Text("• ${session.title}: ${session.statusLabel}, ${session.durationLabel}, ${session.scoreLabel}")
                }
            }
        }
    }
}

@Composable
fun DesktopPracticeScreen(state: DesktopPracticeScreenState) {
    if (state.status == DesktopScreenStatus.EMPTY) {
        DesktopStateMessage(
            title = "No practice data yet",
            message = "The practice boundary is ready, but no desktop-safe questions are available.",
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            DesktopMetricCard(
                label = "Questions",
                value = state.summary.questionCount.toString(),
                detail = "${state.summary.favoriteCount} favorite, ${state.summary.disputedCount} disputed",
                modifier = Modifier.weight(1f),
            )
            DesktopMetricCard(
                label = "Attempts",
                value = (state.summary.testCount + state.summary.mockExamCount).toString(),
                detail = "${state.summary.answeredCount} answered",
                modifier = Modifier.weight(1f),
            )
            DesktopMetricCard(
                label = "Accuracy",
                value = "${state.summary.accuracyPercent}%",
                detail = "${state.summary.correctCount} correct answers",
                modifier = Modifier.weight(1f),
            )
        }

        state.currentQuestion?.let { question ->
            DesktopSectionCard(title = "Current practice card") {
                Text(question.statement, fontWeight = FontWeight.Bold)
                Text("Answer: ${question.answer}")
                Text(question.explanation, maxLines = 3, overflow = TextOverflow.Ellipsis)
                DesktopTagRow(question.tags + question.difficultyLabel)
            }
        }

        DesktopSectionCard(title = "Generated deck") {
            DesktopScrollableList(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.42f)) {
                items(state.deck) { question ->
                    DesktopQuestionCard(question)
                }
            }
        }

        DesktopSectionCard(title = "Practice attempts") {
            state.tests.forEach { attempt ->
                Text("• ${attempt.title}: ${attempt.modeLabel}, ${attempt.progressLabel}, ${attempt.scoreLabel}")
            }
        }
    }
}

@Composable
private fun DesktopActivityRow(activity: DesktopPlannedActivityRow) {
    DesktopStateMessage(
        title = activity.title,
        message = "${activity.typeLabel} • ${activity.timeLabel} • ${activity.durationLabel} • ${activity.statusLabel}",
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun DesktopQuestionCard(question: DesktopQuestionRow) {
    DesktopStateMessage(
        title = question.statement,
        message = "${question.difficultyLabel} • answer: ${question.answer}",
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
fun DesktopScrollableList(
    modifier: Modifier = Modifier,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    Box(modifier = modifier) {
        val listState = rememberLazyListState()
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = content,
        )
        VerticalScrollbar(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            adapter = rememberScrollbarAdapter(scrollState = listState),
        )
    }
}
