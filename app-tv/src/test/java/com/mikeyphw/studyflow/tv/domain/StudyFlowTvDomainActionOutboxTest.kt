package com.mikeyphw.studyflow.tv.domain

import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvControlAction
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvControlCommand
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvControlOrigin
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvDomainActionKind
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvDomainActionResult
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvPresentationAvailability
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvPresentationSnapshot
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvStudySessionPresentation
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowTvDomainActionOutboxTest {
    @Test
    fun outboxQueuesCanonicalRequestAndRemovesOnlyMatchingAcknowledgement() {
        val outbox = StudyFlowTvDomainActionOutbox()
        val command = StudyFlowTvControlCommand(
            commandId = "local-1",
            surface = StudyFlowTvSurface.STUDY_SESSION,
            action = StudyFlowTvControlAction.PAUSE,
            targetId = "session-1",
            issuedAtEpochMillis = 100L,
        )

        assertTrue(outbox.enqueue(command, snapshot(), StudyFlowTvControlOrigin.LOCAL_REMOTE))
        assertTrue(outbox.enqueue(command, snapshot(), StudyFlowTvControlOrigin.LOCAL_REMOTE))
        assertEquals(1, outbox.size())
        assertEquals(StudyFlowTvDomainActionKind.SESSION_PAUSE, outbox.peek()?.kind)
        assertFalse(outbox.acknowledge(StudyFlowTvDomainActionResult("other", true, completedAtEpochMillis = 200L)))
        assertEquals(1, outbox.size())
        assertTrue(outbox.acknowledge(StudyFlowTvDomainActionResult("local-1", true, completedAtEpochMillis = 200L)))
        assertNull(outbox.peek())
        assertEquals("local-1", outbox.lastResult()?.actionId)
    }

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
}
