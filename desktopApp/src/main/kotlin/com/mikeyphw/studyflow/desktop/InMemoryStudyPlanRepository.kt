package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.domain.StudyPlanRepository
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyPlanSettings
import com.mikeyphw.studyflow.core.model.StudyPlanStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class InMemoryStudyPlanRepository(
    initialPlans: List<StudyPlan>,
) : StudyPlanRepository {
    private val planState = MutableStateFlow(initialPlans)
    private val settingsState = MutableStateFlow(
        initialPlans.associate { plan -> plan.id to StudyPlanSettings.defaults(plan.id) },
    )

    override val plans: Flow<List<StudyPlan>> = planState

    override fun observePlan(planId: StudyPlanId): Flow<StudyPlan?> = planState.map { plans ->
        plans.firstOrNull { plan -> plan.id == planId }
    }

    override fun observePlanSettings(planId: StudyPlanId): Flow<StudyPlanSettings> = settingsState.map { settings ->
        settings[planId] ?: StudyPlanSettings.defaults(planId)
    }

    override fun observeStoredPlanSettings(
        planId: StudyPlanId,
    ): Flow<StudyPlanSettings?> = settingsState.map { settings ->
        settings[planId]
    }

    override suspend fun create(plan: StudyPlan, settings: StudyPlanSettings) {
        require(planState.value.none { existing -> existing.id == plan.id }) { "Study Plan already exists." }
        planState.value = planState.value + plan
        settingsState.value = settingsState.value + (plan.id to settings)
    }

    override suspend fun replace(plan: StudyPlan) {
        planState.value = planState.value.map { existing -> if (existing.id == plan.id) plan else existing }
    }

    override suspend fun duplicate(
        sourcePlanId: StudyPlanId,
        newPlanId: StudyPlanId,
        newName: String,
    ): StudyPlan {
        val source = requireNotNull(planState.value.firstOrNull { plan -> plan.id == sourcePlanId }) {
            "Source Study Plan does not exist."
        }
        val duplicate = source.copy(id = newPlanId, name = newName)
        create(duplicate, StudyPlanSettings.defaults(newPlanId))
        return duplicate
    }

    override suspend fun updateStatus(planId: StudyPlanId, status: StudyPlanStatus) {
        planState.value = planState.value.map { plan ->
            if (plan.id == planId) plan.copy(status = status) else plan
        }
    }

    override suspend fun moveToTrash(planId: StudyPlanId) {
        updateStatus(planId, StudyPlanStatus.TRASHED)
    }

    override suspend fun restoreFromTrash(planId: StudyPlanId) {
        updateStatus(planId, StudyPlanStatus.PAUSED)
    }

    override suspend fun updatePlanSettings(settings: StudyPlanSettings) {
        settingsState.value = settingsState.value + (settings.studyPlanId to settings)
    }

    override suspend fun deletePermanently(planId: StudyPlanId) {
        planState.value = planState.value.filterNot { plan -> plan.id == planId }
        settingsState.value = settingsState.value - planId
    }

    override suspend fun clear() {
        planState.value = emptyList()
        settingsState.value = emptyMap()
    }

    override suspend fun restoreFoundationSamples() {
        val sample = DesktopStudyFlowSampleData.create()
        planState.value = sample.plans
        settingsState.value = sample.plans.associate { plan -> plan.id to StudyPlanSettings.defaults(plan.id) }
    }

    fun currentPlans(): List<StudyPlan> = planState.value
}
