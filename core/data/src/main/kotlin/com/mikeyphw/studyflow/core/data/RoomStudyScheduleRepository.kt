package com.mikeyphw.studyflow.core.data

import androidx.room.withTransaction
import com.mikeyphw.studyflow.core.database.StudyFlowDatabase
import com.mikeyphw.studyflow.core.database.mapper.asEntity
import com.mikeyphw.studyflow.core.database.mapper.asExternalModel
import com.mikeyphw.studyflow.core.domain.StudyScheduleRepository
import com.mikeyphw.studyflow.core.model.PlannedActivity
import com.mikeyphw.studyflow.core.model.PlannedActivityId
import com.mikeyphw.studyflow.core.model.PlannedActivityStatus
import com.mikeyphw.studyflow.core.model.ScheduleRule
import com.mikeyphw.studyflow.core.model.ScheduleRuleId
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudySchedule
import com.mikeyphw.studyflow.core.model.StudyStrategy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

internal class RoomStudyScheduleRepository(
    private val database: StudyFlowDatabase,
) : StudyScheduleRepository {
    private val planningDao = database.studyPlanningDao()
    private val planDao = database.studyPlanDao()

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
        planningDao.upsertScheduleRule(rule.asEntity())
        requireNotNull(planningDao.findScheduleRule(rule.studyPlanId.value, rule.id.value)).asExternalModel()
    }

    override suspend fun deleteScheduleRule(planId: StudyPlanId, ruleId: ScheduleRuleId) {
        database.withTransaction {
            require(planningDao.deleteScheduleRule(planId.value, ruleId.value) == 1) {
                "Schedule rule does not belong to this Study Plan."
            }
        }
    }

    override suspend fun upsertPlannedActivity(activity: PlannedActivity): PlannedActivity = database.withTransaction {
        requirePlan(activity.studyPlanId)
        activity.subjectId?.let { subjectId -> requireSubject(activity.studyPlanId, subjectId.value) }
        activity.topicId?.let { topicId -> requireTopic(activity.studyPlanId, topicId.value) }
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
        val updated = activity.copy(status = status, completedAtEpochMillis = completedAtEpochMillis)
        planningDao.upsertPlannedActivity(updated.asEntity())
        updated
    }

    override suspend fun deletePlannedActivity(planId: StudyPlanId, activityId: PlannedActivityId) {
        database.withTransaction {
            require(planningDao.deletePlannedActivity(planId.value, activityId.value) == 1) {
                "Planned activity does not belong to this Study Plan."
            }
        }
    }

    override suspend fun replaceUnlockedActivities(
        planId: StudyPlanId,
        activities: List<PlannedActivity>,
    ): Int = database.withTransaction {
        requirePlan(planId)
        require(activities.all { it.studyPlanId == planId }) { "Generated activities must belong to one Study Plan." }
        val removed = planningDao.deleteReplaceablePlannedActivities(planId.value)
        planningDao.upsertPlannedActivities(activities.map { it.asEntity() })
        removed
    }

    override suspend fun updatePlannedActivities(
        planId: StudyPlanId,
        activities: List<PlannedActivity>,
    ): Int = database.withTransaction {
        requirePlan(planId)
        require(activities.all { it.studyPlanId == planId }) { "Activities must belong to one Study Plan." }
        planningDao.upsertPlannedActivities(activities.map { it.asEntity() })
        activities.size
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
