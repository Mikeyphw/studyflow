package com.mikeyphw.studyflow.feature.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mikeyphw.studyflow.core.designsystem.StudyFlowEmptyState
import com.mikeyphw.studyflow.core.designsystem.StudyFlowMetricPulse
import com.mikeyphw.studyflow.core.designsystem.StudyFlowResponsivePair
import com.mikeyphw.studyflow.core.designsystem.StudyFlowPrimaryActionCard
import com.mikeyphw.studyflow.core.designsystem.StudyFlowTimelineRow
import com.mikeyphw.studyflow.core.designsystem.StudyFlowWorkspaceTabs
import com.mikeyphw.studyflow.core.designsystem.studyFlowSectionHeader
import com.mikeyphw.studyflow.core.designsystem.workspaceContentPadding
import com.mikeyphw.studyflow.core.domain.ManageStudyPractice
import com.mikeyphw.studyflow.core.domain.ManageStudyReview
import com.mikeyphw.studyflow.core.domain.RemediationPriority
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.ReviewItem
import com.mikeyphw.studyflow.core.navigation.PracticeWorkspaceTab
import kotlinx.coroutines.launch
import java.util.concurrent.CancellationException

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
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val operationFailed = stringResource(R.string.operation_failed)

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
                    onOpenQuestionBank = onOpenQuestionBank,
                )
                PracticeWorkspaceTab.ERRORS -> ErrorsPane(
                    entries = notebook?.entries.orEmpty(),
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
    questions: List<com.mikeyphw.studyflow.core.model.Question>,
    tests: List<com.mikeyphw.studyflow.core.model.StudyTest>,
    mocks: List<com.mikeyphw.studyflow.core.model.MockExam>,
    onOpenQuestionBank: () -> Unit,
) {
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
            Text(
                text = reviewTitle(item),
                style = MaterialTheme.typography.titleMedium,
            )
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
private fun reviewTitle(item: ReviewItem): String = when {
    item.flashcardId != null -> {
        val id = item.flashcardId
        stringResource(R.string.review_flashcard, id?.value?.take(8).orEmpty())
    }
    item.questionId != null -> {
        val id = item.questionId
        stringResource(R.string.review_question, id?.value?.take(8).orEmpty())
    }
    item.errorEntryId != null -> {
        val id = item.errorEntryId
        stringResource(R.string.review_error, id?.value?.take(8).orEmpty())
    }
    item.topicId != null -> {
        val id = item.topicId
        stringResource(R.string.review_topic, id?.value?.take(8).orEmpty())
    }
    else -> stringResource(R.string.review_generic, item.id.value.take(8))
}

@Composable
private fun ErrorsPane(
    entries: List<com.mikeyphw.studyflow.core.domain.WrongAnswerRemediation>,
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
private fun remediationPriorityLabel(priority: RemediationPriority): String = stringResource(
    when (priority) {
        RemediationPriority.LOW -> R.string.priority_low
        RemediationPriority.MEDIUM -> R.string.priority_medium
        RemediationPriority.HIGH -> R.string.priority_high
        RemediationPriority.CRITICAL -> R.string.priority_critical
    },
)
