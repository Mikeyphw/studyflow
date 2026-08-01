package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.MockExam
import com.mikeyphw.studyflow.core.model.Question
import com.mikeyphw.studyflow.core.model.StudyTest
import com.mikeyphw.studyflow.core.model.SubjectId
import com.mikeyphw.studyflow.core.model.TopicId
import kotlin.math.roundToInt

data class PracticeSummary(
    val questionCount: Int,
    val favoriteCount: Int,
    val disputedCount: Int,
    val testCount: Int,
    val mockExamCount: Int,
    val answeredCount: Int,
    val correctCount: Int,
    val accuracyPercent: Int,
)

data class PracticeDeckRequest(
    val subjectId: SubjectId? = null,
    val topicId: TopicId? = null,
    val favoriteOnly: Boolean = false,
    val disputedOnly: Boolean = false,
    val minimumDifficulty: Int? = null,
    val limit: Int = 20,
)

object PracticeAnalytics {
    fun summarize(
        questions: List<Question>,
        tests: List<StudyTest>,
        mockExams: List<MockExam>,
    ): PracticeSummary {
        val answered = tests.sumOf { it.answeredCount } + mockExams.sumOf { it.answeredCount }
        val correct = tests.sumOf { it.correctCount } + mockExams.sumOf { it.correctCount }
        return PracticeSummary(
            questionCount = questions.size,
            favoriteCount = questions.count { it.favorite },
            disputedCount = questions.count { it.disputed },
            testCount = tests.size,
            mockExamCount = mockExams.size,
            answeredCount = answered,
            correctCount = correct,
            accuracyPercent = accuracyPercent(correct, answered),
        )
    }

    fun buildDeck(questions: List<Question>, request: PracticeDeckRequest): List<Question> {
        require(request.limit in 1..200) { "Question deck size must be between 1 and 200." }
        return questions.asSequence()
            .filter { request.subjectId == null || it.subjectId == request.subjectId }
            .filter { request.topicId == null || it.topicId == request.topicId }
            .filter { !request.favoriteOnly || it.favorite }
            .filter { !request.disputedOnly || it.disputed }
            .filter { request.minimumDifficulty == null || it.difficulty >= request.minimumDifficulty }
            .sortedWith(compareByDescending<Question> { it.disputed }.thenByDescending { it.updatedAtEpochMillis })
            .take(request.limit)
            .toList()
    }

    fun accuracyPercent(correct: Int, answered: Int): Int {
        require(correct >= 0) { "Correct answers cannot be negative." }
        require(answered >= 0) { "Answered questions cannot be negative." }
        require(correct <= answered) { "Correct answers must not exceed answered questions." }
        return if (answered == 0) 0 else ((correct.toDouble() / answered.toDouble()) * 100).roundToInt()
    }
}
