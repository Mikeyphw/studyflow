package com.mikeyphw.studyflow.core.data

import androidx.room.withTransaction
import com.mikeyphw.studyflow.core.database.StudyFlowDatabase
import com.mikeyphw.studyflow.core.database.entity.DatabaseMetadataEntity
import com.mikeyphw.studyflow.core.database.mapper.asEntity
import com.mikeyphw.studyflow.core.domain.SamplePlanProvisioningRepository
import com.mikeyphw.studyflow.core.domain.SamplePlanProvisioningRequest
import com.mikeyphw.studyflow.core.model.StudyPlanId

internal class RoomSamplePlanProvisioningRepository(
    private val database: StudyFlowDatabase,
    private val clock: () -> Long,
) : SamplePlanProvisioningRepository {
    override suspend fun provision(request: SamplePlanProvisioningRequest): StudyPlanId = database.withTransaction {
        val metadataDao = database.databaseMetadataDao()
        val metadataKey = provisioningMetadataKey(request.provisioningKey)
        metadataDao.findValue(metadataKey)?.let { committedPlanId ->
            if (database.studyPlanDao().countById(committedPlanId) == 1) {
                return@withTransaction StudyPlanId(committedPlanId)
            }
        }

        val planId = request.plan.id
        require(database.studyPlanDao().countById(planId.value) == 0) {
            "A Study Plan with id ${planId.value} already exists."
        }
        val now = clock()
        database.studyPlanDao().insert(request.plan.asEntity(nowEpochMillis = now))
        database.studySupportDao().upsertPlanSettings(request.settings.asEntity())
        request.target?.let { target -> database.studyStructureDao().upsertTarget(target.asEntity()) }
        request.strategy?.let { strategy -> database.studyPlanningDao().upsertStrategy(strategy.asEntity()) }
        if (request.goals.isNotEmpty()) {
            database.studyPlanningDao().upsertStudyGoals(request.goals.map { goal -> goal.asEntity() })
        }
        if (request.subjects.isNotEmpty()) {
            database.studyStructureDao().upsertSubjects(request.subjects.map { subject -> subject.asEntity() })
        }
        // The sample templates currently use flat topics. Keep insertion ordered so future parented
        // templates can place a parent before its children without relying on UI call order.
        val pending = request.topics.toMutableList()
        val inserted = mutableSetOf<com.mikeyphw.studyflow.core.model.TopicId>()
        while (pending.isNotEmpty()) {
            val ready = pending.filter { topic -> topic.parentTopicId == null || topic.parentTopicId in inserted }
            require(ready.isNotEmpty()) { "Sample topic hierarchy contains a cycle or missing parent." }
            ready.forEach { topic ->
                database.studyStructureDao().upsertTopic(topic.asEntity())
                inserted += topic.id
            }
            pending.removeAll(ready.toSet())
        }
        if (request.scheduleRules.isNotEmpty()) {
            database.studyPlanningDao().upsertScheduleRules(request.scheduleRules.map { rule -> rule.asEntity() })
        }
        metadataDao.upsert(DatabaseMetadataEntity(metadataKey, planId.value))
        planId
    }

    override suspend fun findProvisionedPlan(provisioningKey: String): StudyPlanId? {
        val committedPlanId = database.databaseMetadataDao()
            .findValue(provisioningMetadataKey(provisioningKey))
            ?: return null
        return committedPlanId
            .takeIf { database.studyPlanDao().countById(it) == 1 }
            ?.let(::StudyPlanId)
    }

    private fun provisioningMetadataKey(provisioningKey: String): String =
        "sample_provisioning:${provisioningKey.trim()}"
}
