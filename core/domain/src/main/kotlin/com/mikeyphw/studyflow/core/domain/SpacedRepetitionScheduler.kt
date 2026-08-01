package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.ReviewItem
import com.mikeyphw.studyflow.core.model.ReviewStatus
import kotlin.math.max
import kotlin.math.roundToInt

private const val DayMillis = 86_400_000L
private const val MinimumEaseFactor = 1.3

object SpacedRepetitionScheduler {
    fun schedule(
        item: ReviewItem,
        grade: Int,
        reviewedAtEpochMillis: Long,
    ): ReviewItem {
        require(grade in 0..5) { "Review grade must be between 0 and 5." }
        val nextEase = nextEaseFactor(item.easeFactor, grade)
        val nextRepetition = if (grade < 3) 0 else item.repetitionCount + 1
        val nextInterval = nextIntervalDays(item.intervalDays, nextRepetition, nextEase, grade)
        return item.copy(
            status = ReviewStatus.SCHEDULED,
            dueAtEpochMillis = reviewedAtEpochMillis + nextInterval * DayMillis,
            intervalDays = nextInterval,
            repetitionCount = nextRepetition,
            easeFactor = nextEase,
            completedAtEpochMillis = reviewedAtEpochMillis,
        )
    }

    fun initialReview(item: ReviewItem, createdAtEpochMillis: Long): ReviewItem = item.copy(
        status = ReviewStatus.DUE,
        dueAtEpochMillis = createdAtEpochMillis,
        intervalDays = 0,
        repetitionCount = 0,
        easeFactor = 2.5,
        completedAtEpochMillis = null,
    )

    private fun nextEaseFactor(current: Double, grade: Int): Double {
        val qualityGap = 5 - grade
        val adjusted = current + (0.1 - qualityGap * (0.08 + qualityGap * 0.02))
        return max(MinimumEaseFactor, adjusted)
    }

    private fun nextIntervalDays(
        currentInterval: Int,
        nextRepetition: Int,
        easeFactor: Double,
        grade: Int,
    ): Int = when {
        grade < 3 -> 1
        nextRepetition <= 1 -> 1
        nextRepetition == 2 -> 3
        else -> max(4, (max(1, currentInterval) * easeFactor).roundToInt())
    }
}

data class ReviewQueue(
    val due: List<ReviewItem>,
    val upcoming: List<ReviewItem>,
    val completedToday: List<ReviewItem>,
    val flashcards: List<com.mikeyphw.studyflow.core.model.Flashcard>,
    val errors: List<com.mikeyphw.studyflow.core.model.ErrorEntry>,
) {
    val dueCount: Int = due.size
    val upcomingCount: Int = upcoming.size
    val flashcardCount: Int = flashcards.size
    val errorCount: Int = errors.size
}

data class ReviewSummary(
    val dueCount: Int,
    val upcomingCount: Int,
    val flashcardCount: Int,
    val errorCount: Int,
    val averageEaseFactor: Double,
)
