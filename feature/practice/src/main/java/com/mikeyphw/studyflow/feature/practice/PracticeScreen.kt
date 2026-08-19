package com.mikeyphw.studyflow.feature.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mikeyphw.studyflow.core.designsystem.StudyFlowEmptyState
import com.mikeyphw.studyflow.core.designsystem.StudyFlowSectionCard
import com.mikeyphw.studyflow.core.designsystem.StudyFlowStatusPill
import com.mikeyphw.studyflow.core.domain.runRecoverableOperation
import com.mikeyphw.studyflow.core.domain.ManageStudyPractice
import com.mikeyphw.studyflow.core.domain.ManageStudyReview
import com.mikeyphw.studyflow.core.domain.PracticeExecutionState
import com.mikeyphw.studyflow.core.domain.PracticeSummary
import com.mikeyphw.studyflow.core.model.MockExam
import com.mikeyphw.studyflow.core.model.MockExamId
import com.mikeyphw.studyflow.core.model.Question
import com.mikeyphw.studyflow.core.model.QuestionId
import com.mikeyphw.studyflow.core.model.QuestionType
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudyTest
import com.mikeyphw.studyflow.core.model.StudyTestId
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeScreen(
    plan: StudyPlan,
    practice: ManageStudyPractice,
    review: ManageStudyReview,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val questionsFlow = remember(plan.id) { practice.observeQuestions(plan.id) }
    val testsFlow = remember(plan.id) { practice.observeTests(plan.id) }
    val mocksFlow = remember(plan.id) { practice.observeMockExams(plan.id) }
    val summaryFlow = remember(plan.id) { practice.observeSummary(plan.id) }
    val questions by questionsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val tests by testsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val mocks by mocksFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val summary by summaryFlow.collectAsStateWithLifecycle(
        initialValue = PracticeSummary(0, 0, 0, 0, 0, 0, 0, 0),
    )
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var favoriteOnly by rememberSaveable { mutableStateOf(false) }
    var disputedOnly by rememberSaveable { mutableStateOf(false) }
    var activeTestId by rememberSaveable { mutableStateOf<String?>(null) }
    var activeMockId by rememberSaveable { mutableStateOf<String?>(null) }

    val screenState = PracticeScreenState(
        questions = questions,
        tests = tests,
        mocks = mocks,
        summary = summary,
        favoriteOnly = favoriteOnly,
        disputedOnly = disputedOnly,
    )
    val operationFailed = stringResource(R.string.operation_failed)
    val questionSaved = stringResource(R.string.question_saved)
    val questionDeleted = stringResource(R.string.question_deleted)
    val favoriteUpdated = stringResource(R.string.favorite_updated)
    val disputedUpdated = stringResource(R.string.disputed_updated)
    val testCreated = stringResource(R.string.practice_test_created)
    val mockCreated = stringResource(R.string.mock_exam_created)
    val needQuestions = stringResource(R.string.need_questions)
    val answerSaved = stringResource(R.string.answer_saved)
    val attemptFinished = stringResource(R.string.attempt_finished)

    fun launchAction(success: String, action: suspend () -> Unit) {
        scope.launch {
            runRecoverableOperation { action() }
                .onSuccess { snackbar.showSnackbar(success) }
                .onFailure { snackbar.showSnackbar(operationFailed) }
        }
    }

    val activeTest = activeTestId?.let { id -> tests.firstOrNull { it.id.value == id } }
    val activeMock = activeMockId?.let { id -> mocks.firstOrNull { it.id.value == id } }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.practice_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.go_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showEditor = true }) {
                        Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.add_question))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.practice_subtitle, plan.name),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            activeTest?.let { test ->
                item {
                    val state = practice.stateForTest(test, questions)
                    PracticeRunnerCard(
                        title = stringResource(R.string.active_practice_attempt),
                        state = state,
                        onAnswer = { question, answer, elapsedSeconds ->
                            launchAction(answerSaved) {
                                practice.answerPracticeQuestion(plan.id, test.id, question.id, answer, elapsedSeconds)
                            }
                        },
                        onFinish = {
                            launchAction(attemptFinished) {
                                practice.finishPracticeAttempt(plan.id, test.id)
                                activeTestId = null
                            }
                        },
                        onClose = { activeTestId = null },
                    )
                }
            }
            activeMock?.let { mock ->
                item {
                    val state = practice.stateForMockExam(mock, questions)
                    PracticeRunnerCard(
                        title = stringResource(R.string.active_mock_attempt),
                        state = state,
                        onAnswer = { question, answer, elapsedSeconds ->
                            launchAction(answerSaved) {
                                practice.answerMockQuestion(plan.id, mock.id, question.id, answer, elapsedSeconds)
                            }
                        },
                        onFinish = {
                            launchAction(attemptFinished) {
                                practice.finishMockExamAttempt(plan.id, mock.id)
                                activeMockId = null
                            }
                        },
                        onClose = { activeMockId = null },
                    )
                }
            }
            item { PracticeSummaryCard(summary) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    FilterChip(
                        selected = !favoriteOnly && !disputedOnly,
                        onClick = {
                            favoriteOnly = false
                            disputedOnly = false
                        },
                        label = { Text(stringResource(R.string.all_questions)) },
                    )
                    FilterChip(
                        selected = favoriteOnly,
                        onClick = { favoriteOnly = !favoriteOnly },
                        label = { Text(stringResource(R.string.favorites)) },
                    )
                    FilterChip(
                        selected = disputedOnly,
                        onClick = { disputedOnly = !disputedOnly },
                        label = { Text(stringResource(R.string.disputed)) },
                    )
                }
            }
            item {
                StudyFlowSectionCard(title = stringResource(R.string.question_bank)) {
                    if (screenState.filteredQuestions.isEmpty()) {
                        StudyFlowEmptyState(
                            title = stringResource(R.string.no_questions),
                            message = stringResource(R.string.no_questions_message),
                            actionLabel = stringResource(R.string.add_question),
                            onAction = { showEditor = true },
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        if (screenState.filteredQuestions.isEmpty()) {
                                            scope.launch { snackbar.showSnackbar(needQuestions) }
                                        } else {
                                            scope.launch {
                                                runRecoverableOperation {
                                                    practice.createPracticeTest(
                                                        id = StudyTestId(UUID.randomUUID().toString()),
                                                        planId = plan.id,
                                                        title = "${plan.name} practice",
                                                        questions = screenState.filteredQuestions.take(20),
                                                    )
                                                }.onSuccess {
                                                    snackbar.showSnackbar(testCreated)
                                                }.onFailure {
                                                    snackbar.showSnackbar(operationFailed)
                                                }
                                            }
                                        }
                                    },
                                ) { Text(text = stringResource(R.string.create_practice_test)) }
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            runRecoverableOperation {
                                                practice.createMockExam(
                                                    id = MockExamId(UUID.randomUUID().toString()),
                                                    planId = plan.id,
                                                    title = "${plan.name} mock",
                                                    questions = screenState.filteredQuestions.take(50),
                                                    timeLimitSeconds = 7200,
                                                    negativeMarking = 0.0,
                                                    minimumScorePercent = null,
                                                )
                                            }.onSuccess {
                                                snackbar.showSnackbar(mockCreated)
                                            }.onFailure {
                                                snackbar.showSnackbar(operationFailed)
                                            }
                                        }
                                    },
                                ) { Text(text = stringResource(R.string.create_mock_exam)) }
                            }
                            screenState.filteredQuestions.forEach { question ->
                                QuestionCard(
                                    question = question,
                                    onToggleFavorite = {
                                        launchAction(favoriteUpdated) {
                                            practice.toggleFavorite(plan.id, question.id)
                                        }
                                    },
                                    onToggleDisputed = {
                                        launchAction(disputedUpdated) {
                                            practice.toggleDisputed(plan.id, question.id)
                                        }
                                    },
                                    onDelete = {
                                        launchAction(questionDeleted) {
                                            practice.deleteQuestion(plan.id, question.id)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
            item {
                RecentTestsCard(
                    tests = tests,
                    onStart = { test ->
                        launchAction(testCreated) {
                            practice.startPracticeTest(plan.id, test.id)
                            activeMockId = null
                            activeTestId = test.id.value
                        }
                    },
                )
            }
            item {
                RecentMocksCard(
                    mocks = mocks,
                    onStart = { mock ->
                        launchAction(mockCreated) {
                            practice.startMockExamAttempt(plan.id, mock.id)
                            activeTestId = null
                            activeMockId = mock.id.value
                        }
                    },
                )
            }
        }
    }

    if (showEditor) {
        QuestionEditorDialog(
            onDismiss = { showEditor = false },
            onSave = { state ->
                launchAction(questionSaved) {
                    practice.createQuestion(
                        id = QuestionId(UUID.randomUUID().toString()),
                        studyPlanId = plan.id,
                        subjectId = null,
                        topicId = null,
                        type = state.type,
                        statement = state.statement,
                        options = state.options,
                        correctAnswer = state.correctAnswer,
                        explanation = state.explanation,
                        examBoard = null,
                        source = null,
                        year = null,
                        difficulty = state.difficulty,
                        tags = state.tagList,
                    )
                }
                showEditor = false
            },
        )
    }
}

@Composable
private fun PracticeSummaryCard(summary: PracticeSummary) {
    StudyFlowSectionCard(title = stringResource(R.string.practice_summary)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(pluralStringResource(R.plurals.question_count_value, summary.questionCount, summary.questionCount))
            Text(pluralStringResource(R.plurals.favorite_count_value, summary.favoriteCount, summary.favoriteCount))
            Text(pluralStringResource(R.plurals.disputed_count_value, summary.disputedCount, summary.disputedCount))
            Text(pluralStringResource(R.plurals.test_count_value, summary.testCount, summary.testCount))
            Text(pluralStringResource(R.plurals.mock_count_value, summary.mockExamCount, summary.mockExamCount))
            Text(pluralStringResource(R.plurals.answered_value, summary.answeredCount, summary.answeredCount))
            Text(stringResource(R.string.accuracy_value, summary.accuracyPercent))
        }
    }
}

@Composable
private fun PracticeRunnerCard(
    title: String,
    state: PracticeExecutionState,
    onAnswer: (Question, String, Long) -> Unit,
    onFinish: () -> Unit,
    onClose: () -> Unit,
) {
    var nowEpochMillis by remember(state.attemptId) { mutableLongStateOf(System.currentTimeMillis()) }
    val deadline = state.deadlineEpochMillis
    LaunchedEffect(state.attemptId, deadline, state.submitted) {
        if (!state.submitted && deadline != null) {
            val remaining = (deadline - System.currentTimeMillis()).coerceAtLeast(0L)
            delay(remaining)
            nowEpochMillis = System.currentTimeMillis()
            if (!state.submitted) onFinish()
        }
    }
    LaunchedEffect(state.attemptId, state.submitted) {
        while (!state.submitted) {
            nowEpochMillis = System.currentTimeMillis()
            delay(1_000L)
        }
    }
    StudyFlowSectionCard(title = title) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                pluralStringResource(
                    R.plurals.runner_progress,
                    state.answeredCount,
                    state.answeredCount,
                    state.questionIds.size,
                ),
            )
            deadline?.let { deadlineEpochMillis ->
                val remainingSeconds = ((deadlineEpochMillis - nowEpochMillis).coerceAtLeast(0L) + 999L) / 1_000L
                Text(stringResource(R.string.time_remaining_seconds, remainingSeconds),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val question = state.currentQuestion
            if (question == null) {
                Text(stringResource(R.string.no_questions_message))
            } else {
                Text(question.statement, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                val questionStartedAt = remember(state.attemptId, question.id.value) { SystemClock.elapsedRealtime() }
                val options = question.options.ifEmpty { listOf(question.correctAnswer) }
                options.forEach { option ->
                    OutlinedButton(
                        enabled = !state.submitted && !state.deadlinePassed(nowEpochMillis),
                        onClick = {
                            val elapsedSeconds = ((SystemClock
                                .elapsedRealtime() - questionStartedAt) / 1_000L).coerceAtLeast(0L)
                            onAnswer(question, option, elapsedSeconds)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(option) }
                }
                if (state.explanationVisible(question.id)) {
                    Text(
                        text = stringResource(R.string.explanation_value, question.explanation.ifBlank { "—" }),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !state.submitted, onClick = onFinish) {
                    Text(stringResource(R.string.finish_attempt))
                }
                OutlinedButton(onClick = onClose) { Text(stringResource(R.string.close_runner)) }
            }
        }
    }
}

@Composable
private fun QuestionCard(
    question: Question,
    onToggleFavorite: () -> Unit,
    onToggleDisputed: () -> Unit,
    onDelete: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(question.statement, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(question.correctAnswer, color = MaterialTheme.colorScheme.primary)
            if (question.explanation.isNotBlank()) {
                Text(question.explanation, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (question.tags.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.tags_value, question.tags.joinToString()),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StudyFlowStatusPill(text = stringResource(R.string.difficulty, question.difficulty))
                if (question.favorite) {
                    StudyFlowStatusPill(text = stringResource(R.string.favorites))
                }
                if (question.disputed) {
                    StudyFlowStatusPill(text = stringResource(R.string.disputed))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onToggleFavorite) {
                    Icon(Icons.Outlined.Star, contentDescription = null)
                    Text(stringResource(R.string.toggle_favorite))
                }
                OutlinedButton(onClick = onToggleDisputed) {
                    Icon(Icons.Outlined.Flag, contentDescription = null)
                    Text(stringResource(R.string.toggle_disputed))
                }
                ConfirmingDeleteButton(onConfirmed = onDelete)
            }
        }
    }
}

@Composable
private fun RecentTestsCard(tests: List<StudyTest>, onStart: (StudyTest) -> Unit) {
    StudyFlowSectionCard(title = stringResource(R.string.recent_tests)) {
        if (tests.isEmpty()) {
            Text(stringResource(R.string.need_questions), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                tests.take(5).forEach { test ->
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(test.title, style = MaterialTheme.typography.titleSmall)
                            Text(
                                pluralStringResource(
                                    R.plurals.question_count_value,
                                    test.questionCount,
                                    test.questionCount,
                                ),
                            )
                            Text(pluralStringResource(R.plurals.answered_value, test.answeredCount, test.answeredCount))
                            Text(pluralStringResource(R.plurals.correct_value, test.correctCount, test.correctCount))
                            OutlinedButton(enabled = test.submittedAtEpochMillis == null, onClick = { onStart(test) }) {
                                Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                                Text(stringResource(R.string.start_or_resume))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentMocksCard(mocks: List<MockExam>, onStart: (MockExam) -> Unit) {
    StudyFlowSectionCard(title = stringResource(R.string.recent_mocks)) {
        if (mocks.isEmpty()) {
            Text(stringResource(R.string.no_mocks), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                mocks.take(5).forEach { mock ->
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(mock.title, style = MaterialTheme.typography.titleSmall)
                            Text(
                                pluralStringResource(
                                    R.plurals.question_count_value,
                                    mock.questionCount,
                                    mock.questionCount,
                                ),
                            )
                            Text(pluralStringResource(R.plurals.answered_value, mock.answeredCount, mock.answeredCount))
                            Text(pluralStringResource(R.plurals.correct_value, mock.correctCount, mock.correctCount))
                            mock.scorePercent?.let { score ->
                                val threshold = mock.minimumScorePercent
                                val outcome = threshold?.let {
                                    stringResource(if (score >= it) R.string.exam_pass else R.string
                                        .exam_below_threshold)
                                }
                                Text(
                                    if (outcome == null) {
                                        stringResource(R.string.score_value, score.toInt())
                                    } else {
                                        stringResource(R.string.score_outcome_value, score.toInt(), outcome)
                                    },
                                )
                            }
                            OutlinedButton(enabled = mock.submittedAtEpochMillis == null, onClick = { onStart(mock) }) {
                                Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                                Text(stringResource(R.string.start_or_resume))
                            }
                        }
                    }
                }
            }
        }
    }
}

private val QuestionEditorStateSaver = listSaver<QuestionEditorState, Any>(
    save = { state ->
        listOf(
            state.statement,
            state.optionA,
            state.optionB,
            state.optionC,
            state.optionD,
            state.correctAnswer,
            state.explanation,
            state.tags,
            state.difficulty,
            state.type.name,
        )
    },
    restore = { values ->
        QuestionEditorState(
            statement = values[0] as String,
            optionA = values[1] as String,
            optionB = values[2] as String,
            optionC = values[3] as String,
            optionD = values[4] as String,
            correctAnswer = values[5] as String,
            explanation = values[6] as String,
            tags = values[7] as String,
            difficulty = values[8] as Int,
            type = QuestionType.entries.firstOrNull { it.name == values[9] as String }
                ?: QuestionType.MULTIPLE_CHOICE,
        )
    },
)

@Composable
internal fun QuestionEditorDialog(
    onDismiss: () -> Unit,
    onSave: (QuestionEditorState) -> Unit,
) {
    var state by rememberSaveable(stateSaver = QuestionEditorStateSaver) {
        mutableStateOf(QuestionEditorState())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_question)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = state.statement,
                    onValueChange = { state = state.copy(statement = it) },
                    label = { Text(stringResource(R.string.statement)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                OutlinedTextField(
                    value = state.optionA,
                    onValueChange = { state = state.copy(optionA = it) },
                    label = { Text(stringResource(R.string.option_a)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.optionB,
                    onValueChange = { state = state.copy(optionB = it) },
                    label = { Text(stringResource(R.string.option_b)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.optionC,
                    onValueChange = { state = state.copy(optionC = it) },
                    label = { Text(stringResource(R.string.option_c)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.optionD,
                    onValueChange = { state = state.copy(optionD = it) },
                    label = { Text(stringResource(R.string.option_d)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.correctAnswer,
                    onValueChange = { state = state.copy(correctAnswer = it) },
                    label = { Text(stringResource(R.string.correct_answer)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.explanation,
                    onValueChange = { state = state.copy(explanation = it) },
                    label = { Text(stringResource(R.string.explanation)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.tags,
                    onValueChange = { state = state.copy(tags = it) },
                    label = { Text(stringResource(R.string.tags_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    (1..5).forEach { value ->
                        FilterChip(
                            selected = state.difficulty == value,
                            onClick = { state = state.copy(difficulty = value) },
                            label = { Text(value.toString()) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(enabled = state.canSave, onClick = { onSave(state) }) {
                Text(stringResource(R.string.save_question))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}


@Composable
private fun ConfirmingDeleteButton(onConfirmed: () -> Unit) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    OutlinedButton(onClick = { confirming = true }) {
        Icon(Icons.Outlined.Delete, contentDescription = null)
        Text(stringResource(R.string.delete))
    }
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
