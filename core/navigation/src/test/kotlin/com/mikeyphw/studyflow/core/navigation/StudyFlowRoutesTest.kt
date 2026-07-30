package com.mikeyphw.studyflow.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class StudyFlowRoutesTest {
    @Test
    fun `dashboard route retains plan id`() {
        assertEquals("plan-42", DashboardRoute("plan-42").planId)
    }
}
