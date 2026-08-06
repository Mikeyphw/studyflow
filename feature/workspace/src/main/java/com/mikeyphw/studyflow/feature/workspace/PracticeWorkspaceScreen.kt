package com.mikeyphw.studyflow.feature.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mikeyphw.studyflow.core.designsystem.StudyFlowEmptyState
import com.mikeyphw.studyflow.core.designsystem.StudyFlowMetricPulse
import com.mikeyphw.studyflow.core.designsystem.StudyFlowPrimaryActionCard
import com.mikeyphw.studyflow.core.designsystem.StudyFlowResponsivePair
import com.mikeyphw.studyflow.core.designsystem.StudyFlowTimelineRow
import com.mikeyphw.studyflow.core.designsystem.StudyFlowWorkspaceTabs
import com.mikeyphw.studyflow.core.designsystem.studyFlowSectionHeader
import com.mikeyphw.studyflow.core.designsystem.workspaceContentPadding
import com.mikeyphw.studyflow.core.domain.ManageStudyPractice
import com.mikeyphw.studyflow.core.domain.ManageStudyReview
import com.mikeyphw.studyflow.core.domain.MockExamStudioBlueprint
import com.mikeyphw.studyflow.core.domain.MockExamStudioRequest
import com.mikeyphw.studyflow.core.domain.MockExamStudioWarning
import com.mikeyphw.studyflow.core.domain.RemediationPriority
import com.mikeyphw.studyflow.core.domain.WeaknessDrillPlan
import com.mikeyphw.studyflow.core.domain.WrongAnswerRemediation
import com.mikeyphw.studyflow.core.model.MockExam
import com.mikeyphw.studyflow.core.model.MockExamId
import com.mikeyphw.studyflow.core.model.Question
import com.mikeyphw.studyflow.core.model.ReviewItem
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudyTest
import com.mikeyphw.studyflow.core.model.StudyTestId
import com.mikeyphw.studyflow.core.navigation.PracticeWorkspaceTab
import java.util.UUID
import java.util.concurrent.CancellationException
import kotlinx.coroutines.launch

@Composable
fun PracticeWorkspaceScreen(
    plan: StudyPlan,
    selectedTab: PracticeWorkspaceTab,
    practiceManager: ManageStudyPractice,
    reviewManager: ManageStudyReview,
    onTabSelected: (PracticeWorkspaceTab) -> Unit,
    onSwitchPlan: () -> Unit,
    onSearch: () -> Unit,
    onSystem: () -> Unit,
    onQuickAdd: () -> Unit,
    onOpenReviewCenter: () -> Unit,
    onOpenQuestionBank: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reviewFlow = remember(plan.id) { reviewManager.observeQueue(plan.id) }
    val notebookFlow = remember(plan.id) { reviewManager.observeWrongAnswerNotebook(plan.id) }
    val questionsFlow = remember(plan.id) { practiceManager.observeQuestions(plan.id) }
    val testsFlow = remember(plan.id) { practiceManager.observeTests(plan.id) }
    val mocksFlow = remember(plan.id) { practiceManager.observeMockExams(plan.id) }
    val queue by reviewFlow.collectAsStateWithLifecycle(initialValue = null)
    val notebook by notebookFlow.collectAsStateWithLifecycle(initialValue = null)
    val questions by questionsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val tests by testsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val mocks by mocksFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val weaknessDrill = remember(questions, notebook, queue) {
        practiceManager.buildWeaknessDrill(
            questions = questions,
            remediations = notebook?.entries.orEmpty(),
            dueReviews = queue?.due.orEmpty(),
        )
    }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val operationFailed = stringResource(R.string.operation_failed)
    val weaknessDrillTitle = stringResource(R.string.weakness_drill_title)
    val weaknessDrillCreated = stringResource(R.string.weakness_drill_created)
    val weaknessDrillUnavailable = stringResource(R.string.weakness_drill_unavailable)
    val questionsImported = stringResource(R.string.questions_imported)
    val noNewQuestions = stringResource(R.string.question_import_no_new_questions)
    val mockExamCreated = stringResource(R.string.mock_exam_created)
    val mockStudioDefaultTitle = stringResource(R.string.mock_exam_studio_generated_title, plan.name)
    val mockStudio = remember(questions, mockStudioDefaultTitle) {
        practiceManager.previewMockExamStudio(
            questions = questions,
            request = MockExamStudioRequest(title = mockStudioDefaultTitle),
        )
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(onClick = onQuickAdd) {
                Icon(Icons.Outlined.Quiz, contentDescription = stringResource(R.string.quick_add))
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            WorkspaceHeader(
                title = stringResource(R.string.practice_title),
                plan = plan,
                onSwitchPlan = onSwitchPlan,
                onSearch = onSearch,
                onSystem = onSystem,
            )
            StudyFlowWorkspaceTabs(
                tabs = PracticeWorkspaceTab.entries,
                selected = selectedTab,
                label = { tab ->
                    stringResource(
                        when (tab) {
                            PracticeWorkspaceTab.REVIEWS -> R.string.practice_reviews
                            PracticeWorkspaceTab.QUESTIONS -> R.string.practice_questions
                            PracticeWorkspaceTab.ERRORS -> R.string.practice_errors
                        },
                    )
                },
                onSelected = onTabSelected,
            )
            when (selectedTab) {
                PracticeWorkspaceTab.REVIEWS -> ReviewsPane(
                    due = queue?.due.orEmpty(),
                    dueCount = queue?.dueCount ?: 0,
                    upcomingCount = queue?.upcomingCount ?: 0,
                    flashcardCount = queue?.flashcardCount ?: 0,
                    onGrade = { item, grade ->
                        scope.launch {
                            try {
                                reviewManager.gradeReview(plan.id, item.id, grade)
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Throwable) {
                                snackbar.showSnackbar(operationFailed)
                            }
                        }
                    },
                    onSkip = { item ->
                        scope.launch {
                            try {
                                reviewManager.skipReview(plan.id, item.id)
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Throwable) {
                                snackbar.showSnackbar(operationFailed)
                            }
                        }
                    },
                    onOpenReviewCenter = onOpenReviewCenter,
                )
                PracticeWorkspaceTab.QUESTIONS -> QuestionsPane(
                    questions = questions,
                    tests = tests,
                    mocks = mocks,
                    mockStudio = mockStudio,
                    onOpenQuestionBank = onOpenQuestionBank,
                    onPreviewMockExam = { request ->
                        practiceManager.previewMockExamStudio(questions, request)
                    },
                    onCreateMockExam = { request ->
                        scope.launch {
                            try {
                                practiceManager.createMockExamFromStudio(
                                    id = MockExamId(UUID.randomUUID().toString()),
                                    planId = plan.id,
                                    questions = questions,
                                    request = request,
                                )
                                snackbar.showSnackbar(mockExamCreated)
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Throwable) {
                                snackbar.showSnackbar(operationFailed)
                            }
                        }
                    },
                    onImportQuestions = { rawText ->
                        scope.launch {
                            try {
                                val result = practiceManager.importQuestionsFromText(
                                    planId = plan.id,
                                    rawText = rawText,
                                    existingQuestions = questions,
                                )
                                snackbar.showSnackbar(
                                    if (result.importedCount > 0) questionsImported else noNewQuestions,
                                )
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Throwable) {
                                snackbar.showSnackbar(operationFailed)
                            }
                        }
                    },
                )
                PracticeWorkspaceTab.ERRORS -> ErrorsPane(
                    entries = notebook?.entries.orEmpty(),
                    weaknessDrill = weaknessDrill,
                    onCreateWeaknessDrill = {
                        scope.launch {
                            try {
                                if (!weaknessDrill.executable) {
                                    snackbar.showSnackbar(weaknessDrillUnavailable)
                                    return@launch
                                }
                                practiceManager.createWeaknessDrillTest(
                                    id = StudyTestId(UUID.randomUUID().toString()),
                                    planId = plan.id,
                                    title = weaknessDrillTitle,
                                    questions = questions,
                                    remediations = notebook?.entries.orEmpty(),
                                    dueReviews = queue?.due.orEmpty(),
                                )
                                snackbar.showSnackbar(weaknessDrillCreated)
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Throwable) {
                                snackbar.showSnackbar(operationFailed)
                            }
                        }
                    },
                    onOpenReviewCenter = onOpenReviewCenter,
                )
            }
        }
    }
}

@Composable
private fun ReviewsPane(
    due: List<ReviewItem>,
    dueCount: Int,
    upcomingCount: Int,
    flashcardCount: Int,
    onGrade: (ReviewItem, Int) -> Unit,
    onSkip: (ReviewItem) -> Unit,
    onOpenReviewCenter: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = workspaceContentPadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "review-primary", contentType = "primary-action") {
            if (dueCount > 0) {
                StudyFlowPrimaryActionCard(
                    supportingLabel = stringResource(R.string.practice_reviews),
                    title = stringResource(R.string.due_reviews),
                    message = stringResource(R.string.practice_due_message, dueCount),
                    actionLabel = stringResource(R.string.review_now),
                    onAction = onOpenReviewCenter,
                )
            } else {
                StudyFlowEmptyState(
                    title = stringResource(R.string.practice_reviews),
                    message = stringResource(R.string.practice_empty_reviews),
                    actionLabel = stringResource(R.string.open_review_center),
                    onAction = onOpenReviewCenter,
                )
            }
        }
        studyFlowSectionHeader(key = "review-stats", title = { stringResource(R.string.practice_reviews) })
        item(key = "review-metrics", contentType = "metrics") {
            StudyFlowResponsivePair(
                first = { childModifier ->
                    StudyFlowMetricPulse(
                        label = stringResource(R.string.due_reviews),
                        value = dueCount.toString(),
                        modifier = childModifier,
                    )
                },
                second = { childModifier ->
                    StudyFlowMetricPulse(
                        label = stringResource(R.string.planned),
                        value = upcomingCount.toString(),
                        supportingText = stringResource(R.string.flashcards_value, flashcardCount),
                        modifier = childModifier,
                    )
                },
            )
        }
        if (due.isNotEmpty()) {
            studyFlowSectionHeader(key = "due-now", title = { stringResource(R.string.review_due_now) })
            items(
                items = due.take(5),
                key = { item -> "due:${item.id.value}" },
                contentType = { "review-execution" },
            ) { item ->
                ReviewExecutionCard(
                    item = item,
                    onAgain = { onGrade(item, 1) },
                    onHard = { onGrade(item, 3) },
                    onGood = { onGrade(item, 5) },
                    onSkip = { onSkip(item) },
                )
            }
            if (due.size > 5) {
                item(key = "due-more", contentType = "actions") {
                    OutlinedButton(onClick = onOpenReviewCenter, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.open_remaining_reviews, due.size - 5))
                    }
                }
            }
        }
    }
}

@Composable
private fun QuestionsPane(
    questions: List<Question>,
    tests: List<StudyTest>,
    mocks: List<MockExam>,
    mockStudio: MockExamStudioBlueprint,
    onOpenQuestionBank: () -> Unit,
    onPreviewMockExam: (MockExamStudioRequest) -> MockExamStudioBlueprint,
    onCreateMockExam: (MockExamStudioRequest) -> Unit,
    onImportQuestions: (String) -> Unit,
) {
    var showImportDialog by rememberSaveable { mutableStateOf(false) }
    var showMockStudioDialog by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = workspaceContentPadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        studyFlowSectionHeader(
            key = "questions",
            title = { stringResource(R.string.practice_questions) },
            actionLabel = { stringResource(R.string.open_question_bank) },
            onAction = onOpenQuestionBank,
        )
        item(key = "question-metrics", contentType = "metrics") {
            StudyFlowMetricPulse(
                label = stringResource(R.string.questions_value, questions.size),
                value = stringResource(R.string.tests_count, tests.size),
                supportingText = stringResource(R.string.mocks_count, mocks.size),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item(key = "mock-exam-studio", contentType = "mock-exam-studio") {
            StudyFlowTimelineRow(
                title = stringResource(R.string.mock_exam_studio_title),
                metadata = stringResource(
                    R.string.mock_exam_studio_message,
                    mockStudio.questionCount,
                    mockStudio.timeLimitMinutes,
                ),
                icon = Icons.Outlined.Quiz,
                status = mockStudioWarningLabel(mockStudio.warnings.firstOrNull()),
                actionLabel = stringResource(R.string.create_mock_exam),
                onAction = { showMockStudioDialog = true },
            )
        }
        item(key = "question-importer", contentType = "importer") {
            StudyFlowTimelineRow(
                title = stringResource(R.string.question_importer_title),
                metadata = stringResource(R.string.question_importer_message),
                icon = Icons.Outlined.Quiz,
                actionLabel = stringResource(R.string.import_questions),
                onAction = { showImportDialog = true },
            )
        }
        if (questions.isEmpty()) {
            item(key = "questions-empty", contentType = "empty") {
                StudyFlowEmptyState(
                    title = stringResource(R.string.practice_questions),
                    message = stringResource(R.string.practice_empty_questions),
                    actionLabel = stringResource(R.string.open_question_bank),
                    onAction = onOpenQuestionBank,
                )
            }
        } else {
            items(
                items = questions.take(12),
                key = { question -> question.id.value },
                contentType = { "question" },
            ) { question ->
                StudyFlowTimelineRow(
                    title = question.statement,
                    metadata = question.examBoard ?: question.type.name.replace('_', ' ').lowercase(),
                    icon = Icons.Outlined.Quiz,
                    status = stringResource(R.string.percent_value, question.difficulty * 20),
                    onClick = onOpenQuestionBank,
                )
            }
        }
        if (tests.isNotEmpty()) {
            studyFlowSectionHeader(key = "tests", title = { stringResource(R.string.tests) })
            items(
                items = tests.take(5),
                key = { test -> "test:${test.id.value}" },
                contentType = { "practice-test" },
            ) { test ->
                StudyFlowTimelineRow(
                    title = test.title,
                    metadata = stringResource(R.string.test_progress, test.answeredCount, test.questionCount),
                    icon = Icons.Outlined.Quiz,
                    status = test.submittedAtEpochMillis?.let { stringResource(R.string.completed) },
                    onClick = onOpenQuestionBank,
                )
            }
        }
        if (mocks.isNotEmpty()) {
            studyFlowSectionHeader(key = "mocks", title = { stringResource(R.string.mock_exams) })
            items(
                items = mocks.take(5),
                key = { mock -> "mock:${mock.id.value}" },
                contentType = { "mock-exam" },
            ) { mock ->
                StudyFlowTimelineRow(
                    title = mock.title,
                    metadata = stringResource(R.string.test_progress, mock.answeredCount, mock.questionCount),
                    icon = Icons.Outlined.Quiz,
                    status = mock.scorePercent?.toInt()?.let { stringResource(R.string.percent_value, it) },
                    onClick = onOpenQuestionBank,
                )
            }
        }
    }
    if (showMockStudioDialog) {
        MockExamStudioDialog(
            initialBlueprint = mockStudio,
            onPreview = onPreviewMockExam,
            onDismiss = { showMockStudioDialog = false },
            onCreate = { request ->
                showMockStudioDialog = false
                onCreateMockExam(request)
            },
        )
    }
    if (showImportDialog) {
        QuestionImportDialog(
            onDismiss = { showImportDialog = false },
            onImport = { rawText ->
                showImportDialog = false
                onImportQuestions(rawText)
            },
        )
    }
}

@Composable
private fun MockExamStudioDialog(
    initialBlueprint: MockExamStudioBlueprint,
    onPreview: (MockExamStudioRequest) -> MockExamStudioBlueprint,
    onDismiss: () -> Unit,
    onCreate: (MockExamStudioRequest) -> Unit,
) {
    var questionCount by rememberSaveable { mutableStateOf(initialBlueprint.requestedQuestionCount.toString()) }
    var minutes by rememberSaveable { mutableStateOf(initialBlueprint.timeLimitMinutes.toString()) }
    var minimumScore by rememberSaveable { mutableStateOf(initialBlueprint.minimumScorePercent?.toString().orEmpty()) }
    var examBoard by rememberSaveable { mutableStateOf("") }
    var source by rememberSaveable { mutableStateOf("") }
    var tags by rememberSaveable { mutableStateOf("") }
    var includeDisputed by rememberSaveable { mutableStateOf(false) }
    val parsedQuestionCount = questionCount.toIntOrNull()
    val parsedMinutes = minutes.toIntOrNull()
    val parsedMinimum = minimumScore.toIntOrNull()
    val minimumScoreValid = minimumScore.isBlank() || parsedMinimum != null
    val currentRequest = MockExamStudioRequest(
        title = initialBlueprint.title,
        questionCount = parsedQuestionCount ?: initialBlueprint.requestedQuestionCount,
        timeLimitMinutes = parsedMinutes ?: initialBlueprint.timeLimitMinutes,
        minimumScorePercent = if (minimumScore.isBlank()) null else parsedMinimum,
        examBoard = examBoard,
        source = source,
        tags = tags.split(',').map(String::trim).filter(String::isNotBlank),
        includeDisputed = includeDisputed,
    )
    val currentBlueprint = remember(currentRequest) { onPreview(currentRequest) }
    val canCreate = parsedQuestionCount != null && parsedQuestionCount > 0 &&
        parsedMinutes != null && parsedMinutes > 0 && minimumScoreValid && currentBlueprint.executable

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.mock_exam_studio_title)) },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "mock-help") { Text(stringResource(R.string.mock_exam_studio_help)) }
                item(key = "mock-question-count") {
                    OutlinedTextField(
                        value = questionCount,
                        onValueChange = { questionCount = it },
                        label = { Text(stringResource(R.string.mock_exam_question_count)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item(key = "mock-minutes") {
                    OutlinedTextField(
                        value = minutes,
                        onValueChange = { minutes = it },
                        label = { Text(stringResource(R.string.mock_exam_time_limit_minutes)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item(key = "mock-minimum") {
                    OutlinedTextField(
                        value = minimumScore,
                        onValueChange = { minimumScore = it },
                        label = { Text(stringResource(R.string.mock_exam_minimum_score)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item(key = "mock-board") {
                    OutlinedTextField(
                        value = examBoard,
                        onValueChange = { examBoard = it },
                        label = { Text(stringResource(R.string.mock_exam_board_filter)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item(key = "mock-source") {
                    OutlinedTextField(
                        value = source,
                        onValueChange = { source = it },
                        label = { Text(stringResource(R.string.mock_exam_source_filter)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item(key = "mock-tags") {
                    OutlinedTextField(
                        value = tags,
                        onValueChange = { tags = it },
                        label = { Text(stringResource(R.string.mock_exam_tags_filter)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item(key = "mock-disputed") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = includeDisputed, onCheckedChange = { includeDisputed = it })
                        Text(stringResource(R.string.mock_exam_include_disputed))
                    }
                }
                item(key = "mock-preview") {
                    Text(
                        text = stringResource(
                            R.string.mock_exam_studio_sections,
                            currentBlueprint.sections.size,
                            currentBlueprint.questionCount,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                currentBlueprint.warnings.forEach { warning ->
                    item(key = "mock-warning-${warning.name}") {
                        mockStudioWarningLabel(warning)?.let { label ->
                            Text(
                                text = label,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = canCreate,
                onClick = {
                    onCreate(
                        currentRequest.copy(
                            questionCount = requireNotNull(parsedQuestionCount),
                            timeLimitMinutes = requireNotNull(parsedMinutes),
                        ),
                    )
                },
            ) {
                Text(stringResource(R.string.create_mock_exam))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.back))
            }
        },
    )
}

@Composable
private fun mockStudioWarningLabel(warning: MockExamStudioWarning?): String? = warning?.let { value ->
    stringResource(
        when (value) {
            MockExamStudioWarning.NO_QUESTIONS -> R.string.mock_exam_warning_no_questions
            MockExamStudioWarning.INELIGIBLE_QUESTIONS_SKIPPED -> R.string.mock_exam_warning_skipped
            MockExamStudioWarning.NOT_ENOUGH_MATCHING_QUESTIONS -> R.string.mock_exam_warning_not_enough
            MockExamStudioWarning.NARROW_SUBJECT_COVERAGE -> R.string.mock_exam_warning_narrow
        },
    )
}

@Composable
private fun QuestionImportDialog(
    onDismiss: () -> Unit,
    onImport: (String) -> Unit,
) {
    var rawText by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.question_importer_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.question_importer_help))
                OutlinedTextField(
                    value = rawText,
                    onValueChange = { rawText = it },
                    label = { Text(stringResource(R.string.import_questions)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp),
                )
                Text(
                    text = stringResource(R.string.question_importer_formats),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onImport(rawText) },
                enabled = rawText.isNotBlank(),
            ) {
                Text(stringResource(R.string.import_questions))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.back))
            }
        },
    )
}

@Composable
private fun ReviewExecutionCard(
    item: ReviewItem,
    onAgain: () -> Unit,
    onHard: () -> Unit,
    onGood: () -> Unit,
    onSkip: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(text = reviewTitle(item), style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(R.string.review_method_and_interval, item.method.name, item.intervalDays),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = onAgain, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.review_again))
                }
                OutlinedButton(onClick = onHard, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.review_hard))
                }
                Button(onClick = onGood, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.review_good))
                }
            }
            OutlinedButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.skip_review))
            }
        }
    }
}

@Composable
private fun reviewTitle(item: ReviewItem): String {
    val flashcardId = item.flashcardId
    val questionId = item.questionId
    val errorEntryId = item.errorEntryId
    val topicId = item.topicId
    return when {
        flashcardId != null -> stringResource(R.string.review_flashcard, flashcardId.value.take(8))
        questionId != null -> stringResource(R.string.review_question, questionId.value.take(8))
        errorEntryId != null -> stringResource(R.string.review_error, errorEntryId.value.take(8))
        topicId != null -> stringResource(R.string.review_topic, topicId.value.take(8))
        else -> stringResource(R.string.review_generic, item.id.value.take(8))
    }
}

@Composable
private fun ErrorsPane(
    entries: List<WrongAnswerRemediation>,
    weaknessDrill: WeaknessDrillPlan,
    onCreateWeaknessDrill: () -> Unit,
    onOpenReviewCenter: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = workspaceContentPadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        studyFlowSectionHeader(
            key = "errors",
            title = { stringResource(R.string.practice_errors) },
            actionLabel = { stringResource(R.string.open_error_notebook) },
            onAction = onOpenReviewCenter,
        )
        item(key = "weakness-drill", contentType = "weakness-drill") {
            StudyFlowTimelineRow(
                title = stringResource(R.string.weakness_drill_title),
                metadata = weaknessDrillMessage(weaknessDrill),
                icon = Icons.Outlined.ErrorOutline,
                status = stringResource(R.string.questions_value, weaknessDrill.questionCount),
                actionLabel = stringResource(R.string.create_weakness_drill),
                onAction = onCreateWeaknessDrill,
            )
        }
        if (entries.isEmpty()) {
            item(key = "errors-empty", contentType = "empty") {
                StudyFlowEmptyState(
                    title = stringResource(R.string.practice_errors),
                    message = stringResource(R.string.practice_empty_errors),
                    actionLabel = stringResource(R.string.open_error_notebook),
                    onAction = onOpenReviewCenter,
                )
            }
        } else {
            items(
                items = entries,
                key = { entry -> entry.error.id.value },
                contentType = { "error-entry" },
            ) { entry ->
                StudyFlowTimelineRow(
                    title = entry.error.explanation.ifBlank { entry.error.category.name.replace('_', ' ') },
                    metadata = stringResource(R.string.recurrences_value, entry.error.recurrenceCount),
                    icon = Icons.Outlined.ErrorOutline,
                    status = remediationPriorityLabel(entry.priority),
                    actionLabel = stringResource(R.string.open),
                    onAction = onOpenReviewCenter,
                )
            }
        }
    }
}

@Composable
private fun weaknessDrillMessage(plan: WeaknessDrillPlan): String = if (plan.executable) {
    stringResource(R.string.weakness_drill_message, plan.questionCount, plan.driverCount)
} else {
    stringResource(R.string.weakness_drill_unavailable)
}

@Composable
private fun remediationPriorityLabel(priority: RemediationPriority): String = stringResource(
    when (priority) {
        RemediationPriority.LOW -> R.string.priority_low
        RemediationPriority.MEDIUM -> R.string.priority_medium
        RemediationPriority.HIGH -> R.string.priority_high
        RemediationPriority.CRITICAL -> R.string.priority_critical
    },
)
