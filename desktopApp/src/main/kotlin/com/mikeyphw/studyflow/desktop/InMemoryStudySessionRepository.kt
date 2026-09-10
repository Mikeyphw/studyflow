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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class InMemoryStudySessionRepository(
    sessions: List<StudySession>,
    private val scheduleRepository: InMemoryStudyScheduleRepository? = null,
    private val onChanged: (List<StudySession>) -> Unit = {},
) : StudySessionRepository {
    private val sessionState = MutableStateFlow(sessions)
    private val mutationMutex = Mutex()
    private var cycleBindingRecorder: (suspend (StudySessionId, StudyCycleSessionBinding?) -> Unit)? = null
    private var cycleTerminalHandler: (suspend (StudyPlanId, StudySessionId, StudySessionStatus) -> Unit)? = null

    fun configureCycleIntegration(
        recordBinding: suspend (StudySessionId, StudyCycleSessionBinding?) -> Unit,
        onTerminal: suspend (StudyPlanId, StudySessionId, StudySessionStatus) -> Unit,
    ) {
        cycleBindingRecorder = recordBinding
        cycleTerminalHandler = onTerminal
    }

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

    override suspend fun updateNotes(planId: StudyPlanId, sessionId: StudySessionId, notes: String): StudySession =
        mutationMutex.withLock {
            val current = requireNotNull(findSession(planId, sessionId)) { "Study Session does not exist." }
            replace(current.copy(notes = notes)).also { persist() }
        }

    override suspend fun createSession(
        session: StudySession,
        linkedActivityExpectedStatus: PlannedActivityStatus?,
        cycleBinding: StudyCycleSessionBinding?,
    ): StudySession = mutationMutex.withLock {
        require(sessionState.value.none { existing -> existing.id == session.id }) { "Study Session already exists." }
        require(sessionState.value.none { existing -> existing.status in ActiveStatuses }) {
            "Finish or cancel the active Study Session before starting another one."
        }
        val activityId = session.plannedActivityId
        if (activityId != null && linkedActivityExpectedStatus != null) {
            requireNotNull(scheduleRepository) { "Linked Study Session requires a schedule repository." }
                .transitionFromSession(
                    planId = session.studyPlanId,
                    activityId = activityId,
                    expectedStatus = linkedActivityExpectedStatus,
                    newStatus = PlannedActivityStatus.IN_PROGRESS,
                    completedAtEpochMillis = null,
                )
        }
        sessionState.value = sessionState.value + session
        persist()
        if (cycleBinding != null) cycleBindingRecorder?.invoke(session.id, cycleBinding)
        session
    }

    override suspend fun transitionSession(
        expectedStatus: StudySessionStatus,
        session: StudySession,
        linkedActivityExpectedStatus: PlannedActivityStatus?,
        linkedActivityStatus: PlannedActivityStatus?,
        linkedActivityCompletedAtEpochMillis: Long?,
    ): StudySession = mutationMutex.withLock {
        val current = requireNotNull(findSession(session.studyPlanId, session.id)) { "Study Session does not exist." }
        require(current.status == expectedStatus) { "Study Session status changed before transition." }
        val activityId = session.plannedActivityId
        if (activityId != null && linkedActivityExpectedStatus != null && linkedActivityStatus != null) {
            requireNotNull(scheduleRepository) { "Linked Study Session requires a schedule repository." }
                .transitionFromSession(
                    planId = session.studyPlanId,
                    activityId = activityId,
                    expectedStatus = linkedActivityExpectedStatus,
                    newStatus = linkedActivityStatus,
                    completedAtEpochMillis = linkedActivityCompletedAtEpochMillis,
                )
        }
        replace(session).also { persist() }
        if (session.status in TerminalStatuses) {
            cycleTerminalHandler?.invoke(session.studyPlanId, session.id, session.status)
        }
        session
    }

    suspend fun purgePlan(planId: StudyPlanId) = mutationMutex.withLock {
        sessionState.value = sessionState.value.filterNot { it.studyPlanId == planId }
        persist()
    }

    fun currentSessions(planId: StudyPlanId): List<StudySession> = sessionState.value
        .filter { session -> session.studyPlanId == planId }
        .sortedByDescending(StudySession::startedAtEpochMillis)

    fun currentAllSessions(): List<StudySession> = sessionState.value

    private fun replace(session: StudySession): StudySession {
        sessionState.value = sessionState.value.map { existing -> if (existing.id == session.id) session else existing }
        return session
    }

    private fun persist() {
        onChanged(sessionState.value)
    }

    private companion object {
        val ActiveStatuses = setOf(StudySessionStatus.RUNNING, StudySessionStatus.PAUSED)
        val TerminalStatuses = setOf(StudySessionStatus.COMPLETED, StudySessionStatus.CANCELLED)
    }
}
