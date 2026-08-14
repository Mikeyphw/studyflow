package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.ErrorCategory
import com.mikeyphw.studyflow.core.model.ErrorEntry
import com.mikeyphw.studyflow.core.model.ReviewItem
import com.mikeyphw.studyflow.core.model.ReviewMethod
import com.mikeyphw.studyflow.core.model.ReviewStatus

private const val HighRecurrenceThreshold = 3
private const val CriticalRecurrenceThreshold = 5

object WrongAnswerRemediator {
    fun buildNotebook(
        errors: List<ErrorEntry>,
        reviewItems: List<ReviewItem>,
        nowEpochMillis: Long,
    ): WrongAnswerNotebook {
        val reviewsByError = reviewItems
            .filter { item -> item.errorEntryId != null && item.status != ReviewStatus.CANCELLED }
            .groupBy { item -> item.errorEntryId }
        val entries = errors.map { error ->
            val reviews = reviewsByError[error.id].orEmpty()
            val pendingReview = reviews
                .filter(ReviewDuePolicy::isActive)
                .minByOrNull(ReviewItem::dueAtEpochMillis)
                ?.let { item -> if (ReviewDuePolicy.isDue(item, nowEpochMillis)) item
                    .copy(status = ReviewStatus.DUE) else item }
            WrongAnswerRemediation(
                error = error,
                pendingReview = pendingReview,
                priority = priorityFor(error, nowEpochMillis, reviews),
                suggestion = suggestionFor(error),
            )
        }.sortedWith(
            compareByDescending<WrongAnswerRemediation> { it.priority.weight }
                .thenByDescending { it.error.recurrenceCount }
                .thenByDescending { it.error.updatedAtEpochMillis },
        )
        return WrongAnswerNotebook(entries)
    }

    fun priorityFor(
        error: ErrorEntry,
        nowEpochMillis: Long,
        linkedReviews: List<ReviewItem> = emptyList(),
    ): RemediationPriority {
        val hasOverdueReview = linkedReviews.any { item -> ReviewDuePolicy.isDue(item, nowEpochMillis) }
        return when {
            error.recurrenceCount >= CriticalRecurrenceThreshold -> RemediationPriority.CRITICAL
            error.category in conceptualCategories && error.recurrenceCount >= HighRecurrenceThreshold ->
                RemediationPriority.HIGH
            hasOverdueReview -> RemediationPriority.HIGH
            error.recurrenceCount >= HighRecurrenceThreshold -> RemediationPriority.MEDIUM
            else -> RemediationPriority.LOW
        }
    }

    fun suggestionFor(error: ErrorEntry): RemediationSuggestion = when (error.category) {
        ErrorCategory.CONTENT_GAP,
        ErrorCategory.FORGOTTEN_CONTENT,
        ErrorCategory.CONCEPT_CONFUSION,
        -> RemediationSuggestion.REVIEW_THEORY
        ErrorCategory.CALCULATION -> RemediationSuggestion.PRACTICE_SIMILAR_QUESTIONS
        ErrorCategory.INTERPRETATION -> RemediationSuggestion.RE_READ_STATEMENT
        ErrorCategory.TIME_PRESSURE -> RemediationSuggestion.TIMED_DRILL
        ErrorCategory.DISTRACTION -> RemediationSuggestion.SLOW_CHECKLIST
        ErrorCategory.DISPUTED_QUESTION -> RemediationSuggestion.VERIFY_SOURCE
        ErrorCategory.OTHER -> RemediationSuggestion.REVIEW_EXPLANATION
    }

    private val conceptualCategories = setOf(
        ErrorCategory.CONTENT_GAP,
        ErrorCategory.FORGOTTEN_CONTENT,
        ErrorCategory.CONCEPT_CONFUSION,
    )
}

data class WrongAnswerNotebook(
    val entries: List<WrongAnswerRemediation>,
) {
    val activeCount: Int = entries.size
    val criticalCount: Int = entries.count { it.priority == RemediationPriority.CRITICAL }
    val highPriorityCount: Int = entries.count { it.priority == RemediationPriority.HIGH }
    val repeatedCount: Int = entries.count { it.error.recurrenceCount > 1 }
}

data class WrongAnswerRemediation(
    val error: ErrorEntry,
    val pendingReview: ReviewItem?,
    val priority: RemediationPriority,
    val suggestion: RemediationSuggestion,
) {
    val hasPendingReview: Boolean = pendingReview != null && pendingReview.status != ReviewStatus.CANCELLED
    val reviewIsDue: Boolean = pendingReview?.status == ReviewStatus.DUE
    val method: ReviewMethod? = pendingReview?.method
}

enum class RemediationPriority(val weight: Int) {
    LOW(0),
    MEDIUM(1),
    HIGH(2),
    CRITICAL(3),
}

enum class RemediationSuggestion {
    REVIEW_THEORY,
    PRACTICE_SIMILAR_QUESTIONS,
    RE_READ_STATEMENT,
    TIMED_DRILL,
    SLOW_CHECKLIST,
    VERIFY_SOURCE,
    REVIEW_EXPLANATION,
}
