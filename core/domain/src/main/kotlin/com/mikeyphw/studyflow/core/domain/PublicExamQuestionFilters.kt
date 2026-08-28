package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.Question
import com.mikeyphw.studyflow.core.model.QuestionAuthorship
import com.mikeyphw.studyflow.core.model.QuestionId
import com.mikeyphw.studyflow.core.model.MockExam
import com.mikeyphw.studyflow.core.model.StudyTest
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.SubjectId
import com.mikeyphw.studyflow.core.model.TopicId

enum class QuestionAttemptOutcome { CORRECT, WRONG, UNSEEN }

data class PublicExamQuestionFilter(
    val examBoard: String? = null,
    val organization: String? = null,
    val role: String? = null,
    val subjectId: SubjectId? = null,
    val topicId: TopicId? = null,
    val yearFrom: Int? = null,
    val yearTo: Int? = null,
    val authorship: QuestionAuthorship? = null,
    val difficulty: Int? = null,
    val favorite: Boolean? = null,
    val disputed: Boolean? = null,
    val outcome: QuestionAttemptOutcome? = null,
) {
    init {
        require(yearFrom == null || yearFrom in 1900..2200)
        require(yearTo == null || yearTo in 1900..2200)
        require(yearFrom == null || yearTo == null || yearFrom <= yearTo)
        require(difficulty == null || difficulty in 1..5)
    }
}

object PublicExamQuestionFilters {
    fun apply(
        planId: StudyPlanId,
        questions: List<Question>,
        filter: PublicExamQuestionFilter,
        outcomes: Map<QuestionId, QuestionAttemptOutcome> = emptyMap(),
    ): List<Question> = questions.asSequence()
        .filter { it.studyPlanId == planId }
        .filter { filter.examBoard.matchesOptional(it.examBoard) }
        .filter { filter.organization.matchesOptional(it.organization) }
        .filter { filter.role.matchesOptional(it.role) }
        .filter { filter.subjectId == null || it.subjectId == filter.subjectId }
        .filter { filter.topicId == null || it.topicId == filter.topicId }
        .filter { filter.yearFrom == null || (it.year ?: Int.MIN_VALUE) >= filter.yearFrom }
        .filter { filter.yearTo == null || (it.year ?: Int.MAX_VALUE) <= filter.yearTo }
        .filter { filter.authorship == null || it.authorship == filter.authorship }
        .filter { filter.difficulty == null || it.difficulty == filter.difficulty }
        .filter { filter.favorite == null || it.favorite == filter.favorite }
        .filter { filter.disputed == null || it.disputed == filter.disputed }
        .filter { question ->
            val requested = filter.outcome ?: return@filter true
            val actual = outcomes[question.id] ?: QuestionAttemptOutcome.UNSEEN
            actual == requested
        }
        .sortedWith(compareByDescending<Question> { it.year ?: Int.MIN_VALUE }.thenBy { it.id.value })
        .toList()

    private fun String?.matchesOptional(value: String?): Boolean =
        this == null || value?.trim()?.equals(trim(), ignoreCase = true) == true
}


object PublicExamQuestionOutcomeEvidence {
    fun latest(
        questions: List<Question>,
        tests: List<StudyTest>,
        mockExams: List<MockExam>,
    ): Map<QuestionId, QuestionAttemptOutcome> {
        val latest = mutableMapOf<QuestionId, Pair<Long, QuestionAttemptOutcome>>()
        tests.forEach { test ->
            collect(PracticeAttemptCodec.decode(
                attemptId = test.id.value,
                title = test.title,
                mode = test.mode,
                questionCount = test.questionCount,
                configuration = test.configurationJson,
                questions = questions,
                startedAtEpochMillis = test.startedAtEpochMillis,
                submittedAtEpochMillis = test.submittedAtEpochMillis,
                timeLimitSeconds = test.timeLimitSeconds,
            ), latest)
        }
        mockExams.forEach { mock ->
            collect(PracticeAttemptCodec.decode(
                attemptId = mock.id.value,
                title = mock.title,
                mode = mock.mode,
                questionCount = mock.questionCount,
                configuration = mock.configurationJson,
                questions = questions,
                startedAtEpochMillis = mock.startedAtEpochMillis,
                submittedAtEpochMillis = mock.submittedAtEpochMillis,
                timeLimitSeconds = mock.timeLimitSeconds,
                negativeMarking = mock.negativeMarking,
                minimumScorePercent = mock.minimumScorePercent,
            ), latest)
        }
        return latest.mapValues { (_, value) -> value.second }
    }

    private fun collect(
        state: PracticeExecutionState,
        latest: MutableMap<QuestionId, Pair<Long, QuestionAttemptOutcome>>,
    ) {
        state.answers.forEach { answer ->
            val candidate = answer.answeredAtEpochMillis to if (answer.correct) {
                QuestionAttemptOutcome.CORRECT
            } else {
                QuestionAttemptOutcome.WRONG
            }
            val previous = latest[answer.questionId]
            if (previous == null || candidate.first >= previous.first) latest[answer.questionId] = candidate
        }
    }
}
