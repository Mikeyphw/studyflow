package com.mikeyphw.studyflow.core.testing

import org.junit.Assert.assertEquals
import org.junit.Test

class StudyPlanFixturesTest {
    @Test
    fun `fixture supports explicit identity`() {
        assertEquals("custom", StudyPlanFixtures.activePlan(id = "custom").id.value)
    }
}
