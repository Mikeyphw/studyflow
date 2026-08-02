package com.mikeyphw.studyflow

import com.mikeyphw.studyflow.core.diagnostics.DatabaseHealthLabReport
import com.mikeyphw.studyflow.core.diagnostics.DatabaseHealthMessage
import com.mikeyphw.studyflow.core.diagnostics.DatabasePortabilityLabActionResult
import com.mikeyphw.studyflow.core.diagnostics.DatabasePortabilityLabReport
import com.mikeyphw.studyflow.core.diagnostics.DebugLabStatus
import com.mikeyphw.studyflow.core.diagnostics.DiagnosticEvent
import com.mikeyphw.studyflow.core.diagnostics.DiagnosticOutcome
import com.mikeyphw.studyflow.core.diagnostics.DiagnosticRecorder
import com.mikeyphw.studyflow.core.diagnostics.PortabilityLabMessage
import com.mikeyphw.studyflow.core.model.StudyPlanId
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowDatabasePortabilityLabTest {
    private val initial = DatabasePortabilityLabReport(
        database = DatabaseHealthLabReport(status = DebugLabStatus.NOT_RUN),
    )

    @Test
    fun `database inspection returns injected read only report and event pair`() = runBlocking {
        val expected = DatabaseHealthLabReport(
            status = DebugLabStatus.PASSED,
            schemaVersion = 2,
            expectedSchemaVersion = 2,
            quickCheckPassed = true,
            metadataPresent = true,
            migrationPathComplete = true,
        )
        val recorder = RecordingRecorder()
        val lab = lab(
            databaseOperation = DatabaseHealthOperation { expected },
            recorder = recorder,
        )

        val (report, action) = lab.inspectDatabase(initial)

        assertEquals(expected, report.database)
        assertEquals(DatabasePortabilityLabActionResult.DATABASE_INSPECTED, action)
        assertEquals(listOf(DiagnosticOutcome.STARTED, DiagnosticOutcome.SUCCEEDED), recorder.outcomes())
    }

    @Test
    fun `portability drills block before operation without default plan`() = runBlocking {
        var called = false
        val recorder = RecordingRecorder()
        val lab = lab(
            portabilityOperation = PortabilityDrillOperation {
                called = true
                initial
            },
            defaultPlanId = { null },
            recorder = recorder,
        )

        val (report, action) = lab.runPortabilityDrills(initial)

        assertEquals(DebugLabStatus.BLOCKED, report.portabilityStatus)
        assertEquals(PortabilityLabMessage.MISSING_DEFAULT_PLAN, report.portabilityMessage)
        assertEquals(DatabasePortabilityLabActionResult.MISSING_DEFAULT_PLAN, action)
        assertFalse(called)
        assertEquals(listOf(DiagnosticOutcome.STARTED, DiagnosticOutcome.BLOCKED), recorder.outcomes())
    }

    @Test
    fun `portability drills forward exact default plan`() = runBlocking {
        val expectedPlan = StudyPlanId("plan-2")
        var received: StudyPlanId? = null
        val expected = initial.copy(
            portabilityStatus = DebugLabStatus.PASSED,
            portabilityGeneratedAtEpochMillis = 10L,
        )
        val lab = lab(
            portabilityOperation = PortabilityDrillOperation { planId ->
                received = planId
                expected
            },
            defaultPlanId = { expectedPlan },
        )

        val (report, action) = lab.runPortabilityDrills(initial)

        assertEquals(expectedPlan, received)
        assertEquals(expected, report)
        assertEquals(DatabasePortabilityLabActionResult.PORTABILITY_DRILLS_COMPLETED, action)
    }

    @Test
    fun `recoverable database error becomes explicit failed action`() = runBlocking {
        val lab = lab(
            databaseOperation = DatabaseHealthOperation {
                throw IllegalStateException("private plan")
            },
        )

        val (report, action) = lab.inspectDatabase(initial)

        assertEquals(DebugLabStatus.FAILED, report.database.status)
        assertEquals(DatabaseHealthMessage.DATABASE_UNAVAILABLE, report.database.message)
        assertEquals(DatabasePortabilityLabActionResult.FAILED, action)
    }

    @Test
    fun `recoverable portability error becomes explicit failed action`() = runBlocking {
        val lab = lab(
            portabilityOperation = PortabilityDrillOperation {
                throw IllegalStateException("private plan")
            },
        )

        val (report, action) = lab.runPortabilityDrills(initial)

        assertEquals(DebugLabStatus.FAILED, report.portabilityStatus)
        assertEquals(PortabilityLabMessage.DRILL_FAILED, report.portabilityMessage)
        assertEquals(DatabasePortabilityLabActionResult.FAILED, action)
    }

    @Test(expected = CancellationException::class)
    fun `cancellation is never converted into failed action`() {
        runBlocking {
            val lab = lab(
                databaseOperation = DatabaseHealthOperation {
                    throw CancellationException("cancel")
                },
            )
            lab.inspectDatabase(initial)
            Unit
        }
    }

    @Test(expected = AssertionError::class)
    fun `fatal errors are never converted into failed action`() {
        runBlocking {
            val lab = lab(
                databaseOperation = DatabaseHealthOperation {
                    throw AssertionError("fatal")
                },
            )
            lab.inspectDatabase(initial)
            Unit
        }
    }

    private fun lab(
        databaseOperation: DatabaseHealthOperation = DatabaseHealthOperation {
            DatabaseHealthLabReport(status = DebugLabStatus.PASSED)
        },
        portabilityOperation: PortabilityDrillOperation = PortabilityDrillOperation { initial },
        defaultPlanId: () -> StudyPlanId? = { StudyPlanId("plan-1") },
        recorder: DiagnosticRecorder = RecordingRecorder(),
    ): StudyFlowDatabasePortabilityLab = StudyFlowDatabasePortabilityLab(
        databaseOperation = databaseOperation,
        portabilityOperation = portabilityOperation,
        defaultPlanId = defaultPlanId,
        recorder = recorder,
        executionContext = Dispatchers.Unconfined,
    )

    private class RecordingRecorder : DiagnosticRecorder {
        override val enabled: Boolean = true
        val events = mutableListOf<DiagnosticEvent>()

        override fun record(event: DiagnosticEvent) {
            events += event.sanitized()
        }

        fun outcomes(): List<DiagnosticOutcome> = events.map(DiagnosticEvent::outcome)
    }
}
