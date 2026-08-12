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

fun QuestionEntity.asExternalModel(): Question = decodeModel("QuestionEntity") {
    Question(
        id = QuestionId(id),
        studyPlanId = StudyPlanId(studyPlanId),
        subjectId = subjectId?.let(::SubjectId),
        topicId = topicId?.let(::TopicId),
        type = type.decodeEnum<QuestionType>("StudyPracticeEntityMapper.type"),
        statement = statement,
        options = optionsJson.decodeStringListStable("StudyPracticeEntityMapper.optionsJson"),
        correctAnswer = correctAnswer,
        explanation = explanation,
        examBoard = examBoard,
        source = source,
        year = year,
        difficulty = difficulty,
        favorite = favorite,
        disputed = disputed,
        tags = tagsJson.decodeStringListStable("StudyPracticeEntityMapper.tagsJson"),
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )
}

fun Question.asEntity(): QuestionEntity = QuestionEntity(
    id = id.value,
    studyPlanId = studyPlanId.value,
    subjectId = subjectId?.value,
    topicId = topicId?.value,
    type = type.name,
    statement = statement,
    optionsJson = options.encodeStringListStable(),
    correctAnswer = correctAnswer,
    explanation = explanation,
    examBoard = examBoard,
    source = source,
    year = year,
    difficulty = difficulty,
    favorite = favorite,
    disputed = disputed,
    tagsJson = tags.encodeStringListStable(),
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

fun StudyTestEntity.asExternalModel(): StudyTest = decodeModel("StudyTestEntity") {
    StudyTest(
        id = StudyTestId(id),
        studyPlanId = StudyPlanId(studyPlanId),
        title = title,
        mode = mode.decodeEnum<TestMode>("StudyPracticeEntityMapper.mode"),
        timeLimitSeconds = timeLimitSeconds,
        questionCount = questionCount,
        answeredCount = answeredCount,
        correctCount = correctCount,
        startedAtEpochMillis = startedAtEpochMillis,
        submittedAtEpochMillis = submittedAtEpochMillis,
        configurationJson = configurationJson,
    )
}

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

fun MockExamEntity.asExternalModel(): MockExam = decodeModel("MockExamEntity") {
    MockExam(
        id = MockExamId(id),
        studyPlanId = StudyPlanId(studyPlanId),
        title = title,
        mode = mode.decodeEnum<TestMode>("StudyPracticeEntityMapper.mode"),
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
}

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
