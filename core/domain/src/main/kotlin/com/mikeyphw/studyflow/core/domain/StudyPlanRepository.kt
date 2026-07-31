package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyPlanSettings
import com.mikeyphw.studyflow.core.model.StudyPlanStatus
import kotlinx.coroutines.flow.Flow

interface StudyPlanRepository {
    val plans: Flow<List<StudyPlan>>

    fun observePlan(planId: StudyPlanId): Flow<StudyPlan?>

    fun observePlanSettings(planId: StudyPlanId): Flow<StudyPlanSettings>

    suspend fun create(
        plan: StudyPlan,
        settings: StudyPlanSettings = StudyPlanSettings.defaults(plan.id),
    )

    suspend fun replace(plan: StudyPlan)

    suspend fun duplicate(
        sourcePlanId: StudyPlanId,
        newPlanId: StudyPlanId,
        newName: String,
    ): StudyPlan

    suspend fun updateStatus(
        planId: StudyPlanId,
        status: StudyPlanStatus,
    )

    suspend fun moveToTrash(planId: StudyPlanId)

    suspend fun restoreFromTrash(planId: StudyPlanId)

    suspend fun updatePlanSettings(settings: StudyPlanSettings)

    suspend fun deletePermanently(planId: StudyPlanId)

    suspend fun delete(planId: StudyPlanId) = deletePermanently(planId)

    suspend fun clear()

    suspend fun restoreFoundationSamples()
}
