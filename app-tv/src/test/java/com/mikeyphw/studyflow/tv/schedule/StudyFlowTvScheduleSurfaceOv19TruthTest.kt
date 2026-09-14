package com.mikeyphw.studyflow.tv.schedule

import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvControlAction
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvSurface
import com.mikeyphw.studyflow.tv.navigation.StudyFlowTvDestination
import org.junit.Assert.assertEquals
import org.junit.Test

class StudyFlowTvScheduleSurfaceOv19TruthTest {
    @Test
    fun openActiveScheduleCommandCarriesCanonicalActivitySourceId() {
        val snapshot = StudyFlowTvScheduleSnapshot(
            scheduleId = "schedule-real",
            title = "Schedule",
            weekLabel = "Week",
            items = listOf(
                StudyFlowTvScheduleItem(
                    id = StudyFlowTvScheduleItemId.FOCUS_BLOCK,
                    sourceId = "activity-canonical-123",
                    title = "Focus block",
                    timeLabel = "Mon 09:00 · 45 min",
                    kind = StudyFlowTvScheduleItemKind.STUDY_BLOCK,
                    destination = StudyFlowTvDestination.STUDY_SESSION,
                    row = 0,
                    column = 0,
                ),
            ),
        )

        val command = StudyFlowTvScheduleSurface.commandForAction(
            action = StudyFlowTvScheduleAction.OPEN_ACTIVE_ITEM,
            snapshot = snapshot,
            commandId = "ov19-open-schedule",
            issuedAtEpochMillis = 1_000L,
        )

        assertEquals(StudyFlowTvSurface.STUDY_SESSION, command.surface)
        assertEquals(StudyFlowTvControlAction.OPEN, command.action)
        assertEquals("activity-canonical-123", command.targetId)
        assertEquals(StudyFlowTvDestination.STUDY_SESSION.route, command.optionId)
    }
}
