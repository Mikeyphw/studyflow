package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.domain.PlannedActivityRebalanceMutation
import com.mikeyphw.studyflow.core.domain.StudyScheduleRepository
import com.mikeyphw.studyflow.core.model.AdaptiveReschedulePreview
import com.mikeyphw.studyflow.core.model.PlannedActivity
import com.mikeyphw.studyflow.core.model.PlannedActivityId
import com.mikeyphw.studyflow.core.model.PlannedActivityStatus
import com.mikeyphw.studyflow.core.model.ScheduleGenerationResult
import com.mikeyphw.studyflow.core.model.ScheduleRule
import com.mikeyphw.studyflow.core.model.ScheduleRuleId
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudySchedule
import com.mikeyphw.studyflow.core.model.StudyStrategy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class InMemoryStudyScheduleRepository(
    strategies: List<StudyStrategy>,
    scheduleRules: List<ScheduleRule>,
    plannedActivities: List<PlannedActivity>,
) : StudyScheduleRepository {
    private val strategyState = MutableStateFlow(strategies)
    private val ruleState = MutableStateFlow(scheduleRules)
    private val activityState = MutableStateFlow(plannedActivities)

    override fun observeSchedule(planId: StudyPlanId): Flow<StudySchedule> = activityState.map {
        currentSchedule(planId)
    }

    override suspend fun upsertStrategy(strategy: StudyStrategy): StudyStrategy {
        strategyState.value = strategyState.value.filterNot { it.id == strategy.id } + strategy
        return strategy
    }

    override suspend fun upsertScheduleRule(rule: ScheduleRule): ScheduleRule {
        ruleState.value = ruleState.value.filterNot { it.id == rule.id } + rule
        return rule
    }

    override suspend fun deleteScheduleRule(planId: StudyPlanId, ruleId: ScheduleRuleId) {
        ruleState.value = ruleState.value.filterNot { rule -> rule.studyPlanId == planId && rule.id == ruleId }
    }

    override suspend fun upsertPlannedActivity(activity: PlannedActivity): PlannedActivity {
        activityState.value = activityState.value.filterNot { it.id == activity.id } + activity
        return activity
    }

    override suspend fun updatePlannedActivityStatus(
        planId: StudyPlanId,
        activityId: PlannedActivityId,
        status: PlannedActivityStatus,
        completedAtEpochMillis: Long?,
    ): PlannedActivity {
        val current = requireNotNull(currentActivities(planId).firstOrNull { it.id == activityId }) {
            "Planned activity does not exist."
        }
        val updated = current.copy(status = status, completedAtEpochMillis = completedAtEpochMillis)
        upsertPlannedActivity(updated)
        return updated
    }

    override suspend fun deletePlannedActivity(planId: StudyPlanId, activityId: PlannedActivityId) {
        activityState.value = activityState.value.filterNot { activity ->
            activity.studyPlanId == planId && activity.id == activityId
        }
    }

    override suspend fun replaceUnlockedActivities(
        planId: StudyPlanId,
        activities: List<PlannedActivity>,
    ): Int {
        val retained = activityState.value.filterNot { activity ->
            activity.studyPlanId == planId && !activity.locked
        }
        activityState.value = retained + activities
        return activities.size
    }

    override suspend fun applyGeneratedActivities(
        planId: StudyPlanId,
        activities: List<PlannedActivity>,
        replaceUnlocked: Boolean,
    ): ScheduleGenerationResult {
        val changed = if (replaceUnlocked) {
            replaceUnlockedActivities(planId, activities)
        } else {
            activityState.value = activityState.value + activities
            activities.size
        }
        return ScheduleGenerationResult(
            generatedActivities = activities.size,
            removedUnlockedActivities = if (replaceUnlocked) changed - activities.size else 0,
        )
    }

    override suspend fun applyAdaptivePreview(planId: StudyPlanId, preview: AdaptiveReschedulePreview): Int {
        require(preview.studyPlanId == planId) { "Adaptive preview belongs to another Study Plan." }
        val currentById = currentActivities(planId).associateBy(PlannedActivity::id)
        val proposedById = currentById.toMutableMap()
        preview.changes.filter { change -> change.executable }.forEach { change ->
            val activityId = requireNotNull(change.plannedActivityId) {
                "Executable adaptive changes must target a planned activity."
            }
            val current = requireNotNull(currentById[activityId]) {
                "Adaptive preview targets an activity that no longer exists."
            }
            proposedById[activityId] = current.copy(
                type = change.proposedType ?: current.type,
                scheduledStartEpochMillis = change.proposedStartEpochMillis ?: current.scheduledStartEpochMillis,
                plannedDurationMinutes = change.proposedDurationMinutes ?: current.plannedDurationMinutes,
                status = change.proposedStatus ?: current.status,
                completedAtEpochMillis = if (change.proposedStatus == PlannedActivityStatus.PLANNED) {
                    null
                } else {
                    current.completedAtEpochMillis
                },
            )
        }
        val changed = proposedById.values.count { proposed -> currentById[proposed.id] != proposed }
        activityState.value = activityState.value.filterNot { it.studyPlanId == planId } + proposedById.values
        return changed
    }

    override suspend fun applyMissedRebalance(
        planId: StudyPlanId,
        mutations: List<PlannedActivityRebalanceMutation>,
    ): Int {
        val proposedById = mutations.associate { it.expected.id to it.proposed }
        activityState.value = activityState.value.map { activity ->
            if (activity.studyPlanId == planId) proposedById[activity.id] ?: activity else activity
        }
        return mutations.size
    }

    fun currentSchedule(planId: StudyPlanId): StudySchedule = StudySchedule(
        studyPlanId = planId,
        strategy = strategyState.value.firstOrNull { strategy -> strategy.studyPlanId == planId },
        scheduleRules = currentRules(planId),
        plannedActivities = currentActivities(planId),
    )

    fun currentActivities(planId: StudyPlanId): List<PlannedActivity> = activityState.value
        .filter { activity -> activity.studyPlanId == planId }
        .sortedBy { activity -> activity.scheduledStartEpochMillis ?: Long.MAX_VALUE }

    fun currentRules(planId: StudyPlanId): List<ScheduleRule> = ruleState.value
        .filter { rule -> rule.studyPlanId == planId }
        .sortedBy(ScheduleRule::position)
}
