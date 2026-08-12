package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.PlannedActivityStatus
import com.mikeyphw.studyflow.core.model.StudyActivityType
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudySession
import com.mikeyphw.studyflow.core.model.StudySessionId
import com.mikeyphw.studyflow.core.model.StudySessionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
            // Expected. Focus sessions are intentionally bounded to 5..240 minutes.
        }
    }
}

private class FakeStudySessionRepository : StudySessionRepository {
    private val sessions = MutableStateFlow<List<StudySession>>(emptyList())

    override fun observeSessions(planId: StudyPlanId): Flow<List<StudySession>> = sessions

    override suspend fun findSession(planId: StudyPlanId, sessionId: StudySessionId): StudySession? =
        sessions.value.firstOrNull { it.studyPlanId == planId && it.id == sessionId }

    override suspend fun findActiveSession(planId: StudyPlanId): StudySession? =
        sessions.value.firstOrNull {
            it.studyPlanId == planId && it.status in setOf(
                StudySessionStatus.RUNNING,
                StudySessionStatus.PAUSED,
            )
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
    ): StudySession {
        require(findSession(session.studyPlanId, session.id) == null)
        require(findActiveSession(session.studyPlanId) == null)
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
