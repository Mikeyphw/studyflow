package com.mikeyphw.studyflow.core.data

import androidx.room.withTransaction
import com.mikeyphw.studyflow.core.database.StudyFlowDatabase
import com.mikeyphw.studyflow.core.database.mapper.asEntity
import com.mikeyphw.studyflow.core.database.mapper.asExternalModel
import com.mikeyphw.studyflow.core.domain.StudySchedulePlanner
import com.mikeyphw.studyflow.core.domain.AdaptiveReschedulePlanner
import com.mikeyphw.studyflow.core.domain.StudyScheduleRepository
import com.mikeyphw.studyflow.core.domain.PlannedActivityRebalanceMutation
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
import kotlinx.coroutines.flow.combine

internal class RoomStudyScheduleRepository(
    private val database: StudyFlowDatabase,
    private val diagnosticRecorder: com.mikeyphw.studyflow.core.diagnostics.DiagnosticRecorder = com.mikeyphw.studyflow.core.diagnostics.NoOpDiagnosticRecorder,
) : StudyScheduleRepository {
    private val planningDao = database.studyPlanningDao()
    private val planDao = database.studyPlanDao()
    private val planner = StudySchedulePlanner()

    override fun observeSchedule(planId: StudyPlanId): Flow<StudySchedule> = combine(
        planningDao.observeStrategy(planId.value),
        planningDao.observeScheduleRules(planId.value),
        planningDao.observePlannedActivities(planId.value),
    ) { strategy, rules, activities ->
        StudySchedule(
            studyPlanId = planId,
            strategy = strategy?.asExternalModel(),
            scheduleRules = rules.map { it.asExternalModel() },
            plannedActivities = activities.map { it.asExternalModel() },
        )
    }

    override suspend fun upsertStrategy(strategy: StudyStrategy): StudyStrategy = database.withTransaction {
        requirePlan(strategy.studyPlanId)
        planningDao.upsertStrategy(strategy.asEntity())
        strategy
    }

    override suspend fun upsertScheduleRule(rule: ScheduleRule): ScheduleRule = database.withTransaction {
        requirePlan(rule.studyPlanId)
        rule.subjectId?.let { subjectId -> requireSubject(rule.studyPlanId, subjectId.value) }
        val candidateRules = planningDao.findScheduleRules(rule.studyPlanId.value)
            .map { it.asExternalModel() }
            .filterNot { it.id == rule.id } + rule
        planner.validateRules(candidateRules)
        planningDao.upsertScheduleRule(rule.asEntity())
        requireNotNull(planningDao.findScheduleRule(rule.studyPlanId.value, rule.id.value)).asExternalModel()
    }

    override suspend fun deleteScheduleRule(planId: StudyPlanId, ruleId: ScheduleRuleId) {
        database.withTransaction {
            requireNotNull(planningDao.findScheduleRule(planId.value, ruleId.value)) {
                "Schedule rule does not belong to this Study Plan."
            }
            val prefix = StudySchedulePlanner.generatedRulePrefix(ruleId)
            val generated = planningDao.findPlannedActivities(planId.value)
                .filter { it.id.startsWith(prefix) }
            require(generated.none { it.status == PlannedActivityStatus.IN_PROGRESS.name }) {
                "Cannot delete a schedule rule while one of its generated activities is in progress."
            }
            // Lock protects an occurrence from regeneration/rebalancing, but an explicit rule deletion
            // owns the lifecycle of every non-running generated occurrence. Completed history is retained.
            planningDao.deleteGeneratedActivitiesForRule(planId.value, "$prefix%")
            require(planningDao.deleteScheduleRule(planId.value, ruleId.value) == 1) {
                "Schedule rule does not belong to this Study Plan."
            }
        }
    }

    override suspend fun upsertPlannedActivity(activity: PlannedActivity): PlannedActivity = database.withTransaction {
        requirePlan(activity.studyPlanId)
        require(activity.status != PlannedActivityStatus.IN_PROGRESS) {
            "An in-progress activity can only be created by starting its Study Session."
        }
        activity.subjectId?.let { subjectId -> requireSubject(activity.studyPlanId, subjectId.value) }
        activity.topicId?.let { topicId -> requireTopic(activity.studyPlanId, topicId.value) }
        val existing = planningDao.findPlannedActivity(activity.studyPlanId.value, activity.id.value)?.asExternalModel()
        require(existing?.status != PlannedActivityStatus.IN_PROGRESS || existing == activity) {
            "An in-progress activity is owned by its active Study Session and cannot be edited independently."
        }
        planningDao.upsertPlannedActivity(activity.asEntity())
        requireNotNull(planningDao.findPlannedActivity(activity.studyPlanId.value, activity.id.value)).asExternalModel()
    }

    override suspend fun updatePlannedActivityStatus(
        planId: StudyPlanId,
        activityId: PlannedActivityId,
        status: PlannedActivityStatus,
        completedAtEpochMillis: Long?,
    ): PlannedActivity = database.withTransaction {
        val activity = requireNotNull(planningDao.findPlannedActivity(planId.value, activityId.value)) {
            "Planned activity does not belong to this Study Plan."
        }.asExternalModel()
        require(activity.status != PlannedActivityStatus.IN_PROGRESS) {
            "An in-progress activity must transition through its active Study Session."
        }
        require(activity.status in setOf(PlannedActivityStatus.PLANNED, PlannedActivityStatus.MISSED)) {
            "Completed or terminal planned activities cannot be transitioned independently."
        }
        require(status in setOf(PlannedActivityStatus.COMPLETED, PlannedActivityStatus.MISSED)) {
            "Schedule CRUD may only mark an activity completed or missed; session state owns IN_PROGRESS transitions."
        }
        require(planningDao.compareAndSetPlannedActivityStatus(
            planId = planId.value,
            activityId = activityId.value,
            expectedStatus = activity.status.name,
            newStatus = status.name,
            newCompletedAtEpochMillis = completedAtEpochMillis,
        ) == 1) { "Planned activity changed concurrently; refresh before retrying." }
        activity.copy(status = status, completedAtEpochMillis = completedAtEpochMillis)
    }

    override suspend fun deletePlannedActivity(planId: StudyPlanId, activityId: PlannedActivityId) {
        database.withTransaction {
            val activity = requireNotNull(planningDao.findPlannedActivity(planId.value, activityId.value)) {
                "Planned activity does not belong to this Study Plan."
            }
            require(activity.status != PlannedActivityStatus.IN_PROGRESS.name) {
                "An in-progress activity cannot be deleted independently of its active Study Session."
            }
            require(planningDao.deletePlannedActivity(planId.value, activityId.value) == 1) {
                "Planned activity changed concurrently; refresh before retrying."
            }
        }
    }

    override suspend fun replaceUnlockedActivities(
        planId: StudyPlanId,
        activities: List<PlannedActivity>,
    ): Int = database.withTransaction {
        requirePlan(planId)
        require(activities.all { it.studyPlanId == planId }) { "Generated activities must belong to one Study Plan." }
        require(activities.none { it.status == PlannedActivityStatus.IN_PROGRESS }) {
            "Generated activities cannot bypass the Study Session start transition."
        }
        val removed = planningDao.deleteReplaceablePlannedActivities(planId.value)
        planningDao.upsertPlannedActivities(activities.map { it.asEntity() })
        removed
    }

    override suspend fun applyGeneratedActivities(
        planId: StudyPlanId,
        activities: List<PlannedActivity>,
        replaceUnlocked: Boolean,
    ): ScheduleGenerationResult = diagnosticRecorder.recordRepositoryMutation(
        area = com.mikeyphw.studyflow.core.diagnostics.DiagnosticArea.SCHEDULE,
        action = "schedule-generate",
    ) {
        database.withTransaction {
        requirePlan(planId)
        require(activities.all { it.studyPlanId == planId }) { "Generated activities must belong to one Study Plan." }
        require(activities.none { it.status == PlannedActivityStatus.IN_PROGRESS }) {
            "Generated activities cannot bypass the Study Session start transition."
        }
        val removed = if (replaceUnlocked) planningDao.deleteReplaceablePlannedActivities(planId.value) else 0
        val current = planningDao.findPlannedActivities(planId.value).map { it.asExternalModel() }.toMutableList()
        var applied = 0
        activities.forEach { candidate ->
            val existing = current.firstOrNull { it.id == candidate.id }
            val replaceable = existing != null && replaceUnlocked && !existing.locked &&
                existing.status in setOf(PlannedActivityStatus.PLANNED, PlannedActivityStatus.MISSED)
            if (existing == null || replaceable) {
                val withoutCandidate = current.filterNot { it.id == candidate.id }
                planner.validateCandidate(candidate, withoutCandidate + candidate, planningDao.findScheduleRules(planId.value).map { it.asExternalModel() })
                planningDao.upsertPlannedActivity(candidate.asEntity())
                current.removeAll { it.id == candidate.id }
                current += candidate
                applied++
            }
        }
        ScheduleGenerationResult(generatedActivities = applied, removedUnlockedActivities = removed)
    }
    }

    override suspend fun applyAdaptivePreview(
        planId: StudyPlanId,
        preview: AdaptiveReschedulePreview,
    ): Int = database.withTransaction {
        require(preview.studyPlanId == planId) { "Adaptive preview belongs to another Study Plan." }
        val strategy = planningDao.findStrategy(planId.value)?.asExternalModel()
        val rules = planningDao.findScheduleRules(planId.value).map { it.asExternalModel() }
        val currentActivities = planningDao.findPlannedActivities(planId.value).map { it.asExternalModel() }
        val currentSchedule = StudySchedule(planId, strategy, rules, currentActivities)
        require(AdaptiveReschedulePlanner.scheduleVersionToken(currentSchedule) == preview.scheduleVersionToken) {
            "Schedule changed after this adaptive preview was approved. Refresh before applying."
        }

        val currentById = currentActivities.associateBy { it.id }
        val proposedById = currentActivities.associateBy { it.id }.toMutableMap()
        val mutations = preview.changes.filter { it.executable }.map { change ->
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
        mutations.forEach { (_, proposed) -> planner.validateCandidate(proposed, finalActivities, rules) }

        mutations.forEach { (current, proposed) ->
            require(
                planningDao.compareAndSetAdaptivePlannedActivity(
                    planId = planId.value,
                    activityId = current.id.value,
                    expectedType = current.type.name,
                    expectedStatus = current.status.name,
                    expectedStartEpochMillis = current.scheduledStartEpochMillis,
                    expectedDurationMinutes = current.plannedDurationMinutes,
                    newType = proposed.type.name,
                    newStatus = proposed.status.name,
                    newStartEpochMillis = proposed.scheduledStartEpochMillis,
                    newDurationMinutes = proposed.plannedDurationMinutes,
                    newCompletedAtEpochMillis = proposed.completedAtEpochMillis,
                ) == 1,
            ) { "Adaptive schedule changed concurrently; no approved changes were applied." }
        }
        mutations.size
    }

    override suspend fun applyMissedRebalance(
        planId: StudyPlanId,
        mutations: List<PlannedActivityRebalanceMutation>,
    ): Int = database.withTransaction {
        requirePlan(planId)
        require(mutations.all { it.expected.studyPlanId == planId && it.proposed.studyPlanId == planId }) {
            "Missed-activity rebalance must stay inside one Study Plan."
        }
        mutations.forEach { mutation ->
            val expected = mutation.expected
            val proposed = mutation.proposed
            require(expected.id == proposed.id) { "Missed-activity rebalance cannot change activity identity." }
            require(!expected.locked && !proposed.locked) { "Locked activities cannot be changed by missed rebalance." }
            require(expected.status in setOf(PlannedActivityStatus.PLANNED, PlannedActivityStatus.MISSED)) {
                "Only planned or missed activities can be rebalanced."
            }
            require(proposed.status != PlannedActivityStatus.IN_PROGRESS) {
                "Missed rebalance cannot create an in-progress activity."
            }
            require(
                planningDao.compareAndSetMissedRebalanceActivity(
                    planId = planId.value,
                    activityId = expected.id.value,
                    expectedType = expected.type.name,
                    expectedStatus = expected.status.name,
                    expectedStartEpochMillis = expected.scheduledStartEpochMillis,
                    expectedDurationMinutes = expected.plannedDurationMinutes,
                    newStatus = proposed.status.name,
                    newStartEpochMillis = proposed.scheduledStartEpochMillis,
                    newDurationMinutes = proposed.plannedDurationMinutes,
                    newCompletedAtEpochMillis = proposed.completedAtEpochMillis,
                ) == 1,
            ) { "Planned activity changed concurrently; no missed-rebalance changes were applied." }
        }
        mutations.size
    }

    private suspend fun requirePlan(planId: StudyPlanId) {
        requireNotNull(planDao.findById(planId.value)) { "Study Plan does not exist." }
    }

    private suspend fun requireSubject(planId: StudyPlanId, subjectId: String) {
        requireNotNull(database.studyStructureDao().findSubject(planId.value, subjectId)) {
            "The selected subject does not belong to this Study Plan."
        }
    }

    private suspend fun requireTopic(planId: StudyPlanId, topicId: String) {
        requireNotNull(database.studyStructureDao().findTopic(planId.value, topicId)) {
            "The selected topic does not belong to this Study Plan."
        }
    }
}
