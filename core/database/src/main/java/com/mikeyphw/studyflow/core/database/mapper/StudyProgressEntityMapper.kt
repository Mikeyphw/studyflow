package com.mikeyphw.studyflow.core.database.mapper

import com.mikeyphw.studyflow.core.database.entity.StudyGoalEntity
import com.mikeyphw.studyflow.core.database.entity.StudyNoteEntity
import com.mikeyphw.studyflow.core.model.GoalPeriod
import com.mikeyphw.studyflow.core.model.GoalType
import com.mikeyphw.studyflow.core.model.StudyGoal
import com.mikeyphw.studyflow.core.model.StudyGoalId
import com.mikeyphw.studyflow.core.model.StudyMaterialId
import com.mikeyphw.studyflow.core.model.StudyNote
import com.mikeyphw.studyflow.core.model.StudyNoteId
import com.mikeyphw.studyflow.core.model.StudyNoteType
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.SubjectId
import com.mikeyphw.studyflow.core.model.TopicId

fun StudyGoalEntity.asExternalModel(): StudyGoal = decodeModel("StudyGoalEntity") {
    StudyGoal(
        id = StudyGoalId(id),
        studyPlanId = StudyPlanId(studyPlanId),
        subjectId = subjectId?.let(::SubjectId),
        topicId = topicId?.let(::TopicId),
        type = type.decodeEnum<GoalType>("StudyProgressEntityMapper.type"),
        period = period.decodeEnum<GoalPeriod>("StudyProgressEntityMapper.period"),
        title = title,
        targetValue = targetValue,
        currentValue = currentValue,
        startsOnIso = startsOnIso,
        endsOnIso = endsOnIso,
        completedAtEpochMillis = completedAtEpochMillis,
        archived = archived,
    )
}

fun StudyGoal.asEntity(): StudyGoalEntity = StudyGoalEntity(
    id = id.value,
    studyPlanId = studyPlanId.value,
    subjectId = subjectId?.value,
    topicId = topicId?.value,
    type = type.name,
    period = period.name,
    title = title,
    targetValue = targetValue,
    currentValue = currentValue,
    startsOnIso = startsOnIso,
    endsOnIso = endsOnIso,
    completedAtEpochMillis = completedAtEpochMillis,
    archived = archived,
)

fun StudyNoteEntity.asExternalModel(): StudyNote = decodeModel("StudyNoteEntity") {
    StudyNote(
        id = StudyNoteId(id),
        studyPlanId = StudyPlanId(studyPlanId),
        subjectId = subjectId?.let(::SubjectId),
        topicId = topicId?.let(::TopicId),
        materialId = materialId?.let(::StudyMaterialId),
        type = type.decodeEnum<StudyNoteType>("StudyProgressEntityMapper.type"),
        title = title,
        content = content,
        pinned = pinned,
        tags = tagsJson.decodeStringListStable("StudyProgressEntityMapper.tagsJson"),
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )
}

fun StudyNote.asEntity(): StudyNoteEntity = StudyNoteEntity(
    id = id.value,
    studyPlanId = studyPlanId.value,
    subjectId = subjectId?.value,
    topicId = topicId?.value,
    materialId = materialId?.value,
    type = type.name,
    title = title,
    content = content,
    pinned = pinned,
    tagsJson = tags.encodeStringListStable(),
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)
