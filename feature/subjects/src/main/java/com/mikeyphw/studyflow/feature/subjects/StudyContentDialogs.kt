package com.mikeyphw.studyflow.feature.subjects

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mikeyphw.studyflow.core.domain.ExamNoticeImportIssue
import com.mikeyphw.studyflow.core.domain.ExamNoticeImportIssueKind
import com.mikeyphw.studyflow.core.domain.ExamNoticeImportParser
import com.mikeyphw.studyflow.core.domain.ExamNoticeImportPreview
import com.mikeyphw.studyflow.core.domain.ExamNoticeImportSource
import com.mikeyphw.studyflow.core.domain.SyllabusImportDraft
import com.mikeyphw.studyflow.core.domain.SyllabusImportFormat
import com.mikeyphw.studyflow.core.domain.SyllabusImportParser
import com.mikeyphw.studyflow.core.domain.TopicBulkUpdate
import com.mikeyphw.studyflow.core.model.ContentProgressStatus
import com.mikeyphw.studyflow.core.model.ExamStage
import com.mikeyphw.studyflow.core.model.ExamStageId
import com.mikeyphw.studyflow.core.model.StudySyllabus
import com.mikeyphw.studyflow.core.model.Subject
import com.mikeyphw.studyflow.core.model.Topic
import com.mikeyphw.studyflow.core.model.TopicId
import kotlinx.coroutines.launch

internal data class SubjectEditorValue(
    val name: String,
    val description: String,
    val priority: Int,
    val examWeight: Double,
    val estimatedMinutes: Int,
    val examStageId: ExamStageId?,
)

internal data class TopicEditorValue(
    val name: String,
    val description: String,
    val priority: Int,
    val difficulty: Int,
    val examIncidencePercent: Int?,
    val estimatedMinutes: Int,
    val progressPercent: Int,
    val masteryPercent: Int,
    val status: ContentProgressStatus,
    val parentTopicId: TopicId?,
    val examStageId: ExamStageId?,
)

@Composable
internal fun SubjectEditorDialog(
    subject: Subject?,
    examStages: List<ExamStage>,
    onDismiss: () -> Unit,
    onSave: (SubjectEditorValue) -> Unit,
) {
    var name by rememberSaveable(subject?.id?.value) { mutableStateOf(subject?.name.orEmpty()) }
    var description by rememberSaveable(subject?.id?.value) {
        mutableStateOf(subject?.description.orEmpty())
    }
    var priority by rememberSaveable(subject?.id?.value) {
        mutableStateOf(subject?.priority?.toString() ?: "3")
    }
    var weight by rememberSaveable(subject?.id?.value) {
        mutableStateOf(subject?.examWeight?.toString() ?: "1.0")
    }
    var minutes by rememberSaveable(subject?.id?.value) {
        mutableStateOf(subject?.estimatedMinutes?.toString() ?: "0")
    }
    var stageId by rememberSaveable(subject?.id?.value) {
        mutableStateOf(subject?.examStageId?.value)
    }
    val priorityValue = priority.toIntOrNull()?.takeIf { it in 1..5 }
    val weightValue = weight.toDoubleOrNull()?.takeIf { it >= 0.0 }
    val minutesValue = minutes.toIntOrNull()?.takeIf { it >= 0 }
    val valid = name.isNotBlank() && priorityValue != null && weightValue != null && minutesValue != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (subject == null) R.string.add_subject else R.string.edit_subject))
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TextFieldLine(name, { name = it }, R.string.name)
                TextFieldLine(
                    value = description,
                    onValueChange = { description = it },
                    label = R.string.description,
                    minLines = 2,
                )
                NumberField(priority, { priority = it }, R.string.priority)
                NumberField(weight, { weight = it }, R.string.exam_weight, decimal = true)
                NumberField(minutes, { minutes = it }, R.string.estimated_minutes)
                StageChooser(examStages, stageId?.let(::ExamStageId)) { stageId = it?.value }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        SubjectEditorValue(
                            name = name.trim(),
                            description = description.trim(),
                            priority = requireNotNull(priorityValue),
                            examWeight = requireNotNull(weightValue),
                            estimatedMinutes = requireNotNull(minutesValue),
                            examStageId = stageId?.let(::ExamStageId),
                        ),
                    )
                },
                enabled = valid,
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
internal fun TopicEditorDialog(
    topic: Topic?,
    defaultParentTopicId: TopicId?,
    availableParents: List<Topic>,
    examStages: List<ExamStage>,
    onDismiss: () -> Unit,
    onSave: (TopicEditorValue) -> Unit,
) {
    val key = topic?.id?.value ?: defaultParentTopicId?.value.orEmpty()
    var name by rememberSaveable(key) { mutableStateOf(topic?.name.orEmpty()) }
    var description by rememberSaveable(key) { mutableStateOf(topic?.description.orEmpty()) }
    var priority by rememberSaveable(key) { mutableStateOf(topic?.priority?.toString() ?: "3") }
    var difficulty by rememberSaveable(key) { mutableStateOf(topic?.difficulty?.toString() ?: "3") }
    var incidence by rememberSaveable(key) {
        mutableStateOf(topic?.examIncidencePercent?.toString().orEmpty())
    }
    var minutes by rememberSaveable(key) { mutableStateOf(topic?.estimatedMinutes?.toString() ?: "0") }
    var progress by rememberSaveable(key) { mutableStateOf(topic?.progressPercent?.toString() ?: "0") }
    var mastery by rememberSaveable(key) { mutableStateOf(topic?.masteryPercent?.toString() ?: "0") }
    var statusName by rememberSaveable(key) {
        mutableStateOf((topic?.status ?: ContentProgressStatus.NOT_STARTED).name)
    }
    var parentId by rememberSaveable(key) {
        mutableStateOf(topic?.parentTopicId?.value ?: defaultParentTopicId?.value)
    }
    var stageId by rememberSaveable(key) { mutableStateOf(topic?.examStageId?.value) }
    val priorityValue = priority.toIntOrNull()?.takeIf { it in 1..5 }
    val difficultyValue = difficulty.toIntOrNull()?.takeIf { it in 1..5 }
    val incidenceValue = incidence.takeIf { it.isNotBlank() }?.toIntOrNull()?.takeIf { it in 0..100 }
    val incidenceValid = incidence.isBlank() || incidenceValue != null
    val minutesValue = minutes.toIntOrNull()?.takeIf { it >= 0 }
    val progressValue = progress.toIntOrNull()?.takeIf { it in 0..100 }
    val masteryValue = mastery.toIntOrNull()?.takeIf { it in 0..100 }
    val valid = name.isNotBlank() && priorityValue != null && difficultyValue != null &&
        incidenceValid && minutesValue != null && progressValue != null && masteryValue != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (topic == null) R.string.add_topic else R.string.edit_topic)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TextFieldLine(name, { name = it }, R.string.name)
                TextFieldLine(
                    value = description,
                    onValueChange = { description = it },
                    label = R.string.description,
                    minLines = 2,
                )
                NumberField(priority, { priority = it }, R.string.priority)
                NumberField(difficulty, { difficulty = it }, R.string.difficulty)
                NumberField(incidence, { incidence = it }, R.string.exam_incidence)
                NumberField(minutes, { minutes = it }, R.string.estimated_minutes)
                NumberField(progress, { progress = it }, R.string.progress)
                NumberField(mastery, { mastery = it }, R.string.mastery)
                StatusChooser(
                    enumValueOrDefault<ContentProgressStatus>(
                        statusName,
                        ContentProgressStatus.NOT_STARTED,
                    ),
                ) { statusName = it.name }
                ParentChooser(availableParents, parentId?.let(::TopicId)) { parentId = it?.value }
                StageChooser(examStages, stageId?.let(::ExamStageId)) { stageId = it?.value }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        TopicEditorValue(
                            name = name.trim(),
                            description = description.trim(),
                            priority = requireNotNull(priorityValue),
                            difficulty = requireNotNull(difficultyValue),
                            examIncidencePercent = incidenceValue,
                            estimatedMinutes = requireNotNull(minutesValue),
                            progressPercent = requireNotNull(progressValue),
                            masteryPercent = requireNotNull(masteryValue),
                            status = enumValueOrDefault<ContentProgressStatus>(
                                statusName,
                                ContentProgressStatus.NOT_STARTED,
                            ),
                            parentTopicId = parentId?.let(::TopicId),
                            examStageId = stageId?.let(::ExamStageId),
                        ),
                    )
                },
                enabled = valid,
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
internal fun BulkEditDialog(
    selectedCount: Int,
    examStages: List<ExamStage>,
    onDismiss: () -> Unit,
    onApply: (TopicBulkUpdate) -> Unit,
) {
    var priority by rememberSaveable { mutableStateOf<Int?>(null) }
    var difficulty by rememberSaveable { mutableStateOf<Int?>(null) }
    var statusName by rememberSaveable { mutableStateOf<String?>(null) }
    var archived by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var replaceStage by rememberSaveable { mutableStateOf(false) }
    var stageId by rememberSaveable { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.bulk_edit)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(pluralStringResource(R.plurals.selected_topic_count, selectedCount, selectedCount))
                OptionalNumberChooser(R.string.priority, priority) { priority = it }
                OptionalNumberChooser(R.string.difficulty, difficulty) { difficulty = it }
                OptionalStatusChooser(statusName?.let(ContentProgressStatus::valueOf)) {
                    statusName = it?.name
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = archived == null,
                        onClick = { archived = null },
                        label = { Text(stringResource(R.string.leave_unchanged)) },
                    )
                    FilterChip(
                        selected = archived == false,
                        onClick = { archived = false },
                        label = { Text(stringResource(R.string.restore)) },
                    )
                    FilterChip(
                        selected = archived == true,
                        onClick = { archived = true },
                        label = { Text(stringResource(R.string.archive)) },
                    )
                }
                FilterChip(
                    selected = replaceStage,
                    onClick = { replaceStage = !replaceStage },
                    label = { Text(stringResource(R.string.exam_stage)) },
                )
                if (replaceStage) {
                    StageChooser(examStages, stageId?.let(::ExamStageId)) { stageId = it?.value }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onApply(
                        TopicBulkUpdate(
                            priority = priority,
                            difficulty = difficulty,
                            status = statusName?.let(ContentProgressStatus::valueOf),
                            archived = archived,
                            examStageId = stageId?.let(::ExamStageId),
                            replaceExamStage = replaceStage,
                        ),
                    )
                },
            ) {
                Text(stringResource(R.string.apply_changes))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
internal fun ImportSyllabusDialog(
    existingSyllabus: StudySyllabus,
    isPublicExam: Boolean,
    onDismiss: () -> Unit,
    onImport: (SyllabusImportDraft) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var formatName by rememberSaveable { mutableStateOf(SyllabusImportFormat.STRUCTURED_TEXT.name) }
    var input by remember { mutableStateOf("") }
    var sourceName by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<Result<ExamNoticeImportPreview>?>(null) }
    var documentFailure by remember { mutableStateOf<ExamNoticeDocumentFailure?>(null) }
    var loadingDocument by remember { mutableStateOf(false) }
    val format = enumValueOrDefault<SyllabusImportFormat>(formatName, SyllabusImportFormat.STRUCTURED_TEXT)

    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            loadingDocument = true
            documentFailure = null
            when (val result = ExamNoticeDocumentLoader.load(context, uri)) {
                is ExamNoticeDocumentLoadResult.Success -> {
                    sourceName = result.document.displayName
                    input = result.document.text
                    formatName = result.document.format.name
                    preview = null
                }
                is ExamNoticeDocumentLoadResult.Failure -> {
                    documentFailure = result.reason
                }
            }
            loadingDocument = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (isPublicExam) R.string.import_exam_notice else R.string.import_syllabus,
                ),
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 620.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(
                        if (isPublicExam) {
                            R.string.import_exam_notice_description
                        } else {
                            R.string.import_syllabus_description
                        },
                    ),
                )
                OutlinedButton(
                    onClick = {
                        documentPicker.launch(
                            arrayOf(
                                "text/plain",
                                "text/csv",
                                "text/tab-separated-values",
                                "application/pdf",
                            ),
                        )
                    },
                ) {
                    Text(stringResource(R.string.choose_exam_notice_file))
                }
                sourceName?.let { fileName ->
                    Text(stringResource(R.string.exam_notice_loaded_file, fileName))
                }
                if (loadingDocument) {
                    Text(stringResource(R.string.exam_notice_loading_file))
                }
                documentFailure?.let { failure ->
                    Text(stringResource(failure.messageResource()))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SyllabusImportFormat.entries.forEach { candidate ->
                        FilterChip(
                            selected = candidate == format,
                            onClick = {
                                formatName = candidate.name
                                preview = null
                                documentFailure = null
                            },
                            label = { Text(stringResource(candidate.importFormatLabel())) },
                        )
                    }
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = {
                        input = it
                        sourceName = null
                        preview = null
                        documentFailure = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(format.importFormatLabel())) },
                    placeholder = { Text(stringResource(format.importHint())) },
                    minLines = 8,
                )
                OutlinedButton(
                    onClick = {
                        preview = runCatching {
                            ExamNoticeImportParser.preview(
                                planId = existingSyllabus.studyPlanId,
                                source = ExamNoticeImportSource(
                                    displayName = sourceName ?: "pasted-input",
                                    format = format,
                                    text = input,
                                ),
                                existing = existingSyllabus,
                            )
                        }
                    },
                    enabled = input.isNotBlank() && !loadingDocument,
                ) {
                    Text(stringResource(R.string.preview))
                }
                preview?.fold(
                    onSuccess = { result -> ExamNoticePreview(result) },
                    onFailure = { Text(stringResource(R.string.import_failed)) },
                )
            }
        },
        confirmButton = {
            val result = preview?.getOrNull()
            Button(
                onClick = { result?.takeIf(ExamNoticeImportPreview::canApply)?.let { onImport(it.draft) } },
                enabled = result?.canApply == true,
            ) {
                Text(stringResource(R.string.import_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun ExamNoticePreview(preview: ExamNoticeImportPreview) {
    val topics = preview.draft.subjects.sumOf { it.topics.size }
    Text(
        pluralStringResource(
            R.plurals.import_subject_count,
            preview.draft.subjects.size,
            preview.draft.subjects.size,
        ),
    )
    Text(pluralStringResource(R.plurals.import_topic_count, topics, topics))
    Text(
        stringResource(
            R.string.exam_notice_duplicate_summary,
            preview.duplicateSubjects,
            preview.duplicateTopics,
        ),
    )
    Text(stringResource(R.string.exam_notice_destructive_summary, preview.destructiveChanges))
    if (preview.destructiveChanges == 0) {
        Text(stringResource(R.string.exam_notice_additive_only))
    }

    preview.draft.subjects.forEach { subject ->
        Text(subject.name, fontWeight = FontWeight.SemiBold)
        subject.topics.forEach { topic ->
            val depth = examNoticeTopicDepth(topic.key, topic.parentKey, subject.topics)
            Text("${"  ".repeat(depth)}• ${topic.name}")
        }
    }

    if (preview.issues.isNotEmpty()) {
        Text(stringResource(R.string.exam_notice_review_issues), fontWeight = FontWeight.SemiBold)
        preview.issues.take(10).forEach { issue ->
            Text("• ${issue.localizedDescription()}")
        }
        if (preview.issues.size > 10) {
            Text(stringResource(R.string.exam_notice_more_issues, preview.issues.size - 10))
        }
    }
    Text(stringResource(R.string.exam_notice_correction_hint))
}

@Composable
private fun ExamNoticeImportIssue.localizedDescription(): String {
    val detail = when (kind) {
        ExamNoticeImportIssueKind.DUPLICATE_SUBJECT ->
            stringResource(R.string.exam_notice_issue_duplicate_subject, value)
        ExamNoticeImportIssueKind.DUPLICATE_TOPIC ->
            stringResource(R.string.exam_notice_issue_duplicate_topic, value, context.orEmpty())
        ExamNoticeImportIssueKind.UNRECOGNIZED_HEADING ->
            stringResource(R.string.exam_notice_issue_unrecognized_heading, value)
        ExamNoticeImportIssueKind.ORPHAN_TOPIC ->
            stringResource(R.string.exam_notice_issue_orphan_topic, value)
    }
    val line = lineNumber ?: return detail
    return stringResource(R.string.exam_notice_issue_with_line, line, detail)
}

private fun ExamNoticeDocumentFailure.messageResource(): Int = when (this) {
    ExamNoticeDocumentFailure.UNSUPPORTED_FORMAT -> R.string.exam_notice_file_unsupported
    ExamNoticeDocumentFailure.PDF_TEXT_REQUIRES_ANDROID_15 -> R.string.exam_notice_pdf_requires_android_15
    ExamNoticeDocumentFailure.PDF_HAS_NO_TEXT_LAYER -> R.string.exam_notice_pdf_no_text_layer
    ExamNoticeDocumentFailure.DOCUMENT_TOO_LARGE -> R.string.exam_notice_file_too_large
    ExamNoticeDocumentFailure.READ_FAILED -> R.string.exam_notice_file_read_failed
}

private fun examNoticeTopicDepth(
    key: String,
    parentKey: String?,
    topics: List<com.mikeyphw.studyflow.core.domain.SyllabusTopicDraft>,
): Int {
    var depth = 0
    var current = parentKey
    val visited = mutableSetOf(key)
    while (current != null && visited.add(current)) {
        depth++
        current = topics.firstOrNull { it.key == current }?.parentKey
    }
    return depth
}

@Composable
internal fun ConfirmDeleteDialog(
    isSubject: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (isSubject) R.string.delete_subject else R.string.delete_topic)) },
        text = {
            Text(
                stringResource(
                    if (isSubject) R.string.delete_subject_message else R.string.delete_topic_message,
                ),
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) { Text(stringResource(R.string.delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun TextFieldLine(
    value: String,
    onValueChange: (String) -> Unit,
    label: Int,
    minLines: Int = 1,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(label)) },
        minLines = minLines,
        singleLine = minLines == 1,
    )
}

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: Int,
    decimal: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(label)) },
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
        ),
        singleLine = true,
    )
}

@Composable
private fun StatusChooser(
    selected: ContentProgressStatus,
    onSelected: (ContentProgressStatus) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.status))
        ContentProgressStatus.entries.forEach { status ->
            FilterChip(
                selected = selected == status,
                onClick = { onSelected(status) },
                label = { Text(stringResource(status.label())) },
            )
        }
    }
}

@Composable
private fun OptionalStatusChooser(
    selected: ContentProgressStatus?,
    onSelected: (ContentProgressStatus?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.status))
        FilterChip(
            selected = selected == null,
            onClick = { onSelected(null) },
            label = { Text(stringResource(R.string.leave_unchanged)) },
        )
        ContentProgressStatus.entries.forEach { status ->
            FilterChip(
                selected = selected == status,
                onClick = { onSelected(status) },
                label = { Text(stringResource(status.label())) },
            )
        }
    }
}

@Composable
private fun OptionalNumberChooser(label: Int, selected: Int?, onSelected: (Int?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(label))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                selected = selected == null,
                onClick = { onSelected(null) },
                label = { Text(stringResource(R.string.leave_unchanged)) },
            )
            (1..5).forEach { value ->
                FilterChip(
                    selected = selected == value,
                    onClick = { onSelected(value) },
                    label = { Text(value.toString()) },
                )
            }
        }
    }
}

@Composable
private fun StageChooser(stages: List<ExamStage>, selected: ExamStageId?, onSelected: (ExamStageId?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.exam_stage))
        OutlinedButton(onClick = { expanded = true }) {
            Text(stages.firstOrNull { it.id == selected }?.name ?: stringResource(R.string.no_exam_stage))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.no_exam_stage)) },
                onClick = {
                    onSelected(null)
                    expanded = false
                },
            )
            stages.forEach { stage ->
                DropdownMenuItem(
                    text = { Text(stage.name) },
                    onClick = {
                        onSelected(stage.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ParentChooser(parents: List<Topic>, selected: TopicId?, onSelected: (TopicId?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.parent_topic))
        OutlinedButton(onClick = { expanded = true }) {
            Text(parents.firstOrNull { it.id == selected }?.name ?: stringResource(R.string.root_topic))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.root_topic)) },
                onClick = {
                    onSelected(null)
                    expanded = false
                },
            )
            parents.forEach { topic ->
                DropdownMenuItem(
                    text = { Text(topic.name) },
                    onClick = {
                        onSelected(topic.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun SyllabusImportFormat.importFormatLabel(): Int = when (this) {
    SyllabusImportFormat.STRUCTURED_TEXT -> R.string.structured_text
    SyllabusImportFormat.CSV -> R.string.csv
    SyllabusImportFormat.TSV -> R.string.tsv
}

private fun SyllabusImportFormat.importHint(): Int = when (this) {
    SyllabusImportFormat.STRUCTURED_TEXT -> R.string.import_input_hint
    SyllabusImportFormat.CSV -> R.string.csv_input_hint
    SyllabusImportFormat.TSV -> R.string.tsv_input_hint
}

internal fun ContentProgressStatus.label(): Int = when (this) {
    ContentProgressStatus.NOT_STARTED -> R.string.content_not_started
    ContentProgressStatus.STUDYING -> R.string.content_studying
    ContentProgressStatus.REVIEWING -> R.string.content_reviewing
    ContentProgressStatus.MASTERED -> R.string.content_mastered
    ContentProgressStatus.ARCHIVED -> R.string.content_archived
}


private inline fun <reified T : Enum<T>> enumValueOrDefault(name: String, defaultValue: T): T =
    enumValues<T>().firstOrNull { value -> value.name == name } ?: defaultValue
