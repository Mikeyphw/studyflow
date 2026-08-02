package com.mikeyphw.studyflow

import com.mikeyphw.studyflow.core.diagnostics.DebugLabStatus
import com.mikeyphw.studyflow.core.diagnostics.ReminderLabActionResult
import com.mikeyphw.studyflow.core.diagnostics.ReminderLabMessage
import com.mikeyphw.studyflow.core.diagnostics.NoOpDiagnosticRecorder
import com.mikeyphw.studyflow.core.diagnostics.ReminderNotificationLabReport
import com.mikeyphw.studyflow.core.model.StudyPlanId
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class StudyFlowReminderNotificationLabTest {
    @Test
    fun `inspect forwards missing plan without inventing a plan`() = runBlocking {
        var observedPlan: StudyPlanId? = StudyPlanId("unexpected")
        val lab = lab(
            inspect = { planId ->
                observedPlan = planId
                ReminderNotificationLabReport(
                    status = DebugLabStatus.BLOCKED,
                    message = ReminderLabMessage.MISSING_DEFAULT_PLAN,
                )
            },
            defaultPlanId = { null },
        )

        val (report, action) = lab.inspect()

        assertEquals(null, observedPlan)
        assertEquals(DebugLabStatus.BLOCKED, report.status)
        assertEquals(ReminderLabActionResult.INSPECTED, action)
    }

    @Test
    fun `reconcile blocks without default plan`() = runBlocking {
        val calls = AtomicInteger()
        val lab = lab(
            reconcile = {
                calls.incrementAndGet()
                Unit
            },
            defaultPlanId = { null },
        )

        assertEquals(ReminderLabActionResult.MISSING_DEFAULT_PLAN, lab.reconcile())
        assertEquals(0, calls.get())
    }

    @Test
    fun `diagnostic notification reports posted and blocked states`() = runBlocking {
        val posted = lab(notification = { true })
        val blocked = lab(notification = { false })

        assertEquals(
            ReminderLabActionResult.DIAGNOSTIC_NOTIFICATION_POSTED,
            posted.postDiagnosticNotification(),
        )
        assertEquals(
            ReminderLabActionResult.DIAGNOSTIC_NOTIFICATION_BLOCKED,
            blocked.postDiagnosticNotification(),
        )
    }

    @Test(expected = CancellationException::class)
    fun `inspection cancellation is never converted into failure`() {
        runBlocking {
            val lab = lab(inspect = { throw CancellationException("cancel") })
            lab.inspect()
            Unit
        }
    }
    @Test(expected = AssertionError::class)
    fun `fatal errors are never converted into reminder lab failures`() {
        runBlocking {
            val lab = lab(inspect = { throw AssertionError("fatal") })
            lab.inspect()
            Unit
        }
    }


    private fun lab(
        inspect: suspend (StudyPlanId?) -> ReminderNotificationLabReport = {
            ReminderNotificationLabReport(status = DebugLabStatus.PASSED)
        },
        reconcile: suspend (StudyPlanId) -> Unit = {},
        notification: suspend () -> Boolean = { true },
        defaultPlanId: () -> StudyPlanId? = { StudyPlanId("default") },
    ): StudyFlowReminderNotificationLab = StudyFlowReminderNotificationLab(
        inspectOperation = ReminderLabInspectOperation(inspect),
        reconcileOperation = reconcile,
        notificationOperation = notification,
        defaultPlanId = defaultPlanId,
        recorder = NoOpDiagnosticRecorder,
        executionContext = Dispatchers.Unconfined,
    )
}
