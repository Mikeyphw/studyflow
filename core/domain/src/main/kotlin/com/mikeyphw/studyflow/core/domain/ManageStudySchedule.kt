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
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyStrategy

class ManageStudySchedule(
    private val repository: StudyScheduleRepository,
    private val planner: StudySchedulePlanner = StudySchedulePlanner(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun observeSchedule(planId: StudyPlanId) = repository.observeSchedule(planId)

    suspend fun saveStrategy(strategy: StudyStrategy): StudyStrategy {
        require(strategy.minimumSessionMinutes in 5..480) {
            "Minimum session length must be between 5 and 480 minutes."
        }
        require(strategy.maximumSessionMinutes in strategy.minimumSessionMinutes..720) {
            "Maximum session length must be greater than or equal to the minimum."
        }
        val ratioTotal = strategy.theoryRatioPercent + strategy.questionRatioPercent + strategy.reviewRatioPercent
        require(ratioTotal == 100) { "Theory, question and review ratios must total 100 percent." }
        return repository.upsertStrategy(strategy)
    }

    suspend fun saveRule(rule: ScheduleRule): ScheduleRule {
        require(rule.durationMinutes in 5..720) { "Schedule blocks must be between 5 and 720 minutes." }
        if (rule.type != ScheduleRuleType.DAY_OFF) {
            require(rule.dayOfWeek in 1..7) { "Day of week must be between 1 and 7." }
            require(rule.startMinuteOfDay in 0..1439) { "Start time must be inside the day." }
        }
        return repository.upsertScheduleRule(rule)
    }

    suspend fun deleteRule(planId: StudyPlanId, ruleId: ScheduleRuleId) =
        repository.deleteScheduleRule(planId, ruleId)

    suspend fun saveActivity(activity: PlannedActivity): PlannedActivity {
        require(activity.title.isNotBlank()) { "Planned activity title cannot be blank." }
        require(activity.plannedDurationMinutes in 5..720) { "Planned duration must be between 5 and 720 minutes." }
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
    ): ScheduleGenerationResult {
        val generated = planner.generateActivities(request, currentRules)
        val removed = repository.replaceUnlockedActivities(request.studyPlanId, generated)
        return ScheduleGenerationResult(
            generatedActivities = generated.size,
            removedUnlockedActivities = if (request.replaceUnlocked) removed else 0,
        )
    }

    suspend fun rebalanceMissed(
        planId: StudyPlanId,
        currentActivities: List<PlannedActivity>,
        handling: MissedActivityHandling,
    ): RebalanceResult {
        val updates = planner.rebalanceMissedActivities(currentActivities, handling, clock())
        val changed = repository.updatePlannedActivities(planId, updates)
        return RebalanceResult(changed, handling)
    }
}
