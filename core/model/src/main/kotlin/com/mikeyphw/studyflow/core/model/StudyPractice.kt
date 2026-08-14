package com.mikeyphw.studyflow.core.model

enum class QuestionType {
    MULTIPLE_CHOICE,
    TRUE_FALSE,
    SHORT_ANSWER,
    ESSAY,
}

enum class TestMode {
    PRACTICE,
    IMMEDIATE_FEEDBACK,
    EXAM,
}

data class Question(
    val id: QuestionId,
    val studyPlanId: StudyPlanId,
    val subjectId: SubjectId?,
    val topicId: TopicId?,
    val type: QuestionType,
    val statement: String,
    val options: List<String>,
    val correctAnswer: String,
    val explanation: String,
    val examBoard: String?,
    val source: String?,
    val year: Int?,
    val difficulty: Int,
    val favorite: Boolean,
    val disputed: Boolean,
    val tags: List<String>,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
) {
    init {
        require(statement.isNotBlank()) { "Question statement cannot be blank." }
        require(correctAnswer.isNotBlank()) { "Correct answer cannot be blank." }
        require(difficulty in 1..5) { "Question difficulty must be between 1 and 5." }
        require(year == null || year in 1900..2200) { "Question year is outside the supported range." }
        if (type == QuestionType.MULTIPLE_CHOICE) {
            require(options.size >= 2) { "Multiple-choice questions need at least two options." }
            require(options.any { option -> option.trim().equals(correctAnswer.trim(), ignoreCase = true) }) {
                "Multiple-choice correct answer must be one of the options."
            }
        }
    }
}

data class StudyTest(
    val id: StudyTestId,
    val studyPlanId: StudyPlanId,
    val title: String,
    val mode: TestMode,
    val timeLimitSeconds: Long?,
    val questionCount: Int,
    val answeredCount: Int,
    val correctCount: Int,
    val startedAtEpochMillis: Long?,
    val submittedAtEpochMillis: Long?,
    val configurationJson: String,
) {
    init {
        require(title.isNotBlank()) { "Study test title cannot be blank." }
        require(timeLimitSeconds == null || timeLimitSeconds > 0) { "Time limit must be positive." }
        require(questionCount >= 0) { "Question count cannot be negative." }
        require(answeredCount in 0..questionCount) { "Answered count is outside question count." }
        require(correctCount in 0..answeredCount) { "Correct count is outside answered count." }
    }
}

data class MockExam(
    val id: MockExamId,
    val studyPlanId: StudyPlanId,
    val title: String,
    val mode: TestMode,
    val timeLimitSeconds: Long,
    val questionCount: Int,
    val answeredCount: Int,
    val correctCount: Int,
    val scorePercent: Double?,
    val negativeMarking: Double,
    val minimumScorePercent: Int?,
    val startedAtEpochMillis: Long?,
    val submittedAtEpochMillis: Long?,
    val configurationJson: String,
) {
    init {
        require(title.isNotBlank()) { "Mock exam title cannot be blank." }
        require(timeLimitSeconds > 0) { "Mock exam time limit must be positive." }
        require(questionCount > 0) { "Mock exam must contain at least one question." }
        require(answeredCount in 0..questionCount) { "Answered count is outside question count." }
        require(correctCount in 0..answeredCount) { "Correct count is outside answered count." }
        require(scorePercent == null || scorePercent in 0.0..100.0) { "Mock exam score must be 0 to 100." }
        require(negativeMarking.isFinite() && negativeMarking >= 0.0) {
            "Negative marking must be finite and non-negative."
        }
        require(minimumScorePercent == null || minimumScorePercent in 0..100) { "Minimum score must be 0 to 100." }
    }
}
