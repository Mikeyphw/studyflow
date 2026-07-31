package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyPlanSettings
import com.mikeyphw.studyflow.core.model.StudyPlanStatus
import com.mikeyphw.studyflow.core.model.StudyType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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

    @Test
    fun `creates plan and matching settings together`() = runBlocking {
        val repository = RecordingRepository()
        val plan = samplePlan(name = "Plan")
        val settings = StudyPlanSettings.defaults(plan.id).copy(preferredSessionMinutes = 25)

        CreateStudyPlan(repository)(plan, settings)

        assertEquals(plan, repository.plansState.value.single())
        assertEquals(settings, repository.settingsState.value.getValue(plan.id))
    }
}

class ManageStudyPlanTest {
    @Test
    fun `duplicate receives new identity and draft status`() = runBlocking {
        val repository = RecordingRepository(listOf(samplePlan("Source")))
        val manager = ManageStudyPlan(repository)

        val duplicate = manager.duplicate(
            sourcePlanId = StudyPlanId("id"),
            newPlanId = StudyPlanId("copy"),
            newName = "Source Copy",
        )

        assertEquals(StudyPlanId("copy"), duplicate.id)
        assertEquals(StudyPlanStatus.DRAFT, duplicate.status)
    }

    @Test
    fun `restore from trash returns plan paused`() = runBlocking {
        val repository = RecordingRepository(listOf(samplePlan("Source")))
        val manager = ManageStudyPlan(repository)

        manager.moveToTrash(StudyPlanId("id"))
        manager.restoreFromTrash(StudyPlanId("id"))

        assertEquals(StudyPlanStatus.PAUSED, repository.plansState.value.single().status)
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

private class RecordingRepository(
    initialPlans: List<StudyPlan> = emptyList(),
) : StudyPlanRepository {
    val plansState = MutableStateFlow(initialPlans)
    val settingsState = MutableStateFlow(
        initialPlans.associate { it.id to StudyPlanSettings.defaults(it.id) },
    )
    override val plans: Flow<List<StudyPlan>> = plansState

    override fun observePlan(planId: StudyPlanId): Flow<StudyPlan?> =
        plansState.map { plans -> plans.firstOrNull { it.id == planId } }

    override fun observePlanSettings(planId: StudyPlanId): Flow<StudyPlanSettings> =
        settingsState.map { it[planId] ?: StudyPlanSettings.defaults(planId) }

    override suspend fun create(plan: StudyPlan, settings: StudyPlanSettings) {
        plansState.value += plan
        settingsState.value += plan.id to settings
    }

    override suspend fun replace(plan: StudyPlan) {
        plansState.value = plansState.value.map { if (it.id == plan.id) plan else it }
    }

    override suspend fun duplicate(
        sourcePlanId: StudyPlanId,
        newPlanId: StudyPlanId,
        newName: String,
    ): StudyPlan {
        val source = requireNotNull(plansState.value.firstOrNull { it.id == sourcePlanId })
        val duplicate = source.copy(id = newPlanId, name = newName, status = StudyPlanStatus.DRAFT)
        plansState.value += duplicate
        settingsState.value += newPlanId to StudyPlanSettings.defaults(newPlanId)
        return duplicate
    }

    override suspend fun updateStatus(planId: StudyPlanId, status: StudyPlanStatus) {
        plansState.value = plansState.value.map { if (it.id == planId) it.copy(status = status) else it }
    }

    override suspend fun moveToTrash(planId: StudyPlanId) {
        updateStatus(planId, StudyPlanStatus.TRASHED)
    }

    override suspend fun restoreFromTrash(planId: StudyPlanId) {
        updateStatus(planId, StudyPlanStatus.PAUSED)
    }

    override suspend fun updatePlanSettings(settings: StudyPlanSettings) {
        settingsState.value += settings.studyPlanId to settings
    }

    override suspend fun deletePermanently(planId: StudyPlanId) {
        plansState.value = plansState.value.filterNot { it.id == planId }
    }

    override suspend fun clear() {
        plansState.value = emptyList()
    }

    override suspend fun restoreFoundationSamples() = Unit
}
