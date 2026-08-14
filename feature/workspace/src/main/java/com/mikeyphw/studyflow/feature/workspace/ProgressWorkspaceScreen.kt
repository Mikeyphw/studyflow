package com.mikeyphw.studyflow.feature.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mikeyphw.studyflow.core.designsystem.StudyFlowPrimaryActionCard
import com.mikeyphw.studyflow.core.designsystem.StudyFlowCoachCard
import com.mikeyphw.studyflow.core.designsystem.StudyFlowEmptyState
import com.mikeyphw.studyflow.core.designsystem.StudyFlowMetricPulse
import com.mikeyphw.studyflow.core.designsystem.StudyFlowResponsivePair
import com.mikeyphw.studyflow.core.designsystem.StudyFlowTimelineRow
import com.mikeyphw.studyflow.core.designsystem.studyFlowSectionHeader
import com.mikeyphw.studyflow.core.designsystem.workspaceContentPadding
import com.mikeyphw.studyflow.core.domain.ManageAdaptiveReschedule
import com.mikeyphw.studyflow.core.domain.ManageStudyAnalytics
import com.mikeyphw.studyflow.core.domain.ManageStudyPlan
import com.mikeyphw.studyflow.core.domain.ManageStudyContent
import com.mikeyphw.studyflow.core.domain.ManageStudyLibrary
import com.mikeyphw.studyflow.core.domain.ManageStudyPractice
import com.mikeyphw.studyflow.core.domain.ManageStudyProgress
import com.mikeyphw.studyflow.core.domain.ManageStudyReview
import com.mikeyphw.studyflow.core.domain.ManageStudySchedule
import com.mikeyphw.studyflow.core.domain.ManageStudySession
import com.mikeyphw.studyflow.core.domain.asStudyFlowLoadState
import com.mikeyphw.studyflow.core.domain.StudyCoachWeeklyReviewBuilder
import com.mikeyphw.studyflow.core.domain.AdaptiveReschedulePlanner
import com.mikeyphw.studyflow.core.domain.ExamReadinessEngine
import com.mikeyphw.studyflow.core.domain.StudyContentCleanupEngine
import com.mikeyphw.studyflow.core.domain.StudyContentCleanupInput
import com.mikeyphw.studyflow.core.domain.StudyExamReportBuilder
import com.mikeyphw.studyflow.core.domain.StudyExamReportInput
import com.mikeyphw.studyflow.core.domain.StudyExamReportNextActionKind
import com.mikeyphw.studyflow.core.domain.StudyExamReportSnapshot
import com.mikeyphw.studyflow.core.domain.StudyCoachWeeklyReviewInput
import com.mikeyphw.studyflow.core.domain.StudyCoachWeeklyReviewItemKind
import com.mikeyphw.studyflow.core.domain.StudyCoachWeeklyReviewTone
import com.mikeyphw.studyflow.core.domain.StudyEvidenceEvent
import com.mikeyphw.studyflow.core.domain.StudyEvidenceEventKind
import com.mikeyphw.studyflow.core.domain.StudyEvidenceTimelineBuilder
import com.mikeyphw.studyflow.core.domain.StudyEvidenceTimelineInput
import com.mikeyphw.studyflow.core.domain.StudySessionTimer
import com.mikeyphw.studyflow.core.domain.tickingClockFlow
import com.mikeyphw.studyflow.core.model.AdaptiveRescheduleChangeType
import com.mikeyphw.studyflow.core.model.ExamReadinessBand
import com.mikeyphw.studyflow.core.model.ReadinessSignalKind
import com.mikeyphw.studyflow.core.model.ReviewItem
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudySchedule
import com.mikeyphw.studyflow.core.model.StudyFlowLoadState
import com.mikeyphw.studyflow.core.model.StudySessionStatus
import com.mikeyphw.studyflow.core.navigation.ProgressWorkspacePeriod
import java.text.DateFormat
import java.time.ZoneId
import java.util.Date
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Composable
fun ProgressWorkspaceScreen(
    plan: StudyPlan,
    selectedPeriod: ProgressWorkspacePeriod,
    targetId: String? = null,
    analyticsManager: ManageStudyAnalytics,
    planManager: ManageStudyPlan,
    progressManager: ManageStudyProgress,
    contentManager: ManageStudyContent,
    libraryManager: ManageStudyLibrary,
    adaptiveRescheduleManager: ManageAdaptiveReschedule,
    sessionManager: ManageStudySession,
    scheduleManager: ManageStudySchedule,
    reviewManager: ManageStudyReview,
    practiceManager: ManageStudyPractice,
    onPeriodSelected: (ProgressWorkspacePeriod) -> Unit,
    onSwitchPlan: () -> Unit,
    onSearch: () -> Unit,
    onSystem: () -> Unit,
    onOpenProgressCenter: () -> Unit,
    onOpenAnalytics: () -> Unit,
    onOpenPlan: () -> Unit,
    onOpenContent: () -> Unit,
    onOpenReviews: () -> Unit,
    onExportExamReport: suspend (StudyExamReportDocument, StudyExamReportFormat, String) -> Boolean,
    modifier: Modifier = Modifier,
) {
    var loadAttempt by remember(plan.id) { mutableIntStateOf(0) }
    val analyticsFlow = remember(plan.id, loadAttempt) {
        analyticsManager.observeSnapshot(plan.id).asStudyFlowLoadState()
    }
    val progressFlow = remember(plan.id, loadAttempt) {
        progressManager.observeSnapshot(plan.id).asStudyFlowLoadState()
    }
    val syllabusFlow = remember(plan.id, loadAttempt) {
        contentManager.observeSyllabus(plan.id).asStudyFlowLoadState()
    }
    val libraryFlow = remember(plan.id, loadAttempt) {
        libraryManager.observeSnapshot(plan.id).asStudyFlowLoadState()
    }
    val sessionsFlow = remember(plan.id, loadAttempt) {
        sessionManager.observeSessions(plan.id).asStudyFlowLoadState()
    }
    val scheduleFlow = remember(plan.id, loadAttempt) {
        scheduleManager.observeSchedule(plan.id).asStudyFlowLoadState()
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
    val questionsFlow = remember(plan.id, loadAttempt) {
        practiceManager.observeQuestions(plan.id).asStudyFlowLoadState()
    }
    val testsFlow = remember(plan.id, loadAttempt) {
        practiceManager.observeTests(plan.id).asStudyFlowLoadState()
    }
    val mockExamsFlow = remember(plan.id, loadAttempt) {
        practiceManager.observeMockExams(plan.id).asStudyFlowLoadState()
    }
    val planSettingsFlow = remember(plan.id, loadAttempt) {
        planManager.observeSettings(plan.id)
            .map { settings -> requireNotNull(
                settings) { "Study Plan settings must be persisted before progress analysis." } }
            .asStudyFlowLoadState()
    }
    val analyticsState by analyticsFlow.collectAsStateWithLifecycle(initialValue = StudyFlowLoadState.Loading)
    val progressState by progressFlow.collectAsStateWithLifecycle(initialValue = StudyFlowLoadState.Loading)
    val syllabusState by syllabusFlow.collectAsStateWithLifecycle(initialValue = StudyFlowLoadState.Loading)
    val libraryState by libraryFlow.collectAsStateWithLifecycle(initialValue = StudyFlowLoadState.Loading)
    val sessionsState by sessionsFlow.collectAsStateWithLifecycle(initialValue = StudyFlowLoadState.Loading)
    val scheduleState by scheduleFlow.collectAsStateWithLifecycle(initialValue = StudyFlowLoadState.Loading)
    val reviewItemsState by reviewItemsFlow.collectAsStateWithLifecycle(initialValue = StudyFlowLoadState.Loading)
    val flashcardsState by flashcardsFlow.collectAsStateWithLifecycle(initialValue = StudyFlowLoadState.Loading)
    val errorEntriesState by errorEntriesFlow.collectAsStateWithLifecycle(initialValue = StudyFlowLoadState.Loading)
    val questionsState by questionsFlow.collectAsStateWithLifecycle(initialValue = StudyFlowLoadState.Loading)
    val testsState by testsFlow.collectAsStateWithLifecycle(initialValue = StudyFlowLoadState.Loading)
    val mockExamsState by mockExamsFlow.collectAsStateWithLifecycle(initialValue = StudyFlowLoadState.Loading)
    val planSettingsState by planSettingsFlow.collectAsStateWithLifecycle(initialValue = StudyFlowLoadState.Loading)
    if (
        !WorkspaceLoadGate(
            states = listOf(
                analyticsState,
                progressState,
                syllabusState,
                libraryState,
                sessionsState,
                scheduleState,
                reviewItemsState,
                flashcardsState,
                errorEntriesState,
                questionsState,
                testsState,
                mockExamsState,
                planSettingsState,
            ),
            onRetry = { loadAttempt += 1 },
            modifier = modifier,
        )
    ) return
    val analytics = analyticsState.readyValue()
    val progress = progressState.readyValue()
    val syllabus = syllabusState.readyValue()
    val library = libraryState.readyValue()
    val sessions = sessionsState.readyValue()
    val schedule = scheduleState.readyValue()
    val reviewItems = reviewItemsState.readyValue()
    val flashcards = flashcardsState.readyValue()
    val errorEntries = errorEntriesState.readyValue()
    val questions = questionsState.readyValue()
    val tests = testsState.readyValue()
    val mockExams = mockExamsState.readyValue()
    val planSettings = planSettingsState.readyValue()
    val targetNote = progress?.notes?.firstOrNull { note -> note.id.value == targetId }
    val coroutineScope = rememberCoroutineScope()
    var appliedRescheduleChanges by rememberSaveable(plan.id.value) { mutableStateOf<Int?>(null) }
    var skippedRescheduleChanges by rememberSaveable(plan.id.value) { mutableStateOf<Int?>(null) }
    var rescheduleApplyFailed by rememberSaveable(plan.id.value) { mutableStateOf(false) }
    var reportExportResult by rememberSaveable(plan.id.value) { mutableStateOf<Boolean?>(null) }
    val wallClockFlow = remember(plan.id) { tickingClockFlow() }
    val nowEpochMillis by wallClockFlow.collectAsStateWithLifecycle(initialValue = System.currentTimeMillis())
    val readiness = remember(plan, analytics, schedule, mockExams, nowEpochMillis) {
        ExamReadinessEngine.calculate(
            plan = plan,
            analytics = analytics,
            schedule = schedule,
            mockExams = mockExams,
            nowEpochMillis = nowEpochMillis,
            zoneId = ZoneId.systemDefault(),
        )
    }
    val reschedulePreview = remember(plan, analytics, schedule, readiness, planSettings, nowEpochMillis) {
        if (planSettings.adaptivePlanningEnabled) {
            AdaptiveReschedulePlanner.preview(
                plan = plan,
                analytics = analytics,
                schedule = schedule,
                readinessScorePercent = readiness.scorePercent,
                nowEpochMillis = nowEpochMillis,
            )
        } else {
            AdaptiveReschedulePlanner.disabledPreview(
                plan = plan,
                analytics = analytics,
                schedule = schedule,
                readinessScorePercent = readiness.scorePercent,
                nowEpochMillis = nowEpochMillis,
            )
        }
    }
    val cutoff = nowEpochMillis - selectedPeriod.days * 86_400_000L
    val periodSessions = sessions.filter { session ->
        session.startedAtEpochMillis >= cutoff && session.status == StudySessionStatus.COMPLETED
    }
    val studiedSeconds = periodSessions.sumOf { session ->
        StudySessionTimer.activeSeconds(session, session.endedAtEpochMillis ?: nowEpochMillis)
    }
    val answered = periodSessions.sumOf { it.questionsAnswered }
    val correct = periodSessions.sumOf { it.correctAnswers }
    val periodAccuracy = if (answered == 0) null else ((correct.toDouble() / answered) * 100).toInt()
    val halfway = cutoff + (selectedPeriod.days * 86_400_000L / 2L)
    val earlierSessions = periodSessions.filter { it.startedAtEpochMillis < halfway }
    val recentSessions = periodSessions.filter { it.startedAtEpochMillis >= halfway }
    val earlierMinutes = earlierSessions.sumOf { session ->
        StudySessionTimer.activeSeconds(session, session.endedAtEpochMillis ?: nowEpochMillis)
    } / 60
    val recentMinutes = recentSessions.sumOf { session ->
        StudySessionTimer.activeSeconds(session, session.endedAtEpochMillis ?: nowEpochMillis)
    } / 60
    val trendLabel = trendLabel(recentMinutes, earlierMinutes)
    val allEvidence = remember(schedule, sessions, reviewItems, questions, tests, mockExams, progress, nowEpochMillis) {
        StudyEvidenceTimelineBuilder.buildAll(
            StudyEvidenceTimelineInput(
                studyPlanId = plan.id,
                plannedActivities = schedule.plannedActivities,
                sessions = sessions,
                reviewItems = reviewItems,
                questions = questions,
                tests = tests,
                mockExams = mockExams,
                goals = progress?.let { snapshot -> snapshot.activeGoals + snapshot.completedGoals }.orEmpty(),
                notes = progress?.notes.orEmpty(),
                nowEpochMillis = nowEpochMillis,
            ),
        )
    }
    val evidenceTimeline = remember(allEvidence) { allEvidence.take(12) }
    val weeklyReview = remember(plan, analytics, readiness, reschedulePreview, allEvidence, nowEpochMillis) {
        StudyCoachWeeklyReviewBuilder.build(
            StudyCoachWeeklyReviewInput(
                plan = plan,
                analytics = analytics,
                readiness = readiness,
                reschedulePreview = reschedulePreview,
                evidence = allEvidence,
                generatedAtEpochMillis = nowEpochMillis,
            ),
        )
    }
    val cleanupReport = remember(
        plan,
        syllabus,
        library,
        schedule,
        questions,
        reviewItems,
        flashcards,
        errorEntries,
        progress,
        nowEpochMillis,
    ) {
        val currentSyllabus = syllabus
        val currentLibrary = library
        if (currentSyllabus == null || currentLibrary == null) {
            null
        } else {
            StudyContentCleanupEngine.inspect(
                StudyContentCleanupInput(
                    plan = plan,
                    syllabus = currentSyllabus,
                    questions = questions,
                    materials = currentLibrary.materials,
                    videos = currentLibrary.videos,
                    annexes = currentLibrary.annexes,
                    reviewItems = reviewItems,
                    flashcards = flashcards,
                    errorEntries = errorEntries,
                    plannedActivities = schedule.plannedActivities,
                    studySessions = sessions,
                    notes = progress?.notes.orEmpty(),
                    nowEpochMillis = nowEpochMillis,
                ),
            )
        }
    }
    val examReportSnapshot = if (cleanupReport != null && analytics != null && readiness != null) {
        StudyExamReportBuilder.build(
            StudyExamReportInput(
                plan = plan,
                analytics = analytics,
                readiness = readiness,
                weeklyReview = weeklyReview,
                evidence = evidenceTimeline,
                totalEvidenceCount = allEvidence.size,
                requiredInputsReady = true,
                schedule = schedule,
                reviewItems = reviewItems,
                questions = questions,
                tests = tests,
                mockExams = mockExams,
                reschedulePreview = reschedulePreview,
                cleanupReport = cleanupReport,
                generatedAtEpochMillis = nowEpochMillis,
            ),
        )
    } else {
        null
    }
    val reportDocument = examReportSnapshot?.let { snapshot -> localizedExamReportDocument(snapshot) }
    val reportChooserTitle = stringResource(R.string.exam_report_share_chooser)
    val launchReportExport: (StudyExamReportFormat) -> Unit = { format ->
        val currentDocument = reportDocument
        if (currentDocument != null) {
            coroutineScope.launch {
                try {
                    reportExportResult = onExportExamReport(currentDocument, format, reportChooserTitle)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: RuntimeException) {
                    reportExportResult = false
                }
            }
        }
    }

    Scaffold(modifier = modifier) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            WorkspaceHeader(
                title = stringResource(R.string.progress_title),
                plan = plan,
                onSwitchPlan = onSwitchPlan,
                onSearch = onSearch,
                onSystem = onSystem,
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ProgressWorkspacePeriod.entries.forEach { period ->
                    FilterChip(
                        selected = period == selectedPeriod,
                        modifier = Modifier.heightIn(min = 48.dp),
                        onClick = { onPeriodSelected(period) },
                        label = {
                            Text(
                                stringResource(
                                    when (period) {
                                        ProgressWorkspacePeriod.SEVEN_DAYS -> R.string.progress_7_days
                                        ProgressWorkspacePeriod.THIRTY_DAYS -> R.string.progress_30_days
                                        ProgressWorkspacePeriod.NINETY_DAYS -> R.string.progress_90_days
                                    },
                                ),
                            )
                        },
                        leadingIcon = if (period == selectedPeriod) {
                            {
                                androidx.compose.material3.Icon(
                                    Icons.AutoMirrored.Outlined.TrendingUp,
                                    contentDescription = null,
                                )
                            }
                        } else {
                            null
                        },
                    )
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = workspaceContentPadding(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                targetNote?.let { note ->
                    item(key = "search-target:${note.id.value}", contentType = "search-target") {
                        StudyFlowPrimaryActionCard(
                            supportingLabel = stringResource(R.string.notes),
                            title = note.title,
                            message = note.content.take(180),
                            actionLabel = stringResource(R.string.open_progress_center),
                            onAction = onOpenProgressCenter,
                        )
                    }
                }
                studyFlowSectionHeader(key = "readiness", title = { stringResource(R.string.readiness_score) })
                item(key = "readiness-card", contentType = "readiness") {
                    val currentReadiness = readiness
                    StudyFlowPrimaryActionCard(
                        title = currentReadiness?.let { score ->
                            stringResource(R.string.percent_value, score.scorePercent)
                        } ?: stringResource(R.string.not_enough_data),
                        message = currentReadiness?.let { score ->
                            stringResource(readinessBandMessage(score.band))
                        } ?: stringResource(R.string.readiness_waiting_message),
                        supportingLabel = currentReadiness?.band?.let { band ->
                            stringResource(readinessBandLabel(band))
                        } ?: stringResource(R.string.readiness_score),
                        actionLabel = stringResource(R.string.open_analytics),
                        onAction = onOpenAnalytics,
                        secondaryLabel = stringResource(R.string.preview_reschedule),
                        onSecondaryAction = onOpenPlan,
                    )
                }
                item(key = "readiness-metrics", contentType = "metrics") {
                    StudyFlowResponsivePair(
                        first = { childModifier ->
                            StudyFlowMetricPulse(
                                label = stringResource(R.string.readiness_coverage),
                                value = readiness?.let { score ->
                                    stringResource(R.string.percent_value, score.coveragePercent)
                                } ?: stringResource(R.string.not_enough_data),
                                modifier = childModifier,
                            )
                        },
                        second = { childModifier ->
                            StudyFlowMetricPulse(
                                label = stringResource(R.string.readiness_time_feasibility),
                                value = readiness?.let { score ->
                                    stringResource(R.string.percent_value, score.timeFeasibilityPercent)
                                } ?: stringResource(R.string.not_enough_data),
                                supportingText = readiness?.daysUntilExam?.let { days ->
                                    pluralStringResource(R.plurals.days_value, days, days)
                                },
                                modifier = childModifier,
                            )
                        },
                    )
                }
                items(
                    items = readiness?.signals.orEmpty(),
                    key = { signal -> "readiness:${signal.kind}" },
                    contentType = { "readiness-signal" },
                ) { signal ->
                    StudyFlowTimelineRow(
                        title = stringResource(readinessSignalLabel(signal.kind)),
                        metadata = stringResource(readinessSignalMessage(signal.kind)),
                        icon = Icons.Outlined.CheckCircle,
                        status = stringResource(R.string.percent_value, signal.scorePercent),
                        onClick = onOpenAnalytics,
                    )
                }

                studyFlowSectionHeader(key = "weekly-coach", title = { stringResource(R.string.weekly_coach_review) })
                item(key = "weekly-coach-summary", contentType = "coach") {
                    StudyFlowCoachCard(
                        title = stringResource(R.string.weekly_coach_title, weeklyReview.studyPlanName),
                        message = pluralStringResource(
                            R.plurals.weekly_coach_summary,
                            weeklyReview.evidenceCount,
                            weeklyReview.evidenceCount,
                            weeklyReview.studiedMinutes,
                        ),
                        actionLabel = stringResource(R.string.open_analytics),
                        onAction = onOpenAnalytics,
                    )
                }
                items(
                    items = weeklyReview.items,
                    key = { item -> "weekly-coach:${item.kind}" },
                    contentType = { "weekly-coach-item" },
                ) { item ->
                    StudyFlowTimelineRow(
                        title = stringResource(item.kind.titleRes()),
                        metadata = stringResource(item.kind.messageRes()),
                        icon = when (item.tone) {
                            StudyCoachWeeklyReviewTone.WIN -> Icons.Outlined.CheckCircle
                            StudyCoachWeeklyReviewTone.WATCH -> Icons.Outlined.Insights
                            StudyCoachWeeklyReviewTone.ACTION -> Icons.Outlined.AutoFixHigh
                        },
                        status = item.scorePercent?.let { score ->
                            stringResource(R.string.percent_value, score)
                        } ?: item.count?.let { count ->
                            pluralStringResource(R.plurals.items_count, count, count)
                        },
                        onClick = when (item.kind.destination()) {
                            WeeklyCoachDestination.ANALYTICS -> onOpenAnalytics
                            WeeklyCoachDestination.PLAN -> onOpenPlan
                            WeeklyCoachDestination.PROGRESS -> onOpenProgressCenter
                            WeeklyCoachDestination.REVIEWS -> onOpenReviews
                        },
                    )
                }

                studyFlowSectionHeader(
                    key = "content-health",
                    title = { stringResource(R.string.content_health_title) },
                )
                item(key = "content-health-card", contentType = "coach") {
                    val currentCleanup = cleanupReport
                    StudyFlowCoachCard(
                        title = currentCleanup?.let { report ->
                            stringResource(R.string.percent_value, report.healthScorePercent)
                        } ?: stringResource(R.string.not_enough_data),
                        message = when {
                            currentCleanup == null -> stringResource(R.string.content_health_waiting)
                            currentCleanup.issueCount == 0 -> stringResource(R.string.content_health_clean_message)
                            else -> pluralStringResource(
                                R.plurals.content_health_summary,
                                currentCleanup.issueCount,
                                currentCleanup.issueCount,
                                currentCleanup.actionCount,
                            )
                        },
                        actionLabel = stringResource(R.string.review_cleanup),
                        onAction = onOpenContent,
                    )
                }

                studyFlowSectionHeader(
                    key = "exam-report",
                    title = { stringResource(R.string.exam_report_export_title) },
                )
                item(key = "exam-report-card", contentType = "report-export") {
                    StudyFlowCoachCard(
                        title = stringResource(R.string.exam_report_title, plan.name),
                        message = stringResource(R.string.exam_report_export_message),
                        actionLabel = stringResource(R.string.open_progress_center),
                        onAction = onOpenProgressCenter,
                    )
                }
                item(key = "exam-report-actions", contentType = "actions") {
                    StudyFlowResponsivePair(
                        first = { childModifier ->
                            androidx.compose.material3.Button(
                                onClick = { launchReportExport(StudyExamReportFormat.HTML) },
                                enabled = reportDocument != null,
                                modifier = childModifier,
                            ) {
                                Text(stringResource(R.string.export_report_html))
                            }
                        },
                        second = { childModifier ->
                            androidx.compose.material3.OutlinedButton(
                                onClick = { launchReportExport(StudyExamReportFormat.PDF) },
                                enabled = reportDocument != null,
                                modifier = childModifier,
                            ) {
                                Text(stringResource(R.string.export_report_pdf))
                            }
                        },
                    )
                }
                reportExportResult?.let { exported ->
                    item(key = "exam-report-result", contentType = "report-result") {
                        Text(
                            text = stringResource(
                                if (exported) {
                                    R.string.exam_report_export_success
                                } else {
                                    R.string.exam_report_export_failed
                                },
                            ),
                        )
                    }
                }

                studyFlowSectionHeader(
                    key = "adaptive-reschedule",
                    title = { stringResource(R.string.adaptive_reschedule) },
                )
                item(key = "adaptive-summary", contentType = "coach") {
                    StudyFlowCoachCard(
                        title = stringResource(R.string.reschedule_preview),
                        message = reschedulePreview?.let { preview ->
                            stringResource(
                                rescheduleSummary(
                                    types = preview.changes.map { it.type },
                                    readinessScorePercent = preview.readinessScorePercent,
                                ),
                            )
                        } ?: stringResource(R.string.readiness_waiting_message),
                        actionLabel = stringResource(R.string.apply_safe_reschedule),
                        onAction = {
                            coroutineScope.launch {
                                try {
                                    val approvedPreview = requireNotNull(reschedulePreview) {
                                        "Adaptive preview is still loading. Refresh before applying."
                                    }
                                    val result = adaptiveRescheduleManager.applySafePreview(plan, approvedPreview)
                                    appliedRescheduleChanges = result.appliedChanges
                                    skippedRescheduleChanges = result.skippedChanges
                                    rescheduleApplyFailed = false
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: RuntimeException) {
                                    appliedRescheduleChanges = null
                                    skippedRescheduleChanges = null
                                    rescheduleApplyFailed = true
                                }
                            }
                        },
                    )
                }
                if (appliedRescheduleChanges != null || rescheduleApplyFailed) {
                    item(key = "adaptive-apply-feedback", contentType = "coach") {
                        StudyFlowCoachCard(
                            title = stringResource(R.string.reschedule_apply_result),
                            message = if (rescheduleApplyFailed) {
                                stringResource(R.string.reschedule_apply_failed)
                            } else {
                                pluralStringResource(
                                    R.plurals.reschedule_apply_success,
                                    appliedRescheduleChanges ?: 0,
                                    appliedRescheduleChanges ?: 0,
                                    skippedRescheduleChanges ?: 0,
                                )
                            },
                            actionLabel = stringResource(R.string.open_plan),
                            onAction = onOpenPlan,
                        )
                    }
                }
                items(
                    items = reschedulePreview?.changes.orEmpty(),
                    key = { change -> "reschedule:${change.id}" },
                    contentType = { "reschedule-change" },
                ) { change ->
                    StudyFlowTimelineRow(
                        title = if (change.plannedActivityId == null) {
                            stringResource(rescheduleChangeLabel(change.type))
                        } else {
                            change.title
                        },
                        metadata = stringResource(rescheduleChangeReason(change.type)),
                        icon = Icons.Outlined.AutoFixHigh,
                        status = stringResource(rescheduleChangeLabel(change.type)),
                        actionLabel = if (change.executable) {
                            stringResource(R.string.safe_change)
                        } else {
                            stringResource(R.string.preview_only)
                        },
                        onAction = onOpenPlan,
                    )
                }
                studyFlowSectionHeader(key = "period", title = { stringResource(R.string.progress_title) })
                item(key = "period-metrics", contentType = "metrics") {
                    StudyFlowResponsivePair(
                        first = { childModifier ->
                            StudyFlowMetricPulse(
                                label = stringResource(R.string.studied_time),
                                value = stringResource(R.string.minutes_value, (studiedSeconds / 60).toInt()),
                                supportingText = pluralStringResource(
                                    R.plurals.sessions_value,
                                    periodSessions.size,
                                    periodSessions.size,
                                ),
                                modifier = childModifier,
                            )
                        },
                        second = { childModifier ->
                            StudyFlowMetricPulse(
                                label = stringResource(R.string.accuracy),
                                value = periodAccuracy?.let { value ->
                                    stringResource(R.string.percent_value, value)
                                } ?: stringResource(R.string.not_enough_data),
                                supportingText = if (answered > 0) {
                                    pluralStringResource(R.plurals.answered_value, answered, answered)
                                } else {
                                    null
                                },
                                modifier = childModifier,
                            )
                        },
                    )
                }
                item(key = "quality-metrics", contentType = "metrics") {
                    StudyFlowResponsivePair(
                        first = { childModifier ->
                            StudyFlowMetricPulse(
                                label = stringResource(R.string.plan_schedule),
                                value = analytics?.let { snapshot ->
                                    stringResource(R.string.percent_value, snapshot.scheduleAdherencePercent)
                                } ?: stringResource(R.string.not_enough_data),
                                modifier = childModifier,
                            )
                        },
                        second = { childModifier ->
                            val goalPercent = progress?.goalProgressPercent ?: analytics?.goalCompletionPercent
                            StudyFlowMetricPulse(
                                label = stringResource(R.string.goal),
                                value = goalPercent?.let { value ->
                                    stringResource(R.string.percent_value, value)
                                } ?: stringResource(R.string.not_enough_data),
                                modifier = childModifier,
                            )
                        },
                    )
                }
                item(key = "period-trend", contentType = "trend") {
                    StudyFlowTimelineRow(
                        title = stringResource(R.string.period_trend),
                        metadata = stringResource(
                            R.string.period_trend_detail,
                            earlierMinutes.toInt(),
                            recentMinutes.toInt(),
                        ),
                        icon = Icons.AutoMirrored.Outlined.TrendingUp,
                        status = stringResource(trendLabel),
                        onClick = onOpenAnalytics,
                    )
                }

                studyFlowSectionHeader(key = "weak", title = { stringResource(R.string.weak_areas) })
                if (analytics?.weakAreas.isNullOrEmpty()) {
                    item(key = "weak-empty", contentType = "empty") {
                        StudyFlowEmptyState(
                            title = stringResource(R.string.weak_areas),
                            message = stringResource(R.string.no_weak_areas),
                            actionLabel = stringResource(R.string.open_analytics),
                            onAction = onOpenAnalytics,
                        )
                    }
                } else {
                    items(
                        items = analytics?.weakAreas.orEmpty(),
                        key = { area -> area.label },
                        contentType = { "weak-area" },
                    ) { area ->
                        StudyFlowTimelineRow(
                            title = area.label,
                            metadata = stringResource(R.string.percent_value, area.masteryPercent),
                            icon = Icons.Outlined.Insights,
                            status = stringResource(R.string.percent_value, area.progressPercent),
                            onClick = onOpenAnalytics,
                        )
                    }
                }

                studyFlowSectionHeader(key = "goals", title = { stringResource(R.string.goals_and_notes) })
                item(key = "goal-note-metrics", contentType = "metrics") {
                    StudyFlowResponsivePair(
                        first = { childModifier ->
                            StudyFlowMetricPulse(
                                label = pluralStringResource(
                                    R.plurals.active_goals_value,
                                    progress?.activeGoals?.size ?: 0,
                                    progress?.activeGoals?.size ?: 0,
                                ),
                                value = progress?.goalProgressPercent?.let { value ->
                                    stringResource(R.string.percent_value, value)
                                } ?: stringResource(R.string.not_enough_data),
                                modifier = childModifier,
                            )
                        },
                        second = { childModifier ->
                            StudyFlowMetricPulse(
                                label = pluralStringResource(
                                    R.plurals.notes_value,
                                    progress?.notes?.size ?: 0,
                                    progress?.notes?.size ?: 0,
                                ),
                                value = (progress?.pinnedNotes?.size ?: 0).toString(),
                                supportingText = stringResource(R.string.open_progress_center),
                                modifier = childModifier,
                            )
                        },
                    )
                }
                item(key = "adaptation", contentType = "coach") {
                    val message = when {
                        (analytics?.overdueReviewCount ?: 0) > 0 -> {
                            stringResource(R.string.adaptation_backlog)
                        }
                        periodAccuracy != null && periodAccuracy < 60 -> {
                            stringResource(R.string.adaptation_accuracy)
                        }
                        else -> stringResource(R.string.adaptation_steady)
                    }
                    StudyFlowCoachCard(
                        title = stringResource(R.string.adaptation_preview),
                        message = message,
                        actionLabel = stringResource(R.string.adjust_plan),
                        onAction = onOpenPlan,
                    )
                }
                items(
                    items = adaptationRows(
                        overdueReviews = analytics?.overdueReviewCount ?: 0,
                        periodAccuracy = periodAccuracy,
                        weakAreas = analytics?.weakAreas.orEmpty().map { it.label },
                    ),
                    key = { row -> "adapt:${row.titleRes}" },
                    contentType = { "adaptation-step" },
                ) { row ->
                    StudyFlowTimelineRow(
                        title = stringResource(row.titleRes),
                        metadata = stringResource(row.messageRes),
                        icon = Icons.Outlined.AutoFixHigh,
                        actionLabel = stringResource(row.actionRes),
                        onAction = when (row.destination) {
                            AdaptationDestination.ANALYTICS -> onOpenAnalytics
                            AdaptationDestination.PLAN -> onOpenPlan
                            AdaptationDestination.PROGRESS -> onOpenProgressCenter
                            AdaptationDestination.REVIEWS -> onOpenReviews
                        },
                    )
                }
                studyFlowSectionHeader(key = "evidence", title = { stringResource(R.string.evidence_timeline) })
                if (evidenceTimeline.isEmpty()) {
                    item(key = "evidence-empty", contentType = "empty") {
                        StudyFlowEmptyState(
                            title = stringResource(R.string.evidence_timeline),
                            message = stringResource(R.string.evidence_empty),
                            actionLabel = stringResource(R.string.open_progress_center),
                            onAction = onOpenProgressCenter,
                        )
                    }
                } else {
                    items(
                        items = evidenceTimeline,
                        key = { event -> "evidence:${event.id}" },
                        contentType = { "evidence-event" },
                    ) { event ->
                        StudyFlowTimelineRow(
                            title = stringResource(event.kind.titleRes()),
                            metadata = eventMetadata(event),
                            icon = event.kind.icon(),
                            status = eventStatus(event),
                            onClick = onOpenProgressCenter,
                        )
                    }
                }

                item(key = "progress-actions", contentType = "actions") {
                    StudyFlowResponsivePair(
                        first = { childModifier ->
                            androidx.compose.material3.Button(
                                onClick = onOpenProgressCenter,
                                modifier = childModifier,
                            ) {
                                androidx.compose.material3.Icon(Icons.Outlined.Flag, contentDescription = null)
                                Text(stringResource(R.string.open_progress_center))
                            }
                        },
                        second = { childModifier ->
                            androidx.compose.material3.OutlinedButton(
                                onClick = onOpenAnalytics,
                                modifier = childModifier,
                            ) {
                                Text(stringResource(R.string.open_analytics))
                            }
                        },
                    )
                }
            }
        }
    }
}


@Composable
private fun eventMetadata(event: StudyEvidenceEvent): String {
    val label = event.label.takeIf(String::isNotBlank) ?: stringResource(R.string.evidence_no_label)
    val minutes = event.minutes
    val count = event.count
    return when {
        minutes != null && count != null -> {
            pluralStringResource(R.plurals.evidence_minutes_and_count, count, label, minutes, count)
        }
        minutes != null -> stringResource(R.string.evidence_minutes, label, minutes)
        count != null -> pluralStringResource(R.plurals.evidence_count, count, label, count)
        else -> label
    }
}

@Composable
private fun eventStatus(event: StudyEvidenceEvent): String? = event.percent?.let { percent ->
    stringResource(R.string.percent_value, percent)
}

private fun StudyEvidenceEventKind.titleRes(): Int = when (this) {
    StudyEvidenceEventKind.SESSION_COMPLETED -> R.string.evidence_session_completed
    StudyEvidenceEventKind.SESSION_CANCELLED -> R.string.evidence_session_cancelled
    StudyEvidenceEventKind.PLANNED_ACTIVITY_COMPLETED -> R.string.evidence_activity_completed
    StudyEvidenceEventKind.PLANNED_ACTIVITY_MISSED -> R.string.evidence_activity_missed
    StudyEvidenceEventKind.REVIEW_DUE -> R.string.evidence_review_due
    StudyEvidenceEventKind.REVIEW_COMPLETED -> R.string.evidence_review_completed
    StudyEvidenceEventKind.QUESTION_ADDED -> R.string.evidence_question_added
    StudyEvidenceEventKind.PRACTICE_TEST_CREATED -> R.string.evidence_test_created
    StudyEvidenceEventKind.PRACTICE_TEST_SUBMITTED -> R.string.evidence_test_submitted
    StudyEvidenceEventKind.MOCK_EXAM_CREATED -> R.string.evidence_mock_created
    StudyEvidenceEventKind.MOCK_EXAM_SUBMITTED -> R.string.evidence_mock_submitted
    StudyEvidenceEventKind.GOAL_COMPLETED -> R.string.evidence_goal_completed
    StudyEvidenceEventKind.NOTE_PINNED -> R.string.evidence_note_pinned
}

private fun StudyEvidenceEventKind.icon() = when (this) {
    StudyEvidenceEventKind.REVIEW_DUE,
    StudyEvidenceEventKind.PLANNED_ACTIVITY_MISSED -> Icons.Outlined.Notifications
    else -> Icons.Outlined.History
}


private enum class WeeklyCoachDestination {
    ANALYTICS,
    PLAN,
    PROGRESS,
    REVIEWS,
}

private fun StudyCoachWeeklyReviewItemKind.titleRes(): Int = when (this) {
    StudyCoachWeeklyReviewItemKind.READINESS_STABLE -> R.string.weekly_coach_readiness_stable
    StudyCoachWeeklyReviewItemKind.READINESS_RISK -> R.string.weekly_coach_readiness_risk
    StudyCoachWeeklyReviewItemKind.REVIEW_BACKLOG -> R.string.weekly_coach_review_backlog
    StudyCoachWeeklyReviewItemKind.PRACTICE_ACCURACY -> R.string.weekly_coach_practice_accuracy
    StudyCoachWeeklyReviewItemKind.SCHEDULE_DRIFT -> R.string.weekly_coach_schedule_drift
    StudyCoachWeeklyReviewItemKind.MOCK_EXAM_SIGNAL -> R.string.weekly_coach_mock_exam_signal
    StudyCoachWeeklyReviewItemKind.CONSISTENCY_WIN -> R.string.weekly_coach_consistency_win
    StudyCoachWeeklyReviewItemKind.EVIDENCE_EMPTY -> R.string.weekly_coach_evidence_empty
    StudyCoachWeeklyReviewItemKind.RESCHEDULE_READY -> R.string.weekly_coach_reschedule_ready
}

private fun StudyCoachWeeklyReviewItemKind.messageRes(): Int = when (this) {
    StudyCoachWeeklyReviewItemKind.READINESS_STABLE -> R.string.weekly_coach_readiness_stable_message
    StudyCoachWeeklyReviewItemKind.READINESS_RISK -> R.string.weekly_coach_readiness_risk_message
    StudyCoachWeeklyReviewItemKind.REVIEW_BACKLOG -> R.string.weekly_coach_review_backlog_message
    StudyCoachWeeklyReviewItemKind.PRACTICE_ACCURACY -> R.string.weekly_coach_practice_accuracy_message
    StudyCoachWeeklyReviewItemKind.SCHEDULE_DRIFT -> R.string.weekly_coach_schedule_drift_message
    StudyCoachWeeklyReviewItemKind.MOCK_EXAM_SIGNAL -> R.string.weekly_coach_mock_exam_signal_message
    StudyCoachWeeklyReviewItemKind.CONSISTENCY_WIN -> R.string.weekly_coach_consistency_win_message
    StudyCoachWeeklyReviewItemKind.EVIDENCE_EMPTY -> R.string.weekly_coach_evidence_empty_message
    StudyCoachWeeklyReviewItemKind.RESCHEDULE_READY -> R.string.weekly_coach_reschedule_ready_message
}

private fun StudyCoachWeeklyReviewItemKind.destination(): WeeklyCoachDestination = when (this) {
    StudyCoachWeeklyReviewItemKind.REVIEW_BACKLOG -> WeeklyCoachDestination.REVIEWS
    StudyCoachWeeklyReviewItemKind.RESCHEDULE_READY,
    StudyCoachWeeklyReviewItemKind.SCHEDULE_DRIFT -> WeeklyCoachDestination.PLAN
    StudyCoachWeeklyReviewItemKind.EVIDENCE_EMPTY -> WeeklyCoachDestination.PROGRESS
    else -> WeeklyCoachDestination.ANALYTICS
}

private fun readinessBandLabel(band: ExamReadinessBand): Int = when (band) {
    ExamReadinessBand.ON_TRACK -> R.string.readiness_on_track
    ExamReadinessBand.WATCH -> R.string.readiness_watch
    ExamReadinessBand.AT_RISK -> R.string.readiness_at_risk
    ExamReadinessBand.CRITICAL -> R.string.readiness_critical
}

private fun readinessBandMessage(band: ExamReadinessBand): Int = when (band) {
    ExamReadinessBand.ON_TRACK -> R.string.readiness_on_track_message
    ExamReadinessBand.WATCH -> R.string.readiness_watch_message
    ExamReadinessBand.AT_RISK -> R.string.readiness_at_risk_message
    ExamReadinessBand.CRITICAL -> R.string.readiness_critical_message
}

private fun rescheduleChangeLabel(type: AdaptiveRescheduleChangeType): Int = when (type) {
    AdaptiveRescheduleChangeType.REDISTRIBUTE_MISSED -> R.string.reschedule_redistribute_missed
    AdaptiveRescheduleChangeType.PROTECT_REVIEWS -> R.string.reschedule_protect_reviews
    AdaptiveRescheduleChangeType.REDUCE_OVERLOAD -> R.string.reschedule_reduce_overload
    AdaptiveRescheduleChangeType.ADD_THEORY_BEFORE_QUESTIONS -> R.string.reschedule_theory_before_questions
    AdaptiveRescheduleChangeType.KEEP_STEADY -> R.string.reschedule_keep_steady
}

private fun readinessSignalLabel(kind: ReadinessSignalKind): Int = when (kind) {
    ReadinessSignalKind.COVERAGE -> R.string.readiness_signal_coverage
    ReadinessSignalKind.RETENTION -> R.string.readiness_signal_retention
    ReadinessSignalKind.PRACTICE -> R.string.readiness_signal_practice
    ReadinessSignalKind.MOCK_EXAM -> R.string.readiness_signal_mock_exam
    ReadinessSignalKind.CONSISTENCY -> R.string.readiness_signal_consistency
    ReadinessSignalKind.SCHEDULE -> R.string.readiness_signal_schedule
    ReadinessSignalKind.TIME_LEFT -> R.string.readiness_signal_time_left
    ReadinessSignalKind.WEAK_AREAS -> R.string.readiness_signal_weak_areas
}

private fun readinessSignalMessage(kind: ReadinessSignalKind): Int = when (kind) {
    ReadinessSignalKind.COVERAGE -> R.string.readiness_signal_coverage_message
    ReadinessSignalKind.RETENTION -> R.string.readiness_signal_retention_message
    ReadinessSignalKind.PRACTICE -> R.string.readiness_signal_practice_message
    ReadinessSignalKind.MOCK_EXAM -> R.string.readiness_signal_mock_exam_message
    ReadinessSignalKind.CONSISTENCY -> R.string.readiness_signal_consistency_message
    ReadinessSignalKind.SCHEDULE -> R.string.readiness_signal_schedule_message
    ReadinessSignalKind.TIME_LEFT -> R.string.readiness_signal_time_left_message
    ReadinessSignalKind.WEAK_AREAS -> R.string.readiness_signal_weak_areas_message
}

private fun rescheduleChangeReason(type: AdaptiveRescheduleChangeType): Int = when (type) {
    AdaptiveRescheduleChangeType.REDISTRIBUTE_MISSED -> R.string.reschedule_reason_redistribute_missed
    AdaptiveRescheduleChangeType.PROTECT_REVIEWS -> R.string.reschedule_reason_protect_reviews
    AdaptiveRescheduleChangeType.REDUCE_OVERLOAD -> R.string.reschedule_reason_reduce_overload
    AdaptiveRescheduleChangeType.ADD_THEORY_BEFORE_QUESTIONS -> R.string.reschedule_reason_theory_before_questions
    AdaptiveRescheduleChangeType.KEEP_STEADY -> R.string.reschedule_reason_keep_steady
}

private fun rescheduleSummary(types: List<AdaptiveRescheduleChangeType>, readinessScorePercent: Int): Int = when {
    types.any { it == AdaptiveRescheduleChangeType.REDISTRIBUTE_MISSED } -> R.string.reschedule_summary_recover_missed
    types.any { it == AdaptiveRescheduleChangeType.PROTECT_REVIEWS } -> R.string.reschedule_summary_protect_reviews
    readinessScorePercent < 65 -> R.string.reschedule_summary_risky
    else -> R.string.reschedule_summary_stable
}

private fun trendLabel(recentMinutes: Long, earlierMinutes: Long): Int = when {
    recentMinutes >= earlierMinutes + 30L -> R.string.trend_up
    recentMinutes + 30L < earlierMinutes -> R.string.trend_down
    else -> R.string.trend_flat
}

private enum class AdaptationDestination {
    ANALYTICS,
    PLAN,
    PROGRESS,
    REVIEWS,
}

private data class AdaptationRow(
    val titleRes: Int,
    val messageRes: Int,
    val actionRes: Int,
    val destination: AdaptationDestination,
)

private fun adaptationRows(
    overdueReviews: Int,
    periodAccuracy: Int?,
    weakAreas: List<String>,
): List<AdaptationRow> = buildList {
    if (overdueReviews > 0) {
        add(
            AdaptationRow(
                titleRes = R.string.adaptation_step_reviews,
                messageRes = R.string.adaptation_step_reviews_message,
                actionRes = R.string.review_now,
                destination = AdaptationDestination.REVIEWS,
            ),
        )
    }
    if (periodAccuracy != null && periodAccuracy < 60) {
        add(
            AdaptationRow(
                titleRes = R.string.adaptation_step_theory,
                messageRes = R.string.adaptation_step_theory_message,
                actionRes = R.string.adjust_plan,
                destination = AdaptationDestination.PLAN,
            ),
        )
    }
    if (weakAreas.isNotEmpty()) {
        add(
            AdaptationRow(
                titleRes = R.string.adaptation_step_weak_area,
                messageRes = R.string.adaptation_step_weak_area_message,
                actionRes = R.string.open_analytics,
                destination = AdaptationDestination.ANALYTICS,
            ),
        )
    }
    if (isEmpty()) {
        add(
            AdaptationRow(
                titleRes = R.string.adaptation_step_steady,
                messageRes = R.string.adaptation_step_steady_message,
                actionRes = R.string.open_progress_center,
                destination = AdaptationDestination.PROGRESS,
            ),
        )
    }
}

@Composable
private fun localizedExamReportDocument(snapshot: StudyExamReportSnapshot): StudyExamReportDocument {
    val readinessRows = snapshot.readinessSignals.map { signal ->
        StudyExamReportRow(
            label = stringResource(readinessSignalLabel(signal.kind)),
            value = stringResource(R.string.percent_value, signal.scorePercent),
        )
    }
    val coachRows = snapshot.weeklyCoachItems.map { item ->
        StudyExamReportRow(
            label = stringResource(item.kind.titleRes()),
            value = item.scorePercent?.let { score ->
                stringResource(R.string.percent_value, score)
            } ?: item.count?.let { count ->
                pluralStringResource(R.plurals.items_count, count, count)
            } ?: stringResource(R.string.exam_report_status_recorded),
        )
    }
    val weakAreaRows = snapshot.weakAreas.map { area ->
        StudyExamReportRow(
            label = area.label,
            value = stringResource(
                R.string.exam_report_weak_area_value,
                area.progressPercent,
                area.masteryPercent,
            ),
        )
    }
    val nextActionRows = snapshot.nextActions.map { action ->
        StudyExamReportRow(
            label = stringResource(R.string.exam_report_next_action),
            value = stringResource(action.labelRes()),
        )
    }
    val provenanceRows = snapshot.metricProvenance.map { provenance ->
        StudyExamReportRow(
            label = provenance.metric.replace('_', ' ').replaceFirstChar { char -> char.titlecase() },
            value = provenance.source.name.replace('_', ' ').replaceFirstChar { char -> char.titlecase() },
        )
    }
    return StudyExamReportDocument(
        title = stringResource(R.string.exam_report_title, snapshot.studyPlanName),
        fileStem = "studyflow-exam-report-${snapshot.studyPlanName}",
        generatedAtLabel = stringResource(
            R.string.exam_report_generated_at,
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(
                Date(snapshot.generatedAtEpochMillis),
            ),
        ),
        sections = listOf(
            StudyExamReportSection(
                title = stringResource(R.string.exam_report_overview_section),
                rows = listOf(
                    StudyExamReportRow(
                        stringResource(R.string.readiness_score),
                        snapshot.readinessScorePercent?.let { score ->
                            stringResource(R.string.percent_value, score)
                        } ?: stringResource(R.string.not_enough_data),
                    ),
                    StudyExamReportRow(
                        stringResource(R.string.exam_report_readiness_band),
                        snapshot.readinessBand?.let { band ->
                            stringResource(readinessBandLabel(band))
                        } ?: stringResource(R.string.not_enough_data),
                    ),
                    StudyExamReportRow(
                        stringResource(R.string.exam_report_studied_minutes),
                        snapshot.studiedMinutesLastSevenDays.toString(),
                    ),
                    StudyExamReportRow(
                        stringResource(R.string.exam_report_evidence_count),
                        snapshot.evidenceCount.toString(),
                    ),
                ),
            ),
            StudyExamReportSection(
                title = stringResource(R.string.exam_report_readiness_signals_section),
                rows = readinessRows.ifEmpty {
                    listOf(
                        StudyExamReportRow(
                            stringResource(R.string.readiness_score),
                            stringResource(R.string.not_enough_data),
                        ),
                    )
                },
            ),
            StudyExamReportSection(
                title = stringResource(R.string.weekly_coach_review),
                rows = coachRows.ifEmpty {
                    listOf(
                        StudyExamReportRow(
                            stringResource(R.string.weekly_coach_review),
                            stringResource(R.string.exam_report_status_recorded),
                        ),
                    )
                },
            ),
            StudyExamReportSection(
                title = stringResource(R.string.weak_areas),
                rows = weakAreaRows.ifEmpty {
                    listOf(
                        StudyExamReportRow(
                            stringResource(R.string.weak_areas),
                            stringResource(R.string.exam_report_no_weak_areas),
                        ),
                    )
                },
            ),
            StudyExamReportSection(
                title = stringResource(R.string.exam_report_recovery_section),
                rows = listOf(
                    StudyExamReportRow(
                        stringResource(R.string.exam_report_missed_activities),
                        snapshot.missedActivityCount.toString(),
                    ),
                    StudyExamReportRow(
                        stringResource(R.string.exam_report_due_reviews),
                        snapshot.dueReviewCount.toString(),
                    ),
                    StudyExamReportRow(
                        stringResource(R.string.exam_report_overdue_reviews),
                        snapshot.overdueReviewCount.toString(),
                    ),
                    StudyExamReportRow(
                        stringResource(R.string.exam_report_reschedule_changes),
                        snapshot.executableRescheduleChangeCount.toString(),
                    ),
                ),
            ),
            StudyExamReportSection(
                title = stringResource(R.string.exam_report_practice_section),
                rows = listOf(
                    StudyExamReportRow(
                        stringResource(R.string.exam_report_practice_tests),
                        snapshot.practiceTestCount.toString(),
                    ),
                    StudyExamReportRow(
                        stringResource(R.string.exam_report_completed_tests),
                        snapshot.completedPracticeTestCount.toString(),
                    ),
                    StudyExamReportRow(
                        stringResource(R.string.exam_report_test_accuracy),
                        snapshot.practiceTestAccuracyPercent?.let { score ->
                            stringResource(R.string.percent_value, score)
                        } ?: stringResource(R.string.not_enough_data),
                    ),
                    StudyExamReportRow(
                        stringResource(R.string.exam_report_mock_exams),
                        snapshot.mockExamCount.toString(),
                    ),
                    StudyExamReportRow(
                        stringResource(R.string.exam_report_completed_mocks),
                        snapshot.completedMockExamCount.toString(),
                    ),
                    StudyExamReportRow(
                        stringResource(R.string.exam_report_best_mock_score),
                        snapshot.bestMockScorePercent?.let { score ->
                            stringResource(R.string.percent_value, score)
                        } ?: stringResource(R.string.not_enough_data),
                    ),
                    StudyExamReportRow(
                        stringResource(R.string.exam_report_average_mock_score),
                        snapshot.averageMockScorePercent?.let { score ->
                            stringResource(R.string.percent_value, score)
                        } ?: stringResource(R.string.not_enough_data),
                    ),
                    StudyExamReportRow(
                        stringResource(R.string.exam_report_recent_questions),
                        snapshot.recentQuestionCount.toString(),
                    ),
                ),
            ),
            StudyExamReportSection(
                title = stringResource(R.string.content_health_title),
                rows = listOf(
                    StudyExamReportRow(
                        stringResource(R.string.exam_report_health_score),
                        stringResource(R.string.percent_value, snapshot.cleanupHealthScorePercent),
                    ),
                    StudyExamReportRow(
                        stringResource(R.string.exam_report_cleanup_issues),
                        snapshot.cleanupIssueCount.toString(),
                    ),
                    StudyExamReportRow(
                        stringResource(R.string.exam_report_cleanup_actions),
                        snapshot.cleanupActionCount.toString(),
                    ),
                ),
            ),
            StudyExamReportSection(
                title = stringResource(R.string.exam_report_metric_provenance_section),
                rows = provenanceRows,
            ),
            StudyExamReportSection(
                title = stringResource(R.string.exam_report_next_actions_section),
                rows = nextActionRows,
            ),
        ),
        privacyNotice = stringResource(R.string.exam_report_privacy_notice),
    )
}

private fun StudyExamReportNextActionKind.labelRes(): Int = when (this) {
    StudyExamReportNextActionKind.REVIEW_BACKLOG -> R.string.exam_report_action_review_backlog
    StudyExamReportNextActionKind.RECOVER_SCHEDULE -> R.string.exam_report_action_recover_schedule
    StudyExamReportNextActionKind.PRACTICE_WEAK_AREAS -> R.string.exam_report_action_practice_weak_areas
    StudyExamReportNextActionKind.RUN_MOCK_EXAM -> R.string.exam_report_action_run_mock
    StudyExamReportNextActionKind.REVIEW_CONTENT_CLEANUP -> R.string.exam_report_action_review_cleanup
    StudyExamReportNextActionKind.APPLY_SAFE_RESCHEDULE -> R.string.exam_report_action_apply_reschedule
    StudyExamReportNextActionKind.KEEP_PLAN -> R.string.exam_report_action_keep_plan
}
