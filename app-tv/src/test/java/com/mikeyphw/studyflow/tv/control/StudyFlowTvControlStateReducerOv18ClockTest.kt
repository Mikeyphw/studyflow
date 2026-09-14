package com.mikeyphw.studyflow.tv.control

import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvControlAction
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvControlCommand
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvControlOrigin
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvSurface
import com.mikeyphw.studyflow.tv.navigation.StudyFlowTvDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowTvControlStateReducerOv18ClockTest {
    @Test
    fun reducerDoesNotRejectRemoteCommandBecauseSenderClockLooksOlder() {
        val first = StudyFlowTvControlStateReducer.reduce(
            state = StudyFlowTvControlState(),
            command = StudyFlowTvControlCommand(
                commandId = "phone-2",
                surface = StudyFlowTvSurface.SCHEDULE,
                action = StudyFlowTvControlAction.OPEN,
                targetId = "schedule",
                issuedAtEpochMillis = 10_000L,
            ),
            origin = StudyFlowTvControlOrigin.LOCAL_REMOTE,
            nowEpochMillis = 10_000L,
        ).state

        val reduced = StudyFlowTvControlStateReducer.reduce(
            state = first,
            command = StudyFlowTvControlCommand(
                commandId = "phone-1",
                surface = StudyFlowTvSurface.LIBRARY,
                action = StudyFlowTvControlAction.OPEN,
                targetId = "library",
                issuedAtEpochMillis = 1L,
            ),
            origin = StudyFlowTvControlOrigin.LOCAL_REMOTE,
            nowEpochMillis = 10_001L,
        )

        assertTrue(reduced.applied)
        assertEquals(StudyFlowTvDestination.LIBRARY, reduced.state.destination)
    }
}
