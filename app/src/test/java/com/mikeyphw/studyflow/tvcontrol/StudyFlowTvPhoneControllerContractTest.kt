package com.mikeyphw.studyflow.tvcontrol

import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvControlAction
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvReceiverEndpoint
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvSessionCredential
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowTvPhoneControllerContractTest {
    @Test
    fun controllerCoversEveryTvDestinationAndRequiredAction() {
        assertEquals(StudyFlowTvSurface.entries.toSet(), StudyFlowTvPhoneControllerContract.navigationSurfaces)
        assertTrue(StudyFlowTvPhoneControllerContract.requiredControlActions.containsAll(
            setOf(
                StudyFlowTvControlAction.OPEN,
                StudyFlowTvControlAction.START,
                StudyFlowTvControlAction.PAUSE,
                StudyFlowTvControlAction.RESUME,
                StudyFlowTvControlAction.SELECT_ANSWER,
                StudyFlowTvControlAction.SHOW_EXPLANATION,
                StudyFlowTvControlAction.SUBMIT,
                StudyFlowTvControlAction.REVIEW_RESULTS,
                StudyFlowTvControlAction.STOP_CAST,
                StudyFlowTvControlAction.REVOKE_PAIRING,
            ),
        ))
        assertTrue(!StudyFlowTvPhoneControllerContract.presentationCastSynchronizationDeferredToTvov24)
    }

    @Test
    fun commandFactoryPreservesSurfaceOptionAndUserPresence() {
        val command = StudyFlowTvPhoneCommandFactory.command(
            surface = StudyFlowTvSurface.QUESTION_SET,
            action = StudyFlowTvControlAction.SELECT_ANSWER,
            optionId = "2",
            nowEpochMillis = 1234L,
            commandId = "phone-test",
        )
        assertEquals("phone-test", command.commandId)
        assertEquals(StudyFlowTvSurface.QUESTION_SET, command.surface)
        assertEquals(StudyFlowTvControlAction.SELECT_ANSWER, command.action)
        assertEquals("2", command.optionId)
        assertTrue(command.requiresUserPresence)
    }

    @Test
    fun persistedCredentialSelectsMatchingRediscoveredTv() {
        val livingRoom = StudyFlowTvReceiverEndpoint("Living room TV", "192.168.1.20", 9123)
        val bedroom = StudyFlowTvReceiverEndpoint("Bedroom TV", "192.168.1.21", 9124)
        val credential = StudyFlowTvSessionCredential(
            sessionId = "session",
            sessionSecret = "0123456789abcdef0123456789abcdef",
            tvDeviceName = "Living room TV",
            pairedDeviceName = "Phone",
            pairedAtEpochMillis = 100L,
            expiresAtEpochMillis = 200L,
        )
        assertEquals(
            livingRoom,
            StudyFlowTvPhoneCommandFactory.preferredReceiver(
                receivers = listOf(bedroom, livingRoom),
                credential = credential,
                current = null,
            ),
        )
    }
}
