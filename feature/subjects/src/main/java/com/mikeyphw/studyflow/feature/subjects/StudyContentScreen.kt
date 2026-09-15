package com.mikeyphw.studyflow.feature.subjects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mikeyphw.studyflow.core.designsystem.StudyFlowEmptyState
import com.mikeyphw.studyflow.core.designsystem.StudyFlowStatusPill
import com.mikeyphw.studyflow.core.domain.ApplySpecializedContentImport
import com.mikeyphw.studyflow.core.domain.ExamNoticeDiffKind
import com.mikeyphw.studyflow.core.domain.ExamNoticeImportPreview
import com.mikeyphw.studyflow.core.domain.ExamNoticeRevisionPlanner
import com.mikeyphw.studyflow.core.domain.ExamNoticeRevisionPreview
import com.mikeyphw.studyflow.core.domain.ManagePublicExam
import com.mikeyphw.studyflow.core.domain.ManageStudyContent
import com.mikeyphw.studyflow.core.domain.runRecoverableOperation
import com.mikeyphw.studyflow.core.domain.SyllabusImportDraft
import com.mikeyphw.studyflow.core.domain.TopicBulkUpdate
import com.mikeyphw.studyflow.core.model.ContentProgressStatus
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudySyllabus
import com.mikeyphw.studyflow.core.model.StudyType
import com.mikeyphw.studyflow.core.model.Subject
import com.mikeyphw.studyflow.core.model.SubjectId
import com.mikeyphw.studyflow.core.model.SyllabusFilter
import com.mikeyphw.studyflow.core.model.SyllabusSort
import com.mikeyphw.studyflow.core.model.Topic
import com.mikeyphw.studyflow.core.model.TopicId
import com.mikeyphw.studyflow.core.model.TopicNode
import java.util.UUID
import kotlinx.coroutines.launch

private data class TopicEditorRequest(
    val subject: Subject,
    val topic: Topic?,
    val defaultParentTopicId: TopicId?,
)

private sealed interface DeleteRequest {
    data class SubjectRequest(val subject: Subject) : DeleteRequest
    data class TopicRequest(val topic: Topic) : DeleteRequest
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StudyContentScreen(
    plan: StudyPlan,
    content: ManageStudyContent,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    publicExamManager: ManagePublicExam? = null,
    specializedImportWorkflow: ApplySpecializedContentImport? = null,
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val syllabusFlow = remember(plan.id) { content.observeSyllabus(plan.id) }
    val syllabus by syllabusFlow.collectAsStateWithLifecycle(
        initialValue = StudySyllabus(plan.id, emptyList(), emptyList()),
    )
    val revisionFlow = remember(plan.id, publicExamManager) {
        publicExamManager?.observeExamNoticeRevisions(plan.id) ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }
    val revisions by revisionFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val coverage = remember(syllabus) { ExamNoticeRevisionPlanner.coverage(syllabus) }
    var query by rememberSaveable { mutableStateOf("") }
    var filterName by rememberSaveable { mutableStateOf(SyllabusFilter.ACTIVE.name) }
    var sortName by rememberSaveable { mutableStateOf(SyllabusSort.POSITION.name) }
    var expandedSubjectIds by rememberSaveable { mutableStateOf(setOf<String>()) }
    var bulkMode by rememberSaveable { mutableStateOf(false) }
    var selectedTopicIds by rememberSaveable { mutableStateOf(setOf<String>()) }
    var editingSubjectId by rememberSaveable { mutableStateOf<String?>(null) }
    var addingSubject by rememberSaveable { mutableStateOf(false) }
    var topicEditorSubjectId by rememberSaveable { mutableStateOf<String?>(null) }
    var topicEditorTopicId by rememberSaveable { mutableStateOf<String?>(null) }
    var topicEditorParentId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteRequest by remember { mutableStateOf<DeleteRequest?>(null) }
    var showBulkDialog by rememberSaveable { mutableStateOf(false) }
    var showImportDialog by rememberSaveable { mutableStateOf(false) }
    var pendingRevisionPreview by remember { mutableStateOf<ExamNoticeRevisionPreview?>(null) }

    val editingSubject = editingSubjectId?.let { id -> syllabus.subjects.firstOrNull { it.id.value == id } }
    val topicEditorRequest = topicEditorSubjectId?.let { subjectId ->
        syllabus.subjects.firstOrNull { it.id.value == subjectId }?.let { subject ->
            TopicEditorRequest(
                subject = subject,
                topic = topicEditorTopicId?.let { topicId ->
                    syllabus.topics.firstOrNull { it.id.value == topicId }
                },
                defaultParentTopicId = topicEditorParentId?.let(::TopicId),
            )
        }
    }

    fun openTopicEditor(subject: Subject, topic: Topic?, parent: TopicId?) {
        topicEditorSubjectId = subject.id.value
        topicEditorTopicId = topic?.id?.value
        topicEditorParentId = parent?.value
    }

    fun closeTopicEditor() {
        topicEditorSubjectId = null
        topicEditorTopicId = null
        topicEditorParentId = null
    }

    val filter = enumValueOrDefault<SyllabusFilter>(filterName, SyllabusFilter.ACTIVE)
    val sort = enumValueOrDefault<SyllabusSort>(sortName, SyllabusSort.POSITION)
    val visibleSubjects = syllabus.visibleSubjects(query, filter, sort)
    val operationFailed = stringResource(R.string.operation_failed)
    val subjectSaved = stringResource(R.string.subject_saved)
    val topicSaved = stringResource(R.string.topic_saved)
    val contentDeleted = stringResource(R.string.content_deleted)
    val reordered = stringResource(R.string.content_reordered)
    val bulkUpdated = stringResource(R.string.bulk_updated)
    val importCompleted = stringResource(R.string.import_completed)

    fun launchAction(success: String, action: suspend () -> Unit) {
        scope.launch {
            runRecoverableOperation { action() }
                .onSuccess { snackbar.showSnackbar(success) }
                .onFailure { snackbar.showSnackbar(operationFailed) }
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.syllabus_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.go_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showImportDialog = true }) {
                        Icon(Icons.Outlined.FileUpload, contentDescription = stringResource(R.string.import_syllabus))
                    }
                    IconButton(onClick = {
                        bulkMode = !bulkMode
                        if (!bulkMode) selectedTopicIds = emptySet()
                    }) {
                        Icon(
                            if (bulkMode) Icons.Outlined.CheckBox else Icons.Outlined.SelectAll,
                            contentDescription = stringResource(
                                if (bulkMode) R.string.finish_bulk_edit else R.string.bulk_edit,
                            ),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { addingSubject = true }) {
                Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.add_subject))
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (plan.type == StudyType.PUBLIC_EXAMINATION && publicExamManager != null) {
                item {
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                stringResource(R.string.exam_notice_history_title),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                stringResource(
                                    R.string.exam_notice_coverage,
                                    coverage.studiedCanonicalItems,
                                    coverage.totalCanonicalItems,
                                    coverage.coveragePercent,
                                ),
                            )
                            revisions.firstOrNull()?.let { revision ->
                                Text(
                                    stringResource(
                                        R.string.exam_notice_latest_revision,
                                        revision.label,
                                        revision.addedCount,
                                        revision.removedCount,
                                        revision.changedCount,
                                    ),
                                )
                            } ?: Text(stringResource(R.string.exam_notice_no_revisions))
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.syllabus_subtitle, plan.name),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.search_content)) },
                        singleLine = true,
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SyllabusFilter.entries.forEach { candidate ->
                            FilterChip(
                                selected = candidate == filter,
                                onClick = { filterName = candidate.name },
                                label = { Text(stringResource(candidate.filterLabel())) },
                            )
                        }
                    }
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SyllabusSort.entries.forEach { candidate ->
                            FilterChip(
                                selected = candidate == sort,
                                onClick = { sortName = candidate.name },
                                label = { Text(stringResource(candidate.sortLabel())) },
                            )
                        }
                    }
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        StudyFlowStatusPill(
                            text = pluralStringResource(
                                R.plurals.subject_count,
                                syllabus.subjects.size,
                                syllabus.subjects.size,
                            ),
                        )
                        StudyFlowStatusPill(
                            text = pluralStringResource(
                                R.plurals.topic_count,
                                syllabus.topics.size,
                                syllabus.topics.size,
                            ),
                        )
                        if (bulkMode && selectedTopicIds.isNotEmpty()) {
                            Button(onClick = { showBulkDialog = true }) {
                                Text(
                                    pluralStringResource(
                                        R.plurals.selected_topic_count,
                                        selectedTopicIds.size,
                                        selectedTopicIds.size,
                                    ),
                                )
                            }
                        }
                    }
                }
            }

            if (syllabus.subjects.isEmpty()) {
                item {
                    StudyFlowEmptyState(
                        title = stringResource(R.string.empty_syllabus_title),
                        message = stringResource(R.string.empty_syllabus_message),
                        actionLabel = stringResource(R.string.add_subject),
                        onAction = { addingSubject = true },
                    )
                }
            } else if (visibleSubjects.isEmpty()) {
                item {
                    StudyFlowEmptyState(
                        title = stringResource(R.string.no_search_results),
                        message = stringResource(R.string.empty_syllabus_message),
                    )
                }
            } else {
                items(visibleSubjects, key = { it.subject.id.value }) { visible ->
                    SubjectCard(
                        subject = visible.subject,
                        visibleTopicCount = visible.visibleTopicCount,
                        topicTree = visible.topicTree,
                        syllabus = syllabus,
                        expanded = visible.subject.id.value in expandedSubjectIds || query.isNotBlank(),
                        bulkMode = bulkMode,
                        selectedTopicIds = selectedTopicIds,
                        onToggleExpanded = {
                            expandedSubjectIds = if (visible.subject.id.value in expandedSubjectIds) {
                                expandedSubjectIds - visible.subject.id.value
                            } else {
                                expandedSubjectIds + visible.subject.id.value
                            }
                        },
                        onToggleSelected = { topicId ->
                            selectedTopicIds = if (topicId.value in selectedTopicIds) {
                                selectedTopicIds - topicId.value
                            } else {
                                selectedTopicIds + topicId.value
                            }
                        },
                        onEditSubject = { editingSubjectId = visible.subject.id.value },
                        onAddTopic = { parent -> openTopicEditor(visible.subject, null, parent) },
                        onEditTopic = { topic ->
                            openTopicEditor(visible.subject, topic, topic.parentTopicId)
                        },
                        onToggleSubjectArchived = {
                            launchAction(subjectSaved) {
                                if (visible.subject.archived) {
                                    content.restoreSubject(visible.subject)
                                } else {
                                    content.archiveSubject(visible.subject)
                                }
                            }
                        },
                        onToggleTopicArchived = { topic ->
                            launchAction(subjectSaved) {
                                if (topic.archived) content.restoreTopic(topic) else content.archiveTopic(topic)
                            }
                        },
                        onDeleteSubject = { deleteRequest = DeleteRequest.SubjectRequest(visible.subject) },
                        onDeleteTopic = { deleteRequest = DeleteRequest.TopicRequest(it) },
                        onMoveSubject = { direction ->
                            val ordered = syllabus.subjects.sortedBy { it.position }
                            val index = ordered.indexOfFirst { it.id == visible.subject.id }
                            val destination = (index + direction).coerceIn(0, ordered.lastIndex)
                            val ids = moveItem(ordered.map(Subject::id), index, destination)
                            launchAction(reordered) { content.reorderSubjects(plan.id, ids) }
                        },
                        onMoveTopic = { topic, direction ->
                            val ids = siblingIds(syllabus.topics, topic)
                            val index = ids.indexOf(topic.id)
                            val destination = (index + direction).coerceIn(0, ids.lastIndex)
                            launchAction(reordered) {
                                content.reorderTopics(
                                    plan.id,
                                    topic.subjectId,
                                    topic.parentTopicId,
                                    moveItem(ids, index, destination),
                                )
                            }
                        },
                    )
                }
            }
        }
    }

    if (addingSubject || editingSubject != null) {
        SubjectEditorDialog(
            subject = editingSubject,
            examStages = syllabus.examStages,
            onDismiss = { addingSubject = false; editingSubjectId = null },
            onSave = { value ->
                val existing = editingSubject
                val subject = existing?.copy(
                    name = value.name,
                    description = value.description,
                    priority = value.priority,
                    examWeight = value.examWeight,
                    estimatedMinutes = value.estimatedMinutes,
                    examStageId = value.examStageId,
                ) ?: Subject(
                    id = SubjectId(UUID.randomUUID().toString()),
                    studyPlanId = plan.id,
                    name = value.name,
                    description = value.description,
                    colorArgb = null,
                    priority = value.priority,
                    examWeight = value.examWeight,
                    estimatedMinutes = value.estimatedMinutes,
                    progressPercent = 0,
                    accuracyPercent = 0,
                    status = ContentProgressStatus.NOT_STARTED,
                    position = syllabus.subjects.size,
                    archived = false,
                    examStageId = value.examStageId,
                )
                addingSubject = false
                editingSubjectId = null
                launchAction(subjectSaved) { content.saveSubject(subject) }
            },
        )
    }

    topicEditorRequest?.let { request ->
        val excluded = request.topic?.let { topic -> descendantIds(topic.id, syllabus.topics) + topic.id }.orEmpty()
        TopicEditorDialog(
            topic = request.topic,
            defaultParentTopicId = request.defaultParentTopicId,
            availableParents = syllabus.topics.filter { it.subjectId == request.subject.id && it.id !in excluded },
            examStages = syllabus.examStages,
            onDismiss = ::closeTopicEditor,
            onSave = { value ->
                val existing = request.topic
                val siblings = syllabus.topics.filter {
                    it.subjectId == request.subject.id &&
                        it.parentTopicId == value.parentTopicId &&
                        it.id != existing?.id
                }
                val topic = existing?.copy(
                    name = value.name,
                    description = value.description,
                    priority = value.priority,
                    difficulty = value.difficulty,
                    examIncidencePercent = value.examIncidencePercent,
                    estimatedMinutes = value.estimatedMinutes,
                    progressPercent = value.progressPercent,
                    masteryPercent = value.masteryPercent,
                    status = value.status,
                    parentTopicId = value.parentTopicId,
                    examStageId = value.examStageId,
                ) ?: Topic(
                    id = TopicId(UUID.randomUUID().toString()),
                    studyPlanId = plan.id,
                    subjectId = request.subject.id,
                    parentTopicId = value.parentTopicId,
                    name = value.name,
                    description = value.description,
                    priority = value.priority,
                    difficulty = value.difficulty,
                    examIncidencePercent = value.examIncidencePercent,
                    estimatedMinutes = value.estimatedMinutes,
                    progressPercent = value.progressPercent,
                    masteryPercent = value.masteryPercent,
                    status = value.status,
                    position = siblings.size,
                    archived = value.status == ContentProgressStatus.ARCHIVED,
                    examStageId = value.examStageId,
                )
                closeTopicEditor()
                launchAction(topicSaved) { content.saveTopic(topic) }
            },
        )
    }

    if (showBulkDialog) {
        BulkEditDialog(
            selectedCount = selectedTopicIds.size,
            examStages = syllabus.examStages,
            onDismiss = { showBulkDialog = false },
            onApply = { update: TopicBulkUpdate ->
                showBulkDialog = false
                val ids = selectedTopicIds.mapTo(mutableSetOf(), ::TopicId)
                launchAction(bulkUpdated) {
                    content.bulkUpdateTopics(plan.id, ids, update)
                    selectedTopicIds = emptySet()
                    bulkMode = false
                }
            },
        )
    }

    if (showImportDialog) {
        ImportSyllabusDialog(
            existingSyllabus = syllabus,
            isPublicExam = plan.type == StudyType.PUBLIC_EXAMINATION,
            onDismiss = { showImportDialog = false },
            onImport = { importPreview: ExamNoticeImportPreview ->
                showImportDialog = false
                if (plan.type == StudyType.PUBLIC_EXAMINATION && publicExamManager != null) {
                    scope.launch {
                        runRecoverableOperation {
                            publicExamManager.prepareExamNoticeRevision(
                                planId = plan.id,
                                importPreview = importPreview,
                                existing = syllabus,
                            )
                        }.onSuccess { pendingRevisionPreview = it }
                            .onFailure { snackbar.showSnackbar(operationFailed) }
                    }
                } else {
                    launchAction(importCompleted) { content.importSyllabus(plan.id, importPreview.draft) }
                }
            },
        )
    }

    pendingRevisionPreview?.let { preview ->
        AlertDialog(
            onDismissRequest = { pendingRevisionPreview = null },
            title = { Text(stringResource(R.string.exam_notice_revision_preview_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(
                            R.string.exam_notice_revision_summary,
                            preview.addedCount,
                            preview.removedCount,
                            preview.changedCount,
                            preview.unchangedCount,
                        ),
                    )
                    Text(stringResource(R.string.exam_notice_revision_archive_notice))
                    preview.changes.filter { it.kind != ExamNoticeDiffKind.UNCHANGED }.take(12).forEach { change ->
                        Text("• ${change.kind.name.lowercase()}: ${change.afterName ?: change.beforeName.orEmpty()}")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingRevisionPreview = null
                        launchAction(importCompleted) { publicExamManager?.applyExamNoticeRevision(preview) }
                    },
                ) { Text(stringResource(R.string.apply_revision)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRevisionPreview = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    deleteRequest?.let { request ->
        ConfirmDeleteDialog(
            isSubject = request is DeleteRequest.SubjectRequest,
            onDismiss = { deleteRequest = null },
            onConfirm = {
                deleteRequest = null
                launchAction(contentDeleted) {
                    when (request) {
                        is DeleteRequest.SubjectRequest -> content.deleteSubject(plan.id, request.subject.id)
                        is DeleteRequest.TopicRequest -> content.deleteTopic(plan.id, request.topic.id)
                    }
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubjectCard(
    subject: Subject,
    visibleTopicCount: Int,
    topicTree: List<TopicNode>,
    syllabus: StudySyllabus,
    expanded: Boolean,
    bulkMode: Boolean,
    selectedTopicIds: Set<String>,
    onToggleExpanded: () -> Unit,
    onToggleSelected: (TopicId) -> Unit,
    onEditSubject: () -> Unit,
    onAddTopic: (TopicId?) -> Unit,
    onEditTopic: (Topic) -> Unit,
    onToggleSubjectArchived: () -> Unit,
    onToggleTopicArchived: (Topic) -> Unit,
    onDeleteSubject: () -> Unit,
    onDeleteTopic: (Topic) -> Unit,
    onMoveSubject: (Int) -> Unit,
    onMoveTopic: (Topic, Int) -> Unit,
) {
    var menuExpanded by rememberSaveable(subject.id.value) { mutableStateOf(false) }
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        text = subject.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.priority_value, subject.priority),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(R.string.weight_value, subject.examWeight),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    LinearProgressIndicator(
                        progress = { subject.progressPercent / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(stringResource(R.string.progress_value, subject.progressPercent))
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Outlined.MoreVert, stringResource(R.string.more_actions))
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.move_up)) },
                            onClick = { menuExpanded = false; onMoveSubject(-1) },
                            leadingIcon = { Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = null) },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.move_down)) },
                            onClick = { menuExpanded = false; onMoveSubject(1) },
                            leadingIcon = { Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null) },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(stringResource(if (subject.archived) R.string.restore else R.string.archive))
                            },
                            onClick = { menuExpanded = false; onToggleSubjectArchived() },
                            leadingIcon = {
                                Icon(
                                    if (subject.archived) Icons.Outlined.Restore else Icons.Outlined.Archive,
                                    contentDescription = null,
                                )
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.edit_subject)) },
                            onClick = { menuExpanded = false; onEditSubject() },
                            enabled = !subject.archived,
                            leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete_subject)) },
                            onClick = { menuExpanded = false; onDeleteSubject() },
                            leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
                        )
                    }
                }
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StudyFlowStatusPill(
                    text = pluralStringResource(
                        R.plurals.topic_count,
                        visibleTopicCount,
                        visibleTopicCount,
                    ),
                )
                TextButton(onClick = { onAddTopic(null) }) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Text(stringResource(R.string.add_topic))
                }
                TextButton(onClick = onToggleExpanded) {
                    Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
                    Text(stringResource(if (expanded) R.string.collapse else R.string.expand))
                }
            }
            if (expanded) {
                topicTree.forEach { node ->
                    TopicNodeRow(
                        node = node,
                        syllabus = syllabus,
                        bulkMode = bulkMode,
                        selectedTopicIds = selectedTopicIds,
                        onToggleSelected = onToggleSelected,
                        onAddChild = { onAddTopic(it.id) },
                        onEdit = onEditTopic,
                        onToggleArchived = onToggleTopicArchived,
                        onDelete = onDeleteTopic,
                        onMove = onMoveTopic,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TopicNodeRow(
    node: TopicNode,
    syllabus: StudySyllabus,
    bulkMode: Boolean,
    selectedTopicIds: Set<String>,
    onToggleSelected: (TopicId) -> Unit,
    onAddChild: (Topic) -> Unit,
    onEdit: (Topic) -> Unit,
    onToggleArchived: (Topic) -> Unit,
    onDelete: (Topic) -> Unit,
    onMove: (Topic, Int) -> Unit,
) {
    val topic = node.topic
    val selected = topic.id.value in selectedTopicIds
    var menuExpanded by rememberSaveable(topic.id.value) { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (bulkMode) {
                    Modifier
                        .semantics(mergeDescendants = true) {}
                        .toggleable(
                            value = selected,
                            role = Role.Checkbox,
                            onValueChange = { onToggleSelected(topic.id) },
                        )
                } else {
                    Modifier
                },
            )
            .padding(start = (node.depth * 18).dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (bulkMode) {
            Checkbox(checked = selected, onCheckedChange = null)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(topic.name, fontWeight = FontWeight.SemiBold)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(stringResource(topic.status.label()), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = stringResource(R.string.progress_value, topic.progressPercent),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.mastery_value, topic.masteryPercent),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val stage = syllabus.examStages.firstOrNull { it.id == topic.examStageId }
            if (stage != null) Text(stage.name, color = MaterialTheme.colorScheme.primary)
        }
        if (!bulkMode) {
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Outlined.MoreVert, stringResource(R.string.more_actions))
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.move_up)) },
                        onClick = { menuExpanded = false; onMove(topic, -1) },
                        leadingIcon = { Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = null) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.move_down)) },
                        onClick = { menuExpanded = false; onMove(topic, 1) },
                        leadingIcon = { Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.add_child_topic)) },
                        onClick = { menuExpanded = false; onAddChild(topic) },
                        leadingIcon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(if (topic.archived) R.string.restore else R.string.archive)) },
                        onClick = { menuExpanded = false; onToggleArchived(topic) },
                        leadingIcon = {
                            Icon(
                                if (topic.archived) Icons.Outlined.Restore else Icons.Outlined.Archive,
                                contentDescription = null,
                            )
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.edit_topic)) },
                        onClick = { menuExpanded = false; onEdit(topic) },
                        enabled = !topic.archived,
                        leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete_topic)) },
                        onClick = { menuExpanded = false; onDelete(topic) },
                        leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
                    )
                }
            }
        } else {
            Icon(
                if (topic.archived) Icons.Outlined.Archive else Icons.Outlined.Restore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
        }
    }
    node.children.forEach { child ->
        TopicNodeRow(
            node = child,
            syllabus = syllabus,
            bulkMode = bulkMode,
            selectedTopicIds = selectedTopicIds,
            onToggleSelected = onToggleSelected,
            onAddChild = onAddChild,
            onEdit = onEdit,
            onToggleArchived = onToggleArchived,
            onDelete = onDelete,
            onMove = onMove,
        )
    }
}

private fun descendantIds(topicId: TopicId, topics: List<Topic>): Set<TopicId> {
    val byParent = topics.groupBy(Topic::parentTopicId)
    val result = mutableSetOf<TopicId>()
    fun visit(parent: TopicId) {
        byParent[parent].orEmpty().forEach { child -> if (result.add(child.id)) visit(child.id) }
    }
    visit(topicId)
    return result
}

private fun SyllabusFilter.filterLabel(): Int = when (this) {
    SyllabusFilter.ALL -> R.string.filter_all
    SyllabusFilter.ACTIVE -> R.string.filter_active
    SyllabusFilter.NEEDS_ATTENTION -> R.string.filter_attention
    SyllabusFilter.MASTERED -> R.string.filter_mastered
    SyllabusFilter.ARCHIVED -> R.string.filter_archived
}

private fun SyllabusSort.sortLabel(): Int = when (this) {
    SyllabusSort.POSITION -> R.string.sort_position
    SyllabusSort.PRIORITY -> R.string.sort_priority
    SyllabusSort.NAME -> R.string.sort_name
    SyllabusSort.PROGRESS -> R.string.sort_progress
}


private inline fun <reified T : Enum<T>> enumValueOrDefault(name: String, defaultValue: T): T =
    enumValues<T>().firstOrNull { value -> value.name == name } ?: defaultValue
