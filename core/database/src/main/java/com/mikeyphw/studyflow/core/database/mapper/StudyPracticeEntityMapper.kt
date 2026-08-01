package com.mikeyphw.studyflow.core.database.mapper

import com.mikeyphw.studyflow.core.database.entity.MockExamEntity
import com.mikeyphw.studyflow.core.database.entity.QuestionEntity
import com.mikeyphw.studyflow.core.database.entity.StudyTestEntity
import com.mikeyphw.studyflow.core.model.MockExam
import com.mikeyphw.studyflow.core.model.MockExamId
import com.mikeyphw.studyflow.core.model.Question
import com.mikeyphw.studyflow.core.model.QuestionId
import com.mikeyphw.studyflow.core.model.QuestionType
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyTest
import com.mikeyphw.studyflow.core.model.StudyTestId
import com.mikeyphw.studyflow.core.model.SubjectId
import com.mikeyphw.studyflow.core.model.TestMode
import com.mikeyphw.studyflow.core.model.TopicId
import java.nio.charset.StandardCharsets
import java.util.Base64

private const val ListSeparator = "\n"

fun QuestionEntity.asExternalModel(): Question = Question(
    id = QuestionId(id),
    studyPlanId = StudyPlanId(studyPlanId),
    subjectId = subjectId?.let(::SubjectId),
    topicId = topicId?.let(::TopicId),
    type = enumValueOf<QuestionType>(type),
    statement = statement,
    options = optionsJson.decodeStringList(),
    correctAnswer = correctAnswer,
    explanation = explanation,
    examBoard = examBoard,
    source = source,
    year = year,
    difficulty = difficulty,
    favorite = favorite,
    disputed = disputed,
    tags = tagsJson.decodeStringList(),
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

fun Question.asEntity(): QuestionEntity = QuestionEntity(
    id = id.value,
    studyPlanId = studyPlanId.value,
    subjectId = subjectId?.value,
    topicId = topicId?.value,
    type = type.name,
    statement = statement,
    optionsJson = options.encodeStringList(),
    correctAnswer = correctAnswer,
    explanation = explanation,
    examBoard = examBoard,
    source = source,
    year = year,
    difficulty = difficulty,
    favorite = favorite,
    disputed = disputed,
    tagsJson = tags.encodeStringList(),
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

fun StudyTestEntity.asExternalModel(): StudyTest = StudyTest(
    id = StudyTestId(id),
    studyPlanId = StudyPlanId(studyPlanId),
    title = title,
    mode = enumValueOf<TestMode>(mode),
    timeLimitSeconds = timeLimitSeconds,
    questionCount = questionCount,
    answeredCount = answeredCount,
    correctCount = correctCount,
    startedAtEpochMillis = startedAtEpochMillis,
    submittedAtEpochMillis = submittedAtEpochMillis,
    configurationJson = configurationJson,
)

fun StudyTest.asEntity(): StudyTestEntity = StudyTestEntity(
    id = id.value,
    studyPlanId = studyPlanId.value,
    title = title,
    mode = mode.name,
    timeLimitSeconds = timeLimitSeconds,
    questionCount = questionCount,
    answeredCount = answeredCount,
    correctCount = correctCount,
    startedAtEpochMillis = startedAtEpochMillis,
    submittedAtEpochMillis = submittedAtEpochMillis,
    configurationJson = configurationJson,
)

fun MockExamEntity.asExternalModel(): MockExam = MockExam(
    id = MockExamId(id),
    studyPlanId = StudyPlanId(studyPlanId),
    title = title,
    mode = enumValueOf<TestMode>(mode),
    timeLimitSeconds = timeLimitSeconds,
    questionCount = questionCount,
    answeredCount = answeredCount,
    correctCount = correctCount,
    scorePercent = scorePercent,
    negativeMarking = negativeMarking,
    minimumScorePercent = minimumScorePercent,
    startedAtEpochMillis = startedAtEpochMillis,
    submittedAtEpochMillis = submittedAtEpochMillis,
    configurationJson = configurationJson,
)

fun MockExam.asEntity(): MockExamEntity = MockExamEntity(
    id = id.value,
    studyPlanId = studyPlanId.value,
    title = title,
    mode = mode.name,
    timeLimitSeconds = timeLimitSeconds,
    questionCount = questionCount,
    answeredCount = answeredCount,
    correctCount = correctCount,
    scorePercent = scorePercent,
    negativeMarking = negativeMarking,
    minimumScorePercent = minimumScorePercent,
    startedAtEpochMillis = startedAtEpochMillis,
    submittedAtEpochMillis = submittedAtEpochMillis,
    configurationJson = configurationJson,
)

private fun List<String>.encodeStringList(): String = joinToString(ListSeparator) { value ->
    Base64.getEncoder().encodeToString(value.toByteArray(StandardCharsets.UTF_8))
}

private fun String.decodeStringList(): List<String> = if (isBlank()) {
    emptyList()
} else {
    lineSequence()
        .mapNotNull { encoded ->
            runCatching {
                String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8)
            }.getOrNull()
        }
        .toList()
}
