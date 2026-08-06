package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.Question
import com.mikeyphw.studyflow.core.model.QuestionId
import com.mikeyphw.studyflow.core.model.ReviewItem
import com.mikeyphw.studyflow.core.model.ReviewStatus
import com.mikeyphw.studyflow.core.model.SubjectId
import com.mikeyphw.studyflow.core.model.TopicId

private const val DirectErrorWeight = 120
private const val OverdueReviewWeight = 80
private const val TopicMatchWeight = 48
private const val SubjectMatchWeight = 28
private const val RepeatedErrorWeight = 14
private const val FavoriteWeight = 8
private const val MinimumDefaultDrillQuestions = 3
private const val MaximumDefaultDrillQuestions = 20

data class WeaknessDrillRequest(
    val limit: Int = MaximumDefaultDrillQuestions,
    val minimumQuestions: Int = MinimumDefaultDrillQuestions,
    val includeFavoriteQuestions: Boolean = true,
    val includeSameTopicQuestions: Boolean = true,
    val includeSameSubjectQuestions: Boolean = true,
) {
    init {
        require(limit in 1..100) { "Weakness drill limit must be between 1 and 100." }
        require(minimumQuestions in 1..limit) { "Minimum question count must be within the drill limit." }
    }
}

data class WeaknessDrillPlan(
    val questions: List<Question>,
    val drivers: List<WeaknessDrillDriver>,
    val emptyReason: WeaknessDrillEmptyReason?,
) {
    val executable: Boolean = emptyReason == null && questions.isNotEmpty()
    val questionCount: Int = questions.size
    val driverCount: Int = drivers.size
}

data class WeaknessDrillDriver(
    val questionId: QuestionId?,
    val subjectId: SubjectId?,
    val topicId: TopicId?,
    val priority: RemediationPriority,
    val recurrenceCount: Int,
    val overdueReview: Boolean,
)

enum class WeaknessDrillEmptyReason {
    NO_QUESTIONS,
    NO_WEAK_SIGNALS,
    NOT_ENOUGH_MATCHING_QUESTIONS,
}

object WeaknessDrillGenerator {
    fun build(
        questions: List<Question>,
        remediations: List<WrongAnswerRemediation>,
        dueReviews: List<ReviewItem>,
        request: WeaknessDrillRequest = WeaknessDrillRequest(),
    ): WeaknessDrillPlan {
        val usableQuestions = questions.filter { question -> question.statement.isNotBlank() }
        if (usableQuestions.isEmpty()) {
            return WeaknessDrillPlan(emptyList(), emptyList(), WeaknessDrillEmptyReason.NO_QUESTIONS)
        }

        val drivers = buildDrivers(remediations, dueReviews)
        if (drivers.isEmpty()) {
            return WeaknessDrillPlan(emptyList(), emptyList(), WeaknessDrillEmptyReason.NO_WEAK_SIGNALS)
        }

        val ranked = usableQuestions
            .mapNotNull { question -> scoreQuestion(question, drivers, request)?.let { score -> score to question } }
            .sortedWith(
                compareByDescending<Pair<Int, Question>> { it.first }
                    .thenByDescending { it.second.updatedAtEpochMillis }
                    .thenBy { it.second.id.value },
            )
            .map { it.second }
            .distinctBy { it.id }
            .take(request.limit)

        val emptyReason = if (ranked.size < request.minimumQuestions) {
            WeaknessDrillEmptyReason.NOT_ENOUGH_MATCHING_QUESTIONS
        } else {
            null
        }
        return WeaknessDrillPlan(ranked, drivers, emptyReason)
    }

    private fun buildDrivers(
        remediations: List<WrongAnswerRemediation>,
        dueReviews: List<ReviewItem>,
    ): List<WeaknessDrillDriver> {
        val errorDrivers = remediations.map { remediation ->
            WeaknessDrillDriver(
                questionId = remediation.error.questionId,
                subjectId = remediation.error.subjectId,
                topicId = remediation.error.topicId,
                priority = remediation.priority,
                recurrenceCount = remediation.error.recurrenceCount,
                overdueReview = remediation.reviewIsDue,
            )
        }
        val reviewDrivers = dueReviews
            .filter { item -> item.status == ReviewStatus.DUE }
            .map { item ->
                WeaknessDrillDriver(
                    questionId = item.questionId,
                    subjectId = item.subjectId,
                    topicId = item.topicId,
                    priority = RemediationPriority.MEDIUM,
                    recurrenceCount = item.repetitionCount,
                    overdueReview = true,
                )
            }
        return (errorDrivers + reviewDrivers).distinctBy { driver ->
            listOf(
                driver.questionId?.value.orEmpty(),
                driver.subjectId?.value.orEmpty(),
                driver.topicId?.value.orEmpty(),
                driver.priority.name,
            ).joinToString("|")
        }
    }

    private fun scoreQuestion(
        question: Question,
        drivers: List<WeaknessDrillDriver>,
        request: WeaknessDrillRequest,
    ): Int? {
        var score = 0
        var matchedWeakSignal = false
        drivers.forEach { driver ->
            val recurrenceBonus = driver.recurrenceCount.coerceAtMost(8) * RepeatedErrorWeight
            if (driver.questionId == question.id) {
                matchedWeakSignal = true
                score += DirectErrorWeight + driver.priority.weight * 24 + recurrenceBonus
            }
            val sameTopic = driver.topicId != null && driver.topicId == question.topicId
            if (request.includeSameTopicQuestions && sameTopic) {
                matchedWeakSignal = true
                score += TopicMatchWeight + driver.priority.weight * 8 + recurrenceBonus / 2
            }
            val sameSubject = driver.subjectId != null && driver.subjectId == question.subjectId
            if (request.includeSameSubjectQuestions && sameSubject) {
                matchedWeakSignal = true
                score += SubjectMatchWeight + driver.priority.weight * 4 + recurrenceBonus / 4
            }
            if (driver.overdueReview && driver.questionId == question.id) {
                score += OverdueReviewWeight
            }
        }
        if (!matchedWeakSignal) return null
        if (request.includeFavoriteQuestions && question.favorite) score += FavoriteWeight
        return score + question.difficulty.coerceIn(1, 5)
    }
}
