package com.mikeyphw.studyflow.feature.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.MaterialTheme
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
import com.mikeyphw.studyflow.core.designsystem.StudyFlowInsightPanel
import com.mikeyphw.studyflow.core.designsystem.StudyFlowEmptyState
import com.mikeyphw.studyflow.core.designsystem.StudyFlowActionRow
import com.mikeyphw.studyflow.core.designsystem.StudyFlowMetric
import com.mikeyphw.studyflow.core.designsystem.StudyFlowMetricStrip
import com.mikeyphw.studyflow.core.designsystem.StudyFlowPlanSummary
import com.mikeyphw.studyflow.core.designsystem.StudyFlowSectionTitle
import com.mikeyphw.studyflow.core.designsystem.StudyFlowTabStrip
import com.mikeyphw.studyflow.core.designsystem.StudyFlowTimelineItem
import com.mikeyphw.studyflow.core.designsystem.StudyFlowSubjectAccordion
import com.mikeyphw.studyflow.core.designsystem.workspaceContentPadding
import com.mikeyphw.studyflow.core.domain.ManagePublicExam
import com.mikeyphw.studyflow.core.domain.ManageStudyContent
import com.mikeyphw.studyflow.core.domain.ManageStudyLibrary
import com.mikeyphw.studyflow.core.domain.ManageStudyPractice
import com.mikeyphw.studyflow.core.domain.ManageStudyProgress
import com.mikeyphw.studyflow.core.domain.ManageStudyReview
import com.mikeyphw.studyflow.core.domain.ManageStudySchedule
import com.mikeyphw.studyflow.core.domain.ManageStudySession
import com.mikeyphw.studyflow.core.domain.PublicExamDashboardBuilder
import com.mikeyphw.studyflow.core.domain.PublicExamDashboardSnapshot
import com.mikeyphw.studyflow.core.domain.PublicExamDateResolver
import com.mikeyphw.studyflow.core.domain.PublicExamPreEditalBuilder
import com.mikeyphw.studyflow.core.domain.StudyPhaseDefinition
import com.mikeyphw.studyflow.core.domain.StudyPhasePlanCodec
import com.mikeyphw.studyflow.core.domain.StudyPhaseKind
import com.mikeyphw.studyflow.core.domain.PublicExamPreEditalIssue
import com.mikeyphw.studyflow.core.domain.PublicExamPreEditalSnapshot
import com.mikeyphw.studyflow.core.domain.PublicExamPriorityEvidence
import com.mikeyphw.studyflow.core.domain.PublicExamPriorityPlanner
import com.mikeyphw.studyflow.core.domain.PublicExamPriorityReasonCode
import com.mikeyphw.studyflow.core.domain.PublicExamPriorityRecommendation
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
import com.mikeyphw.studyflow.core.model.StudyContentMode
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudyType
import com.mikeyphw.studyflow.core.model.StudySchedule
import com.mikeyphw.studyflow.core.model.StudySyllabus
import com.mikeyphw.studyflow.core.model.StudyTarget
import com.mikeyphw.studyflow.core.model.StudyFlowLoadState
import com.mikeyphw.studyflow.core.navigation.PlanWorkspaceTab
import java.time.LocalDate

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
    publicExamManager: ManagePublicExam,
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
    val targetFlow = remember(plan.id, loadAttempt) {
        contentManager.observeStudyTarget(plan.id).asStudyFlowLoadState()
    }
    val mockExamsFlow = remember(plan.id, loadAttempt) {
        practiceManager.observeMockExams(plan.id).asStudyFlowLoadState()
    }
    val legislationFlow = remember(plan.id, loadAttempt) {
        publicExamManager.observeLegislation(plan.id).asStudyFlowLoadState()
    }
    val studyCycleFlow = remember(plan.id, loadAttempt) {
        publicExamManager.observeStudyCycle(plan.id).asStudyFlowLoadState()
    }
    val examNoticeRevisionsFlow = remember(plan.id, loadAttempt) {
        publicExamManager.observeExamNoticeRevisions(plan.id).asStudyFlowLoadState()
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
    val targetState by targetFlow.collectAsStateWithLifecycle(initialValue = StudyFlowLoadState.Loading)
    val mockExamsState by mockExamsFlow.collectAsStateWithLifecycle(initialValue = StudyFlowLoadState.Loading)
    val legislationState by legislationFlow.collectAsStateWithLifecycle(initialValue = StudyFlowLoadState.Loading)
    val studyCycleState by studyCycleFlow.collectAsStateWithLifecycle(initialValue = StudyFlowLoadState.Loading)
    val examNoticeRevisionsState by examNoticeRevisionsFlow.collectAsStateWithLifecycle(
        initialValue = StudyFlowLoadState.Loading,
    )
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
                targetState,
                mockExamsState,
                legislationState,
                studyCycleState,
                examNoticeRevisionsState,
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
    val readyTarget = targetState.readyValue()
    val readyMockExams = mockExamsState.readyValue()
    val readyLegislation = legislationState.readyValue()
    val readyStudyCycle = studyCycleState.readyValue()
    val readyExamNoticeRevisions = examNoticeRevisionsState.readyValue()
    val currentStudyPhase = remember(readySchedule.strategy?.configurationJson) {
        StudyPhasePlanCodec.decode(readySchedule.strategy?.configurationJson.orEmpty()).activeOn(LocalDate.now())
    }
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
    val priorityRecommendations = remember(
        plan,
        readySyllabus,
        readyReviewItems,
        readyErrorEntries,
        readySchedule,
        readySessions,
        readyStudyCycle,
    ) {
        if (plan.type != StudyType.PUBLIC_EXAMINATION) {
            emptyList()
        } else {
            buildPublicExamPriorityRecommendations(
                plan = plan,
                syllabus = readySyllabus,
                reviewItems = readyReviewItems,
                errorEntries = readyErrorEntries,
                schedule = readySchedule,
                sessions = readySessions,
                studyCycle = readyStudyCycle,
                target = readyTarget,
                nowEpochMillis = System.currentTimeMillis(),
            )
        }
    }

    val preEditalCockpit = remember(plan, readyTarget, readyExamNoticeRevisions) {
        PublicExamPreEditalBuilder.build(
            plan = plan,
            target = readyTarget,
            revisions = readyExamNoticeRevisions,
            nowEpochMillis = System.currentTimeMillis(),
        )?.takeIf { snapshot -> snapshot.preEdital }
    }

    val publicExamDashboard = remember(
        plan,
        readyTarget,
        readySyllabus,
        readyQuestions,
        readyReviewItems,
        readyErrorEntries,
        readySessions,
        readyMockExams,
    ) {
        if (plan.type == StudyType.PUBLIC_EXAMINATION) {
            PublicExamDashboardBuilder.build(
                plan = plan,
                target = readyTarget,
                syllabus = readySyllabus,
                questions = readyQuestions,
                reviews = readyReviewItems,
                errors = readyErrorEntries,
                sessions = readySessions,
                mockExams = readyMockExams,
                nowEpochMillis = System.currentTimeMillis(),
            )
        } else {
            null
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        WorkspaceHeader(
            title = stringResource(R.string.plan_title),
            plan = plan,
            onSwitchPlan = onSwitchPlan,
            onSearch = onSearch,
            onSystem = onSystem,
        )
        StudyFlowTabStrip(
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
                priorityRecommendations = priorityRecommendations,
                publicExamDashboard = publicExamDashboard,
                preEditalCockpit = preEditalCockpit,
                currentStudyPhase = currentStudyPhase,
                onPrioritySelected = onManageContent,
                onManageStudyPhases = onManageSchedule,
                onReviewPreEditalAssumptions = onManageContent,
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
                planId = plan.id,
                legislation = readyLegislation,
                questions = readyQuestions,
                errorEntries = readyErrorEntries,
                reviewItems = readyReviewItems,
                publicExamManager = publicExamManager,
                showLegislation = plan.type == StudyType.PUBLIC_EXAMINATION,
            )
        }
    }
}

@Composable
private fun PlanOverviewPane(
    plan: StudyPlan,
    scheduleCount: Int,
    subjectCount: Int,
    materialCount: Int,
    priorityRecommendations: List<PublicExamPriorityRecommendation>,
    publicExamDashboard: PublicExamDashboardSnapshot?,
    preEditalCockpit: PublicExamPreEditalSnapshot?,
    currentStudyPhase: StudyPhaseDefinition?,
    onPrioritySelected: () -> Unit,
    onManageStudyPhases: () -> Unit,
    onReviewPreEditalAssumptions: () -> Unit,
    onEditPlan: () -> Unit,
    onPlanSettings: () -> Unit,
    onSessions: () -> Unit,
    onReminders: () -> Unit,
    onPortability: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = workspaceContentPadding(bottom = 84.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item(key = "section:progress", contentType = "section") {
            StudyFlowSectionTitle(title = stringResource(R.string.plan_progress))
        }
        item(key = "plan-metrics", contentType = "metrics") {
            StudyFlowMetricStrip(
                firstLabel = stringResource(R.string.plan_progress),
                firstValue = stringResource(R.string.percent_value, plan.progressPercent),
                secondLabel = stringResource(R.string.weekly_goal),
                secondValue = stringResource(R.string.hours_value, plan.weeklyGoalHours),
                secondSupportingText = stringResource(R.string.minutes_value, plan.studiedMinutesThisWeek),
            )
        }
        item(key = "section:structure", contentType = "section") {
            StudyFlowSectionTitle(title = stringResource(R.string.plan_overview))
        }
        item(key = "plan-structure", contentType = "summary") {
            StudyFlowPlanSummary(
                description = plan.description.ifBlank { plan.name },
                examLabel = stringResource(planPrimaryDateLabel(plan)),
                examDate = plan.examDateIso ?: stringResource(R.string.not_set),
                subjectsLabel = stringResource(planContentLabel(plan)),
                subjectCount = pluralStringResource(R.plurals.subjects_count, subjectCount, subjectCount),
                scheduleLabel = stringResource(R.string.plan_schedule),
                scheduleCount = scheduleCount.toString(),
                libraryLabel = stringResource(planLibraryLabel(plan)),
                materialCount = pluralStringResource(R.plurals.materials_count, materialCount, materialCount),
            )
        }
        currentStudyPhase?.let { phase ->
            item(key = "section:study-phase", contentType = "section") {
                StudyFlowSectionTitle(title = stringResource(R.string.study_phase_title))
            }
            item(key = "study-phase", contentType = "study-phase") {
                StudyFlowTimelineItem(
                    title = stringResource(phase.kind.labelKey()),
                    metadata = stringResource(
                        R.string.study_phase_summary,
                        phase.theoryRatioPercent,
                        phase.questionRatioPercent,
                        phase.reviewRatioPercent,
                    ),
                    icon = Icons.Outlined.AutoFixHigh,
                    status = phase.questionGoal?.let { goal ->
                        stringResource(R.string.study_phase_question_goal, goal)
                    },
                    actionLabel = stringResource(R.string.manage_schedule),
                    onClick = onManageStudyPhases,
                )
            }
        }
        preEditalCockpit?.let { cockpit ->
            item(key = "section:pre-edital-cockpit", contentType = "section") {
                StudyFlowSectionTitle(title = stringResource(R.string.pre_edital_cockpit_title))
            }
            item(key = "pre-edital-cockpit", contentType = "notice") {
                PublicExamPreEditalCockpit(
                    snapshot = cockpit,
                    onReviewAssumptions = onReviewPreEditalAssumptions,
                )
            }
        }
        publicExamDashboard?.let { dashboard ->
            item(key = "section:public-exam-dashboard", contentType = "section") {
                StudyFlowSectionTitle(title = stringResource(R.string.public_exam_dashboard_title))
            }
            item(key = "public-exam-dashboard-metrics", contentType = "dashboard") {
                PublicExamDashboardSummary(dashboard)
            }
        }
        if (priorityRecommendations.isNotEmpty()) {
            item(key = "section:public-exam-priority", contentType = "section") {
                StudyFlowSectionTitle(title = stringResource(R.string.public_exam_priority_title))
            }
            item(key = "public-exam-priority-notice", contentType = "notice") {
                Text(
                    text = stringResource(R.string.public_exam_priority_preview_notice),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(
                items = priorityRecommendations,
                key = { recommendation ->
                    recommendation.evidence.topicId?.value ?: recommendation.evidence.subjectId.value
                },
                contentType = { "public-exam-priority" },
            ) { recommendation ->
                val evidence = recommendation.evidence
                val title = evidence.topicName?.let { topic -> "${evidence.subjectName} — $topic" }
                    ?: evidence.subjectName
                val reasons = priorityReasonSummary(recommendation.reasonCodes)
                StudyFlowTimelineItem(
                    title = title,
                    metadata = reasons,
                    icon = Icons.Outlined.AutoFixHigh,
                    status = stringResource(
                        R.string.public_exam_priority_score,
                        (recommendation.score * 100.0).toInt(),
                    ),
                    actionLabel = stringResource(R.string.public_exam_priority_review),
                    onClick = onPrioritySelected,
                )
            }
        }
        item(key = "section:actions", contentType = "section") {
            StudyFlowSectionTitle(title = stringResource(R.string.plan_actions))
        }
        item(key = "action-edit", contentType = "action-row") {
            StudyFlowActionRow(Icons.Outlined.Edit, stringResource(R.string.edit_plan), onEditPlan)
        }
        item(key = "action-settings", contentType = "action-row") {
            StudyFlowActionRow(Icons.Outlined.Settings, stringResource(R.string.plan_settings), onPlanSettings)
        }
        item(key = "action-sessions", contentType = "action-row") {
            StudyFlowActionRow(Icons.Outlined.PlayCircle, stringResource(R.string.study_sessions), onSessions)
        }
        item(key = "action-reminders", contentType = "action-row") {
            StudyFlowActionRow(Icons.Outlined.Notifications, stringResource(R.string.reminders), onReminders)
        }
        item(key = "action-portability", contentType = "action-row") {
            StudyFlowActionRow(Icons.Outlined.Backup, stringResource(R.string.backup_restore), onPortability)
        }
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
        item(key = "section:schedule", contentType = "section") {
            StudyFlowSectionTitle(
                title = stringResource(R.string.plan_schedule),
                actionLabel = stringResource(R.string.manage_schedule),
                onAction = onManage,
            )
        }
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
                StudyFlowTimelineItem(
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
        item(key = "section:content-health", contentType = "section") {
            StudyFlowSectionTitle(title = stringResource(R.string.content_health_title))
        }
        item(key = "content-health-summary", contentType = "coach") {
            val currentReport = cleanupReport
            StudyFlowInsightPanel(
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
            StudyFlowTimelineItem(
                title = stringResource(issue.kind.titleRes()),
                metadata = stringResource(issue.kind.messageRes()),
                icon = Icons.Outlined.AutoFixHigh,
                status = stringResource(issue.severity.labelRes()),
                actionLabel = stringResource(R.string.preview_cleanup_action),
                onClick = onManage,
            )
        }

        item(key = "section:content", contentType = "section") {
            StudyFlowSectionTitle(
                title = stringResource(R.string.plan_content),
                actionLabel = stringResource(R.string.manage_content),
                onAction = onManage,
            )
        }
        item(key = "content-counts", contentType = "metrics") {
            StudyFlowMetric(
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
                            StudyFlowTimelineItem(
                                title = topic.name,
                                metadata = stringResource(R.string.topic_mastery, topic.masteryPercent),
                                icon = Icons.Outlined.AutoStories,
                                status = stringResource(R.string.percent_value, topic.progressPercent),
                                onClick = onManage,
                            )
                        }
                        if (subjectTopics.size > 6) {
                            StudyFlowActionRow(
                                icon = Icons.Outlined.AutoStories,
                                label = pluralStringResource(
                                    R.plurals.view_all_topics,
                                    subjectTopics.size,
                                    subjectTopics.size,
                                ),
                                onClick = onManage,
                            )
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
    planId: com.mikeyphw.studyflow.core.model.StudyPlanId,
    legislation: com.mikeyphw.studyflow.core.model.LegislationStudySnapshot,
    questions: List<com.mikeyphw.studyflow.core.model.Question>,
    errorEntries: List<com.mikeyphw.studyflow.core.model.ErrorEntry>,
    reviewItems: List<com.mikeyphw.studyflow.core.model.ReviewItem>,
    publicExamManager: ManagePublicExam,
    showLegislation: Boolean,
) {
    val rows = (materials.map { (id, title) -> Triple(id, R.string.material_type, title) } +
        videos.map { (id, title) -> Triple(id, R.string.video_type, title) })
        .sortedByDescending { (id, _, _) -> id == targetId }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = workspaceContentPadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (showLegislation) {
            item(key = "public-exam-legislation", contentType = "legislation") {
                PublicExamLegislationPanel(
                    planId = planId,
                    snapshot = legislation,
                    manager = publicExamManager,
                    questions = questions,
                    errorEntries = errorEntries,
                    reviewItems = reviewItems,
                )
            }
        }
        item(key = "section:library", contentType = "section") {
            StudyFlowSectionTitle(
                title = stringResource(R.string.plan_library),
                actionLabel = stringResource(R.string.manage_library),
                onAction = onManage,
            )
        }
        item(key = "library-counts", contentType = "metrics") {
            StudyFlowMetric(
                label = stringResource(R.string.plan_library),
                value = pluralStringResource(R.plurals.materials_count, materials.size, materials.size),
                supportingText = pluralStringResource(R.plurals.videos_count, videos.size, videos.size) +
                    " · " + pluralStringResource(R.plurals.annexes_count, annexCount, annexCount),
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
                StudyFlowTimelineItem(
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

private fun planPrimaryDateLabel(plan: StudyPlan): Int = when (plan.contentMode) {
    StudyContentMode.PROGRAMMING_COURSE -> R.string.programming_target_date
    else -> R.string.exam_date
}

private fun planContentLabel(plan: StudyPlan): Int = when (plan.contentMode) {
    StudyContentMode.PROGRAMMING_COURSE -> R.string.programming_modules
    else -> R.string.plan_content
}

private fun planLibraryLabel(plan: StudyPlan): Int = when (plan.contentMode) {
    StudyContentMode.PROGRAMMING_COURSE -> R.string.programming_resources
    else -> R.string.plan_library
}


private fun buildPublicExamPriorityRecommendations(
    plan: StudyPlan,
    syllabus: StudySyllabus?,
    reviewItems: List<ReviewItem>,
    errorEntries: List<ErrorEntry>,
    schedule: StudySchedule,
    sessions: List<com.mikeyphw.studyflow.core.model.StudySession>,
    studyCycle: com.mikeyphw.studyflow.core.model.StudyCycleSnapshot?,
    target: StudyTarget?,
    nowEpochMillis: Long,
): List<PublicExamPriorityRecommendation> {
    val source = syllabus ?: return emptyList()
    val activeSession = sessions.firstOrNull { session ->
        session.status == com.mikeyphw.studyflow.core.model.StudySessionStatus.RUNNING ||
            session.status == com.mikeyphw.studyflow.core.model.StudySessionStatus.PAUSED
    }
    val weekAgo = nowEpochMillis - 7L * 24L * 60L * 60L * 1_000L
    val accuracyWindowStart = nowEpochMillis - 30L * 24L * 60L * 60L * 1_000L
    val cycleItems = studyCycle?.orderedItems.orEmpty().filter { it.enabled }
    val cyclePointerIndex = studyCycle?.cycle?.currentItemId?.let { pointer ->
        cycleItems.indexOfFirst { it.id == pointer }.takeIf { it >= 0 }
    } ?: 0
    val daysUntilExam = PublicExamDateResolver.daysUntilOfficial(
        plan = plan,
        target = target,
        nowEpochMillis = nowEpochMillis,
        zoneId = java.time.ZoneId.systemDefault(),
    )
    val subjectById = source.subjects.associateBy { it.id }
    val evidence = source.topics.filterNot { it.archived }.mapNotNull { topic ->
        val subject = subjectById[topic.subjectId]?.takeUnless { it.archived } ?: return@mapNotNull null
        val topicSessions = sessions.filter { session ->
            session.status == com.mikeyphw.studyflow.core.model.StudySessionStatus.COMPLETED &&
                session.contributesToTopicEvidence(topic.id, subject.id)
        }
        val minutesLast7Days = topicSessions.filter { it.startedAtEpochMillis >= weekAgo }.sumOf { session ->
            ((session.grossDurationSeconds - session.pauseDurationSeconds).coerceAtLeast(0L) / 60L).toInt()
        }
        val lastStudy = topicSessions.maxOfOrNull { it.endedAtEpochMillis ?: it.startedAtEpochMillis }
        val recentPractice = topicSessions.filter { it.startedAtEpochMillis >= accuracyWindowStart }
        val recentAnswered = recentPractice.sumOf { it.questionsAnswered }
        val recentCorrect = recentPractice.sumOf { it.correctAnswers }
        val cycleIndex = cycleItems.indexOfFirst { item ->
            item.topicId == topic.id || (item.topicId == null && item.subjectId == subject.id)
        }
        val cycleDistance = cycleIndex.takeIf { it >= 0 }?.let { index ->
            if (cycleItems.isEmpty()) null else (index - cyclePointerIndex + cycleItems.size) % cycleItems.size
        }
        PublicExamPriorityEvidence(
            studyPlanId = plan.id,
            subjectId = subject.id,
            topicId = topic.id,
            subjectName = subject.name,
            topicName = topic.name,
            examWeight = subject.examWeight,
            examIncidencePercent = topic.examIncidencePercent,
            masteryPercent = topic.masteryPercent,
            recentAccuracyPercent = if (recentAnswered > 0) recentCorrect * 100 / recentAnswered else null,
            recurringErrorCount = errorEntries.filter { it.studyPlanId == plan.id && it.topicId == topic.id }
                .sumOf { it.recurrenceCount },
            overdueReviewCount = reviewItems.count { review ->
                review.studyPlanId == plan.id && review.topicId == topic.id &&
                    review.status != com.mikeyphw.studyflow.core.model.ReviewStatus.COMPLETED &&
                    review.status != com.mikeyphw.studyflow.core.model.ReviewStatus.CANCELLED &&
                    review.dueAtEpochMillis <= nowEpochMillis
            },
            missedScheduleCount = schedule.plannedActivities.count { activity ->
                activity.studyPlanId == plan.id &&
                    (activity.topicId == topic.id || (activity.topicId == null && activity.subjectId == subject.id)) &&
                    activity.status == PlannedActivityStatus.MISSED
            },
            daysUntilExam = daysUntilExam,
            cycleDistance = cycleDistance,
            minutesStudiedLast7Days = minutesLast7Days,
            hoursSinceLastStudy = lastStudy?.let { ((nowEpochMillis - it).coerceAtLeast(0L) / 3_600_000L).toInt() },
            lockedByActiveSession = activeSession?.contributesToTopicEvidence(topic.id, subject.id) == true,
        )
    }
    return PublicExamPriorityPlanner.rank(plan.id, evidence, limit = 5)
}

@Composable
private fun priorityReasonSummary(reasons: List<PublicExamPriorityReasonCode>): String {
    val labels = mutableListOf<String>()
    for (reason in reasons.take(3)) {
        labels += stringResource(priorityReasonLabel(reason))
    }
    return labels.joinToString(" · ")
}

private fun priorityReasonLabel(reason: PublicExamPriorityReasonCode): Int = when (reason) {
    PublicExamPriorityReasonCode.HIGH_EXAM_WEIGHT -> R.string.priority_reason_weight
    PublicExamPriorityReasonCode.HIGH_INCIDENCE -> R.string.priority_reason_incidence
    PublicExamPriorityReasonCode.LOW_MASTERY -> R.string.priority_reason_mastery
    PublicExamPriorityReasonCode.LOW_ACCURACY -> R.string.priority_reason_accuracy
    PublicExamPriorityReasonCode.RECURRING_ERRORS -> R.string.priority_reason_errors
    PublicExamPriorityReasonCode.OVERDUE_REVIEW -> R.string.priority_reason_review
    PublicExamPriorityReasonCode.MISSED_SCHEDULE -> R.string.priority_reason_missed
    PublicExamPriorityReasonCode.EXAM_NEAR -> R.string.priority_reason_exam_near
    PublicExamPriorityReasonCode.CYCLE_POSITION -> R.string.priority_reason_cycle
    PublicExamPriorityReasonCode.COOLDOWN -> R.string.priority_reason_cooldown
    PublicExamPriorityReasonCode.STARVATION_RELIEF -> R.string.priority_reason_starvation
}


@Composable
private fun PublicExamPreEditalCockpit(
    snapshot: PublicExamPreEditalSnapshot,
    onReviewAssumptions: () -> Unit,
) {
    val reference = snapshot.referenceLabel ?: stringResource(R.string.pre_edital_reference_not_set)
    val message = when {
        PublicExamPreEditalIssue.STALE_REFERENCE_NOTICE in snapshot.issues -> stringResource(
            R.string.pre_edital_cockpit_stale_message,
            reference,
            snapshot.referenceAgeDays ?: 0L,
            snapshot.verificationCount,
        )
        PublicExamPreEditalIssue.REFERENCE_PUBLICATION_DATE_UNKNOWN in snapshot.issues -> stringResource(
            R.string.pre_edital_cockpit_unknown_date_message,
            reference,
            snapshot.verificationCount,
        )
        PublicExamPreEditalIssue.NO_REFERENCE_NOTICE in snapshot.issues -> stringResource(
            R.string.pre_edital_cockpit_missing_reference_message,
            snapshot.verificationCount,
        )
        else -> stringResource(
            R.string.pre_edital_cockpit_message,
            reference,
            snapshot.verificationCount,
        )
    }
    StudyFlowInsightPanel(
        title = stringResource(R.string.pre_edital_cockpit_status),
        message = message,
        actionLabel = stringResource(R.string.pre_edital_review_assumptions),
        onAction = onReviewAssumptions,
    )
}

@Composable
private fun PublicExamDashboardSummary(snapshot: PublicExamDashboardSnapshot) {
    val metrics = snapshot.metrics
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StudyFlowMetricStrip(
            firstLabel = stringResource(R.string.edital_coverage),
            firstValue = metrics.editalCoveragePercent.percentOrUnknown(),
            secondLabel = stringResource(R.string.weighted_mastery),
            secondValue = metrics.weightedMasteryPercent.percentOrUnknown(),
        )
        Text(stringResource(R.string.dashboard_questions_total, metrics.totalQuestions))
        Text(stringResource(R.string.dashboard_accuracy_windows,
            metrics.accuracy7d.percentOrUnknown(), metrics.accuracy30d.percentOrUnknown(),
            metrics.accuracy90d.percentOrUnknown()))
        Text(stringResource(R.string.dashboard_speed,
            metrics.questionsPerHour30d.numberOrUnknown(), metrics.averageSecondsPerQuestion30d.numberOrUnknown()))
        Text(stringResource(R.string.dashboard_backlog, metrics.overdueReviews, metrics.recurringErrors))
        Text(stringResource(R.string.dashboard_mock_cutoff,
            metrics.latestMockScorePercent.percentOrUnknown(),
            metrics.estimatedCutoffGapPercent.signedPercentOrUnknown(),
        ))
        Text(
            stringResource(
                R.string.dashboard_days_to_exam,
                metrics.daysUntilExam?.toString() ?: stringResource(R.string.unknown),
            ),
        )
        StudyFlowSectionTitle(title = stringResource(R.string.edital_matrix_title))
        snapshot.matrix.take(12).forEach { row ->
            Text(
                stringResource(
                    R.string.edital_matrix_row,
                    row.label,
                    row.examWeight?.let { "%.2f".format(it) } ?: stringResource(R.string.unknown),
                    row.incidencePercent?.let { "$it%" } ?: stringResource(R.string.unknown),
                    row.questionsAnswered,
                    row.accuracyPercent.percentOrUnknown(),
                    row.masteryPercent,
                    stringResource(row.status.workspaceLabelRes()),
                    row.overdueReviewCount,
                    row.recurringErrorCount,
                    (row.priorityScore * 100).toInt(),
                ),
            )
        }
        ImportanceMasteryMatrix(snapshot.matrix)
        Text(
            stringResource(R.string.importance_mastery_matrix_hint),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ImportanceMasteryMatrix(rows: List<com.mikeyphw.studyflow.core.domain.PublicExamEditalMatrixRow>) {
    val knownWeights = rows.mapNotNull { it.examWeight }
    val midpoint = knownWeights.maxOrNull()?.div(2.0) ?: Double.POSITIVE_INFINITY
    val buckets = rows.groupBy { row ->
        val important = (row.examWeight ?: 0.0) >= midpoint || (row.incidencePercent ?: 0) >= 60
        val mastered = row.masteryPercent >= 70
        important to mastered
    }
    StudyFlowSectionTitle(title = stringResource(R.string.importance_mastery_matrix_title))
    BoxWithConstraints {
        val narrow = maxWidth < 480.dp
        if (narrow) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ImportanceMasteryCell(
                    stringResource(R.string.matrix_high_importance_low_mastery),
                    buckets[true to false].orEmpty(),
                    Modifier.fillMaxWidth(),
                )
                ImportanceMasteryCell(
                    stringResource(R.string.matrix_high_importance_high_mastery),
                    buckets[true to true].orEmpty(),
                    Modifier.fillMaxWidth(),
                )
                ImportanceMasteryCell(
                    stringResource(R.string.matrix_low_importance_low_mastery),
                    buckets[false to false].orEmpty(),
                    Modifier.fillMaxWidth(),
                )
                ImportanceMasteryCell(
                    stringResource(R.string.matrix_low_importance_high_mastery),
                    buckets[false to true].orEmpty(),
                    Modifier.fillMaxWidth(),
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ImportanceMasteryCell(
                        stringResource(R.string.matrix_high_importance_low_mastery),
                        buckets[true to false].orEmpty(),
                        Modifier.weight(1f),
                    )
                    ImportanceMasteryCell(
                        stringResource(R.string.matrix_high_importance_high_mastery),
                        buckets[true to true].orEmpty(),
                        Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ImportanceMasteryCell(
                        stringResource(R.string.matrix_low_importance_low_mastery),
                        buckets[false to false].orEmpty(),
                        Modifier.weight(1f),
                    )
                    ImportanceMasteryCell(
                        stringResource(R.string.matrix_low_importance_high_mastery),
                        buckets[false to true].orEmpty(),
                        Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ImportanceMasteryCell(
    title: String,
    rows: List<com.mikeyphw.studyflow.core.domain.PublicExamEditalMatrixRow>,
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.ElevatedCard(modifier = modifier) {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium)
            if (rows.isEmpty()) {
                Text(stringResource(R.string.none), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                rows.take(3).forEach { Text(it.label, style = MaterialTheme.typography.bodySmall) }
                if (rows.size > 3) Text("+${rows.size - 3}", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private fun com.mikeyphw.studyflow.core.model.ContentProgressStatus.workspaceLabelRes(): Int = when (this) {
    com.mikeyphw.studyflow.core.model.ContentProgressStatus.NOT_STARTED -> R.string.status_not_started
    com.mikeyphw.studyflow.core.model.ContentProgressStatus.STUDYING -> R.string.status_studying
    com.mikeyphw.studyflow.core.model.ContentProgressStatus.REVIEWING -> R.string.status_reviewing
    com.mikeyphw.studyflow.core.model.ContentProgressStatus.MASTERED -> R.string.status_mastered
    com.mikeyphw.studyflow.core.model.ContentProgressStatus.ARCHIVED -> R.string.status_archived
}

@Composable
private fun Double?.percentOrUnknown(): String = this?.let { "%.1f%%".format(it) } ?: stringResource(R.string.unknown)

@Composable
private fun Double?.signedPercentOrUnknown(): String =
    this?.let { "%+.1f%%".format(it) } ?: stringResource(R.string.unknown)

@Composable
private fun Double?.numberOrUnknown(): String = this?.let { "%.1f".format(it) } ?: stringResource(R.string.unknown)

private fun StudyPhaseKind.labelKey(): Int = when (this) {
    StudyPhaseKind.FOUNDATION -> R.string.study_phase_foundation
    StudyPhaseKind.EXPANSION -> R.string.study_phase_expansion
    StudyPhaseKind.CONSOLIDATION -> R.string.study_phase_consolidation
    StudyPhaseKind.QUESTIONS_REVIEWS -> R.string.study_phase_questions_reviews
}
