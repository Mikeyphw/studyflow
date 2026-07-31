package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.PlannedActivity
import com.mikeyphw.studyflow.core.model.PlannedActivityId
import com.mikeyphw.studyflow.core.model.PlannedActivityStatus
import com.mikeyphw.studyflow.core.model.ScheduleRule
import com.mikeyphw.studyflow.core.model.ScheduleRuleId
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudySchedule
import com.mikeyphw.studyflow.core.model.StudyStrategy
import kotlinx.coroutines.flow.Flow

interface StudyScheduleRepository {
    fun observeSchedule(planId: StudyPlanId): Flow<StudySchedule>

    suspend fun upsertStrategy(strategy: StudyStrategy): StudyStrategy

    suspend fun upsertScheduleRule(rule: ScheduleRule): ScheduleRule

    suspend fun deleteScheduleRule(planId: StudyPlanId, ruleId: ScheduleRuleId)

    suspend fun upsertPlannedActivity(activity: PlannedActivity): PlannedActivity

    suspend fun updatePlannedActivityStatus(
        planId: StudyPlanId,
        activityId: PlannedActivityId,
        status: PlannedActivityStatus,
        completedAtEpochMillis: Long?,
    ): PlannedActivity

    suspend fun deletePlannedActivity(planId: StudyPlanId, activityId: PlannedActivityId)

    suspend fun replaceUnlockedActivities(
        planId: StudyPlanId,
        activities: List<PlannedActivity>,
    ): Int

    suspend fun updatePlannedActivities(
        planId: StudyPlanId,
        activities: List<PlannedActivity>,
    ): Int
}
