package com.mikeyphw.studyflow.feature.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mikeyphw.studyflow.core.designsystem.StudyFlowEmptyState
import com.mikeyphw.studyflow.core.designsystem.StudyFlowSectionCard
import com.mikeyphw.studyflow.core.domain.ManageStudyPractice
import com.mikeyphw.studyflow.core.domain.PracticeSummary
import com.mikeyphw.studyflow.core.model.Question
import com.mikeyphw.studyflow.core.model.QuestionId
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudyTest
import com.mikeyphw.studyflow.core.model.StudyTestId
import java.util.UUID
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeScreen(
    plan: StudyPlan,
    practice: ManageStudyPractice,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val questionsFlow = remember(plan.id) { practice.observeQuestions(plan.id) }
    val testsFlow = remember(plan.id) { practice.observeTests(plan.id) }
    val summaryFlow = remember(plan.id) { practice.observeSummary(plan.id) }
    val questions by questionsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val tests by testsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val summary by summaryFlow.collectAsStateWithLifecycle(
        initialValue = PracticeSummary(0, 0, 0, 0, 0, 0, 0, 0),
    )
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var favoriteOnly by rememberSaveable { mutableStateOf(false) }
    var disputedOnly by rememberSaveable { mutableStateOf(false) }

    val screenState = PracticeScreenState(
        questions = questions,
        tests = tests,
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
    val needQuestions = stringResource(R.string.need_questions)

    fun launchAction(success: String, action: suspend () -> Unit) {
        scope.launch {
            runCatching { action() }
                .onSuccess { snackbar.showSnackbar(success) }
                .onFailure { snackbar.showSnackbar(operationFailed) }
        }
    }

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
                            Button(
                                onClick = {
                                    if (screenState.filteredQuestions.isEmpty()) {
                                        scope.launch { snackbar.showSnackbar(needQuestions) }
                                    } else {
                                        launchAction(testCreated) {
                                            practice.createPracticeTest(
                                                id = StudyTestId(UUID.randomUUID().toString()),
                                                planId = plan.id,
                                                title = "${plan.name} practice",
                                                questions = screenState.filteredQuestions.take(20),
                                            )
                                        }
                                    }
                                },
                            ) {
                                Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                                Text(text = stringResource(R.string.create_practice_test))
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
            item { RecentTestsCard(tests) }
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
            Text(stringResource(R.string.question_count_value, summary.questionCount))
            Text(stringResource(R.string.favorite_count_value, summary.favoriteCount))
            Text(stringResource(R.string.disputed_count_value, summary.disputedCount))
            Text(stringResource(R.string.test_count_value, summary.testCount))
            Text(stringResource(R.string.mock_count_value, summary.mockExamCount))
            Text(stringResource(R.string.answered_value, summary.answeredCount))
            Text(stringResource(R.string.accuracy_value, summary.accuracyPercent))
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
                AssistChip(onClick = {}, label = { Text(stringResource(R.string.difficulty, question.difficulty)) })
                if (question.favorite) {
                    AssistChip(onClick = {}, label = { Text(stringResource(R.string.favorites)) })
                }
                if (question.disputed) {
                    AssistChip(onClick = {}, label = { Text(stringResource(R.string.disputed)) })
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
                OutlinedButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, contentDescription = null)
                    Text(stringResource(R.string.delete))
                }
            }
        }
    }
}

@Composable
private fun RecentTestsCard(tests: List<StudyTest>) {
    StudyFlowSectionCard(title = stringResource(R.string.recent_tests)) {
        if (tests.isEmpty()) {
            Text(stringResource(R.string.need_questions), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                tests.take(5).forEach { test ->
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(test.title, style = MaterialTheme.typography.titleSmall)
                            Text(stringResource(R.string.question_count_value, test.questionCount))
                            Text(stringResource(R.string.answered_value, test.answeredCount))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuestionEditorDialog(
    onDismiss: () -> Unit,
    onSave: (QuestionEditorState) -> Unit,
) {
    var state by remember { mutableStateOf(QuestionEditorState()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_question)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
