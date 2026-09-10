package com.mikeyphw.studyflow.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Button
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DesktopReviewScreen(appState: DesktopAppState) {
    val state = appState.reviewWorkspaceState()
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            DesktopMetricCard("Due", state.due.size.toString(), "${state.upcoming.size} upcoming", Modifier.weight(1f))
            DesktopMetricCard(
                "Flashcards",
                state.flashcards.size.toString(),
                "Persistent SRS cards",
                Modifier.weight(1f),
            )
            DesktopMetricCard(
                "Error Notebook",
                state.errors.size.toString(),
                "Wrong-answer evidence",
                Modifier.weight(1f),
            )
        }

        DesktopSectionCard(title = "Flashcards and SRS") {
            OutlinedTextField(
                value = appState.flashcardFrontDraft,
                onValueChange = appState::updateFlashcardFront,
                label = { Text("Front") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = appState.flashcardBackDraft,
                onValueChange = appState::updateFlashcardBack,
                label = { Text("Back") },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = appState::createFlashcard) { Text("Create flashcard") }
                OutlinedButton(onClick = appState::gradeFirstReview, enabled = state.due.isNotEmpty()) {
                    Text("Grade due 4/5")
                }
                OutlinedButton(onClick = appState::skipFirstReview, enabled = state.due.isNotEmpty()) {
                    Text("Skip due")
                }
                OutlinedButton(onClick = appState::suspendFirstFlashcard, enabled = state.flashcards.isNotEmpty()) {
                    Text("Suspend card")
                }
            }
            state.due.firstOrNull()?.let { Text("Due now: ${it.method.name} • repetition ${it.repetitionCount}") }
        }

        DesktopSectionCard(title = "Error Notebook") {
            if (state.errors.isEmpty()) {
                Text("Wrong answers from Practice Tests and Mock Exams appear here automatically.")
            }
            state.errors.forEach { error ->
                Text("• ${error.category.name} • recurrence ${error.recurrenceCount} • ${error.explanation}")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = appState::reclassifyNotebookError, enabled = state.errors.isNotEmpty()) {
                    Text("Reclassify first")
                }
                OutlinedButton(onClick = appState::resolveNotebookError, enabled = state.errors.isNotEmpty()) {
                    Text("Resolve first")
                }
            }
        }
        if (appState.reviewActionMessage.isNotBlank()) Text(appState.reviewActionMessage)
    }
}
