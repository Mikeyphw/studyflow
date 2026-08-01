package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.PlannedActivityStatus
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudySession
import com.mikeyphw.studyflow.core.model.StudySessionId
import kotlinx.coroutines.flow.Flow

interface StudySessionRepository {
    fun observeSessions(planId: StudyPlanId): Flow<List<StudySession>>

    suspend fun findSession(planId: StudyPlanId, sessionId: StudySessionId): StudySession?

    suspend fun findActiveSession(planId: StudyPlanId): StudySession?

    suspend fun upsertSession(
        session: StudySession,
        linkedActivityStatus: PlannedActivityStatus? = null,
        linkedActivityCompletedAtEpochMillis: Long? = null,
    ): StudySession
}
