package com.mikeyphw.studyflow.feature.reviews

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mikeyphw.studyflow.core.designsystem.StudyFlowSectionCard
import com.mikeyphw.studyflow.core.designsystem.StudyFlowStatusPill
import com.mikeyphw.studyflow.core.domain.ManageStudyContent
import com.mikeyphw.studyflow.core.domain.ManageStudyPractice
import com.mikeyphw.studyflow.core.domain.ManageStudyReview
import com.mikeyphw.studyflow.core.domain.ReviewQueue
import com.mikeyphw.studyflow.core.domain.runRecoverableOperation
import com.mikeyphw.studyflow.core.model.ErrorEntry
import com.mikeyphw.studyflow.core.model.ErrorEntryId
import com.mikeyphw.studyflow.core.model.Flashcard
import com.mikeyphw.studyflow.core.model.Question
import com.mikeyphw.studyflow.core.model.FlashcardId
import com.mikeyphw.studyflow.core.model.FlashcardType
import com.mikeyphw.studyflow.core.domain.RemediationPriority
import com.mikeyphw.studyflow.core.model.ReviewItem
import com.mikeyphw.studyflow.core.model.ReviewItemId
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudySyllabus
import com.mikeyphw.studyflow.core.model.Topic
import java.util.UUID
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
import androidx.compose.runtime.rememberCoroutineScope

internal sealed interface ReviewMutation {
    data class ScheduleError(val errorId: ErrorEntryId) : ReviewMutation
    data class ResolveError(val errorId: ErrorEntryId) : ReviewMutation
    data class Grade(val reviewId: ReviewItemId, val grade: Int) : ReviewMutation
    data class Skip(val reviewId: ReviewItemId) : ReviewMutation
    data class CreateFlashcard(
        val id: FlashcardId,
        val front: String,
        val back: String,
    ) : ReviewMutation
}

private sealed interface ReviewOperationState {
    data object Idle : ReviewOperationState
    data class Running(val mutation: ReviewMutation) : ReviewOperationState
    data class Succeeded(val mutation: ReviewMutation) : ReviewOperationState
    data class Failed(val mutation: ReviewMutation) : ReviewOperationState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyReviewScreen(
    plan: StudyPlan,
    review: ManageStudyReview,
    practice: ManageStudyPractice,
    content: ManageStudyContent,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val queue by review.observeQueue(plan.id).collectAsStateWithLifecycle(
        initialValue = ReviewQueue(
            due = emptyList(),
            upcoming = emptyList(),
            completedToday = emptyList(),
            flashcards = emptyList(),
            errors = emptyList(),
        ),
    )
    val questions by practice.observeQuestions(plan.id).collectAsStateWithLifecycle(initialValue = emptyList())
    val syllabusFlow = remember(content, plan.id) {
        content.observeSyllabus(plan.id)
            .map<StudySyllabus, StudySyllabus?> { current -> current }
    }
    val syllabus by syllabusFlow.collectAsStateWithLifecycle(initialValue = null)
    val coroutineScope = rememberCoroutineScope()
    var filter by rememberSaveable { mutableStateOf(ReviewFilter.DUE) }
    var front by rememberSaveable { mutableStateOf("") }
    var back by rememberSaveable { mutableStateOf("") }
    var operationState by remember { mutableStateOf<ReviewOperationState>(ReviewOperationState.Idle) }

    fun execute(mutation: ReviewMutation) {
        if (operationState is ReviewOperationState.Running) return
        val recoveringFailedMutation = (operationState as? ReviewOperationState.Failed)?.mutation == mutation
        coroutineScope.launch {
            operationState = ReviewOperationState.Running(mutation)
            if (recoveringFailedMutation && queue.alreadyReflects(mutation)) {
                operationState = ReviewOperationState.Succeeded(mutation)
                return@launch
            }
            runRecoverableOperation {
                when (mutation) {
                    is ReviewMutation.ScheduleError -> review.scheduleErrorReview(plan.id, mutation.errorId)
                    is ReviewMutation.ResolveError -> review.resolveErrorEntry(plan.id, mutation.errorId)
                    is ReviewMutation.Grade -> review.gradeReview(plan.id, mutation.reviewId, mutation.grade)
                    is ReviewMutation.Skip -> review.skipReview(plan.id, mutation.reviewId)
                    is ReviewMutation.CreateFlashcard -> review.createFlashcard(
                        id = mutation.id,
                        studyPlanId = plan.id,
                        subjectId = null,
                        topicId = null,
                        type = FlashcardType.BASIC,
                        front = mutation.front,
                        back = mutation.back,
                        clozeText = null,
                        tags = emptyList(),
                    )
                }
            }.onSuccess {
                if (mutation is ReviewMutation.CreateFlashcard) {
                    if (front == mutation.front) front = ""
                    if (back == mutation.back) back = ""
                }
                operationState = ReviewOperationState.Succeeded(mutation)
            }.onFailure {
                operationState = ReviewOperationState.Failed(mutation)
            }
        }
    }

    val operationRunning = operationState is ReviewOperationState.Running
    val contentIndex = remember(questions, queue.flashcards, queue.errors, syllabus?.topics) {
        ReviewItemContentIndex(
            questions = questions,
            flashcards = queue.flashcards,
            errors = queue.errors,
            topics = syllabus?.topics.orEmpty(),
        )
    }
    val activeReviewsByErrorId = remember(queue.due, queue.upcoming) {
        (queue.due + queue.upcoming).groupBy { item -> item.errorEntryId }
    }
    val sortedErrors = remember(queue.errors) {
        queue.errors.sortedWith(
            compareByDescending<ErrorEntry> { it.recurrenceCount }
                .thenByDescending { it.updatedAtEpochMillis },
        )
    }
    val filteredItems = remember(queue, filter) { queue.itemsFor(filter) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.reviews_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.go_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "operation-state") {
                when (val state = operationState) {
                    ReviewOperationState.Idle -> Unit
                    is ReviewOperationState.Running -> StudyFlowSectionCard(
                        title = stringResource(R.string.review_operation_running),
                    ) {
                        Text(stringResource(R.string.review_operation_running_message))
                    }
                    is ReviewOperationState.Succeeded -> StudyFlowSectionCard(
                        title = stringResource(R.string.review_operation_succeeded),
                    ) {
                        Text(stringResource(R.string.review_operation_succeeded_message))
                    }
                    is ReviewOperationState.Failed -> StudyFlowSectionCard(
                        title = stringResource(R.string.review_operation_failed),
                    ) {
                        Text(
                            stringResource(R.string.review_operation_failed_message),
                            color = MaterialTheme.colorScheme.error,
                        )
                        OutlinedButton(onClick = { execute(state.mutation) }) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }
            }
            item(key = "review-summary") { ReviewSummaryCard(queue = queue) }
            item(key = "wrong-answer-heading") {
                StudyFlowSectionCard(title = stringResource(R.string.wrong_answer_notebook)) {
                    Text(
                        text = if (sortedErrors.isEmpty()) {
                            stringResource(R.string.no_wrong_answers)
                        } else {
                            pluralStringResource(R.plurals.error_count, sortedErrors.size, sortedErrors.size)
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(
                items = sortedErrors,
                key = { error -> "error:${error.id.value}" },
            ) { error ->
                val priority = com.mikeyphw.studyflow.core.domain.WrongAnswerRemediator.priorityFor(
                    error = error,
                    nowEpochMillis = System.currentTimeMillis(),
                    linkedReviews = activeReviewsByErrorId[error.id].orEmpty(),
                )
                WrongAnswerRow(
                    error = error,
                    priority = priority,
                    suggestion = com.mikeyphw.studyflow.core.domain.WrongAnswerRemediator.suggestionFor(error),
                    enabled = !operationRunning,
                    onScheduleReview = { execute(ReviewMutation.ScheduleError(error.id)) },
                    onResolve = { execute(ReviewMutation.ResolveError(error.id)) },
                )
            }
            item(key = "add-flashcard") {
                StudyFlowSectionCard(title = stringResource(R.string.add_flashcard)) {
                    OutlinedTextField(
                        value = front,
                        onValueChange = { front = it },
                        label = { Text(stringResource(R.string.flashcard_front)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                    )
                    OutlinedTextField(
                        value = back,
                        onValueChange = { back = it },
                        label = { Text(stringResource(R.string.flashcard_back)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                    )
                    Button(
                        onClick = {
                            execute(
                                ReviewMutation.CreateFlashcard(
                                    id = FlashcardId(UUID.randomUUID().toString()),
                                    front = front,
                                    back = back,
                                ),
                            )
                        },
                        enabled = front.isNotBlank() && back.isNotBlank() && !operationRunning,
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null)
                        Text(text = stringResource(R.string.create_flashcard))
                    }
                }
            }
            item(key = "review-queue-heading") {
                StudyFlowSectionCard(title = stringResource(R.string.review_queue)) {
                    ReviewFilterRow(selected = filter, onSelected = { filter = it })
                    if (filteredItems.isEmpty()) {
                        Text(
                            text = stringResource(R.string.no_reviews_for_filter),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(
                items = filteredItems,
                key = { item -> "review:${item.id.value}" },
            ) { item ->
                ReviewItemRow(
                    item = item,
                    content = contentIndex.contentFor(item),
                    enabled = !operationRunning,
                    onAgain = { execute(ReviewMutation.Grade(item.id, grade = 1)) },
                    onGood = { execute(ReviewMutation.Grade(item.id, grade = 4)) },
                    onEasy = { execute(ReviewMutation.Grade(item.id, grade = 5)) },
                    onSkip = { execute(ReviewMutation.Skip(item.id)) },
                )
            }
        }
    }
}

@Composable
private fun ReviewSummaryCard(queue: ReviewQueue) {
    StudyFlowSectionCard(title = stringResource(R.string.today_reviews)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StudyFlowStatusPill(
                text = pluralStringResource(R.plurals.due_count, queue.dueCount, queue.dueCount),
                leadingIcon = { Icon(Icons.Outlined.Schedule, contentDescription = null) },
            )
            StudyFlowStatusPill(
                text = pluralStringResource(R.plurals.upcoming_count, queue.upcomingCount, queue.upcomingCount),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StudyFlowStatusPill(
                text = pluralStringResource(R.plurals.flashcard_count, queue.flashcardCount, queue.flashcardCount),
            )
            StudyFlowStatusPill(
                text = pluralStringResource(R.plurals.error_count, queue.errorCount, queue.errorCount),
            )
        }
    }
}

@Composable
private fun ReviewFilterRow(
    selected: ReviewFilter,
    onSelected: (ReviewFilter) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ReviewFilter.entries.forEach { filter ->
            FilterChip(
                selected = selected == filter,
                onClick = { onSelected(filter) },
                label = { Text(stringResource(filter.labelRes())) },
            )
        }
    }
}

@Composable
private fun ReviewItemRow(
    item: ReviewItem,
    content: ReviewItemContent,
    enabled: Boolean,
    onAgain: () -> Unit,
    onGood: () -> Unit,
    onEasy: () -> Unit,
    onSkip: () -> Unit,
) {
    StudyFlowSectionCard(
        title = stringResource(R.string.review_item_title, stringResource(item.kindLabelRes())),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
    ) {
        Text(content.title, style = MaterialTheme.typography.titleMedium)
        content.body?.takeIf(String::isNotBlank)?.let { body ->
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            text = pluralStringResource(
                R.plurals.review_item_details,
                item.intervalDays,
                item.repetitionCount,
                item.intervalDays,
                item.easeFactor,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onAgain, enabled = enabled) {
                Icon(Icons.Outlined.Close, contentDescription = null)
                Text(text = stringResource(R.string.again))
            }
            Button(onClick = onGood, enabled = enabled) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null)
                Text(text = stringResource(R.string.good))
            }
            OutlinedButton(onClick = onEasy, enabled = enabled) {
                Text(text = stringResource(R.string.easy))
            }
            OutlinedButton(onClick = onSkip, enabled = enabled) {
                Text(text = stringResource(R.string.skip))
            }
        }
    }
}

internal fun ReviewQueue.alreadyReflects(mutation: ReviewMutation): Boolean = when (mutation) {
    is ReviewMutation.ScheduleError -> (due + upcoming + completedToday).any { it.errorEntryId == mutation.errorId }
    is ReviewMutation.ResolveError -> errors.none { it.id == mutation.errorId }
    is ReviewMutation.Grade -> completedToday.any { it.id == mutation.reviewId }
    is ReviewMutation.Skip -> (due + upcoming).none { it.id == mutation.reviewId }
    is ReviewMutation.CreateFlashcard -> flashcards.any { it.id == mutation.id }
}

private data class ReviewItemContent(val title: String, val body: String?)

private class ReviewItemContentIndex(
    questions: List<Question>,
    flashcards: List<Flashcard>,
    errors: List<ErrorEntry>,
    topics: List<Topic>,
) {
    private val questionsById = questions.associateBy(Question::id)
    private val flashcardsById = flashcards.associateBy(Flashcard::id)
    private val errorsById = errors.associateBy(ErrorEntry::id)
    private val topicsById = topics.associateBy(Topic::id)

    fun contentFor(item: ReviewItem): ReviewItemContent {
        item.questionId?.let(questionsById::get)?.let { question ->
            return ReviewItemContent(question.statement, null)
        }
        item.flashcardId?.let(flashcardsById::get)?.let { flashcard ->
            return ReviewItemContent(flashcard.front, flashcard.back)
        }
        item.errorEntryId?.let(errorsById::get)?.let { error ->
            val body = listOfNotNull(
                error.chosenAnswer?.let { "Chosen: $it" },
                error.correctAnswer?.let { "Correct: $it" },
                error.explanation.takeIf(String::isNotBlank),
            ).joinToString(" • ")
            return ReviewItemContent(
                error.explanation.ifBlank { error.category.name.replace('_', ' ') },
                body.takeIf(String::isNotBlank),
            )
        }
        item.topicId?.let(topicsById::get)?.let { topic ->
            return ReviewItemContent(topic.name, topic.description.takeIf(String::isNotBlank))
        }
        return ReviewItemContent(item.method.name.replace('_', ' '), null)
    }
}

@Composable
private fun WrongAnswerRow(
    error: ErrorEntry,
    priority: RemediationPriority,
    suggestion: com.mikeyphw.studyflow.core.domain.RemediationSuggestion,
    enabled: Boolean,
    onScheduleReview: () -> Unit,
    onResolve: () -> Unit,
) {
    StudyFlowSectionCard(
        title = pluralStringResource(
            R.plurals.error_entry_title,
            error.recurrenceCount,
            error.recurrenceCount,
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
    ) {
        StudyFlowStatusPill(text = priority.label())
        Text(
            suggestion.name.replace('_', ' ').lowercase().replaceFirstChar { char -> char.titlecase() },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        error.chosenAnswer?.let { chosen ->
            Text(stringResource(R.string.chosen_answer_value, chosen))
        }
        error.correctAnswer?.let { correct ->
            Text(stringResource(R.string.correct_answer_value, correct))
        }
        if (error.explanation.isNotBlank()) {
            Text(error.explanation, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onScheduleReview, enabled = enabled) {
                Text(stringResource(R.string.schedule_error_review))
            }
            OutlinedButton(onClick = onResolve, enabled = enabled) {
                Text(stringResource(R.string.resolve_error))
            }
        }
    }
}

@Composable
private fun RemediationPriority.label(): String = when (this) {
    RemediationPriority.LOW -> stringResource(R.string.priority_low)
    RemediationPriority.MEDIUM -> stringResource(R.string.priority_medium)
    RemediationPriority.HIGH -> stringResource(R.string.priority_high)
    RemediationPriority.CRITICAL -> stringResource(R.string.priority_critical)
}

private fun ReviewFilter.labelRes(): Int = when (this) {
    ReviewFilter.DUE -> R.string.filter_due
    ReviewFilter.UPCOMING -> R.string.filter_upcoming
    ReviewFilter.COMPLETED -> R.string.filter_completed
}
