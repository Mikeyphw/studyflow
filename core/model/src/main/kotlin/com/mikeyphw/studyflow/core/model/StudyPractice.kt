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
)

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
)

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
)
