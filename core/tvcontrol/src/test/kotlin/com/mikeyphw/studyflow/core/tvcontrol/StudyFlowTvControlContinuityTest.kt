package com.mikeyphw.studyflow.core.tvcontrol

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowTvControlContinuityTest {
    private val json = Json { ignoreUnknownKeys = false }

    @Test
    fun pairedSessionExpiresAndCanBeRevokedWithoutTransportState() {
        val session = StudyFlowTvControlSession.paired(
            sessionId = "session-1",
            pairedDeviceName = "Study phone",
            pairedAtEpochMillis = 1_000L,
            expiresAtEpochMillis = 61_000L,
        )

        assertTrue(session.acceptsPairedPhoneCommands(60_999L))
        assertFalse(session.acceptsPairedPhoneCommands(61_000L))
        val revoked = session.revoke(20_000L)
        assertEquals(StudyFlowTvPairingState.REVOKED, revoked.pairingState)
        assertEquals(20_000L, revoked.revokedAtEpochMillis)
        assertFalse(revoked.acceptsPairedPhoneCommands(20_001L))
    }

    @Test
    fun continuityStateRoundTripsWithRevisionAndActivePresentation() {
        val state = StudyFlowTvControlContinuityState(
            currentDestination = "session",
            currentSurface = StudyFlowTvSurface.STUDY_SESSION,
            activeSurfaceTitle = "Focused study",
            activeItemId = "focus_block",
            playbackState = StudyFlowTvPlaybackState.PAUSED,
            commandRevision = 8,
            lastCommandId = "cmd-8",
            lastCommandIssuedAtEpochMillis = 8_000L,
            updatedAtEpochMillis = 8_010L,
            pairingState = StudyFlowTvPairingState.PAIRED,
            sessionExpiresAtEpochMillis = 100_000L,
        )

        assertEquals(state, json.decodeFromString<StudyFlowTvControlContinuityState>(json.encodeToString(state)))
        assertEquals(8L, state.commandRevision)
        assertEquals("focus_block", state.activeItemId)
    }

    @Test
    fun revokePairingIntentIsPartOfSharedProtocol() {
        val command = StudyFlowTvControlCommand(
            commandId = "cmd-revoke",
            surface = StudyFlowTvSurface.SETTINGS,
            action = StudyFlowTvControlAction.REVOKE_PAIRING,
            targetId = "revoke-pairing",
            issuedAtEpochMillis = 9_000L,
        )

        assertEquals(command, json.decodeFromString<StudyFlowTvControlCommand>(json.encodeToString(command)))
        assertEquals(StudyFlowTvControlAction.REVOKE_PAIRING, command.action)
    }
}
