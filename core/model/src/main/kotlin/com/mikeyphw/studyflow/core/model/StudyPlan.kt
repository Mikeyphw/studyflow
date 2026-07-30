package com.mikeyphw.studyflow.core.model

@JvmInline
value class StudyPlanId(val value: String)

enum class StudyPlanStatus {
    DRAFT,
    ACTIVE,
    PAUSED,
    COMPLETED,
    ARCHIVED,
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
)
