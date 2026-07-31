package com.mikeyphw.studyflow.core.data

import com.mikeyphw.studyflow.core.domain.StudyPlanRepository
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyPlanSettings
import com.mikeyphw.studyflow.core.model.StudyPlanStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class InMemoryStudyPlanRepository(
    initialPlans: List<StudyPlan> = FoundationStudyPlans.create(),
) : StudyPlanRepository {
    private val mutex = Mutex()
    private val mutablePlans = MutableStateFlow(initialPlans)
    private val mutableSettings = MutableStateFlow(
        initialPlans.associate { plan -> plan.id to StudyPlanSettings.defaults(plan.id) },
    )

    override val plans: Flow<List<StudyPlan>> = mutablePlans.asStateFlow()

    override fun observePlan(planId: StudyPlanId): Flow<StudyPlan?> =
        plans.map { currentPlans -> currentPlans.firstOrNull { it.id == planId } }

    override fun observePlanSettings(planId: StudyPlanId): Flow<StudyPlanSettings> =
        mutableSettings.map { settings -> settings[planId] ?: StudyPlanSettings.defaults(planId) }

    override suspend fun create(
        plan: StudyPlan,
        settings: StudyPlanSettings,
    ) {
        mutex.withLock {
            require(mutablePlans.value.none { it.id == plan.id }) {
                "A Study Plan with id ${plan.id.value} already exists."
            }
            require(settings.studyPlanId == plan.id) { "Plan settings must belong to the created Study Plan." }
            mutablePlans.value = mutablePlans.value + plan
            mutableSettings.value = mutableSettings.value + (plan.id to settings)
        }
    }

    override suspend fun replace(plan: StudyPlan) {
        mutex.withLock {
            require(mutablePlans.value.any { it.id == plan.id }) { "Study Plan does not exist." }
            mutablePlans.value = mutablePlans.value.map { current ->
                if (current.id == plan.id) plan else current
            }
        }
    }

    override suspend fun duplicate(
        sourcePlanId: StudyPlanId,
        newPlanId: StudyPlanId,
        newName: String,
    ): StudyPlan = mutex.withLock {
        val source = requireNotNull(mutablePlans.value.firstOrNull { it.id == sourcePlanId })
        require(source.status != StudyPlanStatus.TRASHED) { "Restore a trashed Study Plan first." }
        require(mutablePlans.value.none { it.id == newPlanId }) { "Study Plan id already exists." }
        val duplicate = source.copy(
            id = newPlanId,
            name = newName,
            status = StudyPlanStatus.DRAFT,
            trashedAtEpochMillis = null,
        )
        mutablePlans.value = mutablePlans.value + duplicate
        val sourceSettings = mutableSettings.value[sourcePlanId] ?: StudyPlanSettings.defaults(sourcePlanId)
        mutableSettings.value = mutableSettings.value + (
            newPlanId to sourceSettings.copy(studyPlanId = newPlanId)
        )
        duplicate
    }

    override suspend fun updateStatus(
        planId: StudyPlanId,
        status: StudyPlanStatus,
    ) {
        require(status != StudyPlanStatus.TRASHED) { "Use moveToTrash for trashed Study Plans." }
        mutex.withLock {
            mutablePlans.value = mutablePlans.value.map { plan ->
                if (plan.id == planId) {
                    require(plan.status != StudyPlanStatus.TRASHED) { "Restore the Study Plan first." }
                    plan.copy(status = status, trashedAtEpochMillis = null)
                } else {
                    plan
                }
            }
        }
    }

    override suspend fun moveToTrash(planId: StudyPlanId) {
        mutex.withLock {
            mutablePlans.value = mutablePlans.value.map { plan ->
                if (plan.id == planId) {
                    plan.copy(status = StudyPlanStatus.TRASHED, trashedAtEpochMillis = 1L)
                } else {
                    plan
                }
            }
        }
    }

    override suspend fun restoreFromTrash(planId: StudyPlanId) {
        mutex.withLock {
            mutablePlans.value = mutablePlans.value.map { plan ->
                if (plan.id == planId) {
                    require(plan.status == StudyPlanStatus.TRASHED) { "Study Plan is not in trash." }
                    plan.copy(status = StudyPlanStatus.PAUSED, trashedAtEpochMillis = null)
                } else {
                    plan
                }
            }
        }
    }

    override suspend fun updatePlanSettings(settings: StudyPlanSettings) {
        mutex.withLock {
            require(mutablePlans.value.any { it.id == settings.studyPlanId }) { "Study Plan does not exist." }
            mutableSettings.value = mutableSettings.value + (settings.studyPlanId to settings)
        }
    }

    override suspend fun deletePermanently(planId: StudyPlanId) {
        mutex.withLock {
            mutablePlans.value = mutablePlans.value.filterNot { it.id == planId }
            mutableSettings.value = mutableSettings.value - planId
        }
    }

    override suspend fun clear() {
        mutex.withLock {
            mutablePlans.value = emptyList()
            mutableSettings.value = emptyMap()
        }
    }

    override suspend fun restoreFoundationSamples() {
        mutex.withLock {
            val samples = FoundationStudyPlans.create()
            mutablePlans.value = samples
            mutableSettings.value = samples.associate { plan ->
                plan.id to StudyPlanSettings.defaults(plan.id)
            }
        }
    }
}
