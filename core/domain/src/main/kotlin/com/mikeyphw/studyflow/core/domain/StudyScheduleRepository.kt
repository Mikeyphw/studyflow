package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.AdaptiveReschedulePreview
import com.mikeyphw.studyflow.core.model.PlannedActivity
import com.mikeyphw.studyflow.core.model.PlannedActivityId
import com.mikeyphw.studyflow.core.model.PlannedActivityStatus
import com.mikeyphw.studyflow.core.model.ScheduleRule
import com.mikeyphw.studyflow.core.model.ScheduleGenerationResult
import com.mikeyphw.studyflow.core.model.ScheduleRuleId
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudySchedule
import com.mikeyphw.studyflow.core.model.StudyStrategy
import kotlinx.coroutines.flow.Flow


data class PlannedActivityRebalanceMutation(
    val expected: PlannedActivity,
    val proposed: PlannedActivity,
) {
    init {
        require(expected.id == proposed.id) { "Rebalance mutation cannot change activity identity." }
        require(expected.studyPlanId == proposed.studyPlanId) { "Rebalance mutation cannot change plan ownership." }
    }
}

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

    suspend fun applyGeneratedActivities(
        planId: StudyPlanId,
        activities: List<PlannedActivity>,
        replaceUnlocked: Boolean,
    ): ScheduleGenerationResult

    suspend fun applyAdaptivePreview(
        planId: StudyPlanId,
        preview: AdaptiveReschedulePreview,
    ): Int = error("This schedule repository does not support atomic adaptive preview application.")

    suspend fun applyMissedRebalance(
        planId: StudyPlanId,
        mutations: List<PlannedActivityRebalanceMutation>,
    ): Int = error("This schedule repository does not support atomic missed-activity rebalance.")
}
