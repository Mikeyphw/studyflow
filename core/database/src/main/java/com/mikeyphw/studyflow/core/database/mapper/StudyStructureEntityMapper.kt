package com.mikeyphw.studyflow.core.database.mapper

import com.mikeyphw.studyflow.core.database.entity.ExamStageEntity
import com.mikeyphw.studyflow.core.database.entity.StudyTargetEntity
import com.mikeyphw.studyflow.core.database.entity.SubjectEntity
import com.mikeyphw.studyflow.core.database.entity.TopicEntity
import com.mikeyphw.studyflow.core.model.ContentProgressStatus
import com.mikeyphw.studyflow.core.model.ExamStage
import com.mikeyphw.studyflow.core.model.ExamStageId
import com.mikeyphw.studyflow.core.model.ExamStageType
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyTarget
import com.mikeyphw.studyflow.core.model.StudyTargetId
import com.mikeyphw.studyflow.core.model.Subject
import com.mikeyphw.studyflow.core.model.SubjectId
import com.mikeyphw.studyflow.core.model.Topic
import com.mikeyphw.studyflow.core.model.TopicId


fun StudyTargetEntity.asExternalModel(): StudyTarget = decodeModel("StudyTargetEntity") {
    StudyTarget(
        id = StudyTargetId(id),
        studyPlanId = StudyPlanId(studyPlanId),
        organization = organization,
        role = role,
        examBoard = examBoard,
        noticeUrl = noticeUrl,
        confirmedExamDateIso = confirmedExamDateIso,
        expectedExamDateIso = expectedExamDateIso,
        openings = openings,
        estimatedCutoffPercent = estimatedCutoffPercent,
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )
}

fun StudyTarget.asEntity(): StudyTargetEntity = StudyTargetEntity(
    id = id.value,
    studyPlanId = studyPlanId.value,
    organization = organization,
    role = role,
    examBoard = examBoard,
    noticeUrl = noticeUrl,
    confirmedExamDateIso = confirmedExamDateIso,
    expectedExamDateIso = expectedExamDateIso,
    openings = openings,
    estimatedCutoffPercent = estimatedCutoffPercent,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

fun ExamStageEntity.asExternalModel(): ExamStage = decodeModel("ExamStageEntity") {
    ExamStage(
        id = ExamStageId(id),
        studyPlanId = StudyPlanId(studyPlanId),
        studyTargetId = StudyTargetId(studyTargetId),
        name = name,
        type = type.decodeEnum<ExamStageType>("StudyStructureEntityMapper.type"),
        scheduledDateIso = scheduledDateIso,
        weight = weight,
        minimumScorePercent = minimumScorePercent,
        position = position,
    )
}

fun ExamStage.asEntity(): ExamStageEntity = ExamStageEntity(
    id = id.value,
    studyPlanId = studyPlanId.value,
    studyTargetId = studyTargetId.value,
    name = name,
    type = type.name,
    scheduledDateIso = scheduledDateIso,
    weight = weight,
    minimumScorePercent = minimumScorePercent,
    position = position,
)

fun SubjectEntity.asExternalModel(): Subject = decodeModel("SubjectEntity") {
    Subject(
        id = SubjectId(id),
        studyPlanId = StudyPlanId(studyPlanId),
        name = name,
        description = description,
        colorArgb = colorArgb,
        priority = priority,
        examWeight = examWeight,
        estimatedMinutes = estimatedMinutes,
        progressPercent = progressPercent,
        accuracyPercent = accuracyPercent,
        status = status.decodeEnum<ContentProgressStatus>("StudyStructureEntityMapper.status"),
        position = position,
        archived = archived,
        examStageId = examStageId?.let(::ExamStageId),
    )
}

fun Subject.asEntity(): SubjectEntity = SubjectEntity(
    id = id.value,
    studyPlanId = studyPlanId.value,
    name = name,
    description = description,
    colorArgb = colorArgb,
    priority = priority,
    examWeight = examWeight,
    estimatedMinutes = estimatedMinutes,
    progressPercent = progressPercent,
    accuracyPercent = accuracyPercent,
    status = status.name,
    position = position,
    archived = archived,
    examStageId = examStageId?.value,
)

fun TopicEntity.asExternalModel(): Topic = decodeModel("TopicEntity") {
    Topic(
        id = TopicId(id),
        studyPlanId = StudyPlanId(studyPlanId),
        subjectId = SubjectId(subjectId),
        parentTopicId = parentTopicId?.let(::TopicId),
        name = name,
        description = description,
        priority = priority,
        difficulty = difficulty,
        examIncidencePercent = examIncidencePercent,
        estimatedMinutes = estimatedMinutes,
        progressPercent = progressPercent,
        masteryPercent = masteryPercent,
        status = status.decodeEnum<ContentProgressStatus>("StudyStructureEntityMapper.status"),
        position = position,
        archived = archived,
        examStageId = examStageId?.let(::ExamStageId),
    )
}

fun Topic.asEntity(): TopicEntity = TopicEntity(
    id = id.value,
    studyPlanId = studyPlanId.value,
    subjectId = subjectId.value,
    parentTopicId = parentTopicId?.value,
    name = name,
    description = description,
    priority = priority,
    difficulty = difficulty,
    examIncidencePercent = examIncidencePercent,
    estimatedMinutes = estimatedMinutes,
    progressPercent = progressPercent,
    masteryPercent = masteryPercent,
    status = status.name,
    position = position,
    archived = archived,
    examStageId = examStageId?.value,
)
