package com.mikeyphw.studyflow.feature.sessions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mikeyphw.studyflow.core.model.StudyActivityType

data class QuickSessionForm(
    val title: String,
    val type: StudyActivityType,
)

data class FinishSessionForm(
    val pagesCompleted: Int,
    val questionsAnswered: Int,
    val correctAnswers: Int,
    val concentrationRating: Int?,
    val difficultyRating: Int?,
    val notes: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickSessionDialog(
    onDismiss: () -> Unit,
    onStart: (QuickSessionForm) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf(StudyActivityType.THEORY) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val valid = title.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.quick_session)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.session_title)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                ) {
                    OutlinedTextField(
                        value = stringResource(type.labelKey()),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.activity_type)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        StudyActivityType.entries.forEach { candidate ->
                            DropdownMenuItem(
                                text = { Text(stringResource(candidate.labelKey())) },
                                onClick = {
                                    type = candidate
                                    expanded = false
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onStart(QuickSessionForm(title.trim(), type)) },
            ) {
                Text(stringResource(R.string.start_session))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
fun FinishSessionDialog(
    initialNotes: String,
    onDismiss: () -> Unit,
    onFinish: (FinishSessionForm) -> Unit,
) {
    var pages by rememberSaveable { mutableStateOf("0") }
    var questions by rememberSaveable { mutableStateOf("0") }
    var correct by rememberSaveable { mutableStateOf("0") }
    var concentration by rememberSaveable { mutableStateOf("") }
    var difficulty by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable(initialNotes) { mutableStateOf(initialNotes) }
    val pagesValue = pages.toIntOrNull() ?: -1
    val questionsValue = questions.toIntOrNull() ?: -1
    val correctValue = correct.toIntOrNull() ?: -1
    val concentrationValue = concentration.toIntOrNull()
    val difficultyValue = difficulty.toIntOrNull()
    val valid = pagesValue >= 0 && questionsValue >= 0 && correctValue in 0..questionsValue &&
        (concentration.isBlank() || concentrationValue in 1..5) &&
        (difficulty.isBlank() || difficultyValue in 1..5)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.finish_session)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NumberField(
                        value = pages,
                        onValueChange = { pages = it },
                        label = stringResource(R.string.pages_completed),
                        modifier = Modifier.weight(1f),
                    )
                    NumberField(
                        value = questions,
                        onValueChange = { questions = it },
                        label = stringResource(R.string.questions_answered),
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NumberField(
                        value = correct,
                        onValueChange = { correct = it },
                        label = stringResource(R.string.correct_answers),
                        modifier = Modifier.weight(1f),
                    )
                    NumberField(
                        value = concentration,
                        onValueChange = { concentration = it },
                        label = stringResource(R.string.concentration_rating),
                        modifier = Modifier.weight(1f),
                    )
                }
                NumberField(
                    value = difficulty,
                    onValueChange = { difficulty = it },
                    label = stringResource(R.string.difficulty_rating),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.session_notes)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onFinish(
                        FinishSessionForm(
                            pagesCompleted = pagesValue,
                            questionsAnswered = questionsValue,
                            correctAnswers = correctValue,
                            concentrationRating = concentrationValue,
                            difficultyRating = difficultyValue,
                            notes = notes,
                        ),
                    )
                },
            ) {
                Text(stringResource(R.string.finish_session))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { candidate ->
            if (candidate.isBlank() || candidate.all { it.isDigit() }) onValueChange(candidate)
        },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = modifier,
    )
}
