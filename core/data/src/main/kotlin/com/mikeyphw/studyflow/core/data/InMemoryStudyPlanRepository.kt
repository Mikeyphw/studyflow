package com.mikeyphw.studyflow.core.data

import com.mikeyphw.studyflow.core.domain.StudyPlanRepository
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudyPlanId
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

    override val plans: Flow<List<StudyPlan>> = mutablePlans.asStateFlow()

    override fun observePlan(planId: StudyPlanId): Flow<StudyPlan?> =
        plans.map { currentPlans -> currentPlans.firstOrNull { it.id == planId } }

    override suspend fun create(plan: StudyPlan) {
        mutex.withLock {
            require(mutablePlans.value.none { it.id == plan.id }) {
                "A Study Plan with id ${plan.id.value} already exists."
            }
            mutablePlans.value = mutablePlans.value + plan
        }
    }

    override suspend fun replace(plan: StudyPlan) {
        mutex.withLock {
            mutablePlans.value = mutablePlans.value.map { current ->
                if (current.id == plan.id) plan else current
            }
        }
    }

    override suspend fun delete(planId: StudyPlanId) {
        mutex.withLock {
            mutablePlans.value = mutablePlans.value.filterNot { it.id == planId }
        }
    }

    override suspend fun clear() {
        mutex.withLock {
            mutablePlans.value = emptyList()
        }
    }

    override suspend fun restoreFoundationSamples() {
        mutex.withLock {
            mutablePlans.value = FoundationStudyPlans.create()
        }
    }
}
