package com.mikeyphw.studyflow.core.database.mapper

import com.mikeyphw.studyflow.core.database.entity.StudyPlanSettingsEntity
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyPlanSettings

fun StudyPlanSettingsEntity.asExternalModel(): StudyPlanSettings = decodeModel("StudyPlanSettingsEntity") {
    StudyPlanSettings(
        studyPlanId = StudyPlanId(studyPlanId),
        adaptivePlanningEnabled = adaptivePlanningEnabled,
        reviewNotificationsEnabled = reviewNotificationsEnabled,
        strictExamModeEnabled = strictExamModeEnabled,
        preferredSessionMinutes = preferredSessionMinutes,
        quietHoursStartMinute = quietHoursStartMinute,
        quietHoursEndMinute = quietHoursEndMinute,
        configurationJson = configurationJson,
    )
}

fun StudyPlanSettings.asEntity(): StudyPlanSettingsEntity = StudyPlanSettingsEntity(
    studyPlanId = studyPlanId.value,
    adaptivePlanningEnabled = adaptivePlanningEnabled,
    reviewNotificationsEnabled = reviewNotificationsEnabled,
    strictExamModeEnabled = strictExamModeEnabled,
    preferredSessionMinutes = preferredSessionMinutes,
    quietHoursStartMinute = quietHoursStartMinute,
    quietHoursEndMinute = quietHoursEndMinute,
    configurationJson = configurationJson,
)
