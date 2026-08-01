package com.mikeyphw.studyflow.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserExperiencePolishAuditorTest {
    @Test
    fun reportMarksEveryMvpPolishAreaReady() {
        val report = UserExperiencePolishAuditor.evaluate()

        assertEquals(UserExperiencePolishArea.entries.size, report.totalCount)
        assertEquals(100, report.readyPercent)
        assertEquals(0, report.blockingCount)
        assertTrue(report.releaseReady)
    }
}
