package com.mikeyphw.studyflow.core.data

import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyPlanStatus
import com.mikeyphw.studyflow.core.model.StudyType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class InMemoryStudyPlanRepositoryTest {
    @Test
    fun `created plan is emitted`() = runTest {
        val repository = InMemoryStudyPlanRepository(emptyList())
        repository.create(samplePlan())

        assertEquals(listOf(samplePlan()), repository.plans.first())
    }

    @Test
    fun `clear and restore samples are deterministic`() = runTest {
        val repository = InMemoryStudyPlanRepository()
        repository.clear()
        assertEquals(emptyList<StudyPlan>(), repository.plans.first())

        repository.restoreFoundationSamples()
        assertEquals(2, repository.plans.first().size)
    }

    private fun samplePlan() = StudyPlan(
        id = StudyPlanId("sample"),
        name = "Sample",
        description = "Description",
        type = StudyType.FREE_STUDY,
        status = StudyPlanStatus.ACTIVE,
        examDateIso = null,
        weeklyGoalHours = 10,
        progressPercent = 0,
        studiedMinutesThisWeek = 0,
        questionsAnswered = 0,
        averageAccuracyPercent = 0,
    )
}
