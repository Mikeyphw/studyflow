package com.mikeyphw.studyflow.tv.control

import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvControlAction
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvControlCommand
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvControlOrigin
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvControlSession
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvPairingState
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvPlaybackState
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvSurface
import com.mikeyphw.studyflow.tv.navigation.StudyFlowTvDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowTvControlStateReducerTest {
    private val pairedSession = StudyFlowTvControlSession.paired(
        sessionId = "paired-session-1",
        pairedDeviceName = "Study phone",
        pairedAtEpochMillis = 1_000L,
        expiresAtEpochMillis = 100_000L,
    )

    @Test
    fun localRemoteAndPairedPhoneDriveTheSameReducerState() {
        val initial = StudyFlowTvControlStateReducer.defaultState(pairedSession)
        val command = command(StudyFlowTvSurface.STUDY_SESSION, StudyFlowTvControlAction.START, "session", 2_000L)

        val local = StudyFlowTvControlStateReducer.reduce(initial, command, StudyFlowTvControlOrigin.LOCAL_REMOTE, 2_001L)
        val phone = StudyFlowTvControlStateReducer.reduce(initial, command, StudyFlowTvControlOrigin.PAIRED_PHONE, 2_001L)

        assertTrue(local.applied)
        assertTrue(phone.applied)
        assertEquals(local.state, phone.state)
        val continuity = StudyFlowTvControlStateReducer.continuityState(local.state)
        assertEquals("session", continuity.currentDestination)
        assertEquals(StudyFlowTvPlaybackState.RUNNING, continuity.playbackState)
        assertEquals(1L, continuity.commandRevision)
        assertEquals("warm_up", continuity.activeItemId)
    }

    @Test
    fun openCommandsCoverEveryImplementedTvDestination() {
        var state = StudyFlowTvControlStateReducer.defaultState(pairedSession)
        StudyFlowTvDestination.entries.forEachIndexed { index, destination ->
            val reduction = StudyFlowTvControlStateReducer.reduce(
                state,
                command(destination.surface, StudyFlowTvControlAction.OPEN, destination.route, 3_000L + index),
                StudyFlowTvControlOrigin.PAIRED_PHONE,
                3_000L + index,
            )
            assertTrue(reduction.applied)
            assertEquals(destination, reduction.state.destination)
            state = reduction.state
        }
        assertEquals(StudyFlowTvDestination.entries.size.toLong(), state.commandRevision)
    }

    @Test
    fun surfaceCommandsPreserveActiveQuestionSectionReviewScheduleAndLibraryState() {
        var state = StudyFlowTvControlStateReducer.defaultState(pairedSession)
        fun apply(surface: StudyFlowTvSurface, action: StudyFlowTvControlAction, target: String? = null, option: String? = null) {
            val revision = state.commandRevision + 1
            val reduction = StudyFlowTvControlStateReducer.reduce(
                state,
                StudyFlowTvControlCommand(
                    commandId = "cmd-$revision",
                    surface = surface,
                    action = action,
                    targetId = target,
                    optionId = option,
                    issuedAtEpochMillis = 10_000L + revision,
                ),
                StudyFlowTvControlOrigin.PAIRED_PHONE,
                10_000L + revision,
            )
            assertTrue(reduction.applied)
            state = reduction.state
        }

        apply(StudyFlowTvSurface.QUESTION_SET, StudyFlowTvControlAction.SELECT_ANSWER, option = "B")
        apply(StudyFlowTvSurface.QUESTION_SET, StudyFlowTvControlAction.SHOW_EXPLANATION)
        assertTrue(state.questionPractice.explanationVisible)
        apply(StudyFlowTvSurface.QUESTION_SET, StudyFlowTvControlAction.NEXT)
        assertEquals(1, state.questionPractice.activeQuestionIndex)

        apply(StudyFlowTvSurface.MOCK_EXAM, StudyFlowTvControlAction.START)
        apply(StudyFlowTvSurface.MOCK_EXAM, StudyFlowTvControlAction.NEXT)
        assertEquals(1, state.mockExam.activeSectionIndex)

        apply(StudyFlowTvSurface.REVIEW, StudyFlowTvControlAction.OPEN, target = "administrative_law_errors")
        assertEquals(1, state.review.activeReviewIndex)
        apply(StudyFlowTvSurface.REVIEW, StudyFlowTvControlAction.SHOW_EXPLANATION)
        assertTrue(state.review.explanationVisible)

        apply(StudyFlowTvSurface.SCHEDULE, StudyFlowTvControlAction.NEXT)
        assertEquals(1, state.schedule.activeItemIndex)
        apply(StudyFlowTvSurface.LIBRARY, StudyFlowTvControlAction.NEXT)
        assertEquals(1, state.library.activeReferenceIndex)

        val continuity = StudyFlowTvControlStateReducer.continuityState(state)
        assertEquals(StudyFlowTvSurface.LIBRARY, continuity.currentSurface)
        assertEquals(state.library.activeReference.id.name.lowercase(), continuity.activeItemId)
    }

    @Test
    fun duplicateStaleExpiredAndRevokedPhoneCommandsFailClosed() {
        val initial = StudyFlowTvControlStateReducer.defaultState(pairedSession)
        val firstCommand = command(StudyFlowTvSurface.HOME, StudyFlowTvControlAction.OPEN, "home", 20_000L)
        val first = StudyFlowTvControlStateReducer.reduce(initial, firstCommand, StudyFlowTvControlOrigin.PAIRED_PHONE, 20_000L)
        assertTrue(first.applied)

        val duplicate = StudyFlowTvControlStateReducer.reduce(first.state, firstCommand, StudyFlowTvControlOrigin.PAIRED_PHONE, 20_001L)
        assertFalse(duplicate.applied)
        assertEquals("duplicate-command", duplicate.reason)

        val stale = StudyFlowTvControlStateReducer.reduce(
            first.state,
            command(StudyFlowTvSurface.TODAY, StudyFlowTvControlAction.OPEN, "today", 19_999L),
            StudyFlowTvControlOrigin.PAIRED_PHONE,
            20_002L,
        )
        assertFalse(stale.applied)
        assertEquals("stale-command", stale.reason)

        val expired = StudyFlowTvControlStateReducer.reduce(
            first.state,
            command(StudyFlowTvSurface.TODAY, StudyFlowTvControlAction.OPEN, "today", 100_001L),
            StudyFlowTvControlOrigin.PAIRED_PHONE,
            100_001L,
        )
        assertFalse(expired.applied)
        assertEquals("expired-pairing", expired.reason)
        assertEquals(StudyFlowTvPairingState.REVOKED, expired.state.session.pairingState)
    }

    @Test
    fun settingsCanRevokePairingThroughTheSameSharedCommandLayer() {
        val initial = StudyFlowTvControlStateReducer.defaultState(pairedSession)
        val reduction = StudyFlowTvControlStateReducer.reduce(
            initial,
            command(StudyFlowTvSurface.SETTINGS, StudyFlowTvControlAction.REVOKE_PAIRING, "revoke-pairing", 30_000L),
            StudyFlowTvControlOrigin.LOCAL_REMOTE,
            30_000L,
        )

        assertTrue(reduction.applied)
        assertEquals(StudyFlowTvDestination.SETTINGS, reduction.state.destination)
        assertEquals(StudyFlowTvPairingState.REVOKED, reduction.state.session.pairingState)
        assertEquals(StudyFlowTvPairingState.REVOKED, reduction.state.settings.pairingState)
    }

    private fun command(
        surface: StudyFlowTvSurface,
        action: StudyFlowTvControlAction,
        target: String? = null,
        issuedAt: Long,
    ): StudyFlowTvControlCommand = StudyFlowTvControlCommand(
        commandId = "${surface.name}-${action.name}-$issuedAt",
        surface = surface,
        action = action,
        targetId = target,
        issuedAtEpochMillis = issuedAt,
    )

    @Test
    fun stopCastIsAcceptedAsARevisionedPresentationLifecycleCommand() {
        val session = StudyFlowTvControlSession.paired(
            sessionId = "session-stop",
            pairedDeviceName = "Phone",
            pairedAtEpochMillis = 100L,
            expiresAtEpochMillis = 10_000L,
        )
        val state = StudyFlowTvControlStateReducer.defaultState(session)
        val command = StudyFlowTvControlCommand(
            commandId = "stop-cast",
            surface = StudyFlowTvSurface.HOME,
            action = StudyFlowTvControlAction.STOP_CAST,
            issuedAtEpochMillis = 500L,
        )

        val result = StudyFlowTvControlStateReducer.reduce(
            state = state,
            command = command,
            origin = StudyFlowTvControlOrigin.PAIRED_PHONE,
            nowEpochMillis = 500L,
        )

        assertTrue(result.applied)
        assertEquals(1L, result.state.commandRevision)
        assertEquals("stop-cast", result.state.lastCommandId)
    }

}
