package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.PlannedActivityStatus
import com.mikeyphw.studyflow.core.model.StudyActivityType
import com.mikeyphw.studyflow.core.model.StudyCycleSessionBinding
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyPlanSettings
import com.mikeyphw.studyflow.core.model.StudyPlanStatus
import com.mikeyphw.studyflow.core.model.StudySession
import com.mikeyphw.studyflow.core.model.StudySessionId
import com.mikeyphw.studyflow.core.model.StudySessionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class ManageStudySessionFocusTest {
    @Test
    fun quickSessionPersistsTargetDurationAndEditableNotes() = runBlocking {
        val repository = FakeStudySessionRepository()
        val manager = ManageStudySession(repository, clock = { 1_000L })
        val planId = StudyPlanId("plan")
        val session = manager.startQuickSession(
            sessionId = StudySessionId("session"),
            studyPlanId = planId,
            title = "Constitutional law",
            type = StudyActivityType.THEORY,
            targetDurationMinutes = 45,
        )

        assertEquals(45, session.targetDurationMinutes)
        val updated = manager.updateNotes(planId, session.id, "Articles 5 through 7")
        assertEquals("Articles 5 through 7", updated.notes)
    }


    @Test
    fun aSecondSessionCannotStartWhileFocusIsActive() = runBlocking {
        val repository = FakeStudySessionRepository()
        val manager = ManageStudySession(repository, clock = { 1_000L })
        val planId = StudyPlanId("plan")
        manager.startQuickSession(
            sessionId = StudySessionId("first"),
            studyPlanId = planId,
            title = "First",
            type = StudyActivityType.THEORY,
            targetDurationMinutes = 25,
        )

        try {
            manager.startQuickSession(
                sessionId = StudySessionId("second"),
                studyPlanId = planId,
                title = "Second",
                type = StudyActivityType.QUESTIONS,
                targetDurationMinutes = 25,
            )
            fail("Expected a second active session to be rejected")
        } catch (_: IllegalArgumentException) {
            // Active focus ownership stays with the first session.
        }
    }

    @Test
    fun quickSessionUsesPreferredDurationWhenNoOverrideIsProvided() = runBlocking {
        val planId = StudyPlanId("plan")
        val settings = StudyPlanSettings.defaults(planId).copy(preferredSessionMinutes = 55)
        val manager = ManageStudySession(
            repository = FakeStudySessionRepository(),
            studyPlanRepository = FakeStudyPlanSettingsRepository(settings),
            clock = { 1_000L },
        )

        val session = manager.startQuickSession(
            sessionId = StudySessionId("preferred"),
            studyPlanId = planId,
            title = "Preferred duration",
            type = StudyActivityType.THEORY,
        )

        assertEquals(55, session.targetDurationMinutes)
    }

    @Test
    fun activeSessionInAnotherPlanBlocksStartingFocus() = runBlocking {
        val repository = FakeStudySessionRepository()
        val manager = ManageStudySession(repository, clock = { 1_000L })
        manager.startQuickSession(
            sessionId = StudySessionId("plan-a-session"),
            studyPlanId = StudyPlanId("plan-a"),
            title = "Plan A",
            type = StudyActivityType.THEORY,
            targetDurationMinutes = 25,
        )

        try {
            manager.startQuickSession(
                sessionId = StudySessionId("plan-b-session"),
                studyPlanId = StudyPlanId("plan-b"),
                title = "Plan B",
                type = StudyActivityType.QUESTIONS,
                targetDurationMinutes = 25,
            )
            fail("Expected the process-wide active Study Session to block another plan")
        } catch (_: IllegalArgumentException) {
            // The application owns a single active focus session across Study Plans.
        }
    }

    @Test
    fun completionPersistsRatingsAndFreezesDuration() = runBlocking {
        var now = 1_000L
        val repository = FakeStudySessionRepository()
        val manager = ManageStudySession(repository, clock = { now })
        val planId = StudyPlanId("plan")
        val started = manager.startQuickSession(
            sessionId = StudySessionId("session"),
            studyPlanId = planId,
            title = "Theory",
            type = StudyActivityType.THEORY,
            targetDurationMinutes = 5,
        )
        now = 301_000L

        val completed = manager.complete(
            planId = planId,
            sessionId = started.id,
            pagesCompleted = 12,
            questionsAnswered = 20,
            correctAnswers = 16,
            concentrationRating = 4,
            difficultyRating = 3,
            notes = "  Articles reviewed  ",
        )

        assertEquals(StudySessionStatus.COMPLETED, completed.status)
        assertEquals(300, StudySessionTimer.activeSeconds(completed, nowEpochMillis = 999_000L))
        assertEquals(4, completed.concentrationRating)
        assertEquals(3, completed.difficultyRating)
        assertEquals("Articles reviewed", completed.notes)
    }

    @Test
    fun monotonicRuntimeSurvivesWallClockMovingBackward() = runBlocking {
        var wallNow = 10_000L
        var monotonicNow = 1_000L
        val repository = FakeStudySessionRepository()
        val manager = ManageStudySession(
            repository = repository,
            clock = { wallNow },
            monotonicClockMillis = { monotonicNow },
        )
        val planId = StudyPlanId("plan")
        val started = manager.startQuickSession(
            sessionId = StudySessionId("clock-jump"),
            studyPlanId = planId,
            title = "Monotonic runtime",
            type = StudyActivityType.THEORY,
            targetDurationMinutes = 5,
        )

        wallNow = 5_000L
        monotonicNow = 61_000L
        val completed = manager.complete(
            planId = planId,
            sessionId = started.id,
            pagesCompleted = 0,
            questionsAnswered = 0,
            correctAnswers = 0,
            concentrationRating = null,
            difficultyRating = null,
            notes = "",
        )

        assertEquals(60, completed.grossDurationSeconds)
        assertEquals(started.startedAtEpochMillis, completed.endedAtEpochMillis)
    }


    @Test
    fun validLongPlannedActivityStartsWithItsFullTargetDuration() = runBlocking {
        val repository = FakeStudySessionRepository()
        val manager = ManageStudySession(repository)
        val activity = com.mikeyphw.studyflow.core.model.PlannedActivity(
            id = com.mikeyphw.studyflow.core.model.PlannedActivityId("long-activity"),
            studyPlanId = StudyPlanId("plan"),
            subjectId = null,
            topicId = null,
            materialId = null,
            videoResourceId = null,
            type = StudyActivityType.THEORY,
            title = "Long simulation",
            scheduledStartEpochMillis = null,
            plannedDurationMinutes = 300,
            status = PlannedActivityStatus.PLANNED,
            locked = false,
            completedAtEpochMillis = null,
        )

        val started = manager.startFromActivity(StudySessionId("long-session"), activity)

        assertEquals(300, started.targetDurationMinutes)
    }

    @Test
    fun pauseResumePersistsActiveAndPausedTimeWithoutDoubleCounting() = runBlocking {
        var wallNow = 1_000L
        var monotonicNow = 1_000L
        val repository = FakeStudySessionRepository()
        val manager = ManageStudySession(
            repository = repository,
            clock = { wallNow },
            monotonicClockMillis = { monotonicNow },
        )
        val planId = StudyPlanId("plan")
        val started = manager.startQuickSession(
            sessionId = StudySessionId("paused-session"),
            studyPlanId = planId,
            title = "Pause semantics",
            type = StudyActivityType.THEORY,
            targetDurationMinutes = 30,
        )

        wallNow += 600_000L
        monotonicNow += 600_000L
        val paused = manager.pause(planId, started.id)
        assertEquals(600L, paused.grossDurationSeconds)
        assertEquals(0L, paused.pauseDurationSeconds)

        wallNow += 600_000L
        monotonicNow += 600_000L
        val resumed = manager.resume(planId, started.id)
        assertEquals(600L, resumed.grossDurationSeconds)
        assertEquals(600L, resumed.pauseDurationSeconds)

        val completed = manager.complete(
            planId = planId,
            sessionId = started.id,
            pagesCompleted = 0,
            questionsAnswered = 0,
            correctAnswers = 0,
            concentrationRating = null,
            difficultyRating = null,
            notes = "",
        )
        assertEquals(600L, completed.grossDurationSeconds)
        assertEquals(600L, completed.pauseDurationSeconds)
        assertEquals(600L, StudySessionTimer.persistedActiveSeconds(completed))
    }

    @Test
    fun targetDurationRejectsValuesOutsideFocusBounds() = runBlocking {
        val manager = ManageStudySession(FakeStudySessionRepository())

        try {
            manager.startQuickSession(
                sessionId = StudySessionId("session"),
                studyPlanId = StudyPlanId("plan"),
                title = "Invalid",
                type = StudyActivityType.CUSTOM,
                targetDurationMinutes = 4,
            )
            fail("Expected an invalid focus duration to be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected. Session targets are canonically bounded to 5..720 minutes.
        }
    }
}

private class FakeStudySessionRepository : StudySessionRepository {
    private val sessions = MutableStateFlow<List<StudySession>>(emptyList())

    override fun observeSessions(planId: StudyPlanId): Flow<List<StudySession>> = sessions

    override fun observeActiveSessions(): Flow<List<StudySession>> = sessions

    override suspend fun findSession(planId: StudyPlanId, sessionId: StudySessionId): StudySession? =
        sessions.value.firstOrNull { it.studyPlanId == planId && it.id == sessionId }

    override suspend fun findActiveSession(planId: StudyPlanId): StudySession? =
        sessions.value.firstOrNull {
            it.studyPlanId == planId && it.status in setOf(
                StudySessionStatus.RUNNING,
                StudySessionStatus.PAUSED,
            )
        }

    override suspend fun findAnyActiveSession(): StudySession? = sessions.value.firstOrNull {
        it.status in setOf(StudySessionStatus.RUNNING, StudySessionStatus.PAUSED)
    }

    override suspend fun updateNotes(
        planId: StudyPlanId,
        sessionId: StudySessionId,
        notes: String,
    ): StudySession {
        val current = requireNotNull(findSession(planId, sessionId))
        val updated = current.copy(notes = notes)
        sessions.value = sessions.value.filterNot { it.id == sessionId } + updated
        return updated
    }

    override suspend fun createSession(
        session: StudySession,
        linkedActivityExpectedStatus: PlannedActivityStatus?,
        cycleBinding: StudyCycleSessionBinding?,
    ): StudySession {
        require(findSession(session.studyPlanId, session.id) == null)
        require(findAnyActiveSession() == null)
        sessions.value = sessions.value + session
        return session
    }

    override suspend fun transitionSession(
        expectedStatus: StudySessionStatus,
        session: StudySession,
        linkedActivityExpectedStatus: PlannedActivityStatus?,
        linkedActivityStatus: PlannedActivityStatus?,
        linkedActivityCompletedAtEpochMillis: Long?,
    ): StudySession {
        val current = requireNotNull(findSession(session.studyPlanId, session.id))
        require(current.status == expectedStatus)
        sessions.value = sessions.value.filterNot { it.id == session.id } + session
        return session
    }
}


private class FakeStudyPlanSettingsRepository(
    private val settings: StudyPlanSettings,
) : StudyPlanRepository {
    override val plans: Flow<List<StudyPlan>> = flowOf(emptyList())

    override fun observePlan(planId: StudyPlanId): Flow<StudyPlan?> = flowOf(null)

    override fun observePlanSettings(planId: StudyPlanId): Flow<StudyPlanSettings> = flowOf(settings)

    override fun observeStoredPlanSettings(planId: StudyPlanId): Flow<StudyPlanSettings?> = flowOf(settings)

    override suspend fun create(plan: StudyPlan, settings: StudyPlanSettings) = Unit

    override suspend fun replace(plan: StudyPlan) = Unit

    override suspend fun duplicate(
        sourcePlanId: StudyPlanId,
        newPlanId: StudyPlanId,
        newName: String,
    ): StudyPlan = error("Not used")

    override suspend fun updateStatus(planId: StudyPlanId, status: StudyPlanStatus) = Unit

    override suspend fun moveToTrash(planId: StudyPlanId) = Unit

    override suspend fun restoreFromTrash(planId: StudyPlanId) = Unit

    override suspend fun updatePlanSettings(settings: StudyPlanSettings) = Unit

    override suspend fun deletePermanently(planId: StudyPlanId) = Unit

    override suspend fun clear() = Unit

    override suspend fun restoreFoundationSamples() = Unit

}
