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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mikeyphw.studyflow.core.model.StudyContentMode

@Composable
fun DesktopContentModeScreen(appState: DesktopAppState) {
    val state = appState.contentModeState()
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        DesktopSectionCard(title = desktopText("Content modes")) {
            Text("Current mode: ${state.mode.name.replace('_', ' ')}", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeButton("Exam", StudyContentMode.EXAM_PLAN, appState)
                ModeButton("Programming", StudyContentMode.PROGRAMMING_COURSE, appState)
                ModeButton("Language", StudyContentMode.LANGUAGE_DECK, appState)
                ModeButton("Reading", StudyContentMode.READING_STUDY, appState)
                ModeButton("General", StudyContentMode.GENERAL_SKILL, appState)
            }
            Text(
                desktopText(
                    "Mode selection is persisted with the Study Plan and changes the available study workflow.",
                ),
            )
        }

        if (state.mode == StudyContentMode.PROGRAMMING_COURSE) {
            DesktopSectionCard(title = desktopText("Programming Course")) {
                Text(desktopText("Desktop runner executes fixed argument vectors inside the StudyFlow workspace."))
                Text(
                    desktopText(
                        "Local code runs with your desktop user permissions; no shell command string is interpolated.",
                    ),
                )
                if (state.codingExercises.isEmpty()) {
                    Button(onClick = appState::ensureProgrammingSample) { Text(desktopText("Create Python sample")) }
                } else {
                    val exercise = state.codingExercises.first()
                    Text(exercise.title, fontWeight = FontWeight.Bold)
                    Text(exercise.prompt)
                    Text(exercise.starterCode)
                    OutlinedButton(onClick = appState::runProgrammingExercise) { Text(desktopText("Run locally")) }
                    state.lastProgrammingResult?.let { result ->
                        Text("Runner: ${result.commandLabel}")
                        Text("Exit: ${result.exitCode ?: "n/a"}${if (result.timedOut) " • timed out" else ""}")
                        if (result.stdout.isNotBlank()) Text("stdout: ${result.stdout.trim()}")
                        if (result.stderr.isNotBlank()) Text("stderr: ${result.stderr.trim()}")
                    }
                }
            }
        }

        if (state.mode == StudyContentMode.LANGUAGE_DECK) {
            DesktopSectionCard(title = desktopText("Language Deck")) {
                if (state.languageExpressions.isEmpty()) {
                    Button(onClick = appState::ensureLanguageSample) { Text(desktopText("Create idiom sample")) }
                } else {
                    val expression = state.languageExpressions.first()
                    Text(expression.expression, fontWeight = FontWeight.Bold)
                    Text(desktopText("What does this expression mean?"))
                    OutlinedTextField(
                        value = appState.languageReviewAnswerDraft,
                        onValueChange = appState::updateLanguageReviewAnswer,
                        label = { Text(desktopText("Answer")) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(onClick = appState::submitLanguageReview) { Text(desktopText("Check answer")) }
                    state.languageAttempts.lastOrNull()?.let { attempt ->
                        Text("${attempt.outcome.name.replace('_', ' ')} • ${attempt.scorePercent}%")
                    }
                }
            }
        }

        if (state.mode == StudyContentMode.READING_STUDY) {
            DesktopStateMessage("Reading Study", "Use Library materials and Study Sessions as the reading workflow.")
        }
        if (state.mode == StudyContentMode.GENERAL_SKILL) {
            DesktopStateMessage(
                "General Skill",
                "Use Plan topics, sessions, practice and reviews without exam semantics.",
            )
        }
        if (appState.contentModeActionMessage.isNotBlank()) Text(appState.contentModeActionMessage)
    }
}

@Composable
private fun ModeButton(label: String, mode: StudyContentMode, appState: DesktopAppState) {
    OutlinedButton(onClick = { appState.setContentMode(mode) }) { Text(label) }
}
