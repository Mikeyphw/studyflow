package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.PlannedActivity
import com.mikeyphw.studyflow.core.model.PlannedActivityStatus
import com.mikeyphw.studyflow.core.model.StudyActivityType
import com.mikeyphw.studyflow.core.model.StudyMaterialId
import com.mikeyphw.studyflow.core.model.StudyCycleSessionBinding
import com.mikeyphw.studyflow.core.model.StudyDomainInvariants
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudySession
import com.mikeyphw.studyflow.core.model.StudySessionId
import com.mikeyphw.studyflow.core.model.StudySessionStatus
import com.mikeyphw.studyflow.core.model.SubjectId
import com.mikeyphw.studyflow.core.model.TopicId
import com.mikeyphw.studyflow.core.model.VideoResourceId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class ManageStudySession(
    private val repository: StudySessionRepository,
    private val studyPlanRepository: StudyPlanRepository? = null,
    private val clock: () -> Long = System::currentTimeMillis,
    private val monotonicClockMillis: () -> Long = clock,
) {
    private val runtimeClock = StudySessionRuntimeClock(clock, monotonicClockMillis)
    fun observeSessions(planId: StudyPlanId) = repository.observeSessions(planId)

    fun observeActiveSessions(): Flow<List<StudySession>> = repository.observeActiveSessions()

    fun liveActiveSeconds(session: StudySession): Long = runtimeClock.activeSeconds(session)

    fun observePreferredSessionMinutes(planId: StudyPlanId): Flow<Int?> =
        studyPlanRepository?.observeStoredPlanSettings(planId)?.map { settings -> settings?.preferredSessionMinutes }
            ?: flowOf(null)

    suspend fun startQuickSession(
        sessionId: StudySessionId,
        studyPlanId: StudyPlanId,
        title: String,
        type: StudyActivityType,
        subjectId: SubjectId? = null,
        topicId: TopicId? = null,
        materialId: StudyMaterialId? = null,
        videoResourceId: VideoResourceId? = null,
        targetDurationMinutes: Int? = null,
        cycleBinding: StudyCycleSessionBinding? = null,
    ): StudySession {
        require(title.isNotBlank()) { "Quick Study Session title cannot be blank." }
        StudyDomainInvariants.requireOptionalSessionMinutes(targetDurationMinutes, "Target duration")
        val resolvedTargetDurationMinutes = targetDurationMinutes ?: studyPlanRepository?.let { plans ->
            requireNotNull(plans.observeStoredPlanSettings(studyPlanId).first()) {
                "Study Plan settings must be persisted before starting a quick Study Session."
            }.preferredSessionMinutes
        }
        require(repository.findAnyActiveSession() == null) {
            "Finish or cancel the active Study Session before starting another one."
        }
        val created = repository.createSession(
            StudySession(
                id = sessionId,
                studyPlanId = studyPlanId,
                plannedActivityId = null,
                subjectId = subjectId,
                topicId = topicId,
                materialId = materialId,
                videoResourceId = videoResourceId,
                type = type,
                status = StudySessionStatus.RUNNING,
                startedAtEpochMillis = clock(),
                endedAtEpochMillis = null,
                grossDurationSeconds = 0,
                pauseDurationSeconds = 0,
                pagesCompleted = 0,
                questionsAnswered = 0,
                correctAnswers = 0,
                concentrationRating = null,
                difficultyRating = null,
                notes = title.trim(),
                targetDurationMinutes = resolvedTargetDurationMinutes,
            ),
            cycleBinding = cycleBinding,
        )
        runtimeClock.register(created)
        return created
    }

    suspend fun startFromActivity(sessionId: StudySessionId, activity: PlannedActivity): StudySession {
        require(activity.status in setOf(PlannedActivityStatus.PLANNED, PlannedActivityStatus.MISSED)) {
            "Only planned or missed activities can start a Study Session."
        }
        require(repository.findAnyActiveSession() == null) {
            "Finish or cancel the active Study Session before starting another one."
        }
        val created = repository.createSession(
            StudySession(
                id = sessionId,
                studyPlanId = activity.studyPlanId,
                plannedActivityId = activity.id,
                subjectId = activity.subjectId,
                topicId = activity.topicId,
                materialId = activity.materialId,
                videoResourceId = activity.videoResourceId,
                type = activity.type,
                status = StudySessionStatus.RUNNING,
                startedAtEpochMillis = clock(),
                endedAtEpochMillis = null,
                grossDurationSeconds = 0,
                pauseDurationSeconds = 0,
                pagesCompleted = 0,
                questionsAnswered = 0,
                correctAnswers = 0,
                concentrationRating = null,
                difficultyRating = null,
                notes = activity.title,
                targetDurationMinutes = activity.plannedDurationMinutes,
            ),
            linkedActivityExpectedStatus = activity.status,
        )
        runtimeClock.register(created)
        return created
    }

    suspend fun pause(planId: StudyPlanId, sessionId: StudySessionId): StudySession {
        val session = requireSession(planId, sessionId)
        return repository.transitionSession(
            expectedStatus = StudySessionStatus.RUNNING,
            session = runtimeClock.markPaused(session),
        )
    }

    suspend fun resume(planId: StudyPlanId, sessionId: StudySessionId): StudySession {
        val session = requireSession(planId, sessionId)
        return repository.transitionSession(
            expectedStatus = StudySessionStatus.PAUSED,
            session = runtimeClock.markResumed(session),
        )
    }

    suspend fun complete(
        planId: StudyPlanId,
        sessionId: StudySessionId,
        pagesCompleted: Int,
        questionsAnswered: Int,
        correctAnswers: Int,
        concentrationRating: Int?,
        difficultyRating: Int?,
        notes: String,
    ): StudySession {
        require(pagesCompleted >= 0) { "Completed pages cannot be negative." }
        require(questionsAnswered >= 0) { "Answered questions cannot be negative." }
        require(correctAnswers in 0..questionsAnswered) { "Correct answers must be within answered questions." }
        require(concentrationRating == null || concentrationRating in 1..5) {
            "Concentration rating must be between 1 and 5."
        }
        require(difficultyRating == null || difficultyRating in 1..5) {
            "Difficulty rating must be between 1 and 5."
        }
        val current = requireSession(planId, sessionId)
        val finished = runtimeClock.markEnded(
            session = current,
            status = StudySessionStatus.COMPLETED,
        ).copy(
            pagesCompleted = pagesCompleted,
            questionsAnswered = questionsAnswered,
            correctAnswers = correctAnswers,
            concentrationRating = concentrationRating,
            difficultyRating = difficultyRating,
            notes = notes.trim(),
        )
        return repository.transitionSession(
            expectedStatus = current.status,
            session = finished,
            linkedActivityExpectedStatus = PlannedActivityStatus.IN_PROGRESS,
            linkedActivityStatus = PlannedActivityStatus.COMPLETED,
            linkedActivityCompletedAtEpochMillis = finished.endedAtEpochMillis,
        )
    }

    suspend fun cancel(planId: StudyPlanId, sessionId: StudySessionId): StudySession {
        val current = requireSession(planId, sessionId)
        val cancelled = runtimeClock.markEnded(
            session = current,
            status = StudySessionStatus.CANCELLED,
        )
        return repository.transitionSession(
            expectedStatus = current.status,
            session = cancelled,
            linkedActivityExpectedStatus = PlannedActivityStatus.IN_PROGRESS,
            linkedActivityStatus = PlannedActivityStatus.PLANNED,
            linkedActivityCompletedAtEpochMillis = null,
        )
    }

    suspend fun saveWritingDraft(
        planId: StudyPlanId,
        sessionId: StudySessionId,
        prompt: String,
        draftText: String,
    ): StudySession {
        val current = requireSession(planId, sessionId)
        require(current.type == StudyActivityType.WRITING) { "Writing drafts require a WRITING Study Session." }
        require(current.status == StudySessionStatus.RUNNING || current.status == StudySessionStatus.PAUSED) {
            "Only an active Writing Study Session can save a draft."
        }
        val existing = WritingSessionNoteCodec.decode(current.notes)
        val resolvedPrompt = prompt.trim().ifBlank { existing?.prompt ?: current.notes.trim() }
        require(resolvedPrompt.isNotBlank()) { "Writing prompt cannot be blank." }
        return repository.updateNotes(
            planId = planId,
            sessionId = sessionId,
            notes = WritingSessionNoteCodec.encode(
                WritingSessionRecord(
                    prompt = resolvedPrompt,
                    draftText = draftText,
                    rubric = existing?.rubric,
                    checklist = existing?.checklist ?: WritingCompletionChecklist(
                        taskReviewed = false,
                        structureReviewed = false,
                        cohesionReviewed = false,
                        languageReviewed = false,
                    ),
                ),
            ),
        )
    }

    suspend fun completeWriting(
        planId: StudyPlanId,
        sessionId: StudySessionId,
        prompt: String,
        draftText: String,
        rubric: WritingRubric,
        checklist: WritingCompletionChecklist,
    ): StudySession {
        val current = requireSession(planId, sessionId)
        require(current.type == StudyActivityType.WRITING) { "Writing completion requires a WRITING Study Session." }
        require(current.status == StudySessionStatus.RUNNING || current.status == StudySessionStatus.PAUSED) {
            "Only an active Writing Study Session can be completed."
        }
        require(prompt.isNotBlank()) { "Writing prompt cannot be blank." }
        require(draftText.isNotBlank()) { "Writing draft cannot be blank." }
        require(checklist.complete) { "Complete the Writing checklist before finishing." }
        return complete(
            planId = planId,
            sessionId = sessionId,
            pagesCompleted = 0,
            questionsAnswered = 0,
            correctAnswers = 0,
            concentrationRating = null,
            difficultyRating = null,
            notes = WritingSessionNoteCodec.encode(
                WritingSessionRecord(
                    prompt = prompt.trim(),
                    draftText = draftText,
                    rubric = rubric,
                    checklist = checklist,
                ),
            ),
        )
    }

    suspend fun activeSession(planId: StudyPlanId): StudySession? = repository.findActiveSession(planId)

    suspend fun activeSession(): StudySession? = repository.findAnyActiveSession()

    suspend fun updateNotes(
        planId: StudyPlanId,
        sessionId: StudySessionId,
        notes: String,
    ): StudySession {
        return repository.updateNotes(
            planId = planId,
            sessionId = sessionId,
            notes = notes.trim(),
        )
    }

    private suspend fun requireSession(planId: StudyPlanId, sessionId: StudySessionId): StudySession =
        requireNotNull(repository.findSession(planId, sessionId)) {
            "Study Session does not belong to this Study Plan."
        }
}


private class StudySessionRuntimeClock(
    private val wallClockMillis: () -> Long,
    private val monotonicClockMillis: () -> Long,
) {
    private data class RuntimeState(
        val status: StudySessionStatus,
        val activeMillis: Long,
        val pausedMillis: Long,
        val lastMonotonicMillis: Long,
    ) {
        val activeSeconds: Long get() = activeMillis / 1_000L
        val pausedSeconds: Long get() = pausedMillis / 1_000L
    }

    private val states = java.util.concurrent.ConcurrentHashMap<StudySessionId, RuntimeState>()

    fun register(session: StudySession) {
        states[session.id] = RuntimeState(
            status = session.status,
            activeMillis = session.grossDurationSeconds.coerceAtLeast(0) * 1_000L,
            pausedMillis = session.pauseDurationSeconds.coerceAtLeast(0) * 1_000L,
            lastMonotonicMillis = monotonicClockMillis(),
        )
    }

    fun activeSeconds(session: StudySession): Long = snapshot(session).activeSeconds

    fun markPaused(session: StudySession): StudySession {
        require(session.status == StudySessionStatus.RUNNING) { "Only a running Study Session can be paused." }
        val snapshot = snapshot(session)
        val wallNow = wallClockMillis()
        states[session.id] = snapshot.copy(
            status = StudySessionStatus.PAUSED,
            lastMonotonicMillis = monotonicClockMillis(),
        )
        return session.copy(
            status = StudySessionStatus.PAUSED,
            endedAtEpochMillis = wallNow.coerceAtLeast(session.startedAtEpochMillis),
            // Historical schema name: this column stores canonical active study time, not wall/gross time.
            grossDurationSeconds = snapshot.activeSeconds,
            pauseDurationSeconds = snapshot.pausedSeconds,
        )
    }

    fun markResumed(session: StudySession): StudySession {
        require(session.status == StudySessionStatus.PAUSED) { "Only a paused Study Session can be resumed." }
        val snapshot = snapshot(session)
        states[session.id] = snapshot.copy(
            status = StudySessionStatus.RUNNING,
            lastMonotonicMillis = monotonicClockMillis(),
        )
        return session.copy(
            status = StudySessionStatus.RUNNING,
            endedAtEpochMillis = null,
            grossDurationSeconds = snapshot.activeSeconds,
            pauseDurationSeconds = snapshot.pausedSeconds,
        )
    }

    fun markEnded(session: StudySession, status: StudySessionStatus): StudySession {
        require(status in setOf(StudySessionStatus.COMPLETED, StudySessionStatus.CANCELLED)) {
            "Study Session can only end as completed or cancelled."
        }
        val snapshot = snapshot(session)
        states.remove(session.id)
        return session.copy(
            status = status,
            endedAtEpochMillis = wallClockMillis().coerceAtLeast(session.startedAtEpochMillis),
            grossDurationSeconds = snapshot.activeSeconds,
            pauseDurationSeconds = snapshot.pausedSeconds,
        )
    }

    @Synchronized
    private fun snapshot(session: StudySession): RuntimeState {
        val monotonicNow = monotonicClockMillis()
        val previous = states[session.id]?.takeIf { it.status == session.status } ?: bootstrap(session, monotonicNow)
        val deltaMillis = (monotonicNow - previous.lastMonotonicMillis).coerceAtLeast(0L)
        val updated = when (session.status) {
            StudySessionStatus.RUNNING -> previous.copy(
                activeMillis = previous.activeMillis + deltaMillis,
                lastMonotonicMillis = monotonicNow,
            )
            StudySessionStatus.PAUSED -> previous.copy(
                pausedMillis = previous.pausedMillis + deltaMillis,
                lastMonotonicMillis = monotonicNow,
            )
            StudySessionStatus.COMPLETED, StudySessionStatus.CANCELLED -> previous
                .copy(lastMonotonicMillis = monotonicNow)
        }
        states[session.id] = updated
        return updated
    }

    private fun bootstrap(session: StudySession, monotonicNow: Long): RuntimeState {
        val wallNow = wallClockMillis()
        return RuntimeState(
            status = session.status,
            activeMillis = StudySessionTimer.activeSeconds(session, wallNow) * 1_000L,
            pausedMillis = StudySessionTimer.pausedSeconds(session, wallNow) * 1_000L,
            lastMonotonicMillis = monotonicNow,
        )
    }
}
