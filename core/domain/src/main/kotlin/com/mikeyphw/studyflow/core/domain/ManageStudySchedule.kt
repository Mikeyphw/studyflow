package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.MissedActivityHandling
import com.mikeyphw.studyflow.core.model.PlannedActivity
import com.mikeyphw.studyflow.core.model.PlannedActivityId
import com.mikeyphw.studyflow.core.model.PlannedActivityStatus
import com.mikeyphw.studyflow.core.model.RebalanceResult
import com.mikeyphw.studyflow.core.model.ScheduleGenerationRequest
import com.mikeyphw.studyflow.core.model.ScheduleGenerationResult
import com.mikeyphw.studyflow.core.model.ScheduleRule
import com.mikeyphw.studyflow.core.model.ScheduleRuleId
import com.mikeyphw.studyflow.core.model.ScheduleRuleType
import com.mikeyphw.studyflow.core.model.StudyDomainInvariants
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyStrategy
import java.time.LocalDate
import kotlinx.coroutines.flow.first

class ManageStudySchedule(
    private val repository: StudyScheduleRepository,
    private val planner: StudySchedulePlanner = StudySchedulePlanner(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun observeSchedule(planId: StudyPlanId) = repository.observeSchedule(planId)

    suspend fun saveStrategy(strategy: StudyStrategy): StudyStrategy {
        StudyDomainInvariants.requireStrategyDurations(
            strategy.minimumSessionMinutes,
            strategy.maximumSessionMinutes,
        )
        val ratioTotal = strategy.theoryRatioPercent + strategy.questionRatioPercent + strategy.reviewRatioPercent
        require(ratioTotal == 100) { "Theory, question and review ratios must total 100 percent." }
        return repository.upsertStrategy(strategy)
    }

    suspend fun savePhasePlan(strategy: StudyStrategy, phasePlan: StudyPhasePlan): StudyStrategy = saveStrategy(
        strategy.copy(configurationJson = StudyPhasePlanCodec.encode(strategy.configurationJson, phasePlan)),
    )

    fun effectiveStrategyForDate(strategy: StudyStrategy?, date: LocalDate): StudyStrategy? {
        val plan = StudyPhasePlanCodec.decode(strategy?.configurationJson.orEmpty())
        return StudyPhasePlanning.effectiveStrategy(strategy, plan, date)
    }

    suspend fun saveRule(rule: ScheduleRule): ScheduleRule {
        StudyDomainInvariants.requireSessionMinutes(rule.durationMinutes, "Schedule block")
        require(rule.dayOfWeek in 1..7) { "Day of week must be between 1 and 7." }
        if (rule.type != ScheduleRuleType.DAY_OFF) {
            require(rule.startMinuteOfDay in 0..1439) { "Start time must be inside the day." }
        }
        return repository.upsertScheduleRule(rule)
    }

    suspend fun deleteRule(planId: StudyPlanId, ruleId: ScheduleRuleId) =
        repository.deleteScheduleRule(planId, ruleId)

    suspend fun saveActivity(activity: PlannedActivity): PlannedActivity {
        require(activity.title.isNotBlank()) { "Planned activity title cannot be blank." }
        StudyDomainInvariants.requireSessionMinutes(activity.plannedDurationMinutes, "Planned duration")
        return repository.upsertPlannedActivity(activity)
    }

    suspend fun completeActivity(planId: StudyPlanId, activityId: PlannedActivityId): PlannedActivity =
        repository.updatePlannedActivityStatus(
            planId = planId,
            activityId = activityId,
            status = PlannedActivityStatus.COMPLETED,
            completedAtEpochMillis = clock(),
        )

    suspend fun markActivityMissed(planId: StudyPlanId, activityId: PlannedActivityId): PlannedActivity =
        repository.updatePlannedActivityStatus(
            planId = planId,
            activityId = activityId,
            status = PlannedActivityStatus.MISSED,
            completedAtEpochMillis = null,
        )

    suspend fun deleteActivity(planId: StudyPlanId, activityId: PlannedActivityId) =
        repository.deletePlannedActivity(planId, activityId)

    suspend fun generateActivities(
        request: ScheduleGenerationRequest,
        currentRules: List<ScheduleRule>,
        strategy: StudyStrategy? = null,
    ): ScheduleGenerationResult {
        val generated = planner.generateActivities(request, currentRules, strategy)
        return repository.applyGeneratedActivities(
            planId = request.studyPlanId,
            activities = generated,
            replaceUnlocked = request.replaceUnlocked,
        )
    }

    suspend fun applyVersionedPreview(
        planId: StudyPlanId,
        preview: StudySprintPreview,
    ): Int {
        require(preview.studyPlanId == planId) { "Study sprint preview belongs to another Study Plan." }
        require(preview.scheduleVersionToken.isNotBlank()) {
            "Study sprint preview is missing its version token. Refresh before applying."
        }
        val currentSchedule = repository.observeSchedule(planId).first()
        StudySprintPlanner.requireSafeApply(
            preview = preview,
            schedule = currentSchedule,
            nowEpochMillis = clock(),
        )
        return repository.applyAdaptivePreview(planId, preview.schedulePreview)
    }

    suspend fun rebalanceMissed(
        planId: StudyPlanId,
        handling: MissedActivityHandling,
    ): RebalanceResult {
        val schedule = repository.observeSchedule(planId).first()
        require(schedule.studyPlanId == planId) { "Schedule snapshot belongs to another Study Plan." }
        val strategy = schedule.strategy
        require(strategy == null || strategy.studyPlanId == planId) {
            "Schedule strategy belongs to another Study Plan."
        }
        require(schedule.scheduleRules.all { it.studyPlanId == planId }) {
            "Schedule rules belong to another Study Plan."
        }
        require(schedule.plannedActivities.all { it.studyPlanId == planId }) {
            "Schedule activities belong to another Study Plan."
        }
        val now = clock()
        val policyAnchor = schedule.plannedActivities
            .mapNotNull(PlannedActivity::scheduledStartEpochMillis)
            .minOrNull()
            ?: now
        val mutations = planner.rebalanceMissedMutations(
            activities = schedule.plannedActivities,
            rules = schedule.scheduleRules,
            handling = handling,
            nowEpochMillis = now,
            policyRangeStartEpochMillis = minOf(policyAnchor, now),
        )
        val changed = repository.applyMissedRebalance(planId, mutations)
        return RebalanceResult(changed, handling)
    }
}
