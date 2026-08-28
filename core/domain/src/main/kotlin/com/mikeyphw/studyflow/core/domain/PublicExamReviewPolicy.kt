package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.ErrorCategory
import com.mikeyphw.studyflow.core.model.ReviewItem
import com.mikeyphw.studyflow.core.model.ReviewStatus
import kotlin.math.max

private const val DayMillis = 86_400_000L

data class PublicExamReviewPolicy(
    val intervalsDays: List<Int> = listOf(1, 7, 30),
) {
    init {
        require(intervalsDays.size in 1..8) { "Public-exam review policy needs between 1 and 8 intervals." }
        require(intervalsDays.all { it in 1..365 }) { "Review-policy intervals must be between 1 and 365 days." }
        require(intervalsDays.zipWithNext().all { (left, right) -> right >= left }) {
            "Review-policy intervals must be non-decreasing."
        }
    }

    fun initialDueAt(nowEpochMillis: Long, recurrenceCount: Int): Long {
        require(recurrenceCount >= 1) { "Error recurrence count must be positive." }
        val base = intervalsDays.first().toLong() * DayMillis
        val urgencyDivisor = when {
            recurrenceCount >= 5 -> 4L
            recurrenceCount >= 3 -> 2L
            else -> 1L
        }
        return nowEpochMillis + max(DayMillis / 4L, base / urgencyDivisor)
    }

    fun successor(current: ReviewItem, grade: Int, reviewedAtEpochMillis: Long): ReviewItem {
        require(grade in 0..5) { "Review grade must be between 0 and 5." }
        val nextRepetition = current.repetitionCount + 1
        val index = if (grade < 3) 0 else nextRepetition.coerceAtMost(intervalsDays.lastIndex)
        val interval = intervalsDays[index]
        return current.copy(
            status = ReviewStatus.SCHEDULED,
            dueAtEpochMillis = reviewedAtEpochMillis + interval.toLong() * DayMillis,
            intervalDays = interval,
            repetitionCount = nextRepetition,
            easeFactor = when {
                grade < 3 -> (current.easeFactor - 0.2).coerceAtLeast(1.3)
                grade == 5 -> (current.easeFactor + 0.1).coerceAtMost(3.0)
                else -> current.easeFactor
            },
            completedAtEpochMillis = null,
        )
    }
}

object PublicExamReviewPolicyCodec {
    private val arrayRegex = Regex("\\\"publicExamReviewDays\\\"\\s*:\\s*\\[([^]]*)]")

    fun decode(configurationJson: String): PublicExamReviewPolicy {
        val raw = arrayRegex.find(configurationJson)?.groupValues?.get(1) ?: return PublicExamReviewPolicy()
        val values = raw.split(',').mapNotNull { it.trim().toIntOrNull() }
        return runCatching { PublicExamReviewPolicy(values) }.getOrElse { PublicExamReviewPolicy() }
    }

    fun encode(configurationJson: String, policy: PublicExamReviewPolicy): String {
        val normalized = configurationJson.trim().ifBlank { "{}" }
        val replacement = "\"publicExamReviewDays\":[${policy.intervalsDays.joinToString(",")}]"
        if (arrayRegex.containsMatchIn(normalized)) {
            return normalized.replace(arrayRegex, replacement)
        }
        if (normalized == "{}") return "{$replacement}"
        require(normalized.startsWith('{') && normalized.endsWith('}')) {
            "Study Plan configuration must remain a JSON object."
        }
        val body = normalized.substring(1, normalized.lastIndex).trim()
        return if (body.isBlank()) "{$replacement}" else "{$body,$replacement}"
    }
}

enum class PublicExamRemediationAction {
    STUDY_TOPIC,
    REVIEW,
    COMPARE_CONCEPTS,
    TARGETED_QUESTION_DRILL,
    TIMED_SHORT_SET,
    CALCULATION_DRILL,
    TIMED_SIMULATION,
    VERIFY_SOURCE,
    REVIEW_EXPLANATION,
}

object PublicExamErrorRemediation {
    fun actionFor(category: ErrorCategory): PublicExamRemediationAction = when (category) {
        ErrorCategory.CONTENT_GAP -> PublicExamRemediationAction.STUDY_TOPIC
        ErrorCategory.FORGOTTEN_CONTENT -> PublicExamRemediationAction.REVIEW
        ErrorCategory.CONCEPT_CONFUSION -> PublicExamRemediationAction.COMPARE_CONCEPTS
        ErrorCategory.INTERPRETATION -> PublicExamRemediationAction.TARGETED_QUESTION_DRILL
        ErrorCategory.DISTRACTION -> PublicExamRemediationAction.TIMED_SHORT_SET
        ErrorCategory.CALCULATION -> PublicExamRemediationAction.CALCULATION_DRILL
        ErrorCategory.TIME_PRESSURE -> PublicExamRemediationAction.TIMED_SIMULATION
        ErrorCategory.DISPUTED_QUESTION -> PublicExamRemediationAction.VERIFY_SOURCE
        ErrorCategory.OTHER -> PublicExamRemediationAction.REVIEW_EXPLANATION
    }
}
