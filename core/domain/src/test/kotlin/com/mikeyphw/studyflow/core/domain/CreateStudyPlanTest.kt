package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyPlanStatus
import com.mikeyphw.studyflow.core.model.StudyType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Test

class CreateStudyPlanTest {
    @Test
    fun `rejects blank names`() {
        val repository = RecordingRepository()
        val useCase = CreateStudyPlan(repository)

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { useCase(samplePlan(name = " ")) }
        }
    }

    private fun samplePlan(name: String) = StudyPlan(
        id = StudyPlanId("id"),
        name = name,
        description = "",
        type = StudyType.PUBLIC_EXAMINATION,
        status = StudyPlanStatus.ACTIVE,
        examDateIso = null,
        weeklyGoalHours = 20,
        progressPercent = 0,
        studiedMinutesThisWeek = 0,
        questionsAnswered = 0,
        averageAccuracyPercent = 0,
    )
}

private class RecordingRepository : StudyPlanRepository {
    private val state = MutableStateFlow<List<StudyPlan>>(emptyList())
    override val plans: Flow<List<StudyPlan>> = state

    override fun observePlan(planId: StudyPlanId): Flow<StudyPlan?> =
        state.map { plans -> plans.firstOrNull { it.id == planId } }

    override suspend fun create(plan: StudyPlan) {
        state.value = state.value + plan
    }

    override suspend fun replace(plan: StudyPlan) = Unit
    override suspend fun delete(planId: StudyPlanId) = Unit
    override suspend fun clear() = Unit
    override suspend fun restoreFoundationSamples() = Unit
}
