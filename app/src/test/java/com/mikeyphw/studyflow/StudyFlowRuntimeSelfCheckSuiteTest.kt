package com.mikeyphw.studyflow

import com.mikeyphw.studyflow.core.diagnostics.DebugProbeCatalog
import com.mikeyphw.studyflow.core.diagnostics.DebugProbeId
import com.mikeyphw.studyflow.core.diagnostics.DebugProbeReport
import com.mikeyphw.studyflow.core.diagnostics.DebugProbeResult
import com.mikeyphw.studyflow.core.diagnostics.DebugProbeStatus
import com.mikeyphw.studyflow.core.diagnostics.DiagnosticEvent
import com.mikeyphw.studyflow.core.diagnostics.DiagnosticOutcome
import com.mikeyphw.studyflow.core.diagnostics.DiagnosticRecorder
import java.util.concurrent.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowRuntimeSelfCheckSuiteTest {
    @Test
    fun `safe suite runs read only catalog records history and terminal events`() = runBlocking {
        val recorder = RecordingRecorder()
        val observed = mutableListOf<DebugProbeId>()
        val suite = StudyFlowRuntimeSelfCheckSuite(
            runProbe = { id ->
                observed += id
                DebugProbeResult(id, DebugProbeStatus.PASSED)
            },
            recorder = recorder,
            clock = tickingClock(),
        )

        val (_, run) = suite.run(DebugProbeReport.empty())

        assertEquals(DebugProbeCatalog.safeSuiteIds, observed)
        assertEquals(DebugProbeCatalog.safeSuiteIds, run.attemptedProbeIds)
        assertTrue(run.successful)
        assertEquals(DebugProbeCatalog.safeSuiteIds.size, run.passedCount)
        assertEquals(
            listOf(DiagnosticOutcome.STARTED, DiagnosticOutcome.SUCCEEDED),
            recorder.events.map(DiagnosticEvent::outcome),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `suite rejects side effect probes`() {
        StudyFlowRuntimeSelfCheckSuite(
            probeIds = listOf(DebugProbeId.WIDGET_REFRESH),
            runProbe = { id -> DebugProbeResult(id, DebugProbeStatus.PASSED) },
            recorder = RecordingRecorder(),
        )
    }

    @Test(expected = CancellationException::class)
    fun `suite cancellation is recorded and rethrown`() {
        runBlocking {
            val recorder = RecordingRecorder()
            val suite = StudyFlowRuntimeSelfCheckSuite(
                probeIds = listOf(DebugProbeId.RECORDER_HEALTH),
                runProbe = { throw CancellationException("cancel") },
                recorder = recorder,
            )
            try {
                suite.run(DebugProbeReport.empty())
            } finally {
                assertEquals(
                    listOf(DiagnosticOutcome.STARTED, DiagnosticOutcome.CANCELLED),
                    recorder.events.map(DiagnosticEvent::outcome),
                )
            }
        }
    }

    @Test(expected = AssertionError::class)
    fun `suite fatal errors are never converted into ordinary failure`() {
        runBlocking {
            val suite = StudyFlowRuntimeSelfCheckSuite(
                probeIds = listOf(DebugProbeId.RECORDER_HEALTH),
                runProbe = { throw AssertionError("fatal") },
                recorder = RecordingRecorder(),
            )
            suite.run(DebugProbeReport.empty())
        }
    }

    private fun tickingClock(): () -> Long {
        var value = 100L
        return { value++ }
    }

    private class RecordingRecorder : DiagnosticRecorder {
        override val enabled: Boolean = true
        val events = mutableListOf<DiagnosticEvent>()

        override fun record(event: DiagnosticEvent) {
            events += event.sanitized()
        }
    }
}
