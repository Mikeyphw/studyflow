package com.mikeyphw.studyflow.feature.schedule

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mikeyphw.studyflow.core.model.StudyDomainInvariants
import com.mikeyphw.studyflow.core.model.StudyActivityType
import com.mikeyphw.studyflow.core.model.Subject
import com.mikeyphw.studyflow.core.model.SubjectId
import com.mikeyphw.studyflow.core.model.Topic
import com.mikeyphw.studyflow.core.model.TopicId
import java.time.LocalDate
import java.time.ZoneId

internal data class ScheduleRuleDraft(
    val subjectId: SubjectId?,
    val dayOfWeek: Int,
    val startMinuteOfDay: Int,
    val durationMinutes: Int,
    val locked: Boolean,
    val enabled: Boolean,
)

internal data class PlannedActivityDraft(
    val subjectId: SubjectId?,
    val topicId: TopicId?,
    val scheduledStartEpochMillis: Long,
    val title: String,
    val durationMinutes: Int,
    val type: StudyActivityType,
    val locked: Boolean,
)

@Composable
internal fun ScheduleRuleDialog(
    subjects: List<Subject>,
    onDismiss: () -> Unit,
    onSave: (ScheduleRuleDraft) -> Unit,
) {
    var selectedSubjectId by rememberSaveable { mutableStateOf("") }
    var day by rememberSaveable { mutableIntStateOf(1) }
    var start by rememberSaveable { mutableStateOf("08:00") }
    var duration by rememberSaveable { mutableStateOf("45") }
    var locked by rememberSaveable { mutableStateOf(false) }
    var enabled by rememberSaveable { mutableStateOf(true) }
    val activeSubjects = subjects.filterNot(Subject::archived).sortedBy(Subject::position)
    val parsedStart = parseMinuteOfDay(start)
    val parsedDuration = duration.toIntOrNull()?.takeIf { it in StudyDomainInvariants.sessionMinutes }
    val inputValid = parsedStart != null && parsedDuration != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_block)) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SubjectSelector(
                    subjects = activeSubjects,
                    selectedSubjectId = selectedSubjectId,
                    anyLabel = stringResource(R.string.any_subject),
                    onSubjectChanged = { selectedSubjectId = it?.value.orEmpty() },
                )
                DaySelector(day = day, onDayChanged = { day = it })
                OutlinedTextField(
                    value = start,
                    onValueChange = { start = it },
                    label = { Text(stringResource(R.string.start_time)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = start.isNotBlank() && parsedStart == null,
                    supportingText = if (start.isNotBlank() && parsedStart == null) {
                        { Text(stringResource(R.string.invalid_time)) }
                    } else null,
                )
                OutlinedTextField(
                    value = duration,
                    onValueChange = { duration = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.duration_minutes)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = duration.isNotBlank() && parsedDuration == null,
                    supportingText = if (duration.isNotBlank() && parsedDuration == null) {
                        { Text(stringResource(R.string.invalid_duration)) }
                    } else null,
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
                enabled = inputValid,
                onClick = {
                    onSave(
                        ScheduleRuleDraft(
                            subjectId = selectedSubjectId.takeIf(String::isNotEmpty)?.let(::SubjectId),
                            dayOfWeek = day,
                            startMinuteOfDay = requireNotNull(parsedStart),
                            durationMinutes = requireNotNull(parsedDuration),
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
    subjects: List<Subject>,
    topics: List<Topic>,
    zoneId: ZoneId,
    onDismiss: () -> Unit,
    onSave: (PlannedActivityDraft) -> Unit,
) {
    var selectedSubjectId by rememberSaveable { mutableStateOf("") }
    var selectedTopicId by rememberSaveable { mutableStateOf("") }
    var title by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable(zoneId.id) { mutableStateOf(LocalDate.now(zoneId).toString()) }
    var start by rememberSaveable { mutableStateOf("08:00") }
    var duration by rememberSaveable { mutableStateOf("45") }
    var typeName by rememberSaveable { mutableStateOf(StudyActivityType.THEORY.name) }
    var locked by rememberSaveable { mutableStateOf(false) }
    val defaultActivityTitle = stringResource(R.string.default_activity_title)
    val activeSubjects = subjects.filterNot(Subject::archived).sortedBy(Subject::position)
    val selectedSubject = selectedSubjectId.takeIf(String::isNotEmpty)?.let(::SubjectId)
    val availableTopics = topics.filter { topic ->
        !topic.archived && selectedSubject != null && topic.subjectId == selectedSubject
    }.sortedBy(Topic::position)
    val parsedActivityDuration = duration.toIntOrNull()?.takeIf { it in StudyDomainInvariants.sessionMinutes }
    val parsedDate = parseScheduleDate(date)
    val parsedTime = parseScheduleTime(start)
    val scheduledStart = scheduleStartEpochMillis(date, start, zoneId)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_activity)) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.title)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                SubjectSelector(
                    subjects = activeSubjects,
                    selectedSubjectId = selectedSubjectId,
                    anyLabel = stringResource(R.string.any_subject),
                    onSubjectChanged = { subjectId ->
                        selectedSubjectId = subjectId?.value.orEmpty()
                        selectedTopicId = ""
                    },
                )
                TopicSelector(
                    topics = availableTopics,
                    selectedTopicId = selectedTopicId,
                    enabled = selectedSubject != null,
                    onTopicChanged = { selectedTopicId = it?.value.orEmpty() },
                )
                ActivityTypeSelector(typeName = typeName, onTypeChanged = { typeName = it.name })
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text(stringResource(R.string.activity_date)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = date.isNotBlank() && parsedDate == null,
                    supportingText = if (date.isNotBlank() && parsedDate == null) {
                        { Text(stringResource(R.string.invalid_date)) }
                    } else null,
                )
                OutlinedTextField(
                    value = start,
                    onValueChange = { start = it },
                    label = { Text(stringResource(R.string.activity_time)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = start.isNotBlank() && parsedTime == null,
                    supportingText = if (start.isNotBlank() && parsedTime == null) {
                        { Text(stringResource(R.string.invalid_time)) }
                    } else null,
                )
                OutlinedTextField(
                    value = duration,
                    onValueChange = { duration = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.duration_minutes)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = duration.isNotBlank() && parsedActivityDuration == null,
                    supportingText = if (duration.isNotBlank() && parsedActivityDuration == null) {
                        { Text(stringResource(R.string.invalid_duration)) }
                    } else null,
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
                enabled = parsedActivityDuration != null && scheduledStart != null,
                onClick = {
                    onSave(
                        PlannedActivityDraft(
                            subjectId = selectedSubject,
                            topicId = selectedTopicId.takeIf(String::isNotEmpty)?.let(::TopicId),
                            scheduledStartEpochMillis = requireNotNull(scheduledStart),
                            title = title.ifBlank { defaultActivityTitle },
                            durationMinutes = requireNotNull(parsedActivityDuration),
                            type = enumValueOrDefault(typeName, StudyActivityType.THEORY),
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
private fun SubjectSelector(
    subjects: List<Subject>,
    selectedSubjectId: String,
    anyLabel: String,
    onSubjectChanged: (SubjectId?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.filter_subject))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FilterChip(
                selected = selectedSubjectId.isEmpty(),
                onClick = { onSubjectChanged(null) },
                label = { Text(anyLabel) },
            )
            subjects.forEach { subject ->
                FilterChip(
                    selected = selectedSubjectId == subject.id.value,
                    onClick = { onSubjectChanged(subject.id) },
                    label = { Text(subject.name) },
                )
            }
        }
    }
}

@Composable
private fun TopicSelector(
    topics: List<Topic>,
    selectedTopicId: String,
    enabled: Boolean,
    onTopicChanged: (TopicId?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.filter_topic))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FilterChip(
                selected = selectedTopicId.isEmpty(),
                enabled = enabled,
                onClick = { onTopicChanged(null) },
                label = { Text(stringResource(R.string.no_topic)) },
            )
            topics.forEach { topic ->
                FilterChip(
                    selected = selectedTopicId == topic.id.value,
                    enabled = enabled,
                    onClick = { onTopicChanged(topic.id) },
                    label = { Text(topic.name) },
                )
            }
        }
    }
}

@Composable
private fun DaySelector(day: Int, onDayChanged: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.day_of_week))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            (1..4).forEach { candidate ->
                FilterChip(
                    selected = day == candidate,
                    onClick = { onDayChanged(candidate) },
                    label = { Text(stringResource(dayLabelKey(candidate)).take(3)) },
                )
            }
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
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
private fun ActivityTypeSelector(typeName: String, onTypeChanged: (StudyActivityType) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.activity_type))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            StudyActivityType.entries.forEach { candidate ->
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
private fun BooleanRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label)
        Checkbox(checked = checked, onCheckedChange = null)
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

