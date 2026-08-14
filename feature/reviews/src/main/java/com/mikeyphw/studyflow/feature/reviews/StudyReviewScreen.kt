package com.mikeyphw.studyflow.feature.reviews

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.mikeyphw.studyflow.core.model.ErrorEntry
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
import androidx.compose.runtime.rememberCoroutineScope

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
    val syllabus by content.observeSyllabus(plan.id)
        .map<StudySyllabus, StudySyllabus?> { current -> current }
        .collectAsStateWithLifecycle(initialValue = null)
    val coroutineScope = rememberCoroutineScope()
    var filter by rememberSaveable { mutableStateOf(ReviewFilter.DUE) }
    var front by rememberSaveable { mutableStateOf("") }
    var back by rememberSaveable { mutableStateOf("") }

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ReviewSummaryCard(queue = queue)
            WrongAnswerNotebookCard(
                errors = queue.errors,
                reviewItems = queue.due + queue.upcoming,
                onScheduleReview = { error ->
                    coroutineScope.launch { review.scheduleErrorReview(plan.id, error.id) }
                },
                onResolve = { error ->
                    coroutineScope.launch { review.resolveErrorEntry(plan.id, error.id) }
                },
            )
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
                        createFlashcard(
                            coroutineScope = coroutineScope,
                            review = review,
                            plan = plan,
                            front = front,
                            back = back,
                            onCreated = {
                                front = ""
                                back = ""
                            },
                        )
                    },
                    enabled = front.isNotBlank() && back.isNotBlank(),
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Text(text = stringResource(R.string.create_flashcard))
                }
            }
            StudyFlowSectionCard(title = stringResource(R.string.review_queue)) {
                ReviewFilterRow(selected = filter, onSelected = { filter = it })
                val items = queue.itemsFor(filter)
                if (items.isEmpty()) {
                    Text(
                        text = stringResource(R.string.no_reviews_for_filter),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    items.forEach { item ->
                        ReviewItemRow(
                            item = item,
                            content = reviewItemContent(
                                item = item,
                                questions = questions,
                                flashcards = queue.flashcards,
                                errors = queue.errors,
                                topics = syllabus?.topics.orEmpty(),
                            ),
                            onAgain = {
                                coroutineScope.launch { review.gradeReview(plan.id, item.id, grade = 1) }
                            },
                            onGood = {
                                coroutineScope.launch { review.gradeReview(plan.id, item.id, grade = 4) }
                            },
                            onEasy = {
                                coroutineScope.launch { review.gradeReview(plan.id, item.id, grade = 5) }
                            },
                            onSkip = {
                                coroutineScope.launch { review.skipReview(plan.id, item.id) }
                            },
                        )
                    }
                }
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
            OutlinedButton(onClick = onAgain) {
                Icon(Icons.Outlined.Close, contentDescription = null)
                Text(text = stringResource(R.string.again))
            }
            Button(onClick = onGood) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null)
                Text(text = stringResource(R.string.good))
            }
            OutlinedButton(onClick = onEasy) {
                Text(text = stringResource(R.string.easy))
            }
            OutlinedButton(onClick = onSkip) {
                Text(text = stringResource(R.string.skip))
            }
        }
    }
}

private data class ReviewItemContent(val title: String, val body: String?)

private fun reviewItemContent(
    item: ReviewItem,
    questions: List<Question>,
    flashcards: List<Flashcard>,
    errors: List<ErrorEntry>,
    topics: List<Topic>,
): ReviewItemContent {
    item.questionId?.let { id ->
        questions.firstOrNull { it.id == id }?.let { return ReviewItemContent(it.statement, null) }
    }
    item.flashcardId?.let { id ->
        flashcards.firstOrNull { it.id == id }?.let { return ReviewItemContent(it.front, it.back) }
    }
    item.errorEntryId?.let { id ->
        errors.firstOrNull { it.id == id }?.let { error ->
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
    }
    item.topicId?.let { id ->
        topics.firstOrNull { it.id == id }?.let { topic ->
            return ReviewItemContent(topic.name, topic.description.takeIf(String::isNotBlank))
        }
    }
    return ReviewItemContent(item.method.name.replace('_', ' '), null)
}

private fun createFlashcard(
    coroutineScope: CoroutineScope,
    review: ManageStudyReview,
    plan: StudyPlan,
    front: String,
    back: String,
    onCreated: () -> Unit,
) {
    coroutineScope.launch {
        review.createFlashcard(
            id = FlashcardId(UUID.randomUUID().toString()),
            studyPlanId = plan.id,
            subjectId = null,
            topicId = null,
            type = FlashcardType.BASIC,
            front = front,
            back = back,
            clozeText = null,
            tags = emptyList(),
        )
        onCreated()
    }
}


@Composable
private fun WrongAnswerNotebookCard(
    errors: List<ErrorEntry>,
    reviewItems: List<ReviewItem>,
    onScheduleReview: (ErrorEntry) -> Unit,
    onResolve: (ErrorEntry) -> Unit,
) {
    StudyFlowSectionCard(title = stringResource(R.string.wrong_answer_notebook)) {
        if (errors.isEmpty()) {
            Text(
                text = stringResource(R.string.no_wrong_answers),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                errors.sortedWith(
                    compareByDescending<ErrorEntry> { it.recurrenceCount }
                        .thenByDescending { it.updatedAtEpochMillis },
                ).forEach { error ->
                    val priority = com.mikeyphw.studyflow.core.domain.WrongAnswerRemediator.priorityFor(
                        error = error,
                        nowEpochMillis = System.currentTimeMillis(),
                        linkedReviews = reviewItems.filter { it.errorEntryId == error.id },
                    )
                    WrongAnswerRow(
                        error = error,
                        priority = priority,
                        suggestion = com.mikeyphw.studyflow.core.domain.WrongAnswerRemediator.suggestionFor(error),
                        onScheduleReview = { onScheduleReview(error) },
                        onResolve = { onResolve(error) },
                    )
                }
            }
        }
    }
}

@Composable
private fun WrongAnswerRow(
    error: ErrorEntry,
    priority: RemediationPriority,
    suggestion: com.mikeyphw.studyflow.core.domain.RemediationSuggestion,
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
            Button(onClick = onScheduleReview) {
                Text(stringResource(R.string.schedule_error_review))
            }
            OutlinedButton(onClick = onResolve) {
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
