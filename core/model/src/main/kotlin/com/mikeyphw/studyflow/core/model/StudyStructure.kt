package com.mikeyphw.studyflow.core.model

enum class ExamStageType {
    OBJECTIVE,
    WRITTEN,
    ESSAY,
    PRACTICAL,
    PHYSICAL,
    ORAL,
    TITLES,
    OTHER,
}

enum class ContentProgressStatus {
    NOT_STARTED,
    STUDYING,
    REVIEWING,
    MASTERED,
    ARCHIVED,
}

data class StudyTarget(
    val id: StudyTargetId,
    val studyPlanId: StudyPlanId,
    val organization: String,
    val role: String,
    val examBoard: String,
    val noticeUrl: String?,
    val confirmedExamDateIso: String?,
    val expectedExamDateIso: String?,
    val openings: Int?,
    val estimatedCutoffPercent: Int?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

data class ExamStage(
    val id: ExamStageId,
    val studyPlanId: StudyPlanId,
    val studyTargetId: StudyTargetId,
    val name: String,
    val type: ExamStageType,
    val scheduledDateIso: String?,
    val weight: Double,
    val minimumScorePercent: Int?,
    val position: Int,
)

data class Subject(
    val id: SubjectId,
    val studyPlanId: StudyPlanId,
    val name: String,
    val description: String,
    val colorArgb: Long?,
    val priority: Int,
    val examWeight: Double,
    val estimatedMinutes: Int,
    val progressPercent: Int,
    val accuracyPercent: Int,
    val status: ContentProgressStatus,
    val position: Int,
    val archived: Boolean,
)

data class Topic(
    val id: TopicId,
    val studyPlanId: StudyPlanId,
    val subjectId: SubjectId,
    val parentTopicId: TopicId?,
    val name: String,
    val description: String,
    val priority: Int,
    val difficulty: Int,
    val examIncidencePercent: Int?,
    val estimatedMinutes: Int,
    val progressPercent: Int,
    val masteryPercent: Int,
    val status: ContentProgressStatus,
    val position: Int,
    val archived: Boolean,
)
