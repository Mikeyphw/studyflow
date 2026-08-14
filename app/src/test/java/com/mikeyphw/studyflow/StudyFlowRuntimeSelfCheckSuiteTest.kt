package com.mikeyphw.studyflow

import com.mikeyphw.studyflow.core.diagnostics.DebugProbeCatalog
import com.mikeyphw.studyflow.core.diagnostics.DebugProbeId
import com.mikeyphw.studyflow.core.diagnostics.DebugProbeReport
import com.mikeyphw.studyflow.core.diagnostics.DebugProbeResult
import com.mikeyphw.studyflow.core.diagnostics.DebugProbeStatus
import com.mikeyphw.studyflow.core.diagnostics.DiagnosticEvent
import com.mikeyphw.studyflow.core.diagnostics.DiagnosticOutcome
import com.mikeyphw.studyflow.core.diagnostics.DiagnosticRecorder
import com.mikeyphw.studyflow.core.diagnostics.RuntimeSelfCheckVerdict
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
                passingResult(id)
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

    @Test
    fun `suite cancellation preserves partial terminal run`() = runBlocking {
        val suite = StudyFlowRuntimeSelfCheckSuite(
            probeIds = listOf(DebugProbeId.RECORDER_HEALTH, DebugProbeId.NOTIFICATION_HEALTH),
            runProbe = { id ->
                if (id == DebugProbeId.NOTIFICATION_HEALTH) throw CancellationException("cancel")
                passingResult(id)
            },
            recorder = RecordingRecorder(),
            clock = tickingClock(),
        )

        try {
            suite.run(DebugProbeReport.empty())
            throw AssertionError("Expected cancellation")
        } catch (cancelled: RuntimeSelfCheckCancellation) {
            assertEquals(RuntimeSelfCheckVerdict.CANCELLED, cancelled.run.verdict)
            assertEquals(2, cancelled.run.attemptedProbeIds.size)
            assertEquals(DebugProbeStatus.PASSED, cancelled.run.results.getValue(DebugProbeId.RECORDER_HEALTH).status)
            assertEquals(DebugProbeStatus.CANCELLED, cancelled.run.results.getValue(DebugProbeId
                .NOTIFICATION_HEALTH).status)
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

    private fun passingResult(id: DebugProbeId): DebugProbeResult {
        val facts = when (id) {
            DebugProbeId.SEARCH_QUERY,
            DebugProbeId.RECOMMENDATION_QUERY,
            DebugProbeId.SYNC_FOUNDATION,
            DebugProbeId.WIDGET_REFRESH,
            -> mapOf("behavior_verified" to "true")
            DebugProbeId.BACKUP_CONFIGURATION -> mapOf("enabled" to "false", "expected_enabled" to "false")
            DebugProbeId.FILE_PROVIDER_CONFIGURATION -> mapOf(
                "allowed_root" to "true",
                "private_root_rejected" to "true",
            )
            else -> emptyMap()
        }
        return DebugProbeResult(id, DebugProbeStatus.PASSED, safeFacts = facts)
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
