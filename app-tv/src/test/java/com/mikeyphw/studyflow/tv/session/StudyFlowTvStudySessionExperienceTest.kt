package com.mikeyphw.studyflow.tv.session

import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvControlAction
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvSurface
import com.mikeyphw.studyflow.tv.focus.StudyFlowTvFocusPolicy
import com.mikeyphw.studyflow.tv.focus.StudyFlowTvFocusRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowTvStudySessionExperienceTest {
    @Test
    fun studySessionOwnsRemoteAndPhoneControlledSessionSurface() {
        assertEquals("TVOV08", StudyFlowTvStudySessionExperience.overlay)
        assertTrue(StudyFlowTvStudySessionExperience.ownsStudySessionSurface)
        assertTrue(StudyFlowTvStudySessionExperience.remoteCanStartPauseResumeAndComplete)
        assertTrue(StudyFlowTvStudySessionExperience.pairedPhoneCanStartPauseResumeAndComplete)
        assertTrue(StudyFlowTvStudySessionExperience.emitsCastStateWithoutMirroring)
        assertTrue(StudyFlowTvStudySessionExperience.usesSharedControlProtocol)
    }

    @Test
    fun defaultSessionContainsACompleteTvRunPlan() {
        val state = StudyFlowTvStudySessionExperience.defaultState()

        assertEquals(StudyFlowTvSessionStatus.READY, state.status)
        assertEquals(StudyFlowTvStudySessionExperience.segmentCount, state.segments.size)
        assertEquals(0, state.elapsedMinutes)
        assertEquals(0, state.progressPercent)
        assertEquals(StudyFlowTvSessionSegmentId.WARM_UP, state.activeSegment.id)
    }

    @Test
    fun sessionStateMachineStartsPausesResumesAdvancesAndCompletes() {
        val ready = StudyFlowTvStudySessionExperience.defaultState()
        val running = StudyFlowTvStudySessionExperience.applyAction(ready, StudyFlowTvSessionAction.START)
        val paused = StudyFlowTvStudySessionExperience.applyAction(running, StudyFlowTvSessionAction.PAUSE)
        val resumed = StudyFlowTvStudySessionExperience.applyAction(paused, StudyFlowTvSessionAction.RESUME)
        val advanced = StudyFlowTvStudySessionExperience.applyAction(resumed, StudyFlowTvSessionAction.NEXT_SEGMENT)
        val completed = StudyFlowTvStudySessionExperience.applyAction(advanced, StudyFlowTvSessionAction.COMPLETE)

        assertEquals(StudyFlowTvSessionStatus.RUNNING, running.status)
        assertEquals(StudyFlowTvSessionStatus.PAUSED, paused.status)
        assertEquals(StudyFlowTvSessionStatus.RUNNING, resumed.status)
        assertEquals(1, advanced.activeSegmentIndex)
        assertTrue(advanced.elapsedMinutes > ready.elapsedMinutes)
        assertEquals(StudyFlowTvSessionStatus.COMPLETED, completed.status)
        assertEquals(100, completed.progressPercent)
    }

    @Test
    fun sessionFocusCommandsAndCastStateUseSharedControlProtocol() {
        val state = StudyFlowTvStudySessionExperience.applyAction(
            StudyFlowTvStudySessionExperience.defaultState(),
            StudyFlowTvSessionAction.START,
        )
        val focusItems = StudyFlowTvStudySessionExperience.focusItems(state)
        val command = StudyFlowTvStudySessionExperience.commandForAction(
            action = StudyFlowTvSessionAction.PAUSE,
            state = state,
            commandId = "session-command-1",
            issuedAtEpochMillis = 2_000L,
        )
        val castState = StudyFlowTvStudySessionExperience.castStateFor(
            state = state,
            castId = "cast-session-1",
            updatedAtEpochMillis = 3_000L,
        )

        assertEquals("session-segment-warm_up", StudyFlowTvFocusPolicy.initialFocusId(focusItems))
        assertTrue(focusItems.any { it.role == StudyFlowTvFocusRole.PRIMARY_ACTION })
        assertEquals(StudyFlowTvSurface.STUDY_SESSION, command.surface)
        assertEquals(StudyFlowTvControlAction.PAUSE, command.action)
        assertEquals(state.sessionId, command.targetId)
        assertEquals(StudyFlowTvSurface.STUDY_SESSION, castState.surface)
        assertEquals(state.title, castState.title)
        assertEquals(state.progressPercent, castState.progressPercent ?: -1)
    }
}
