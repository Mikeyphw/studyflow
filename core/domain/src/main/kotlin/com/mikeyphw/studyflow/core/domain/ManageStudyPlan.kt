package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyPlanSettings
import com.mikeyphw.studyflow.core.model.StudyPlanStatus

class ManageStudyPlan(
    private val repository: StudyPlanRepository,
) {
    val plans = repository.plans

    fun observePlan(planId: StudyPlanId) = repository.observePlan(planId)

    fun observeSettings(planId: StudyPlanId) = repository.observeStoredPlanSettings(planId)

    suspend fun update(plan: StudyPlan) {
        require(plan.status !in setOf(StudyPlanStatus.ARCHIVED, StudyPlanStatus.TRASHED)) {
            "Archived or trashed Study Plans must be restored before editing."
        }
        repository.replace(plan)
    }

    suspend fun duplicate(
        sourcePlanId: StudyPlanId,
        newPlanId: StudyPlanId,
        newName: String,
    ): StudyPlan {
        require(newName.isNotBlank()) { "Duplicated Study Plan name must not be blank." }
        require(sourcePlanId != newPlanId) { "Duplicated Study Plan must use a new id." }
        return repository.duplicate(sourcePlanId, newPlanId, newName.trim())
    }

    suspend fun activate(planId: StudyPlanId) =
        repository.updateStatus(planId, StudyPlanStatus.ACTIVE)

    suspend fun pause(planId: StudyPlanId) =
        repository.updateStatus(planId, StudyPlanStatus.PAUSED)

    suspend fun complete(planId: StudyPlanId) =
        repository.updateStatus(planId, StudyPlanStatus.COMPLETED)

    suspend fun archive(planId: StudyPlanId) =
        repository.updateStatus(planId, StudyPlanStatus.ARCHIVED)

    suspend fun restoreArchived(planId: StudyPlanId) =
        repository.updateStatus(planId, StudyPlanStatus.PAUSED)

    suspend fun moveToTrash(planId: StudyPlanId) = repository.moveToTrash(planId)

    suspend fun restoreFromTrash(planId: StudyPlanId) = repository.restoreFromTrash(planId)

    suspend fun deletePermanently(planId: StudyPlanId) = repository.deletePermanently(planId)

    suspend fun updateSettings(settings: StudyPlanSettings) =
        repository.updatePlanSettings(settings)
}
