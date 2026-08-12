package com.mikeyphw.studyflow.core.model

enum class StudyPlanStatus {
    DRAFT,
    ACTIVE,
    PAUSED,
    COMPLETED,
    ARCHIVED,
    TRASHED,
}

enum class StudyType {
    PUBLIC_EXAMINATION,
    CERTIFICATION,
    UNIVERSITY,
    ENTRANCE_EXAMINATION,
    FREE_STUDY,
}

data class StudyPlan(
    val id: StudyPlanId,
    val name: String,
    val description: String,
    val type: StudyType,
    val status: StudyPlanStatus,
    val examDateIso: String?,
    val weeklyGoalHours: Int,
    val progressPercent: Int,
    val studiedMinutesThisWeek: Int,
    val questionsAnswered: Int,
    val averageAccuracyPercent: Int,
    val createdAtEpochMillis: Long = 0L,
    val updatedAtEpochMillis: Long = createdAtEpochMillis,
    val trashedAtEpochMillis: Long? = null,
) {
    init {
        require(name.isNotBlank()) { "Study Plan name cannot be blank." }
        examDateIso?.let { date -> requireValidIsoDate(date, "Exam date") }
        require(weeklyGoalHours in 1..100) { "Weekly goal must be between 1 and 100 hours." }
        require(progressPercent in 0..100) { "Progress must be between 0 and 100 percent." }
        require(studiedMinutesThisWeek >= 0) { "Studied minutes cannot be negative." }
        require(questionsAnswered >= 0) { "Question count cannot be negative." }
        require(averageAccuracyPercent in 0..100) { "Accuracy must be between 0 and 100 percent." }
    }
}
