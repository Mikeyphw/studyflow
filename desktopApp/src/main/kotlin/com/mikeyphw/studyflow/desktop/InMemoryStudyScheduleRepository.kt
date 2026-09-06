package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.domain.AdaptiveReschedulePlanner
import com.mikeyphw.studyflow.core.domain.PlannedActivityRebalanceMutation
import com.mikeyphw.studyflow.core.domain.StudySchedulePlanner
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class InMemoryStudyScheduleRepository(
    strategies: List<StudyStrategy>,
    scheduleRules: List<ScheduleRule>,
    plannedActivities: List<PlannedActivity>,
) : StudyScheduleRepository {
    private val strategyState = MutableStateFlow(strategies)
    private val ruleState = MutableStateFlow(scheduleRules)
    private val activityState = MutableStateFlow(plannedActivities)
    private val planner = StudySchedulePlanner()
    private val mutationMutex = Mutex()

    override fun observeSchedule(planId: StudyPlanId): Flow<StudySchedule> = combine(
        strategyState,
        ruleState,
        activityState,
    ) { _, _, _ ->
        currentSchedule(planId)
    }

    override suspend fun upsertStrategy(strategy: StudyStrategy): StudyStrategy = mutationMutex.withLock {
        strategyState.value = strategyState.value.filterNot { it.id == strategy.id } + strategy
        strategy
    }

    override suspend fun upsertScheduleRule(rule: ScheduleRule): ScheduleRule = mutationMutex.withLock {
        val candidateRules = ruleState.value
            .filter { existing -> existing.studyPlanId == rule.studyPlanId && existing.id != rule.id }
            .plus(rule)
        planner.validateRules(candidateRules)
        ruleState.value = ruleState.value.filterNot { it.id == rule.id } + rule
        rule
    }

    override suspend fun deleteScheduleRule(planId: StudyPlanId, ruleId: ScheduleRuleId) = mutationMutex.withLock {
        require(ruleState.value.any { rule -> rule.studyPlanId == planId && rule.id == ruleId }) {
            "Schedule rule does not belong to this Study Plan."
        }
        val prefix = StudySchedulePlanner.generatedRulePrefix(ruleId)
        val generated = currentActivities(planId).filter { activity -> activity.id.value.startsWith(prefix) }
        require(generated.none { activity -> activity.status == PlannedActivityStatus.IN_PROGRESS }) {
            "Cannot delete a schedule rule while one of its generated activities is in progress."
        }
        activityState.value = activityState.value.filterNot { activity ->
            activity.studyPlanId == planId &&
                activity.id.value.startsWith(prefix) &&
                activity.status in RuleDeletionStatuses
        }
        ruleState.value = ruleState.value.filterNot { rule -> rule.studyPlanId == planId && rule.id == ruleId }
    }

    override suspend fun upsertPlannedActivity(activity: PlannedActivity): PlannedActivity = mutationMutex.withLock {
        require(activity.status != PlannedActivityStatus.IN_PROGRESS) {
            "An in-progress activity can only be created by starting its Study Session."
        }
        val existing = currentActivities(activity.studyPlanId).firstOrNull { it.id == activity.id }
        require(existing?.status != PlannedActivityStatus.IN_PROGRESS || existing == activity) {
            "An in-progress activity is owned by its active Study Session and cannot be edited independently."
        }
        activityState.value = activityState.value.filterNot { it.id == activity.id } + activity
        activity
    }

    override suspend fun updatePlannedActivityStatus(
        planId: StudyPlanId,
        activityId: PlannedActivityId,
        status: PlannedActivityStatus,
        completedAtEpochMillis: Long?,
    ): PlannedActivity = mutationMutex.withLock {
        val current = requireNotNull(currentActivities(planId).firstOrNull { it.id == activityId }) {
            "Planned activity does not belong to this Study Plan."
        }
        require(current.status != PlannedActivityStatus.IN_PROGRESS) {
            "An in-progress activity must transition through its active Study Session."
        }
        require(current.status in IndependentlyMutableStatuses) {
            "Completed or terminal planned activities cannot be transitioned independently."
        }
        require(status in setOf(PlannedActivityStatus.COMPLETED, PlannedActivityStatus.MISSED)) {
            "Schedule CRUD may only mark an activity completed or missed; session state owns IN_PROGRESS transitions."
        }
        val updated = current.copy(status = status, completedAtEpochMillis = completedAtEpochMillis)
        activityState.value = activityState.value.map { existing ->
            if (existing.studyPlanId == planId && existing.id == activityId) updated else existing
        }
        updated
    }

    override suspend fun deletePlannedActivity(planId: StudyPlanId, activityId: PlannedActivityId) =
        mutationMutex.withLock {
            val current = requireNotNull(currentActivities(planId).firstOrNull { it.id == activityId }) {
                "Planned activity does not belong to this Study Plan."
            }
            require(current.status != PlannedActivityStatus.IN_PROGRESS) {
                "An in-progress activity cannot be deleted independently of its active Study Session."
            }
            activityState.value = activityState.value.filterNot { activity ->
                activity.studyPlanId == planId && activity.id == activityId
            }
        }

    override suspend fun replaceUnlockedActivities(
        planId: StudyPlanId,
        activities: List<PlannedActivity>,
    ): Int = mutationMutex.withLock {
        validateGeneratedActivities(planId, activities)
        val replaceable = activityState.value.filter { activity ->
            activity.studyPlanId == planId && !activity.locked && activity.status in IndependentlyMutableStatuses
        }
        activityState.value = activityState.value
            .filterNot { activity -> activity in replaceable }
            .let { retained -> upsertAll(retained, activities) }
        replaceable.size
    }

    override suspend fun applyGeneratedActivities(
        planId: StudyPlanId,
        activities: List<PlannedActivity>,
        replaceUnlocked: Boolean,
    ): ScheduleGenerationResult = mutationMutex.withLock {
        validateGeneratedActivities(planId, activities)
        val currentAll = activityState.value
        val replaceable = if (replaceUnlocked) {
            currentAll.filter { activity ->
                activity.studyPlanId == planId && !activity.locked && activity.status in IndependentlyMutableStatuses
            }
        } else {
            emptyList()
        }
        val retained = currentAll.filterNot { activity -> activity in replaceable }
        val working = retained.filter { it.studyPlanId == planId }.toMutableList()
        val rules = currentRules(planId)
        var applied = 0

        activities.forEach { candidate ->
            val existing = working.firstOrNull { it.id == candidate.id }
            if (existing == null) {
                val withoutCandidate = working.filterNot { it.id == candidate.id }
                planner.validateCandidate(candidate, withoutCandidate + candidate, rules)
                working.removeAll { it.id == candidate.id }
                working += candidate
                applied++
            }
        }

        activityState.value = retained.filterNot { it.studyPlanId == planId } + working
        ScheduleGenerationResult(
            generatedActivities = applied,
            removedUnlockedActivities = replaceable.size,
        )
    }

    override suspend fun applyAdaptivePreview(planId: StudyPlanId, preview: AdaptiveReschedulePreview): Int =
        mutationMutex.withLock {
            require(preview.studyPlanId == planId) { "Adaptive preview belongs to another Study Plan." }
            val currentSchedule = currentSchedule(planId)
            require(AdaptiveReschedulePlanner.scheduleVersionToken(currentSchedule) == preview.scheduleVersionToken) {
                "Schedule changed after this adaptive preview was approved. Refresh before applying."
            }

            val currentActivities = currentActivities(planId)
            val currentById = currentActivities.associateBy(PlannedActivity::id)
            val proposedById = currentById.toMutableMap()
            val mutations = preview.changes.filter { change -> change.executable }.map { change ->
                require(!change.locked) { "Locked planned activities cannot be changed by adaptive rescheduling." }
                val activityId = requireNotNull(change.plannedActivityId) {
                    "Executable adaptive changes must target a planned activity."
                }
                val current = requireNotNull(currentById[activityId]) {
                    "Adaptive preview targets an activity that no longer exists."
                }
                require(!current.locked) { "Locked planned activities cannot be changed by adaptive rescheduling." }
                require(current.status != PlannedActivityStatus.IN_PROGRESS) {
                    "An in-progress activity is owned by its active Study Session."
                }
                require(
                    current.scheduledStartEpochMillis == change.currentStartEpochMillis &&
                        current.plannedDurationMinutes == change.currentDurationMinutes &&
                        current.status == change.currentStatus &&
                        (change.currentType == null || current.type == change.currentType),
                ) { "Adaptive preview is stale. Refresh before applying." }

                val proposed = current.copy(
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
                require(proposed != current) { "Adaptive preview contains an executable no-op change." }
                require(proposed.status != PlannedActivityStatus.IN_PROGRESS) {
                    "Adaptive rescheduling cannot bypass the Study Session start transition."
                }
                proposedById[activityId] = proposed
                current to proposed
            }

            val finalActivities = proposedById.values.toList()
            val rules = currentRules(planId)
            mutations.forEach { (_, proposed) -> planner.validateCandidate(proposed, finalActivities, rules) }

            activityState.value = activityState.value.filterNot { it.studyPlanId == planId } + finalActivities
            mutations.size
        }

    override suspend fun applyMissedRebalance(
        planId: StudyPlanId,
        mutations: List<PlannedActivityRebalanceMutation>,
    ): Int = mutationMutex.withLock {
        require(mutations.all { it.expected.studyPlanId == planId && it.proposed.studyPlanId == planId }) {
            "Missed-activity rebalance must stay inside one Study Plan."
        }
        require(mutations.map { it.expected.id }.distinct().size == mutations.size) {
            "Missed-activity rebalance cannot mutate one activity more than once."
        }
        val currentById = currentActivities(planId).associateBy(PlannedActivity::id)
        val proposedById = currentById.toMutableMap()

        mutations.forEach { mutation ->
            val expected = mutation.expected
            val proposed = mutation.proposed
            require(expected.id == proposed.id) { "Missed-activity rebalance cannot change activity identity." }
            require(!expected.locked && !proposed.locked) { "Locked activities cannot be changed by missed rebalance." }
            require(expected.status in IndependentlyMutableStatuses) {
                "Only planned or missed activities can be rebalanced."
            }
            require(proposed.status != PlannedActivityStatus.IN_PROGRESS) {
                "Missed rebalance cannot create an in-progress activity."
            }
            val current = requireNotNull(currentById[expected.id]) {
                "Planned activity changed concurrently; no missed-rebalance changes were applied."
            }
            require(current.matchesMissedRebalanceExpectation(expected)) {
                "Planned activity changed concurrently; no missed-rebalance changes were applied."
            }
            proposedById[expected.id] = proposed
        }

        activityState.value = activityState.value.filterNot { it.studyPlanId == planId } + proposedById.values
        mutations.size
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

    private fun validateGeneratedActivities(planId: StudyPlanId, activities: List<PlannedActivity>) {
        require(activities.all { it.studyPlanId == planId }) { "Generated activities must belong to one Study Plan." }
        require(activities.none { it.status == PlannedActivityStatus.IN_PROGRESS }) {
            "Generated activities cannot bypass the Study Session start transition."
        }
    }

    private fun upsertAll(
        retained: List<PlannedActivity>,
        activities: List<PlannedActivity>,
    ): List<PlannedActivity> = activities.fold(retained) { current, activity ->
        current.filterNot { existing -> existing.id == activity.id } + activity
    }

    private fun PlannedActivity.matchesMissedRebalanceExpectation(expected: PlannedActivity): Boolean =
        !locked &&
            type == expected.type &&
            status == expected.status &&
            plannedDurationMinutes == expected.plannedDurationMinutes &&
            scheduledStartEpochMillis == expected.scheduledStartEpochMillis

    private companion object {
        val IndependentlyMutableStatuses = setOf(PlannedActivityStatus.PLANNED, PlannedActivityStatus.MISSED)
        val RuleDeletionStatuses = setOf(
            PlannedActivityStatus.PLANNED,
            PlannedActivityStatus.MISSED,
            PlannedActivityStatus.SKIPPED,
            PlannedActivityStatus.CANCELLED,
        )
    }
}
