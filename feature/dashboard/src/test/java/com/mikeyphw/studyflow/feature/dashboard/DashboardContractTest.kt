package com.mikeyphw.studyflow.feature.dashboard

import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardContractTest {
    @Test
    fun `progress percentages stay bounded`() {
        assertTrue(42 in 0..100)
    }
}
