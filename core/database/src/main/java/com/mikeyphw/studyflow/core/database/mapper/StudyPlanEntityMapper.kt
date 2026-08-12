package com.mikeyphw.studyflow.core.database.mapper

import com.mikeyphw.studyflow.core.database.entity.StudyPlanEntity
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyPlanStatus
import com.mikeyphw.studyflow.core.model.StudyType

fun StudyPlanEntity.asExternalModel(): StudyPlan = decodeModel("StudyPlanEntity") {
    StudyPlan(
        id = StudyPlanId(id),
        name = name,
        description = description,
        type = type.decodeEnum<StudyType>("StudyPlanEntityMapper.type"),
        status = status.decodeEnum<StudyPlanStatus>("StudyPlanEntityMapper.status"),
        examDateIso = examDateIso,
        weeklyGoalHours = weeklyGoalHours,
        progressPercent = progressPercent,
        studiedMinutesThisWeek = studiedMinutesThisWeek,
        questionsAnswered = questionsAnswered,
        averageAccuracyPercent = averageAccuracyPercent,
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
        trashedAtEpochMillis = trashedAtEpochMillis,
    )
}

fun StudyPlan.asEntity(nowEpochMillis: Long): StudyPlanEntity {
    val normalizedCreatedAt = createdAtEpochMillis.takeIf { it > 0L } ?: nowEpochMillis
    val normalizedUpdatedAt = updatedAtEpochMillis.takeIf { it > 0L } ?: nowEpochMillis
    return StudyPlanEntity(
        id = id.value,
        name = name,
        description = description,
        type = type.name,
        status = status.name,
        examDateIso = examDateIso,
        weeklyGoalHours = weeklyGoalHours,
        progressPercent = progressPercent,
        studiedMinutesThisWeek = studiedMinutesThisWeek,
        questionsAnswered = questionsAnswered,
        averageAccuracyPercent = averageAccuracyPercent,
        createdAtEpochMillis = normalizedCreatedAt,
        updatedAtEpochMillis = normalizedUpdatedAt,
        trashedAtEpochMillis = trashedAtEpochMillis,
    )
}
