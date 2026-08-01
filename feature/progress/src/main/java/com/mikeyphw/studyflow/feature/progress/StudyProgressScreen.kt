package com.mikeyphw.studyflow.feature.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mikeyphw.studyflow.core.designsystem.StudyFlowSectionCard
import com.mikeyphw.studyflow.core.domain.ManageStudyProgress
import com.mikeyphw.studyflow.core.domain.StudyProgressSnapshot
import com.mikeyphw.studyflow.core.model.GoalPeriod
import com.mikeyphw.studyflow.core.model.GoalType
import com.mikeyphw.studyflow.core.model.StudyGoal
import com.mikeyphw.studyflow.core.model.StudyGoalId
import com.mikeyphw.studyflow.core.model.StudyNote
import com.mikeyphw.studyflow.core.model.StudyNoteId
import com.mikeyphw.studyflow.core.model.StudyNoteType
import com.mikeyphw.studyflow.core.model.StudyPlan
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyProgressScreen(
    plan: StudyPlan,
    progress: ManageStudyProgress,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snapshot by progress.observeSnapshot(plan.id).collectAsStateWithLifecycle(
        initialValue = StudyProgressSnapshot(goals = emptyList(), notes = emptyList()),
    )
    val coroutineScope = rememberCoroutineScope()
    var filter by rememberSaveable { mutableStateOf(ProgressFilter.GOALS) }
    var goalTitle by rememberSaveable { mutableStateOf("") }
    var targetValue by rememberSaveable { mutableStateOf("") }
    var noteTitle by rememberSaveable { mutableStateOf("") }
    var noteContent by rememberSaveable { mutableStateOf("") }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.progress_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.go_back),
                        )
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
                    text = stringResource(
                        R.string.progress_summary,
                        snapshot.activeGoals.size,
                        snapshot.pinnedNotes.size,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AssistChip(
                    onClick = {},
                    label = { Text(stringResource(R.string.goal_progress, snapshot.goalProgressPercent)) },
                )
            }
            StudyFlowSectionCard(title = stringResource(R.string.add_goal)) {
                OutlinedTextField(
                    value = goalTitle,
                    onValueChange = { goalTitle = it },
                    label = { Text(stringResource(R.string.goal_title)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = targetValue,
                    onValueChange = { targetValue = it.filter { char -> char.isDigit() || char == '.' } },
                    label = { Text(stringResource(R.string.target_value)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        coroutineScope.launch {
                            progress.createGoal(
                                id = StudyGoalId(UUID.randomUUID().toString()),
                                studyPlanId = plan.id,
                                subjectId = null,
                                topicId = null,
                                type = GoalType.STUDY_TIME,
                                period = GoalPeriod.WEEKLY,
                                title = goalTitle,
                                targetValue = targetValue.toDoubleOrNull() ?: 1.0,
                                startsOnIso = LocalDate.now().toString(),
                                endsOnIso = null,
                            )
                            goalTitle = ""
                            targetValue = ""
                        }
                    },
                    enabled = goalTitle.isNotBlank() && targetValue.toDoubleOrNull() != null,
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Text(stringResource(R.string.create_goal))
                }
            }
            StudyFlowSectionCard(title = stringResource(R.string.add_note)) {
                OutlinedTextField(
                    value = noteTitle,
                    onValueChange = { noteTitle = it },
                    label = { Text(stringResource(R.string.note_title)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = noteContent,
                    onValueChange = { noteContent = it },
                    label = { Text(stringResource(R.string.note_content)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                )
                Button(
                    onClick = {
                        coroutineScope.launch {
                            progress.createNote(
                                id = StudyNoteId(UUID.randomUUID().toString()),
                                studyPlanId = plan.id,
                                subjectId = null,
                                topicId = null,
                                materialId = null,
                                type = StudyNoteType.FREEFORM,
                                title = noteTitle,
                                content = noteContent,
                                tags = emptyList(),
                            )
                            noteTitle = ""
                            noteContent = ""
                        }
                    },
                    enabled = noteTitle.isNotBlank() && noteContent.isNotBlank(),
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Text(stringResource(R.string.create_note))
                }
            }
            StudyFlowSectionCard(title = stringResource(R.string.progress_title)) {
                ProgressFilterRow(selected = filter, onSelected = { filter = it })
                when (filter) {
                    ProgressFilter.GOALS -> GoalList(snapshot.activeGoals, progress, plan)
                    ProgressFilter.NOTES -> NoteList(snapshot.notes, progress, plan)
                }
            }
        }
    }
}

@Composable
private fun ProgressFilterRow(selected: ProgressFilter, onSelected: (ProgressFilter) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ProgressFilter.entries.forEach { filter ->
            FilterChip(
                selected = selected == filter,
                onClick = { onSelected(filter) },
                label = { Text(stringResource(filter.labelRes())) },
            )
        }
    }
}

@Composable
private fun GoalList(goals: List<StudyGoal>, progress: ManageStudyProgress, plan: StudyPlan) {
    val coroutineScope = rememberCoroutineScope()
    if (goals.isEmpty()) {
        Text(stringResource(R.string.no_goals), color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        goals.forEach { goal ->
            val row = goal.asRowState()
            StudyFlowSectionCard(title = row.title) {
                Text(stringResource(R.string.progress_percent, row.progressPercent))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { coroutineScope.launch { progress.archiveGoal(plan.id, goal.id) } }) {
                        Text(stringResource(R.string.archive))
                    }
                    OutlinedButton(onClick = { coroutineScope.launch { progress.deleteGoal(plan.id, goal.id) } }) {
                        Icon(Icons.Outlined.Delete, contentDescription = null)
                        Text(stringResource(R.string.delete))
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteList(notes: List<StudyNote>, progress: ManageStudyProgress, plan: StudyPlan) {
    val coroutineScope = rememberCoroutineScope()
    if (notes.isEmpty()) {
        Text(stringResource(R.string.no_notes), color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        notes.forEach { note ->
            StudyFlowSectionCard(title = note.title) {
                Text(note.content, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { coroutineScope.launch { progress.togglePinnedNote(plan.id, note.id) } },
                    ) {
                        Icon(Icons.Outlined.PushPin, contentDescription = null)
                        Text(stringResource(R.string.pin))
                    }
                    OutlinedButton(onClick = { coroutineScope.launch { progress.deleteNote(plan.id, note.id) } }) {
                        Text(stringResource(R.string.delete))
                    }
                }
            }
        }
    }
}

private fun ProgressFilter.labelRes(): Int = when (this) {
    ProgressFilter.GOALS -> R.string.goals
    ProgressFilter.NOTES -> R.string.notes
}
