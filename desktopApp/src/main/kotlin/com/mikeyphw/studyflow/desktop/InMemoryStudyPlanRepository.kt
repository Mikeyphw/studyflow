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
    initialSettings: Map<StudyPlanId, StudyPlanSettings> = initialPlans.associate { plan ->
        plan.id to StudyPlanSettings.defaults(plan.id)
    },
    private val onChanged: (List<StudyPlan>, Map<StudyPlanId, StudyPlanSettings>) -> Unit = { _, _ -> },
) : StudyPlanRepository {
    private val planState = MutableStateFlow(initialPlans)
    private val settingsState = MutableStateFlow(initialSettings)

    override val plans: Flow<List<StudyPlan>> = planState

    override fun observePlan(planId: StudyPlanId): Flow<StudyPlan?> = planState.map { plans ->
        plans.firstOrNull { plan -> plan.id == planId }
    }

    override fun observePlanSettings(planId: StudyPlanId): Flow<StudyPlanSettings> = settingsState.map { settings ->
        settings[planId] ?: StudyPlanSettings.defaults(planId)
    }

    override fun observeStoredPlanSettings(planId: StudyPlanId): Flow<StudyPlanSettings?> =
        settingsState.map { settings ->
        settings[planId]
    }

    override suspend fun create(plan: StudyPlan, settings: StudyPlanSettings) {
        require(planState.value.none { existing -> existing.id == plan.id }) { "Study Plan already exists." }
        require(settings.studyPlanId == plan.id) { "Study Plan settings must belong to the created Study Plan." }
        planState.value = planState.value + plan
        settingsState.value = settingsState.value + (plan.id to settings)
        persist()
    }

    override suspend fun replace(plan: StudyPlan) {
        require(planState.value.any { existing -> existing.id == plan.id }) { "Study Plan does not exist." }
        planState.value = planState.value.map { existing -> if (existing.id == plan.id) plan else existing }
        persist()
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
        require(planState.value.any { plan -> plan.id == planId }) { "Study Plan does not exist." }
        planState.value = planState.value.map { plan ->
            if (plan.id == planId) plan.copy(status = status) else plan
        }
        persist()
    }

    override suspend fun moveToTrash(planId: StudyPlanId) {
        updateStatus(planId, StudyPlanStatus.TRASHED)
    }

    override suspend fun restoreFromTrash(planId: StudyPlanId) {
        updateStatus(planId, StudyPlanStatus.PAUSED)
    }

    override suspend fun updatePlanSettings(settings: StudyPlanSettings) {
        require(planState.value.any { plan -> plan.id == settings.studyPlanId }) { "Study Plan does not exist." }
        settingsState.value = settingsState.value + (settings.studyPlanId to settings)
        persist()
    }

    override suspend fun deletePermanently(planId: StudyPlanId) {
        require(planState.value.any { plan -> plan.id == planId }) { "Study Plan does not exist." }
        planState.value = planState.value.filterNot { plan -> plan.id == planId }
        settingsState.value = settingsState.value - planId
        persist()
    }

    override suspend fun clear() {
        planState.value = emptyList()
        settingsState.value = emptyMap()
        persist()
    }

    override suspend fun restoreFoundationSamples() {
        val sample = DesktopStudyFlowSampleData.create()
        planState.value = sample.plans
        settingsState.value = sample.plans.associate { plan -> plan.id to StudyPlanSettings.defaults(plan.id) }
        persist()
    }

    fun currentPlans(): List<StudyPlan> = planState.value

    fun currentSettings(): Map<StudyPlanId, StudyPlanSettings> = settingsState.value

    private fun persist() {
        onChanged(planState.value, settingsState.value)
    }
}
