package com.mikeyphw.studyflow.core.data

import androidx.room.withTransaction
import com.mikeyphw.studyflow.core.database.StudyFlowDatabase
import com.mikeyphw.studyflow.core.database.mapper.asEntity
import com.mikeyphw.studyflow.core.database.mapper.asExternalModel
import com.mikeyphw.studyflow.core.domain.StudySessionRepository
import com.mikeyphw.studyflow.core.model.PlannedActivityStatus
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudySession
import com.mikeyphw.studyflow.core.model.StudySessionId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class RoomStudySessionRepository(
    private val database: StudyFlowDatabase,
) : StudySessionRepository {
    private val planningDao = database.studyPlanningDao()
    private val planDao = database.studyPlanDao()

    override fun observeSessions(planId: StudyPlanId): Flow<List<StudySession>> =
        planningDao.observeStudySessions(planId.value).map { sessions ->
            sessions.map { it.asExternalModel() }
        }

    override suspend fun findSession(planId: StudyPlanId, sessionId: StudySessionId): StudySession? =
        planningDao.findStudySession(planId.value, sessionId.value)?.asExternalModel()

    override suspend fun findActiveSession(planId: StudyPlanId): StudySession? =
        planningDao.findActiveStudySession(planId.value)?.asExternalModel()

    override suspend fun upsertSession(
        session: StudySession,
        linkedActivityStatus: PlannedActivityStatus?,
        linkedActivityCompletedAtEpochMillis: Long?,
    ): StudySession = database.withTransaction {
        requirePlan(session.studyPlanId)
        session.subjectId?.let { subjectId -> requireSubject(session.studyPlanId, subjectId.value) }
        session.topicId?.let { topicId -> requireTopic(session.studyPlanId, topicId.value) }
        session.plannedActivityId?.let { activityId ->
            val activity = requireNotNull(
                planningDao.findPlannedActivity(session.studyPlanId.value, activityId.value),
            ) { "Planned activity does not belong to this Study Plan." }
            linkedActivityStatus?.let { status ->
                planningDao.upsertPlannedActivity(
                    activity.copy(
                        status = status.name,
                        completedAtEpochMillis = linkedActivityCompletedAtEpochMillis,
                    ),
                )
            }
        }
        planningDao.upsertStudySession(session.asEntity())
        requireNotNull(planningDao.findStudySession(session.studyPlanId.value, session.id.value))
            .asExternalModel()
    }

    private suspend fun requirePlan(planId: StudyPlanId) {
        requireNotNull(planDao.findById(planId.value)) { "Study Plan does not exist." }
    }

    private suspend fun requireSubject(planId: StudyPlanId, subjectId: String) {
        requireNotNull(database.studyStructureDao().findSubject(planId.value, subjectId)) {
            "The selected subject does not belong to this Study Plan."
        }
    }

    private suspend fun requireTopic(planId: StudyPlanId, topicId: String) {
        requireNotNull(database.studyStructureDao().findTopic(planId.value, topicId)) {
            "The selected topic does not belong to this Study Plan."
        }
    }
}
