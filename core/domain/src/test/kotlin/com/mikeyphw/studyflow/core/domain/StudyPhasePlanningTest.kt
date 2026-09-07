package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyStrategy
import com.mikeyphw.studyflow.core.model.StudyStrategyId
import com.mikeyphw.studyflow.core.model.StudyStrategyType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyPhasePlanningTest {
    @Test
    fun `BB reference phase dates and targets are preserved while allocations are marked suggested`() {
        val plan = BancoDoBrasilStudyPhasePreset.plan()
        assertEquals(listOf(300, 500, 700, 1_000), plan.phases.map { it.questionGoal })
        assertEquals(listOf(3, 5, 4, 4), plan.phases.map { it.mockExamGoal })
        assertEquals(LocalDate.parse("2026-09-07"), plan.phases.first().startsOn)
        assertEquals(LocalDate.parse("2026-12-31"), plan.phases.last().endsOn)
        assertTrue(plan.phases.all { it.allocationIsStudyFlowSuggested })
    }

    @Test
    fun `codec round trips inside an existing JSON object`() {
        val original = BancoDoBrasilStudyPhasePreset.plan()
        val encoded = StudyPhasePlanCodec.encode("{\"publicExamReviewDays\":[1,7,30]}", original)
        assertTrue("publicExamReviewDays" in encoded)
        assertEquals(original, StudyPhasePlanCodec.decode(encoded))
    }

    @Test
    fun `active phase changes generation ratios without mutating strategy identity or type`() {
        val strategy = StudyStrategy(
            id = StudyStrategyId("strategy"), studyPlanId = StudyPlanId("plan"), type = StudyStrategyType.FIXED_WEEKLY,
            minimumSessionMinutes = 25, maximumSessionMinutes = 120, theoryRatioPercent = 40,
            questionRatioPercent = 40, reviewRatioPercent = 20, adaptivePlanningEnabled = false,
            configurationJson = "{}",
        )
        val effective = StudyPhasePlanning.effectiveStrategy(
            strategy, BancoDoBrasilStudyPhasePreset.plan(), LocalDate.parse("2026-12-15"),
        )!!
        assertEquals(strategy.id, effective.id)
        assertEquals(StudyStrategyType.FIXED_WEEKLY, effective.type)
        assertEquals(10, effective.theoryRatioPercent)
        assertEquals(65, effective.questionRatioPercent)
        assertEquals(25, effective.reviewRatioPercent)
        assertNull(BancoDoBrasilStudyPhasePreset.plan().activeOn(LocalDate.parse("2027-01-01")))
    }
}
