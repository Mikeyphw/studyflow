package com.mikeyphw.studyflow.feature.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mikeyphw.studyflow.core.designsystem.StudyFlowActionOrb
import com.mikeyphw.studyflow.core.designsystem.StudyFlowMetricStrip
import com.mikeyphw.studyflow.core.designsystem.StudyFlowSectionTitle
import com.mikeyphw.studyflow.core.designsystem.StudyFlowStatusPill
import com.mikeyphw.studyflow.core.designsystem.StudyFlowTabStrip
import com.mikeyphw.studyflow.core.designsystem.StudyFlowTimelineItem
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.launch

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
    val canCreateGoal = goalTitle.isNotBlank() && targetValue.toDoubleOrNull() != null
    val canCreateNote = noteTitle.isNotBlank() && noteContent.isNotBlank()

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp),
        ) {
            StudyProgressHeader(plan = plan, onBack = onBack)
            androidx.compose.foundation.layout.Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StudyFlowMetricStrip(
                    firstLabel = stringResource(R.string.goals),
                    firstValue = snapshot.activeGoals.size.toString(),
                    firstSupportingText = stringResource(R.string.goal_progress, snapshot.goalProgressPercent),
                    secondLabel = stringResource(R.string.notes),
                    secondValue = snapshot.pinnedNotes.size.toString(),
                    secondSupportingText = pluralStringResource(
                        R.plurals.progress_summary,
                        snapshot.activeGoals.size,
                        snapshot.activeGoals.size,
                        snapshot.pinnedNotes.size,
                    ),
                )
                StudyFlowStatusPill(text = stringResource(R.string.goal_progress, snapshot.goalProgressPercent))

                StudyFlowSectionTitle(title = stringResource(R.string.add_goal))
                StudyFlowInputPanel {
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
                    StudyFlowFormAction(
                        icon = Icons.Outlined.Add,
                        label = stringResource(R.string.create_goal),
                        contentDescription = stringResource(R.string.create_goal),
                        enabled = canCreateGoal,
                        disabledMessage = stringResource(R.string.goal_title),
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
                                    startsOnIso = todayIsoDate(),
                                    endsOnIso = null,
                                )
                                goalTitle = ""
                                targetValue = ""
                            }
                        },
                    )
                }

                StudyFlowSectionTitle(title = stringResource(R.string.add_note))
                StudyFlowInputPanel {
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
                    StudyFlowFormAction(
                        icon = Icons.Outlined.Add,
                        label = stringResource(R.string.create_note),
                        contentDescription = stringResource(R.string.create_note),
                        enabled = canCreateNote,
                        disabledMessage = stringResource(R.string.note_title),
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
                    )
                }

                StudyFlowSectionTitle(title = stringResource(R.string.progress_title))
                StudyFlowTabStrip(
                    tabs = ProgressFilter.entries,
                    selected = filter,
                    label = { tab -> stringResource(tab.labelRes()) },
                    onSelected = { filter = it },
                )
                when (filter) {
                    ProgressFilter.GOALS -> GoalList(
                        goals = snapshot.activeGoals + snapshot.completedGoals + snapshot.archivedGoals,
                        progress = progress,
                        plan = plan,
                    )
                    ProgressFilter.NOTES -> NoteList(snapshot.notes, progress, plan)
                }
            }
        }
    }
}

@Composable
private fun StudyProgressHeader(plan: StudyPlan, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(
                Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.go_back),
            )
        }
        androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.progress_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = plan.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StudyFlowInputPanel(content: @Composable ColumnScope.() -> Unit) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
private fun StudyFlowFormAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    contentDescription: String,
    enabled: Boolean,
    disabledMessage: String,
    onClick: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        if (enabled) {
            StudyFlowActionOrb(
                icon = icon,
                label = label,
                contentDescription = contentDescription,
                onClick = onClick,
            )
        } else {
            Text(
                text = disabledMessage,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            )
        }
    }
}

@Composable
private fun GoalList(goals: List<StudyGoal>, progress: ManageStudyProgress, plan: StudyPlan) {
    val coroutineScope = rememberCoroutineScope()
    if (goals.isEmpty()) {
        StudyFlowEmptyLine(text = stringResource(R.string.no_goals))
    } else {
        androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            goals.forEach { goal ->
                val row = goal.asRowState()
                StudyFlowRecordPanel {
                    StudyFlowTimelineItem(
                        title = row.title,
                        metadata = stringResource(R.string.progress_percent, row.progressPercent),
                        icon = Icons.Outlined.Add,
                        actionLabel = stringResource(if (goal.archived) R.string.restore else R.string.archive),
                        onAction = {
                            coroutineScope.launch {
                                if (goal.archived) {
                                    progress.restoreGoal(plan.id, goal.id)
                                } else {
                                    progress.archiveGoal(plan.id, goal.id)
                                }
                            }
                        },
                    )
                    ConfirmingDeleteButton {
                        coroutineScope.launch { progress.deleteGoal(plan.id, goal.id) }
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
        StudyFlowEmptyLine(text = stringResource(R.string.no_notes))
    } else {
        androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            notes.forEach { note ->
                StudyFlowRecordPanel {
                    StudyFlowTimelineItem(
                        title = note.title,
                        metadata = note.content,
                        icon = Icons.Outlined.PushPin,
                        actionLabel = stringResource(if (note.pinned) R.string.unpin else R.string.pin),
                        onAction = {
                            coroutineScope.launch { progress.togglePinnedNote(plan.id, note.id) }
                        },
                    )
                    ConfirmingDeleteButton {
                        coroutineScope.launch { progress.deleteNote(plan.id, note.id) }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudyFlowRecordPanel(content: @Composable ColumnScope.() -> Unit) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.82f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}

@Composable
private fun StudyFlowEmptyLine(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.72f))
            .padding(16.dp),
    ) {
        Text(text = text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun ProgressFilter.labelRes(): Int = when (this) {
    ProgressFilter.GOALS -> R.string.goals
    ProgressFilter.NOTES -> R.string.notes
}

private fun todayIsoDate(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())


@Composable
private fun ConfirmingDeleteButton(onConfirmed: () -> Unit) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    Text(
        text = stringResource(R.string.delete),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button) { confirming = true }
            .padding(horizontal = 12.dp, vertical = 12.dp),
    )
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.confirm_delete_title)) },
            text = { Text(stringResource(R.string.confirm_delete_message)) },
            confirmButton = {
                TextButton(onClick = { confirming = false; onConfirmed() }) {
                    Text(stringResource(R.string.confirm_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}
