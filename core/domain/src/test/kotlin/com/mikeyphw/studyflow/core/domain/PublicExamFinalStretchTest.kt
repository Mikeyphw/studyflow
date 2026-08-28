package com.mikeyphw.studyflow.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PublicExamFinalStretchTest {
    @Test
    fun `bands cover more than thirty through exam eve`() {
        assertEquals(PublicExamFinalStretchBand.BEYOND_30_DAYS, PublicExamFinalStretchPlanner.plan(31)?.band)
        assertEquals(PublicExamFinalStretchBand.DAYS_30, PublicExamFinalStretchPlanner.plan(30)?.band)
        assertEquals(PublicExamFinalStretchBand.DAYS_15, PublicExamFinalStretchPlanner.plan(15)?.band)
        assertEquals(PublicExamFinalStretchBand.DAYS_7, PublicExamFinalStretchPlanner.plan(7)?.band)
        assertEquals(PublicExamFinalStretchBand.DAYS_2, PublicExamFinalStretchPlanner.plan(2)?.band)
        assertEquals(PublicExamFinalStretchBand.EXAM_EVE, PublicExamFinalStretchPlanner.plan(1)?.band)
        assertEquals(PublicExamFinalStretchBand.EXAM_EVE, PublicExamFinalStretchPlanner.plan(0)?.band)
        assertNull(PublicExamFinalStretchPlanner.plan(null))
    }

    @Test
    fun `new content decreases while consolidation grows near the exam`() {
        val longRange = assertNotNull(PublicExamFinalStretchPlanner.plan(40)).allocation
        val sevenDays = assertNotNull(PublicExamFinalStretchPlanner.plan(7)).allocation
        val eve = assertNotNull(PublicExamFinalStretchPlanner.plan(1)).allocation

        assertTrue(longRange.newContentPercent > sevenDays.newContentPercent)
        assertTrue(sevenDays.newContentPercent > eve.newContentPercent)
        assertEquals(0, eve.newContentPercent)
        assertTrue(
            eve.reviewPercent + eve.errorNotebookPercent >
                longRange.reviewPercent + longRange.errorNotebookPercent,
        )
        listOf(longRange, sevenDays, eve).forEach { allocation ->
            assertEquals(
                100,
                allocation.newContentPercent + allocation.reviewPercent + allocation.errorNotebookPercent +
                    allocation.questionsPercent + allocation.mockExamPercent + allocation.legislationPercent,
            )
        }
    }
}
