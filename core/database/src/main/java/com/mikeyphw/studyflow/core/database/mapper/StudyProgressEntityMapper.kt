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
import java.nio.charset.StandardCharsets
import android.util.Base64

private const val ListSeparator = "\n"

fun StudyGoalEntity.asExternalModel(): StudyGoal = StudyGoal(
    id = StudyGoalId(id),
    studyPlanId = StudyPlanId(studyPlanId),
    subjectId = subjectId?.let(::SubjectId),
    topicId = topicId?.let(::TopicId),
    type = enumValueOf<GoalType>(type),
    period = enumValueOf<GoalPeriod>(period),
    title = title,
    targetValue = targetValue,
    currentValue = currentValue,
    startsOnIso = startsOnIso,
    endsOnIso = endsOnIso,
    completedAtEpochMillis = completedAtEpochMillis,
    archived = archived,
)

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

fun StudyNoteEntity.asExternalModel(): StudyNote = StudyNote(
    id = StudyNoteId(id),
    studyPlanId = StudyPlanId(studyPlanId),
    subjectId = subjectId?.let(::SubjectId),
    topicId = topicId?.let(::TopicId),
    materialId = materialId?.let(::StudyMaterialId),
    type = enumValueOf<StudyNoteType>(type),
    title = title,
    content = content,
    pinned = pinned,
    tags = tagsJson.decodeStringList(),
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

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
    tagsJson = tags.encodeStringList(),
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

private fun List<String>.encodeStringList(): String = joinToString(ListSeparator) { value ->
    Base64.encodeToString(value.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
}

private fun String.decodeStringList(): List<String> = if (isBlank()) {
    emptyList()
} else {
    lineSequence().mapNotNull { encoded ->
        runCatching { String(Base64.decode(encoded, Base64.NO_WRAP), StandardCharsets.UTF_8) }.getOrNull()
    }.toList()
}
