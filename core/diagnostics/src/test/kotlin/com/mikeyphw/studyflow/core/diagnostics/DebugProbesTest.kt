package com.mikeyphw.studyflow.core.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugProbesTest {
    @Test
    fun `safe suite contains only read-only probes`() {
        assertTrue(DebugProbeCatalog.safeSuiteIds.isNotEmpty())
        DebugProbeCatalog.safeSuiteIds.forEach { id ->
            assertEquals(DebugProbeSafety.READ_ONLY, DebugProbeCatalog.descriptor(id).safety)
        }
        assertFalse(DebugProbeId.REMINDER_RECONCILIATION in DebugProbeCatalog.safeSuiteIds)
        assertFalse(DebugProbeId.WIDGET_REFRESH in DebugProbeCatalog.safeSuiteIds)
    }

    @Test
    fun `catalog contains no destructive probes`() {
        assertEquals(2, DebugProbeCatalog.descriptors.count { it.safety == DebugProbeSafety.VISIBLE_SIDE_EFFECT })
        assertEquals(DebugProbeId.entries.size, DebugProbeCatalog.descriptors.map { it.id }.distinct().size)
    }

    @Test
    fun `missing plan is blocked rather than failed`() {
        val result = DebugProbeReportPolicy.blockedForMissingPlan(
            DebugProbeId.BACKUP_DRY_RUN,
            nowEpochMillis = 123L,
        )
        assertEquals(DebugProbeStatus.BLOCKED, result.status)
        assertEquals(DebugProbeMessage.MISSING_DEFAULT_PLAN, result.message)
        assertEquals(123L, result.completedAtEpochMillis)
    }

    @Test
    fun `report transitions remove running state and sanitize facts`() {
        val running = DebugProbeReportPolicy.markRunning(
            DebugProbeReport.empty(),
            DebugProbeId.SEARCH_QUERY,
        )
        assertTrue(DebugProbeId.SEARCH_QUERY in running.running)

        val completed = DebugProbeReportPolicy.complete(
            running,
            DebugProbeResult(
                id = DebugProbeId.SEARCH_QUERY,
                status = DebugProbeStatus.PASSED,
                safeFacts = mapOf(
                    "count" to "4",
                    "reason" to "Private calculus note",
                ),
                durationMillis = 4L,
                completedAtEpochMillis = 9L,
            ),
        )
        assertFalse(DebugProbeId.SEARCH_QUERY in completed.running)
        assertEquals(1, completed.passedCount)
        assertEquals("4", completed.results.getValue(DebugProbeId.SEARCH_QUERY).safeFacts.getValue("count"))
        assertTrue(
            completed.results.getValue(DebugProbeId.SEARCH_QUERY)
                .safeFacts.getValue("reason")
                .matches(Regex("<fingerprint:[0-9a-f]{16}>")),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `completed result rejects running state`() {
        DebugProbeReportPolicy.complete(
            DebugProbeReport.empty(),
            DebugProbeResult(
                id = DebugProbeId.RECORDER_HEALTH,
                status = DebugProbeStatus.RUNNING,
            ),
        )
    }
}
