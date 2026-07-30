package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudyPlanId
import kotlinx.coroutines.flow.Flow

interface StudyPlanRepository {
    val plans: Flow<List<StudyPlan>>

    fun observePlan(planId: StudyPlanId): Flow<StudyPlan?>

    suspend fun create(plan: StudyPlan)

    suspend fun replace(plan: StudyPlan)

    suspend fun delete(planId: StudyPlanId)

    suspend fun clear()

    suspend fun restoreFoundationSamples()
}
