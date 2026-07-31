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

fun StudyStrategyEntity.asExternalModel(): StudyStrategy = StudyStrategy(
    id = StudyStrategyId(id),
    studyPlanId = StudyPlanId(studyPlanId),
    type = enumValueOf<StudyStrategyType>(type),
    minimumSessionMinutes = minimumSessionMinutes,
    maximumSessionMinutes = maximumSessionMinutes,
    theoryRatioPercent = theoryRatioPercent,
    questionRatioPercent = questionRatioPercent,
    reviewRatioPercent = reviewRatioPercent,
    adaptivePlanningEnabled = adaptivePlanningEnabled,
    configurationJson = configurationJson,
)

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

fun ScheduleRuleEntity.asExternalModel(): ScheduleRule = ScheduleRule(
    id = ScheduleRuleId(id),
    studyPlanId = StudyPlanId(studyPlanId),
    subjectId = subjectId?.let(::SubjectId),
    type = enumValueOf<ScheduleRuleType>(type),
    dayOfWeek = dayOfWeek,
    startMinuteOfDay = startMinuteOfDay,
    durationMinutes = durationMinutes,
    recurrenceRule = recurrenceRule,
    locked = locked,
    enabled = enabled,
    position = position,
)

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

fun PlannedActivityEntity.asExternalModel(): PlannedActivity = PlannedActivity(
    id = PlannedActivityId(id),
    studyPlanId = StudyPlanId(studyPlanId),
    subjectId = subjectId?.let(::SubjectId),
    topicId = topicId?.let(::TopicId),
    materialId = materialId?.let(::StudyMaterialId),
    videoResourceId = videoResourceId?.let(::VideoResourceId),
    type = enumValueOf<StudyActivityType>(type),
    title = title,
    scheduledStartEpochMillis = scheduledStartEpochMillis,
    plannedDurationMinutes = plannedDurationMinutes,
    status = enumValueOf<PlannedActivityStatus>(status),
    locked = locked,
    completedAtEpochMillis = completedAtEpochMillis,
)

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
