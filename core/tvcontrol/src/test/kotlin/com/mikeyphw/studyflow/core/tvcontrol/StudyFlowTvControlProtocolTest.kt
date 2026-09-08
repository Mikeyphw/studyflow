package com.mikeyphw.studyflow.core.tvcontrol

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowTvControlProtocolTest {
    private val json = Json { ignoreUnknownKeys = false }

    @Test
    fun pairingTokenRequiresRevocableShortLivedExplicitPairing() {
        val token = StudyFlowTvPairingToken(
            tvDeviceName = "Living Room TV",
            pairingCode = "123456",
            sessionNonce = "nonce-1234567890",
            issuedAtEpochMillis = 1_000L,
            expiresAtEpochMillis = 121_000L,
        )

        assertEquals(1, StudyFlowTvControlProtocol.version)
        assertEquals(6, StudyFlowTvControlProtocol.pairingCodeLength)
        assertEquals(120, StudyFlowTvControlProtocol.pairingCodeTtlSeconds)
        assertTrue(StudyFlowTvControlProtocol.requiresRevocablePairing)
        assertEquals("123456", token.pairingCode)
    }

    @Test
    fun controlCommandRoundTripsAsIntentNotDatabaseRows() {
        val command = StudyFlowTvControlCommand(
            commandId = "cmd-1",
            surface = StudyFlowTvSurface.QUESTION_SET,
            action = StudyFlowTvControlAction.SELECT_ANSWER,
            targetId = "question-42",
            optionId = "B",
            issuedAtEpochMillis = 2_000L,
        )

        val encoded = json.encodeToString(command)
        val decoded = json.decodeFromString<StudyFlowTvControlCommand>(encoded)

        assertEquals(command, decoded)
        assertTrue(command.requiresUserPresence)
        assertFalse(encoded.contains("Room"))
        assertFalse(encoded.contains("Dao"))
        assertFalse(encoded.contains("database"))
    }

    @Test
    fun castStateAllowsLearningSurfacesButRejectsSettings() {
        assertTrue(StudyFlowTvControlProtocol.isCastable(StudyFlowTvSurface.STUDY_SESSION))
        assertTrue(StudyFlowTvControlProtocol.isCastable(StudyFlowTvSurface.MOCK_EXAM))
        assertFalse(StudyFlowTvControlProtocol.isCastable(StudyFlowTvSurface.SETTINGS))

        val castState = StudyFlowTvCastState(
            castId = "cast-1",
            surface = StudyFlowTvSurface.PROGRESS,
            title = "Weekly progress",
            progressPercent = 72,
            updatedAtEpochMillis = 3_000L,
        )

        assertEquals(castState, json.decodeFromString<StudyFlowTvCastState>(json.encodeToString(castState)))
    }

    @Test
    fun practiceAndMockExamActionsRoundTripThroughSharedControlProtocol() {
        val practiceCommand = StudyFlowTvControlCommand(
            commandId = "cmd-practice-explain",
            surface = StudyFlowTvSurface.QUESTION_SET,
            action = StudyFlowTvControlAction.SHOW_EXPLANATION,
            targetId = "question_one",
            issuedAtEpochMillis = 4_000L,
        )
        val mockCommand = StudyFlowTvControlCommand(
            commandId = "cmd-mock-submit",
            surface = StudyFlowTvSurface.MOCK_EXAM,
            action = StudyFlowTvControlAction.SUBMIT,
            targetId = "specific_knowledge",
            issuedAtEpochMillis = 5_000L,
        )

        assertEquals(practiceCommand, json.decodeFromString<StudyFlowTvControlCommand>(json.encodeToString(practiceCommand)))
        assertEquals(mockCommand, json.decodeFromString<StudyFlowTvControlCommand>(json.encodeToString(mockCommand)))
        assertTrue(StudyFlowTvControlProtocol.isCastable(StudyFlowTvSurface.QUESTION_SET))
        assertTrue(StudyFlowTvControlProtocol.isCastable(StudyFlowTvSurface.MOCK_EXAM))
    }


    @Test
    fun scheduleAndLibraryCastStatesArePresentationOnly() {
        assertTrue(StudyFlowTvControlProtocol.isCastable(StudyFlowTvSurface.SCHEDULE))
        assertTrue(StudyFlowTvControlProtocol.isCastable(StudyFlowTvSurface.LIBRARY))

        val libraryCast = StudyFlowTvCastState(
            castId = "cast-library-1",
            surface = StudyFlowTvSurface.LIBRARY,
            title = "Reference card",
            subtitle = "Pinned excerpt",
            progressPercent = 50,
            activeItemId = "constitution_article_five",
            updatedAtEpochMillis = 6_000L,
        )

        val encoded = json.encodeToString(libraryCast)
        assertEquals(libraryCast, json.decodeFromString<StudyFlowTvCastState>(encoded))
        assertFalse(encoded.contains("file://"))
        assertFalse(encoded.contains("Room"))
        assertFalse(encoded.contains("Dao"))
    }
}
