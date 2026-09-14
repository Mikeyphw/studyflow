package com.mikeyphw.studyflow.tv.domain

import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvControlAction
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvControlCommand
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvControlOrigin
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvDomainActionProtocol
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvDomainActionResult
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvPresentationAvailability
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvPresentationSnapshot
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvStudySessionPresentation
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowTvDomainActionOutboxOv18DurabilityTest {
    @Test
    fun outboxRestoresPendingActionsAndAcknowledgementFromPersistence() {
        val persistence = MemoryPersistence()
        val first = StudyFlowTvDomainActionOutbox(persistence)
        assertTrue(first.enqueue(command("cmd-1"), snapshot(), StudyFlowTvControlOrigin.LOCAL_REMOTE))
        assertEquals(1, first.size())

        val restored = StudyFlowTvDomainActionOutbox(persistence)
        assertEquals("cmd-1", restored.peek()?.actionId)
        assertTrue(restored.acknowledge(StudyFlowTvDomainActionResult("cmd-1", true, completedAtEpochMillis = 200L)))

        val restoredAgain = StudyFlowTvDomainActionOutbox(persistence)
        assertNull(restoredAgain.peek())
        assertEquals("cmd-1", restoredAgain.lastResult()?.actionId)
    }

    @Test
    fun fullOutboxRejectsWithBackpressureInsteadOfDroppingOldest() {
        val outbox = StudyFlowTvDomainActionOutbox()
        repeat(StudyFlowTvDomainActionProtocol.maxPendingActions) { index ->
            assertTrue(outbox.enqueue(command("cmd-$index"), snapshot(), StudyFlowTvControlOrigin.LOCAL_REMOTE))
        }
        assertFalse(outbox.enqueue(command("cmd-overflow"), snapshot(), StudyFlowTvControlOrigin.LOCAL_REMOTE))
        assertEquals("domain-action-outbox-full", outbox.lastRejectionReason())
        assertEquals(StudyFlowTvDomainActionProtocol.maxPendingActions, outbox.size())
        assertNotNull(outbox.pendingActions().firstOrNull { it.actionId == "cmd-0" })
    }

    private fun command(id: String) = StudyFlowTvControlCommand(
        commandId = id,
        surface = StudyFlowTvSurface.STUDY_SESSION,
        action = StudyFlowTvControlAction.PAUSE,
        targetId = "session-1",
        issuedAtEpochMillis = id.filter(Char::isDigit).toLongOrNull() ?: 100L,
    )

    private fun snapshot() = StudyFlowTvPresentationSnapshot(
        snapshotId = "snapshot",
        studyPlanId = "plan-1",
        planTitle = "Plan",
        generatedAtEpochMillis = 100L,
        availability = StudyFlowTvPresentationAvailability.READY,
        studySession = StudyFlowTvStudySessionPresentation(
            sessionId = "session-1",
            title = "Session",
            subjectLabel = "Law",
            status = "RUNNING",
            elapsedMinutes = 1,
            targetMinutes = 30,
            questionsAnswered = 0,
            correctAnswers = 0,
        ),
    )

    private class MemoryPersistence : StudyFlowTvDomainActionOutboxPersistence {
        private var snapshot: StudyFlowTvDomainActionOutboxSnapshot? = null
        override fun load(): StudyFlowTvDomainActionOutboxSnapshot? = snapshot
        override fun save(snapshot: StudyFlowTvDomainActionOutboxSnapshot): Boolean {
            this.snapshot = snapshot
            return true
        }
    }
}
