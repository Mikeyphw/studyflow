package com.mikeyphw.studyflow

import com.mikeyphw.studyflow.core.diagnostics.DebugLabStatus
import com.mikeyphw.studyflow.core.diagnostics.DiagnosticEvent
import com.mikeyphw.studyflow.core.diagnostics.DiagnosticOutcome
import com.mikeyphw.studyflow.core.diagnostics.DiagnosticRecorder
import com.mikeyphw.studyflow.core.diagnostics.StudyEngineDiagnostic
import com.mikeyphw.studyflow.core.diagnostics.StudyEngineDiagnosticKind
import com.mikeyphw.studyflow.core.diagnostics.StudyEngineLabActionResult
import com.mikeyphw.studyflow.core.diagnostics.StudyEngineLabMessage
import com.mikeyphw.studyflow.core.diagnostics.StudyEngineLabReport
import com.mikeyphw.studyflow.core.model.StudyPlanId
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class StudyFlowStudyEngineLabTest {
    @Test
    fun `inspection blocks before operation without default plan`() = runBlocking {
        var called = false
        val recorder = RecordingRecorder()
        val lab = lab(
            operation = StudyEngineInspectionOperation {
                called = true
                passedReport()
            },
            defaultPlanId = { null },
            recorder = recorder,
        )

        val (report, action) = lab.inspect()

        assertEquals(DebugLabStatus.BLOCKED, report.status)
        assertEquals(StudyEngineLabMessage.MISSING_DEFAULT_PLAN, report.message)
        assertEquals(StudyEngineLabActionResult.MISSING_DEFAULT_PLAN, action)
        assertFalse(called)
        assertEquals(listOf(DiagnosticOutcome.STARTED, DiagnosticOutcome.BLOCKED), recorder.outcomes())
    }

    @Test
    fun `inspection forwards exact default plan and records event pair`() = runBlocking {
        val expectedPlan = StudyPlanId("plan-42")
        var received: StudyPlanId? = null
        val recorder = RecordingRecorder()
        val lab = lab(
            operation = StudyEngineInspectionOperation { planId ->
                received = planId
                passedReport()
            },
            defaultPlanId = { expectedPlan },
            recorder = recorder,
        )

        val (report, action) = lab.inspect()

        assertEquals(expectedPlan, received)
        assertEquals(DebugLabStatus.PASSED, report.status)
        assertEquals(StudyEngineLabActionResult.INSPECTED, action)
        assertEquals(listOf(DiagnosticOutcome.STARTED, DiagnosticOutcome.SUCCEEDED), recorder.outcomes())
    }

    @Test
    fun `recoverable inspection error becomes failed action`() = runBlocking {
        val lab = lab(
            operation = StudyEngineInspectionOperation {
                throw IllegalStateException("private study text")
            },
        )

        val (report, action) = lab.inspect()

        assertEquals(DebugLabStatus.FAILED, report.status)
        assertEquals(StudyEngineLabMessage.INSPECTION_FAILED, report.message)
        assertEquals(StudyEngineLabActionResult.FAILED, action)
    }

    @Test(expected = CancellationException::class)
    fun `cancellation is never converted into failed action`() {
        runBlocking {
            val lab = lab(
                operation = StudyEngineInspectionOperation { throw CancellationException("cancel") },
            )
            lab.inspect()
            Unit
        }
    }

    @Test(expected = AssertionError::class)
    fun `fatal errors are never converted into failed action`() {
        runBlocking {
            val lab = lab(
                operation = StudyEngineInspectionOperation { throw AssertionError("fatal") },
            )
            lab.inspect()
            Unit
        }
    }

    private fun lab(
        operation: StudyEngineInspectionOperation = StudyEngineInspectionOperation { passedReport() },
        defaultPlanId: () -> StudyPlanId? = { StudyPlanId("plan-1") },
        recorder: DiagnosticRecorder = RecordingRecorder(),
    ): StudyFlowStudyEngineLab = StudyFlowStudyEngineLab(
        operation = operation,
        defaultPlanId = defaultPlanId,
        recorder = recorder,
        executionContext = Dispatchers.Unconfined,
        clock = { 1_000L },
    )

    private class RecordingRecorder : DiagnosticRecorder {
        override val enabled: Boolean = true
        private val events = mutableListOf<DiagnosticEvent>()

        override fun record(event: DiagnosticEvent) {
            events += event.sanitized()
        }

        fun outcomes(): List<DiagnosticOutcome> = events.map(DiagnosticEvent::outcome)
    }
}

private fun passedReport(): StudyEngineLabReport = StudyEngineLabReport(
    status = DebugLabStatus.PASSED,
    inspectedAtEpochMillis = 1_000L,
    diagnostics = listOf(
        StudyEngineDiagnostic(
            kind = StudyEngineDiagnosticKind.SCHEDULE_EXPANSION,
            status = DebugLabStatus.PASSED,
            primaryCount = 1,
            secondaryCount = 1,
        ),
    ),
)
