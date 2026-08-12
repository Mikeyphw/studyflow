package com.mikeyphw.studyflow.core.database.mapper

import com.mikeyphw.studyflow.core.database.entity.PlannedActivityEntity
import com.mikeyphw.studyflow.core.database.entity.ScheduleRuleEntity
import com.mikeyphw.studyflow.core.database.entity.StudyStrategyEntity
import com.mikeyphw.studyflow.core.model.PlannedActivity
import com.mikeyphw.studyflow.core.model.PlannedActivityId
import com.mikeyphw.studyflow.core.model.PlannedActivityStatus
import com.mikeyphw.studyflow.core.model.ScheduleRule
import com.mikeyphw.studyflow.core.model.ScheduleRuleId
import com.mikeyphw.studyflow.core.model.ScheduleRuleType
import com.mikeyphw.studyflow.core.model.StudyActivityType
import com.mikeyphw.studyflow.core.model.StudyMaterialId
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyStrategy
import com.mikeyphw.studyflow.core.model.StudyStrategyId
import com.mikeyphw.studyflow.core.model.StudyStrategyType
import com.mikeyphw.studyflow.core.model.SubjectId
import com.mikeyphw.studyflow.core.model.TopicId
import com.mikeyphw.studyflow.core.model.VideoResourceId
import com.mikeyphw.studyflow.core.database.entity.StudySessionEntity
import com.mikeyphw.studyflow.core.model.StudySession
import com.mikeyphw.studyflow.core.model.StudySessionId
import com.mikeyphw.studyflow.core.model.StudySessionStatus

fun StudyStrategyEntity.asExternalModel(): StudyStrategy = decodeModel("StudyStrategyEntity") {
    StudyStrategy(
        id = StudyStrategyId(id),
        studyPlanId = StudyPlanId(studyPlanId),
        type = type.decodeEnum<StudyStrategyType>("StudyPlanningEntityMapper.type"),
        minimumSessionMinutes = minimumSessionMinutes,
        maximumSessionMinutes = maximumSessionMinutes,
        theoryRatioPercent = theoryRatioPercent,
        questionRatioPercent = questionRatioPercent,
        reviewRatioPercent = reviewRatioPercent,
        adaptivePlanningEnabled = adaptivePlanningEnabled,
        configurationJson = configurationJson,
    )
}

fun StudyStrategy.asEntity(): StudyStrategyEntity = StudyStrategyEntity(
    id = id.value,
    studyPlanId = studyPlanId.value,
    type = type.name,
    minimumSessionMinutes = minimumSessionMinutes,
    maximumSessionMinutes = maximumSessionMinutes,
    theoryRatioPercent = theoryRatioPercent,
    questionRatioPercent = questionRatioPercent,
    reviewRatioPercent = reviewRatioPercent,
    adaptivePlanningEnabled = adaptivePlanningEnabled,
    configurationJson = configurationJson,
)

fun ScheduleRuleEntity.asExternalModel(): ScheduleRule = decodeModel("ScheduleRuleEntity") {
    ScheduleRule(
        id = ScheduleRuleId(id),
        studyPlanId = StudyPlanId(studyPlanId),
        subjectId = subjectId?.let(::SubjectId),
        type = type.decodeEnum<ScheduleRuleType>("StudyPlanningEntityMapper.type"),
        dayOfWeek = dayOfWeek,
        startMinuteOfDay = startMinuteOfDay,
        durationMinutes = durationMinutes,
        recurrenceRule = recurrenceRule,
        locked = locked,
        enabled = enabled,
        position = position,
    )
}

fun ScheduleRule.asEntity(): ScheduleRuleEntity = ScheduleRuleEntity(
    id = id.value,
    studyPlanId = studyPlanId.value,
    subjectId = subjectId?.value,
    type = type.name,
    dayOfWeek = dayOfWeek,
    startMinuteOfDay = startMinuteOfDay,
    durationMinutes = durationMinutes,
    recurrenceRule = recurrenceRule,
    locked = locked,
    enabled = enabled,
    position = position,
)

fun PlannedActivityEntity.asExternalModel(): PlannedActivity = decodeModel("PlannedActivityEntity") {
    PlannedActivity(
        id = PlannedActivityId(id),
        studyPlanId = StudyPlanId(studyPlanId),
        subjectId = subjectId?.let(::SubjectId),
        topicId = topicId?.let(::TopicId),
        materialId = materialId?.let(::StudyMaterialId),
        videoResourceId = videoResourceId?.let(::VideoResourceId),
        type = type.decodeEnum<StudyActivityType>("StudyPlanningEntityMapper.type"),
        title = title,
        scheduledStartEpochMillis = scheduledStartEpochMillis,
        plannedDurationMinutes = plannedDurationMinutes,
        status = status.decodeEnum<PlannedActivityStatus>("StudyPlanningEntityMapper.status"),
        locked = locked,
        completedAtEpochMillis = completedAtEpochMillis,
    )
}

fun PlannedActivity.asEntity(): PlannedActivityEntity = PlannedActivityEntity(
    id = id.value,
    studyPlanId = studyPlanId.value,
    subjectId = subjectId?.value,
    topicId = topicId?.value,
    materialId = materialId?.value,
    videoResourceId = videoResourceId?.value,
    type = type.name,
    title = title,
    scheduledStartEpochMillis = scheduledStartEpochMillis,
    plannedDurationMinutes = plannedDurationMinutes,
    status = status.name,
    locked = locked,
    completedAtEpochMillis = completedAtEpochMillis,
)
fun StudySessionEntity.asExternalModel(): StudySession = decodeModel("StudySessionEntity") {
    StudySession(
        id = StudySessionId(id),
        studyPlanId = StudyPlanId(studyPlanId),
        plannedActivityId = plannedActivityId?.let(::PlannedActivityId),
        subjectId = subjectId?.let(::SubjectId),
        topicId = topicId?.let(::TopicId),
        materialId = materialId?.let(::StudyMaterialId),
        videoResourceId = videoResourceId?.let(::VideoResourceId),
        type = type.decodeEnum<StudyActivityType>("StudyPlanningEntityMapper.type"),
        status = status.decodeEnum<StudySessionStatus>("StudyPlanningEntityMapper.status"),
        startedAtEpochMillis = startedAtEpochMillis,
        endedAtEpochMillis = endedAtEpochMillis,
        grossDurationSeconds = grossDurationSeconds,
        pauseDurationSeconds = pauseDurationSeconds,
        pagesCompleted = pagesCompleted,
        questionsAnswered = questionsAnswered,
        correctAnswers = correctAnswers,
        concentrationRating = concentrationRating,
        difficultyRating = difficultyRating,
        notes = notes,
        targetDurationMinutes = targetDurationMinutes,
    )
}

fun StudySession.asEntity(): StudySessionEntity = StudySessionEntity(
    id = id.value,
    studyPlanId = studyPlanId.value,
    plannedActivityId = plannedActivityId?.value,
    subjectId = subjectId?.value,
    topicId = topicId?.value,
    materialId = materialId?.value,
    videoResourceId = videoResourceId?.value,
    type = type.name,
    status = status.name,
    startedAtEpochMillis = startedAtEpochMillis,
    endedAtEpochMillis = endedAtEpochMillis,
    grossDurationSeconds = grossDurationSeconds,
    pauseDurationSeconds = pauseDurationSeconds,
    pagesCompleted = pagesCompleted,
    questionsAnswered = questionsAnswered,
    correctAnswers = correctAnswers,
    concentrationRating = concentrationRating,
    difficultyRating = difficultyRating,
    notes = notes,
    targetDurationMinutes = targetDurationMinutes,
)
