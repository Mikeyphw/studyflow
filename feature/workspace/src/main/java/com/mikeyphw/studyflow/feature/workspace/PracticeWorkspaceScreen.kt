package com.mikeyphw.studyflow.feature.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mikeyphw.studyflow.core.designsystem.StudyFlowEmptyState
import com.mikeyphw.studyflow.core.designsystem.StudyFlowFloatingAction
import com.mikeyphw.studyflow.core.designsystem.StudyFlowMetric
import com.mikeyphw.studyflow.core.designsystem.StudyFlowPracticeHero
import com.mikeyphw.studyflow.core.designsystem.StudyFlowResponsivePair
import com.mikeyphw.studyflow.core.designsystem.StudyFlowTabStrip
import com.mikeyphw.studyflow.core.designsystem.StudyFlowTimelineItem
import com.mikeyphw.studyflow.core.designsystem.studyFlowSectionHeader
import com.mikeyphw.studyflow.core.designsystem.workspaceContentPadding
import com.mikeyphw.studyflow.core.domain.ManageStudyContent
import com.mikeyphw.studyflow.core.domain.ManageStudyPractice
import com.mikeyphw.studyflow.core.domain.ManageStudyReview
import com.mikeyphw.studyflow.core.domain.asStudyFlowLoadState
import com.mikeyphw.studyflow.core.domain.MockExamStudioBlueprint
import com.mikeyphw.studyflow.core.domain.MockExamStudioRequest
import com.mikeyphw.studyflow.core.domain.MockExamStudioWarning
import com.mikeyphw.studyflow.core.domain.QuestionImportOwnership
import com.mikeyphw.studyflow.core.domain.QuestionImportPreview
import com.mikeyphw.studyflow.core.domain.RemediationPriority
import com.mikeyphw.studyflow.core.domain.WeaknessDrillPlan
import com.mikeyphw.studyflow.core.domain.WrongAnswerRemediation
import com.mikeyphw.studyflow.core.model.ErrorEntry
import com.mikeyphw.studyflow.core.model.Flashcard
import com.mikeyphw.studyflow.core.model.MockExam
import com.mikeyphw.studyflow.core.model.MockExamId
import com.mikeyphw.studyflow.core.model.Question
import com.mikeyphw.studyflow.core.model.ReviewItem
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudyTest
import com.mikeyphw.studyflow.core.model.StudyTestId
import com.mikeyphw.studyflow.core.model.Topic
import com.mikeyphw.studyflow.core.model.StudyFlowLoadState
import com.mikeyphw.studyflow.core.navigation.PracticeWorkspaceTab
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun PracticeWorkspaceScreen(
    plan: StudyPlan,
    selectedTab: PracticeWorkspaceTab,
    practiceManager: ManageStudyPractice,
    reviewManager: ManageStudyReview,
    contentManager: ManageStudyContent,
    onTabSelected: (PracticeWorkspaceTab) -> Unit,
    onSwitchPlan: () -> Unit,
    onSearch: () -> Unit,
    onSystem: () -> Unit,
    onQuickAdd: () -> Unit,
    onOpenReviewCenter: () -> Unit,
    onOpenQuestionBank: () -> Unit,
    modifier: Modifier = Modifier,
    targetId: String? = null,
) {
    var loadAttempt by remember(plan.id) { mutableIntStateOf(0) }
    val reviewFlow = remember(plan.id, loadAttempt) {
        reviewManager.observeQueue(plan.id).asStudyFlowLoadState()
    }
    val notebookFlow = remember(plan.id, loadAttempt) {
        reviewManager.observeWrongAnswerNotebook(plan.id).asStudyFlowLoadState()
    }
    val questionsFlow = remember(plan.id, loadAttempt) {
        practiceManager.observeQuestions(plan.id).asStudyFlowLoadState()
    }
    val testsFlow = remember(plan.id, loadAttempt) {
        practiceManager.observeTests(plan.id).asStudyFlowLoadState()
    }
    val mocksFlow = remember(plan.id, loadAttempt) {
        practiceManager.observeMockExams(plan.id).asStudyFlowLoadState()
    }
    val syllabusFlow = remember(plan.id, loadAttempt) {
        contentManager.observeSyllabus(plan.id).asStudyFlowLoadState()
    }
    val queueState by reviewFlow.collectAsStateWithLifecycle(initialValue = StudyFlowLoadState.Loading)
    val notebookState by notebookFlow.collectAsStateWithLifecycle(initialValue = StudyFlowLoadState.Loading)
    val questionsState by questionsFlow.collectAsStateWithLifecycle(initialValue = StudyFlowLoadState.Loading)
    val testsState by testsFlow.collectAsStateWithLifecycle(initialValue = StudyFlowLoadState.Loading)
    val mocksState by mocksFlow.collectAsStateWithLifecycle(initialValue = StudyFlowLoadState.Loading)
    val syllabusState by syllabusFlow.collectAsStateWithLifecycle(initialValue = StudyFlowLoadState.Loading)
    if (
        !workspaceLoadGate(
            states = listOf(queueState, notebookState, questionsState, testsState, mocksState, syllabusState),
            onRetry = { loadAttempt += 1 },
            modifier = modifier,
        )
    ) return
    val queue = queueState.readyValue()
    val notebook = notebookState.readyValue()
    val questions = questionsState.readyValue()
    val tests = testsState.readyValue()
    val mocks = mocksState.readyValue()
    val syllabus = syllabusState.readyValue()
    val weaknessDrill = remember(questions, notebook, queue) {
        practiceManager.buildWeaknessDrill(
            questions = questions,
            remediations = notebook.entries,
            dueReviews = queue.due,
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

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            WorkspaceHeader(
                title = stringResource(R.string.practice_title),
                plan = plan,
                onSwitchPlan = onSwitchPlan,
                onSearch = onSearch,
                onSystem = onSystem,
            )
            StudyFlowTabStrip(
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
            Box(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    PracticeWorkspaceTab.REVIEWS -> ReviewsPane(
                        due = queue.due,
                        dueCount = queue.dueCount,
                        upcomingCount = queue.upcomingCount,
                        flashcardCount = queue.flashcardCount,
                        questions = questions,
                        flashcards = queue.flashcards,
                        errors = queue.errors,
                        topics = syllabus.topics,
                        targetId = targetId,
                        onGrade = { item, grade ->
                            scope.launch {
                                try {
                                    reviewManager.gradeReview(plan.id, item.id, grade)
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
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
                                } catch (_: Exception) {
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
                        targetId = targetId,
                        onOpenQuestionBank = onOpenQuestionBank,
                        onPreviewMockExam = { request ->
                            practiceManager.previewMockExamStudio(questions, request)
                        },
                        onPreviewQuestionImport = { rawText ->
                            practiceManager.previewQuestionImport(
                                planId = plan.id,
                                rawText = rawText,
                                existingQuestions = questions,
                                ownership = QuestionImportOwnership.from(syllabus),
                            )
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
                                } catch (_: Exception) {
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
                                        ownership = QuestionImportOwnership.from(syllabus),
                                    )
                                    snackbar.showSnackbar(
                                        if (result.importedCount > 0) questionsImported else noNewQuestions,
                                    )
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    snackbar.showSnackbar(operationFailed)
                                }
                            }
                        },
                    )
                    PracticeWorkspaceTab.ERRORS -> ErrorsPane(
                        entries = notebook.entries,
                        weaknessDrill = weaknessDrill,
                        targetId = targetId,
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
                                        remediations = notebook.entries,
                                        dueReviews = queue.due,
                                    )
                                    snackbar.showSnackbar(weaknessDrillCreated)
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    snackbar.showSnackbar(operationFailed)
                                }
                            }
                        },
                        onOpenReviewCenter = onOpenReviewCenter,
                    )
                }
            }
        }
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 8.dp),
        )
        StudyFlowFloatingAction(
            icon = Icons.Outlined.Quiz,
            contentDescription = stringResource(R.string.quick_add),
            onClick = onQuickAdd,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        )
    }
}

@Composable
private fun ReviewsPane(
    due: List<ReviewItem>,
    dueCount: Int,
    upcomingCount: Int,
    flashcardCount: Int,
    questions: List<Question>,
    flashcards: List<Flashcard>,
    errors: List<ErrorEntry>,
    topics: List<Topic>,
    targetId: String?,
    onGrade: (ReviewItem, Int) -> Unit,
    onSkip: (ReviewItem) -> Unit,
    onOpenReviewCenter: () -> Unit,
) {
    val targetedFlashcard = targetId?.let { id -> flashcards.firstOrNull { it.id.value == id } }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = workspaceContentPadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        targetedFlashcard?.let { flashcard ->
            item(key = "target-flashcard:${flashcard.id.value}", contentType = "target-flashcard") {
                StudyFlowTimelineItem(
                    title = flashcard.front,
                    metadata = flashcard.back,
                    icon = Icons.Outlined.Quiz,
                )
            }
        }
        item(key = "review-primary", contentType = "primary-action") {
            if (dueCount > 0) {
                StudyFlowPracticeHero(
                    count = dueCount.toString(),
                    label = stringResource(R.string.practice_reviews),
                    message = pluralStringResource(R.plurals.practice_due_message, dueCount, dueCount),
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
                    StudyFlowMetric(
                        label = stringResource(R.string.due_reviews),
                        value = dueCount.toString(),
                        modifier = childModifier,
                    )
                },
                second = { childModifier ->
                    StudyFlowMetric(
                        label = stringResource(R.string.planned),
                        value = upcomingCount.toString(),
                        supportingText = pluralStringResource(
                            R.plurals.flashcards_value,
                            flashcardCount,
                            flashcardCount,
                        ),
                        modifier = childModifier,
                    )
                },
            )
        }
        if (due.isNotEmpty()) {
            studyFlowSectionHeader(key = "due-now", title = { stringResource(R.string.review_due_now) })
            items(
                items = due.sortedByDescending { item -> item.matchesTarget(targetId) }.take(5),
                key = { item -> "due:${item.id.value}" },
                contentType = { "review-execution" },
            ) { item ->
                ReviewExecutionCard(
                    item = item,
                    content = reviewContent(item, questions, flashcards, errors, topics),
                    onAgain = { onGrade(item, 1) },
                    onHard = { onGrade(item, 3) },
                    onGood = { onGrade(item, 5) },
                    onSkip = { onSkip(item) },
                )
            }
            if (due.size > 5) {
                item(key = "due-more", contentType = "actions") {
                    OutlinedButton(onClick = onOpenReviewCenter, modifier = Modifier.fillMaxWidth()) {
                        Text(pluralStringResource(R.plurals.open_remaining_reviews, due.size - 5, due.size - 5))
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
    targetId: String?,
    onOpenQuestionBank: () -> Unit,
    onPreviewMockExam: (MockExamStudioRequest) -> MockExamStudioBlueprint,
    onPreviewQuestionImport: (String) -> QuestionImportPreview,
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
            StudyFlowMetric(
                label = pluralStringResource(R.plurals.questions_value, questions.size, questions.size),
                value = pluralStringResource(R.plurals.tests_count, tests.size, tests.size),
                supportingText = pluralStringResource(R.plurals.mocks_count, mocks.size, mocks.size),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item(key = "mock-exam-studio", contentType = "mock-exam-studio") {
            StudyFlowTimelineItem(
                title = stringResource(R.string.mock_exam_studio_title),
                metadata = pluralStringResource(
                    R.plurals.mock_exam_studio_message,
                    mockStudio.questionCount,
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
            StudyFlowTimelineItem(
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
                items = questions.sortedByDescending { it.id.value == targetId }.take(12),
                key = { question -> question.id.value },
                contentType = { "question" },
            ) { question ->
                StudyFlowTimelineItem(
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
                StudyFlowTimelineItem(
                    title = test.title,
                    metadata = pluralStringResource(
                        R.plurals.test_progress,
                        test.answeredCount,
                        test.answeredCount,
                        test.questionCount,
                    ),
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
                StudyFlowTimelineItem(
                    title = mock.title,
                    metadata = pluralStringResource(
                        R.plurals.test_progress,
                        mock.answeredCount,
                        mock.answeredCount,
                        mock.questionCount,
                    ),
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
            onPreview = onPreviewQuestionImport,
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
    var negativeMarking by rememberSaveable { mutableStateOf(initialBlueprint.negativeMarking.toString()) }
    var examBoard by rememberSaveable { mutableStateOf("") }
    var source by rememberSaveable { mutableStateOf("") }
    var tags by rememberSaveable { mutableStateOf("") }
    var includeDisputed by rememberSaveable { mutableStateOf(false) }
    val parsedQuestionCount = questionCount.toIntOrNull()
    val parsedMinutes = minutes.toIntOrNull()
    val parsedMinimum = minimumScore.toIntOrNull()
    val parsedNegativeMarking = negativeMarking.toDoubleOrNull()
    val questionCountValid = parsedQuestionCount != null && parsedQuestionCount in 1..120
    val minutesValid = parsedMinutes != null && parsedMinutes > 0
    val minimumScoreValid = minimumScore.isBlank() || parsedMinimum?.let { it in 0..100 } == true
    val negativeMarkingValid = parsedNegativeMarking?.let { it.isFinite() && it >= 0.0 } == true
    val currentRequest = MockExamStudioRequest(
        title = initialBlueprint.title,
        questionCount = parsedQuestionCount?.takeIf { it in 1..120 } ?: initialBlueprint.requestedQuestionCount,
        timeLimitMinutes = parsedMinutes?.takeIf { it > 0 } ?: initialBlueprint.timeLimitMinutes,
        minimumScorePercent = if (minimumScore.isBlank()) {
            null
        } else {
            parsedMinimum?.takeIf { it in 0..100 } ?: initialBlueprint.minimumScorePercent
        },
        negativeMarking = parsedNegativeMarking?.takeIf { it.isFinite() && it >= 0.0 }
            ?: initialBlueprint.negativeMarking,
        examBoard = examBoard,
        source = source,
        tags = tags.split(',').map(String::trim).filter(String::isNotBlank),
        includeDisputed = includeDisputed,
    )
    val currentBlueprint = remember(currentRequest) { onPreview(currentRequest) }
    val canCreate = questionCountValid && minutesValid && minimumScoreValid && negativeMarkingValid
        && currentBlueprint.executable

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
                item(key = "mock-negative-marking") {
                    OutlinedTextField(
                        value = negativeMarking,
                        onValueChange = { negativeMarking = it },
                        label = { Text(stringResource(R.string.mock_exam_negative_marking)) },
                        modifier = Modifier.fillMaxWidth(),
                        supportingText = if (negativeMarkingValid) null else {
                            { Text(stringResource(R.string.mock_exam_negative_marking_invalid)) }
                        },
                        isError = !negativeMarkingValid,
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics(mergeDescendants = true) {}
                            .toggleable(
                                value = includeDisputed,
                                role = Role.Checkbox,
                                onValueChange = { includeDisputed = it },
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = includeDisputed, onCheckedChange = null)
                        Text(stringResource(R.string.mock_exam_include_disputed))
                    }
                }
                item(key = "mock-preview") {
                    Text(
                        text = pluralStringResource(
                            R.plurals.mock_exam_studio_sections,
                            currentBlueprint.sections.size,
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
                            negativeMarking = requireNotNull(parsedNegativeMarking),
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
    onPreview: (String) -> QuestionImportPreview,
    onDismiss: () -> Unit,
    onImport: (String) -> Unit,
) {
    var rawText by rememberSaveable { mutableStateOf("") }
    val preview = remember(rawText) { if (rawText.isBlank()) null else onPreview(rawText) }
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
                preview?.let { value ->
                    Text(
                        text = stringResource(
                            R.string.question_import_preview,
                            pluralStringResource(
                                R.plurals.question_import_ready,
                                value.acceptedCount,
                                value.acceptedCount,
                            ),
                            pluralStringResource(
                                R.plurals.question_import_duplicates,
                                value.duplicateCount,
                                value.duplicateCount,
                            ),
                            pluralStringResource(
                                R.plurals.question_import_rejected,
                                value.rejectedCount,
                                value.rejectedCount,
                            ),
                        ),
                        color = if (value.hasErrors) MaterialTheme.colorScheme.error else MaterialTheme
                            .colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    value.issues.take(5).forEach { issue ->
                        Text(stringResource(R.string.question_import_issue_row, issue.rowNumber, issue
                            .message), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onImport(rawText) },
                enabled = rawText.isNotBlank() && preview?.hasErrors == false && preview.acceptedCount > 0,
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
    content: ReviewContent,
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
            Text(text = content.title, style = MaterialTheme.typography.titleMedium)
            content.body?.takeIf(String::isNotBlank)?.let { body ->
                Text(text = body, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                text = pluralStringResource(
                    R.plurals.review_method_and_interval,
                    item.intervalDays,
                    item.method.name,
                    item.intervalDays,
                ),
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

private fun ReviewItem.matchesTarget(targetId: String?): Boolean = targetId != null && (
    id.value == targetId || questionId?.value == targetId || flashcardId?.value == targetId
        || errorEntryId?.value == targetId
)

private data class ReviewContent(
    val title: String,
    val body: String?,
)

@Composable
private fun reviewContent(
    item: ReviewItem,
    questions: List<Question>,
    flashcards: List<Flashcard>,
    errors: List<ErrorEntry>,
    topics: List<Topic>,
): ReviewContent {
    item.questionId?.let { id ->
        questions.firstOrNull { it.id == id }?.let { question ->
            return ReviewContent(question.statement, null)
        }
    }
    item.flashcardId?.let { id ->
        flashcards.firstOrNull { it.id == id }?.let { flashcard ->
            return ReviewContent(flashcard.front, flashcard.back)
        }
    }
    item.errorEntryId?.let { id ->
        errors.firstOrNull { it.id == id }?.let { error ->
            val body = listOfNotNull(
                error.chosenAnswer?.let { stringResource(R.string.review_chosen_answer, it) },
                error.correctAnswer?.let { stringResource(R.string.review_correct_answer, it) },
                error.explanation.takeIf(String::isNotBlank),
            ).joinToString(" • ")
            return ReviewContent(
                error.explanation.ifBlank { error.category.name.replace('_', ' ') },
                body.takeIf(String::isNotBlank),
            )
        }
    }
    item.topicId?.let { id ->
        topics.firstOrNull { it.id == id }?.let { topic ->
            return ReviewContent(topic.name, topic.description.takeIf(String::isNotBlank))
        }
    }
    return ReviewContent(reviewTitle(item), null)
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
    targetId: String?,
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
            StudyFlowTimelineItem(
                title = stringResource(R.string.weakness_drill_title),
                metadata = weaknessDrillMessage(weaknessDrill),
                icon = Icons.Outlined.ErrorOutline,
                status = pluralStringResource(
                    R.plurals.questions_value,
                    weaknessDrill.questionCount,
                    weaknessDrill.questionCount,
                ),
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
                items = entries.sortedByDescending { entry -> entry.error.id.value == targetId },
                key = { entry -> entry.error.id.value },
                contentType = { "error-entry" },
            ) { entry ->
                StudyFlowTimelineItem(
                    title = entry.error.explanation.ifBlank { entry.error.category.name.replace('_', ' ') },
                    metadata = pluralStringResource(
                        R.plurals.recurrences_value,
                        entry.error.recurrenceCount,
                        entry.error.recurrenceCount,
                    ) + " • " + remediationSuggestionLabel(entry.suggestion),
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
    pluralStringResource(R.plurals.weakness_drill_message, plan.questionCount, plan.questionCount, plan.driverCount)
} else {
    stringResource(R.string.weakness_drill_unavailable)
}

@Composable
private fun remediationSuggestionLabel(suggestion: com.mikeyphw.studyflow.core.domain.RemediationSuggestion): String =
    stringResource(
        when (suggestion) {
            com.mikeyphw.studyflow.core.domain.RemediationSuggestion.REVIEW_THEORY -> R.string.remediation_review_theory
            com.mikeyphw.studyflow.core.domain.RemediationSuggestion.PRACTICE_SIMILAR_QUESTIONS -> R.string
                .remediation_practice_similar
            com.mikeyphw.studyflow.core.domain.RemediationSuggestion.RE_READ_STATEMENT -> R.string
                .remediation_reread_statement
            com.mikeyphw.studyflow.core.domain.RemediationSuggestion.TIMED_DRILL -> R.string.remediation_timed_drill
            com.mikeyphw.studyflow.core.domain.RemediationSuggestion.SLOW_CHECKLIST -> R.string
                .remediation_slow_checklist
            com.mikeyphw.studyflow.core.domain.RemediationSuggestion.VERIFY_SOURCE -> R.string.remediation_verify_source
            com.mikeyphw.studyflow.core.domain.RemediationSuggestion.REVIEW_EXPLANATION -> R.string
                .remediation_review_explanation
        },
    )

@Composable
private fun remediationPriorityLabel(priority: RemediationPriority): String = stringResource(
    when (priority) {
        RemediationPriority.LOW -> R.string.priority_low
        RemediationPriority.MEDIUM -> R.string.priority_medium
        RemediationPriority.HIGH -> R.string.priority_high
        RemediationPriority.CRITICAL -> R.string.priority_critical
    },
)
