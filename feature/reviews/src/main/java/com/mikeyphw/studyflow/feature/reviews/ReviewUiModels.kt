package com.mikeyphw.studyflow.feature.reviews

import com.mikeyphw.studyflow.core.domain.ReviewQueue
import com.mikeyphw.studyflow.core.model.ReviewItem

internal enum class ReviewFilter {
    DUE,
    UPCOMING,
    COMPLETED,
}

internal fun ReviewQueue.itemsFor(filter: ReviewFilter): List<ReviewItem> = when (filter) {
    ReviewFilter.DUE -> due
    ReviewFilter.UPCOMING -> upcoming
    ReviewFilter.COMPLETED -> completedToday
}

internal fun ReviewItem.kindLabelRes(): Int = when {
    flashcardId != null -> R.string.review_kind_flashcard
    errorEntryId != null -> R.string.review_kind_error
    questionId != null -> R.string.review_kind_question
    topicId != null -> R.string.review_kind_topic
    subjectId != null -> R.string.review_kind_subject
    else -> R.string.review_kind_manual
}
