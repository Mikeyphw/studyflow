package com.mikeyphw.studyflow.core.database.mapper

import com.mikeyphw.studyflow.core.database.entity.ErrorEntryEntity
import com.mikeyphw.studyflow.core.database.entity.FlashcardEntity
import com.mikeyphw.studyflow.core.database.entity.ReviewItemEntity
import com.mikeyphw.studyflow.core.model.ErrorCategory
import com.mikeyphw.studyflow.core.model.ErrorEntry
import com.mikeyphw.studyflow.core.model.ErrorEntryId
import com.mikeyphw.studyflow.core.model.Flashcard
import com.mikeyphw.studyflow.core.model.FlashcardId
import com.mikeyphw.studyflow.core.model.FlashcardType
import com.mikeyphw.studyflow.core.model.QuestionId
import com.mikeyphw.studyflow.core.model.ReviewItem
import com.mikeyphw.studyflow.core.model.ReviewItemId
import com.mikeyphw.studyflow.core.model.ReviewMethod
import com.mikeyphw.studyflow.core.model.ReviewStatus
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.SubjectId
import com.mikeyphw.studyflow.core.model.TopicId

fun ReviewItemEntity.asExternalModel(): ReviewItem = decodeModel("ReviewItemEntity") {
    ReviewItem(
        id = ReviewItemId(id),
        studyPlanId = StudyPlanId(studyPlanId),
        subjectId = subjectId?.let(::SubjectId),
        topicId = topicId?.let(::TopicId),
        questionId = questionId?.let(::QuestionId),
        flashcardId = flashcardId?.let(::FlashcardId),
        errorEntryId = errorEntryId?.let(::ErrorEntryId),
        method = method.decodeEnum<ReviewMethod>("StudyRetentionEntityMapper.method"),
        status = status.decodeEnum<ReviewStatus>("StudyRetentionEntityMapper.status"),
        dueAtEpochMillis = dueAtEpochMillis,
        intervalDays = intervalDays,
        repetitionCount = repetitionCount,
        easeFactor = easeFactor,
        completedAtEpochMillis = completedAtEpochMillis,
    )
}

fun ReviewItem.asEntity(): ReviewItemEntity = ReviewItemEntity(
    id = id.value,
    studyPlanId = studyPlanId.value,
    subjectId = subjectId?.value,
    topicId = topicId?.value,
    questionId = questionId?.value,
    flashcardId = flashcardId?.value,
    errorEntryId = errorEntryId?.value,
    method = method.name,
    status = status.name,
    dueAtEpochMillis = dueAtEpochMillis,
    intervalDays = intervalDays,
    repetitionCount = repetitionCount,
    easeFactor = easeFactor,
    completedAtEpochMillis = completedAtEpochMillis,
)

fun FlashcardEntity.asExternalModel(): Flashcard = decodeModel("FlashcardEntity") {
    Flashcard(
        id = FlashcardId(id),
        studyPlanId = StudyPlanId(studyPlanId),
        subjectId = subjectId?.let(::SubjectId),
        topicId = topicId?.let(::TopicId),
        type = type.decodeEnum<FlashcardType>("StudyRetentionEntityMapper.type"),
        front = front,
        back = back,
        clozeText = clozeText,
        tags = tagsJson.decodeStringListStable("StudyRetentionEntityMapper.tagsJson"),
        suspended = suspended,
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )
}

fun Flashcard.asEntity(): FlashcardEntity = FlashcardEntity(
    id = id.value,
    studyPlanId = studyPlanId.value,
    subjectId = subjectId?.value,
    topicId = topicId?.value,
    type = type.name,
    front = front,
    back = back,
    clozeText = clozeText,
    tagsJson = tags.encodeStringListStable(),
    suspended = suspended,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

fun ErrorEntryEntity.asExternalModel(): ErrorEntry = decodeModel("ErrorEntryEntity") {
    ErrorEntry(
        id = ErrorEntryId(id),
        studyPlanId = StudyPlanId(studyPlanId),
        questionId = questionId?.let(::QuestionId),
        subjectId = subjectId?.let(::SubjectId),
        topicId = topicId?.let(::TopicId),
        category = category.decodeEnum<ErrorCategory>("StudyRetentionEntityMapper.category"),
        chosenAnswer = chosenAnswer,
        correctAnswer = correctAnswer,
        explanation = explanation,
        recurrenceCount = recurrenceCount,
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )
}

fun ErrorEntry.asEntity(): ErrorEntryEntity = ErrorEntryEntity(
    id = id.value,
    studyPlanId = studyPlanId.value,
    questionId = questionId?.value,
    subjectId = subjectId?.value,
    topicId = topicId?.value,
    category = category.name,
    chosenAnswer = chosenAnswer,
    correctAnswer = correctAnswer,
    explanation = explanation,
    recurrenceCount = recurrenceCount,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)
