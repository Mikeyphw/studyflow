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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mikeyphw.studyflow.core.designsystem.StudyFlowEmptyState
import com.mikeyphw.studyflow.core.designsystem.StudyFlowMetricPulse
import com.mikeyphw.studyflow.core.designsystem.StudyFlowResponsivePair
import com.mikeyphw.studyflow.core.designsystem.StudyFlowSubjectAccordion
import com.mikeyphw.studyflow.core.designsystem.StudyFlowTimelineRow
import com.mikeyphw.studyflow.core.designsystem.StudyFlowWorkspaceTabs
import com.mikeyphw.studyflow.core.designsystem.studyFlowSectionHeader
import com.mikeyphw.studyflow.core.designsystem.workspaceContentPadding
import com.mikeyphw.studyflow.core.domain.ManageStudyContent
import com.mikeyphw.studyflow.core.domain.ManageStudyLibrary
import com.mikeyphw.studyflow.core.domain.ManageStudySchedule
import com.mikeyphw.studyflow.core.model.PlannedActivityStatus
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudySchedule
import com.mikeyphw.studyflow.core.model.StudySyllabus
import com.mikeyphw.studyflow.core.navigation.PlanWorkspaceTab

@Composable
fun PlanWorkspaceScreen(
    plan: StudyPlan,
    selectedTab: PlanWorkspaceTab,
    scheduleManager: ManageStudySchedule,
    contentManager: ManageStudyContent,
    libraryManager: ManageStudyLibrary,
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
) {
    val scheduleFlow = remember(plan.id) { scheduleManager.observeSchedule(plan.id) }
    val syllabusFlow = remember(plan.id) { contentManager.observeSyllabus(plan.id) }
    val libraryFlow = remember(plan.id) { libraryManager.observeSnapshot(plan.id) }
    val schedule by scheduleFlow.collectAsStateWithLifecycle(
        initialValue = StudySchedule(plan.id, null, emptyList(), emptyList()),
    )
    val syllabus by syllabusFlow.collectAsStateWithLifecycle(initialValue = null)
    val library by libraryFlow.collectAsStateWithLifecycle(initialValue = null)

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
                    scheduleCount = schedule.plannedActivities.size,
                    subjectCount = syllabus?.subjects?.size ?: 0,
                    materialCount = library?.materials?.size ?: 0,
                    onEditPlan = onEditPlan,
                    onPlanSettings = onPlanSettings,
                    onSessions = onSessions,
                    onReminders = onReminders,
                    onPortability = onPortability,
                )
                PlanWorkspaceTab.SCHEDULE -> PlanSchedulePane(
                    schedule = schedule,
                    onManage = onManageSchedule,
                )
                PlanWorkspaceTab.CONTENT -> PlanContentPane(
                    syllabus = syllabus,
                    onManage = onManageContent,
                )
                PlanWorkspaceTab.LIBRARY -> PlanLibraryPane(
                    materialTitles = library?.materials.orEmpty().filterNot { it.archived }.map { it.title },
                    videoTitles = library?.videos.orEmpty().map { it.title },
                    annexCount = library?.annexes?.size ?: 0,
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
                    Text(stringResource(R.string.subjects_count, subjectCount))
                    Text("$scheduleCount · ${stringResource(R.string.plan_schedule)}")
                    Text(stringResource(R.string.materials_count, materialCount))
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
    onManage: () -> Unit,
) {
    val subjects = syllabus?.subjects.orEmpty().filterNot { it.archived }
    val topics = syllabus?.topics.orEmpty().filterNot { it.archived }
    val topicsBySubject = remember(topics) { topics.groupBy { it.subjectId } }
    var expandedSubjectIds by rememberSaveable(syllabus?.studyPlanId?.value) { mutableStateOf(emptyList<String>()) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = workspaceContentPadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        studyFlowSectionHeader(
            key = "content",
            title = { stringResource(R.string.plan_content) },
            actionLabel = { stringResource(R.string.manage_content) },
            onAction = onManage,
        )
        item(key = "content-counts", contentType = "metrics") {
            StudyFlowMetricPulse(
                label = stringResource(R.string.plan_content),
                value = stringResource(R.string.subjects_count, subjects.size),
                supportingText = stringResource(R.string.topics_count, topics.size),
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
                    supportingText = stringResource(R.string.topics_count, subjectTopics.size),
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
                                Text(stringResource(R.string.view_all_topics, subjectTopics.size))
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
    materialTitles: List<String>,
    videoTitles: List<String>,
    annexCount: Int,
    onManage: () -> Unit,
) {
    val rows = materialTitles.map { R.string.material_type to it } +
        videoTitles.map { R.string.video_type to it }
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
                label = stringResource(R.string.materials_count, materialTitles.size),
                value = stringResource(R.string.videos_count, videoTitles.size),
                supportingText = stringResource(R.string.annexes_count, annexCount),
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
            items(rows, key = { row -> "${row.first}:${row.second}" }, contentType = { "library-row" }) { row ->
                StudyFlowTimelineRow(
                    title = row.second,
                    metadata = stringResource(row.first),
                    icon = Icons.Outlined.FolderOpen,
                    onClick = onManage,
                )
            }
        }
    }
}
