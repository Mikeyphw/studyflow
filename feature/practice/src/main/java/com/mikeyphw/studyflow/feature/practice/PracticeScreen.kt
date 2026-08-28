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
import com.mikeyphw.studyflow.core.domain.ExamBlueprintPlanner
import com.mikeyphw.studyflow.core.domain.ExamBlueprintPreview
import com.mikeyphw.studyflow.core.domain.ExamScoringMode
import com.mikeyphw.studyflow.core.domain.ExamScoringPolicy
import com.mikeyphw.studyflow.core.domain.ManageStudyContent
import com.mikeyphw.studyflow.core.domain.ManageStudyPractice
import com.mikeyphw.studyflow.core.domain.PublicExamQuestionFilter
import com.mikeyphw.studyflow.core.domain.PublicExamQuestionOutcomeEvidence
import com.mikeyphw.studyflow.core.domain.QuestionAttemptOutcome
import com.mikeyphw.studyflow.core.domain.ManageStudyReview
import com.mikeyphw.studyflow.core.domain.PracticeExecutionState
import com.mikeyphw.studyflow.core.domain.PracticeSummary
import com.mikeyphw.studyflow.core.model.ErrorCategory
import com.mikeyphw.studyflow.core.model.MockExam
import com.mikeyphw.studyflow.core.model.MockExamId
import com.mikeyphw.studyflow.core.model.Question
import com.mikeyphw.studyflow.core.model.QuestionAuthorship
import com.mikeyphw.studyflow.core.model.SubjectId
import com.mikeyphw.studyflow.core.model.TopicId
import com.mikeyphw.studyflow.core.model.QuestionId
import com.mikeyphw.studyflow.core.model.QuestionType
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudyTest
import com.mikeyphw.studyflow.core.model.StudyTestId
import com.mikeyphw.studyflow.core.model.TestMode
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeScreen(
    plan: StudyPlan,
    practice: ManageStudyPractice,
    review: ManageStudyReview,
    content: ManageStudyContent,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val questionsFlow = remember(plan.id) { practice.observeQuestions(plan.id) }
    val testsFlow = remember(plan.id) { practice.observeTests(plan.id) }
    val mocksFlow = remember(plan.id) { practice.observeMockExams(plan.id) }
    val summaryFlow = remember(plan.id) { practice.observeSummary(plan.id) }
    val syllabusFlow = remember(plan.id) { content.observeSyllabus(plan.id) }
    val questions by questionsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val tests by testsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val mocks by mocksFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val summary by summaryFlow.collectAsStateWithLifecycle(
        initialValue = PracticeSummary(0, 0, 0, 0, 0, 0, 0, 0),
    )
    val syllabus by syllabusFlow.collectAsStateWithLifecycle(initialValue = null)
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var favoriteOnly by rememberSaveable { mutableStateOf(false) }
    var disputedOnly by rememberSaveable { mutableStateOf(false) }
    var examBoardFilter by rememberSaveable { mutableStateOf("") }
    var organizationFilter by rememberSaveable { mutableStateOf("") }
    var roleFilter by rememberSaveable { mutableStateOf("") }
    var yearFromFilter by rememberSaveable { mutableStateOf("") }
    var yearToFilter by rememberSaveable { mutableStateOf("") }
    var selectedSubjectId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedTopicId by rememberSaveable { mutableStateOf<String?>(null) }
    var authorshipFilter by rememberSaveable { mutableStateOf<QuestionAuthorship?>(null) }
    var difficultyFilter by rememberSaveable { mutableStateOf<Int?>(null) }
    var outcomeFilter by rememberSaveable { mutableStateOf<QuestionAttemptOutcome?>(null) }
    var activeTestId by rememberSaveable { mutableStateOf<String?>(null) }
    var activeMockId by rememberSaveable { mutableStateOf<String?>(null) }
    var bancaBlueprintPreview by remember { mutableStateOf<ExamBlueprintPreview?>(null) }
    var blueprintScoringMode by rememberSaveable { mutableStateOf(ExamScoringMode.NO_PENALTY) }
    var blueprintWrongPenalty by rememberSaveable { mutableStateOf("1.0") }
    var blueprintBlankPenalty by rememberSaveable { mutableStateOf("0.0") }
    var blueprintTimeMinutes by rememberSaveable { mutableStateOf("120") }
    var blueprintOverallMinimum by rememberSaveable { mutableStateOf("60") }
    var blueprintSectionMinimum by rememberSaveable { mutableStateOf("") }
    var blueprintSectionCounts by rememberSaveable { mutableStateOf("") }
    var blueprintSectionWeights by rememberSaveable { mutableStateOf("") }
    var blueprintStrictMode by rememberSaveable { mutableStateOf(true) }
    var blueprintAllowMultipleChoice by rememberSaveable { mutableStateOf(true) }
    var blueprintAllowTrueFalse by rememberSaveable { mutableStateOf(true) }
    var blueprintAllowShortAnswer by rememberSaveable { mutableStateOf(true) }
    val blueprintAllowedTypes = buildSet {
        if (blueprintAllowMultipleChoice) add(QuestionType.MULTIPLE_CHOICE)
        if (blueprintAllowTrueFalse) add(QuestionType.TRUE_FALSE)
        if (blueprintAllowShortAnswer) add(QuestionType.SHORT_ANSWER)
    }.ifEmpty { setOf(QuestionType.MULTIPLE_CHOICE) }

    val questionFilter = PublicExamQuestionFilter(
        examBoard = examBoardFilter.trim().ifBlank { null },
        organization = organizationFilter.trim().ifBlank { null },
        role = roleFilter.trim().ifBlank { null },
        subjectId = selectedSubjectId?.let(::SubjectId),
        topicId = selectedTopicId?.let(::TopicId),
        yearFrom = yearFromFilter.toIntOrNull(),
        yearTo = yearToFilter.toIntOrNull(),
        authorship = authorshipFilter,
        difficulty = difficultyFilter,
        favorite = true.takeIf { favoriteOnly },
        disputed = true.takeIf { disputedOnly },
        outcome = outcomeFilter,
    )
    val questionOutcomes = remember(questions, tests, mocks) {
        PublicExamQuestionOutcomeEvidence.latest(questions, tests, mocks)
    }
    val screenState = PracticeScreenState(
        questions = questions,
        tests = tests,
        mocks = mocks,
        summary = summary,
        planId = plan.id,
        filter = questionFilter,
        outcomes = questionOutcomes,
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
    val bancaMockCreated = stringResource(R.string.banca_mock_created)

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
                        onAnswer = { question, answer, elapsedSeconds, category ->
                            launchAction(answerSaved) {
                                practice.answerPracticeQuestion(
                                    plan.id, test.id, question.id, answer, elapsedSeconds, category,
                                )
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
                        onAnswer = { question, answer, elapsedSeconds, category ->
                            launchAction(answerSaved) {
                                practice.answerMockQuestion(
                                    plan.id, mock.id, question.id, answer, elapsedSeconds, category,
                                )
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
                            examBoardFilter = ""
                            organizationFilter = ""
                            roleFilter = ""
                            yearFromFilter = ""
                            yearToFilter = ""
                            selectedSubjectId = null
                            selectedTopicId = null
                            authorshipFilter = null
                            difficultyFilter = null
                            outcomeFilter = null
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
                StudyFlowSectionCard(title = stringResource(R.string.public_exam_filters)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = examBoardFilter,
                            onValueChange = { examBoardFilter = it },
                            label = { Text(stringResource(R.string.filter_exam_board)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = organizationFilter,
                            onValueChange = { organizationFilter = it },
                            label = { Text(stringResource(R.string.filter_organization)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = roleFilter,
                            onValueChange = { roleFilter = it },
                            label = { Text(stringResource(R.string.filter_role)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = yearFromFilter,
                                onValueChange = { yearFromFilter = it.filter(Char::isDigit).take(4) },
                                label = { Text(stringResource(R.string.filter_year_from)) },
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = yearToFilter,
                                onValueChange = { yearToFilter = it.filter(Char::isDigit).take(4) },
                                label = { Text(stringResource(R.string.filter_year_to)) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilterChip(
                                selected = authorshipFilter == null,
                                onClick = { authorshipFilter = null },
                                label = { Text(stringResource(R.string.filter_any_origin)) },
                            )
                            FilterChip(
                                selected = authorshipFilter == QuestionAuthorship.OFFICIAL,
                                onClick = { authorshipFilter = QuestionAuthorship.OFFICIAL },
                                label = { Text(stringResource(R.string.filter_official)) },
                            )
                            FilterChip(
                                selected = authorshipFilter == QuestionAuthorship.AUTHORED,
                                onClick = { authorshipFilter = QuestionAuthorship.AUTHORED },
                                label = { Text(stringResource(R.string.filter_authored)) },
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilterChip(
                                selected = difficultyFilter == null,
                                onClick = { difficultyFilter = null },
                                label = { Text(stringResource(R.string.filter_any_difficulty)) },
                            )
                            (1..5).forEach { level ->
                                FilterChip(
                                    selected = difficultyFilter == level,
                                    onClick = { difficultyFilter = level },
                                    label = { Text(level.toString()) },
                                )
                            }
                        }
                        Text(stringResource(R.string.filter_attempt_outcome))
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilterChip(
                                selected = outcomeFilter == null,
                                onClick = { outcomeFilter = null },
                                label = { Text(stringResource(R.string.filter_outcome_any)) },
                            )
                            FilterChip(
                                selected = outcomeFilter == QuestionAttemptOutcome.CORRECT,
                                onClick = { outcomeFilter = QuestionAttemptOutcome.CORRECT },
                                label = { Text(stringResource(R.string.filter_outcome_correct)) },
                            )
                            FilterChip(
                                selected = outcomeFilter == QuestionAttemptOutcome.WRONG,
                                onClick = { outcomeFilter = QuestionAttemptOutcome.WRONG },
                                label = { Text(stringResource(R.string.filter_outcome_wrong)) },
                            )
                            FilterChip(
                                selected = outcomeFilter == QuestionAttemptOutcome.UNSEEN,
                                onClick = { outcomeFilter = QuestionAttemptOutcome.UNSEEN },
                                label = { Text(stringResource(R.string.filter_outcome_unseen)) },
                            )
                        }
                        val activeSubjects = syllabus?.subjects.orEmpty().filterNot { it.archived }
                        if (activeSubjects.isNotEmpty()) {
                            Text(stringResource(R.string.filter_subject))
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                FilterChip(
                                    selected = selectedSubjectId == null,
                                    onClick = { selectedSubjectId = null; selectedTopicId = null },
                                    label = { Text(stringResource(R.string.all_questions)) },
                                )
                                activeSubjects.forEach { subject ->
                                    FilterChip(
                                        selected = selectedSubjectId == subject.id.value,
                                        onClick = { selectedSubjectId = subject.id.value; selectedTopicId = null },
                                        label = { Text(subject.name) },
                                    )
                                }
                            }
                        }
                        val activeTopics = syllabus?.topics.orEmpty().filter { topic ->
                            !topic.archived && (selectedSubjectId == null || topic.subjectId.value == selectedSubjectId)
                        }
                        if (activeTopics.isNotEmpty()) {
                            Text(stringResource(R.string.filter_topic))
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                FilterChip(
                                    selected = selectedTopicId == null,
                                    onClick = { selectedTopicId = null },
                                    label = { Text(stringResource(R.string.all_questions)) },
                                )
                                activeTopics.forEach { topic ->
                                    FilterChip(
                                        selected = selectedTopicId == topic.id.value,
                                        onClick = { selectedTopicId = topic.id.value },
                                        label = { Text(topic.name) },
                                    )
                                }
                            }
                        }
                    }
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
                            Text(stringResource(R.string.banca_blueprint_settings), fontWeight = FontWeight.SemiBold)
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                ExamScoringMode.entries.forEach { mode ->
                                    FilterChip(
                                        selected = blueprintScoringMode == mode,
                                        onClick = { blueprintScoringMode = mode; bancaBlueprintPreview = null },
                                        label = { Text(stringResource(mode.practiceLabelRes())) },
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = blueprintTimeMinutes,
                                    onValueChange = { blueprintTimeMinutes = it; bancaBlueprintPreview = null },
                                    modifier = Modifier.weight(1f),
                                    label = { Text(stringResource(R.string.banca_blueprint_time_minutes)) },
                                    singleLine = true,
                                )
                                OutlinedTextField(
                                    value = blueprintOverallMinimum,
                                    onValueChange = { blueprintOverallMinimum = it; bancaBlueprintPreview = null },
                                    modifier = Modifier.weight(1f),
                                    label = { Text(stringResource(R.string.banca_blueprint_overall_minimum)) },
                                    singleLine = true,
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = blueprintWrongPenalty,
                                    onValueChange = { blueprintWrongPenalty = it; bancaBlueprintPreview = null },
                                    modifier = Modifier.weight(1f),
                                    label = { Text(stringResource(R.string.banca_blueprint_wrong_penalty)) },
                                    singleLine = true,
                                )
                                OutlinedTextField(
                                    value = blueprintBlankPenalty,
                                    onValueChange = { blueprintBlankPenalty = it; bancaBlueprintPreview = null },
                                    modifier = Modifier.weight(1f),
                                    label = { Text(stringResource(R.string.banca_blueprint_blank_penalty)) },
                                    singleLine = true,
                                )
                                OutlinedTextField(
                                    value = blueprintSectionMinimum,
                                    onValueChange = { blueprintSectionMinimum = it; bancaBlueprintPreview = null },
                                    modifier = Modifier.weight(1f),
                                    label = { Text(stringResource(R.string.banca_blueprint_section_minimum)) },
                                    singleLine = true,
                                )
                            }
                            val blueprintSubjectOrder = screenState.filteredQuestions
                                .map { it.subjectId?.value ?: "general" }
                                .distinct()
                                .sorted()
                                .map { key ->
                                    if (key == "general") {
                                        stringResource(R.string.banca_blueprint_general_section)
                                    } else {
                                        syllabus?.subjects?.firstOrNull { it.id.value == key }?.name ?: key
                                    }
                                }
                            if (blueprintSubjectOrder.isNotEmpty()) {
                                Text(
                                    stringResource(
                                        R.string.banca_blueprint_section_order,
                                        blueprintSubjectOrder.joinToString(" · "),
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            OutlinedTextField(
                                value = blueprintSectionCounts,
                                onValueChange = { blueprintSectionCounts = it; bancaBlueprintPreview = null },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.banca_blueprint_section_counts)) },
                                supportingText = {
                                    Text(stringResource(R.string.banca_blueprint_comma_separated_hint))
                                },
                                singleLine = true,
                            )
                            OutlinedTextField(
                                value = blueprintSectionWeights,
                                onValueChange = { blueprintSectionWeights = it; bancaBlueprintPreview = null },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.banca_blueprint_section_weights)) },
                                supportingText = {
                                    Text(stringResource(R.string.banca_blueprint_comma_separated_hint))
                                },
                                singleLine = true,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                FilterChip(
                                    selected = blueprintStrictMode,
                                    onClick = {
                                        blueprintStrictMode = !blueprintStrictMode
                                        bancaBlueprintPreview = null
                                    },
                                    label = { Text(stringResource(R.string.banca_blueprint_strict_mode)) },
                                )
                                FilterChip(
                                    selected = blueprintAllowMultipleChoice,
                                    onClick = {
                                        if (!blueprintAllowMultipleChoice ||
                                            blueprintAllowTrueFalse ||
                                            blueprintAllowShortAnswer
                                        ) {
                                            blueprintAllowMultipleChoice = !blueprintAllowMultipleChoice
                                            bancaBlueprintPreview = null
                                        }
                                    },
                                    label = { Text(QuestionType.MULTIPLE_CHOICE.name.replace('_', ' ')) },
                                )
                                FilterChip(
                                    selected = blueprintAllowTrueFalse,
                                    onClick = {
                                        if (!blueprintAllowTrueFalse ||
                                            blueprintAllowMultipleChoice ||
                                            blueprintAllowShortAnswer
                                        ) {
                                            blueprintAllowTrueFalse = !blueprintAllowTrueFalse
                                            bancaBlueprintPreview = null
                                        }
                                    },
                                    label = { Text(QuestionType.TRUE_FALSE.name.replace('_', ' ')) },
                                )
                                FilterChip(
                                    selected = blueprintAllowShortAnswer,
                                    onClick = {
                                        if (!blueprintAllowShortAnswer ||
                                            blueprintAllowMultipleChoice ||
                                            blueprintAllowTrueFalse
                                        ) {
                                            blueprintAllowShortAnswer = !blueprintAllowShortAnswer
                                            bancaBlueprintPreview = null
                                        }
                                    },
                                    label = { Text(QuestionType.SHORT_ANSWER.name.replace('_', ' ')) },
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    if (screenState.filteredQuestions.isEmpty()) {
                                        scope.launch { snackbar.showSnackbar(needQuestions) }
                                    } else {
                                        val exactYear = yearFromFilter.toIntOrNull()
                                            ?.takeIf { it == yearToFilter.toIntOrNull() }
                                        val scoring = ExamScoringPolicy(
                                            mode = blueprintScoringMode,
                                            wrongAnswerPenalty = blueprintWrongPenalty
                                                .toDoubleOrNull()
                                                ?.coerceAtLeast(0.0) ?: 0.0,
                                            unansweredPenalty = blueprintBlankPenalty
                                                .toDoubleOrNull()
                                                ?.coerceAtLeast(0.0) ?: 0.0,
                                        )
                                        val sectionMinimum = blueprintSectionMinimum.toIntOrNull()?.coerceIn(0, 100)
                                        val baseBlueprint = ExamBlueprintPlanner.balancedFromSelection(
                                            title = "${plan.name} banca mock",
                                            questions = screenState.filteredQuestions,
                                            timeLimitMinutes = blueprintTimeMinutes
                                                .toIntOrNull()
                                                ?.coerceIn(1, 1_440) ?: 120,
                                            scoringPolicy = scoring,
                                            minimumOverallScorePercent = blueprintOverallMinimum
                                                .toIntOrNull()
                                                ?.coerceIn(0, 100),
                                            examBoard = examBoardFilter.trim().ifBlank { null },
                                            year = exactYear,
                                        )
                                        val customCounts = blueprintSectionCounts.split(',')
                                            .mapNotNull { it.trim().toIntOrNull()?.takeIf { count -> count > 0 } }
                                        val customWeights = blueprintSectionWeights.split(',')
                                            .mapNotNull { raw ->
                                                raw.trim().toDoubleOrNull()?.takeIf { it > 0.0 }
                                            }
                                        val blueprint = baseBlueprint.copy(
                                            strictExamMode = blueprintStrictMode,
                                            sections = baseBlueprint.sections.mapIndexed { index, section ->
                                                section.copy(
                                                    questionCount = customCounts.getOrNull(index)
                                                        ?: section.questionCount,
                                                    weight = customWeights.getOrNull(index) ?: section.weight,
                                                    minimumScorePercent = sectionMinimum,
                                                    allowedQuestionTypes = blueprintAllowedTypes,
                                                )
                                            },
                                        )
                                        bancaBlueprintPreview = practice.previewExamBlueprint(
                                            screenState.filteredQuestions,
                                            blueprint,
                                        )
                                    }
                                },
                            ) {
                                Text(stringResource(R.string.preview_banca_blueprint))
                            }
                            bancaBlueprintPreview?.let { preview ->
                                StudyFlowSectionCard(
                                    title = stringResource(R.string.banca_blueprint_preview_title),
                                ) {
                                    Text(
                                        stringResource(
                                            R.string.banca_blueprint_distribution,
                                            preview.selectedQuestions.size,
                                            preview.blueprint.requestedQuestionCount,
                                            preview.missingQuestionCount,
                                        ),
                                    )
                                    preview.sections.forEach { section ->
                                        Text(
                                            stringResource(
                                                R.string.banca_blueprint_section,
                                                section.section.key,
                                                section.selectedQuestions.size,
                                                section.section.questionCount,
                                            ),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (!preview.executable) {
                                        Text(
                                            stringResource(R.string.banca_blueprint_insufficient_bank),
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                    Button(
                                        enabled = preview.executable,
                                        onClick = {
                                            scope.launch {
                                                runRecoverableOperation {
                                                    practice.createMockExamFromBlueprint(
                                                        id = MockExamId(UUID.randomUUID().toString()),
                                                        planId = plan.id,
                                                        questions = screenState.filteredQuestions,
                                                        blueprint = preview.blueprint,
                                                    )
                                                }.onSuccess {
                                                    bancaBlueprintPreview = null
                                                    snackbar.showSnackbar(bancaMockCreated)
                                                }.onFailure {
                                                    snackbar.showSnackbar(operationFailed)
                                                }
                                            }
                                        },
                                    ) {
                                        Text(stringResource(R.string.create_banca_mock))
                                    }
                                }
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
    onAnswer: (Question, String, Long, ErrorCategory?) -> Unit,
    onFinish: () -> Unit,
    onClose: () -> Unit,
) {
    var nowEpochMillis by remember(state.attemptId) { mutableLongStateOf(System.currentTimeMillis()) }
    var pendingWrongAnswer by remember(state.attemptId) { mutableStateOf<PendingWrongAnswer?>(null) }
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
                            val wrong = !option.trim().equals(question.correctAnswer.trim(), ignoreCase = true)
                            if (wrong && state.mode == TestMode.IMMEDIATE_FEEDBACK) {
                                pendingWrongAnswer = PendingWrongAnswer(question, option, elapsedSeconds)
                            } else {
                                onAnswer(question, option, elapsedSeconds, null)
                            }
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
    pendingWrongAnswer?.let { pending ->
        WrongAnswerClassificationDialog(
            onDismiss = {
                onAnswer(pending.question, pending.answer, pending.elapsedSeconds, ErrorCategory.OTHER)
                pendingWrongAnswer = null
            },
            onClassify = { category ->
                onAnswer(pending.question, pending.answer, pending.elapsedSeconds, category)
                pendingWrongAnswer = null
            },
        )
    }
}

private data class PendingWrongAnswer(
    val question: Question,
    val answer: String,
    val elapsedSeconds: Long,
)

@Composable
private fun WrongAnswerClassificationDialog(
    onDismiss: () -> Unit,
    onClassify: (ErrorCategory) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.quick_error_classification_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(stringResource(R.string.quick_error_classification_message))
                ErrorCategory.entries.forEach { category ->
                    OutlinedButton(
                        onClick = { onClassify(category) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(category.practiceLabelRes()))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.classify_later)) }
        },
    )
}

private fun ExamScoringMode.practiceLabelRes(): Int = when (this) {
    ExamScoringMode.NO_PENALTY -> R.string.scoring_no_penalty
    ExamScoringMode.FIXED_NEGATIVE -> R.string.scoring_fixed_negative
    ExamScoringMode.RIGHT_MINUS_WRONG -> R.string.scoring_right_minus_wrong
    ExamScoringMode.WEIGHTED_CUSTOM -> R.string.scoring_weighted
}

private fun ErrorCategory.practiceLabelRes(): Int = when (this) {
    ErrorCategory.CONTENT_GAP -> R.string.error_category_content_gap
    ErrorCategory.FORGOTTEN_CONTENT -> R.string.error_category_forgotten
    ErrorCategory.CONCEPT_CONFUSION -> R.string.error_category_confusion
    ErrorCategory.INTERPRETATION -> R.string.error_category_interpretation
    ErrorCategory.DISTRACTION -> R.string.error_category_distraction
    ErrorCategory.CALCULATION -> R.string.error_category_calculation
    ErrorCategory.TIME_PRESSURE -> R.string.error_category_time_pressure
    ErrorCategory.DISPUTED_QUESTION -> R.string.error_category_disputed
    ErrorCategory.OTHER -> R.string.error_category_other
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
