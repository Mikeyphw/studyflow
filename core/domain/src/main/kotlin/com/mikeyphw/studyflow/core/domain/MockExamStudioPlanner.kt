package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.Question
import com.mikeyphw.studyflow.core.model.QuestionType

private const val DefaultQuestionCount = 50
private const val DefaultTimeLimitMinutes = 120
private const val DefaultMinimumScorePercent = 60
private const val MaxQuestionCount = 120

/**
 * Builds a deterministic mock-exam blueprint without mutating storage.
 *
 * The planner keeps the domain output language-neutral: UI layers localize warnings and labels.
 */
object MockExamStudioPlanner {
    fun preview(
        questions: List<Question>,
        request: MockExamStudioRequest = MockExamStudioRequest(),
    ): MockExamStudioBlueprint {
        val normalized = request.normalized()
        val candidates = questions
            .filter { question -> question.studyPlanId == normalized.planQuestionOwner(questions) }
            .filter { question -> question.isExamEligible() }
            .map { question -> MockExamCandidate(question, question.scoreFor(normalized)) }
            .filter { candidate -> candidate.score > 0 }
            .sortedWith(compareByDescending<MockExamCandidate> { it.score }
                .thenByDescending { it.question.updatedAtEpochMillis }
                .thenBy { it.question.id.value })
        val selected = candidates.take(normalized.questionCount).map(MockExamCandidate::question)
        val sections = selected
            .groupBy { question -> question.subjectId?.value ?: UnassignedSection }
            .map { (sectionId, sectionQuestions) ->
                MockExamSection(
                    key = sectionId,
                    questionCount = sectionQuestions.size,
                    averageDifficulty = sectionQuestions.map(Question::difficulty).averageOrZero(),
                )
            }
            .sortedWith(compareByDescending<MockExamSection> { it.questionCount }.thenBy { it.key })
        return MockExamStudioBlueprint(
            title = normalized.title,
            questionCount = selected.size,
            requestedQuestionCount = normalized.questionCount,
            timeLimitSeconds = normalized.timeLimitMinutes * 60L,
            minimumScorePercent = normalized.minimumScorePercent,
            negativeMarking = normalized.negativeMarking,
            selectedQuestions = selected,
            sections = sections,
            warnings = warningsFor(questions, selected, normalized),
        )
    }

    private fun warningsFor(
        allQuestions: List<Question>,
        selected: List<Question>,
        request: MockExamStudioRequest,
    ): List<MockExamStudioWarning> = buildList {
        if (allQuestions.isEmpty()) add(MockExamStudioWarning.NO_QUESTIONS)
        if (allQuestions.any { !it.isExamEligible() }) add(MockExamStudioWarning.INELIGIBLE_QUESTIONS_SKIPPED)
        if (selected.size < request.questionCount) add(MockExamStudioWarning.NOT_ENOUGH_MATCHING_QUESTIONS)
        if (selected.mapNotNull { it.subjectId }.distinct().size <= 1 && selected.size >= 2) {
            add(MockExamStudioWarning.NARROW_SUBJECT_COVERAGE)
        }
    }.distinct()

    private fun Question.scoreFor(request: MockExamStudioRequest): Int {
        var score = 1
        if (disputed && !request.includeDisputed) return 0
        if (request.examBoard != null && examBoard?.equals(request.examBoard, ignoreCase = true) != true) return 0
        if (request.source != null && source?.equals(request.source, ignoreCase = true) != true) return 0
        if (request.tags.isNotEmpty() && tags.none { tag -> request.tags.any { it.equals(tag, ignoreCase = true) } }) {
            return 0
        }
        score += difficulty.coerceIn(1, 5) * 4
        if (type == QuestionType.MULTIPLE_CHOICE) score += 6
        if (favorite) score += 3
        if (disputed) score += 2
        if (examBoard != null) score += 2
        if (source != null) score += 1
        if (year != null) score += 1
        return score
    }

    private fun Question.isExamEligible(): Boolean = correctAnswer.isNotBlank() && type != QuestionType.ESSAY

    private fun MockExamStudioRequest.planQuestionOwner(questions: List<Question>) =
        questions.firstOrNull()?.studyPlanId

    private fun MockExamStudioRequest.normalized(): MockExamStudioRequest = copy(
        title = title.trim().ifBlank { "Mock exam" },
        questionCount = questionCount.coerceIn(1, MaxQuestionCount),
        timeLimitMinutes = timeLimitMinutes.coerceAtLeast(1),
        minimumScorePercent = minimumScorePercent?.coerceIn(0, 100),
        negativeMarking = negativeMarking.coerceAtLeast(0.0),
        examBoard = examBoard?.trim()?.ifBlank { null },
        source = source?.trim()?.ifBlank { null },
        tags = tags.map(String::trim).filter(String::isNotBlank).distinct(),
    )

    private fun List<Int>.averageOrZero(): Double = if (isEmpty()) 0.0 else average()

    private const val UnassignedSection = "general"
}

data class MockExamStudioRequest(
    val title: String = "Mock exam",
    val questionCount: Int = DefaultQuestionCount,
    val timeLimitMinutes: Int = DefaultTimeLimitMinutes,
    val minimumScorePercent: Int? = DefaultMinimumScorePercent,
    val negativeMarking: Double = 0.0,
    val examBoard: String? = null,
    val source: String? = null,
    val tags: List<String> = emptyList(),
    val includeDisputed: Boolean = false,
)

data class MockExamStudioBlueprint(
    val title: String,
    val questionCount: Int,
    val requestedQuestionCount: Int,
    val timeLimitSeconds: Long,
    val minimumScorePercent: Int?,
    val negativeMarking: Double,
    val selectedQuestions: List<Question>,
    val sections: List<MockExamSection>,
    val warnings: List<MockExamStudioWarning>,
) {
    val executable: Boolean get() = selectedQuestions.isNotEmpty()
    val timeLimitMinutes: Int get() = (timeLimitSeconds / 60L).toInt()
}

data class MockExamSection(
    val key: String,
    val questionCount: Int,
    val averageDifficulty: Double,
)

data class MockExamCandidate(
    val question: Question,
    val score: Int,
)

enum class MockExamStudioWarning {
    NO_QUESTIONS,
    INELIGIBLE_QUESTIONS_SKIPPED,
    NOT_ENOUGH_MATCHING_QUESTIONS,
    NARROW_SUBJECT_COVERAGE,
}
