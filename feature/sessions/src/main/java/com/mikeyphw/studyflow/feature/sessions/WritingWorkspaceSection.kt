package com.mikeyphw.studyflow.feature.sessions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.mikeyphw.studyflow.core.designsystem.StudyFlowSectionCard
import com.mikeyphw.studyflow.core.domain.WritingCompletionChecklist
import com.mikeyphw.studyflow.core.domain.WritingRubric
import com.mikeyphw.studyflow.core.domain.WritingSessionNoteCodec
import com.mikeyphw.studyflow.core.domain.WritingWorkspaceAnalytics
import com.mikeyphw.studyflow.core.model.StudyActivityType
import com.mikeyphw.studyflow.core.model.StudySession

@Composable
internal fun WritingWorkspaceSection(
    activeSession: StudySession?,
    allSessions: List<StudySession>,
    weeklyTarget: Int?,
    activeSeconds: Long?,
    controlsEnabled: Boolean,
    onSaveDraft: (prompt: String, draft: String) -> Unit,
    onFinishWriting: (
        prompt: String,
        draft: String,
        rubric: WritingRubric,
        checklist: WritingCompletionChecklist,
    ) -> Unit,
) {
    val writingSession = activeSession?.takeIf { it.type == StudyActivityType.WRITING }
    val history = WritingWorkspaceAnalytics.history(allSessions)
    val weeklyProgress = weeklyTarget?.let { target ->
        WritingWorkspaceAnalytics.weeklyProgress(allSessions, target = target)
    }
    val decoded = writingSession?.let { WritingSessionNoteCodec.decode(it.notes) }
    val initialPrompt = decoded?.prompt ?: writingSession?.notes.orEmpty()
    val stateKey = writingSession?.id?.value ?: "writing-history"
    var prompt by rememberSaveable(stateKey) { mutableStateOf(initialPrompt) }
    var draft by rememberSaveable(stateKey) { mutableStateOf(decoded?.draftText.orEmpty()) }
    var theme by rememberSaveable(stateKey) { mutableStateOf(decoded?.rubric?.themeAndTask?.toString().orEmpty()) }
    var textType by rememberSaveable(stateKey) { mutableStateOf(decoded?.rubric?.textType?.toString().orEmpty()) }
    var cohesion by rememberSaveable(stateKey) { mutableStateOf(decoded?.rubric?.cohesion?.toString().orEmpty()) }
    var argumentation by rememberSaveable(stateKey) {
        mutableStateOf(decoded?.rubric?.argumentation?.toString().orEmpty())
    }
    var language by rememberSaveable(stateKey) {
        mutableStateOf(decoded?.rubric?.standardLanguage?.toString().orEmpty())
    }
    var taskReviewed by rememberSaveable(stateKey) { mutableStateOf(decoded?.checklist?.taskReviewed == true) }
    var structureReviewed by rememberSaveable(stateKey) {
        mutableStateOf(decoded?.checklist?.structureReviewed == true)
    }
    var cohesionReviewed by rememberSaveable(stateKey) {
        mutableStateOf(decoded?.checklist?.cohesionReviewed == true)
    }
    var languageReviewed by rememberSaveable(stateKey) {
        mutableStateOf(decoded?.checklist?.languageReviewed == true)
    }

    val rubricValues = listOf(theme, textType, cohesion, argumentation, language).map { it.toIntOrNull() }
    val rubricValid = rubricValues.all { it != null && it in 0..20 }
    val score = if (rubricValid) rubricValues.filterNotNull().sum() else null
    val checklist = WritingCompletionChecklist(
        taskReviewed = taskReviewed,
        structureReviewed = structureReviewed,
        cohesionReviewed = cohesionReviewed,
        languageReviewed = languageReviewed,
    )
    val canFinish = controlsEnabled && writingSession != null && prompt.isNotBlank() && draft.isNotBlank() &&
        rubricValid && checklist.complete

    StudyFlowSectionCard(title = stringResource(R.string.writing_workspace_title)) {
        weeklyProgress?.let { progress ->
            Text(
                text = stringResource(
                    R.string.writing_weekly_goal,
                    progress.completed,
                    progress.target,
                ),
                color = if (progress.reached) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }

        if (writingSession != null) {
            Text(
                text = formatSessionDuration(activeSeconds ?: writingSession.grossDurationSeconds),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.writing_timer_supporting),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                label = { Text(stringResource(R.string.writing_prompt)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                label = { Text(stringResource(R.string.writing_editor)) },
                minLines = 10,
                modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp),
            )
            OutlinedButton(
                enabled = controlsEnabled && prompt.isNotBlank(),
                onClick = { onSaveDraft(prompt.trim(), draft) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.writing_save_draft))
            }

            HorizontalDivider()
            Text(
                text = stringResource(R.string.writing_rubric_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.writing_rubric_help),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            WritingRubricField(
                value = theme,
                onValueChange = { theme = it },
                label = stringResource(R.string.writing_rubric_theme),
            )
            WritingRubricField(
                value = textType,
                onValueChange = { textType = it },
                label = stringResource(R.string.writing_rubric_text_type),
            )
            WritingRubricField(
                value = cohesion,
                onValueChange = { cohesion = it },
                label = stringResource(R.string.writing_rubric_cohesion),
            )
            WritingRubricField(
                value = argumentation,
                onValueChange = { argumentation = it },
                label = stringResource(R.string.writing_rubric_argumentation),
            )
            WritingRubricField(
                value = language,
                onValueChange = { language = it },
                label = stringResource(R.string.writing_rubric_language),
            )
            Text(
                text = score?.let { stringResource(R.string.writing_score_value, it) }
                    ?: stringResource(R.string.writing_score_pending),
                style = MaterialTheme.typography.titleMedium,
            )

            HorizontalDivider()
            Text(
                text = stringResource(R.string.writing_checklist_title),
                style = MaterialTheme.typography.titleMedium,
            )
            WritingChecklistRow(
                checked = taskReviewed,
                onCheckedChange = { taskReviewed = it },
                label = stringResource(R.string.writing_checklist_task),
            )
            WritingChecklistRow(
                checked = structureReviewed,
                onCheckedChange = { structureReviewed = it },
                label = stringResource(R.string.writing_checklist_structure),
            )
            WritingChecklistRow(
                checked = cohesionReviewed,
                onCheckedChange = { cohesionReviewed = it },
                label = stringResource(R.string.writing_checklist_cohesion),
            )
            WritingChecklistRow(
                checked = languageReviewed,
                onCheckedChange = { languageReviewed = it },
                label = stringResource(R.string.writing_checklist_language),
            )
            Button(
                enabled = canFinish,
                onClick = {
                    onFinishWriting(
                        prompt.trim(),
                        draft,
                        WritingRubric(
                            themeAndTask = requireNotNull(rubricValues[0]),
                            textType = requireNotNull(rubricValues[1]),
                            cohesion = requireNotNull(rubricValues[2]),
                            argumentation = requireNotNull(rubricValues[3]),
                            standardLanguage = requireNotNull(rubricValues[4]),
                        ),
                        checklist,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.writing_finish))
            }
        } else {
            Text(
                text = stringResource(R.string.writing_no_active_session),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (history.isNotEmpty()) {
            HorizontalDivider()
            Text(
                text = stringResource(R.string.writing_history_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                history.take(5).forEach { entry ->
                    val record = entry.record
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = record?.prompt ?: stringResource(R.string.activity_type_writing),
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = record?.scorePercent?.let { stringResource(R.string.writing_score_value, it) }
                                ?: stringResource(R.string.writing_score_unavailable),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WritingRubricField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { candidate ->
            if (candidate.isBlank() || candidate.all(Char::isDigit)) {
                val numeric = candidate.toIntOrNull()
                if (numeric == null || numeric in 0..20) onValueChange(candidate)
            }
        },
        label = { Text(label) },
        supportingText = { Text(stringResource(R.string.writing_rubric_points)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun WritingChecklistRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(text = label, modifier = Modifier.padding(top = 12.dp))
    }
}
