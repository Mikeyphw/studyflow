package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.domain.StudySessionRepository
import com.mikeyphw.studyflow.core.model.PlannedActivityStatus
import com.mikeyphw.studyflow.core.model.StudyCycleSessionBinding
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudySession
import com.mikeyphw.studyflow.core.model.StudySessionId
import com.mikeyphw.studyflow.core.model.StudySessionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class InMemoryStudySessionRepository(
    sessions: List<StudySession>,
) : StudySessionRepository {
    private val sessionState = MutableStateFlow(sessions)

    override fun observeSessions(planId: StudyPlanId): Flow<List<StudySession>> = sessionState.map { sessions ->
        sessions.filter { session -> session.studyPlanId == planId }
    }

    override fun observeActiveSessions(): Flow<List<StudySession>> = sessionState.map { sessions ->
        sessions.filter { session -> session.status in ActiveStatuses }
    }

    override suspend fun findSession(planId: StudyPlanId, sessionId: StudySessionId): StudySession? =
        currentSessions(planId).firstOrNull { session -> session.id == sessionId }

    override suspend fun findActiveSession(planId: StudyPlanId): StudySession? =
        currentSessions(planId).firstOrNull { session -> session.status in ActiveStatuses }

    override suspend fun findAnyActiveSession(): StudySession? =
        sessionState.value.firstOrNull { session -> session.status in ActiveStatuses }

    override suspend fun updateNotes(
        planId: StudyPlanId,
        sessionId: StudySessionId,
        notes: String,
    ): StudySession {
        val current = requireNotNull(findSession(planId, sessionId)) { "Study Session does not exist." }
        return replace(current.copy(notes = notes))
    }

    override suspend fun createSession(
        session: StudySession,
        linkedActivityExpectedStatus: PlannedActivityStatus?,
        cycleBinding: StudyCycleSessionBinding?,
    ): StudySession {
        require(sessionState.value.none { existing -> existing.id == session.id }) { "Study Session already exists." }
        sessionState.value = sessionState.value + session
        return session
    }

    override suspend fun transitionSession(
        expectedStatus: StudySessionStatus,
        session: StudySession,
        linkedActivityExpectedStatus: PlannedActivityStatus?,
        linkedActivityStatus: PlannedActivityStatus?,
        linkedActivityCompletedAtEpochMillis: Long?,
    ): StudySession {
        val current = requireNotNull(findSession(session.studyPlanId, session.id)) { "Study Session does not exist." }
        require(current.status == expectedStatus) { "Study Session status changed before transition." }
        return replace(session)
    }

    fun currentSessions(planId: StudyPlanId): List<StudySession> = sessionState.value
        .filter { session -> session.studyPlanId == planId }
        .sortedByDescending(StudySession::startedAtEpochMillis)

    private fun replace(session: StudySession): StudySession {
        sessionState.value = sessionState.value.map { existing -> if (existing.id == session.id) session else existing }
        return session
    }

    private companion object {
        val ActiveStatuses = setOf(StudySessionStatus.RUNNING, StudySessionStatus.PAUSED)
    }
}
