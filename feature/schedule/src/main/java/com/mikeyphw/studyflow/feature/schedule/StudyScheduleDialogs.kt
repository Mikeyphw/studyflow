package com.mikeyphw.studyflow.feature.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mikeyphw.studyflow.core.model.StudyActivityType

internal data class ScheduleRuleDraft(
    val dayOfWeek: Int,
    val startMinuteOfDay: Int,
    val durationMinutes: Int,
    val locked: Boolean,
    val enabled: Boolean,
)

internal data class PlannedActivityDraft(
    val title: String,
    val durationMinutes: Int,
    val type: StudyActivityType,
    val locked: Boolean,
)

@Composable
internal fun ScheduleRuleDialog(
    onDismiss: () -> Unit,
    onSave: (ScheduleRuleDraft) -> Unit,
) {
    var day by rememberSaveable { mutableIntStateOf(1) }
    var start by rememberSaveable { mutableStateOf("08:00") }
    var duration by rememberSaveable { mutableStateOf("45") }
    var locked by rememberSaveable { mutableStateOf(false) }
    var enabled by rememberSaveable { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_block)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DaySelector(day = day, onDayChanged = { day = it })
                OutlinedTextField(
                    value = start,
                    onValueChange = { start = it },
                    label = { Text(stringResource(R.string.start_time)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = duration,
                    onValueChange = { duration = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.duration_minutes)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                BooleanRow(
                    label = stringResource(R.string.locked),
                    checked = locked,
                    onCheckedChange = { locked = it },
                )
                BooleanRow(
                    label = stringResource(R.string.enabled),
                    checked = enabled,
                    onCheckedChange = { enabled = it },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        ScheduleRuleDraft(
                            dayOfWeek = day,
                            startMinuteOfDay = parseMinuteOfDay(start) ?: 8 * 60,
                            durationMinutes = duration.toIntOrNull()?.coerceIn(5, 720) ?: 45,
                            locked = locked,
                            enabled = enabled,
                        ),
                    )
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
internal fun PlannedActivityDialog(
    onDismiss: () -> Unit,
    onSave: (PlannedActivityDraft) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var duration by rememberSaveable { mutableStateOf("45") }
    var typeName by rememberSaveable { mutableStateOf(StudyActivityType.THEORY.name) }
    var locked by rememberSaveable { mutableStateOf(false) }
    val defaultActivityTitle = stringResource(R.string.default_activity_title)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_activity)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.title)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                ActivityTypeSelector(typeName = typeName, onTypeChanged = { typeName = it.name })
                OutlinedTextField(
                    value = duration,
                    onValueChange = { duration = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.duration_minutes)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                BooleanRow(
                    label = stringResource(R.string.locked),
                    checked = locked,
                    onCheckedChange = { locked = it },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        PlannedActivityDraft(
                            title = title.ifBlank { defaultActivityTitle },
                            durationMinutes = duration.toIntOrNull()?.coerceIn(5, 720) ?: 45,
                            type = enumValueOf<StudyActivityType>(typeName),
                            locked = locked,
                        ),
                    )
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun DaySelector(
    day: Int,
    onDayChanged: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.day_of_week))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (1..4).forEach { candidate ->
                FilterChip(
                    selected = day == candidate,
                    onClick = { onDayChanged(candidate) },
                    label = { Text(stringResource(dayLabelKey(candidate)).take(3)) },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (5..7).forEach { candidate ->
                FilterChip(
                    selected = day == candidate,
                    onClick = { onDayChanged(candidate) },
                    label = { Text(stringResource(dayLabelKey(candidate)).take(3)) },
                )
            }
        }
    }
}

@Composable
private fun ActivityTypeSelector(
    typeName: String,
    onTypeChanged: (StudyActivityType) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.activity_type))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                StudyActivityType.THEORY,
                StudyActivityType.QUESTIONS,
                StudyActivityType.REVIEW,
            ).forEach { candidate ->
                FilterChip(
                    selected = typeName == candidate.name,
                    onClick = { onTypeChanged(candidate) },
                    label = { Text(stringResource(candidate.labelKey())) },
                )
            }
        }
    }
}

@Composable
private fun BooleanRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun parseMinuteOfDay(value: String): Int? {
    val parts = value.split(':')
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    if (hour !in 0..23 || minute !in 0..59) return null
    return hour * 60 + minute
}


private inline fun <reified T : Enum<T>> enumValueOrDefault(name: String, defaultValue: T): T =
    enumValues<T>().firstOrNull { value -> value.name == name } ?: defaultValue
