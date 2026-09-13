package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.model.StudyActivityType
import com.mikeyphw.studyflow.core.model.StudySessionId
import com.mikeyphw.studyflow.core.model.StudySessionStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

class DesktopSessionMonotonicClockTest {
    @Test
    fun desktopSessionDurationIgnoresWallClockJumps() = runBlocking {
        var wallNow = 1_800_000_000_000L
        var monotonicNow = 10_000L
        val services = createDesktopStudyFlowServices(
            wallClockMillis = { wallNow },
            monotonicClockMillis = { monotonicNow },
        )
        val planId = requireNotNull(services.defaultPlanId)
        val started = services.manageStudySession.startQuickSession(
            sessionId = StudySessionId("desktop-monotonic"),
            studyPlanId = planId,
            title = "Monotonic session",
            type = StudyActivityType.THEORY,
            targetDurationMinutes = 30,
        )

        // Simulate a large NTP/manual wall-clock jump while only two monotonic minutes elapse.
        wallNow += 14L * 24L * 60L * 60L * 1_000L
        monotonicNow += 120_000L
        val paused = services.manageStudySession.pause(planId, started.id)

        assertEquals(StudySessionStatus.PAUSED, paused.status)
        assertEquals(120L, paused.grossDurationSeconds)
        assertEquals(0L, paused.pauseDurationSeconds)
    }
}
