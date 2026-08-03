package com.mikeyphw.studyflow

import com.mikeyphw.studyflow.core.diagnostics.DebugProbeCatalog
import com.mikeyphw.studyflow.core.diagnostics.DebugProbeDescriptor
import com.mikeyphw.studyflow.core.diagnostics.DebugProbeId
import com.mikeyphw.studyflow.core.diagnostics.DebugProbeMessage
import com.mikeyphw.studyflow.core.diagnostics.DebugProbeResult
import com.mikeyphw.studyflow.core.diagnostics.DebugProbeStatus
import com.mikeyphw.studyflow.core.diagnostics.DiagnosticEvent
import com.mikeyphw.studyflow.core.diagnostics.DiagnosticOutcome
import com.mikeyphw.studyflow.core.diagnostics.DiagnosticRecorder
import com.mikeyphw.studyflow.core.model.StudyPlanId
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowDebugProbeRegistryTest {
    @Test
    fun `blocked probe records start and terminal events without executing operation`() = runBlocking {
        val recorder = RecordingRecorder()
        val calls = AtomicInteger()
        val registry = registry(
            recorder = recorder,
            operations = StudyFlowDebugProbeOperations { _, _ ->
                calls.incrementAndGet()
                DebugProbeResult(DebugProbeId.BACKUP_DRY_RUN, DebugProbeStatus.PASSED)
            },
            defaultPlanId = { null },
        )

        val result = registry.run(DebugProbeId.BACKUP_DRY_RUN)

        assertEquals(DebugProbeStatus.BLOCKED, result.status)
        assertEquals(DebugProbeMessage.MISSING_DEFAULT_PLAN, result.message)
        assertEquals(0, calls.get())
        assertEquals(
            listOf(DiagnosticOutcome.STARTED, DiagnosticOutcome.BLOCKED),
            recorder.events.map(DiagnosticEvent::outcome),
        )
    }

    @Test
    fun `cooperative operation timeout returns explicit failure and terminal event`() = runBlocking {
        val recorder = RecordingRecorder()
        val registry = registry(
            recorder = recorder,
            operations = StudyFlowDebugProbeOperations { id, _ ->
                delay(200L)
                DebugProbeResult(id, DebugProbeStatus.PASSED)
            },
            descriptorProvider = { id -> descriptor(id, timeoutMillis = 25L) },
        )

        val result = registry.run(DebugProbeId.SEARCH_QUERY)

        assertEquals(DebugProbeStatus.FAILED, result.status)
        assertEquals(DebugProbeMessage.PROBE_TIMED_OUT, result.message)
        assertEquals(DiagnosticOutcome.FAILED, recorder.events.last().outcome)
    }

    @Test
    fun `interruptible blocking operation obeys probe timeout`() = runBlocking {
        val recorder = RecordingRecorder()
        val registry = registry(
            recorder = recorder,
            operations = StudyFlowDebugProbeOperations { id, _ ->
                runInterruptible { Thread.sleep(10_000L) }
                DebugProbeResult(id, DebugProbeStatus.PASSED)
            },
            descriptorProvider = { id -> descriptor(id, timeoutMillis = 25L) },
        )

        val started = System.nanoTime()
        val result = registry.run(DebugProbeId.WIDGET_HEALTH)
        val elapsedMillis = (System.nanoTime() - started) / 1_000_000L

        assertEquals(DebugProbeStatus.FAILED, result.status)
        assertEquals(DebugProbeMessage.PROBE_TIMED_OUT, result.message)
        assertTrue(elapsedMillis < 1_000L)
        assertEquals(DiagnosticOutcome.FAILED, recorder.events.last().outcome)
    }

    @Test
    fun `external cancellation is recorded and propagated`() = runBlocking {
        val recorder = RecordingRecorder()
        val registry = registry(
            recorder = recorder,
            operations = StudyFlowDebugProbeOperations { id, _ ->
                delay(10_000L)
                DebugProbeResult(id, DebugProbeStatus.PASSED)
            },
        )

        val job = launch { registry.run(DebugProbeId.SEARCH_QUERY) }
        delay(25L)
        job.cancelAndJoin()

        assertTrue(job.isCancelled)
        assertEquals(
            listOf(DiagnosticOutcome.STARTED, DiagnosticOutcome.CANCELLED),
            recorder.events.map(DiagnosticEvent::outcome),
        )
    }

    @Test
    fun `recoverable exception becomes failed probe`() = runBlocking {
        val recorder = RecordingRecorder()
        val registry = registry(
            recorder = recorder,
            operations = StudyFlowDebugProbeOperations { _, _ ->
                throw IllegalStateException("private plan title")
            },
        )

        val result = registry.run(DebugProbeId.SEARCH_QUERY)

        assertEquals(DebugProbeStatus.FAILED, result.status)
        assertTrue(result.exception != null)
        assertFalse(result.exception?.fingerprint.orEmpty().contains("private"))
        assertEquals(DiagnosticOutcome.FAILED, recorder.events.last().outcome)
    }

    @Test(expected = AssertionError::class)
    fun `fatal errors are never converted into probe failures`(): Unit {
        runBlocking {
            val registry = registry(
                recorder = RecordingRecorder(),
                operations = StudyFlowDebugProbeOperations { _, _ ->
                    throw AssertionError("fatal")
                },
            )
            registry.run(DebugProbeId.SEARCH_QUERY)
        }
    }

    @Test
    fun `operation receives the selected default plan`() = runBlocking {
        val observed = mutableListOf<StudyPlanId?>()
        val planId = StudyPlanId("plan-1")
        val registry = registry(
            recorder = RecordingRecorder(),
            operations = StudyFlowDebugProbeOperations { id, selectedPlanId ->
                observed += selectedPlanId
                DebugProbeResult(id, DebugProbeStatus.PASSED)
            },
            defaultPlanId = { planId },
        )

        val result = registry.run(DebugProbeId.RECOMMENDATION_QUERY)

        assertEquals(DebugProbeStatus.PASSED, result.status)
        assertEquals(listOf(planId), observed)
    }

    private fun registry(
        recorder: DiagnosticRecorder,
        operations: StudyFlowDebugProbeOperations,
        defaultPlanId: () -> StudyPlanId? = { StudyPlanId("default") },
        descriptorProvider: (DebugProbeId) -> DebugProbeDescriptor = DebugProbeCatalog::descriptor,
    ): StudyFlowDebugProbeRegistry {
        val elapsed = AtomicLong(100L)
        return StudyFlowDebugProbeRegistry(
            operations = operations,
            recorder = recorder,
            defaultPlanId = defaultPlanId,
            executionContext = Dispatchers.Default,
            clock = { 1_000L },
            elapsedRealtime = { elapsed.getAndAdd(5L) },
            descriptorProvider = descriptorProvider,
        )
    }

    private fun descriptor(id: DebugProbeId, timeoutMillis: Long): DebugProbeDescriptor {
        val original = DebugProbeCatalog.descriptor(id)
        return original.copy(timeoutMillis = timeoutMillis)
    }

    private class RecordingRecorder : DiagnosticRecorder {
        override val enabled: Boolean = true
        val events = mutableListOf<DiagnosticEvent>()

        override fun record(event: DiagnosticEvent) {
            events += event.sanitized()
        }
    }
}
