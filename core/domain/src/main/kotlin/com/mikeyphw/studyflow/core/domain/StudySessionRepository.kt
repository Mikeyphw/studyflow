package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.PlannedActivityStatus
import com.mikeyphw.studyflow.core.model.StudyCycleSessionBinding
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudySession
import com.mikeyphw.studyflow.core.model.StudySessionId
import com.mikeyphw.studyflow.core.model.StudySessionStatus
import kotlinx.coroutines.flow.Flow

interface StudySessionRepository {
    fun observeSessions(planId: StudyPlanId): Flow<List<StudySession>>

    fun observeActiveSessions(): Flow<List<StudySession>>

    suspend fun findSession(planId: StudyPlanId, sessionId: StudySessionId): StudySession?

    suspend fun findActiveSession(planId: StudyPlanId): StudySession?

    suspend fun findAnyActiveSession(): StudySession?

    suspend fun updateNotes(
        planId: StudyPlanId,
        sessionId: StudySessionId,
        notes: String,
    ): StudySession

    suspend fun createSession(
        session: StudySession,
        linkedActivityExpectedStatus: PlannedActivityStatus? = null,
        cycleBinding: StudyCycleSessionBinding? = null,
    ): StudySession

    suspend fun transitionSession(
        expectedStatus: StudySessionStatus,
        session: StudySession,
        linkedActivityExpectedStatus: PlannedActivityStatus? = null,
        linkedActivityStatus: PlannedActivityStatus? = null,
        linkedActivityCompletedAtEpochMillis: Long? = null,
    ): StudySession
}
