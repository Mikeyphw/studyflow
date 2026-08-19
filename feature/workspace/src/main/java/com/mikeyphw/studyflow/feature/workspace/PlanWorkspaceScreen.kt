package com.mikeyphw.studyflow.feature.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mikeyphw.studyflow.core.designsystem.StudyFlowCoachCard
import com.mikeyphw.studyflow.core.designsystem.StudyFlowEmptyState
import com.mikeyphw.studyflow.core.designsystem.StudyFlowMetricPulse
import com.mikeyphw.studyflow.core.designsystem.StudyFlowLoadingPane
import com.mikeyphw.studyflow.core.designsystem.StudyFlowResponsivePair
import com.mikeyphw.studyflow.core.designsystem.StudyFlowSubjectAccordion
import com.mikeyphw.studyflow.core.designsystem.StudyFlowTimelineRow
import com.mikeyphw.studyflow.core.designsystem.StudyFlowWorkspaceTabs
import com.mikeyphw.studyflow.core.designsystem.studyFlowSectionHeader
import com.mikeyphw.studyflow.core.designsystem.workspaceContentPadding
import com.mikeyphw.studyflow.core.domain.ManageStudyContent
import com.mikeyphw.studyflow.core.domain.ManageStudyLibrary
import com.mikeyphw.studyflow.core.domain.ManageStudyPractice
import com.mikeyphw.studyflow.core.domain.ManageStudyProgress
import com.mikeyphw.studyflow.core.domain.ManageStudyReview
import com.mikeyphw.studyflow.core.domain.ManageStudySchedule
import com.mikeyphw.studyflow.core.domain.ManageStudySession
import com.mikeyphw.studyflow.core.domain.asStudyFlowLoadState
import com.mikeyphw.studyflow.core.domain.StudyContentCleanupEngine
import com.mikeyphw.studyflow.core.domain.StudyContentCleanupInput
import com.mikeyphw.studyflow.core.domain.StudyContentCleanupIssueKind
import com.mikeyphw.studyflow.core.domain.StudyContentCleanupReport
import com.mikeyphw.studyflow.core.domain.StudyContentCleanupSeverity
import com.mikeyphw.studyflow.core.model.ErrorEntry
import com.mikeyphw.studyflow.core.model.Flashcard
import com.mikeyphw.studyflow.core.model.Question
import com.mikeyphw.studyflow.core.model.ReviewItem
import com.mikeyphw.studyflow.core.model.PlannedActivityStatus
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudySchedule
import com.mikeyphw.studyflow.core.model.StudySyllabus
import com.mikeyphw.studyflow.core.model.StudyFlowLoadState
import com.mikeyphw.studyflow.core.navigation.PlanWorkspaceTab

@Composable
fun PlanWorkspaceScreen(
    plan: StudyPlan,
    selectedTab: PlanWorkspaceTab,
    scheduleManager: ManageStudySchedule,
    contentManager: ManageStudyContent,
    libraryManager: ManageStudyLibrary,
    practiceManager: ManageStudyPractice,
    reviewManager: ManageStudyReview,
    progressManager: ManageStudyProgress,
    sessionManager: ManageStudySession,
    onTabSelected: (PlanWorkspaceTab) -> Unit,
    onSwitchPlan: () -> Unit,
    onSearch: () -> Unit,
    onSystem: () -> Unit,
    onEditPlan: () -> Unit,
    onPlanSettings: () -> Unit,
    onSessions: () -> Unit,
    onReminders: () -> Unit,
    onPortability: () -> Unit,
    onManageSchedule: () -> Unit,
    onManageContent: () -> Unit,
    onManageLibrary: () -> Unit,
    modifier: Modifier = Modifier,
    targetId: String? = null,
) {
    var loadAttempt by remember(plan.id) { mutableIntStateOf(0) }
    val scheduleFlow = remember(plan.id, loadAttempt) {
        scheduleManager.observeSchedule(plan.id).asStudyFlowLoadState()
    }
    val syllabusFlow = remember(plan.id, loadAttempt) {
        contentManager.observeSyllabus(plan.id).asStudyFlowLoadState()
    }
    val libraryFlow = remember(plan.id, loadAttempt) {
        libraryManager.observeSnapshot(plan.id).asStudyFlowLoadState()
    }
    val questionsFlow = remember(plan.id, loadAttempt) {
        practiceManager.observeQuestions(plan.id).asStudyFlowLoadState()
    }
    val reviewItemsFlow = remember(plan.id, loadAttempt) {
        reviewManager.observeReviewItems(plan.id).asStudyFlowLoadState()
    }
    val flashcardsFlow = remember(plan.id, loadAttempt) {
        reviewManager.observeFlashcards(plan.id).asStudyFlowLoadState()
    }
    val errorEntriesFlow = remember(plan.id, loadAttempt) {
        reviewManager.observeErrorEntries(plan.id).asStudyFlowLoadState()
    }
    val progressFlow = remember(plan.id, loadAttempt) {
        progressManager.observeSnapshot(plan.id).asStudyFlowLoadState()
    }
    val sessionsFlow = remember(plan.id, loadAttempt) {
        sessionManager.observeSessions(plan.id).asStudyFlowLoadState()
    }
    val scheduleState by scheduleFlow.collectAsStateWithLifecycle(initialValue = StudyFlowLoadState.Loading)
    val syllabusState by syllabusFlow.collectAsStateWithLifecycle(initialValue = StudyFlowLoadState.Loading)
    val libraryState by libraryFlow.collectAsStateWithLifecycle(initialValue = StudyFlowLoadState.Loading)
    val questionsState by questionsFlow.collectAsStateWithLifecycle(initialValue = StudyFlowLoadState.Loading)
    val reviewItemsState by reviewItemsFlow.collectAsStateWithLifecycle(initialValue = StudyFlowLoadState.Loading)
    val flashcardsState by flashcardsFlow.collectAsStateWithLifecycle(initialValue = StudyFlowLoadState.Loading)
    val errorEntriesState by errorEntriesFlow.collectAsStateWithLifecycle(initialValue = StudyFlowLoadState.Loading)
    val progressState by progressFlow.collectAsStateWithLifecycle(initialValue = StudyFlowLoadState.Loading)
    val sessionsState by sessionsFlow.collectAsStateWithLifecycle(initialValue = StudyFlowLoadState.Loading)
    if (
        !workspaceLoadGate(
            states = listOf(
                scheduleState,
                syllabusState,
                libraryState,
                questionsState,
                reviewItemsState,
                flashcardsState,
                errorEntriesState,
                progressState,
                sessionsState,
            ),
            onRetry = { loadAttempt += 1 },
            modifier = modifier,
        )
    ) return
    val readySchedule = scheduleState.readyValue()
    val readySyllabus = syllabusState.readyValue()
    val readyLibrary = libraryState.readyValue()
    val readyQuestions = questionsState.readyValue()
    val readyReviewItems = reviewItemsState.readyValue()
    val readyFlashcards = flashcardsState.readyValue()
    val readyErrorEntries = errorEntriesState.readyValue()
    val readyProgress = progressState.readyValue()
    val readySessions = sessionsState.readyValue()
    val cleanupReport = remember(
        plan,
        readySyllabus,
        readyLibrary,
        readySchedule,
        readyQuestions,
        readyReviewItems,
        readyFlashcards,
        readyErrorEntries,
        readyProgress,
        readySessions,
    ) {
        StudyContentCleanupEngine.inspect(
            StudyContentCleanupInput(
                plan = plan,
                syllabus = readySyllabus,
                questions = readyQuestions,
                materials = readyLibrary.materials,
                videos = readyLibrary.videos,
                annexes = readyLibrary.annexes,
                reviewItems = readyReviewItems,
                flashcards = readyFlashcards,
                errorEntries = readyErrorEntries,
                plannedActivities = readySchedule.plannedActivities,
                studySessions = readySessions,
                notes = readyProgress.notes,
                nowEpochMillis = System.currentTimeMillis(),
            ),
        )
    }

    Scaffold(modifier = modifier) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            WorkspaceHeader(
                title = stringResource(R.string.plan_title),
                plan = plan,
                onSwitchPlan = onSwitchPlan,
                onSearch = onSearch,
                onSystem = onSystem,
            )
            StudyFlowWorkspaceTabs(
                tabs = PlanWorkspaceTab.entries,
                selected = selectedTab,
                label = { tab ->
                    stringResource(
                        when (tab) {
                            PlanWorkspaceTab.OVERVIEW -> R.string.plan_overview
                            PlanWorkspaceTab.SCHEDULE -> R.string.plan_schedule
                            PlanWorkspaceTab.CONTENT -> R.string.plan_content
                            PlanWorkspaceTab.LIBRARY -> R.string.plan_library
                        },
                    )
                },
                onSelected = onTabSelected,
            )
            when (selectedTab) {
                PlanWorkspaceTab.OVERVIEW -> PlanOverviewPane(
                    plan = plan,
                    scheduleCount = readySchedule.plannedActivities.size,
                    subjectCount = readySyllabus.subjects.size,
                    materialCount = readyLibrary.activeMaterials.size,
                    onEditPlan = onEditPlan,
                    onPlanSettings = onPlanSettings,
                    onSessions = onSessions,
                    onReminders = onReminders,
                    onPortability = onPortability,
                )
                PlanWorkspaceTab.SCHEDULE -> PlanSchedulePane(
                    schedule = readySchedule,
                    onManage = onManageSchedule,
                )
                PlanWorkspaceTab.CONTENT -> PlanContentPane(
                    syllabus = readySyllabus,
                    cleanupReport = cleanupReport,
                    targetId = targetId,
                    onManage = onManageContent,
                )
                PlanWorkspaceTab.LIBRARY -> PlanLibraryPane(
                    materials = readyLibrary.materials.filterNot { it.archived }.map { it.id.value to it.title },
                    videos = readyLibrary.videos.map { it.id.value to it.title },
                    targetId = targetId,
                    annexCount = readyLibrary.annexes.size,
                    onManage = onManageLibrary,
                )
            }
        }
    }
}

@Composable
private fun PlanOverviewPane(
    plan: StudyPlan,
    scheduleCount: Int,
    subjectCount: Int,
    materialCount: Int,
    onEditPlan: () -> Unit,
    onPlanSettings: () -> Unit,
    onSessions: () -> Unit,
    onReminders: () -> Unit,
    onPortability: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = workspaceContentPadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        studyFlowSectionHeader(key = "progress", title = { stringResource(R.string.plan_progress) })
        item(key = "plan-metrics", contentType = "metrics") {
            StudyFlowResponsivePair(
                first = { childModifier ->
                    StudyFlowMetricPulse(
                        label = stringResource(R.string.plan_progress),
                        value = stringResource(R.string.percent_value, plan.progressPercent),
                        progress = plan.progressPercent / 100f,
                        modifier = childModifier,
                    )
                },
                second = { childModifier ->
                    StudyFlowMetricPulse(
                        label = stringResource(R.string.weekly_goal),
                        value = stringResource(R.string.hours_value, plan.weeklyGoalHours),
                        supportingText = stringResource(R.string.minutes_value, plan.studiedMinutesThisWeek),
                        modifier = childModifier,
                    )
                },
            )
        }
        item(key = "plan-structure", contentType = "summary-card") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(plan.description.ifBlank { plan.name }, style = MaterialTheme.typography.bodyLarge)
                    val examDate = plan.examDateIso ?: stringResource(R.string.not_set)
                    Text(
                        text = stringResource(R.string.exam_date) + ": " + examDate,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(pluralStringResource(R.plurals.subjects_count, subjectCount, subjectCount))
                    Text("$scheduleCount · ${stringResource(R.string.plan_schedule)}")
                    Text(pluralStringResource(R.plurals.materials_count, materialCount, materialCount))
                }
            }
        }
        studyFlowSectionHeader(key = "actions", title = { stringResource(R.string.plan_actions) })
        item(key = "actions-card", contentType = "actions") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PlanActionButton(Icons.Outlined.Edit, stringResource(R.string.edit_plan), onEditPlan)
                PlanActionButton(Icons.Outlined.Settings, stringResource(R.string.plan_settings), onPlanSettings)
                PlanActionButton(Icons.Outlined.PlayCircle, stringResource(R.string.study_sessions), onSessions)
                PlanActionButton(Icons.Outlined.Notifications, stringResource(R.string.reminders), onReminders)
                PlanActionButton(Icons.Outlined.Backup, stringResource(R.string.backup_restore), onPortability)
            }
        }
    }
}

@Composable
private fun PlanActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Icon(icon, contentDescription = null)
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun PlanSchedulePane(
    schedule: StudySchedule,
    onManage: () -> Unit,
) {
    val activities = schedule.plannedActivities.sortedBy { it.scheduledStartEpochMillis ?: Long.MAX_VALUE }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = workspaceContentPadding(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        studyFlowSectionHeader(
            key = "schedule",
            title = { stringResource(R.string.plan_schedule) },
            actionLabel = { stringResource(R.string.manage_schedule) },
            onAction = onManage,
        )
        if (activities.isEmpty()) {
            item(key = "schedule-empty", contentType = "empty") {
                StudyFlowEmptyState(
                    title = stringResource(R.string.plan_schedule),
                    message = stringResource(R.string.schedule_empty),
                    actionLabel = stringResource(R.string.manage_schedule),
                    onAction = onManage,
                )
            }
        } else {
            items(
                items = activities,
                key = { activity -> activity.id.value },
                contentType = { "plan-activity" },
            ) { activity ->
                StudyFlowTimelineRow(
                    title = activity.title,
                    metadata = stringResource(R.string.minutes_value, activity.plannedDurationMinutes),
                    icon = Icons.Outlined.Event,
                    status = when (activity.status) {
                        PlannedActivityStatus.PLANNED -> stringResource(R.string.planned)
                        PlannedActivityStatus.IN_PROGRESS -> stringResource(R.string.workspace_in_progress)
                        PlannedActivityStatus.COMPLETED -> stringResource(R.string.completed)
                        PlannedActivityStatus.MISSED -> stringResource(R.string.missed)
                        PlannedActivityStatus.SKIPPED -> stringResource(R.string.skipped)
                        PlannedActivityStatus.CANCELLED -> stringResource(R.string.cancelled)
                    },
                    onClick = onManage,
                )
            }
        }
    }
}

@Composable
private fun PlanContentPane(
    syllabus: StudySyllabus?,
    cleanupReport: StudyContentCleanupReport?,
    targetId: String?,
    onManage: () -> Unit,
) {
    val rawSubjects = syllabus?.subjects.orEmpty().filterNot { it.archived }
    val topics = syllabus?.topics.orEmpty().filterNot { it.archived }
    val targetTopic = topics.firstOrNull { it.id.value == targetId }
    val targetSubjectId = rawSubjects.firstOrNull { it.id.value == targetId }?.id ?: targetTopic?.subjectId
    val subjects = rawSubjects.sortedByDescending { it.id == targetSubjectId }
    val topicsBySubject = remember(topics, targetId) {
        topics.groupBy { it.subjectId }.mapValues { (_, values) ->
            values.sortedByDescending { it.id.value == targetId }
        }
    }
    var expandedSubjectIds by rememberSaveable(syllabus?.studyPlanId?.value, targetId) {
        mutableStateOf(listOfNotNull(targetSubjectId?.value))
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = workspaceContentPadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        studyFlowSectionHeader(
            key = "content-health",
            title = { stringResource(R.string.content_health_title) },
        )
        item(key = "content-health-summary", contentType = "coach") {
            val currentReport = cleanupReport
            StudyFlowCoachCard(
                title = currentReport?.let { report ->
                    stringResource(R.string.percent_value, report.healthScorePercent)
                } ?: stringResource(R.string.not_enough_data),
                message = when {
                    currentReport == null -> stringResource(R.string.content_health_waiting)
                    currentReport.issueCount == 0 -> stringResource(R.string.content_health_clean_message)
                    else -> pluralStringResource(
                        R.plurals.content_health_summary,
                        currentReport.issueCount,
                        currentReport.issueCount,
                        currentReport.actionCount,
                    )
                },
                actionLabel = stringResource(R.string.review_cleanup),
                onAction = onManage,
            )
        }
        item(key = "content-health-safety", contentType = "notice") {
            Text(
                text = stringResource(R.string.cleanup_preview_notice),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(
            items = cleanupReport?.issues.orEmpty().take(5),
            key = { issue -> "cleanup:${issue.kind}" },
            contentType = { "cleanup-issue" },
        ) { issue ->
            StudyFlowTimelineRow(
                title = stringResource(issue.kind.titleRes()),
                metadata = stringResource(issue.kind.messageRes()),
                icon = Icons.Outlined.AutoFixHigh,
                status = stringResource(issue.severity.labelRes()),
                actionLabel = stringResource(R.string.preview_cleanup_action),
                onClick = onManage,
            )
        }

        studyFlowSectionHeader(
            key = "content",
            title = { stringResource(R.string.plan_content) },
            actionLabel = { stringResource(R.string.manage_content) },
            onAction = onManage,
        )
        item(key = "content-counts", contentType = "metrics") {
            StudyFlowMetricPulse(
                label = stringResource(R.string.plan_content),
                value = pluralStringResource(R.plurals.subjects_count, subjects.size, subjects.size),
                supportingText = pluralStringResource(R.plurals.topics_count, topics.size, topics.size),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (subjects.isEmpty()) {
            item(key = "content-empty", contentType = "empty") {
                StudyFlowEmptyState(
                    title = stringResource(R.string.plan_content),
                    message = stringResource(R.string.content_empty),
                    actionLabel = stringResource(R.string.manage_content),
                    onAction = onManage,
                )
            }
        } else {
            items(subjects, key = { subject -> subject.id.value }, contentType = { "subject-accordion" }) { subject ->
                val subjectTopics = topicsBySubject[subject.id].orEmpty()
                val expanded = subject.id.value in expandedSubjectIds
                StudyFlowSubjectAccordion(
                    title = subject.name,
                    supportingText = pluralStringResource(
                        R.plurals.topics_count,
                        subjectTopics.size,
                        subjectTopics.size,
                    ),
                    status = stringResource(R.string.subject_status, subject.progressPercent, subject.accuracyPercent),
                    expanded = expanded,
                    expandedStateDescription = stringResource(R.string.expanded),
                    collapsedStateDescription = stringResource(R.string.collapsed),
                    onExpandedChange = { next ->
                        expandedSubjectIds = if (next) {
                            (expandedSubjectIds + subject.id.value).distinct()
                        } else {
                            expandedSubjectIds - subject.id.value
                        }
                    },
                ) {
                    if (subjectTopics.isEmpty()) {
                        Text(
                            text = stringResource(R.string.no_topics_for_subject),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        subjectTopics.take(6).forEach { topic ->
                            StudyFlowTimelineRow(
                                title = topic.name,
                                metadata = stringResource(R.string.topic_mastery, topic.masteryPercent),
                                icon = Icons.Outlined.AutoStories,
                                status = stringResource(R.string.percent_value, topic.progressPercent),
                                onClick = onManage,
                            )
                        }
                        if (subjectTopics.size > 6) {
                            Button(onClick = onManage, modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    pluralStringResource(
                                        R.plurals.view_all_topics,
                                        subjectTopics.size,
                                        subjectTopics.size,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanLibraryPane(
    materials: List<Pair<String, String>>,
    videos: List<Pair<String, String>>,
    targetId: String?,
    annexCount: Int,
    onManage: () -> Unit,
) {
    val rows = (materials.map { (id, title) -> Triple(id, R.string.material_type, title) } +
        videos.map { (id, title) -> Triple(id, R.string.video_type, title) })
        .sortedByDescending { (id, _, _) -> id == targetId }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = workspaceContentPadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        studyFlowSectionHeader(
            key = "library",
            title = { stringResource(R.string.plan_library) },
            actionLabel = { stringResource(R.string.manage_library) },
            onAction = onManage,
        )
        item(key = "library-counts", contentType = "metrics") {
            StudyFlowMetricPulse(
                label = pluralStringResource(R.plurals.materials_count, materials.size, materials.size),
                value = pluralStringResource(R.plurals.videos_count, videos.size, videos.size),
                supportingText = pluralStringResource(R.plurals.annexes_count, annexCount, annexCount),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (rows.isEmpty() && annexCount == 0) {
            item(key = "library-empty", contentType = "empty") {
                StudyFlowEmptyState(
                    title = stringResource(R.string.plan_library),
                    message = stringResource(R.string.library_empty),
                    actionLabel = stringResource(R.string.manage_library),
                    onAction = onManage,
                )
            }
        } else {
            items(rows, key = { row -> row.first }, contentType = { "library-row" }) { row ->
                StudyFlowTimelineRow(
                    title = row.third,
                    metadata = stringResource(row.second),
                    icon = Icons.Outlined.FolderOpen,
                    onClick = onManage,
                )
            }
        }
    }
}

private fun StudyContentCleanupIssueKind.titleRes(): Int = when (this) {
    StudyContentCleanupIssueKind.DUPLICATE_QUESTION -> R.string.cleanup_duplicate_questions
    StudyContentCleanupIssueKind.ORPHAN_TOPIC -> R.string.cleanup_orphan_topics
    StudyContentCleanupIssueKind.STALE_RESOURCE -> R.string.cleanup_stale_resources
    StudyContentCleanupIssueKind.STALE_PLAN -> R.string.cleanup_stale_plan
    StudyContentCleanupIssueKind.INCONSISTENT_TAG -> R.string.cleanup_inconsistent_tags
    StudyContentCleanupIssueKind.STALE_REVIEW -> R.string.cleanup_stale_reviews
    StudyContentCleanupIssueKind.BROKEN_SCHEDULE_ALIGNMENT -> R.string.cleanup_broken_schedule
}

private fun StudyContentCleanupIssueKind.messageRes(): Int = when (this) {
    StudyContentCleanupIssueKind.DUPLICATE_QUESTION -> R.string.cleanup_duplicate_questions_message
    StudyContentCleanupIssueKind.ORPHAN_TOPIC -> R.string.cleanup_orphan_topics_message
    StudyContentCleanupIssueKind.STALE_RESOURCE -> R.string.cleanup_stale_resources_message
    StudyContentCleanupIssueKind.STALE_PLAN -> R.string.cleanup_stale_plan_message
    StudyContentCleanupIssueKind.INCONSISTENT_TAG -> R.string.cleanup_inconsistent_tags_message
    StudyContentCleanupIssueKind.STALE_REVIEW -> R.string.cleanup_stale_reviews_message
    StudyContentCleanupIssueKind.BROKEN_SCHEDULE_ALIGNMENT -> R.string.cleanup_broken_schedule_message
}

private fun StudyContentCleanupSeverity.labelRes(): Int = when (this) {
    StudyContentCleanupSeverity.INFO -> R.string.cleanup_severity_info
    StudyContentCleanupSeverity.REVIEW -> R.string.cleanup_severity_review
    StudyContentCleanupSeverity.ACTION -> R.string.cleanup_severity_action
}
