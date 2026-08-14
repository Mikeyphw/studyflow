package com.mikeyphw.studyflow.core.data

import androidx.room.withTransaction
import com.mikeyphw.studyflow.core.database.StudyFlowDatabase
import com.mikeyphw.studyflow.core.database.entity.DatabaseMetadataEntity
import com.mikeyphw.studyflow.core.database.mapper.asEntity
import com.mikeyphw.studyflow.core.database.mapper.asExternalModel
import com.mikeyphw.studyflow.core.domain.StudySessionRepository
import com.mikeyphw.studyflow.core.model.PlannedActivityStatus
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudySession
import com.mikeyphw.studyflow.core.model.StudySessionId
import com.mikeyphw.studyflow.core.model.StudySessionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class RoomStudySessionRepository(
    private val database: StudyFlowDatabase,
    private val diagnosticRecorder: com.mikeyphw.studyflow.core.diagnostics.DiagnosticRecorder = com
        .mikeyphw.studyflow.core.diagnostics.NoOpDiagnosticRecorder,
) : StudySessionRepository {
    private val planningDao = database.studyPlanningDao()
    private val planDao = database.studyPlanDao()
    private val metadataDao = database.databaseMetadataDao()

    override fun observeSessions(planId: StudyPlanId): Flow<List<StudySession>> =
        planningDao.observeStudySessions(planId.value).map { sessions ->
            sessions.map { it.asExternalModel() }
        }

    override fun observeActiveSessions(): Flow<List<StudySession>> =
        planningDao.observeAllActiveStudySessions().map { sessions ->
            check(sessions.size <= 1) {
                "StudyFlow database corruption: multiple active Study Sessions exist across Study Plans."
            }
            sessions.map { it.asExternalModel() }
        }

    override suspend fun findSession(planId: StudyPlanId, sessionId: StudySessionId): StudySession? =
        planningDao.findStudySession(planId.value, sessionId.value)?.asExternalModel()

    override suspend fun findActiveSession(planId: StudyPlanId): StudySession? {
        val active = planningDao.findActiveStudySessions(planId.value)
        check(active.size <= 1) {
            "StudyFlow database corruption: multiple active Study Sessions exist for one Study Plan."
        }
        return active.singleOrNull()?.asExternalModel()
    }

    override suspend fun findAnyActiveSession(): StudySession? {
        val active = planningDao.findAllActiveStudySessions()
        check(active.size <= 1) {
            "StudyFlow database corruption: multiple active Study Sessions exist across Study Plans."
        }
        return active.singleOrNull()?.asExternalModel()
    }

    override suspend fun updateNotes(
        planId: StudyPlanId,
        sessionId: StudySessionId,
        notes: String,
    ): StudySession = database.withTransaction {
        require(
            planningDao.updateActiveStudySessionNotes(
                planId = planId.value,
                sessionId = sessionId.value,
                notes = notes,
            ) == 1,
        ) { "Only an active Study Session can update notes." }
        requireNotNull(planningDao.findStudySession(planId.value, sessionId.value)).asExternalModel()
    }

    override suspend fun createSession(
        session: StudySession,
        linkedActivityExpectedStatus: PlannedActivityStatus?,
    ): StudySession = diagnosticRecorder.recordRepositoryMutation(
        area = com.mikeyphw.studyflow.core.diagnostics.DiagnosticArea.STUDY_SESSIONS,
        action = "session-create",
    ) {
        database.withTransaction {
        require(session.status == StudySessionStatus.RUNNING) {
            "A new Study Session must start in RUNNING state."
        }
        validateOwnership(session)
        require(planningDao.findStudySession(session.studyPlanId.value, session.id.value) == null) {
            "Study Session already exists."
        }
        val active = planningDao.findAllActiveStudySessions()
        require(active.isEmpty(
            )) { "Another Study Session is already active. Finish or cancel it before starting a new one." }

        session.plannedActivityId?.let { activityId ->
            val expected = requireNotNull(linkedActivityExpectedStatus) {
                "Starting a planned Study Session requires the expected activity status."
            }
            require(expected in setOf(PlannedActivityStatus.PLANNED, PlannedActivityStatus.MISSED)) {
                "Only a planned or missed activity can start a Study Session."
            }
            requireNotNull(planningDao.findPlannedActivity(session.studyPlanId.value, activityId.value)) {
                "Planned activity does not belong to this Study Plan."
            }
            require(
                planningDao.compareAndSetPlannedActivityStatus(
                    planId = session.studyPlanId.value,
                    activityId = activityId.value,
                    expectedStatus = expected.name,
                    newStatus = PlannedActivityStatus.IN_PROGRESS.name,
                    newCompletedAtEpochMillis = null,
                ) == 1,
            ) { "Planned activity changed before the Study Session could start." }
            metadataDao.upsert(
                DatabaseMetadataEntity(
                    key = sourceActivityStatusKey(session.id),
                    value = expected.name,
                ),
            )
        }

        planningDao.upsertStudySession(session.asEntity())
        requireNotNull(planningDao.findStudySession(session.studyPlanId.value, session.id.value))
            .asExternalModel()
    }
    }

    override suspend fun transitionSession(
        expectedStatus: StudySessionStatus,
        session: StudySession,
        linkedActivityExpectedStatus: PlannedActivityStatus?,
        linkedActivityStatus: PlannedActivityStatus?,
        linkedActivityCompletedAtEpochMillis: Long?,
    ): StudySession = diagnosticRecorder.recordRepositoryMutation(
        area = com.mikeyphw.studyflow.core.diagnostics.DiagnosticArea.STUDY_SESSIONS,
        action = "session-transition",
    ) {
        database.withTransaction {
        validateOwnership(session)
        val current = requireNotNull(
            planningDao.findStudySession(session.studyPlanId.value, session.id.value),
        ) { "Study Session does not belong to this Study Plan." }.asExternalModel()
        require(current.status == expectedStatus) {
            "Study Session changed before the requested transition could be applied."
        }
        requireStableSessionIdentity(current, session)
        requireValidSessionTransition(current, session)

        if (linkedActivityStatus != null) {
            val activityId = requireNotNull(session.plannedActivityId) {
                "Only a Study Session linked to a planned activity can update activity status."
            }
            val activityExpected = requireNotNull(linkedActivityExpectedStatus) {
                "Linked activity transition requires an expected status."
            }
            val resolvedLinkedStatus = if (session.status == StudySessionStatus.CANCELLED) {
                metadataDao.findValue(sourceActivityStatusKey(session.id))
                    ?.let { value -> runCatching { PlannedActivityStatus.valueOf(value) }.getOrNull() }
                    ?.takeIf { it in setOf(PlannedActivityStatus.PLANNED, PlannedActivityStatus.MISSED) }
                    ?: linkedActivityStatus
            } else {
                linkedActivityStatus
            }
            require(
                planningDao.compareAndSetPlannedActivityStatus(
                    planId = session.studyPlanId.value,
                    activityId = activityId.value,
                    expectedStatus = activityExpected.name,
                    newStatus = resolvedLinkedStatus.name,
                    newCompletedAtEpochMillis = linkedActivityCompletedAtEpochMillis,
                ) == 1,
            ) { "Planned activity changed before the Study Session transition could be applied." }
        }

        val updated = if (session.status == StudySessionStatus.COMPLETED) {
            planningDao.completeStudySessionIfStatus(
                planId = session.studyPlanId.value,
                sessionId = session.id.value,
                expectedStatus = expectedStatus.name,
                endedAtEpochMillis = requireNotNull(session.endedAtEpochMillis),
                grossDurationSeconds = session.grossDurationSeconds,
                pauseDurationSeconds = session.pauseDurationSeconds,
                pagesCompleted = session.pagesCompleted,
                questionsAnswered = session.questionsAnswered,
                correctAnswers = session.correctAnswers,
                concentrationRating = session.concentrationRating,
                difficultyRating = session.difficultyRating,
                notes = session.notes,
            )
        } else {
            planningDao.compareAndSetStudySessionStatus(
                planId = session.studyPlanId.value,
                sessionId = session.id.value,
                expectedStatus = expectedStatus.name,
                newStatus = session.status.name,
                endedAtEpochMillis = session.endedAtEpochMillis,
                grossDurationSeconds = session.grossDurationSeconds,
                pauseDurationSeconds = session.pauseDurationSeconds,
            )
        }
        require(updated == 1) {
            "Study Session changed before the requested transition could be applied."
        }
        if (session.status in setOf(StudySessionStatus.COMPLETED, StudySessionStatus.CANCELLED)) {
            metadataDao.delete(sourceActivityStatusKey(session.id))
        }
        requireNotNull(planningDao.findStudySession(session.studyPlanId.value, session.id.value))
            .asExternalModel()
    }
    }

    private suspend fun validateOwnership(session: StudySession) {
        requirePlan(session.studyPlanId)
        session.subjectId?.let { subjectId -> requireSubject(session.studyPlanId, subjectId.value) }
        session.topicId?.let { topicId -> requireTopic(session.studyPlanId, topicId.value) }
        session.materialId?.let { materialId -> requireMaterial(session.studyPlanId, materialId.value) }
        session.videoResourceId?.let { videoId -> requireVideo(session.studyPlanId, videoId.value) }
        session.plannedActivityId?.let { activityId ->
            val activity = requireNotNull(planningDao.findPlannedActivity(session.studyPlanId.value,
                activityId.value)) {
                "Planned activity does not belong to this Study Plan."
            }
            require(activity.subjectId == session.subjectId?.value) {
                "Study Session subject must match its linked planned activity."
            }
            require(activity.topicId == session.topicId?.value) {
                "Study Session topic must match its linked planned activity."
            }
            require(activity.materialId == session.materialId?.value) {
                "Study Session material must match its linked planned activity."
            }
            require(activity.videoResourceId == session.videoResourceId?.value) {
                "Study Session video must match its linked planned activity."
            }
            require(activity.type == session.type.name) {
                "Study Session type must match its linked planned activity."
            }
        }
    }

    private fun requireStableSessionIdentity(current: StudySession, proposed: StudySession) {
        require(current.id == proposed.id) { "Study Session id cannot change." }
        require(current.studyPlanId == proposed.studyPlanId) { "Study Session ownership cannot change." }
        require(current.plannedActivityId == proposed.plannedActivityId) { "Planned activity link cannot change." }
        require(current.subjectId == proposed.subjectId) { "Study Session subject cannot change during execution." }
        require(current.topicId == proposed.topicId) { "Study Session topic cannot change during execution." }
        require(current.materialId == proposed.materialId) { "Study Session material cannot change during execution." }
        require(current.videoResourceId == proposed.videoResourceId) {
            "Study Session video cannot change during execution."
        }
        require(current.type == proposed.type) { "Study Session type cannot change during execution." }
        require(current.startedAtEpochMillis == proposed.startedAtEpochMillis) { "Study Session start cannot change." }
        require(current.targetDurationMinutes == proposed.targetDurationMinutes) {
            "Study Session target duration cannot change during execution."
        }
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

    private suspend fun requireMaterial(planId: StudyPlanId, materialId: String) {
        requireNotNull(database.studyLibraryDao().findMaterial(planId.value, materialId)) {
            "The selected material does not belong to this Study Plan."
        }
    }

    private suspend fun requireVideo(planId: StudyPlanId, videoId: String) {
        requireNotNull(database.studyLibraryDao().findVideo(planId.value, videoId)) {
            "The selected video does not belong to this Study Plan."
        }
    }

    private fun requireValidSessionTransition(current: StudySession?, proposed: StudySession) {
        if (current == null) {
            require(proposed.status == StudySessionStatus.RUNNING) {
                "A new Study Session must start in RUNNING state."
            }
            return
        }
        require(current.studyPlanId == proposed.studyPlanId) { "Study Session ownership cannot change." }
        val allowed = when (current.status) {
            StudySessionStatus.RUNNING -> setOf(
                StudySessionStatus.RUNNING,
                StudySessionStatus.PAUSED,
                StudySessionStatus.COMPLETED,
                StudySessionStatus.CANCELLED,
            )
            StudySessionStatus.PAUSED -> setOf(
                StudySessionStatus.PAUSED,
                StudySessionStatus.RUNNING,
                StudySessionStatus.COMPLETED,
                StudySessionStatus.CANCELLED,
            )
            StudySessionStatus.COMPLETED -> setOf(StudySessionStatus.COMPLETED)
            StudySessionStatus.CANCELLED -> setOf(StudySessionStatus.CANCELLED)
        }
        require(proposed.status in allowed) {
            "Study Session cannot transition from ${current.status} to ${proposed.status}."
        }
        if (current.status == StudySessionStatus.COMPLETED || current.status == StudySessionStatus.CANCELLED) {
            require(current == proposed) { "A terminal Study Session is immutable." }
        }
    }

    private fun sourceActivityStatusKey(sessionId: StudySessionId): String =
        "session_source_activity_status:${sessionId.value}"

}
