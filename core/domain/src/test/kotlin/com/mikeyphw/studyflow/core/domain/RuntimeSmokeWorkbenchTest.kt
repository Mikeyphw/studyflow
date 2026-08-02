package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.RuntimeSmokeProbeKind
import com.mikeyphw.studyflow.core.model.RuntimeSmokeProbeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeSmokeWorkbenchTest {
    @Test
    fun initialReportIncludesEverySafeProbe() {
        val report = RuntimeSmokeWorkbench.initialReport(generatedAtEpochMillis = 1L)

        assertEquals(RuntimeSmokeProbeKind.values().toSet(), report.probes.map { it.kind }.toSet())
        assertTrue(report.probes.all { probe -> probe.status == RuntimeSmokeProbeStatus.NOT_RUN })
        assertTrue(report.probes.all { probe -> probe.safeForManualRun })
        assertFalse(report.readyForReleaseSeal)
    }

    @Test
    fun completedReportCanSatisfyDeviceSmokeGate() {
        val report = RuntimeSmokeWorkbench.completedReport(generatedAtEpochMillis = 2L)

        assertEquals(RuntimeSmokeProbeKind.values().size, report.passedCount)
        assertEquals(0, report.blockedCount)
        assertTrue(report.readyForReleaseSeal)
    }

    @Test
    fun blockedProbePreventsReleaseSeal() {
        val report = RuntimeSmokeWorkbench.markBlocked(
            RuntimeSmokeWorkbench.completedReport(generatedAtEpochMillis = 3L),
            RuntimeSmokeProbeKind.BACKUP_DRY_RUN,
            detail = "Dry-run failed.",
        )

        assertFalse(report.readyForReleaseSeal)
        assertEquals(1, report.blockedCount)
    }
}
