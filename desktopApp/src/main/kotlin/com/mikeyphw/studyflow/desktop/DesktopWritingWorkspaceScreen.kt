package com.mikeyphw.studyflow.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Button
import androidx.compose.material.Checkbox
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun DesktopWritingWorkspaceScreen(appState: DesktopAppState) {
    val state = appState.writingWorkspaceState()
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            DesktopMetricCard(
                label = "This week",
                value = "${state.weeklyProgress.completed}/${state.weeklyProgress.target}",
                detail = if (state.weeklyProgress.reached) "Writing target reached" else "Completed Writing Sessions",
                modifier = Modifier.weight(1f),
            )
            DesktopMetricCard(
                label = "History",
                value = state.history.size.toString(),
                detail = "Completed Writing Workspace sessions",
                modifier = Modifier.weight(1f),
            )
        }

        DesktopSectionCard(title = "Writing Workspace") {
            OutlinedTextField(
                value = appState.writingPromptDraft,
                onValueChange = appState::updateWritingPrompt,
                label = { Text("Prompt") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = appState.writingTextDraft,
                onValueChange = appState::updateWritingDraft,
                label = { Text("Draft") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 8,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = appState::startWritingSession, enabled = state.canStart) { Text("Start writing") }
                OutlinedButton(onClick = appState::saveWritingDraft, enabled = state.canSave) { Text("Save draft") }
            }
            if (state.activeSession != null) {
                Text("Active writing session", fontWeight = FontWeight.Bold)
                Text("Drafts are persisted inside the canonical Study Session record.")
            }
            if (appState.writingActionMessage.isNotBlank()) Text(appState.writingActionMessage)
        }

        DesktopSectionCard(title = "0–100 writing rubric") {
            DesktopWritingRubricField("Theme / task", appState.writingThemeScore, appState::updateWritingThemeScore)
            DesktopWritingRubricField("Text type", appState.writingTextTypeScore, appState::updateWritingTextTypeScore)
            DesktopWritingRubricField("Cohesion", appState.writingCohesionScore, appState::updateWritingCohesionScore)
            DesktopWritingRubricField(
                "Argumentation",
                appState.writingArgumentationScore,
                appState::updateWritingArgumentationScore,
            )
            DesktopWritingRubricField(
                "Standard language",
                appState.writingLanguageScore,
                appState::updateWritingLanguageScore,
            )
            Text("Total: ${appState.writingRubricTotal()}/100", fontWeight = FontWeight.Bold)
        }

        DesktopSectionCard(title = "Completion checklist") {
            DesktopWritingChecklistRow(
                "Task reviewed",
                appState.writingTaskReviewed,
                appState::toggleWritingTaskReviewed,
            )
            DesktopWritingChecklistRow(
                "Structure reviewed",
                appState.writingStructureReviewed,
                appState::toggleWritingStructureReviewed,
            )
            DesktopWritingChecklistRow(
                "Cohesion reviewed",
                appState.writingCohesionReviewed,
                appState::toggleWritingCohesionReviewed,
            )
            DesktopWritingChecklistRow(
                "Language reviewed",
                appState.writingLanguageReviewed,
                appState::toggleWritingLanguageReviewed,
            )
            Button(onClick = appState::completeWritingSession, enabled = state.canComplete) {
                Text("Complete writing")
            }
            Text("Completion is rejected until the draft, rubric and all four checks are present.")
        }

        DesktopSectionCard(title = "Writing history") {
            if (state.history.isEmpty()) Text("No completed Writing Sessions yet.")
            state.history.take(10).forEach { entry ->
                val score = entry.record?.scorePercent?.let { "$it/100" } ?: "No rubric"
                Text("• ${entry.record?.prompt ?: entry.session.notes} — $score")
            }
        }
    }
}

@Composable
private fun DesktopWritingRubricField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("$label (0–20)") },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun DesktopWritingChecklistRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Text(label)
    }
}
